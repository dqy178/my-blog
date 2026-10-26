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
          <span class="comment-floor" v-if="level === 0">#{{ index + 1 }}</span>
        </div>
        <div class="comment-content" v-html="safeContent"></div>
        <div class="comment-image" v-if="comment.imageUrl">
          <img :src="comment.imageUrl" :alt="comment.nickname" class="comment-img" />
        </div>
        <div class="comment-actions">
          <button class="action-btn" @click="handleReply">
            <component :is="ChatDotRound" class="action-icon" />
            回复
          </button>
        </div>
      </div>
    </div>

<transition name="reply-form">
  <div v-if="showReplyForm" class="reply-form">
    <el-form ref="replyFormRef" :model="replyForm" :rules="replyRules" label-width="0" inline>
<template v-if="!userStore.isLoggedIn">
  <el-form-item prop="nickname">
    <el-input v-model="replyForm.nickname" placeholder="昵称" style="width: 100%" />
  </el-form-item>
  <el-form-item prop="email">
    <el-input v-model="replyForm.email" placeholder="邮箱" style="width: 100%" />
  </el-form-item>
</template>
      <el-form-item prop="content">
        <el-input
          v-model="replyForm.content"
          type="textarea"
          :rows="3"
          placeholder="写下你的回复..."
          @keydown.enter.exact="submitReply"
        />
      </el-form-item>
      <el-form-item>
        <el-button size="small" type="primary" @click="submitReply" :loading="replySubmitting">
          回复
        </el-button>
        <el-button size="small" @click="cancelReply">取消</el-button>
      </el-form-item>
    </el-form>
  </div>
</transition>

    <div class="comment-children" v-if="comment.children && comment.children.length > 0">
      <CommentItem
        v-for="(child, index) in comment.children"
        :key="child.id"
        :comment="child"
        :level="level + 1"
        :index="index"
        @reply="handleReply"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, nextTick, computed } from 'vue'
import { ElMessage, ElForm } from 'element-plus'
import { ChatDotRound } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { commentApi, CommentVO, CommentDTO } from '@/api/comment'
import { safeHtml } from '@/utils/safeHtml'

interface Props {
  comment: CommentVO
  level: number
  index?: number
}

const props = defineProps<Props>()
const emit = defineEmits<{ reply: [CommentVO] }>()

const userStore = useUserStore()
const safeContent = computed(() => safeHtml(props.comment.content))

const showReplyForm = ref(false)
const replySubmitting = ref(false)

const replyForm = reactive<CommentDTO>({
  articleId: props.comment.articleId,
  parentId: props.comment.id,
  content: ''
})

const replyRules = {
  content: [
    { required: true, message: '请输入回复内容', trigger: 'change' },
    { min: 1, max: 1000, message: '回复长度在 1 到 1000 字符', trigger: 'change' }
  ]
}

const replyFormRef = ref<InstanceType<typeof ElForm>>()

function handleReply() {
  if (showReplyForm.value) {
    replyForm.content = ''
    try {
      replyFormRef.value?.resetFields?.()
      replyFormRef.value?.clearValidate?.()
    } catch (e) {}
    nextTick(() => {
      showReplyForm.value = false
    })
  } else {
    replyForm.content = ''
    replyForm.nickname = userStore.isLoggedIn ? (userStore.userInfo?.nickname || '') : ''
    replyForm.email = userStore.isLoggedIn ? (userStore.userInfo?.email || '') : ''
    try {
      replyFormRef.value?.resetFields?.()
      replyFormRef.value?.clearValidate?.()
    } catch (e) {}
    showReplyForm.value = true
    nextTick(() => {
      replyFormRef.value?.$el?.querySelector('textarea')?.focus()
    })
  }
}

async function submitReply() {
  try {
    await replyFormRef.value?.validate()
    replySubmitting.value = true
    const data: CommentDTO = {
      ...replyForm,
      nickname: userStore.isLoggedIn ? userStore.userInfo?.nickname || '' : replyForm.nickname,
      email: userStore.isLoggedIn ? userStore.userInfo?.email || '' : replyForm.email
    }
    await commentApi.createComment(data)
    ElMessage.success('回复成功')
    replyForm.content = ''
    replyFormRef.value?.resetFields?.()
    showReplyForm.value = false
    emit('refresh')
  } catch (error) {
    console.error('回复失败:', error)
  } finally {
    replySubmitting.value = false
  }
}

function cancelReply() {
  replyForm.content = ''
  try {
    replyFormRef.value?.resetFields?.()
  } catch (e) {}
  showReplyForm.value = false
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

.comment-image {
  margin-top: 12px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #ebeef5;
}

.comment-img {
  display: block;
  max-width: 100%;
  max-height: 300px;
  height: auto;
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

.reply-form {
  padding: 16px;
  background: #fafafa;
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
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
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

.comment-children {
  border-left: 2px solid #ebeef5;
  margin-left: 24px;
  padding-left: 16px;
}

@media (max-width: 768px) {
  .comment-item {
    margin-left: 0 !important;
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