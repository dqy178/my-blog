package com.blog.utils;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * HTML 白名单净化。
 *
 * <p>前端渲染时已有 DOMPurify 兜底，但净化只发生在浏览器里：
 * 后端存的仍是原始 HTML，任何绕过前端直接调 API 的客户端（脚本、爬虫、
 * 将来的邮件通知或导出功能）拿到的都是未净化的内容，一旦某处新增 v-html
 * 忘了套 safeHtml() 就是存储型 XSS。所以在入库前再净化一层。</p>
 */
@Component
public class HtmlSanitizer {

    /**
     * 允许的标签与属性。
     * <ul>
     *   <li>Safelist.relaxed() 不含 script / style / iframe / form 等危险标签</li>
     *   <li>不含 style 属性与任何 on* 事件属性</li>
     *   <li>a 标签移除 javascript: 协议</li>
     * </ul>
     */
    private static final Safelist SAFELIST = Safelist.relaxed()
            .addTags("img", "pre", "code", "blockquote", "hr", "del", "s", "mark")
            .addAttributes("img", "src", "alt", "title", "width", "height")
            .addProtocols("img", "src", "http", "https", "data")
            .addAttributes("a", "href", "title", "target", "rel")
            .removeProtocols("a", "href", "javascript", "vbscript", "data")
            .addAttributes("code", "class")
            .preserveRelativeLinks(true);

    /** 净化 HTML：移除危险标签与属性，保留白名单内的富文本 */
    public String sanitize(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        return Jsoup.clean(html, SAFELIST);
    }
}
