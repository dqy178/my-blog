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
        v-loading="loading"
        :data="tags"
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
            <input v-model="form.color" type="color" style="width:32px; height:32px; border:none; cursor:pointer;" />
            <span>{{ form.color }}</span>
          </div>
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
 * 后台标签管理页。
 *
 * <p>提供标签的新增、编辑、单个与批量删除。
 * 删除前后端会校验标签是否仍被文章引用（含草稿），被引用时返回 409。</p>
 */
import { logError } from '@/utils/logError'
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox, ElForm } from 'element-plus'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import { tagApi, TagVO, TagDTO } from '@/api/tag'

/** 表格加载中 */
const loading = ref(false)
/** 标签列表 */
const tags = ref<TagVO[]>([])
/** 新增/编辑对话框可见性 */
const dialogVisible = ref(false)
/** 提交中 */
const submitting = ref(false)
/** 正在编辑的标签 ID。为 null 表示新增 */
const editId = ref<number | null>(null)
/** 选中的标签 ID */
const selectedIds = ref<number[]>([])

/**
 * 记录选中的标签 ID。
 *
 * @param selection 当前选中的标签
 */
function handleSelectionChange(selection: TagVO[]) {
  selectedIds.value = selection.map(item => item.id)
}

/**
 * 批量删除标签（带二次确认）。
 */
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
    // 用户取消删除时不算错误
    if (error !== 'cancel' && error !== 'close') {
      logError('批量删除失败:', error)
    }
  }
}

/** 新增/编辑表单数据。默认色与后端保持一致 */
const form = reactive<TagDTO>({
  id: undefined,
  name: '',
  color: '#409eff'
})

/** 表单校验规则 */
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

/**
 * 加载标签列表。
 *
 * <p>做了多层兼容：确保最终赋值的一定是数组，
 * 否则 el-table 内部调用 .map 会直接抛错，导致整页白屏。</p>
 */
async function fetchTags() {
  loading.value = true
  try {
const res = (await tagApi.getAdminTags()) as any
      // 多层兼容处理：确保 tags 始终是数组，防止 el-table 内部调用 .map 报错
      const raw = Array.isArray(res) ? res : (Array.isArray(res?.data) ? res.data : (res?.list || []))
    tags.value = Array.isArray(raw) ? raw : []
  } catch (error) {
    logError('获取标签列表失败:', error)
    tags.value = []
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
 * @param row 被编辑的标签
 */
function handleEdit(row: TagVO) {
  editId.value = row.id
  form.id = row.id
  form.name = row.name
  form.color = row.color
  dialogVisible.value = true
}

/** 重置表单并清除上一次的校验提示 */
function resetForm() {
  form.id = undefined
  form.name = ''
  form.color = '#409eff'
  formRef.value?.clearValidate()
}

/**
 * 提交表单（按 editId 区分新增/更新）。
 *
 * <p>更新成功后先就地改本地列表，让界面立刻反映新值，
 * 再由随后的 fetchTags 做最终校正。</p>
 */
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
    logError('提交失败:', error)
    ElMessage.error('保存失败，请查看控制台')
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
 * 删除单个标签（带二次确认）。
 *
 * @param id   标签 ID
 * @param name 标签名称，用于确认文案
 */
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
    logError('删除失败:', error)
  }
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

.table-card {
  height: 100%;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.3);
}

.table-card :deep(.el-table) {
  font-size: 16px;
  background: rgba(255, 255, 255, 0.3);
  border-radius: 12px;
  overflow: hidden;
}

.table-card :deep(.el-table__header-wrapper th) {
  background: rgba(255, 255, 255, 0.3);
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