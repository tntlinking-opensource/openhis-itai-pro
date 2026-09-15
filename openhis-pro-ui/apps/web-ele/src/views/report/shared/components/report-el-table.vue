<script setup lang="ts">
import type { CSSProperties } from 'vue';

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

/**
 * 表头对齐：优先列 headerAlign / align；未声明时默认 center。
 * 费用、数量列应与单元格同向 right（见 frontend-report-query-search）。
 */
function resolveHeaderCellStyle({
  column,
}: {
  column: { headerAlign?: string; align?: string };
}): CSSProperties {
  const align = column.headerAlign || column.align || 'center';
  return { textAlign: align as CSSProperties['textAlign'] };
}
</script>

<template>
  <ElTable
    v-bind="$attrs"
    v-loading="loading"
    :stripe="stripe"
    border
    empty-text="暂无数据"
    :header-cell-style="resolveHeaderCellStyle"
    :class="REPORT_TABLE_CLASS"
  >
    <slot />
  </ElTable>
</template>

<style scoped lang="scss">
.report-list-page__table {
  /* 固定列表头走 EP 变量；打包后选择器优先级变化时避免回落白底 */
  --el-table-header-bg-color: var(--el-color-primary-light-9);

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

  /* 与 EP `.el-table__header-wrapper tr th.el-table-fixed-column--*` 同级，避免打包后回落白底 */
  :deep(.el-table__header-wrapper tr th.el-table-fixed-column--left),
  :deep(.el-table__header-wrapper tr th.el-table-fixed-column--right) {
    background-color: var(--el-color-primary-light-9);
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

  /*
   * 退费/退药/冲减：整行 danger，覆盖单元格默认色与金额列强调色。
   * ElTag 自带色不强制覆盖。
   */
  :deep(.el-table__body tr.report-list-page__refund-row > td.el-table__cell),
  :deep(.el-table__body tr.report-list-page__refund-row > td.el-table-fixed-column--left),
  :deep(.el-table__body tr.report-list-page__refund-row > td.el-table-fixed-column--right),
  :deep(.el-table__body tr.report-list-page__refund-row > td.el-table__cell .cell),
  :deep(.el-table__body tr.report-list-page__refund-row .report-list-page__amount-cell .cell),
  :deep(.el-table__body tr.report-list-page__refund-row .report-list-page__busno) {
    color: var(--el-color-danger);
  }

  :deep(.el-table__body tr.report-list-page__refund-row:hover > td.el-table__cell),
  :deep(.el-table__body tr.report-list-page__refund-row:hover > td.el-table-fixed-column--left),
  :deep(.el-table__body tr.report-list-page__refund-row:hover > td.el-table-fixed-column--right) {
    color: var(--el-color-danger);
  }

  /*
   * 账批≠实发批：warning 底色提示（不改正额）；可与退行 danger 字色并存。
   */
  :deep(.el-table__body tr.report-list-page__lot-mismatch-row > td.el-table__cell),
  :deep(.el-table__body tr.report-list-page__lot-mismatch-row > td.el-table-fixed-column--left),
  :deep(.el-table__body tr.report-list-page__lot-mismatch-row > td.el-table-fixed-column--right) {
    background-color: var(--el-color-warning-light-9);
  }

  :deep(.el-table__body tr.report-list-page__lot-mismatch-row:hover > td.el-table__cell),
  :deep(
    .el-table__body tr.report-list-page__lot-mismatch-row:hover > td.el-table-fixed-column--left
  ),
  :deep(
    .el-table__body tr.report-list-page__lot-mismatch-row:hover > td.el-table-fixed-column--right
  ) {
    background-color: var(--el-color-warning-light-8);
  }
}
</style>

<style lang="scss">
@use '../report-list-page-table-cells.scss';

/*
 * 表头字段间小竖条分割线（不满格，避免整列竖线）。
 * 不走 SFC scoped：院点打包曾丢失 scoped，须随 REPORT_TABLE_CLASS 外挂生效。
 */
.report-list-page__table {
  th.el-table__cell:not(.gutter):not(:last-child)::after {
    position: absolute;
    top: 50%;
    right: 0;
    z-index: 1;
    width: 1px;
    height: 14px;
    pointer-events: none;
    background: var(--el-color-primary-light-5);
    content: '';
    transform: translateY(-50%);
  }

  th.el-table__cell:has(+ .gutter)::after,
  th.el-table__cell:has(+ .report-list-page__col-group-start)::after {
    display: none;
  }

  /*
   * 账批≠实发批底色（外挂双保险：院点打包可能丢 scoped）。
   */
  .el-table__body tr.report-list-page__lot-mismatch-row > td.el-table__cell,
  .el-table__body tr.report-list-page__lot-mismatch-row > td.el-table-fixed-column--left,
  .el-table__body tr.report-list-page__lot-mismatch-row > td.el-table-fixed-column--right {
    background-color: var(--el-color-warning-light-9);
  }

  .el-table__body tr.report-list-page__lot-mismatch-row:hover > td.el-table__cell,
  .el-table__body tr.report-list-page__lot-mismatch-row:hover > td.el-table-fixed-column--left,
  .el-table__body tr.report-list-page__lot-mismatch-row:hover > td.el-table-fixed-column--right {
    background-color: var(--el-color-warning-light-8);
  }
}
</style>
