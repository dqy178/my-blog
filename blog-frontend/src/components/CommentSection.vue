<template>
    <div class="comment-section">
    <h3 class="section-title">
      评论 <span class="comment-count">({{ totalComments }})</span>
    </h3>

    <!-- 「开楼」评论：直接在这里发表 -->
    <div v-if="!userStore.isLoggedIn" class="comment-form">
      <el-form ref="commentFormRef" :model="commentForm" :rules="commentRules" label-width="80px">
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="commentForm.nickname" placeholder="请输入昵称" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="commentForm.email" placeholder="请输入邮箱" />
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
        <el-form-item v-if="needCaptcha" label="验证码" prop="captcha">
          <div class="captcha-row">
            <el-input
              v-model="captchaState.answer"
              placeholder="请输入计算结果"
              style="width: 160px"
            />
            <span class="captcha-question">{{ captchaState.question }}</span>
            <el-button link type="primary" @click="refreshCaptcha">换一题</el-button>
          </div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="submitComment">
            提交评论
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <div v-else class="comment-form">
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
          <el-button type="primary" :loading="submitting" @click="submitComment">
            提交评论
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <div v-if="comments.length > 0" class="comment-list">
      <CommentItem
        v-for="(comment, index) in comments"
        :key="comment.id"
        :comment="comment"
        :level="0"
        :index="index"
        @refresh="fetchComments"
      />
    </div>

    <div v-else class="empty-comment">
      <component :is="ChatDotRound" class="empty-icon" />
      <p>暂无评论，快来抢沙发吧~</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch, computed, provide } from 'vue'
import { ElMessage, ElForm } from 'element-plus'
import { ChatDotRound } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { commentApi, CommentVO, CommentDTO } from '@/api/comment'
import { authApi } from '@/api/auth'
import { logError } from '@/utils/logError'
import { COMMENT_CAPTCHA_KEY, CommentCaptchaState } from './commentCaptcha'
import CommentItem from './CommentItem.vue'

interface Props {
  articleId: number
}

const props = defineProps<Props>()
const emit = defineEmits<{ refresh: []; 'count-update': [number] }>()

// 递归统计评论数（含回复）
function countComments(list: any[]): number {
  let count = 0
  for (const c of list) {
    count++
    if (c.children && c.children.length) count += countComments(c.children)
  }
  return count
}

const userStore = useUserStore()

const comments = ref<CommentVO[]>([])
const totalComments = ref(0)
const submitting = ref(false)

const commentForm = reactive<CommentDTO>({
  articleId: props.articleId,
  content: '',
  nickname: '',
  email: ''
})

// 游客评论需要验证码（后端强制校验），登录用户不需要。
// 判定条件与后端保持一致：后端按 isAdmin 判断（管理员直接放行），
// 前端若用 isLoggedIn 会出现「登录了但不是管理员」时前端不显示、后端却要求的不一致。
const captchaState = reactive<CommentCaptchaState>({
  id: '',
  question: '',
  answer: ''
})

const needCaptcha = computed(() => !userStore.isAdmin)

async function loadCaptcha() {
  captchaState.id = ''
  captchaState.question = ''
  captchaState.answer = ''
  // 换题后答案被清空，残留的红色「请输入验证码」要一并清掉
  commentFormRef.value?.clearValidate('captcha')
  if (!needCaptcha.value) return
  try {
    const res = await authApi.getCaptcha()
    captchaState.id = res.captchaId
    captchaState.question = res.question
  } catch (error) {
    logError('获取验证码失败', error)
  }
}

function refreshCaptcha() {
  loadCaptcha()
}

/**
 * 把验证码状态下发给所有内联回复表单。
 * 这样「开楼」和「回复某人」两处入口都受后端的验证码 / 频率限制约束，
 * 内联回复不会成为绕过防护的缺口。
 */
provide(COMMENT_CAPTCHA_KEY, {
  // 用 getter 保证登录状态变化时内联表单能同步
  get required() {
    return needCaptcha.value
  },
  state: captchaState,
  refresh: loadCaptcha
})

const commentRules = {
  content: [
    // 用 change 而非 blur：主楼评论框在空着的时候点击下方评论的「回复」按钮会让 textarea 失焦，
    // blur 会立刻误报「请输入评论内容」把主楼标红。change 只在用户真正编辑内容时校验。
    { required: true, message: '请输入评论内容', trigger: 'change' },
    { min: 1, max: 1000, message: '评论长度在 1 到 1000 字符', trigger: 'change' }
  ],
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  captcha: [
    {
      // 验证码答案放在共享的 captchaState 里（主楼与内联回复共用同一道验证码），
      // commentForm 里并没有 captcha 字段，所以不能用 required 规则去校验 model.captcha：
      // 那会恒为 undefined，导致无论用户填没填都报「请输入验证码」，请求根本发不出去。
      // 这里用自定义校验器直接读真实答案，只在没填时提示。
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

const commentFormRef = ref<InstanceType<typeof ElForm>>()

// 验证码答案一变（用户开始输入，或换题后被清空），
// 就清掉残留的红色「请输入验证码」提示。
watch(
  () => captchaState.answer,
  () => {
    commentFormRef.value?.clearValidate('captcha')
  }
)

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
    const count = countComments(res)
    totalComments.value = count
    emit('count-update', count)
  } catch (error) {
    logError('获取评论失败', error)
  }
}

async function submitComment() {
  // 表单校验单独前置：校验不通过直接返回。
  // 若混在提交逻辑的 try 里，校验失败也会落进 catch 换新验证码，
  // 而此时请求根本没发出去，验证码本来还是有效的（后端校验验证码才是一次性消费）。
  try {
    await commentFormRef.value?.validate()
  } catch {
    return
  }

  try {
    submitting.value = true

    const data: CommentDTO = {
      ...commentForm,
      articleId: props.articleId,
      // 主表单是「开楼」发表评论；回复某人走评论下方的内联表单
      parentId: undefined,
      nickname: userStore.isLoggedIn ? userStore.userInfo?.nickname || '' : commentForm.nickname,
      email: userStore.isLoggedIn ? userStore.userInfo?.email || '' : commentForm.email,
      captchaId: captchaState.id,
      captcha: captchaState.answer
    }

    await commentApi.createComment(data)
    ElMessage.success('评论成功')

    commentForm.content = ''
    // 验证码一次性使用，提交后必须换新
    if (needCaptcha.value) {
      await loadCaptcha()
    }
    await fetchComments()
  } catch (error: any) {
    // request.ts 的拦截器已经弹过一次错误提示，这里不再重复弹
    logError('提交评论失败', error)
    // 验证码可能已失效或答错，换一道新题让用户重试
    if (needCaptcha.value) {
      await loadCaptcha()
    }
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  fetchComments()
  loadCaptcha()
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

.reply-hint {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 16px;
  margin-bottom: 12px;
  font-size: 14px;
  color: #606266;
  background: rgba(255, 255, 255, 0.5);
  border: 1px solid #ebeef5;
  border-radius: 8px;
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
  background: rgba(255, 255, 255, 0.3);
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