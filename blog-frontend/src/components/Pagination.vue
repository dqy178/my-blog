<template>
  <div v-if="total > pageSize" class="pagination">
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
/**
 * 通用分页组件。
 *
 * <p>对外只暴露一个 {@code pageChange} 事件，把页码与每页条数的变化统一上抛，
 * 由父组件负责重新请求数据。</p>
 */
import { ref, watch } from 'vue'

interface Props {
  /** 当前页码，从 1 开始 */
  currentPage: number
  /** 每页条数 */
  pageSize: number
  /** 记录总数 */
  total: number
}

const props = defineProps<Props>()
const emit = defineEmits<{ pageChange: [page: number, size?: number] }>()

/** 组件内部的当前页码（受控同步） */
const currentPage = ref(props.currentPage)

// 外部页码变化时同步到内部，避免父组件重置页码后组件仍显示旧页
watch(() => props.currentPage, (val) => {
  currentPage.value = val
})

/**
 * 每页条数变化。
 *
 * <p>切回第 1 页：若停留在第 5 页而每页条数变大，之前的第 5 页可能已不存在，
 * 保持在原页码会得到空列表。</p>
 */
function handleSizeChange(size: number) {
  // 必须把新页大小透传给父组件：
  // el-pagination 在 pageSize 受控时不会更新内部值，只 emit size-change，
  // 若父组件的 pageSize 不变，「每页条数」下拉框就是个摆设。
  emit('pageChange', 1, size)
}

/**
 * 页码变化。
 */
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