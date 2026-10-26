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
        :data="friendLinks"
        v-loading="loading"
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
                <p class="link-url" @click="copyUrl(scope.row.url)" title="点击复制">{{ scope.row.url }}</p>
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
              @change="val => handleStatusChange(scope.row.id, val)"
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
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox, ElForm } from 'element-plus'
import { Plus, Edit, Delete, CopyDocument, Picture } from '@element-plus/icons-vue'
import { friendLinkApi, FriendLinkVO, FriendLinkDTO } from '@/api/friendLink'
import { fileApi } from '@/api/file'

const loading = ref(false)
const friendLinks = ref<FriendLinkVO[]>([])
const dialogVisible = ref(false)
const submitting = ref(false)
const editId = ref<number | null>(null)
const selectedIds = ref<number[]>([])

function handleSelectionChange(selection: FriendLinkVO[]) {
  selectedIds.value = selection.map(item => item.id)
}

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
    console.error('批量删除失败:', error)
  }
}

const form = reactive<FriendLinkDTO>({
  id: undefined,
  name: '',
  url: '',
  avatar: '',
  description: '',
  sort: 0,
  status: 1
})

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

async function fetchFriendLinks() {
  loading.value = true
  try {
    const res = await friendLinkApi.getAllFriendLinks()
    friendLinks.value = res
  } catch (error) {
    console.error('获取友链列表失败:', error)
  } finally {
    loading.value = false
  }
}

function handleAdd() {
  editId.value = null
  resetForm()
  dialogVisible.value = true
}

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

async function handleAvatarUpload(file: any) {
  try {
    const res = await fileApi.upload(file.file || file)
    form.avatar = res.url
    ElMessage.success('头像上传成功')
  } catch (error) {
    ElMessage.error('头像上传失败')
  }
}

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
    console.error('提交失败:', error)
  } finally {
    submitting.value = false
  }
}

function handleClose(done: () => void) {
  dialogVisible.value = false
  done()
}

async function handleStatusChange(id: number, val: boolean) {
  try {
    await friendLinkApi.updateFriendLinkStatus(id, val ? 1 : 0)
    ElMessage.success(val ? '已显示' : '已隐藏')
  } catch (error) {
    fetchFriendLinks()
    console.error('状态修改失败:', error)
  }
}

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
    console.error('删除失败:', error)
  }
}

function copyUrl(url: string) {
  navigator.clipboard.writeText(url).then(() => {
    ElMessage.success('链接已复制')
  }).catch(() => {
    ElMessage.error('复制失败')
  })
}

function formatDate(dateStr: string) {
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN')
}

import { onMounted } from 'vue'
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

.table-card :deep(.el-table) {
  font-size: 13px;
}

.table-card :deep(.el-table__header-wrapper th) {
  background: #fafafa;
  color: #606266;
  font-weight: 600;
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