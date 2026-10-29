package com.blog.rag.service;

import com.blog.rag.chunk.ChineseTokenizer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 查询预处理：把用户提问加工成检索用的关键词集合。
 *
 * <p>做三件事：</p>
 * <ol>
 *   <li>抽出有效词元（分词 + 去停用词）</li>
 *   <li>识别并剔除「问题性词汇」——「怎么」「如何」「是什么」这类词在所有文章里都高频出现，
 *       保留它们只会稀释 BM25 的区分度</li>
 *   <li>补充关键词的 bigram 变体，提升召回</li>
 * </ol>
 */
@Component
public class QueryPreprocessor {

    /** 疑问词与泛用词：出现频率高但检索价值低 */
    private static final Set<String> QUESTION_WORDS = new LinkedHashSet<>(List.of(
            "怎么", "如何", "什么", "为什么", "哪些", "是否", "可以", "请问", "介绍",
            "使用", "用法", "怎么用", "是什么", "什么是", "吗", "呢", "吧", "啊",
            "how", "what", "why", "which", "can", "could", "please"
    ));

    private final ChineseTokenizer tokenizer = new ChineseTokenizer();

    /**
     * 技术领域同义词表：解决关键词检索的「词汇鸿沟」问题。
     *
     * <p><b>为什么需要它</b>：纯关键词检索要求「问题用词」与「文章用词」一致。
     * 实测中用户问「项目的鉴权是怎么做的」，而文章里写的是「JWT / 登录 / Token」，
     * 并不出现「鉴权」二字 —— 于是讲 JWT 的文章被判为不相关，
     * 反而召回了顺带提过一句「鉴权状态」的 Pinia 文章，模型据此编出了错误答案。</p>
     *
     * <p>这类「用户问法 ≠ 文章写法」的鸿沟，纯关键词检索无能为力，
     * 向量检索能缓解但需要 Embedding 模型与向量库。本阶段用**同义词扩展**兜住：
     * 检索时把「鉴权」扩展为「认证 / JWT / Token / 登录 / 权限」一起参与打分。</p>
     *
     * <p>扩展词是噪声的担忧由 BM25 的 IDF 自行缓解：
     * 「认证」「登录」在语料里高频出现，IDF 低，贡献自然被压制。</p>
     */
    private static final Map<String, List<String>> SYNONYMS = new LinkedHashMap<>();

    static {
        // 鉴权 / 认证 / 登录 是一组，文章与提问的用词差异最大
        synonyms("鉴权", "认证", "jwt", "token", "登录", "权限", "security", "身份", "刷新");
        synonyms("认证", "鉴权", "jwt", "token", "登录", "权限");
        synonyms("登录", "鉴权", "认证", "token", "jwt");
        synonyms("权限", "鉴权", "认证", "role", "角色");
        synonyms("缓存", "redis", "cache", "穿透", "击穿", "雪崩", "预热");
        synonyms("穿透", "缓存", "redis", "布隆", "空值");
        synonyms("索引", "index", "b+树", "最左", "回表", "失效");
        synonyms("跨域", "cors", "代理", "nginx");
        synonyms("容器", "docker", "镜像", "compose");
        synonyms("部署", "docker", "compose", "nginx", "镜像", "ci");
        synonyms("镜像", "docker", "多阶段", "瘦身", "体积");
        synonyms("异常", "exception", "全局异常", "错误处理");
        synonyms("事务", "transaction", "回滚", "隔离");
        synonyms("分页", "page", "pageable", "limit");
        synonyms("并发", "线程", "锁", "lock", "同步");
        synonyms("线程池", "线程", "异步", "async", "executor");
        synonyms("垃圾回收", "jvm", "gc", "内存");
        synonyms("vue", "vue3", "组合式", "composable", "组件");
        synonyms("路由", "router", "守卫", "guard", "导航");
        synonyms("状态管理", "pinia", "store", "vuex");
    }

    /**
     * 注册一组同义词（双向）。
     *
     * <p>值一律用 {@link ArrayList} 而不是 {@code List.of()}：
     * 同义词表是双向累积的，后续 {@code add} 会往里追加，
     * 用不可变列表会抛 {@code UnsupportedOperationException}。</p>
     *
     * @param key    主词
     * @param values 同义词
     */
    private static void synonyms(String key, String... values) {
        SYNONYMS.put(key.toLowerCase(), new ArrayList<>(List.of(values)));
        for (String v : values) {
            List<String> list = SYNONYMS.computeIfAbsent(v.toLowerCase(), k -> new ArrayList<>());
            if (!list.contains(key)) {
                list.add(key);
            }
        }
    }

    /**
     * 处理查询，产出用于检索的关键词。
     *
     * <p>做四件事：</p>
     * <ol>
     *   <li>抽出有效词元（分词 + 去停用词）</li>
     *   <li>剔除「疑问性词汇」——「怎么」「如何」「是什么」这类词在所有文章里都高频出现，
     *       保留它们只会稀释 BM25 的区分度</li>
     *   <li><b>有双字词时丢弃单字</b>：单字词元过于宽泛（「天」「气」「样」几乎任何中文块
     *       都能命中），BM25 对高频单字的 IDF 本就很低，留着只会引入噪声召回；
     *       但当查询本身就很短（只有单字，如「锁」）时，仍保留单字以免查不到任何东西</li>
     *   <li><b>同义词扩展</b>：补上同义表述，缓解「用户问法 ≠ 文章写法」的词汇鸿沟</li>
     * </ol>
     *
     * @param query 用户原始提问
     * @return 关键词集合（保持顺序）
     */
    public Set<String> process(String query) {
        Set<String> result = new LinkedHashSet<>();
        if (query == null || query.isBlank()) {
            return result;
        }

        List<String> candidates = new ArrayList<>();
        for (String term : tokenizer.tokenize(query)) {
            if (QUESTION_WORDS.contains(term)) {
                continue;
            }
            candidates.add(term);
        }

        boolean hasMultiChar = candidates.stream().anyMatch(t -> t.length() >= 2);
        for (String term : candidates) {
            if (hasMultiChar && term.length() < 2) {
                // 已有双字词时，单字词元只贡献噪声
                continue;
            }
            result.add(term);
        }

        // 同义词扩展：把同义表述一起加入检索词
        Set<String> expanded = new LinkedHashSet<>(result);
        for (String term : result) {
            List<String> synonyms = SYNONYMS.get(term.toLowerCase());
            if (synonyms != null) {
                expanded.addAll(synonyms);
            }
        }
        return expanded;
    }

    /**
     * 判断是否为疑问词集合。
     *
     * @param terms 词集合
     * @return 全部是疑问词返回 true
     */
    public boolean isAllQuestionWords(Set<String> terms) {
        return !terms.isEmpty() && QUESTION_WORDS.containsAll(terms);
    }
}
