<template>
  <div class="friend-link-list-page">
    <div class="page-header">
      <div class="header-actions">
        <el-button type="primary" size="" @click="handleAdd">
          <component :is="Plus" class="mr-1" />
          新增友链
        </el-button>
        <el-button type="danger" :disabled="!selectedIds.length" @click="handleBatchDelete">
          批量删除
        </el-button>
      </div>
    </div>

    <el-card class="table-card" shadow="never">
      <el-table
        v-loading="loading"
        :data="friendLinks"
        border
        stripe
        row-key="id"
        style="width: 100%"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" />
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column label="友链信息" min-width="250">
          <template #default="scope">
            <div class="link-info">
              <img :src="scope.row.avatar || '/friend-avatar.png'" :alt="scope.row.name" class="link-avatar" />
              <div>
                <p class="link-name">{{ scope.row.name }}</p>
                <p class="link-url" title="点击复制" @click="copyUrl(scope.row.url)">{{ scope.row.url }}</p>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="sort" label="排序" width="80" align="center" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="scope">
            <el-switch
              v-model="scope.row.status"
              :active-value="1"
              :inactive-value="0"
              @change="(val: any) => handleStatusChange(scope.row.id, val)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160" align="center">
          <template #default="scope">
            {{ formatDate(scope.row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="scope">
            <el-button size="small" type="primary" link @click="handleEdit(scope.row)">
              <component :is="Edit" class="mr-1" />
              编辑
            </el-button>
            <el-button size="small" type="danger" link @click="handleDelete(scope.row.id, scope.row.name)">
              <component :is="Delete" class="mr-1" />
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editId ? '编辑友链' : '新增友链'" width="500px" :before-close="handleClose">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="友链名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入友链名称" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="友链地址" prop="url">
          <el-input v-model="form.url" placeholder="请输入友链地址 (需包含http/https)" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="头像" prop="avatar">
          <el-upload
            action="#"
            :http-request="handleAvatarUpload"
            :show-file-list="false"
            accept="image/*"
          >
            <img v-if="form.avatar" :src="form.avatar" class="avatar-preview" alt="头像预览" />
            <div v-else class="avatar-placeholder">
              <component :is="Picture" class="upload-icon" />
              <p>点击上传头像</p>
            </div>
          </el-upload>
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入友链描述" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="排序" prop="sort">
          <el-input-number v-model="form.sort" :min="0" :max="9999" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 后台友链管理页。
 *
 * <p>提供友链的新增、编辑、单个与批量删除，以及显示/隐藏开关。
 * 前台只展示 status=1 的友链。</p>
 */
import { logError } from '@/utils/logError'
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox, ElForm } from 'element-plus'
import { Plus, Edit, Delete, Picture } from '@element-plus/icons-vue'
import { friendLinkApi, FriendLinkVO, FriendLinkDTO } from '@/api/friendLink'
import { fileApi } from '@/api/file'

/** 表格加载中 */
const loading = ref(false)
/** 友链列表（含已隐藏） */
const friendLinks = ref<FriendLinkVO[]>([])
/** 新增/编辑对话框可见性 */
const dialogVisible = ref(false)
/** 提交中 */
const submitting = ref(false)
/** 正在编辑的友链 ID。为 null 表示新增 */
const editId = ref<number | null>(null)
/** 选中的友链 ID */
const selectedIds = ref<number[]>([])

/**
 * 记录选中的友链 ID。
 *
 * @param selection 当前选中的友链
 */
function handleSelectionChange(selection: FriendLinkVO[]) {
  selectedIds.value = selection.map(item => item.id)
}

/**
 * 批量删除友链（带二次确认）。
 */
async function handleBatchDelete() {
  try {
    await ElMessageBox.confirm(`确定要删除选中的 ${selectedIds.value.length} 个友链吗？`, '确认批量删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await friendLinkApi.batchDeleteFriendLinks(selectedIds.value)
    ElMessage.success('批量删除成功')
    selectedIds.value = []
    fetchFriendLinks()
  } catch (error) {
    // 用户取消删除时不算错误
    if (error !== 'cancel' && error !== 'close') {
      logError('批量删除失败:', error)
    }
  }
}

/** 新增/编辑表单数据。默认显示 */
const form = reactive<FriendLinkDTO>({
  id: undefined,
  name: '',
  url: '',
  avatar: '',
  description: '',
  sort: 0,
  status: 1
})

/** 表单校验规则 */
const rules = {
  name: [
    { required: true, message: '请输入友链名称', trigger: 'blur' },
    { max: 50, message: '名称长度不能超过50字符', trigger: 'blur' }
  ],
  url: [
    { required: true, message: '请输入友链地址', trigger: 'blur' },
    { pattern: /^https?:\/\//, message: '地址必须以 http:// 或 https:// 开头', trigger: 'blur' }
  ],
  sort: [
    { required: true, message: '请输入排序', trigger: 'blur' }
  ]
}

const formRef = ref<InstanceType<typeof ElForm>>()

/** 加载友链列表 */
async function fetchFriendLinks() {
  loading.value = true
  try {
    const res = await friendLinkApi.getAllFriendLinks()
    friendLinks.value = res
  } catch (error) {
    logError('获取友链列表失败:', error)
  } finally {
    loading.value = false
  }
}

/** 打开新增对话框 */
function handleAdd() {
  editId.value = null
  resetForm()
  dialogVisible.value = true
}

/**
 * 打开编辑对话框并回填数据。
 *
 * @param row 被编辑的友链
 */
function handleEdit(row: FriendLinkVO) {
  editId.value = row.id
  form.id = row.id
  form.name = row.name
  form.url = row.url
  form.avatar = row.avatar || ''
  form.description = row.description || ''
  form.sort = row.sort
  form.status = row.status
  dialogVisible.value = true
}

/** 重置表单并清除上一次的校验提示 */
function resetForm() {
  form.id = undefined
  form.name = ''
  form.url = ''
  form.avatar = ''
  form.description = ''
  form.sort = 0
  form.status = 1
  formRef.value?.clearValidate()
}

/**
 * 上传友链头像。
 *
 * <p>兼容两种入参形态：el-upload 的 upload 钩子传入的是 { file } 对象，
 * 而自定义调用可能直接传 File。</p>
 *
 * @param file 待上传的文件或 el-upload 的选项对象
 */
async function handleAvatarUpload(file: any) {
  try {
    const res = await fileApi.upload(file.file || file)
    form.avatar = res.url
    ElMessage.success('头像上传成功')
  } catch (error) {
    ElMessage.error('头像上传失败')
  }
}

/** 提交表单（按 editId 区分新增/更新） */
async function handleSubmit() {
  try {
    await formRef.value?.validate()
    submitting.value = true

    if (editId.value) {
      await friendLinkApi.updateFriendLink(editId.value, form)
      ElMessage.success('更新成功')
    } else {
      await friendLinkApi.createFriendLink(form)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchFriendLinks()
  } catch (error) {
    logError('提交失败:', error)
  } finally {
    submitting.value = false
  }
}

/**
 * 对话框关闭时同步隐藏状态。
 *
 * @param done el-dialog 提供的关闭回调
 */
function handleClose(done: () => void) {
  dialogVisible.value = false
  done()
}

/**
 * 切换友链的显示/隐藏状态。
 *
 * <p>失败时重新加载列表：开关的视觉状态已被用户改过，不回滚会与服务端不一致。</p>
 *
 * @param id  友链 ID
 * @param val 目标显示状态
 */
async function handleStatusChange(id: number, val: boolean) {
  try {
    await friendLinkApi.updateFriendLinkStatus(id, val ? 1 : 0)
    ElMessage.success(val ? '已显示' : '已隐藏')
  } catch (error) {
    fetchFriendLinks()
    logError('状态修改失败:', error)
  }
}

/**
 * 删除单个友链（带二次确认）。
 *
 * @param id   友链 ID
 * @param name 友链名称，用于确认文案
 */
async function handleDelete(id: number, name: string) {
  try {
    await ElMessageBox.confirm(`确定要删除友链“${name}”吗？`, '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await friendLinkApi.deleteFriendLink(id)
    ElMessage.success('删除成功')
    fetchFriendLinks()
  } catch (error) {
    logError('删除失败:', error)
  }
}

/**
 * 复制友链地址到剪贴板。
 *
 * @param url 友链地址
 */
function copyUrl(url: string) {
  navigator.clipboard.writeText(url).then(() => {
    ElMessage.success('链接已复制')
  }).catch(() => {
    ElMessage.error('复制失败')
  })
}

/**
 * 格式化时间为本地时间字符串。
 *
 * @param dateStr 时间字符串
 */
function formatDate(dateStr: string) {
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN')
}

import { onMounted } from 'vue'
/** 初始化加载 */
onMounted(() => {
  fetchFriendLinks()
})
</script>

<style scoped>
.friend-link-list-page {
  padding: 0;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 6px;
  margin-bottom: 24px;
}

.table-card {
  height: 100%;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.3);
}

.table-card :deep(.el-table) {
  font-size: 13px;
  background: rgba(255, 255, 255, 0.3);
  border-radius: 12px;
  overflow: hidden;
}

.table-card :deep(.el-table__header-wrapper th) {
  background: rgba(255, 255, 255, 0.3);
  color: #000;
  font-weight: 600;
  font-size: 18px;
}

.link-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.link-avatar {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  object-fit: cover;
  background: #f5f7fa;
}

.avatar-preview {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  object-fit: cover;
  border: 2px solid #e2e3e5;
}

.avatar-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 20px;
  border: 2px dashed #dcdfe6;
  border-radius: 8px;
  background: #fafafa;
  cursor: pointer;
}

.upload-icon {
  font-size: 24px;
  color: #909399;
}

.avatar-placeholder p {
  margin: 0;
  font-size: 12px;
  color: #606266;
}

.link-name {
  margin: 0;
  font-size: 13px;
  font-weight: 500;
  color: #303133;
}

.link-url {
  margin: 4px 0 0;
  font-size: 11px;
  color: #909399;
  cursor: pointer;
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.link-url:hover {
  color: #409eff;
}
</style>