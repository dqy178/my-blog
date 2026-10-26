<template>
  <div class="comment-list-page">
      <div class="page-header">
        <div class="header-actions">
          <el-button type="success" size="default" :disabled="!selectedIds.length || allSelectedPassed" @click="handleBatchPass">
            批量通过
          </el-button>
          <el-button type="danger" size="default" :disabled="!selectedIds.length || allSelectedRejected" @click="handleBatchReject">
            批量拒绝
          </el-button>
          <el-button type="danger" size="default" :disabled="!selectedIds.length" @click="handleBatchDelete">
            批量删除
          </el-button>
          <el-select
            v-model="query.status"
            placeholder="全部状态"
            clearable
            size="small"
            style="width: 90px"
          >
            <el-option label="已通过" :value="1" />
            <el-option label="待审核" :value="0" />
            <el-option label="已拒绝" :value="2" />
          </el-select>
        </div>
      </div>

    <el-card class="table-card" shadow="never">
      <el-table
        :data="comments"
        v-loading="loading"
        border
        stripe
        row-key="id"
        style="width: 100%"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" />
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column label="文章" min-width="180" show-overflow-tooltip>
          <template #default="scope">
            <router-link :to="`/article/${scope.row.articleId}`" target="_blank" class="table-link">
              {{ scope.row.articleTitle || `文章${scope.row.articleId}` }}
            </router-link>
          </template>
        </el-table-column>
        <el-table-column label="内容" min-width="250">
          <template #default="scope">
            <p v-html="escape(scope.row.content)" class="comment-content" />
          </template>
        </el-table-column>
        <el-table-column label="作者" width="140">
          <template #default="scope">
            <div class="author-info">
              <div>
                <p class="author-name">{{ scope.row.nickname }}</p>
                <p class="author-email">{{ scope.row.email }}</p>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="scope">
            <el-tag :type="getStatusType(scope.row.status)" size="small" :disable-transitions="false">
              {{ scope.row.status === 1 ? '已通过' : scope.row.status === 0 ? '待审核' : '已拒绝' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="80" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.isAdmin ? 'success' : 'info'" size="small">
              {{ scope.row.isAdmin ? '管理员' : '访客' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160" align="center">
          <template #default="scope">
            {{ formatDate(scope.row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" align="center" fixed="right">
          <template #default="scope">
            <el-dropdown trigger="click" size="small">
              <el-button link type="primary">操作</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item
                    v-if="scope.row.status !== 1"
                    @click="handleAudit(scope.row.id, 1)"
                  >
                    <component :is="Check" class="mr-2" />
                    通过
                  </el-dropdown-item>
                  <el-dropdown-item
                    v-if="scope.row.status !== 2"
                    divided
                    @click="handleAudit(scope.row.id, 2)"
                  >
                    <component :is="Close" class="mr-2" />
                    拒绝
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

      <div class="pagination-wrapper" v-if="total > pageSize">
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Close, Delete } from '@element-plus/icons-vue'
import { commentApi, CommentVO, CommentQueryDTO, PageResult } from '@/api/comment'
import { articleApi } from '@/api/article'
import { safeHtml } from '@/utils/safeHtml'

const loading = ref(false)
const comments = ref<CommentVO[]>([])
const total = ref(0)
const selectedIds = ref<number[]>([])

const articleTitleCache = new Map<number, string>()

const allSelectedPassed = computed(() => selectedIds.value.length > 0 && selectedIds.value.every(id => comments.value.find(c => c.id === id)?.status === 1))
const allSelectedRejected = computed(() => selectedIds.value.length > 0 && selectedIds.value.every(id => comments.value.find(c => c.id === id)?.status === 2))

const query = reactive<CommentQueryDTO>({
  pageNum: 1,
  pageSize: 10,
  status: undefined,
  sortField: 'createTime',
  sortOrder: 'desc'
})

function handleSelectionChange(selection: CommentVO[]) {
  selectedIds.value = selection.map(item => item.id)
}

async function handleBatchDelete() {
  try {
    await ElMessageBox.confirm(`确定要删除选中的 ${selectedIds.value.length} 条评论吗？此操作不可恢复。`, '确认批量删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await commentApi.batchDeleteComments(selectedIds.value)
    ElMessage.success('批量删除成功')
    selectedIds.value = []
    fetchComments()
  } catch (error) {
    console.error('批量删除失败:', error)
  }
}

async function handleBatchPass() {
  try {
    await ElMessageBox.confirm(`确定要将选中的 ${selectedIds.value.length} 条评论全部通过吗？`, '确认批量通过', {
      confirmButtonText: '通过',
      cancelButtonText: '取消',
      type: 'warning'
    })
    const toPass = selectedIds.value.filter(id => {
      const comment = comments.value.find(c => c.id === id)
      return comment && comment.status !== 1
    })
    await Promise.all(toPass.map(id => commentApi.updateCommentStatus(id, 1)))
    ElMessage.success('批量通过成功')
    selectedIds.value = []
    fetchComments()
  } catch (error) {
    console.error('批量通过失败:', error)
  }
}

async function handleBatchReject() {
  try {
    await ElMessageBox.confirm(`确定要将选中的 ${selectedIds.value.length} 条评论全部拒绝吗？`, '确认批量拒绝', {
      confirmButtonText: '拒绝',
      cancelButtonText: '取消',
      type: 'warning'
    })
    const toReject = selectedIds.value.filter(id => {
      const comment = comments.value.find(c => c.id === id)
      return comment && comment.status !== 2
    })
    await Promise.all(toReject.map(id => commentApi.updateCommentStatus(id, 2)))
    ElMessage.success('批量拒绝成功')
    selectedIds.value = []
    fetchComments()
  } catch (error) {
    console.error('批量拒绝失败:', error)
  }
}

async function fetchComments() {
  loading.value = true
  try {
    const res = await commentApi.getAdminCommentPage(query)
    comments.value = res.list
    total.value = res.total

    // 批量获取文章标题，填充到评论数据中
    const articleIds = [...new Set(comments.value.map(c => c.articleId))]
    await Promise.allSettled(articleIds.map(async (articleId) => {
      if (articleTitleCache.has(articleId)) {
        const title = articleTitleCache.get(articleId)
        for (const comment of comments.value) {
          if (comment.articleId === articleId) {
            comment.articleTitle = title
          }
        }
        return
      }
      try {
        const article = await articleApi.getArticleDetail(articleId)
        articleTitleCache.set(articleId, article.title)
        for (const comment of comments.value) {
          if (comment.articleId === articleId) {
            comment.articleTitle = article.title
          }
        }
      } catch (e) {}
    }))
  } catch (error) {
    console.error('获取评论列表失败:', error)
  } finally {
    loading.value = false
  }
}

function escape(text: string) {
  return safeHtml(text)
}

function handleSizeChange(size: number) {
  query.pageSize = size
  fetchComments()
}

function handleCurrentChange(page: number) {
  query.pageNum = page
  fetchComments()
}

async function handleAudit(id: number, status: number) {
  try {
    await commentApi.updateCommentStatus(id, status)
    ElMessage.success(status === 1 ? '审核通过' : '已拒绝')
    fetchComments()
  } catch (error) {
    console.error('审核失败:', error)
  }
}

async function handleDelete(id: number) {
  try {
    await ElMessageBox.confirm('确定要删除这条评论吗？此操作不可恢复。', '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await commentApi.deleteComment(id)
    ElMessage.success('删除成功')
    fetchComments()
  } catch (error) {
    console.error('删除失败:', error)
  }
}

function getStatusType(status: number) {
  return status === 1 ? 'success' : status === 0 ? 'warning' : 'danger'
}

function formatDate(dateStr: string) {
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN')
}

import { onMounted, watch } from 'vue'
onMounted(() => {
  fetchComments()
})

watch(() => query.status, () => {
  query.pageNum = 1
  fetchComments()
})
</script>

<style scoped>
.comment-list-page {
  padding: 0;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 6px;
  margin-bottom: 24px;
}

.header-filters {
  display: flex;
  gap: 12px;
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

.table-link {
  color: #409eff;
  text-decoration: none;
}

.table-link:hover {
  text-decoration: underline;
}

.comment-content {
  margin: 0;
  max-height: 200px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-word;
}

.author-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.author-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  object-fit: cover;
}

.author-name {
  margin: 0;
  font-size: 13px;
  font-weight: 500;
  color: #303133;
}

.author-email {
  margin: 0;
  font-size: 11px;
  color: #909399;
}

.text-muted {
  color: #c0c4cc;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-actions :deep(.el-select__wrapper) {
  height: 32px !important;
  min-height: 32px !important;
  line-height: 30px !important;
}

.pagination-wrapper {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>