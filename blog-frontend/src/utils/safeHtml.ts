import DOMPurify from 'dompurify'

/**
 * 对用于 v-html 的字符串做安全净化。
 * 默认只放行安全的行内标签，避免评论 / 搜索关键字回显被注入 <script>。
 */
export function safeHtml(input: string | null | undefined): string {
  if (!input) return ''
  return DOMPurify.sanitize(input, {
    ALLOWED_TAGS: [
      'mark', 'b', 'strong', 'i', 'em', 'u', 'p', 'br', 'span',
      'a', 'img', 'code', 'pre', 'blockquote',
      'ul', 'ol', 'li',
      'h1', 'h2', 'h3', 'h4', 'h5', 'h6'
    ],
    ALLOWED_ATTR: ['href', 'title', 'target', 'rel', 'src', 'alt'],
    ALLOW_DATA_ATTR: false
  })
}
