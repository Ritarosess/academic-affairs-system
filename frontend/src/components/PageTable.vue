<template>
  <div class="page-table">
    <!-- $attrs 透传到 el-table，使调用方可以使用 type="selection"、
         @selection-change、@sort-change 等原生表格能力 -->
    <el-table
      v-bind="$attrs"
      v-loading="loading"
      :data="rows"
      border
      stripe
      :row-key="rowKey"
      size="default"
      :empty-text="emptyText"
    >
      <slot />
    </el-table>

    <div v-if="mode === 'server'" class="pagination-wrap">
      <el-pagination
        background
        :current-page="page"
        :page-size="size"
        :page-sizes="pageSizes"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </div>

    <div v-else-if="mode === 'client' && total > size" class="pagination-wrap">
      <el-pagination
        background
        :current-page="page"
        :page-size="size"
        :page-sizes="pageSizes"
        :total="total"
        layout="total, sizes, prev, pager, next"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

// 属性与事件全部透传给内部 el-table（避免落到根 div 上）
defineOptions({ inheritAttrs: false })

/**
 * 统一列表容器。
 * - mode="server"：配合后端 { total, page, size, records } 做服务端分页
 * - mode="client"：对已全量返回的数据做前端分页（如 /admin/org/*、/admin/semesters）
 * - mode="none"  ：不分页
 */
const props = defineProps({
  data: { type: [Array, Object], default: () => [] },
  loading: { type: Boolean, default: false },
  mode: {
    type: String,
    default: 'server',
    validator: (v) => ['server', 'client', 'none'].includes(v)
  },
  page: { type: Number, default: 1 },
  size: { type: Number, default: 10 },
  total: { type: Number, default: 0 },
  rowKey: { type: [String, Function], default: 'id' },
  emptyText: { type: String, default: '暂无数据' },
  pageSizes: { type: Array, default: () => [10, 20, 50, 100] }
})

const emit = defineEmits(['update:page', 'update:size', 'change'])

/** server 模式取 records，client 模式切片，none 模式全量 */
const rows = computed(() => {
  if (props.mode === 'server') {
    return props.data?.records || []
  }
  const list = Array.isArray(props.data) ? props.data : []
  if (props.mode === 'none') return list
  const start = (props.page - 1) * props.size
  return list.slice(start, start + props.size)
})

function onPageChange(next) {
  emit('update:page', next)
  emit('change')
}

function onSizeChange(next) {
  emit('update:size', next)
  emit('update:page', 1)
  emit('change')
}
</script>

<style scoped>
.page-table {
  width: 100%;
}
</style>
