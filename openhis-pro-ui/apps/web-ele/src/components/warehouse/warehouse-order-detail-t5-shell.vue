<script setup lang="ts">
import { ElCard } from 'element-plus';

import WarehouseDocOrderHeadPanel from '#/components/warehouse/warehouse-doc-order-head-panel.vue';

defineOptions({ name: 'WarehouseOrderDetailT5Shell' });

interface Props {
  approverLabel?: string;
  approvalTimeText?: string;
  statusText?: string;
  statusTagType?: 'danger' | 'info' | 'success';
  loading?: boolean;
  /** 透传单据头标题；缺省为「单据信息」 */
  headPanelTitle?: string;
  /** 是否展示单据头底部审核横排 */
  showHeadAudit?: boolean;
}

withDefaults(defineProps<Props>(), {
  approverLabel: '暂无',
  approvalTimeText: '暂无',
  statusText: '暂无',
  statusTagType: 'info',
  loading: false,
  showHeadAudit: true,
});
</script>

<template>
  <div class="warehouse-order-detail-t5-shell flex h-full min-h-0 flex-1 flex-col gap-1.5">
    <WarehouseDocOrderHeadPanel
      :title="headPanelTitle ?? '单据信息'"
      :approver-label="approverLabel"
      :approval-time-text="approvalTimeText"
      :status-text="statusText"
      :status-tag-type="statusTagType"
      :show-audit-inline="showHeadAudit"
    >
      <slot name="form" />
    </WarehouseDocOrderHeadPanel>
    <div class="mb-2 shrink-0">
      <slot name="toolbar" />
    </div>
    <ElCard
      shadow="never"
      class="warehouse-order-detail-grid-card flex min-h-0 min-w-0 flex-1 flex-col !rounded-md border-[var(--el-border-color-extra-light)]"
      body-class="flex min-h-0 min-w-0 flex-1 flex-col !px-2 !pb-1 !pt-0"
      v-loading="loading"
    >
      <div class="warehouse-order-detail-grid-wrap flex min-h-0 flex-1 flex-col">
        <slot name="grid" />
      </div>
    </ElCard>
    <slot />
  </div>
</template>

<style scoped>
.warehouse-order-detail-grid-wrap :deep(.vxe-grid),
.warehouse-order-detail-grid-wrap :deep(.el-table) {
  flex: 1;
  min-height: 0;
}
</style>
