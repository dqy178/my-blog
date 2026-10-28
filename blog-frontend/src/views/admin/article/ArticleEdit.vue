<template>
  <div class="article-edit-page">
    <div class="page-header">
      <div class="header-actions">
        <el-button @click="goBack">返回列表</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave(0)">
          保存草稿
        </el-button>
        <el-button type="success" :loading="saving" @click="handleSave(1)">
          发布文章
        </el-button>
      </div>
    </div>

    <el-card class="editor-card" shadow="never">
      <el-form ref="articleFormRef" :model="articleForm" :rules="articleRules" label-width="100px">
        <el-form-item label="文章标题" prop="title">
          <el-input v-model="articleForm.title" placeholder="请输入文章标题" maxlength="100" show-word-limit size="large" style="height: 48px" />
        </el-form-item>

        <el-form-item label="文章摘要" prop="summary">
          <el-input
            v-model="articleForm.summary"
            type="textarea"
            :rows="6"
            placeholder="请输入文章摘要 (用于列表展示和SEO)"
            maxlength="300"
            show-word-limit
            size="large"
          />
        </el-form-item>

        <el-form-item label="分类" prop="categoryId">
          <el-select v-model="articleForm.categoryId" placeholder="请选择分类" clearable style="width: 300px">
            <el-option
              v-for="cate in categories"
              :key="cate.id"
              :label="cate.name"
              :value="cate.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="标签" prop="tagIds">
          <el-select v-model="articleForm.tagIds" placeholder="请选择标签" multiple clearable style="width: 500px" collapse-tags>
            <el-option
              v-for="tag in tags"
              :key="tag.id"
              :label="tag.name"
              :value="tag.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="封面图">
          <div class="cover-upload">
            <el-upload
              action="#"
              :http-request="uploadCover"
              :auto-upload="true"
              :show-file-list="false"
              accept="image/*"
            >
              <div v-if="articleForm.cover" class="cover-preview">
                <img :src="articleForm.cover" alt="封面预览" />
                <el-button
                  link
                  size="small"
                  class="remove-cover"
                  @click.stop="articleForm.cover = ''"
                >
                  <component :is="Delete" /> 移除
                </el-button>
              </div>
              <!-- el-upload 的根节点自身就会触发文件选择框，
                   这里不能再挂 openCoverPicker，否则一次点击会弹出两个文件对话框 -->
              <div v-else class="cover-placeholder">
                <component :is="Picture" class="upload-icon" />
                <p>点击上传封面图</p>
                <span>建议尺寸: 800x450px, 不超过2MB</span>
              </div>
            </el-upload>
          </div>
        </el-form-item>

        <el-form-item label="允许评论">
          <el-switch v-model="articleForm.allowComment" :active-value="1" :inactive-value="0" />
        </el-form-item>

        <el-form-item label="文章内容" prop="content">
          <MarkdownEditor
            v-model="articleForm.content"
            :image-upload="handleImageUpload"
          />
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
/**
 * 文章编辑页（新建与编辑复用同一组件）。
 *
 * <p>是否处于编辑态由路由参数 {@code route.params.id} 判定：没有 id 是新建，
 * 有 id 是编辑。两个入口的差异全部由该 computed 驱动，不需要拆成两个组件。</p>
 *
 * <p>离开页面时若表单有未保存改动会弹出确认框，避免正在写的文章被无声丢弃。</p>
 */
import { logError } from '@/utils/logError'
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { useRouter, useRoute, onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Delete,
  Picture
} from '@element-plus/icons-vue'
import { articleApi, ArticleDTO } from '@/api/article'
import { categoryApi, CategoryVO } from '@/api/category'
import { tagApi, TagVO } from '@/api/tag'
import { fileApi } from '@/api/file'
import MarkdownEditor from '@/components/MarkdownEditor.vue'

const router = useRouter()
const route = useRoute()

/** 是否为编辑模式（有路由 id 即为编辑） */
const isEdit = computed(() => !!route.params.id)
/** 当前编辑的文章 ID */
const articleId = computed(() => Number(route.params.id || 0))

/** 保存中标记 */
const saving = ref(false)
/** 分类下拉选项 */
const categories = ref<CategoryVO[]>([])
/** 标签下拉选项 */
const tags = ref<TagVO[]>([])
// 表单是否有未保存的改动，用于离开页面时提醒
let dirty = false

/** 表单数据 */
const articleForm = reactive<ArticleDTO>({
  id: undefined,
  title: '',
  summary: '',
  content: '',
  cover: '',
  categoryId: undefined,
  status: 0,
  isTop: 0,
  allowComment: 1,
  tagIds: []
})

/** 表单校验规则 */
const articleRules = {
  title: [
    { required: true, message: '请输入文章标题', trigger: 'blur' },
    { max: 100, message: '标题长度不能超过100字符', trigger: 'blur' }
  ],
  summary: [
    { max: 300, message: '摘要长度不能超过300字符', trigger: 'blur' }
  ],
  categoryId: [
    { required: true, message: '请选择分类', trigger: 'change' }
  ],
  content: [
    { required: true, message: '请输入文章内容', trigger: 'blur' }
  ]
}

const articleFormRef = ref()

/** 加载分类下拉选项 */
async function fetchCategories() {
  try {
    const res = await categoryApi.getAllCategories()
    categories.value = res
  } catch (error) {
    logError('获取分类失败:', error)
  }
}

/** 加载标签下拉选项 */
async function fetchTags() {
  try {
    const res = await tagApi.getAllTags()
    tags.value = res
  } catch (error) {
    logError('获取标签失败:', error)
  }
}

/**
 * 加载待编辑文章并回填表单。
 *
 * <p>新建模式下直接返回，不发请求。标签需要从 {@code tags} 对象列表转成 ID 数组，
 * 因为表单提交的是 tagIds。</p>
 */
async function fetchArticle() {
  if (!isEdit.value) return
  try {
    const res = await articleApi.getAdminArticleDetail(articleId.value)
    if (res) {
      Object.assign(articleForm, {
        id: res.id,
        title: res.title,
        summary: res.summary,
        content: res.content,
        cover: res.cover,
        categoryId: res.categoryId,
        status: res.status,
        isTop: res.isTop,
        allowComment: res.allowComment,
        tagIds: res.tags?.map(t => t.id) || []
      })
    }
  } catch (error) {
    logError('获取文章详情失败:', error)
  }
}

/**
 * 保存文章。
 *
 * @param status 目标状态：0=存为草稿，1=发布
 */
async function handleSave(status: number) {
  try {
    await articleFormRef.value?.validate()
    saving.value = true
    articleForm.status = status

    if (isEdit.value) {
      await articleApi.updateArticle(articleId.value, articleForm)
      ElMessage.success(status === 1 ? '发布成功' : '保存草稿成功')
    } else {
      await articleApi.createArticle(articleForm)
      ElMessage.success(status === 1 ? '发布成功' : '保存草稿成功')
    }
    dirty = false
    router.push('/admin/articles')
  } catch (error) {
    logError('保存失败:', error)
  } finally {
    saving.value = false
  }
}

/** 返回文章列表页 */
function goBack() {
  router.push('/admin/articles')
}

/**
 * 封面上传回调（Element Plus el-upload 的 httpRequest）。
 *
 * @param options el-upload 传入的选项对象
 */
function uploadCover(options: any) {
  // 不用 async：async 返回 Promise 时 Element Plus 会在 resolve 后自动再调一次
  // options.onSuccess，与手动调用叠加会让 on-success 回调触发两次。
  // .then() 形式下 httpRequest 返回值不是 Promise，回调只会被调用一次。
  fileApi.upload(options.file)
    .then((res) => {
      articleForm.cover = res.url
      options.onSuccess(res)
    })
    .catch((error) => {
      logError('封面上传失败', error)
      options.onError()
      // show-file-list 为 false 时 el-upload 不会显示任何错误，必须自己提示
      ElMessage.error('封面上传失败，请重试')
    })
}

/**
 * Markdown 编辑器的图片上传回调。
 *
 * <p>返回 Promise 是编辑器契约的一部分：编辑器拿到 URL 后插入 Markdown 图片语法。
 * 失败时抛出异常，由编辑器捕获并提示。</p>
 *
 * @param file 待上传的图片
 * @returns 图片的可访问 URL
 */
async function handleImageUpload(file: File) {
  try {
    const res = await fileApi.upload(file)
    return res.url
  } catch (error) {
    ElMessage.error('图片上传失败，请重试')
    throw new Error('图片上传失败')
  }
}

/** 初始化：加载分类、标签与文章详情 */
onMounted(() => {
  fetchCategories()
  fetchTags()
  fetchArticle()
})

// 路由参数变化时重新加载。
// /admin/articles/edit/:id 与 /admin/articles/create 复用同一个组件，
// vue-router 在「同组件不同参数」时会复用实例而不重新 mount，
// 若不监听参数变化，表单里仍是上一篇的内容，保存时会写进另一篇文章。
watch(
  () => route.params.id,
  async (newId, oldId) => {
    if (newId === oldId) return
    resetForm()
    await fetchArticle()
  }
)

/**
 * 重置表单为初始状态。
 *
 * <p>切换编辑目标时调用，清掉上一篇的内容与 dirty 标记。</p>
 */
function resetForm() {
  Object.assign(articleForm, {
    id: undefined,
    title: '',
    summary: '',
    content: '',
    cover: '',
    categoryId: undefined,
    status: 0,
    isTop: 0,
    allowComment: 1,
    tagIds: []
  })
}

/**
 * 离开页面前提醒：正在写的文章不能被无声丢弃。
 *
 * <p>监听标题/摘要/正文/封面四个字段——它们覆盖了作者真正编辑的内容，
 * 改动任意一项就标记为 dirty。</p>
 */
watch(
  () => [articleForm.title, articleForm.summary, articleForm.content, articleForm.cover],
  () => {
    dirty = true
  }
)

/**
 * 路由离开守卫：有未保存改动时弹出确认框。
 *
 * <p>用户选择「离开」则放行（next()），选择「继续编辑」则拦截（next(false)）。</p>
 */
onBeforeRouteLeave((_to, _from, next) => {
  if (!dirty) {
    next()
    return
  }
  ElMessageBox.confirm('当前文章尚未保存，确定要离开吗？', '提示', {
    confirmButtonText: '离开',
    cancelButtonText: '继续编辑',
    type: 'warning'
  })
    .then(() => next())
    .catch(() => next(false))
})
</script>

<style scoped>
.article-edit-page {
  padding: 0;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 6px;
  margin-bottom: 24px;
}

.header-actions {
  display: flex;
  gap: 12px;
}

.editor-card :deep(.el-form-item) {
  margin-bottom: 24px;
}

.editor-card :deep(.el-form-item__label) {
  font-weight: 500;
  color: #303133;
}

.cover-upload {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.cover-preview {
  position: relative;
  width: 300px;
  height: 168px;
  border-radius: 8px;
  overflow: hidden;
  background: #f5f7fa;
}

.cover-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.remove-cover {
  position: absolute;
  top: 8px;
  right: 8px;
  background: rgba(0, 0, 0, 0.6);
  color: #fff;
  border-radius: 4px;
}

.cover-placeholder {
  width: 300px;
  height: 168px;
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

.cover-placeholder:hover {
  border-color: #409eff;
  background: #f0f9ff;
}

.upload-icon {
  font-size: 32px;
  color: #909399;
  margin-bottom: 8px;
}

.cover-placeholder p {
  font-size: 14px;
  color: #606266;
  margin: 0 0 4px;
}

.cover-placeholder span {
  font-size: 12px;
  color: #c0c4cc;
}

:deep(.el-textarea__inner) {
    min-height: 240px;
    resize: vertical;
    font-size: 16px;
  }
</style>