package com.blog.rag.service;

import com.blog.common.RateLimitException;
import com.blog.config.RagProperties;
import com.blog.dto.RagAskDTO;
import com.blog.entity.User;
import com.blog.rag.chunk.RetrievedChunk;
import com.blog.rag.llm.LlmClient;
import com.blog.rag.retrieval.ChunkRetriever;
import com.blog.service.UserService;
import com.blog.utils.JwtUtil;
import com.blog.utils.RedisUtil;
import com.blog.vo.RagAskVO;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 问答编排服务（RagAskService）的单元测试。
 *
 * <p>重点覆盖<b>限流异常必须原样抛出</b>这一点。</p>
 *
 * <p>这里曾经踩过一个坑：把限流异常从 {@code IllegalStateException} 换成
 * {@code RateLimitException} 之后，{@code checkRateLimit} 里的
 * {@code catch (IllegalStateException e)} 匹配不上了，异常落到兜底的
 * {@code catch (Exception e)} 里被当成「Redis 故障」吞掉 —— 限流因此完全失效，
 * 而且只在日志里留下一行 WARN，不写测试就发现不了。</p>
 */
@ExtendWith(MockitoExtension.class)
class RagAskServiceTest {

    @Mock
    private ChunkRetriever retriever;
    @Mock
    private QueryPreprocessor preprocessor;
    @Mock
    private ContextBuilder contextBuilder;
    @Mock
    private PromptBuilder promptBuilder;
    @Mock
    private LlmClient llmClient;
    @Mock
    private RedisUtil redisUtil;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private UserService userService;

    private RagProperties properties;
    private RagAskService ragAskService;

    @BeforeEach
    void setUp() {
        properties = new RagProperties();
        ragAskService = new RagAskService(retriever, preprocessor, contextBuilder, promptBuilder,
                llmClient, redisUtil, properties, jwtUtil, userService);
    }

    /**
     * 造一个来自指定 IP 的访客请求。
     *
     * @param ip 客户端 IP
     * @return 请求对象
     */
    private HttpServletRequest visitorRequest(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(ip);
        return request;
    }

    /**
     * 造一个带 Bearer token 的请求。
     *
     * @param token JWT 字符串
     * @return 请求对象
     */
    private HttpServletRequest adminRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        request.setRemoteAddr("10.0.0.1");
        return request;
    }

    /**
     * 打桩检索与模型调用，让问答能正常走完（用于「不该被限流」的场景）。
     */
    private void stubSuccessfulRetrieval() {
        when(preprocessor.process(anyString())).thenReturn(Set.of("缓存"));
        RetrievedChunk chunk = new RetrievedChunk();
        chunk.setArticleId(1L);
        chunk.setScore(10.0);
        chunk.setContent("缓存穿透是指请求不存在的数据");
        when(retriever.retrieveByTerms(any(), anyInt())).thenReturn(List.of(chunk));

        ContextBuilder.BuiltContext built = new ContextBuilder.BuiltContext(
                "[1] 《测试》\n缓存穿透是指请求不存在的数据", List.of());
        when(contextBuilder.build(any(), anyInt())).thenReturn(built);

        when(promptBuilder.buildSystemPrompt(anyString())).thenReturn("sys");
        when(promptBuilder.build(anyString(), anyString(), anyString())).thenReturn("user");
        when(llmClient.chat(anyString(), anyString())).thenReturn("缓存穿透是请求不存在的数据 [1]");
    }

    @Test
    @DisplayName("超过分钟级限额时应抛出限流异常，而不是被当成 Redis 故障吞掉")
    void ask_exceedingMinuteLimit_shouldThrowRateLimitException() {
        properties.setRateLimitPerMinute(5);
        // 计数已超过阈值
        when(redisUtil.increment(anyString())).thenReturn(6L);

        RateLimitException e = assertThrows(RateLimitException.class,
                () -> ragAskService.ask(new RagAskDTO(), visitorRequest("10.0.0.9")));

        assertEquals("提问过于频繁，请稍后再试", e.getMessage());
        // 关键：一旦判定限流，就不能再走到「调模型」这一步
        verify(llmClient, never()).chat(anyString(), anyString());
    }

    @Test
    @DisplayName("超过天级限额时应抛出限流异常")
    void ask_exceedingDailyLimit_shouldThrowRateLimitException() {
        properties.setRateLimitPerMinute(100);
        properties.setDailyLimit(10);
        // 第一次调用是分钟级计数（未超），第二次是天级计数（超）
        when(redisUtil.increment(anyString())).thenReturn(1L, 11L);

        RateLimitException e = assertThrows(RateLimitException.class,
                () -> ragAskService.ask(new RagAskDTO(), visitorRequest("10.0.0.9")));

        assertEquals("今日提问次数已达上限，请明天再试", e.getMessage());
    }

    @Test
    @DisplayName("Redis 故障时应跳过限流而不是让问答失败")
    void ask_redisFailure_shouldSkipRateLimitAndContinue() {
        properties.setRateLimitPerMinute(5);
        // Redis 抛异常
        when(redisUtil.increment(anyString())).thenThrow(new RuntimeException("Redis 连不上"));
        stubSuccessfulRetrieval();

        RagAskDTO dto = new RagAskDTO();
        dto.setQuestion("缓存穿透怎么解决");

        // 关键：不抛异常，答案照常返回
        RagAskVO vo = ragAskService.ask(dto, visitorRequest("10.0.0.9"));
        assertEquals("缓存穿透是请求不存在的数据 [1]", vo.getAnswer());
    }

    @Test
    @DisplayName("管理员应豁免限流，且完全不触碰 Redis 计数器")
    void ask_admin_shouldBypassRateLimit() {
        properties.setRateLimitPerMinute(1);

        // 模拟管理员身份
        when(jwtUtil.getUserIdFromToken(eq("valid-token"))).thenReturn(1L);
        User admin = new User();
        admin.setId(1L);
        admin.setRole("ROLE_ADMIN");
        when(userService.getById(eq(1L))).thenReturn(admin);
        stubSuccessfulRetrieval();

        RagAskDTO dto = new RagAskDTO();
        dto.setQuestion("缓存穿透怎么解决");

        // 限流阈值只有 1，但管理员不受限：不应抛异常
        assertDoesNotThrow(() -> ragAskService.ask(dto, adminRequest("valid-token")));
        // 管理员路径下根本不该去操作 Redis 计数器
        verify(redisUtil, never()).increment(anyString());
    }

    @Test
    @DisplayName("功能关闭时应直接拒绝")
    void ask_disabled_shouldThrow() {
        properties.setEnabled(false);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> ragAskService.ask(new RagAskDTO(), visitorRequest("10.0.0.9")));
        assertTrue(e.getMessage().contains("未启用"));
    }

    @Test
    @DisplayName("检索不到内容时应返回「未找到」而不是调用模型")
    void ask_noRetrievedChunks_shouldReturnNotFound() {
        when(preprocessor.process(anyString())).thenReturn(Set.of("缓存"));
        when(retriever.retrieveByTerms(any(), anyInt())).thenReturn(List.of());

        RagAskDTO dto = new RagAskDTO();
        dto.setQuestion("缓存穿透怎么解决");

        RagAskVO vo = ragAskService.ask(dto, visitorRequest("10.0.0.9"));

        assertTrue(vo.getNotFound(), "应标记为未找到");
        assertEquals(0, vo.getRetrievedCount());
        verify(llmClient, never()).chat(anyString(), anyString());
    }
}
