<template>
  <div class="article-list-page">
    <div class="page-header">
      <div class="header-left">
        <el-button type="primary" @click="goCreate">
          <component :is="Plus" class="mr-1" />
          新建文章
        </el-button>
        <el-button type="danger" :disabled="!selectedRows.length" @click="handleBatchDelete">
          批量删除
        </el-button>
        <el-button type="success" :disabled="!selectedRows.length" @click="handleBatchPublish">
          批量发布
        </el-button>
        <el-button type="warning" :disabled="!selectedRows.length" @click="handleBatchUnpublish">
          批量下架
        </el-button>
        <el-tag v-if="query.status === 0" type="info" class="ml-2">草稿</el-tag>
        <el-tag v-else-if="query.status === 1" type="success" class="ml-2">已发布</el-tag>
      </div>
    </div>

    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="query" class="search-form">
        <el-form-item label="标题">
          <el-input v-model="query.keyword" placeholder="搜索标题" clearable @keydown.enter="handleSearch" style="width: 250px" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="query.categoryId" placeholder="全部分类" style="width: 180px" clearable>
            <el-option
              v-for="cate in categories"
              :key="cate.id"
              :label="cate.name"
              :value="cate.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部状态" style="width: 140px" clearable>
            <el-option label="草稿" :value="0" />
            <el-option label="已发布" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item label="置顶">
          <el-select v-model="query.isTop" placeholder="全部" style="width: 120px" clearable>
            <el-option label="置顶" :value="1" />
            <el-option label="普通" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <component :is="Search" class="mr-1" />
            搜索
          </el-button>
          <el-button @click="handleReset">
            <component :is="Refresh" class="mr-1" />
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <el-table
        :data="articles"
        v-loading="loading"
        border
        stripe
        row-key="id"
        style="width: 100%"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column label="分类" width="140">
          <template #default="scope">
            <el-tag size="small">{{ scope.row.categoryName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="标签" min-width="150" show-overflow-tooltip>
          <template #default="scope">
            <el-tag
              v-for="tag in (scope.row.tagNames || []).slice(0, 3)"
              :key="tag"
              size="small"
              effect="dark"
            >
              {{ tag }}
            </el-tag>
            <span v-if="(scope.row.tagNames || []).length > 3" class="tag-more">+{{ (scope.row.tagNames || []).length - 3 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="scope">
            <el-tag :type="getStatusType(scope.row.status)" size="small">
              {{ scope.row.status === 1 ? '已发布' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="置顶" width="80" align="center">
          <template #default="scope">
            <el-switch
              v-model="scope.row.isTop"
              :active-value="1"
              :inactive-value="0"
              @change="val => handleTopChange(scope.row.id, val)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="viewCount" label="阅读" width="80" align="center" />
        <el-table-column prop="commentCount" label="评论" width="80" align="center" />
        <el-table-column prop="createTime" label="创建时间" width="160" align="center">
          <template #default="scope">
            {{ formatDate(scope.row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="scope">
            <el-dropdown trigger="click">
              <el-button size="small" link type="primary">操作</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="goEdit(scope.row.id)">
                    <component :is="Edit" class="mr-2" />
                    编辑
                  </el-dropdown-item>
                  <el-dropdown-item
                    :disabled="scope.row.status === 1"
                    @click="handlePublish(scope.row.id)"
                  >
                    <component :is="Promotion" class="mr-2" />
                    发布
                  </el-dropdown-item>
                  <el-dropdown-item
                    :disabled="scope.row.status === 0"
                    @click="handleUnpublish(scope.row.id)"
                  >
                    <component :is="Close" class="mr-2" />
                    下架
                  </el-dropdown-item>
                  <el-dropdown-item divided @click="handleDelete(scope.row.id)">
                    <component :is="Delete" class="mr-2" />
                    删除
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="query.pageNum"
          :page-size="query.pageSize"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          :page-sizes="[10, 20, 50, 100]"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="deleteDialogVisible"
      title="确认删除"
      width="400px"
      :before-close="handleDeleteDialogClose"
    >
      <span>确定要删除选中的 {{ deleteCount }} 篇文章吗？此操作不可恢复。</span>
      <template #footer>
        <el-button @click="deleteDialogVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmBatchDelete" :loading="deleting">确定删除</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="publishDialogVisible"
      title="确认发布"
      width="400px"
      :before-close="handlePublishDialogClose"
    >
      <span>确定要发布选中的 {{ publishCount }} 篇文章吗？</span>
      <template #footer>
        <el-button @click="publishDialogVisible = false">取消</el-button>
        <el-button type="success" @click="confirmBatchPublish" :loading="publishing">确定发布</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="unpublishDialogVisible"
      title="确认下架"
      width="400px"
      :before-close="handleUnpublishDialogClose"
    >
      <span>确定要下架选中的 {{ unpublishCount }} 篇文章吗？</span>
      <template #footer>
        <el-button @click="unpublishDialogVisible = false">取消</el-button>
        <el-button type="warning" @click="confirmBatchUnpublish" :loading="unpublishing">确定下架</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus,
  Search,
  Refresh,
  Edit,
  Promotion,
  Close,
  Delete
} from '@element-plus/icons-vue'
import { articleApi, ArticleVO, ArticleQueryDTO, PageResult } from '@/api/article'
import { categoryApi, CategoryVO } from '@/api/category'

const router = useRouter()

const loading = ref(false)
const articles = ref<ArticleVO[]>([])
const total = ref(0)
const selectedRows = ref<ArticleVO[]>([])
const categories = ref<CategoryVO[]>([])
const deleteDialogVisible = ref(false)
const deleting = ref(false)
const deleteCount = ref(0)
const publishDialogVisible = ref(false)
const publishing = ref(false)
const publishCount = ref(0)
const unpublishDialogVisible = ref(false)
const unpublishing = ref(false)
const unpublishCount = ref(0)

const query = reactive<ArticleQueryDTO>({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  categoryId: undefined,
  status: undefined,
  isTop: undefined,
  sortField: 'createTime',
  sortOrder: 'desc'
})

async function fetchCategories() {
  try {
    const res = await categoryApi.getAllCategories()
    categories.value = res
  } catch (error) {
    console.error('获取分类失败:', error)
  }
}

async function fetchArticles() {
  loading.value = true
  try {
    const res = await articleApi.getAdminArticlePage(query)
    articles.value = res.list
    total.value = res.total
  } catch (error) {
    console.error('获取文章列表失败:', error)
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.pageNum = 1
  fetchArticles()
}

function handleReset() {
  query.keyword = ''
  query.categoryId = undefined
  query.status = undefined
  query.isTop = undefined
  fetchArticles()
}

function handleSelectionChange(rows: ArticleVO[]) {
  selectedRows.value = rows
}

function handleSizeChange(size: number) {
  query.pageSize = size
  fetchArticles()
}

function handleCurrentChange(page: number) {
  query.pageNum = page
  fetchArticles()
}

function goCreate() {
  router.push('/admin/articles/create')
}

function goEdit(id: number) {
  router.push(`/admin/articles/edit/${id}`)
}

async function handleTopChange(id: number, val: boolean) {
  try {
    await articleApi.updateArticleTop(id, val ? 1 : 0)
    ElMessage.success(val ? '已置顶' : '已取消置顶')
    fetchArticles()
  } catch (error) {
    fetchArticles()
    console.error('置顶操作失败:', error)
  }
}

async function handlePublish(id: number) {
  try {
    await articleApi.updateArticleStatus(id, 1)
    ElMessage.success('发布成功')
    fetchArticles()
  } catch (error) {
    console.error('发布失败:', error)
  }
}

async function handleUnpublish(id: number) {
  try {
    await articleApi.updateArticleStatus(id, 0)
    ElMessage.success('已下架')
    fetchArticles()
  } catch (error) {
    console.error('下架失败:', error)
  }
}

function handleDelete(id: number) {
  ElMessageBox.confirm('确定要删除这篇文章吗？此操作不可恢复。', '确认删除', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning',
    dangerouslyUseHTMLString: true
  }).then(async () => {
    try {
      await articleApi.deleteArticle(id)
      ElMessage.success('删除成功')
      fetchArticles()
    } catch (error) {
      console.error('删除失败:', error)
    }
  })
}

function handleBatchDelete() {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先选择要删除的文章')
    return
  }
  deleteCount.value = selectedRows.value.length
  deleteDialogVisible.value = true
}

function handleDeleteDialogClose(done: () => void) {
  deleteDialogVisible.value = false
  done()
}

async function confirmBatchDelete() {
  deleting.value = true
  try {
    const ids = selectedRows.value.map(r => r.id)
    await articleApi.batchDeleteArticles(ids)
    ElMessage.success('批量删除成功')
    selectedRows.value = []
    fetchArticles()
  } catch (error) {
    console.error('批量删除失败:', error)
  } finally {
    deleting.value = false
    deleteDialogVisible.value = false
  }
}

function handleBatchPublish() {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先选择要发布的文章')
    return
  }
  publishCount.value = selectedRows.value.length
  publishDialogVisible.value = true
}

function handlePublishDialogClose(done: () => void) {
  publishDialogVisible.value = false
  done()
}

async function confirmBatchPublish() {
  publishing.value = true
  try {
    const ids = selectedRows.value.map(r => r.id)
    await articleApi.batchUpdateArticleStatus(ids, 1)
    ElMessage.success('批量发布成功')
    selectedRows.value = []
    fetchArticles()
  } catch (error) {
    console.error('批量发布失败:', error)
  } finally {
    publishing.value = false
    publishDialogVisible.value = false
  }
}

function handleBatchUnpublish() {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先选择要下架的文章')
    return
  }
  unpublishCount.value = selectedRows.value.length
  unpublishDialogVisible.value = true
}

function handleUnpublishDialogClose(done: () => void) {
  unpublishDialogVisible.value = false
  done()
}

async function confirmBatchUnpublish() {
  unpublishing.value = true
  try {
    const ids = selectedRows.value.map(r => r.id)
    await articleApi.batchUpdateArticleStatus(ids, 0)
    ElMessage.success('批量下架成功')
    selectedRows.value = []
    fetchArticles()
  } catch (error) {
    console.error('批量下架失败:', error)
  } finally {
    unpublishing.value = false
    unpublishDialogVisible.value = false
  }
}

function getStatusType(status: number) {
  return status === 1 ? 'success' : 'info'
}

function formatDate(dateStr: string) {
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN')
}

onMounted(() => {
  fetchCategories()
  fetchArticles()
})
</script>

<style scoped>
.article-list-page {
  padding: 0;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 6px;
  margin-bottom: 24px;
}

.header-left {
  display: flex;
  align-items: center;
}

.search-card {
  margin-bottom: 20px;
}

.search-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.search-form :deep(.el-form-item__label) {
  font-size: 18px;
  font-weight: 700;
  color: #000;
}

.table-card :deep(.el-table) {
  font-size: 16px;
}

.table-card :deep(.el-table__header-wrapper th) {
  background: #fafafa;
  color: #000;
  font-weight: 700;
  font-size: 18px;
  height: 50px;
  line-height: 50px;
}

.tag-more {
  font-size: 12px;
  color: #909399;
  margin-left: 4px;
}

.pagination-wrapper {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>