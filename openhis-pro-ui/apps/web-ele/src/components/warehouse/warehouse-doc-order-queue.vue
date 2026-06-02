<!--
  库房单据工作站：左侧「单据队列」列表卡片（与 warehouse-doc-workstation #aside-queue 配合）。
  业务页传入解析单据号、选中态、审批判断与日期格式化即可复用。
-->
<script setup lang="ts">
import { Calendar, Document, Files, Shop, User } from '@element-plus/icons-vue';
import { ElButton, ElCard, ElTag } from 'element-plus';

defineOptions({ name: 'WarehouseDocOrderQueue' });

const props = withDefaults(
  defineProps<{
    /** 左侧单据列表数据 */
    orders: Record<string, unknown>[];
    /** 从行数据解析展示用单据号（含 supplyBusNo / busNo 等别名） */
    resolveBusNo: (row: Record<string, unknown>) => string | undefined;
    /** 当前行是否选中（通常按单据号与选中行比较） */
    isSelected: (row: Record<string, unknown>) => boolean;
    /** 是否审批通过，用于审核状态 Tag 类型 */
    isApproved: (row: Record<string, unknown>) => boolean;
    /** 单据日期展示格式化 */
    formatApplyTime: (value: unknown) => string;
    /** 是否展示供应商一行（订货单 true；损益等无供应商则 false） */
    showSupplier?: boolean;
    /** 空列表文案 */
    emptyDescription?: string;
    /** 第一行经办人标签文案（默认采购员） */
    applicantRoleLabel?: string;
  }>(),
  {
    showSupplier: true,
    emptyDescription: '暂无单据',
    applicantRoleLabel: '采购员',
  },
);

const emit = defineEmits<{
  /** 选中某条单据 */
  select: [row: Record<string, unknown>];
  /** 空态点击「新增单据」 */
  add: [];
}>();

/** 点击卡片：请求父级切换当前单据并联查明细 */
function handleRowClick(row: Record<string, unknown>) {
  emit('select', row);
}

/** 空列表时引导新增 */
function handleEmptyAdd() {
  emit('add');
}
</script>

<template>
  <div class="warehouse-doc-order-queue flex flex-col gap-2">
    <template v-if="orders.length > 0">
      <div
        v-for="(item, orderIdx) in orders"
        :key="resolveBusNo(item) ?? `order-${orderIdx}`"
        class="order-item"
        :class="{ 'is-active': isSelected(item) }"
        @click="handleRowClick(item)"
      >
        <ElCard class="order-card !border-0" shadow="hover">
          <div class="order-header">
            <div class="order-number">
              <el-icon><Document /></el-icon>
              <span class="number-text">{{ resolveBusNo(item) }}</span>
            </div>
          </div>
          <div class="order-content">
            <div class="info-item">
              <el-icon class="info-icon"><User /></el-icon>
              <span class="label">{{ applicantRoleLabel }}</span>
              <span class="value">{{ item.applicantId_dictText }}</span>
            </div>
            <div v-if="showSupplier" class="info-item">
              <el-icon class="info-icon"><Shop /></el-icon>
              <span class="label">供应商</span>
              <span class="value">{{ item.supplierId_dictText }}</span>
            </div>
            <div class="info-item">
              <el-icon class="info-icon"><User /></el-icon>
              <span class="label">审核状态</span>
              <ElTag v-if="isApproved(item)" type="success" size="small">
                {{ item.statusEnum_enumText }}
              </ElTag>
              <ElTag v-else type="danger" size="small">
                {{ item.statusEnum_enumText }}
              </ElTag>
            </div>
            <div class="info-item">
              <el-icon class="info-icon"><Files /></el-icon>
              <span class="label">单据类型</span>
              <span class="value">{{ item.typeEnum_enumText }}</span>
            </div>
            <div class="info-item">
              <el-icon class="info-icon"><Calendar /></el-icon>
              <span class="label">单据日期</span>
              <span class="value">{{ formatApplyTime(item.applyTime) }}</span>
            </div>
          </div>
        </ElCard>
      </div>
    </template>
    <div v-else class="empty-state flex flex-col items-center justify-center py-8">
      <el-empty :description="emptyDescription" :image-size="72">
        <ElButton type="primary" @click="handleEmptyAdd">新增单据</ElButton>
      </el-empty>
    </div>
  </div>
</template>

<style scoped lang="scss">
.order-item {
  width: 100%;
  cursor: pointer;
  border-radius: var(--el-border-radius-base);

  &:hover .order-card {
    background-color: var(--el-fill-color);
  }

  &.is-active .order-card {
    background-color: var(--el-color-primary-light-9);
    box-shadow: inset 0 0 0 1px var(--el-color-primary-light-5);
  }

  &.is-active .order-number .number-text {
    color: var(--el-color-primary);
    font-weight: 600;
  }
}

.order-card {
  border-radius: var(--el-border-radius-base);
  background: var(--el-fill-color-light);
  transition:
    background-color 0.2s ease,
    box-shadow 0.2s ease;

  :deep(.el-card__body) {
    padding: 10px;
  }
}

.order-header {
  display: flex;
  align-items: center;
  margin-bottom: 6px;
}

.order-number {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  gap: 6px;

  .el-icon {
    flex-shrink: 0;
    font-size: 14px;
    color: var(--el-color-primary);
  }

  .number-text {
    overflow: hidden;
    font-size: 13px;
    font-weight: 500;
    color: var(--el-text-color-primary);
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.order-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-item {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 6px;
  padding: 2px 0;
  font-size: 12px;

  .info-icon {
    flex-shrink: 0;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  .label {
    flex-shrink: 0;
    font-weight: 500;
    color: var(--el-text-color-regular);

    &::after {
      content: '：';
    }
  }

  .value {
    min-width: 0;
    flex: 1;
    overflow: hidden;
    color: var(--el-text-color-primary);
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.empty-state {
  color: var(--el-text-color-secondary);

  :deep(.el-empty__description) {
    font-size: 13px;
  }
}
</style>
