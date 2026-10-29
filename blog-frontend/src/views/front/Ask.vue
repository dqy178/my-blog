<script setup lang="ts">
/**
 * 站内知识问答页（/ask）。
 *
 * <p>独立的对话式界面：顶部标题、中间消息列表、底部输入框。
 * 每条回答下方展示出处卡片，点击可跳转到对应文章。</p>
 *
 * <p>回答正文里的 {@code [1]} 引用标记会被渲染成可点击的上标，
 * 点击后定位到对应的出处卡片。</p>
 */
import { logError } from '@/utils/logError'
import { ref, nextTick, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Promotion, Delete, ChatDotRound, Position } from '@element-plus/icons-vue'
import { ragApi, RagReferenceVO } from '@/api/rag'
import { safeHtml } from '@/utils/safeHtml'
import markdownIt from 'markdown-it'
import FrontLayout from '@/layouts/FrontLayout.vue'

const router = useRouter()

/**
 * Markdown 渲染器。
 *
 * <p>{@code html: false} 表示不解析正文内嵌的 HTML，模型返回的尖括号会被转义；
 * 最后再过一遍 {@code safeHtml} 兜底。</p>
 */
const md = markdownIt({ html: false, linkify: true, breaks: true })

/** 一条对话消息 */
interface Message {
  /** 角色：user=提问，assistant=回答 */
  role: 'user' | 'assistant'
  /** 消息正文 */
  content: string
  /** 回答对应的出处列表 */
  references?: RagReferenceVO[]
  /** 是否未检索到内容 */
  notFound?: boolean
  /** 耗时（毫秒） */
  elapsedMs?: number
}

/** 消息列表 */
const messages = ref<Message[]>([])
/** 当前输入 */
const input = ref('')
/** 请求进行中 */
const loading = ref(false)
/** 消息列表容器，用于滚动到底部 */
const listRef = ref<HTMLElement>()

/** 会话存储键 */
const STORAGE_KEY = 'rag_messages'
/** 最多保留的消息条数，防止 sessionStorage 无限增长 */
const MAX_MESSAGES = 50

/**
 * 从 sessionStorage 恢复历史对话。
 *
 * <p>对话记录只存在组件内存里的话，刷新页面就会回到未提问状态。
 * 这里落到 sessionStorage：刷新可恢复，关掉标签页自动清空
 * （与本项目 token 的存储策略一致，不做长期留存）。</p>
 */
function restoreMessages() {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return
    const parsed = JSON.parse(raw)
    if (Array.isArray(parsed) && parsed.length > 0) {
      messages.value = parsed
    }
  } catch (e) {
    // 存储内容损坏时直接丢弃，不能让页面因解析失败而白屏
    sessionStorage.removeItem(STORAGE_KEY)
    logError('恢复对话记录失败:', e)
  }
}

/**
 * 把当前对话写入 sessionStorage。
 */
function persistMessages() {
  try {
    if (!messages.value.length) {
      sessionStorage.removeItem(STORAGE_KEY)
      return
    }
    // 只保留最近若干条：问答正文可能很长，无上限会把 sessionStorage 撑爆
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(messages.value.slice(-MAX_MESSAGES)))
  } catch (e) {
    // 存储失败（配额满、隐私模式）不应影响对话本身
    logError('保存对话记录失败:', e)
  }
}

/**
 * 推荐问题。
 *
 * <p>刻意分成两组：一组是知识库里确实有对应文章的主题（能答出来），
 * 另一组是知识库没覆盖的主题（答不出来）。</p>
 *
 * <p>保留第二组不是为了凑数——「资料里没有就说没有」本身就是 RAG 的核心能力，
 * 把它做成显式的入口，用户能直观看到系统在检索不到时如何诚实地回应，
 * 而不是硬凑一个看似有理的错误答案。</p>
 */
const suggestions = {
  /** 知识库有对应文章 */
  answerable: [
    'JWT 双 Token 无感刷新是怎么实现的？',
    'Redis 缓存穿透、击穿、雪崩分别是什么？怎么解决？',
    'Spring Boot 集成 Spring Security 6 有哪些坑？',
    'Docker 镜像怎么瘦身？',
    'MySQL 索引失效有哪些经典场景？',
    'ES 的倒排索引是什么原理？'
  ],
  /** 知识库没有对应文章：用来展示「查不到就说查不到」 */
  unanswerable: [
    '浏览量为什么存数据库不存 Redis？',
    '文章上下篇是怎么实现的？',
    '评论区的验证码和限流是怎么做的？'
  ]
}

/**
 * 渲染回答正文。
 *
 * <p>三步：</p>
 * <ol>
 *   <li>先转义 {@code & < >}，配合 markdown-it 的 {@code html: false} 双重防止 XSS</li>
 *   <li>用 markdown-it 渲染——真实模型通常会返回列表、加粗等 Markdown 语法，
 *       若只做转义会把 {@code **加粗**} 原样显示出来</li>
 *   <li>把 {@code [n]} 引用标记替换成可点击的上标，最后再过一遍 {@code safeHtml} 兜底</li>
 * </ol>
 *
 * @param text 回答正文
 * @returns 渲染后的 HTML
 */
function renderAnswer(text: string): string {
  if (!text) return ''
  const escaped = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  const rendered = md.render(escaped)
  const withCitations = rendered.replace(
    /\[(\d+)]/g,
    '<sup class="cite" data-cite="$1">[$1]</sup>'
  )
  return safeHtml(withCitations)
}

/**
 * 滚动消息列表到底部。
 *
 * <p>先等 DOM 更新（{@code nextTick}）再滚，否则新消息还没渲染，滚动位置不对。</p>
 */
async function scrollToBottom() {
  await nextTick()
  if (listRef.value) {
    listRef.value.scrollTop = listRef.value.scrollHeight
  }
}

/**
 * 发送提问。
 *
 * <p>流程：把用户消息推入列表 → 调接口 → 推入回答 → 写 sessionStorage → 滚到底部。
 * 请求失败时也会推一条 assistant 消息，保证对话流不断、不出现「问完没下文」。</p>
 *
 * @param text 问题内容；为空时用输入框的值
 */
async function send(text?: string) {
  const question = (text ?? input.value).trim()
  if (!question || loading.value) return

  messages.value.push({ role: 'user', content: question })
  input.value = ''
  loading.value = true
  await scrollToBottom()

  try {
    const res = await ragApi.ask({ question })
    messages.value.push({
      role: 'assistant',
      content: res.answer,
      references: res.references,
      notFound: res.notFound,
      elapsedMs: res.elapsedMs
    })
    persistMessages()
  } catch (error) {
    logError('提问失败:', error)
    const msg = error instanceof Error ? error.message : '提问失败，请稍后重试'
    messages.value.push({ role: 'assistant', content: msg, references: [] })
    ElMessage.error(msg)
  } finally {
    loading.value = false
    await scrollToBottom()
  }
}

/**
 * 点击回答中的引用标记，滚动定位到对应的出处卡片。
 *
 * @param index 引用编号
 */
function scrollToReference(index: number) {
  const target = document.querySelector(`[data-ref-index="${index}"]`)
  if (target) {
    target.scrollIntoView({ behavior: 'smooth', block: 'center' })
    target.classList.add('highlight')
    setTimeout(() => target.classList.remove('highlight'), 1500)
  }
}

/**
 * 跳转到文章详情。
 *
 * @param articleId 文章 ID
 */
function goArticle(articleId: number) {
  router.push(`/article/${articleId}`)
}

/**
 * 清空对话。
 *
 * <p>同时清掉 sessionStorage 里的记录，否则刷新后旧对话又会回来。</p>
 */
function clearMessages() {
  messages.value = []
  persistMessages()
}

/** 初始化：恢复历史对话并滚动到底部 */
onMounted(() => {
  restoreMessages()
  scrollToBottom()
})
</script>

<template>
  <FrontLayout>
    <div class="ask-page">
      <div class="ask-container">
        <!-- 顶部标题 -->
        <header class="ask-header">
          <div class="header-left">
            <el-icon class="header-icon"><ChatDotRound /></el-icon>
            <div>
              <h1 class="header-title">站内知识问答</h1>
              <p class="header-desc">基于本博客已发布的文章回答问题，回答均标注出处</p>
            </div>
          </div>
          <el-button v-if="messages.length" text @click="clearMessages">
            <el-icon><Delete /></el-icon>
            清空
          </el-button>
        </header>

        <!-- 消息列表 -->
        <div ref="listRef" class="message-list">
          <!-- 空态：常用问题 -->
          <div v-if="!messages.length" class="empty-state">
            <el-icon class="empty-icon"><ChatDotRound /></el-icon>
            <p class="empty-title">试着问点什么</p>
            <p class="empty-desc">我会先检索博客文章，再基于检索到的内容作答</p>

            <!-- 第一组：知识库有对应文章 -->
            <div class="suggestion-group">
              <p class="group-title">这些能查到资料</p>
              <div class="suggestions">
                <el-button
                  v-for="s in suggestions.answerable"
                  :key="s"
                  plain
                  size="small"
                  @click="send(s)"
                >
                  {{ s }}
                </el-button>
              </div>
            </div>

            <!-- 第二组：知识库没有对应文章，展示「查不到就说查不到」 -->
            <div class="suggestion-group">
              <p class="group-title">这些博客里没涉及</p>
              <div class="suggestions">
                <el-button
                  v-for="s in suggestions.unanswerable"
                  :key="s"
                  plain
                  size="small"
                  class="ghost"
                  @click="send(s)"
                >
                  {{ s }}
                </el-button>
              </div>
            </div>
          </div>

          <!-- 对话消息 -->
          <div
            v-for="(msg, index) in messages"
            :key="index"
            class="message-item"
            :class="msg.role"
          >
            <div v-if="msg.role === 'user'" class="bubble">
              {{ msg.content }}
            </div>
            <div v-else class="bubble assistant">
              <!-- 回答正文，引用标记可点击 -->
              <div
                class="answer-body"
                @click="(e: any) => {
                  const t = e.target as HTMLElement
                  const cite = t.closest('[data-cite]')
                  if (cite) scrollToReference(Number(cite.getAttribute('data-cite')))
                }"
                v-html="renderAnswer(msg.content)"
              ></div>

              <!-- 未找到时的提示 -->
              <div v-if="msg.notFound" class="not-found-hint">
                这个问题在现有文章中没有找到相关内容
              </div>

              <!-- 出处卡片 -->
              <div v-if="msg.references && msg.references.length" class="references">
                <div class="ref-title">
                  <el-icon><Position /></el-icon>
                  参考来源
                </div>
                <div
                  v-for="reference in msg.references"
                  :key="reference.index"
                  class="ref-card"
                  :data-ref-index="reference.index"
                  @click="goArticle(reference.articleId)"
                >
                  <div class="ref-head">
                    <span class="ref-index">[{{ reference.index }}]</span>
                    <span class="ref-article">{{ reference.articleTitle }}</span>
                  </div>
                  <div v-if="reference.headingPath" class="ref-path">{{ reference.headingPath }}</div>
                  <div class="ref-snippet">{{ reference.snippet }}</div>
                </div>
              </div>

              <!-- 耗时 -->
              <div v-if="msg.elapsedMs" class="elapsed">耗时 {{ msg.elapsedMs }} ms</div>
            </div>
          </div>

          <!-- 加载中 -->
          <div v-if="loading" class="message-item assistant">
            <div class="bubble assistant loading">
              <span class="dot"></span><span class="dot"></span><span class="dot"></span>
              正在检索文章并组织回答…
            </div>
          </div>
        </div>

        <!-- 输入区 -->
        <footer class="ask-input-area">
          <div class="input-box">
            <el-input
              v-model="input"
              type="textarea"
              :rows="2"
              maxlength="500"
              show-word-limit
              resize="none"
              placeholder="输入你的问题，Enter 发送，Shift+Enter 换行"
              @keydown.enter.exact.prevent="send()"
            />
            <el-button
              type="primary"
              :loading="loading"
              :disabled="!input.trim()"
              class="send-btn"
              @click="send()"
            >
              <el-icon><Promotion /></el-icon>
              发送
            </el-button>
          </div>
          <p class="input-tip">回答由大模型基于检索到的文章内容生成，可能存在偏差，请点击出处核对原文</p>
        </footer>
      </div>
    </div>
  </FrontLayout>
</template>

<style scoped>
.ask-page {
  /* 高度由父级（FrontLayout 的 main）分配，不再用固定的 calc(100vh - 60px)：
     那样会漏算 header 与 footer 的实际高度，导致整页溢出。 */
  flex: 1;
  min-height: 0;
  width: 100%;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  background: var(--bg-color, #f5f7fa);
}

.ask-container {
  max-width: 900px;
  width: 100%;
  margin: 0 auto;
  padding: 24px 20px 32px;
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  box-sizing: border-box;
}

/* 顶部标题 */
.ask-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  margin-bottom: 8px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-icon {
  font-size: 28px;
  color: var(--el-color-primary);
}

.header-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.header-desc {
  margin: 2px 0 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

/* 消息列表 */
.message-list {
  flex: 1;
  /* 关键：flex item 的 min-height 默认是 auto，意味着它不肯收缩到小于内容高度。
     消息变多时列表会撑破容器，把底部输入区挤扁（表现为「提问后输入框变窄」）。
     显式设为 0 后，列表超出部分转为自身滚动，输入区高度保持不变。 */
  min-height: 0;
  width: 100%;
  overflow-y: auto;
  padding: 16px 4px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

/* 空态 */
.empty-state {
  margin: auto;
  text-align: center;
  color: var(--el-text-color-secondary);
}

.empty-icon {
  font-size: 46px;
  color: var(--el-color-primary);
  opacity: 0.55;
}

.empty-title {
  margin: 12px 0 4px;
  font-size: 16px;
  color: var(--el-text-color-primary);
}

.empty-desc {
  margin: 0 0 18px;
  font-size: 13px;
}

.suggestions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
}

/* 推荐问题的两个分组 */
.suggestion-group {
  margin-top: 14px;
}

.group-title {
  margin: 0 0 8px;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

/* 「知识库没写过」那组用更淡的边框，与上一组区分开 */
.suggestions .ghost {
  border-style: dashed;
  color: var(--el-text-color-secondary);
}

/* 消息气泡 */
.message-item {
  display: flex;
  width: 100%;
}

.message-item.user {
  justify-content: flex-end;
}

.bubble {
  max-width: 78%;
  /* 允许在 flex 容器内收缩到内容以下，长文本才会正常换行而不是撑出横向滚动条 */
  min-width: 0;
  padding: 11px 15px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
  white-space: pre-wrap;
}

/* 用户气泡。
   注意选择器：模板里 user 类加在父级 .message-item 上，
   气泡自身的 class 只有 "bubble"，所以不能用 .bubble.user（匹配不上）。 */
.message-item.user .bubble {
  background: var(--el-color-primary);
  color: #fff;
  border-bottom-right-radius: 4px;
}

.bubble.assistant {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  color: var(--el-text-color-primary);
  border-bottom-left-radius: 4px;
  max-width: 88%;
  /* 宽度下限：短回答（如「未找到相关内容」）不至于缩成一小条；
     上限封顶：长回答最多占 88%，右侧留白。
     不用固定 width —— 那样每条回答都是同样宽，视觉上像表格。 */
  min-width: 46%;
  /* 回答正文是 markdown 渲染后的 HTML，不能用 pre-wrap，否则段落之间会多出空行 */
  white-space: normal;
}

/* 回答正文的 markdown 排版 */
.answer-body :deep(p) {
  margin: 0 0 8px;
}

.answer-body :deep(p:last-child) {
  margin-bottom: 0;
}

.answer-body :deep(ul),
.answer-body :deep(ol) {
  margin: 6px 0;
  padding-left: 22px;
}

.answer-body :deep(li) {
  margin: 3px 0;
}

.answer-body :deep(code) {
  padding: 1px 5px;
  border-radius: 4px;
  background: var(--el-fill-color);
  font-size: 12px;
  font-family: Consolas, Monaco, monospace;
}

.answer-body :deep(pre) {
  margin: 8px 0;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--el-fill-color);
  overflow-x: auto;
}

.answer-body :deep(pre code) {
  padding: 0;
  background: transparent;
}

.answer-body :deep(blockquote) {
  margin: 8px 0;
  padding: 2px 12px;
  border-left: 3px solid var(--el-color-primary);
  color: var(--el-text-color-secondary);
}

.answer-body :deep(strong) {
  font-weight: 600;
}

.bubble.loading {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--el-text-color-secondary);
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--el-color-primary);
  animation: blink 1.2s infinite ease-in-out;
}

.dot:nth-child(2) { animation-delay: 0.2s; }
.dot:nth-child(3) { animation-delay: 0.4s; }

@keyframes blink {
  0%, 80%, 100% { opacity: 0.25; }
  40% { opacity: 1; }
}

/* 回答正文里的引用标记 */
.answer-body :deep(.cite) {
  color: var(--el-color-primary);
  cursor: pointer;
  font-size: 11px;
  vertical-align: super;
  padding: 0 2px;
}

.answer-body :deep(.cite:hover) {
  text-decoration: underline;
}

/* 未找到提示 */
.not-found-hint {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-color-warning);
}

/* 出处卡片 */
.references {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed var(--el-border-color-lighter);
}

.ref-title {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 8px;
}

.ref-card {
  padding: 9px 12px;
  margin-bottom: 8px;
  border-radius: 8px;
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter);
  cursor: pointer;
  transition: all 0.2s;
}

.ref-card:hover {
  border-color: var(--el-color-primary);
  transform: translateX(2px);
}

.ref-card.highlight {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.ref-head {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.ref-index {
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}

.ref-article {
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.ref-path {
  margin-top: 2px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.ref-snippet {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}

/* 耗时 */
.elapsed {
  margin-top: 6px;
  font-size: 11px;
  color: var(--el-text-color-placeholder);
}

/* 输入区 */
.ask-input-area {
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.input-box {
  display: flex;
  gap: 10px;
  align-items: flex-end;
}

.send-btn {
  flex-shrink: 0;
  height: 62px;
}

.input-tip {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  text-align: center;
}

@media (max-width: 768px) {
  .bubble.assistant {
    max-width: 96%;
  }
  .ask-container {
    padding: 16px 12px 20px;
  }
}
</style>
