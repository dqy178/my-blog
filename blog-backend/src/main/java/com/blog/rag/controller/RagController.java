package com.blog.rag.controller;

import com.blog.common.RateLimitException;
import com.blog.common.Result;
import com.blog.dto.RagAskDTO;
import com.blog.rag.llm.LlmException;
import com.blog.rag.service.RagAskService;
import com.blog.vo.RagAskVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前台站内知识问答接口。
 *
 * <p>公开访问（读者提问不需要登录），但带 IP 限流 —— 每次问答都会调用大模型，
 * 成本敏感，必须限制调用频率。已登录的管理员豁免限流。</p>
 *
 * <p><b>状态码语义</b>（前端按 code 区分提示文案）：</p>
 * <table border="1">
 *   <caption>状态码</caption>
 *   <tr><th>code</th><th>含义</th></tr>
 *   <tr><td>200</td><td>正常返回答案</td></tr>
 *   <tr><td>400</td><td>参数不合法，或功能未启用</td></tr>
 *   <tr><td>429</td><td>触发限流，提示稍后再试</td></tr>
 *   <tr><td>503</td><td>大模型服务不可用（网络故障、重试耗尽）</td></tr>
 *   <tr><td>500</td><td>其余未预期故障</td></tr>
 * </table>
 *
 * <p>把「大模型挂了」与「参数错了」区分开，是为了让前端能给出准确的提示：
 * 前者值得重试，后者重试也没用。</p>
 */
@Slf4j
@RestController
@RequestMapping("/front/rag")
@RequiredArgsConstructor
public class RagController {

    /** 问答编排服务 */
    private final RagAskService ragAskService;

    /**
     * 提问。
     *
     * @param dto     问题与 topK
     * @param request 请求对象（取客户端 IP 做限流）
     * @return 回答与出处列表
     */
    @PostMapping("/ask")
    public Result<RagAskVO> ask(@Valid @RequestBody RagAskDTO dto, HttpServletRequest request) {
        try {
            RagAskVO vo = ragAskService.ask(dto, request);
            return Result.success(vo);
        } catch (RateLimitException e) {
            // 限流属于客户端侧问题：429，前端可据此提示稍后再试
            return Result.failed(429, e.getMessage());
        } catch (IllegalStateException e) {
            // 功能未启用 / 参数问题 → 400
            return Result.failed(400, e.getMessage());
        } catch (LlmException e) {
            // 大模型不可用（网络故障、超时、连续重试仍失败）→ 503
            log.error("[RAG] 调用大模型失败：{}", e.getMessage());
            return Result.failed(503, "服务暂时不可用，请稍后重试");
        } catch (Exception e) {
            // 其余未知故障 → 500
            log.error("[RAG] 问答失败：{}", e.getMessage(), e);
            return Result.failed(500, "问答服务异常，请稍后重试");
        }
    }
}
