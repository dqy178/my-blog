import DOMPurify from 'dompurify'

/**
 * 对用于 v-html 的字符串做安全净化。
 *
 * <p>全站统一的净化入口：文章正文、评论、搜索高亮、Markdown 预览都走这里，
 * 避免有的地方用 DOMPurify 默认配置（默认允许 style 属性，可被用来做全页覆盖钓鱼），
 * 有的地方用严格白名单，口径不一致。</p>
 */
export function safeHtml(input: string | null | undefined): string {
  if (!input) return ''
  return DOMPurify.sanitize(input, {
    ALLOWED_TAGS: [
      'mark', 'b', 'strong', 'i', 'em', 'u', 's', 'del', 'p', 'br', 'span', 'div',
      'a', 'img', 'code', 'pre', 'blockquote',
      'ul', 'ol', 'li',
      'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
      'hr',
      // Markdown 表格
      'table', 'thead', 'tbody', 'tfoot', 'tr', 'th', 'td'
    ],
    ALLOWED_ATTR: [
      'href', 'title', 'target', 'rel', 'src', 'alt',
      'width', 'height', 'class', 'colspan', 'rowspan'
    ],
    // 明确禁止 style 属性：DOMPurify 默认配置允许它，
    // 可被用来写 position:fixed 全屏覆盖做钓鱼
    FORBID_ATTR: ['style'],
    ALLOW_DATA_ATTR: false
  })
}
