<!--
  库房单据工作站：左侧单据卡片队列（主色条、选中态、票尾虚线）。
  默认：到分钟时间格式、单据号多字段解析、审批态、选中态、库房/审批人字典与选项解析；业务仅需传 orders + selectedOrder，可选传库房/职员选项与 locationContextId（草稿库房回退）。
-->
<script setup lang="ts">
import type { ApiOptionItem } from '#/types/api-response';

import { formatDate } from '@vben/utils';

import { Document } from '@element-plus/icons-vue';
import { ElCard } from 'element-plus';

import { formatInventoryTotalAmountDisplay } from '#/utils/inventory/inventory-qty-unit-display';
import {
  isSupplyStatusDraft,
  isSupplyStatusRejected,
} from '#/utils/inventory/inbound-receipt-status';
import { isPurchaseRequestApproved } from '#/utils/warehouse/purchase-request-order-detail';
import {
  normalizeStocktakeStatusSort,
  stocktakeStatusLabel,
} from '#/utils/inventory/stocktake-doc-actions';

import InvPrimaryBtn from './inv-primary-btn.vue';

defineOptions({ name: 'InvQueueCards' });

const props = withDefaults(
  defineProps<{
    applicantRoleLabel?: string;
    approverRoleLabel?: string;
    /** 库房下拉（用于草稿行 purpose 回退及 id→名称） */
    cabinetListOptions?: Record<string, unknown>[];
    /** 空态主按钮文案（默认「新增单据」） */
    emptyAddLabel?: string;
    emptyDescription?: string;
    /** 空态是否展示「新增」按钮；只读壳单台账应关闭，避免歧义 */
    showEmptyAdd?: boolean;
    /** 覆盖默认制单时间格式化 */
    formatApplyTime?: (value: unknown) => string;
    /** 覆盖默认审批时间格式化 */
    formatApprovalTime?: (value: unknown) => string;
    /** 覆盖默认「审批通过」判定 */
    isApproved?: (row: Record<string, unknown>) => boolean;
    /** 与单头/筛选同步的库房 id，草稿卡片库房一行回退用 */
    locationContextId?: number | string;
    orders: Record<string, unknown>[];
    practitionerOption?: ApiOptionItem[];
    /** 完全自定义审批人一行（极少用） */
    resolveApproverLine?: (row: Record<string, unknown>) => string | undefined;
    /** 完全自定义单据号（极少用） */
    resolveBusNo?: (row: Record<string, unknown>) => string | undefined;
    /** 完全自定义库房一行（极少用） */
    resolveWarehouseLine?: (row: Record<string, unknown>) => string | undefined;
    /** 当前选中行（与列表行同构）；用于高亮与草稿 clientId 比对 */
    selectedOrder?: null | Record<string, unknown>;
    /** 是否展示供应商（采购进货等少数场景为 true；默认 false 突出库房） */
    showSupplier?: boolean;
    /** 票尾金额列标题（默认「总金额」） */
    footerAmountLabel?: string;
    /** 覆盖默认金额解析（如调价单进价/售价盈亏） */
    resolveAmountLine?: (row: Record<string, unknown>) => string | undefined;
  }>(),
  {
    applicantRoleLabel: '制单人',
    approverRoleLabel: '审批人',
    cabinetListOptions: () => [],
    emptyAddLabel: '新增单据',
    emptyDescription: '暂无单据',
    footerAmountLabel: '总金额',
    practitionerOption: () => [],
    showEmptyAdd: true,
    showSupplier: false,
  },
);

const emit = defineEmits<{
  add: [];
  select: [row: Record<string, unknown>];
}>();

const QUEUE_CARD_DATETIME_FORMAT = 'YYYY-MM-DD HH:mm' as const;

/** 点击卡片：通知父级切换当前单据并联查明细 */
function handleRowClick(row: Record<string, unknown>) {
  emit('select', row);
}

/** 空态引导新增单据 */
function handleEmptyAdd() {
  emit('add');
}

function defaultFormatQueueTime(value: unknown): string {
  if (value === undefined || value === null || value === '') return '';
  return formatDate(value as Date | number | string, QUEUE_CARD_DATETIME_FORMAT);
}

function defaultResolveBusNo(row?: null | Record<string, unknown>): string | undefined {
  if (!row) return undefined;
  const candidates = [row.supplyBusNo, row.busNo, row.orderBusNo, row.purchaseBusNo];
  for (const raw of candidates) {
    if (raw === undefined || raw === null) continue;
    const s = String(raw).trim();
    if (s !== '') return s;
  }
  return undefined;
}

function effectiveResolveBusNo(row?: null | Record<string, unknown>): string | undefined {
  if (props.resolveBusNo) return props.resolveBusNo(row as Record<string, unknown>);
  return defaultResolveBusNo(row);
}

/** 解析单行展示文案 */
function displayText(value: unknown): string {
  if (value === undefined || value === null) return '';
  return String(value).trim();
}

/** 单据号：草稿无号时展示占位 */
function displayBusNo(item: Record<string, unknown>): string {
  const no = effectiveResolveBusNo(item);
  if (no !== undefined && no !== null && String(no).trim() !== '') return String(no).trim();
  if (item._draft) return '（新单据）';
  return '—';
}

function queueStatusClass(item: Record<string, unknown>): string {
  if (item._draft || isSupplyStatusDraft(item.statusEnum)) {
    return 'queue-status--editing';
  }
  const approveFn = props.isApproved ?? isPurchaseRequestApproved;
  return approveFn(item) ? 'queue-status--ok' : 'queue-status--pending';
}

function isRowSelected(row: Record<string, unknown>): boolean {
  const sel = props.selectedOrder;
  if (!sel) return false;
  if (Boolean(sel._draft) && Boolean(row._draft)) {
    return sel._clientDraftId === row._clientDraftId;
  }
  if (Boolean(sel._draft) || Boolean(row._draft)) return false;
  const selNo = effectiveResolveBusNo(sel);
  const rowNo = effectiveResolveBusNo(row);
  if (!selNo || !rowNo) return false;
  return selNo === rowNo;
}

function rowListKey(item: Record<string, unknown>, orderIdx: number): string {
  const draftId = item._clientDraftId;
  if (draftId !== undefined && draftId !== null && String(draftId) !== '') {
    return String(draftId);
  }
  return effectiveResolveBusNo(item) ?? `order-${orderIdx}`;
}

function formatApplyTimeForDisplay(value: unknown): string {
  return (props.formatApplyTime ?? defaultFormatQueueTime)(value);
}

/** 从行上解析总金额展示（兼容 totalPrice / totalAmount 等；两位小数 + `元`） */
function displayTotalAmount(row: Record<string, unknown>): string {
  const fromFn = props.resolveAmountLine?.(row);
  if (fromFn !== undefined && fromFn !== null && String(fromFn).trim() !== '') {
    return String(fromFn).trim();
  }
  const keys = [
    'totalPrice',
    'totalAmount',
    'totalMoney',
    'sumTotalPrice',
    'amount',
    'actualAmountSum',
  ];
  for (const k of keys) {
    const raw = row[k];
    if (raw === undefined || raw === null || raw === '') continue;
    const text = formatInventoryTotalAmountDisplay({ [k]: raw }, k);
    if (text) return text;
  }
  return '';
}

function cabinetNameById(id: unknown): string {
  if (id === undefined || id === null || id === '') return '';
  const idStr = String(id);
  for (const o of props.cabinetListOptions) {
    const v = o.id ?? o.value;
    if (String(v) === idStr) return String(o.name ?? o.label ?? '').trim();
  }
  return '';
}

/** 库房一行：优先完全自定义回调，其次草稿回退 + 字典 + id→名称 */
function displayWarehouseLine(item: Record<string, unknown>): string {
  const fromFn = props.resolveWarehouseLine?.(item);
  if (fromFn !== undefined && fromFn !== null && String(fromFn).trim() !== '') {
    return String(fromFn).trim();
  }
  if (item._draft) {
    const lid = item.purposeLocationId ?? item.locationId ?? props.locationContextId;
    const lab = cabinetNameById(lid);
    return lab || '';
  }
  const src =
    displayText(item.sourceLocationId_dictText) ||
    displayText(item.fromLocationId_dictText) ||
    displayText(item.shipLocationId_dictText) ||
    cabinetNameById(item.sourceLocationId ?? item.fromLocationId);
  const dst =
    displayText(item.purposeLocationId_dictText) ||
    displayText(item.locationId_dictText) ||
    displayText(item.targetLocationId_dictText) ||
    displayText(item.purposeLocationName) ||
    cabinetNameById(item.purposeLocationId ?? item.locationId);
  // 领用/退领：底部展示「科室药房 → 领用科室」，避免只看见药房名
  const consumingOrg = displayText(item.consumingOrganizationName);
  if (dst && consumingOrg) {
    return `${dst} → ${consumingOrg}`;
  }
  if (consumingOrg) {
    return consumingOrg;
  }
  if (src && dst && src !== dst) return `${src} → ${dst}`;
  if (dst) return dst;
  if (src) return src;
  return '';
}

function displayApprovalTime(item: Record<string, unknown>): string {
  const raw = item.approvalTime ?? item.auditTime ?? item.approveTime ?? item.reviewTime;
  return (props.formatApprovalTime ?? defaultFormatQueueTime)(raw) || '';
}

function practitionerLabelById(id: unknown): string {
  if (id === undefined || id === null || id === '') return '';
  const idStr = String(id);
  for (const o of props.practitionerOption) {
    if (String(o.value) === idStr) return String(o.label ?? '').trim();
  }
  return '';
}

/** 制单人展示名：优先后端 enrich 姓名，其次字典与职员选项 */
function displayApplicantLine(item: Record<string, unknown>): string {
  return (
    displayText(item.applicantName) ||
    displayText(item.applicantId_dictText) ||
    displayText(item.creatorName) ||
    practitionerLabelById(item.applicantId ?? item.practitionerId)
  );
}

/** 审批人展示名：优先完全自定义，其次后端 enrich 姓名、字典 + 职员选项 */
function displayApproverLine(item: Record<string, unknown>): string {
  const fromFn = props.resolveApproverLine?.(item);
  if (fromFn !== undefined && fromFn !== null && String(fromFn).trim() !== '') {
    return String(fromFn).trim();
  }
  return (
    displayText(item.approverName) ||
    displayText(item.approverId_dictText) ||
    displayText(item.auditBy_dictText) ||
    displayText(item.reviewerId_dictText) ||
    practitionerLabelById(item.approverId ?? item.auditBy)
  );
}

/** 状态徽章：优先 code→中文，避免透出 WITHDRAW 等英文 code */
function displayStatusText(item: Record<string, unknown>): string {
  const fromCode = stocktakeStatusLabel(
    normalizeStocktakeStatusSort(
      item.statusEnum ?? item.statusEnumText ?? item.statusEnum_enumText,
    ),
  );
  if (fromCode && fromCode !== '—') return fromCode;
  return displayText(item.statusEnumText) || displayText(item.statusEnum_enumText) || '—';
}

/** 驳回原因：列表 DTO rejectReason / reason */
function displayRejectReason(item: Record<string, unknown>): string {
  if (!isSupplyStatusRejected(item.statusEnum ?? item.statusEnumText)) return '';
  return (
    displayText(item.rejectReason) ||
    displayText(item.reason) ||
    displayText(item.changeReason) ||
    ''
  );
}
</script>

<template>
  <div class="inv-queue-cards flex flex-col gap-1">
    <template v-if="orders.length > 0">
      <div
        v-for="(item, orderIdx) in orders"
        :key="rowListKey(item, orderIdx)"
        class="queue-item"
        :class="{ 'is-active': isRowSelected(item) }"
        @click="handleRowClick(item)"
      >
        <ElCard class="queue-card" shadow="never">
          <div class="queue-head-wrap">
            <div class="queue-head">
              <div class="queue-bus-no">
                <el-icon class="queue-bus-no__icon"><Document /></el-icon>
                <span class="queue-bus-no__text">{{ displayBusNo(item) }}</span>
              </div>
              <div class="queue-head__status">
                <span class="queue-status" :class="queueStatusClass(item)">
                  {{ displayStatusText(item) }}
                </span>
              </div>
            </div>
          </div>

          <div class="queue-body">
            <div
              v-if="showSupplier"
              class="queue-line queue-line--plain queue-line--supplier min-w-0"
            >
              <span class="queue-plain-value truncate">{{
                displayText(item.supplierId_dictText) || displayText(item.supplierName) || '—'
              }}</span>
            </div>

            <div
              v-if="displayRejectReason(item)"
              class="queue-line queue-line--plain queue-line--reject-reason min-w-0"
            >
              <span class="queue-label">驳回原因</span>
              <span
                class="queue-plain-value queue-reject-reason truncate"
                :title="displayRejectReason(item)"
                >{{ displayRejectReason(item) }}</span
              >
            </div>

            <div class="queue-line queue-line--people-times">
              <div class="queue-people-col min-w-0">
                <div class="queue-person-row min-w-0">
                  <span class="queue-label">{{ applicantRoleLabel }}</span>
                  <span class="queue-person-value truncate">{{
                    displayApplicantLine(item) || '—'
                  }}</span>
                </div>
                <div class="queue-person-row min-w-0">
                  <span class="queue-label">{{ approverRoleLabel }}</span>
                  <span class="queue-person-value queue-person-value--slot truncate">{{
                    displayApproverLine(item) || '—'
                  }}</span>
                </div>
              </div>
              <div class="queue-times-col shrink-0">
                <div class="queue-time-row truncate">
                  <span class="queue-time-value queue-time-value--slot">{{
                    formatApplyTimeForDisplay(item.applyTime) || '—'
                  }}</span>
                </div>
                <div class="queue-time-row truncate">
                  <span class="queue-time-value queue-time-value--slot">{{
                    displayApprovalTime(item) || '—'
                  }}</span>
                </div>
              </div>
            </div>

            <div class="queue-line queue-line--footer">
              <div class="queue-footer-left--warehouse min-w-0">
                <span class="queue-warehouse-value truncate">{{
                  displayWarehouseLine(item) || '—'
                }}</span>
              </div>
              <div class="queue-footer-right shrink-0">
                <span class="queue-label">{{ footerAmountLabel }}</span>
                <span class="queue-amount-num">{{ displayTotalAmount(item) || '—' }}</span>
              </div>
            </div>
          </div>
        </ElCard>
      </div>
    </template>
    <div v-else class="queue-empty flex flex-col items-center justify-center py-8">
      <el-empty :description="emptyDescription" :image-size="72">
        <InvPrimaryBtn v-if="showEmptyAdd" :label="emptyAddLabel" @click="handleEmptyAdd" />
      </el-empty>
    </div>
  </div>
</template>

<style scoped lang="scss">
.queue-item {
  width: 100%;
  cursor: pointer;
  border-radius: var(--el-border-radius-base);

  &.is-active .queue-bus-no__text {
    color: var(--el-color-primary);
    font-weight: 600;
  }
}

.queue-card {
  position: relative;
  overflow: hidden;
  border-radius: var(--el-border-radius-base);
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-extra-light);
  box-shadow:
    inset 3px 0 0 0 var(--el-color-primary),
    1px 2px 3px rgb(0 0 0 / 5%);
  transition:
    background-color 0.2s ease,
    border-color 0.2s ease,
    box-shadow 0.2s ease;

  :deep(.el-card__body) {
    position: relative;
    z-index: 0;
    padding: 6px 8px;
  }
}

.queue-item:hover .queue-card {
  border-color: var(--el-border-color-lighter);
  box-shadow:
    inset 3px 0 0 0 var(--el-color-primary),
    1px 2px 6px rgb(0 0 0 / 7%);
}

.queue-item.is-active .queue-card {
  background-color: var(--el-fill-color-blank);
  border-color: var(--el-color-primary-light-3);
  box-shadow:
    inset 3px 0 0 0 var(--el-color-primary),
    1px 2px 4px rgb(0 0 0 / 5%);
}

.queue-item.is-active .queue-card::after {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 1;
  border-radius: inherit;
  pointer-events: none;
  background-color: var(--el-color-primary);
  opacity: 0.04;
}

.queue-head-wrap {
  margin-bottom: 6px;
}

.queue-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-width: 0;
}

.queue-bus-no {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  gap: 4px;
}

.queue-bus-no__icon {
  flex-shrink: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.queue-bus-no__text {
  min-width: 0;
  overflow: hidden;
  font-size: 13px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-primary);
  letter-spacing: 0.02em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.queue-head__status {
  flex-shrink: 0;
}

.queue-status {
  display: inline-block;
  padding: 0 5px;
  line-height: 18px;
  font-size: 11px;
  font-weight: 500;
  border-radius: 2px;
}

.queue-status--ok {
  color: var(--el-color-success);
  background-color: var(--el-color-success-light-9);
  border: 1px solid var(--el-color-success-light-7);
}

.queue-status--pending {
  color: var(--el-color-danger);
  background-color: var(--el-color-danger-light-9);
  border: 1px solid var(--el-color-danger-light-7);
}

.queue-status--editing {
  color: var(--el-color-primary);
  background-color: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-7);
}

.queue-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.queue-line {
  line-height: 1.35;
  min-width: 0;
}

.queue-line--reject-reason {
  display: flex;
  align-items: flex-start;
  gap: 6px;
}

.queue-reject-reason {
  min-width: 0;
  flex: 1;
  color: var(--el-color-danger);
  font-size: 12px;
}

.queue-line--plain {
  font-size: 12px;
  color: var(--el-text-color-primary);
}

.queue-line--supplier {
  margin-bottom: 4px;
}

.queue-plain-value {
  display: block;
}

.queue-line--people-times {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
}

.queue-people-col {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 2px;
}

.queue-person-row {
  display: inline-flex;
  min-width: 0;
  align-items: center;
}

.queue-person-value {
  min-width: 0;
  font-size: 12px;
  color: var(--el-text-color-primary);
}

.queue-person-value--slot {
  display: inline-block;
  min-width: 7ch;
}

.queue-footer-left--warehouse {
  display: block;
  min-width: 0;
  flex: 1;
}

.queue-footer-left--warehouse .queue-warehouse-value {
  display: block;
  width: 100%;
  min-width: 0;
  font-size: 12px;
  color: var(--el-text-color-primary);
}

.queue-times-col {
  display: flex;
  max-width: 58%;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  line-height: 1.25;
  text-align: right;
}

.queue-time-row {
  display: flex;
  max-width: 100%;
  align-items: baseline;
  justify-content: flex-end;
  font-size: 12px;
}

.queue-time-value {
  min-width: 0;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-regular);
}

/** 无审批/无制单时间时也占位，避免与有时间的卡片右栏宽度不齐 */
.queue-time-value--slot {
  display: inline-block;
  min-width: 15ch;
  text-align: right;
}

.queue-line--footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 0;
  padding-top: 3px;
  border-top: 1px dashed var(--el-border-color-lighter);
  font-size: 12px;
}

.queue-footer-right {
  display: inline-flex;
  align-items: baseline;
  justify-content: flex-end;
}

.queue-label {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 500;
  color: var(--el-text-color-regular);

  &::after {
    content: '：';
  }
}

.queue-amount-num {
  font-size: 12px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-primary);
}

.queue-empty {
  color: var(--el-text-color-secondary);

  :deep(.el-empty__description) {
    font-size: 13px;
  }
}
</style>
