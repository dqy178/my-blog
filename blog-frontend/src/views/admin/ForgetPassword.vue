<template>
  <div class="forget-page">
    <div class="forget-container">
      <div class="forget-header">
        <component :is="Lock" class="forget-logo" />
        <h1 class="forget-title">找回密码</h1>
        <p class="forget-subtitle">回答安全问题，重置登录密码</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        class="forget-form"
        @submit.prevent="handleSubmit"
      >
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            placeholder="请输入用户名"
            :prefix-icon="User"
            clearable
          />
        </el-form-item>

        <el-form-item prop="answer1" label="安全问题 1">
          <div class="security-question">
            <span class="question-text">你的爸爸是谁？</span>
            <el-input
              v-model="form.answer1"
              placeholder="请输入答案"
              class="answer-input"
            />
          </div>
        </el-form-item>

        <el-form-item prop="answer2" label="安全问题 2">
          <div class="security-question">
            <span class="question-text">你的妈妈是谁？</span>
            <el-input
              v-model="form.answer2"
              placeholder="请输入答案"
              class="answer-input"
            />
          </div>
        </el-form-item>

        <el-form-item prop="newPassword">
          <el-input
            v-model="form.newPassword"
            type="password"
            placeholder="请输入新密码"
            :prefix-icon="Lock"
            :show-password="showPassword"
          />
        </el-form-item>

        <el-form-item prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="请再次输入新密码"
            :prefix-icon="Lock"
            :show-password="showPassword"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            :loading="loading"
            class="reset-btn"
            native-type="submit"
          >
            重置密码
          </el-button>
        </el-form-item>
      </el-form>

      <div class="forget-footer">
        <router-link to="/login" class="back-login-link">记得密码了？返回登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 密保找回密码页。
 *
 * <p>用户通过回答两个安全问题重置密码。该接口是公开的（用户此时无法登录），
 * 服务端通过密保答案比对与失败限流来防止被暴力猜解。</p>
 */
import { logError } from '@/utils/logError'
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElForm } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { authApi, ForgetPasswordDTO } from '@/api/auth'

const router = useRouter()

/** 表单引用，用于触发校验 */
const formRef = ref<InstanceType<typeof ElForm>>()
/** 提交进行中 */
const loading = ref(false)
/** 密码是否明文显示 */
const showPassword = ref(false)

/** 表单数据。confirmPassword 仅前端校验用，不提交给后端 */
const form = reactive<ForgetPasswordDTO>({
  username: '',
  answer1: '',
  answer2: '',
  newPassword: '',
  confirmPassword: ''
})

/** 表单校验规则 */
const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  answer1: [
    { required: true, message: '请回答第一个安全问题', trigger: 'blur' }
  ],
  answer2: [
    { required: true, message: '请回答第二个安全问题', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度在6-20位之间', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

/**
 * 校验两次输入的新密码是否一致。
 *
 * @param _rule    规则对象（未使用）
 * @param value    当前输入值
 * @param callback 校验回调
 */
function validateConfirmPassword(_rule: any, value: string, callback: any) {
  if (value !== form.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

/** 提交重置密码请求 */
async function handleSubmit() {
  try {
    await formRef.value?.validate()
    loading.value = true
    await authApi.forgetPassword(form)
    ElMessage.success('密码重置成功，请重新登录')
    router.push('/login')
  } catch (error) {
    logError('重置密码失败:', error)
    const msg = error instanceof Error ? error.message : '重置失败，请重试'
    ElMessage.error(msg)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.forget-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background-image: url('/前端背景.png');
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  padding: 20px;
}

.forget-container {
  width: 100%;
  max-width: 520px;
  background: rgba(255, 255, 255, 0.3);
  border-radius: 16px;
  padding: 15px 48px;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.15);
  animation: slideDown 0.6s ease-out;
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-30px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.forget-header {
  text-align: center;
  margin-bottom: 32px;
}

.forget-logo {
  font-size: 20px;
  color: #409eff;
  margin-bottom: 0;
}

.forget-title {
  font-size: 24px;
  font-weight: 700;
  color: #303133;
  margin-bottom: 8px;
}

.forget-subtitle {
  font-size: 14px;
  color: #909399;
}

.forget-form :deep(.el-form-item) {
  margin-bottom: 20px;
}

.security-question {
  display: flex;
  align-items: center;
  gap: 12px;
}

.question-text {
  flex-shrink: 0;
  font-size: 14px;
  color: #606266;
  min-width: 120px;
}

.answer-input {
  flex: 1;
}

.reset-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
  border-radius: 8px;
}

.forget-footer {
  margin-top: 24px;
  text-align: center;
  font-size: 12px;
  color: #c0c4cc;
}

.back-login-link {
  color: #409eff;
  text-decoration: none;
  font-size: 16px;
}

.back-login-link:hover {
  text-decoration: underline;
  color: #66b1ff;
}
</style>
