<template>
  <div class="markdown-editor" ref="editorRef">
    <div class="editor-toolbar">
      <div class="toolbar-group">
        <button type="button" class="toolbar-btn" @click="wrapText('## ', '')" title="标题 (H2)">
          <component :is="Edit" />
        </button>
        <button type="button" class="toolbar-btn" @click="wrapText('### ', '')" title="标题 (H3)">
          <component :is="Edit" class="small" />
        </button>
      </div>
      <div class="toolbar-divider" />
      <div class="toolbar-group">
        <button type="button" class="toolbar-btn" @click="wrapText('**', '**')" title="加粗 (Ctrl+B)">
          <component :is="Edit" />
        </button>
        <button type="button" class="toolbar-btn" @click="wrapText('*', '*')" title="斜体 (Ctrl+I)">
          <component :is="Edit" />
        </button>
        <button type="button" class="toolbar-btn" @click="wrapText('~~', '~~')" title="删除线">
          <component :is="Delete" />
        </button>
        <button type="button" class="toolbar-btn" @click="wrapText('`', '`')" title="行内代码">
          <component :is="Monitor" />
        </button>
      </div>
      <div class="toolbar-divider" />
      <div class="toolbar-group">
        <button type="button" class="toolbar-btn" @click="wrapText('\n```\n', '\n```\n')" title="代码块">
          <component :is="Document" />
        </button>
        <button type="button" class="toolbar-btn" @click="wrapText('> ', '')" title="引用">
          <component :is="ChatLineSquare" />
        </button>
      </div>
      <div class="toolbar-divider" />
      <div class="toolbar-group">
        <button type="button" class="toolbar-btn" @click="wrapText('- ', '')" title="无序列表">
          <component :is="List" />
        </button>
        <button type="button" class="toolbar-btn" @click="wrapText('1. ', '')" title="有序列表">
          <component :is="List" />
        </button>
        <button type="button" class="toolbar-btn" @click="wrapText('- [ ] ', '')" title="任务列表">
          <component :is="Check" class="task" />
        </button>
      </div>
      <div class="toolbar-divider" />
      <div class="toolbar-group">
        <button type="button" class="toolbar-btn" @click="wrapText('[', '](url)')" title="链接">
          <component :is="Link" />
        </button>
        <button type="button" class="toolbar-btn" @click="handleImageUpload" title="插入图片 (Ctrl+P)">
          <component :is="Picture" />
        </button>
        <button type="button" class="toolbar-btn" @click="wrapText('\n---\n', '')" title="分割线">
          <component :is="Minus" />
        </button>
        <button type="button" class="toolbar-btn" @click="wrapText('\n| 表头1 | 表头2 |\n| --- | --- |\n| 内容1 | 内容2 |\n', '')" title="表格">
          <component :is="Grid" />
        </button>
      </div>
      <div class="toolbar-divider" />
      <div class="toolbar-group">
        <button type="button" class="toolbar-btn" @click="undo" title="撤销 (Ctrl+Z)" :disabled="!canUndo">
          <component :is="Refresh" />
        </button>
        <button type="button" class="toolbar-btn" @click="redo" title="重做 (Ctrl+Y)" :disabled="!canRedo">
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
          @input="handleInput"
          @keydown="handleKeydown"
          @scroll="handleScroll"
          placeholder="开始写作... (支持 Markdown 语法)"
          spellcheck="false"
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

    <input type="file" ref="imageInput" accept="image/*" style="display: none" @change="onImageSelect" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import markdownIt from 'markdown-it'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'
import DOMPurify from 'dompurify'
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
  FullScreen
} from '@element-plus/icons-vue'

interface Props {
  modelValue: string
}

interface Emits {
  (e: 'update:modelValue', value: string): void
  (e: 'image-upload', file: File): Promise<string>
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const editorRef = ref<HTMLDivElement>()
const textareaRef = ref<HTMLTextAreaElement>()
const imageInput = ref<HTMLInputElement>()

const content = ref(props.modelValue)
const fullscreen = ref(false)
const previewHtml = ref('')

const history = ref<string[]>([])
const historyIndex = ref(-1)
const maxHistory = 50

const md = markdownIt({
  html: true,
  linkify: true,
  typographer: true,
  highlight: (str, lang) => {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return hljs.highlight(str, { language: lang, ignoreIllegals: true }).value
      } catch (__) {}
    }
    return ''
  }
})

const charCount = computed(() => content.value.length)
const lineCount = computed(() => content.value.split('\n').length)
const wordCount = computed(() => content.value.trim() ? content.value.trim().split(/\s+/).length : 0)

const canUndo = computed(() => historyIndex.value > 0)
const canRedo = computed(() => historyIndex.value < history.value.length - 1)

function updatePreview() {
  const html = md.render(content.value)
  previewHtml.value = DOMPurify.sanitize(html)
}

function handleInput() {
  emit('update:modelValue', content.value)
  updatePreview()
  saveToHistory()
}

function saveToHistory() {
  if (historyIndex.value < history.value.length - 1) {
    history.value = history.value.slice(0, historyIndex.value + 1)
  }
  history.value.push(content.value)
  if (history.value.length > maxHistory) {
    history.value.shift()
  } else {
    historyIndex.value++
  }
}

function wrapText(prefix: string, suffix: string) {
  const textarea = textareaRef.value
  if (!textarea) return

  const start = textarea.selectionStart
  const end = textarea.selectionEnd
  const selectedText = content.value.substring(start, end)
  const newText = prefix + selectedText + suffix

  content.value = content.value.substring(0, start) + newText + content.value.substring(end)

  nextTick(() => {
    textarea.focus()
    if (selectedText) {
      textarea.setSelectionRange(start + prefix.length, start + prefix.length + selectedText.length)
    } else {
      textarea.setSelectionRange(start + prefix.length, start + prefix.length)
    }
  })
}

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
    wrapText('\n', '')
  }
}

function handleScroll() {
  const textarea = textareaRef.value
  const preview = editorRef.value?.querySelector('.preview-content') as HTMLElement
  if (textarea && preview) {
    const scrollPercent = textarea.scrollTop / (textarea.scrollHeight - textarea.clientHeight)
    preview.scrollTop = scrollPercent * (preview.scrollHeight - preview.clientHeight)
  }
}

function undo() {
  if (!canUndo.value) return
  historyIndex.value--
  content.value = history.value[historyIndex.value]
  const textarea = textareaRef.value
  if (textarea) {
    nextTick(() => textarea.focus())
  }
}

function redo() {
  if (!canRedo.value) return
  historyIndex.value++
  content.value = history.value[historyIndex.value]
  const textarea = textareaRef.value
  if (textarea) {
    nextTick(() => textarea.focus())
  }
}

function handleImageUpload() {
  imageInput.value?.click()
}

async function onImageSelect(event: Event) {
  const input = event.target as HTMLInputElement
  if (input.files && input.files[0]) {
    const file = input.files[0]
    try {
      const url = await emit('image-upload', file)
      wrapText(`\n![图片](${url})\n`, '')
    } catch (error) {
      console.error('图片上传失败:', error)
    }
    input.value = ''
  }
}

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

watch(() => props.modelValue, (newVal) => {
  if (newVal !== content.value) {
    content.value = newVal
    updatePreview()
  }
})

onMounted(() => {
  history.value = [content.value]
  historyIndex.value = 0
  updatePreview()

  const handlePaste = async (event: ClipboardEvent) => {
    const items = event.clipboardData?.items
    if (!items) return

    for (const item of items) {
      if (item.type.startsWith('image/')) {
        event.preventDefault()
        const file = item.getAsFile()
        if (file) {
          try {
            const url = await emit('image-upload', file)
            wrapText(`\n![图片](${url})\n`, '')
          } catch (error) {
            console.error('粘贴图片上传失败:', error)
          }
        }
      }
    }
  }

  textareaRef.value?.addEventListener('paste', handlePaste)
  window.addEventListener('keydown', handleGlobalKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleGlobalKeydown)
})

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