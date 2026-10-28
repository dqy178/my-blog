<template>
  <div class="comment-item" :class="{ 'has-reply': level > 0 }" :style="{ marginLeft: level > 0 ? '48px' : '0' }">
    <div class="comment-main">
      <div class="comment-body">
        <div class="comment-header">
          <span class="comment-author">
            <span v-if="comment.isAdmin" class="admin-badge">博主</span>
            {{ comment.nickname }}
          </span>
          <span class="comment-time">{{ formatTime(comment.createTime) }}</span>
          <span v-if="level === 0" class="comment-floor">#{{ (index ?? 0) + 1 }}</span>
        </div>
        <div class="comment-content" v-html="safeContent"></div>
        <div class="comment-actions">
          <button class="action-btn" @click="handleReply">
            <component :is="ChatDotRound" class="action-icon" />
            回复
          </button>
        </div>
      </div>
    </div>

    <!-- 内联回复表单：在被回复的评论下方展开。
         提交仍走统一的评论接口，验证码由评论区通过 provide/inject 下发，
         因此不会成为绕过验证码与频率限制的缺口。 -->
    <transition name="reply-form">
      <div v-if="showReplyForm" class="reply-form">
        <el-form ref="replyFormRef" :model="replyForm" :rules="replyRules" label-width="80px">
          <template v-if="needCaptcha">
            <el-form-item label="昵称" prop="nickname">
              <el-input v-model="replyForm.nickname" placeholder="请输入昵称" style="width: 100%" />
            </el-form-item>
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="replyForm.email" placeholder="请输入邮箱" style="width: 100%" />
            </el-form-item>
          </template>
          <el-form-item label="内容" prop="content">
            <el-input
              v-model="replyForm.content"
              type="textarea"
              :rows="3"
              placeholder="写下你的回复..."
              @keydown.enter.exact="submitReply"
            />
          </el-form-item>
          <el-form-item v-if="needCaptcha" prop="captcha">
            <div class="captcha-row">
              <el-input
                v-model="captchaState.answer"
                placeholder="请输入计算结果"
                style="width: 140px"
              />
              <span class="captcha-question">{{ captchaState.question }}</span>
              <el-button link type="primary" @click="refreshCaptcha">换一题</el-button>
            </div>
          </el-form-item>
          <el-form-item>
            <el-button size="small" type="primary" :loading="replySubmitting" @click="submitReply">
              回复
            </el-button>
            <el-button size="small" @click="cancelReply">取消</el-button>
          </el-form-item>
        </el-form>
      </div>
    </transition>

    <div v-if="comment.children && comment.children.length > 0" class="comment-children">
      <CommentItem
        v-for="(child, index) in comment.children"
        :key="child.id"
        :comment="child"
        :level="level + 1"
        :index="index"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, inject, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage, ElForm } from 'element-plus'
import { ChatDotRound } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { commentApi, CommentVO, CommentDTO } from '@/api/comment'
import { safeHtml } from '@/utils/safeHtml'
import { logError } from '@/utils/logError'
import { COMMENT_CAPTCHA_KEY, CommentCaptchaContext } from './commentCaptcha'

interface Props {
  comment: CommentVO
  level: number
  index?: number
}

const props = defineProps<Props>()
// 只声明 refresh 一个事件。
// 早期版本曾有 reply 事件（「点击回复 → 通知父级 → 父级统一显示回复框」），
// 现已改为回复表单在对应评论下方内联展开，回复逻辑完全自治在本组件内，
// reply 事件再无触发源，随之移除其声明与子组件转发。
const emit = defineEmits<{ refresh: [] }>()

const userStore = useUserStore()
const safeContent = computed(() => safeHtml(props.comment.content))

// 验证码由评论区统一持有并下发，两处表单共用同一份状态
const captchaCtx = inject<CommentCaptchaContext | null>(COMMENT_CAPTCHA_KEY, null)
const needCaptcha = computed(() => captchaCtx?.required ?? false)
const captchaState = captchaCtx?.state ?? { id: '', question: '', answer: '' }
const refreshCaptcha = () => captchaCtx?.refresh()

const showReplyForm = ref(false)
const replySubmitting = ref(false)

const replyForm = reactive<CommentDTO>({
  articleId: props.comment.articleId,
  parentId: props.comment.id,
  content: ''
})

const replyRules = {
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  content: [
    { required: true, message: '请输入回复内容', trigger: 'change' },
    { min: 1, max: 1000, message: '回复长度在 1 到 1000 字符', trigger: 'change' }
  ],
  captcha: [
    {
      // 答案存在共享的 captchaState 里，replyForm 并无 captcha 字段，
      // 用 required 校验 model.captcha 会恒为 undefined，导致永远提示「请输入验证码」。
      validator: (_rule: any, _value: any, callback: any) => {
        if (!captchaState.answer || !captchaState.answer.trim()) {
          callback(new Error('请输入验证码'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

const replyFormRef = ref<InstanceType<typeof ElForm>>()

// 验证码答案一变（用户输入，或主楼提交失败/换题后被清空），
// 就要清掉残留的红色「请输入验证码」提示。
watch(
  () => captchaState.answer,
  () => {
    replyFormRef.value?.clearValidate('captcha')
  }
)

/**
 * 展开 / 收起内联回复框。
 */
function handleReply() {
  if (showReplyForm.value) {
    cancelReply()
    return
  }
  replyForm.content = ''
  replyForm.nickname = userStore.isLoggedIn ? (userStore.userInfo?.nickname || '') : ''
  replyForm.email = userStore.isLoggedIn ? (userStore.userInfo?.email || '') : ''
  try {
    replyFormRef.value?.resetFields?.()
    replyFormRef.value?.clearValidate?.()
  } catch (e) { /* ignore */ }
  showReplyForm.value = true
  nextTick(() => {
    replyFormRef.value?.$el?.querySelector('textarea')?.focus()
  })
}

function cancelReply() {
  replyForm.content = ''
  try {
    replyFormRef.value?.resetFields?.()
    replyFormRef.value?.clearValidate?.()
  } catch (e) { /* ignore */ }
  showReplyForm.value = false
}

async function submitReply() {
  // 表单校验单独前置：校验不通过直接返回。
  // 若混在提交逻辑的 try 里，昵称/邮箱/内容没填时也会落进 catch 换新验证码，
  // 而此时请求根本没发出去，验证码本来还是有效的。
  try {
    await replyFormRef.value?.validate()
  } catch {
    return
  }

  try {
    replySubmitting.value = true
    const data: CommentDTO = {
      ...replyForm,
      nickname: userStore.isLoggedIn ? userStore.userInfo?.nickname || '' : replyForm.nickname,
      email: userStore.isLoggedIn ? userStore.userInfo?.email || '' : replyForm.email,
      captchaId: captchaState.id,
      captcha: captchaState.answer
    }
    await commentApi.createComment(data)
    ElMessage.success('回复成功')
    cancelReply()
    // 验证码一次性使用，换新题
    if (needCaptcha.value) {
      await refreshCaptcha()
    }
    emit('refresh')
  } catch (error) {
    // request.ts 的拦截器已经弹过一次错误提示，这里不重复弹
    logError('回复失败', error)
    if (needCaptcha.value) {
      await refreshCaptcha()
    }
  } finally {
    replySubmitting.value = false
  }
}

function formatTime(dateStr: string) {
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()

  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  if (diff < 604800000) return `${Math.floor(diff / 86400000)}天前`

  return date.toLocaleDateString('zh-CN', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  })
}
</script>

<style scoped>
.comment-item {
  border-radius: 8px;
  transition: background 0.3s;
}

.comment-item:hover {
  background: #fafafa;
}

.comment-main {
  display: flex;
  gap: 12px;
  padding: 16px;
  align-items: flex-start;
}

.comment-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
}

.comment-body {
  flex: 1;
  min-width: 0;
}

.comment-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.comment-author {
  font-weight: 500;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 20px;
}

.admin-badge {
  padding: 2px 6px;
  background: #f0f9ff;
  color: #409eff;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
}

.comment-time {
  font-size: 18px;
  color: #909399;
  margin-left: 20px;
}

.comment-floor {
  font-size: 12px;
  color: #909399;
  margin-left: auto;
}

.comment-content {
  font-size: 18px;
  color: #606266;
  line-height: 1.7;
  word-break: break-word;
}

.comment-actions {
  display: flex;
  gap: 16px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #909399;
  font-size: 13px;
  padding: 4px 8px;
  border-radius: 4px;
  transition: all 0.3s;
}

.action-btn:hover {
  color: #409eff;
  background: #f0f9ff;
}

.action-icon {
  font-size: 14px;
}

.comment-children {
  border-left: 2px solid #ebeef5;
  margin-left: 24px;
  padding-left: 16px;
}

@media (max-width: 768px) {
  .comment-item {
    margin-left: 0 !important;
  }

.reply-form {
  padding: 16px;
  background: rgba(255, 255, 255, 0.5);
  border-top: 1px solid #f0f0f0;
}

.reply-form-enter-active,
.reply-form-leave-active {
  transition: all 0.3s ease;
}
.reply-form-leave-active :deep(.el-textarea__inner) {
  border-color: red;
}
.reply-form-enter-from,
.reply-form-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

.reply-form :deep(.el-form-item) {
  margin-bottom: 12px;
  width: 100%;
}

  .reply-form :deep(.el-form-item__content) {
  display: block;
  width: 100%;
}

.reply-form :deep(.el-form-item__label) {
  font-size: 20px;
  color: #000;
}

.reply-form :deep(.el-textarea__inner) {
  width: 100%;
  box-sizing: border-box;
  font-size: 18px;
}

.captcha-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.captcha-question {
  font-family: 'Courier New', monospace;
  font-size: 15px;
  font-weight: 700;
  color: #409eff;
  user-select: none;
}

.comment-children {
    margin-left: 16px;
    padding-left: 12px;
    border-left-width: 1px;
  }

  .comment-main {
    padding: 12px;
  }

  .comment-header {
    gap: 8px;
  }
}
</style>