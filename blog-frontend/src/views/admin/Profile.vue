<template>
  <div class="profile-page">
    <el-row :gutter="20" align="stretch">
      <el-col :xs="24" :lg="8">
        <el-card class="profile-card stretch-card" shadow="never">
          <div class="profile-header">
            <img :src="userInfo?.avatar || '/avatar.png'" :alt="userInfo?.nickname" class="profile-avatar" />
            <h3 class="profile-name">{{ userInfo?.nickname }}</h3>
            <p v-if="userInfo?.motto" class="profile-motto">{{ userInfo.motto }}</p>
            <el-tag v-if="userInfo" :type="userInfo.role === 'ROLE_ADMIN' ? 'success' : 'info'">{{ userInfo.role === 'ROLE_ADMIN' ? '管理员' : '用户' }}</el-tag>
          </div>
          <div class="profile-stats">
            <div class="stat-item">
              <span class="stat-label stat-label-lg">文章</span>
              <span class="stat-value">{{ userInfo?.articleCount || 0 }}</span>
            </div>
            <div class="stat-item">
              <span class="stat-label stat-label-lg">评论</span>
              <span class="stat-value">{{ userInfo?.commentCount || 0 }}</span>
            </div>
            <div class="stat-item">
              <span class="stat-label stat-label-lg">总浏览量</span>
              <span class="stat-value">{{ userInfo?.viewCount || 0 }}</span>
            </div>
            <div class="stat-item">
              <span class="stat-label stat-label-lg">最后登录</span>
              <span class="stat-value">{{ formatDate(userInfo?.lastLoginTime) }}</span>
            </div>
          </div>
        </el-card>

        <el-card class="form-card" shadow="never" style="margin-top: 20px;">
          <h4 class="form-section-title">修改密保答案</h4>
          <el-form ref="securityFormRef" :model="securityForm" :rules="securityRules" label-width="100px">
            <el-form-item label="安全问题 1">
              <span class="security-question-text">你的爸爸是谁？</span>
            </el-form-item>
            <el-form-item label="答案 1" prop="answer1">
              <el-input v-model="securityForm.answer1" placeholder="请输入答案" />
            </el-form-item>
            <el-form-item label="安全问题 2">
              <span class="security-question-text">你的妈妈是谁？</span>
            </el-form-item>
            <el-form-item label="答案 2" prop="answer2">
              <el-input v-model="securityForm.answer2" placeholder="请输入答案" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="savingSecurity" @click="handleUpdateSecurityAnswers">保存密保答案</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="16">
        <el-card class="form-card" shadow="never">
          <h4 class="form-section-title">基本信息</h4>
          <el-form ref="profileFormRef" :model="profileForm" :rules="profileRules" label-width="100px">
            <el-form-item label="用户名" prop="username">
              <el-input v-model="profileForm.username" disabled placeholder="用户名不可修改" />
            </el-form-item>
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="profileForm.email" placeholder="请输入邮箱" />
            </el-form-item>
            <el-form-item label="昵称" prop="nickname">
              <el-input v-model="profileForm.nickname" placeholder="请输入昵称" maxlength="50" show-word-limit />
            </el-form-item>
            <el-form-item label="个人简介" prop="bio">
              <el-input v-model="profileForm.bio" type="textarea" :rows="4" placeholder="请输入个人简介" maxlength="500" show-word-limit />
            </el-form-item>
            <el-form-item label="座右铭" prop="motto">
              <el-input v-model="profileForm.motto" placeholder="请输入座右铭" maxlength="200" show-word-limit />
            </el-form-item>
            <el-form-item label="头像">
              <div class="avatar-upload">
                <el-upload
                  action="#"
                  :http-request="uploadAvatar"
                  :auto-upload="true"
                  :show-file-list="false"
                  accept="image/*"
                  :on-success="handleAvatarSuccess"
                >
                  <img v-if="profileForm.avatar" :src="profileForm.avatar" class="avatar-preview" alt="头像预览" />
                  <div v-else class="avatar-placeholder">
                    <component :is="Picture" class="upload-icon" />
                    <p>点击上传头像</p>
                    <span>建议正方形, 不超过2MB</span>
                  </div>
                </el-upload>
              </div>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="savingProfile" @click="handleSaveProfile">保存修改</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card class="form-card stretch-card" shadow="never" style="margin-top: 20px;">
          <h4 class="form-section-title">修改密码</h4>
          <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-width="100px">
            <el-form-item label="原密码" prop="oldPassword">
              <el-input v-model="passwordForm.oldPassword" type="password" placeholder="请输入原密码" show-password />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input v-model="passwordForm.newPassword" type="password" placeholder="请输入新密码" show-password />
            </el-form-item>
            <el-form-item label="确认新密码" prop="confirmPassword">
              <el-input v-model="passwordForm.confirmPassword" type="password" placeholder="请再次输入新密码" show-password />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="changingPassword" @click="handleChangePassword">修改密码</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
/**
 * 后台个人中心。
 *
 * <p>包含三块：基本资料修改、修改密码、修改密保答案。
 * 三者各自独立校验与 loading，互不影响。</p>
 *
 * <p>改密与改密保后，后端会让该用户的全部会话立即失效，
 * 因此前端也需同步清除本地登录态并跳回登录页。</p>
 */
import { logError } from '@/utils/logError'
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElForm } from 'element-plus'
import { Picture } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { authApi } from '@/api/auth'

const userStore = useUserStore()
const router = useRouter()
/** 当前登录用户信息（与 store 同步） */
const userInfo = computed(() => userStore.userInfo)

/** 保存资料中 */
const savingProfile = ref(false)
/** 修改密码中 */
const changingPassword = ref(false)
/** 保存密保中 */
const savingSecurity = ref(false)

/** 基本资料表单 */
const profileForm = reactive({
  username: '',
  email: '',
  nickname: '',
  bio: '',
  motto: '',
  avatar: ''
})

/** 基本资料校验规则 */
const profileRules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { max: 50, message: '昵称长度不能超过50字符', trigger: 'blur' }
  ]
}

/** 修改密码表单 */
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

/** 密保答案表单 */
const securityForm = reactive({
  answer1: '',
  answer2: ''
})

/** 修改密码校验规则 */
const passwordRules = {
  oldPassword: [
    { required: true, message: '请输入原密码', trigger: 'blur' }
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

/** 密保答案校验规则 */
const securityRules = {
  answer1: [
    { required: true, message: '请输入答案1', trigger: 'blur' }
  ],
  answer2: [
    { required: true, message: '请输入答案2', trigger: 'blur' }
  ]
}

const profileFormRef = ref<InstanceType<typeof ElForm>>()
const passwordFormRef = ref<InstanceType<typeof ElForm>>()
const securityFormRef = ref<InstanceType<typeof ElForm>>()

/**
 * 校验两次输入的新密码是否一致。
 *
 * <p>自定义 validator 而非简单的 max/min 规则：需要跨字段比较。</p>
 *
 * @param _rule    规则对象（未使用）
 * @param value    当前输入值
 * @param callback 校验回调，通过时调用无参，出错时传 Error
 */
function validateConfirmPassword(_rule: any, value: string, callback: any) {
  if (value !== passwordForm.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

/** 加载个人资料并回填表单 */
async function fetchProfile() {
  try {
    const res = await authApi.getProfile()
    Object.assign(profileForm, res)
    userStore.setUserInfo(res)
  } catch (error) {
    logError('获取个人信息失败:', error)
  }
}

/** 保存基本资料 */
async function handleSaveProfile() {
  try {
    await profileFormRef.value?.validate()
    savingProfile.value = true
    const res = await authApi.updateProfile(profileForm)
    Object.assign(profileForm, res)
    userStore.setUserInfo(res)
    // 重新获取完整 profile，确保 articleCount/commentCount/viewCount 等统计字段不会被 updateProfile 返回的精简对象抹掉
    await fetchProfile()
    ElMessage.success('保存成功')
  } catch (error) {
    logError('保存失败:', error)
    ElMessage.error('保存失败，请查看控制台')
  } finally {
    savingProfile.value = false
  }
}

/**
 * 修改密码。
 *
 * <p>成功后后端已让旧 token 失效，这里同步清本地登录态并跳登录页。</p>
 */
async function handleChangePassword() {
  try {
    await passwordFormRef.value?.validate()
    changingPassword.value = true
    await authApi.updatePassword(passwordForm.oldPassword, passwordForm.newPassword)
    ElMessage.success('密码修改成功，请重新登录')
    // 改密后后端已让旧 token 失效，这里同步清本地登录态
    await userStore.logout()
    router.push('/login')
  } catch (error) {
    logError('修改密码失败:', error)
    const msg = error instanceof Error ? error.message : '修改密码失败，请查看控制台'
    ElMessage.error(msg)
  } finally {
    changingPassword.value = false
  }
}

/**
 * 修改密保答案。
 *
 * <p>保存后清空输入框：答案已加密落库，不应在页面上继续明文停留。</p>
 */
async function handleUpdateSecurityAnswers() {
  try {
    await securityFormRef.value?.validate()
    savingSecurity.value = true
    await authApi.updateSecurityAnswers(securityForm.answer1, securityForm.answer2)
    ElMessage.success('密保答案保存成功')
    securityForm.answer1 = ''
    securityForm.answer2 = ''
  } catch (error) {
    logError('保存密保答案失败:', error)
    const msg = error instanceof Error ? error.message : '保存密保答案失败，请查看控制台'
    ElMessage.error(msg)
  } finally {
    savingSecurity.value = false
  }
}

/**
 * 头像上传回调（Element Plus el-upload 的 httpRequest）。
 *
 * @param options el-upload 传入的选项对象
 */
function uploadAvatar(options: any) {
  // 刻意不用 async：async 函数返回的是 Promise，Element Plus 会在 Promise resolve 后
  // 自动再调一次 options.onSuccess，和这里手动调用叠加后 on-success 回调会触发两次，
  // 弹出两个「头像上传成功」。改用 .then() 后 httpRequest 的返回值不是 Promise，
  // onSuccess/onError 只会被调用一次。
  fileApi.upload(options.file)
    .then((res) => {
      profileForm.avatar = res.url
      options.onSuccess(res)
    })
    .catch((error) => {
      logError('头像上传失败', error)
      options.onError()
      // show-file-list 为 false 时 el-upload 不显示错误，必须自己提示
      ElMessage.error('头像上传失败，请重试')
    })
}

/** 头像上传成功提示 */
function handleAvatarSuccess() {
  ElMessage.success('头像上传成功')
}

/**
 * 格式化最后登录时间。
 *
 * @param dateStr 时间字符串
 * @returns 格式化后的时间；无记录时返回「未登录」
 */
function formatDate(dateStr: string | undefined) {
  if (!dateStr) return '未登录'
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  })
}

import { fileApi } from '@/api/file'

/** 初始化：加载个人资料 */
onMounted(() => {
  fetchProfile()
})
</script>

<style scoped>

.page-header {
  margin-bottom: 24px;
}

.profile-card,
.form-card {
  background: rgba(255, 255, 255, 0.3);
  border-radius: 12px;
  overflow: hidden;
}

.profile-card :deep(.el-card__body),
.form-card :deep(.el-card__body) {
  background: rgba(255, 255, 255, 0.3);
}

.profile-page :deep(.el-row) {
  align-items: stretch;
}

.profile-page :deep(.el-col) {
  display: flex;
  flex-direction: column;
}

.stretch-card {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.stretch-card :deep(.el-card__body) {
  flex: 1;
}

.profile-header {
  text-align: center;
  margin-bottom: 16px;
}

.profile-avatar {
  height: 100px;
  border-radius: 50%;
  object-fit: cover;
  border: 4px solid #f5f7fa;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  margin-bottom: 16px;
}

.profile-name {
  font-size: 32px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 8px;
  white-space: nowrap;
}

.profile-motto {
  font-size: 25px;
  color: #909399;
  font-style: italic;
  margin: 0 0 12px;
}

.profile-stats {
  display: flex;
  justify-content: center;
  gap: 24px;
  margin-top: 24px;
  padding-top: 24px;
  border-top: 1px solid #ebeef5;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.stat-value {
  font-size: 24px;
  font-weight: 400;
  color: #303133;
}

.stat-label {
  font-size: 22px;
  font-weight: 700;
  color: #909399;
}

.stat-label-lg {
  font-size: 22px;
  font-weight: 600;
  color: #303133;
}

.form-card :deep(.el-form-item) {
  margin-bottom: 20px;
  font-size: 18px;
}

.form-card :deep(.el-input__inner),
.form-card :deep(.el-textarea__inner),
.form-card :deep(.el-form-item__label) {
  font-size: 18px;
}

.form-card :deep(.el-form-item__label) {
  font-weight: 500;
  color: #303133;
}

.security-question-text {
  color: #606266;
  font-size: 14px;
}

.form-section-title {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 24px;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
}

.avatar-upload {
  display: inline-block;
}

.avatar-preview {
  width: 100px;
  height: 100px;
  border-radius: 8px;
  object-fit: cover;
  cursor: pointer;
  transition: transform 0.3s;
}

.avatar-preview:hover {
  transform: scale(1.05);
}

.avatar-placeholder {
  width: 100px;
  height: 100px;
  border: 2px dashed #dcdfe6;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.3s;
  background: #fafafa;
}

.avatar-placeholder:hover {
  border-color: #409eff;
  background: #f0f9ff;
}

.upload-icon {
  font-size: 24px;
  color: #909399;
  margin-bottom: 8px;
}

.avatar-placeholder p {
  font-size: 13px;
  color: #606266;
  margin: 0 0 4px;
}

.avatar-placeholder span {
  font-size: 11px;
  color: #c0c4cc;
}
</style>