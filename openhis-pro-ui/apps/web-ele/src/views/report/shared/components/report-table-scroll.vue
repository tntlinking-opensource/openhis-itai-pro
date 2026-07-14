<script setup lang="ts">
import { useReportTableHeight } from '../use-report-table-height';

defineOptions({ name: 'ReportTableScroll' });

interface Props {
  initialHeight?: number;
}

const props = withDefaults(defineProps<Props>(), {
  initialHeight: 420,
});

const { tableWrapRef, tableHeight, updateTableHeight, bindTableResizeObserver } =
  useReportTableHeight(props.initialHeight);

defineExpose({
  tableWrapRef,
  tableHeight,
  updateTableHeight,
  bindTableResizeObserver,
});
</script>

<template>
  <div ref="tableWrapRef" class="report-list-page__table-scroll">
    <slot :table-height="tableHeight" />
  </div>
</template>
