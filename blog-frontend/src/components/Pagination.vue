<template>
  <div class="pagination" v-if="total > pageSize">
    <el-pagination
      v-model:current-page="currentPage"
      :page-size="pageSize"
      :total="total"
      layout="total, sizes, prev, pager, next, jumper"
      :page-sizes="[10, 20, 50, 100]"
      @size-change="handleSizeChange"
      @current-change="handleCurrentChange"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

interface Props {
  currentPage: number
  pageSize: number
  total: number
}

const props = defineProps<Props>()
const emit = defineEmits<{ pageChange: [page: number] }>()

const currentPage = ref(props.currentPage)

watch(() => props.currentPage, (val) => {
  currentPage.value = val
})

function handleSizeChange(size: number) {
  emit('pageChange', 1)
}

function handleCurrentChange(page: number) {
  emit('pageChange', page)
}
</script>

<style scoped>
.pagination {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}
</style>