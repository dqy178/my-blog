<template>
  <div class="article-edit-page">
    <div class="page-header">
      <div class="header-actions">
        <el-button @click="goBack">返回列表</el-button>
        <el-button type="primary" @click="handleSave(0)" :loading="saving">
          保存草稿
        </el-button>
        <el-button type="success" @click="handleSave(1)" :loading="saving">
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
                  type="text"
                  size="small"
                  @click.stop="articleForm.cover = ''"
                  class="remove-cover"
                >
                  <component :is="Delete" /> 移除
                </el-button>
              </div>
              <div v-else class="cover-placeholder" @click="$refs.coverInput.click()">
                <component :is="Picture" class="upload-icon" />
                <p>点击上传封面图</p>
                <span>建议尺寸: 800x450px, 不超过2MB</span>
              </div>
            </el-upload>
            <input ref="coverInput" type="file" accept="image/*" style="display: none" @change="onCoverChange" />
          </div>
        </el-form-item>

        <el-form-item label="允许评论">
          <el-switch v-model="articleForm.allowComment" :active-value="1" :inactive-value="0" />
        </el-form-item>

        <el-form-item label="文章内容" prop="content">
          <MarkdownEditor
            v-model="articleForm.content"
            @image-upload="handleImageUpload"
          />
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Plus,
  ArrowLeft,
  Delete,
  Picture
} from '@element-plus/icons-vue'
import { articleApi, ArticleDTO, ArticleVO } from '@/api/article'
import { categoryApi, CategoryVO } from '@/api/category'
import { tagApi, TagVO } from '@/api/tag'
import { fileApi } from '@/api/file'
import MarkdownEditor from '@/components/MarkdownEditor.vue'

const router = useRouter()
const route = useRoute()

const isEdit = computed(() => !!route.params.id)
const articleId = computed(() => Number(route.params.id || 0))

const saving = ref(false)
const categories = ref<CategoryVO[]>([])
const tags = ref<TagVO[]>([])

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

async function fetchCategories() {
  try {
    const res = await categoryApi.getAllCategories()
    categories.value = res
  } catch (error) {
    console.error('获取分类失败:', error)
  }
}

async function fetchTags() {
  try {
    const res = await tagApi.getAllTags()
    tags.value = res
  } catch (error) {
    console.error('获取标签失败:', error)
  }
}

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
    console.error('获取文章详情失败:', error)
  }
}

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
    router.push('/admin/articles')
  } catch (error) {
    console.error('保存失败:', error)
  } finally {
    saving.value = false
  }
}

function goBack() {
  router.push('/admin/articles')
}

async function uploadCover(options: any) {
  const file = options.file
  try {
    const res = await fileApi.upload(file)
    articleForm.cover = res.url
    options.onSuccess()
  } catch (error) {
    options.onError()
  }
}

function onCoverChange(event: Event) {
  const input = event.target as HTMLInputElement
  if (input.files && input.files[0]) {
    const file = input.files[0]
    const formData = new FormData()
    formData.append('file', file)
    fileApi.upload(file).then(res => {
      articleForm.cover = res.url
    }).catch(() => {
      ElMessage.error('上传失败')
    })
    input.value = ''
  }
}

async function handleImageUpload(file: File) {
  try {
    const res = await fileApi.upload(file)
    return res.url
  } catch (error) {
    throw new Error('图片上传失败')
  }
}

onMounted(() => {
  fetchCategories()
  fetchTags()
  fetchArticle()
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