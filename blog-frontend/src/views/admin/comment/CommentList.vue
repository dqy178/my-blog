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
        v-loading="loading"
        :data="comments"
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
            <p class="comment-content" v-html="escape(scope.row.content)" />
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

      <div v-if="total > query.pageSize" class="pagination-wrapper">
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
/**
 * 后台评论管理页。
 *
 * <p>提供评论审核（通过/拒绝）、单条与批量删除。</p>
 *
 * <p>评论本身不携带文章标题，需要按 articleId 补查。
 * 这里用 {@code articleTitleCache} 缓存已查到的标题，避免翻页时重复请求同一篇文章。</p>
 */
import { logError } from '@/utils/logError'
import { ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Close, Delete } from '@element-plus/icons-vue'
import { commentApi, CommentVO, CommentQueryDTO } from '@/api/comment'
import { articleApi } from '@/api/article'
import { safeHtml } from '@/utils/safeHtml'

/** 表格加载中 */
const loading = ref(false)
/** 当前页评论 */
const comments = ref<CommentVO[]>([])
/** 评论总数 */
const total = ref(0)
/** 选中的评论 ID */
const selectedIds = ref<number[]>([])

/** 文章标题缓存：articleId → title */
const articleTitleCache = new Map<number, string>()

/** 选中的评论是否全部已通过审核 */
const allSelectedPassed = computed(() => selectedIds.value.length > 0 && selectedIds.value.every(id => comments.value.find(c => c.id === id)?.status === 1))
/** 选中的评论是否全部已拒绝 */
const allSelectedRejected = computed(() => selectedIds.value.length > 0 && selectedIds.value.every(id => comments.value.find(c => c.id === id)?.status === 2))

/** 查询条件。默认按创建时间倒序 */
const query = reactive<CommentQueryDTO>({
  pageNum: 1,
  pageSize: 10,
  status: undefined,
  sortField: 'createTime',
  sortOrder: 'desc'
})

/**
 * 记录选中的评论 ID。
 *
 * @param selection 当前选中的评论
 */
function handleSelectionChange(selection: CommentVO[]) {
  selectedIds.value = selection.map(item => item.id)
}

/** 批量删除选中的评论（带二次确认） */
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
    // 用户取消删除时不算错误
    if (error !== 'cancel' && error !== 'close') {
      logError('批量删除失败:', error)
    }
  }
}

/**
 * 批量通过审核。
 *
 * <p>先过滤掉已经是「通过」状态的评论，避免对同一条评论重复调用审核接口。</p>
 */
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
    logError('批量通过失败:', error)
  }
}

/**
 * 批量拒绝。
 *
 * <p>同样先过滤掉已经是「拒绝」状态的评论。</p>
 */
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
    logError('批量拒绝失败:', error)
  }
}

/**
 * 加载评论列表并补齐文章标题。
 *
 * <p>文章标题按 articleId 去重后并发补查（Promise.allSettled 保证单个失败不影响整体），
 * 查到的标题写入缓存，后续翻页可直接复用。</p>
 */
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
      } catch (e) { /* ignore */ }
    }))
  } catch (error) {
    logError('获取评论列表失败:', error)
  } finally {
    loading.value = false
  }
}

/**
 * 净化评论内容后再交给 v-html 渲染。
 *
 * <p>评论内容入库时已被后端净化过，这里前端再过一道，
 * 避免任何历史脏数据导致 XSS。</p>
 *
 * @param text 评论原文
 * @returns 净化后的 HTML
 */
function escape(text: string) {
  return safeHtml(text)
}

/**
 * 每页条数变化。
 *
 * @param size 新的每页条数
 */
function handleSizeChange(size: number) {
    query.pageSize = size
    // 改变每页条数后必须回到第 1 页：
    // 停留在第 3 页再把 pageSize 调大，后端返回的页码已超出总页数，列表会变成空白。
    query.pageNum = 1
    fetchComments()
  }

/**
 * 页码变化。
 *
 * @param page 新的页码
 */
function handleCurrentChange(page: number) {
  query.pageNum = page
  fetchComments()
}

/**
 * 审核单条评论。
 *
 * @param id     评论 ID
 * @param status 目标状态：1=通过，2=拒绝
 */
async function handleAudit(id: number, status: number) {
  try {
    await commentApi.updateCommentStatus(id, status)
    ElMessage.success(status === 1 ? '审核通过' : '已拒绝')
    fetchComments()
  } catch (error) {
    logError('审核失败:', error)
  }
}

/**
 * 删除单条评论（带二次确认）。
 *
 * @param id 评论 ID
 */
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
    logError('删除失败:', error)
  }
}

/**
 * 审核状态对应的标签类型。
 *
 * @param status 审核状态：0=待审核，1=通过，2=拒绝
 * @returns Element Plus tag 的 type
 */
function getStatusType(status: number) {
  return status === 1 ? 'success' : status === 0 ? 'warning' : 'danger'
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

import { onMounted, watch } from 'vue'
/** 初始化加载 */
onMounted(() => {
  fetchComments()
})

/**
 * 监听审核状态筛选变化：切回第 1 页并重新加载。
 */
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