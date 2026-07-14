<script setup lang="ts">
import { ElTable } from 'element-plus';

import { REPORT_TABLE_CLASS } from '../report-list-page-classes';

defineOptions({ name: 'ReportElTable', inheritAttrs: false });

interface Props {
  loading?: boolean;
  stripe?: boolean;
}

withDefaults(defineProps<Props>(), {
  stripe: true,
});
</script>

<template>
  <ElTable
    v-bind="$attrs"
    v-loading="loading"
    :stripe="stripe"
    border
    empty-text="暂无数据"
    :header-cell-style="{ textAlign: 'center' }"
    :class="REPORT_TABLE_CLASS"
  >
    <slot />
  </ElTable>
</template>

<style scoped lang="scss">
.report-list-page__table {
  width: 100%;
  font-size: 13px;

  :deep(.el-table__inner-wrapper) {
    &::before,
    &::after {
      display: none;
    }
  }

  :deep(.el-table__border-left-patch) {
    display: none;
  }

  :deep(th.el-table__cell) {
    height: 36px;
    padding: 0;
    font-size: 13px;
    font-weight: 600;
    letter-spacing: 0.02em;
    color: var(--el-text-color-primary);
    background: var(--el-color-primary-light-9);
    border-top: none;
    border-right: none !important;
    border-bottom: 2px solid var(--el-color-primary-light-5);
  }

  :deep(td.el-table__cell) {
    height: 35px;
    padding: 0;
    color: var(--el-text-color-regular);
    border-right: none !important;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }

  :deep(.el-table__body tr:last-child td.el-table__cell) {
    border-bottom: none;
  }

  :deep(.el-table__body tr.el-table__row--striped td.el-table__cell) {
    background: var(--el-fill-color-light);
  }

  :deep(.el-table__body tr.el-table__row--striped td.el-table-fixed-column--left),
  :deep(.el-table__body tr.el-table__row--striped td.el-table-fixed-column--right) {
    background: var(--el-fill-color-light);
  }

  /* 关闭行悬停变色：保持当前行背景（含斑马纹、业务行着色）。 */
  :deep(.el-table__body tr:hover > td.el-table__cell),
  :deep(.el-table__body tr:hover > td.el-table-fixed-column--left),
  :deep(.el-table__body tr:hover > td.el-table-fixed-column--right) {
    background-color: var(--el-table-tr-bg-color, var(--el-bg-color)) !important;
  }

  :deep(.el-table__body tr.el-table__row--striped:hover > td.el-table__cell),
  :deep(.el-table__body tr.el-table__row--striped:hover > td.el-table-fixed-column--left),
  :deep(.el-table__body tr.el-table__row--striped:hover > td.el-table-fixed-column--right) {
    background-color: var(--el-table-tr-bg-color, var(--el-fill-color-light)) !important;
  }

  :deep(.cell) {
    padding-right: 10px;
    padding-left: 10px;
    line-height: 1.35;
  }

  :deep(th.report-list-page__col-group-start),
  :deep(td.report-list-page__col-group-start) {
    border-left: 1px solid var(--el-border-color-lighter) !important;
  }

  :deep(.report-list-page__num-cell .cell) {
    font-variant-numeric: tabular-nums;
  }

  :deep(.report-list-page__amount-cell .cell) {
    font-weight: 500;
    color: var(--el-text-color-primary);
  }

  :deep(.el-table-fixed-column--left.is-last-column::before) {
    box-shadow: inset 8px 0 8px -8px rgb(15 23 42 / 10%);
  }
}
</style>

<style lang="scss">
@use '../report-list-page-table-cells.scss';
</style>
