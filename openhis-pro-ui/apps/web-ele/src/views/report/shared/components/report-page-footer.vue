<script setup lang="ts">
import { ElPagination } from 'element-plus';

export interface ReportSummaryItem {
  label: string;
  value: number | string;
  primary?: boolean;
  prefix?: string;
}

defineOptions({ name: 'ReportPageFooter' });

interface Props {
  summaryItems?: ReportSummaryItem[];
  currentPage: number;
  pageSize: number;
  total: number;
  pageSizes?: number[];
  layout?: string;
}

withDefaults(defineProps<Props>(), {
  summaryItems: () => [],
  pageSizes: () => [10, 20, 50, 100],
  layout: 'sizes, prev, pager, next, jumper',
});

const emit = defineEmits<{
  sizeChange: [size: number];
  currentChange: [page: number];
  'update:currentPage': [page: number];
  'update:pageSize': [size: number];
}>();

/** 页码变更：同步 v-model 并上抛，避免受控模式下视觉回弹。 */
function onCurrentChange(page: number) {
  emit('update:currentPage', page);
  emit('currentChange', page);
}

/** 每页条数变更：同步 v-model 并上抛。 */
function onSizeChange(size: number) {
  emit('update:pageSize', size);
  emit('sizeChange', size);
}
</script>

<template>
  <footer class="report-list-page__footer">
    <div v-if="summaryItems.length > 0" class="report-list-page__summary">
      <template v-for="(item, index) in summaryItems" :key="`${item.label}-${index}`">
        <div v-if="index > 0" class="report-list-page__summary-divider" />
        <div class="report-list-page__summary-item">
          <span class="report-list-page__summary-label">{{ item.label }}</span>
          <span
            class="report-list-page__summary-value"
            :class="{ 'report-list-page__summary-value--primary': item.primary }"
          >
            {{ item.prefix ?? '' }}{{ item.value }}
          </span>
        </div>
      </template>
    </div>
    <slot />
    <ElPagination
      :current-page="currentPage"
      :page-size="pageSize"
      :page-sizes="pageSizes"
      :total="total"
      :layout="layout"
      @update:current-page="onCurrentChange"
      @update:page-size="onSizeChange"
    />
  </footer>
</template>
