<template>
  <div ref="editorRef" class="markdown-editor">
    <div class="editor-toolbar">
      <div class="toolbar-group">
        <button type="button" class="toolbar-btn" title="标题 (H2)" @click="wrapText('## ', '')">
          <component :is="Edit" />
        </button>
        <button type="button" class="toolbar-btn" title="标题 (H3)" @click="wrapText('### ', '')">
          <component :is="Edit" class="small" />
        </button>
      </div>
      <div class="toolbar-divider" />
      <div class="toolbar-group">
        <button type="button" class="toolbar-btn" title="加粗 (Ctrl+B)" @click="wrapText('**', '**')">
          <component :is="Edit" />
        </button>
        <button type="button" class="toolbar-btn" title="斜体 (Ctrl+I)" @click="wrapText('*', '*')">
          <component :is="Edit" />
        </button>
        <button type="button" class="toolbar-btn" title="删除线" @click="wrapText('~~', '~~')">
          <component :is="Delete" />
        </button>
        <button type="button" class="toolbar-btn" title="行内代码" @click="wrapText('`', '`')">
          <component :is="Monitor" />
        </button>
      </div>
      <div class="toolbar-divider" />
      <div class="toolbar-group">
        <button type="button" class="toolbar-btn" title="代码块" @click="wrapText('\n```\n', '\n```\n')">
          <component :is="Document" />
        </button>
        <button type="button" class="toolbar-btn" title="引用" @click="wrapText('> ', '')">
          <component :is="ChatLineSquare" />
        </button>
      </div>
      <div class="toolbar-divider" />
      <div class="toolbar-group">
        <button type="button" class="toolbar-btn" title="无序列表" @click="wrapText('- ', '')">
          <component :is="List" />
        </button>
        <button type="button" class="toolbar-btn" title="有序列表" @click="wrapText('1. ', '')">
          <component :is="List" />
        </button>
        <button type="button" class="toolbar-btn" title="任务列表" @click="wrapText('- [ ] ', '')">
          <component :is="Check" class="task" />
        </button>
      </div>
      <div class="toolbar-divider" />
      <div class="toolbar-group">
        <button type="button" class="toolbar-btn" title="链接" @click="wrapText('[', '](url)')">
          <component :is="Link" />
        </button>
        <button type="button" class="toolbar-btn" title="插入图片 (Ctrl+P)" @click="handleImageUpload">
          <component :is="Picture" />
        </button>
        <button type="button" class="toolbar-btn" title="分割线" @click="wrapText('\n---\n', '')">
          <component :is="Minus" />
        </button>
        <button type="button" class="toolbar-btn" title="表格" @click="wrapText('\n| 表头1 | 表头2 |\n| --- | --- |\n| 内容1 | 内容2 |\n', '')">
          <component :is="Grid" />
        </button>
      </div>
      <div class="toolbar-divider" />
      <div class="toolbar-group">
        <button type="button" class="toolbar-btn" title="撤销 (Ctrl+Z)" :disabled="!canUndo" @click="undo">
          <component :is="Refresh" />
        </button>
        <button type="button" class="toolbar-btn" title="重做 (Ctrl+Y)" :disabled="!canRedo" @click="redo">
          <component :is="Refresh" />
        </button>
      </div>
    </div>

    <div class="editor-container">
      <div class="editor-pane">
        <div class="pane-header">
          <span>编辑</span>
          <el-switch v-model="fullscreen" :active-text="''" :inactive-text="''" @change="toggleFullscreen" />
        </div>
        <textarea
          ref="textareaRef"
          v-model="content"
          class="editor-textarea"
          placeholder="开始写作... (支持 Markdown 语法)"
          spellcheck="false"
          @input="handleInput"
          @keydown="handleKeydown"
          @scroll="handleScroll"
        />
      </div>
      <div class="editor-pane preview-pane">
        <div class="pane-header">
          <span>预览</span>
        </div>
        <div class="preview-content" v-html="previewHtml"></div>
      </div>
    </div>

    <div class="editor-statusbar">
      <span class="status-item">{{ charCount }} 字符</span>
      <span class="status-item">{{ lineCount }} 行</span>
      <span class="status-item">{{ wordCount }} 词</span>
      <div class="status-right">
        <el-tooltip content="全屏模式" placement="top">
          <button type="button" class="status-btn" @click="toggleFullscreen">
            <component :is="fullscreen ? 'FullScreen' : 'FullScreen'" />
          </button>
        </el-tooltip>
      </div>
    </div>

    <input ref="imageInput" type="file" accept="image/*" style="display: none" @change="onImageSelect" />
  </div>
</template>

<script setup lang="ts">
/**
 * 自研 Markdown 编辑器。
 *
 * <p>左右分栏：左侧 textarea 编辑，右侧实时预览。工具栏提供标题、加粗、斜体、
 * 删除线、行内代码、代码块、引用、列表、任务列表、链接、图片、分割线、表格，
 * 以及撤销/重做。</p>
 *
 * <p>两个值得注意的实现细节：</p>
 * <ul>
 *   <li>预览内容必须经 {@link safeHtml} 净化后再交给 v-html，防止 XSS</li>
 *   <li>所有会修改内容的入口都必须调用 {@link emitContent}，
 *       否则父组件拿到的仍是旧值，保存时会丢失工具栏/撤销/图片插入的结果</li>
 * </ul>
 */
import { logError } from '@/utils/logError'
import { ref, computed, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import markdownIt from 'markdown-it'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'
import { safeHtml } from '@/utils/safeHtml'
import { ElMessage } from 'element-plus'
import {
    Edit,
    Delete,
    Monitor,
    ChatLineSquare,
    List,
    Check,
    Link,
    Picture,
    Minus,
    Grid,
    Refresh,
    Document
  } from '@element-plus/icons-vue'

interface Props {
  /** 双向绑定的 Markdown 原文 */
  modelValue: string
  /**
   * 图片上传回调，返回图片可访问的 URL。
   * 用 prop 回调而不是 emit：emit 的返回值依赖父组件 handler 是 async 且能被透传，
   * 属于冷门用法；prop 回调更直观，也让组件可以独立判断「未提供上传能力」。
   */
  imageUpload?: (file: File) => Promise<string>
}

interface Emits {
  (e: 'update:modelValue', value: string): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

/** 编辑器根容器（用于全屏样式与预览区查询） */
const editorRef = ref<HTMLDivElement>()
/** 文本域 */
const textareaRef = ref<HTMLTextAreaElement>()
/** 隐藏的文件选择 input（用于选择本地图片） */
const imageInput = ref<HTMLInputElement>()

/** 当前编辑内容 */
const content = ref(props.modelValue)
/** 是否处于全屏模式 */
const fullscreen = ref(false)
/** 净化后的预览 HTML */
const previewHtml = ref('')

/**
 * 撤销/重做历史栈。
 *
 * <p>存的是完整内容快照而非差量，实现简单、内存占用在可接受范围内（上限 50 条）。</p>
 */
const history = ref<string[]>([])
/** 当前所处的历史位置 */
const historyIndex = ref(-1)
/** 历史栈最大长度，超出后丢弃最早的快照 */
const maxHistory = 50
/** 连续输入的合并窗口：此间隔内的输入会合并成同一个撤销快照 */
const HISTORY_MERGE_INTERVAL = 600
/** 上一次快照的时间戳，用于判断是否处于连续输入窗口内 */
const lastSnapshotTime = ref(0)

/**
 * Markdown 渲染器实例。
 *
 * <p>{@code html: true} 允许正文内嵌 HTML，因此渲染结果必须再经 safeHtml 净化；
 * {@code highlight} 回调交给 highlight.js 做代码高亮。</p>
 */
const md = markdownIt({
  html: true,
  linkify: true,
  typographer: true,
  highlight: (str, lang) => {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return hljs.highlight(str, { language: lang, ignoreIllegals: true }).value
      } catch (__) { /* ignore */ }
    }
    return ''
  }
})

/** 字符数 */
const charCount = computed(() => content.value.length)
/** 行数 */
const lineCount = computed(() => content.value.split('\n').length)
/** 词数（按空白分隔） */
const wordCount = computed(() => content.value.trim() ? content.value.trim().split(/\s+/).length : 0)

/** 是否可撤销 */
const canUndo = computed(() => historyIndex.value > 0)
/** 是否可重做 */
const canRedo = computed(() => historyIndex.value < history.value.length - 1)

/** 重新渲染并净化预览内容 */
function updatePreview() {
  const html = md.render(content.value)
  previewHtml.value = safeHtml(html)
}

/** 监听输入：同步内容 + 记录撤销快照 */
function handleInput() {
  emitContent()
  // 连续输入合并成同一个撤销快照，否则撤销一次只能退一个字
  saveToHistory(true)
}

  /**
   * 记录一次撤销快照。
   *
   * <p>合并条件（三个同时满足）：可合并、上次快照在合并窗口内、
   * 新内容以旧内容为前缀（只追加而不删改）。不满足则新增一个独立历史点。</p>
   *
   * <p>若当前不在历史栈末尾，先截断「redo 分支」再追加，
   * 否则撤销后再编辑会留下悬空的重做链。</p>
   *
   * @param mergeable 是否可与上一个快照合并。
   *        连续打字传 true（合并成一个快照）；工具栏操作传 false（独立快照）。
   */
  function saveToHistory(mergeable = false) {
  const now = Date.now()
  const last = history.value[historyIndex.value]
  if (
    mergeable &&
    last !== undefined &&
    now - lastSnapshotTime.value < HISTORY_MERGE_INTERVAL &&
    content.value.startsWith(last)
  ) {
    // 与上一个快照合并：直接替换，不新增历史点
    history.value[historyIndex.value] = content.value
    lastSnapshotTime.value = now
    return
  }
  if (historyIndex.value < history.value.length - 1) {
    history.value = history.value.slice(0, historyIndex.value + 1)
  }
  history.value.push(content.value)
  if (history.value.length > maxHistory) {
    history.value.shift()
  } else {
    historyIndex.value++
  }
  lastSnapshotTime.value = now
}

/**
 * 把当前内容同步给父组件并刷新预览。
 * 所有会修改 content 的入口都必须调用它，
 * 否则父组件拿到的仍是旧值，保存时会丢失工具栏/撤销/图片插入的结果。
 */
function emitContent() {
  emit('update:modelValue', content.value)
  updatePreview()
}

/**
 * 在选区两侧包裹前缀/后缀，实现加粗、斜体、标题、列表等格式插入。
 *
 * <p>没有选中内容时，光标停在插入点；有选中内容时，选区保持在被包裹的文本上，
 * 方便用户连续操作。</p>
 *
 * @param prefix 前缀，如 `**`
 * @param suffix 后缀，如 `**`
 */
function wrapText(prefix: string, suffix: string) {
  const textarea = textareaRef.value
  if (!textarea) return

  const start = textarea.selectionStart
  const end = textarea.selectionEnd
  const selectedText = content.value.substring(start, end)
  const newText = prefix + selectedText + suffix

  content.value = content.value.substring(0, start) + newText + content.value.substring(end)
  // 工具栏操作是独立的一次修改，不与连续输入合并成同一快照
  emitContent()
  saveToHistory()

  nextTick(() => {
    textarea.focus()
    if (selectedText) {
      textarea.setSelectionRange(start + prefix.length, start + prefix.length + selectedText.length)
    } else {
      textarea.setSelectionRange(start + prefix.length, start + prefix.length)
    }
  })
}

/**
 * 编辑器快捷键处理。
 *
 * <p>Ctrl/Cmd + B 加粗、I 斜体、P 插图、Z 撤销、Y 重做；
 * Tab 插入两空格缩进；Shift+Enter 换行（不插入额外空行）。</p>
 */
function handleKeydown(event: KeyboardEvent) {
  if ((event.ctrlKey || event.metaKey) && event.key === 'b') {
    event.preventDefault()
    wrapText('**', '**')
  } else if ((event.ctrlKey || event.metaKey) && event.key === 'i') {
    event.preventDefault()
    wrapText('*', '*')
  } else if ((event.ctrlKey || event.metaKey) && event.key === 'p') {
    event.preventDefault()
    handleImageUpload()
  } else if ((event.ctrlKey || event.metaKey) && event.key === 'z' && !event.shiftKey) {
    event.preventDefault()
    undo()
  } else if ((event.ctrlKey || event.metaKey) && (event.key === 'y' || (event.key === 'z' && event.shiftKey))) {
    event.preventDefault()
    redo()
  } else if (event.key === 'Tab') {
    event.preventDefault()
    wrapText('  ', '')
  } else if (event.key === 'Enter' && event.shiftKey) {
    // 必须阻止默认行为：textarea 默认已插入一个 \n，
    // 不阻止的话 wrapText 又插一个，一次按键会得到两个换行
    event.preventDefault()
    wrapText('\n', '')
  }
}

/**
 * 编辑区与预览区滚动同步。
 *
 * <p>按滚动百分比换算，而不是直接复制 scrollTop——两栏内容高度不同，
 * 直接赋值会导致滚到一半就到底。</p>
 */
function handleScroll() {
  const textarea = textareaRef.value
  const preview = editorRef.value?.querySelector('.preview-content') as HTMLElement
  if (textarea && preview) {
    const scrollPercent = textarea.scrollTop / (textarea.scrollHeight - textarea.clientHeight)
    preview.scrollTop = scrollPercent * (preview.scrollHeight - preview.clientHeight)
  }
}

/** 撤销：回退到上一个历史快照 */
function undo() {
  if (!canUndo.value) return
  historyIndex.value--
  content.value = history.value[historyIndex.value]
  // 撤销不写历史：否则会把撤销后的内容又存成新快照，破坏撤销栈
  emitContent()
  const textarea = textareaRef.value
  if (textarea) {
    nextTick(() => textarea.focus())
  }
}

/** 重做：前进到下一个历史快照 */
function redo() {
  if (!canRedo.value) return
  historyIndex.value++
  content.value = history.value[historyIndex.value]
  // 同上，redo 也不写历史
  emitContent()
  const textarea = textareaRef.value
  if (textarea) {
    nextTick(() => textarea.focus())
  }
}

/** 打开系统文件选择框选择本地图片 */
function handleImageUpload() {
  imageInput.value?.click()
}

/**
 * 调用父组件提供的图片上传能力，返回图片 URL。
 * 未提供回调时直接抛错，避免静默失败。
 */
async function uploadImage(file: File): Promise<string> {
  if (!props.imageUpload) {
    throw new Error('未提供图片上传回调 (image-upload)')
  }
  return props.imageUpload(file)
}

/**
 * 选择本地图片后上传并插入 Markdown 图片语法。
 */
async function onImageSelect(event: Event) {
  const input = event.target as HTMLInputElement
  if (input.files && input.files[0]) {
    const file = input.files[0]
    try {
      const url = await uploadImage(file)
      wrapText(`\n![图片](${url})\n`, '')
    } catch (error) {
      logError('图片上传失败:', error)
      ElMessage.error('图片上传失败，请重试')
    }
    input.value = ''
  }
}

/**
 * 切换全屏模式。
 *
 * <p>全屏时锁定 body 滚动，避免鼠标滚轮作用在背景页面上。</p>
 */
function toggleFullscreen() {
  fullscreen.value = !fullscreen.value
  if (editorRef.value) {
    if (fullscreen.value) {
      editorRef.value.classList.add('fullscreen')
      document.body.style.overflow = 'hidden'
    } else {
      editorRef.value.classList.remove('fullscreen')
      document.body.style.overflow = ''
    }
  }
}

/**
 * 监听外部内容变化。
 *
 * <p>父组件重置表单（如切换编辑的文章）时同步到本地，
 * 但要避开回显造成的光标跳动：值相同时不做任何处理。</p>
 */
watch(() => props.modelValue, (newVal) => {
  if (newVal !== content.value) {
    content.value = newVal
    updatePreview()
  }
})

/**
 * 初始化：建立首个历史快照、渲染预览、注册粘贴与全局快捷键。
 */
onMounted(() => {
  history.value = [content.value]
  historyIndex.value = 0
  updatePreview()

  /**
   * 粘贴图片时自动上传并插入 Markdown 图片语法。
   *
   * <p>图片不会随剪贴板文本一起带过来，因此需要拦截 paste 事件单独处理。</p>
   */
  const handlePaste = async (event: ClipboardEvent) => {
    const items = event.clipboardData?.items
    if (!items) return

    for (const item of items) {
      if (item.type.startsWith('image/')) {
        event.preventDefault()
        const file = item.getAsFile()
        if (file) {
          try {
            const url = await uploadImage(file)
            wrapText(`\n![图片](${url})\n`, '')
          } catch (error) {
            logError('粘贴图片上传失败:', error)
            ElMessage.error('粘贴图片上传失败，请重试')
          }
        }
      }
    }
  }

  textareaRef.value?.addEventListener('paste', handlePaste)
  window.addEventListener('keydown', handleGlobalKeydown)
})

/**
 * 组件卸载时移除全局快捷键监听。
 */
onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleGlobalKeydown)
})

/**
 * 全屏状态下按 Esc 退出全屏。
 */
function handleGlobalKeydown(event: KeyboardEvent) {
  if (fullscreen.value && event.key === 'Escape') {
    toggleFullscreen()
  }
}
</script>

<style scoped>
.markdown-editor {
  display: flex;
  flex-direction: column;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fff;
  overflow: hidden;
  font-family: inherit;
}

.markdown-editor.fullscreen {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 9999;
  border-radius: 0;
  box-shadow: 0 0 30px rgba(0, 0, 0, 0.3);
}

.editor-toolbar {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  background: #fafafa;
  border-bottom: 1px solid #ebeef5;
  flex-wrap: wrap;
  gap: 4px;
}

.toolbar-group {
  display: flex;
  align-items: center;
  gap: 2px;
}

.toolbar-divider {
  width: 1px;
  height: 24px;
  background: #dcdfe6;
  margin: 0 8px;
}

.toolbar-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 4px;
  color: #606266;
  transition: all 0.2s;
}

.toolbar-btn:hover:not(:disabled) {
  background: #f0f9ff;
  color: #409eff;
}

.toolbar-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.toolbar-btn :deep(svg) {
  width: 16px;
  height: 16px;
}

.toolbar-btn .small :deep(svg) {
  width: 14px;
  height: 14px;
}

.editor-container {
  display: flex;
  flex: 1;
  min-height: 700px;
  overflow: hidden;
}

.markdown-editor.fullscreen .editor-container {
  height: calc(100vh - 100px);
}

.editor-pane {
  flex: 1;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #ebeef5;
  min-width: 0;
}

.pane-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: #fafafa;
  border-bottom: 1px solid #ebeef5;
  font-size: 13px;
  font-weight: 500;
  color: #606266;
}

.editor-textarea {
  flex: 1;
  width: 100%;
  padding: 16px;
  border: none;
  outline: none;
  resize: none;
  font-family: 'SF Mono', 'Fira Code', 'Consolas', monospace;
  font-size: 16px;
  line-height: 1.8;
  color: #303133;
  background: #fff;
}

.editor-textarea::placeholder {
  color: #c0c4cc;
}

.preview-pane {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #fff;
  min-width: 0;
}

.preview-content {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
  font-size: 16px;
  line-height: 1.8;
  color: #303133;
}

.preview-content h1,
.preview-content h2,
.preview-content h3,
.preview-content h4,
.preview-content h5,
.preview-content h6 {
  margin-top: 24px;
  margin-bottom: 12px;
  font-weight: 600;
}

.preview-content h1 { font-size: 32px; border-bottom: 2px solid #ebeef5; padding-bottom: 10px; }
.preview-content h2 { font-size: 26px; border-bottom: 1px solid #ebeef5; padding-bottom: 8px; }
.preview-content h3 { font-size: 22px; }
.preview-content h4 { font-size: 20px; }
.preview-content h5 { font-size: 18px; }
.preview-content h6 { font-size: 16px; color: #909399; }

.preview-content p { margin: 12px 0; }
.preview-content a { color: #409eff; text-decoration: underline; }
.preview-content code {
  font-family: 'SF Mono', 'Fira Code', 'Consolas', monospace;
  background: #f5f7fa;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 0.9em;
}
.preview-content pre {
  background: #2d2d2d;
  border-radius: 8px;
  padding: 16px;
  overflow-x: auto;
  margin: 16px 0;
}
.preview-content pre code {
  background: transparent;
  padding: 0;
  color: #f8f8f2;
}
.preview-content blockquote {
  border-left: 4px solid #409eff;
  padding-left: 16px;
  margin: 16px 0;
  color: #909399;
}
.preview-content ul, .preview-content ol { margin: 12px 0; padding-left: 24px; }
.preview-content li { margin-bottom: 6px; }
.preview-content img { max-width: 100%; border-radius: 8px; margin: 12px 0; }
.preview-content table { width: 100%; border-collapse: collapse; margin: 16px 0; }
.preview-content th, .preview-content td { border: 1px solid #ebeef5; padding: 8px 12px; }
.preview-content th { background: #fafafa; font-weight: 600; }
.preview-content hr { border: none; border-top: 1px solid #ebeef5; margin: 24px 0; }

.editor-statusbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 16px;
  background: #fafafa;
  border-top: 1px solid #ebeef5;
  font-size: 12px;
  color: #909399;
}

.status-item {
  padding: 0 12px;
  border-right: 1px solid #ebeef5;
}

.status-item:last-child {
  border-right: none;
}

.status-right {
  display: flex;
  gap: 8px;
}

.status-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 4px;
  color: #909399;
  transition: all 0.2s;
}

.status-btn:hover {
  background: #f0f9ff;
  color: #409eff;
}
</style>