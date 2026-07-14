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

defineEmits<{
  sizeChange: [size: number];
  currentChange: [page: number];
}>();
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
      @size-change="(size: number) => $emit('sizeChange', size)"
      @current-change="(page: number) => $emit('currentChange', page)"
    />
  </footer>
</template>
