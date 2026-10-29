package com.blog.rag.service;

import com.blog.common.RateLimitException;
import com.blog.config.RagProperties;
import com.blog.dto.RagAskDTO;
import com.blog.entity.User;
import com.blog.rag.chunk.RetrievedChunk;
import com.blog.rag.llm.LlmClient;
import com.blog.rag.llm.LlmException;
import com.blog.rag.retrieval.ChunkRetriever;
import com.blog.service.UserService;
import com.blog.utils.IpUtil;
import com.blog.utils.JwtUtil;
import com.blog.utils.RedisUtil;
import com.blog.vo.RagAskVO;
import com.blog.vo.RagReferenceVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 站内知识问答编排服务。
 *
 * <p>完整链路：</p>
 * <pre>
 * 问题 → ① 查询预处理（抽词 / 去停用词 / 去疑问词 / 同义词扩展）
 *      → ② 检索 Top-K（BM25 倒排索引）
 *      → ③ 相关性阈值过滤（剔除弱相关块，防拼接式幻觉）
 *      → ④ 构建上下文（去重 / 按预算截断 / 编号）
 *      → ⑤ 拼 Prompt（角色 + 边界 + 无答案兜底话术 + 引用格式）
 *      → ⑥ 调大模型（带重试）
 *      → ⑦ 返回答案 + 出处列表
 * </pre>
 *
 * <p>各步骤刻意拆成独立组件（{@code QueryPreprocessor} / {@code ChunkRetriever} /
 * {@code ContextBuilder} / {@code PromptBuilder} / {@code LlmClient}），
 * 这样第二阶段把关键词检索换成向量检索时，只需替换 {@code ChunkRetriever} 的实现，
 * 本类的编排逻辑与前端接口都不用动。</p>
 *
 * <p><b>三处「防御性」设计</b>，都是在真实模型接入后补上的：</p>
 * <ul>
 *   <li>③ 相关性阈值 —— 弱相关块进上下文会让模型拼凑出资料里没有的细节</li>
 *   <li>⑥ 调用重试 —— 外部模型偶发网络抖动（Connection refused）不该直接失败</li>
 *   <li>限流的异常必须原样抛出 —— 被 Redis 故障的 catch 吞掉会让限流形同虚设</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagAskService {

    private final ChunkRetriever retriever;
    private final QueryPreprocessor preprocessor;
    private final ContextBuilder contextBuilder;
    private final PromptBuilder promptBuilder;
    private final LlmClient llmClient;
    private final RedisUtil redisUtil;
    private final RagProperties properties;
    private final JwtUtil jwtUtil;
    private final UserService userService;

    /** 站点名，写进 Prompt 让模型知道自己在回答什么问题域的问题 */
    private static final String SITE_NAME = "个人博客";

    /**
     * 回答一个问题。
     *
     * @param dto     入参
     * @param request 请求对象（用于取客户端 IP 做限流）
     * @return 回答与出处
     */
    public RagAskVO ask(RagAskDTO dto, HttpServletRequest request) {
        long start = System.currentTimeMillis();

        if (!properties.isEnabled()) {
            throw new IllegalStateException("站内问答功能未启用");
        }

        checkRateLimit(request);

        String question = dto.getQuestion().trim();
        int topK = normalizeTopK(dto.getTopK());

        // 1. 查询预处理：抽出检索词元（含同义词扩展）
        Set<String> terms = preprocessor.process(question);
        if (terms.isEmpty()) {
            return buildEmptyAnswer(start, "问题过于简单，请补充更具体的信息");
        }

        // 2. 检索：用预处理后的词元检索，避免二次分词引入噪声。
        //    先粗排多取一些，再由 retriever 精排
        List<RetrievedChunk> chunks = retriever.retrieveByTerms(terms, topK * properties.getCandidateMultiplier());
        if (chunks.isEmpty()) {
            log.info("[RAG] 问题「{}」未召回任何内容", question);
            return buildEmptyAnswer(start, null);
        }
        // 粗排结果截断到 topK
        if (chunks.size() > topK) {
            chunks = new ArrayList<>(chunks.subList(0, topK));
        }

        // 3. 相关性阈值：只保留得分接近最高分的块。
        //
        // 为什么要这一步：BM25 只过滤「一分不得」的块，但弱相关块（得分是正数却很低）
        // 仍会进上下文。实测中问「鉴权怎么做的」会混进《Pinia 状态管理》这类文章，
        // 模型据此把别处的细节（如某个并不存在的持久化插件）拼进答案 —— 这是典型的
        // 「拼接式幻觉」。剔除弱相关块既减少幻觉，也降低 token 成本。
        chunks = filterByRelevance(chunks);

        // 4. 构建上下文（去重、截断、编号）
        ContextBuilder.BuiltContext built = contextBuilder.build(chunks, properties.getContextMaxChars());
        if (built.getContext().isBlank()) {
            return buildEmptyAnswer(start, null);
        }

        // 5. 拼 Prompt
        String systemPrompt = promptBuilder.buildSystemPrompt(SITE_NAME);
        String userPrompt = promptBuilder.build(built.getContext(), question, SITE_NAME);

        // 6. 调模型（客户端内部带重试；重试仍失败才抛异常）
        String answer;
        try {
            answer = llmClient.chat(systemPrompt, userPrompt);
        } catch (Exception e) {
            log.error("[RAG] 生成回答失败：{}", e.getMessage(), e);
            throw new LlmException("生成回答失败，请稍后重试");
        }

        // 7. 组装响应
        RagAskVO vo = new RagAskVO();
        vo.setAnswer(answer);
        vo.setReferences(toReferenceVOs(built.getReferences()));
        // notFound 依据「模型是否回了标准兜底话术」判定，
        // 前端据此渲染「未找到」空态，而不是靠猜测答案内容
        vo.setNotFound(PromptBuilder.NO_ANSWER_TEXT.equals(answer.trim()));
        vo.setRetrievedCount(built.getReferences().size());
        vo.setElapsedMs(System.currentTimeMillis() - start);
        vo.setModel(llmClient.name());
        log.info("[RAG] 回答完成：问题「{}」，召回 {} 块，耗时 {}ms",
                question, built.getReferences().size(), vo.getElapsedMs());
        return vo;
    }

    /**
     * 按相关性阈值过滤检索结果。
     *
     * <p>保留得分不低于「最高分 × 阈值比例」的块。比例配置在
     * {@code rag.relevance-ratio}，默认 0.5。</p>
     *
     * <p>效果：弱相关块（正分但很低）不进上下文，模型少了可拼凑的素材，
     * 幻觉随之减少；上下文变短，token 成本也下降。</p>
     *
     * @param chunks 已按得分降序排列的块
     * @return 过滤后的块
     */
    private List<RetrievedChunk> filterByRelevance(List<RetrievedChunk> chunks) {
        if (chunks.isEmpty()) {
            return chunks;
        }
        double max = chunks.get(0).getScore();
        double threshold = max * properties.getRelevanceRatio();
        List<RetrievedChunk> kept = chunks.stream()
                .filter(c -> c.getScore() >= threshold)
                .collect(java.util.stream.Collectors.toList());
        log.debug("[RAG] 相关性过滤：{} -> {} 块（阈值 {}）", chunks.size(), kept.size(), threshold);
        return kept;
    }

    /**
     * 判断请求是否来自已登录的管理员。
     *
     * <p>解析 Authorization 头里的 JWT，再查用户角色。解析失败一律当作非管理员，
     * 不影响功能——管理员身份只是用于豁免限流，判不出来就按普通访客限流。</p>
     *
     * @param request 请求对象
     * @return 是管理员返回 true
     */
    private boolean isAdmin(HttpServletRequest request) {
        try {
            String header = request.getHeader("Authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                return false;
            }
            String token = header.substring(7).trim();
            Long userId = jwtUtil.getUserIdFromToken(token);
            if (userId == null) {
                return false;
            }
            User user = userService.getById(userId);
            return user != null && "ROLE_ADMIN".equals(user.getRole());
        } catch (Exception e) {
            // 身份识别失败不阻断问答，退回普通访客的限流策略
            return false;
        }
    }

    /**
     * 限流：分钟级 + 天级双层。
     *
     * <p>分钟级防突发刷屏，天级控制大模型调用总成本。</p>
     *
     * <p><b>管理员豁免</b>：博主本人（已登录的 ROLE_ADMIN）不受限流约束。
     * 单用户博客的后台就是博主自己在用，调试与体验时反复提问是常态，
     * 按 IP 限流会把自己挡在门外。</p>
     *
     * @param request 请求对象
     */
    private void checkRateLimit(HttpServletRequest request) {
        if (isAdmin(request)) {
            return;
        }
        String ip = IpUtil.resolveIp(request);
        if (ip == null) {
            return;
        }
        try {
            String minuteKey = "rag:limit:min:" + ip;
            Long minuteCount = redisUtil.increment(minuteKey);
            if (minuteCount != null && minuteCount == 1L) {
                redisUtil.expire(minuteKey, 1, TimeUnit.MINUTES);
            }
            if (minuteCount != null && minuteCount > properties.getRateLimitPerMinute()) {
                throw new RateLimitException("提问过于频繁，请稍后再试");
            }

            String dayKey = "rag:limit:day:" + ip;
            Long dayCount = redisUtil.increment(dayKey);
            if (dayCount != null && dayCount == 1L) {
                redisUtil.expire(dayKey, 24, TimeUnit.HOURS);
            }
            if (dayCount != null && dayCount > properties.getDailyLimit()) {
                throw new RateLimitException("今日提问次数已达上限，请明天再试");
            }
        } catch (RateLimitException e) {
            // 限流是「业务性拒绝」，必须原样抛出去交给控制器映射成 429。
            //
            // 注意 catch 的顺序：RateLimitException 必须在 Exception 之前。
            // 若只写 catch (Exception e)，限流异常会被当成「Redis 故障」一起吞掉，
            // 结果是限流形同虚设 —— 曾经就踩过这个坑：异常被下面的 catch (Exception)
            // 捕获并只打了 WARN，请求照常放行。
            throw e;
        } catch (Exception e) {
            // 真正的 Redis 异常（连不上、命令失败）不阻断问答：
            // 限流不可用比提问失败体验更差，宁可少一层防护也不能让功能整体不可用。
            log.warn("[RAG] 限流检查异常（Redis 故障），本次跳过限流：{}", e.getMessage());
        }
    }

    /**
     * 归一化 topK，限制在 1~10。
     *
     * @param topK 请求值
     * @return 归一化后的值
     */
    private int normalizeTopK(Integer topK) {
        if (topK == null || topK <= 0) {
            return properties.getTopK();
        }
        return Math.min(topK, 10);
    }

    /**
     * 构造「未找到」的回答。
     *
     * @param start  开始时间
     * @param hint   提示语；为 null 时用标准话术
     * @return 响应
     */
    private RagAskVO buildEmptyAnswer(long start, String hint) {
        RagAskVO vo = new RagAskVO();
        vo.setAnswer(hint != null ? hint : PromptBuilder.NO_ANSWER_TEXT);
        vo.setReferences(List.of());
        vo.setNotFound(true);
        vo.setRetrievedCount(0);
        vo.setElapsedMs(System.currentTimeMillis() - start);
        vo.setModel(llmClient.name());
        return vo;
    }

    /**
     * 上下文引用转 VO。
     *
     * @param refs 引用列表
     * @return VO 列表
     */
    private List<RagReferenceVO> toReferenceVOs(List<ContextBuilder.Reference> refs) {
        List<RagReferenceVO> list = new ArrayList<>(refs.size());
        for (ContextBuilder.Reference ref : refs) {
            RagReferenceVO vo = new RagReferenceVO();
            vo.setIndex(ref.getIndex());
            vo.setArticleId(ref.getArticleId());
            vo.setArticleTitle(ref.getArticleTitle());
            vo.setHeadingPath(ref.getHeadingPath());
            vo.setSnippet(ref.getSnippet());
            vo.setScore(ref.getScore());
            list.add(vo);
        }
        return list;
    }
}
