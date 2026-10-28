<template>
  <div class="login-page">
    <div class="login-container">
      <div class="login-header">
        <component :is="Monitor" class="login-logo" />
        <h1 class="login-title">个人博客后台</h1>
        <p class="login-subtitle">请登录后继续</p>
      </div>

      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        class="login-form"
        @submit.prevent="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="用户名"
            :prefix-icon="User"
            clearable
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="密码"
            :prefix-icon="Lock"
            :show-password="showPassword"
            @keydown.enter="handleLogin"
          />
        </el-form-item>

        <el-form-item>
          <el-checkbox v-model="loginForm.rememberMe" class="remember-me">
            记住我
          </el-checkbox>
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            :loading="loading"
            class="login-btn"
            native-type="submit"
          >
            登录
          </el-button>
        </el-form-item>
      </el-form>

      <div class="login-footer">
        <div class="footer-links">
          <router-link to="/forget-password" class="forget-link">忘记密码？</router-link>
          <router-link to="/" class="back-home-link">返回首页</router-link>
        </div>
        <p>默认账号: admin / 123456</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 后台登录页。
 *
 * <p>登录成功后按 {@code route.query.redirect} 跳回原访问页面，
 * 没有该参数时默认进入仪表盘。</p>
 */
import { logError } from '@/utils/logError'
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElForm } from 'element-plus'
import { Monitor, User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

/** 登录表单引用，用于触发校验 */
const loginFormRef = ref<InstanceType<typeof ElForm>>()
/** 登录进行中 */
const loading = ref(false)
/** 密码是否明文显示 */
const showPassword = ref(false)

/** 登录表单。rememberMe 默认为开启 */
const loginForm = reactive({
  username: '',
  password: '',
  rememberMe: true
})

/** 登录表单校验规则 */
const loginRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ]
}

/**
 * 执行登录。
 *
 * <p>登录成功后必须再拉一次 profile 写入 userInfo.role，
 * 否则路由守卫读不到角色会被踢回 /login。</p>
 */
async function handleLogin() {
  try {
    await loginFormRef.value?.validate()
    loading.value = true
    await userStore.login(loginForm)
    // 拉取一次 profile 写入 userInfo.role；否则路由守卫读不到角色会被踢回 /login
    try {
      await userStore.fetchProfile()
    } catch (e) {
      console.warn('fetchProfile 失败：', e)
    }
    ElMessage.success('登录成功')

    const redirect = (route.query.redirect as string) || '/admin/dashboard'
    // 必须 await，确保守卫在 userInfo 已更新的前提下执行
    await router.replace(redirect)
  } catch (error) {
    logError('登录失败:', error)
    const msg = error instanceof Error ? error.message : '登录失败，请重试'
    ElMessage.error(msg)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
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

.login-container {
  width: 100%;
  max-width: 520px;
  background: rgba(255, 255, 255, 0.3);
  border-radius: 16px;
  padding: 44px 48px;
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

.login-header {
  text-align: center;
  margin-bottom: 32px;
}

.login-logo {
  font-size: 36px;
  color: #409eff;
  margin-bottom: 16px;
}

.login-title {
  font-size: 24px;
  font-weight: 700;
  color: #303133;
  margin-bottom: 8px;
}

.login-subtitle {
  font-size: 14px;
  color: #909399;
}

.login-form :deep(.el-form-item) {
  margin-bottom: 20px;
}

.login-form :deep(.el-input__prefix) {
  color: #909399;
}

.remember-me :deep(.el-checkbox__label) {
  font-size: 14px;
  color: #606266;
}

.login-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
  border-radius: 8px;
}

.login-footer {
  margin-top: 24px;
  text-align: center;
  font-size: 12px;
  color: #c0c4cc;
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: center;
}

.footer-links {
  display: flex;
  gap: 20px;
  font-size: 16px;
}

.forget-link {
  color: #409eff;
  text-decoration: none;
}

.forget-link:hover {
  text-decoration: underline;
  color: #66b1ff;
}

.back-home-link {
  color: #409eff;
  text-decoration: none;
  font-size: 16px;
}

.back-home-link:hover {
  text-decoration: underline;
  color: #66b1ff;
}

@media (max-width: 480px) {
  .login-container {
    padding: 32px 24px;
  }
}
</style>