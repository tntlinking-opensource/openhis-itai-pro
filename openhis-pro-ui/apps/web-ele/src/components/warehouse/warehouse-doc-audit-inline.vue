<!--
  库房单据下方横排审核摘要：审核人 / 审核日期 / 审核状态（标签展示）。
  文案与展示形态由父组件计算后传入。
-->
<script lang="ts" setup>
defineOptions({
  name: 'WarehouseDocAuditInline',
});

withDefaults(
  defineProps<{
    approvalTimeText?: string;
    approverLabel?: string;
    statusTagType?: 'danger' | 'info' | 'success';
    statusText?: string;
  }>(),
  {
    approverLabel: '暂无',
    approvalTimeText: '暂无',
    statusText: '暂无',
    statusTagType: 'info',
  },
);
</script>

<template>
  <div
    class="warehouse-doc-audit-inline mt-0.5 flex flex-wrap items-center gap-x-1 gap-y-0.5 border-t border-dashed border-[var(--el-border-color-extra-light)] pt-1"
  >
    <span class="inline-flex min-w-0 max-w-full items-center gap-1">
      <span class="shrink-0 text-[12px] leading-tight text-[var(--el-text-color-secondary)]">
        审核人
      </span>
      <el-tag
        size="small"
        type="warning"
        plain
        class="!my-0 max-w-[min(40vw,180px)] truncate"
        :title="approverLabel"
      >
        {{ approverLabel }}
      </el-tag>
    </span>
    <span
      class="mx-1 hidden h-3 w-px shrink-0 bg-[var(--el-border-color-lighter)] sm:inline"
      aria-hidden="true"
    ></span>
    <span class="inline-flex min-w-0 max-w-full items-center gap-1">
      <span class="shrink-0 text-[12px] leading-tight text-[var(--el-text-color-secondary)]">
        审核日期
      </span>
      <el-tag
        size="small"
        type="primary"
        plain
        class="!my-0 max-w-[min(48vw,220px)] truncate"
        :title="approvalTimeText"
      >
        {{ approvalTimeText }}
      </el-tag>
    </span>
    <span
      class="mx-1 hidden h-3 w-px shrink-0 bg-[var(--el-border-color-lighter)] sm:inline"
      aria-hidden="true"
    ></span>
    <span class="inline-flex min-w-0 max-w-full items-center gap-1">
      <span class="shrink-0 text-[12px] leading-tight text-[var(--el-text-color-secondary)]">
        审核状态
      </span>
      <el-tag size="small" :type="statusTagType" plain class="!my-0">
        {{ statusText }}
      </el-tag>
    </span>
  </div>
</template>
