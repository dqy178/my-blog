<template>
  <div class="tag-list-page">
    <div class="page-header">
      <div class="header-actions">
        <el-button type="primary" @click="handleAdd">
          <component :is="Plus" class="mr-1" />
          新增标签
        </el-button>
        <el-button type="danger" :disabled="!selectedIds.length" @click="handleBatchDelete">
          批量删除
        </el-button>
      </div>
    </div>

    <el-card class="table-card" shadow="never">
      <el-table
        :data="tags"
        v-loading="loading"
        border
        stripe
        row-key="id"
        style="width: 100%"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" />
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column label="标签名称" min-width="150">
          <template #default="scope">
            <el-tag :style="{ backgroundColor: scope.row.color + '20', color: scope.row.color }" effect="dark">
              {{ scope.row.name }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="颜色" width="120">
          <template #default="scope">
            <div class="color-cell">
              <span class="color-block" :style="{ backgroundColor: scope.row.color }" />
              <span>{{ scope.row.color }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="articleCount" label="文章数" width="80" align="center" />
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
            <el-button
              size="small"
              type="danger"
              link
              :disabled="(scope.row.referenceCount ?? 0) > 0 || scope.row.articleCount > 0"
              @click="handleDelete(scope.row.id, scope.row.name)"
            >
              <component :is="Delete" class="mr-1" />
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editId ? '编辑标签' : '新增标签'" width="450px" :before-close="handleClose">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="标签名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入标签名称" maxlength="30" show-word-limit />
        </el-form-item>
        <el-form-item label="颜色" prop="color">
          <div style="display:flex; align-items:center; gap:8px;">
            <input type="color" v-model="form.color" style="width:32px; height:32px; border:none; cursor:pointer;" />
            <span>{{ form.color }}</span>
          </div>
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
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import { tagApi, TagVO, TagDTO } from '@/api/tag'

const loading = ref(false)
const tags = ref<TagVO[]>([])
const dialogVisible = ref(false)
const submitting = ref(false)
const editId = ref<number | null>(null)
const selectedIds = ref<number[]>([])

function handleSelectionChange(selection: TagVO[]) {
  selectedIds.value = selection.map(item => item.id)
}

async function handleBatchDelete() {
  try {
    await ElMessageBox.confirm(`确定要删除选中的 ${selectedIds.value.length} 个标签吗？`, '确认批量删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await tagApi.batchDeleteTags(selectedIds.value)
    ElMessage.success('批量删除成功')
    selectedIds.value = []
    fetchTags()
  } catch (error) {
    console.error('批量删除失败:', error)
  }
}

const form = reactive<TagDTO>({
  id: undefined,
  name: '',
  color: '#409eff'
})

const rules = {
  name: [
    { required: true, message: '请输入标签名称', trigger: 'blur' },
    { max: 30, message: '名称长度不能超过30字符', trigger: 'blur' }
  ],
  color: [
    { required: true, message: '请选择颜色', trigger: 'blur' }
  ]
}

const formRef = ref<InstanceType<typeof ElForm>>()

async function fetchTags() {
  loading.value = true
  try {
    const res = await tagApi.getAdminTags()
    // 多层兼容处理：确保 tags 始终是数组，防止 el-table 内部调用 .map 报错
    const raw = Array.isArray(res) ? res : (Array.isArray(res?.data) ? res.data : (res?.list || []))
    tags.value = Array.isArray(raw) ? raw : []
  } catch (error) {
    console.error('获取标签列表失败:', error)
    tags.value = []
  } finally {
    loading.value = false
  }
}

function handleAdd() {
  editId.value = null
  resetForm()
  dialogVisible.value = true
}

function handleEdit(row: TagVO) {
  editId.value = row.id
  form.id = row.id
  form.name = row.name
  form.color = row.color
  dialogVisible.value = true
}

function resetForm() {
  form.id = undefined
  form.name = ''
  form.color = '#409eff'
  formRef.value?.clearValidate()
}

async function handleSubmit() {
  try {
    await formRef.value?.validate()
    submitting.value = true

    if (editId.value) {
      await tagApi.updateTag(editId.value, form)
      ElMessage.success('更新成功')
      // 立即更新本地列表，无需等待异步刷新
      const idx = tags.value.findIndex(t => t.id === editId.value)
      if (idx !== -1) {
        tags.value[idx].name = form.name
        tags.value[idx].color = form.color
      }
    } else {
      await tagApi.createTag(form)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchTags()
  } catch (error) {
    console.error('提交失败:', error)
    ElMessage.error('保存失败，请查看控制台')
  } finally {
    submitting.value = false
  }
}

function handleClose(done: () => void) {
  dialogVisible.value = false
  done()
}

async function handleDelete(id: number, name: string) {
  try {
    await ElMessageBox.confirm(`确定要删除标签“${name}”吗？`, '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await tagApi.deleteTag(id)
    ElMessage.success('删除成功')
    fetchTags()
  } catch (error) {
    console.error('删除失败:', error)
  }
}

function formatDate(dateStr: string) {
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN')
}

import { onMounted } from 'vue'
onMounted(() => {
  fetchTags()
})
</script>

<style scoped>
.tag-list-page {
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
  font-size: 16px;
}

.table-card :deep(.el-table__header-wrapper th) {
  background: #fafafa;
  color: #000;
  font-weight: 700;
  font-size: 18px;
}

.color-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.color-block {
  width: 20px;
  height: 20px;
  border-radius: 4px;
  border: 1px solid #ebeef5;
}
</style>