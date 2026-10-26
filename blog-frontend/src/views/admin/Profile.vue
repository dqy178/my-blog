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
              <el-button type="primary" @click="handleUpdateSecurityAnswers" :loading="savingSecurity">保存密保答案</el-button>
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
              <el-button type="primary" @click="handleSaveProfile" :loading="savingProfile">保存修改</el-button>
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
              <el-button type="primary" @click="handleChangePassword" :loading="changingPassword">修改密码</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElForm } from 'element-plus'
import { Picture, Lock, User } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { authApi, UserVO } from '@/api/auth'

const userStore = useUserStore()
const router = useRouter()
const userInfo = computed(() => userStore.userInfo)

const savingProfile = ref(false)
const changingPassword = ref(false)
const savingSecurity = ref(false)

const profileForm = reactive({
  username: '',
  email: '',
  nickname: '',
  bio: '',
  motto: '',
  avatar: ''
})

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

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const securityForm = reactive({
  answer1: '',
  answer2: ''
})

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

function validateConfirmPassword(rule: any, value: string, callback: any) {
  if (value !== passwordForm.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

async function fetchProfile() {
  try {
    const res = await authApi.getProfile()
    Object.assign(profileForm, res)
    userStore.setUserInfo(res)
  } catch (error) {
    console.error('获取个人信息失败:', error)
  }
}

async function handleSaveProfile() {
  try {
    await profileFormRef.value?.validate()
    savingProfile.value = true
    const res = await authApi.updateProfile(profileForm)
    Object.assign(profileForm, res)
    userStore.setUserInfo(res)
    ElMessage.success('保存成功')
  } catch (error) {
    console.error('保存失败:', error)
    ElMessage.error('保存失败，请查看控制台')
  } finally {
    savingProfile.value = false
  }
}

async function handleChangePassword() {
  try {
    await passwordFormRef.value?.validate()
    changingPassword.value = true
    await authApi.updatePassword(passwordForm.oldPassword, passwordForm.newPassword)
    ElMessage.success('密码修改成功，请重新登录')
    userStore.logout()
    router.push('/login')
  } catch (error) {
    console.error('修改密码失败:', error)
    const msg = error instanceof Error ? error.message : '修改密码失败，请查看控制台'
    ElMessage.error(msg)
  } finally {
    changingPassword.value = false
  }
}

async function handleUpdateSecurityAnswers() {
  try {
    await securityFormRef.value?.validate()
    savingSecurity.value = true
    await authApi.updateSecurityAnswers(securityForm.answer1, securityForm.answer2)
    ElMessage.success('密保答案保存成功')
    securityForm.answer1 = ''
    securityForm.answer2 = ''
  } catch (error) {
    console.error('保存密保答案失败:', error)
    const msg = error instanceof Error ? error.message : '保存密保答案失败，请查看控制台'
    ElMessage.error(msg)
  } finally {
    savingSecurity.value = false
  }
}

async function uploadAvatar(options: any) {
  const file = options.file
  try {
    const res = await fileApi.upload(file)
    profileForm.avatar = res.url
    options.onSuccess()
  } catch (error) {
    options.onError()
  }
}

function handleAvatarSuccess() {
  ElMessage.success('头像上传成功')
}

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
import { onMounted } from 'vue'

onMounted(() => {
  fetchProfile()
})
</script>

<style scoped>

.page-header {
  margin-bottom: 24px;
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