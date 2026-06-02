<!--
  库房单据头：标题 + 表单槽位 + 底部审核横排（订货申请 / 采购退货等共用）。
-->
<script lang="ts" setup>
import SectionTitle from '#/components/layouts/section-title.vue';
import WarehouseDocAuditInline from '#/components/warehouse/warehouse-doc-audit-inline.vue';

defineOptions({
  name: 'WarehouseDocOrderHeadPanel',
});

withDefaults(
  defineProps<{
    approvalTimeText?: string;
    approverLabel?: string;
    statusTagType?: 'danger' | 'info' | 'success';
    statusText?: string;
    /** 为 false 时隐藏底部审核人/时间/状态横排 */
    showAuditInline?: boolean;
    title?: string;
  }>(),
  {
    approvalTimeText: '暂无',
    approverLabel: '暂无',
    statusTagType: 'info',
    statusText: '暂无',
    showAuditInline: true,
    title: '单据信息',
  },
);
</script>

<template>
  <section
    class="warehouse-doc-order-head-panel shrink-0 rounded-md border border-[var(--el-border-color-extra-light)] bg-[var(--el-fill-color-blank)] px-2 py-1"
  >
    <header class="warehouse-doc-order-head-panel__header">
      <SectionTitle dense :title="title" />
    </header>
    <div class="warehouse-doc-order-head-panel__form">
      <slot />
    </div>
    <WarehouseDocAuditInline
      v-if="showAuditInline"
      :approver-label="approverLabel"
      :approval-time-text="approvalTimeText"
      :status-text="statusText"
      :status-tag-type="statusTagType"
    />
  </section>
</template>

<style scoped>
.warehouse-doc-order-head-panel__header {
  margin-bottom: 2px;
}

.warehouse-doc-order-head-panel__header :deep(.section-title--dense) {
  font-size: 12px;
  line-height: 1.15;
}

.warehouse-doc-order-head-panel__header :deep(.section-title--dense::before) {
  height: 10px;
}
</style>
