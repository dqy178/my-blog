package com.blog.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HTML 白名单净化测试。
 *
 * <p>后端入库前净化是 XSS 的一道独立防线（前端 DOMPurify 只在浏览器里生效），
 * 因此这里逐个验证危险构造被拦截、正常富文本被保留。</p>
 */
class HtmlSanitizerTest {

    private final HtmlSanitizer sanitizer = new HtmlSanitizer();

    @Test
    @DisplayName("script 标签应被移除")
    void scriptTag_shouldBeRemoved() {
        String result = sanitizer.sanitize("<script>alert(1)</script>");
        assertFalse(result.contains("<script"), "不应保留 script 标签: " + result);
        assertFalse(result.contains("alert(1)"), "不应保留 script 内容: " + result);
    }

    @Test
    @DisplayName("事件属性 onerror 应被移除")
    void eventAttribute_shouldBeRemoved() {
        String result = sanitizer.sanitize("<img src=x onerror=alert(1)>");
        assertFalse(result.toLowerCase().contains("onerror"), "不应保留 onerror: " + result);
    }

    @Test
    @DisplayName("style 标签与 style 属性应被移除")
    void style_shouldBeRemoved() {
        String tag = sanitizer.sanitize("<style>body{display:none}</style>");
        assertFalse(tag.toLowerCase().contains("<style"), "不应保留 style 标签: " + tag);

        String attr = sanitizer.sanitize("<div style=\"position:fixed;inset:0\">x</div>");
        assertFalse(attr.toLowerCase().contains("style="), "不应保留 style 属性: " + attr);
    }

    @Test
    @DisplayName("iframe / object / form 应被移除")
    void dangerousTags_shouldBeRemoved() {
        for (String html : new String[]{
                "<iframe src=\"http://evil.com\"></iframe>",
                "<object data=\"x\"></object>",
                "<form action=\"x\"><input name=a></form>"}) {
            String result = sanitizer.sanitize(html).toLowerCase();
            assertFalse(result.contains("<iframe"), "不应保留 iframe: " + result);
            assertFalse(result.contains("<object"), "不应保留 object: " + result);
            assertFalse(result.contains("<form"), "不应保留 form: " + result);
        }
    }

    @Test
    @DisplayName("javascript: 协议应被移除")
    void javascriptProtocol_shouldBeRemoved() {
        String result = sanitizer.sanitize("<a href=\"javascript:alert(1)\">click</a>");
        assertFalse(result.toLowerCase().contains("javascript:"), "不应保留 javascript 协议: " + result);
    }

    @Test
    @DisplayName("正常富文本应被保留")
    void safeHtml_shouldBeKept() {
        String html = "<p>hello <strong>world</strong></p><ul><li>a</li></ul>"
                + "<a href=\"https://example.com\" title=\"t\">link</a>"
                + "<img src=\"https://example.com/a.png\" alt=\"img\">";
        String result = sanitizer.sanitize(html);
        assertTrue(result.contains("<strong>"), "应保留 strong: " + result);
        assertTrue(result.contains("<li>"), "应保留 li: " + result);
        assertTrue(result.contains("https://example.com"), "应保留链接: " + result);
        assertTrue(result.contains("example.com/a.png"), "应保留图片: " + result);
    }

    @Test
    @DisplayName("空值与 null 应原样返回")
    void nullAndEmpty_shouldPassThrough() {
        assertNull(sanitizer.sanitize(null));
        assertEquals("", sanitizer.sanitize(""));
    }
}
