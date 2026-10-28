<template>
  <div class="category-list-page">
    <div class="page-header">
      <div class="header-actions">
        <el-button type="primary" size="" @click="handleAdd">
          <component :is="Plus" class="mr-1" />
          新增分类
        </el-button>
        <el-button type="danger" :disabled="!selectedIds.length" @click="handleBatchDelete">
          批量删除
        </el-button>
      </div>
    </div>

    <el-card class="table-card" shadow="never">
      <el-table
        v-loading="loading"
        :data="categories"
        border
        stripe
        row-key="id"
        style="width: 100%"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" />
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="name" label="分类名称" min-width="150" />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="sort" label="排序" width="80" align="center" />
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

    <el-dialog v-model="dialogVisible" :title="editId ? '编辑分类' : '新增分类'" width="500px" :before-close="handleClose">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="分类名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入分类名称" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" placeholder="请输入分类描述" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="图标">
          <el-input v-model="form.icon" placeholder="图标类名 (如: Folder, Document)" maxlength="100" />
        </el-form-item>
        <el-form-item label="排序" prop="sort">
          <el-input-number v-model="form.sort" :min="0" :max="9999" style="width: 100%" />
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
 * 后台分类管理页。
 *
 * <p>提供分类的新增、编辑、单个与批量删除。
 * 删除前后端会校验分类是否仍被文章引用（含草稿），被引用时返回 409。</p>
 */
import { logError } from '@/utils/logError'
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox, ElForm } from 'element-plus'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import { categoryApi, CategoryVO, CategoryDTO } from '@/api/category'

/** 表格加载中 */
const loading = ref(false)
/** 分类列表 */
const categories = ref<CategoryVO[]>([])
/** 新增/编辑对话框可见性 */
const dialogVisible = ref(false)
/** 提交中 */
const submitting = ref(false)
/** 正在编辑的分类 ID。为 null 表示新增 */
const editId = ref<number | null>(null)
/** 选中的分类 ID */
const selectedIds = ref<number[]>([])

/**
 * 记录选中的分类 ID。
 *
 * @param selection 当前选中的分类
 */
function handleSelectionChange(selection: CategoryVO[]) {
  selectedIds.value = selection.map(item => item.id)
}

/**
 * 批量删除分类（带二次确认）。
 *
 * <p>后端会在任一分类被引用时整体拒绝，并返回具体的冲突原因。</p>
 */
async function handleBatchDelete() {
  try {
    await ElMessageBox.confirm(`确定要删除选中的 ${selectedIds.value.length} 个分类吗？`, '确认批量删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await categoryApi.batchDeleteCategories(selectedIds.value)
    ElMessage.success('批量删除成功')
    selectedIds.value = []
    fetchCategories()
  } catch (error) {
    // 用户取消删除时不算错误
    if (error !== 'cancel' && error !== 'close') {
      logError('批量删除失败:', error)
    }
  }
}

/** 新增/编辑表单数据 */
const form = reactive<CategoryDTO>({
  id: undefined,
  name: '',
  description: '',
  icon: '',
  sort: 0
})

/** 表单校验规则 */
const rules = {
  name: [
    { required: true, message: '请输入分类名称', trigger: 'blur' },
    { max: 50, message: '名称长度不能超过50字符', trigger: 'blur' }
  ],
  sort: [
    { required: true, message: '请输入排序', trigger: 'blur' }
  ]
}

const formRef = ref<InstanceType<typeof ElForm>>()

/** 加载分类列表 */
async function fetchCategories() {
  loading.value = true
  try {
    const res = await categoryApi.getAdminCategories()
    categories.value = res
  } catch (error) {
    logError('获取分类列表失败:', error)
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
 * @param row 被编辑的分类
 */
function handleEdit(row: CategoryVO) {
  editId.value = row.id
  form.id = row.id
  form.name = row.name
  form.description = row.description || ''
  form.icon = row.icon || ''
  form.sort = row.sort
  dialogVisible.value = true
}

/** 重置表单并清除上一次的校验提示 */
function resetForm() {
  form.id = undefined
  form.name = ''
  form.description = ''
  form.icon = ''
  form.sort = 0
  formRef.value?.clearValidate()
}

/** 提交表单（按 editId 区分新增/更新） */
async function handleSubmit() {
  try {
    await formRef.value?.validate()
    submitting.value = true

    if (editId.value) {
      await categoryApi.updateCategory(editId.value, form)
      ElMessage.success('更新成功')
    } else {
      await categoryApi.createCategory(form)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchCategories()
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
 * 删除单个分类（带二次确认）。
 *
 * @param id   分类 ID
 * @param name 分类名称，用于确认文案
 */
async function handleDelete(id: number, name: string) {
  try {
    await ElMessageBox.confirm(`确定要删除分类“${name}”吗？`, '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await categoryApi.deleteCategory(id)
    ElMessage.success('删除成功')
    fetchCategories()
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
  fetchCategories()
})
</script>

<style scoped>
.category-list-page {
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

.icon-preview {
  font-size: 18px;
  color: #409eff;
}

.no-icon {
  color: #c0c4cc;
  font-size: 12px;
}
</style>