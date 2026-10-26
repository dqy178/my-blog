<template>
  <div class="comment-section">
    <h3 class="section-title">
      评论 <span class="comment-count">({{ totalComments }})</span>
    </h3>

    <div class="comment-form" v-if="!userStore.isLoggedIn">
      <el-form ref="commentFormRef" :model="commentForm" :rules="commentRules" label-width="80px">
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="commentForm.nickname" placeholder="请输入昵称" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="commentForm.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="网站" prop="website">
          <el-input v-model="commentForm.website" placeholder="请输入个人网站 (可选)" />
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input
            v-model="commentForm.content"
            type="textarea"
            :rows="4"
            placeholder="请输入评论内容..."
            @keydown.enter.exact="submitComment"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="submitComment" :loading="submitting">
            提交评论
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="comment-form" v-else>
      <el-form ref="commentFormRef" :model="commentForm" :rules="commentRules" label-width="80px">
        <el-form-item label="内容" prop="content">
          <el-input
            v-model="commentForm.content"
            type="textarea"
            :rows="4"
            placeholder="写下你的评论..."
            @keydown.enter.exact="submitComment"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="submitComment" :loading="submitting">
            提交评论
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="comment-list" v-if="comments.length > 0">
      <CommentItem
        v-for="(comment, index) in comments"
        :key="comment.id"
        :comment="comment"
        :level="0"
        :index="index"
        @reply="handleReply"
        @refresh="fetchComments"
      />
    </div>

    <div class="empty-comment" v-else>
      <component :is="ChatDotRound" class="empty-icon" />
      <p>暂无评论，快来抢沙发吧~</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElForm } from 'element-plus'
import { ChatDotRound } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { commentApi, CommentVO, CommentDTO } from '@/api/comment'
import CommentItem from './CommentItem.vue'

interface Props {
  articleId: number
}

const props = defineProps<Props>()
const emit = defineEmits<{ refresh: []; 'count-update': [number] }>()

const userStore = useUserStore()

const comments = ref<CommentVO[]>([])
const totalComments = ref(0)
const submitting = ref(false)
const replyTo = ref<CommentVO | null>(null)

const commentForm = reactive<CommentDTO>({
  articleId: props.articleId,
  content: '',
  nickname: '',
  email: '',
  website: '',
  imageUrl: ''
})

const commentRules = {
  content: [
    { required: true, message: '请输入评论内容', trigger: 'blur' },
    { min: 1, max: 1000, message: '评论长度在 1 到 1000 字符', trigger: 'blur' }
  ],
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ]
}

const commentFormRef = ref<InstanceType<typeof ElForm>>()

async function fetchComments() {
  try {
    const res = await commentApi.getCommentsByArticleId(props.articleId)
    // 顶级评论按时间降序，回复按时间升序（自然流）
    res.sort((a, b) => new Date(b.createTime).getTime() - new Date(a.createTime).getTime())
    for (const c of res) {
      if (c.children && c.children.length) {
        c.children.sort((a, b) => new Date(a.createTime).getTime() - new Date(b.createTime).getTime())
      }
    }
    comments.value = res
    // 统计所有评论数量（包括回复）
    let count = 0
    function countAll(list: any[]) {
      for (const c of list) {
        count++
        if (c.children && c.children.length) {
          countAll(c.children)
        }
      }
    }
    countAll(res)
    totalComments.value = count
    emit('count-update', count)
  } catch (error) {
    console.error('获取评论失败:', error)
  }
}

async function submitComment() {
  try {
    await commentFormRef.value?.validate()
    submitting.value = true

    const data: CommentDTO = {
      ...commentForm,
      articleId: props.articleId,
      parentId: replyTo.value?.id,
      imageUrl: '',
      nickname: userStore.isLoggedIn ? userStore.userInfo?.nickname || '' : commentForm.nickname,
      email: userStore.isLoggedIn ? userStore.userInfo?.email || '' : commentForm.email
    }

    await commentApi.createComment(data)
    ElMessage.success(replyTo.value ? '回复成功' : '评论成功')

    commentForm.content = ''
    replyTo.value = null
    await fetchComments()
  } catch (error: any) {
    console.error('提交评论失败:', error)
    const rawMsg = error?.response?.data?.message || error?.message || '提交失败'
    const msg = typeof rawMsg === 'string' ? rawMsg : JSON.stringify(rawMsg)
    ElMessage.error(msg)
  } finally {
    submitting.value = false
  }
}

function handleReply(comment: CommentVO) {
  replyTo.value = comment
  commentFormRef.value?.$el?.scrollIntoView({ behavior: 'smooth' })
  if (commentFormRef.value?.$el) {
    const textarea = commentFormRef.value.$el.querySelector('textarea')
    textarea?.focus()
  }
}

function resetReply() {
  replyTo.value = null
}

onMounted(() => {
  fetchComments()
})

watch(() => props.articleId, () => {
  fetchComments()
})
</script>

<style scoped>
.comment-section {
  margin-top: 40px;
  padding: 0 40px 32px;
  border-top: 1px solid #ebeef5;
}

.section-title {
  font-size: 25px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 24px;
}

.comment-count {
  font-weight: 400;
  color: #000;
  font-size: 25px;
}

.comment-form {
  background: #fafafa;
  border-radius: 12px;
  padding: 24px 0;
  margin-bottom: 32px;
}

.comment-form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.comment-form :deep(.el-form-item__label) {
  font-size: 20px;
  color: #000;
}

.comment-form :deep(.el-form-item:last-child) {
  margin-bottom: 0;
}

.comment-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.empty-comment {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: #909399;
  text-align: center;
}

.empty-icon {
  width: 32px;
  height: 32px;
  margin-bottom: 16px;
  opacity: 0.5;
}
</style>