import type { TableColumn } from '@vben-core/table-layout-ui';
import type { ApiOptionItem } from '#/types/api-response';

import { formatInventoryTotalAmountDisplay } from '#/utils/inventory/inventory-qty-unit-display';
import { resolveLocationDisplayNameFromOptions } from '#/utils/inventory/inventory-workbench-list';

/** 关联上游单弹窗：制单人 / 金额 / 库房 三列（插入在「操作」列之前） */
export function withInvDocLinkPickerEnrichedColumns(
  columns: TableColumn[],
  options?: { warehouseLabel?: string },
): TableColumn[] {
  const warehouseLabel = options?.warehouseLabel ?? '库房';
  const enriched: TableColumn[] = [
    {
      prop: 'creatorName',
      label: '制单人',
      minWidth: 96,
      showOverflowTooltip: true,
      slot: 'linkPickerCreator',
    },
    {
      prop: 'totalAmount',
      label: '金额',
      width: 112,
      align: 'right',
      headerAlign: 'center',
      slot: 'linkPickerAmount',
    },
    {
      prop: 'warehouseName',
      label: warehouseLabel,
      minWidth: 120,
      showOverflowTooltip: true,
      slot: 'linkPickerWarehouse',
    },
  ];
  const actionIdx = columns.findIndex((col) => col.slot === 'rowActions' || col.label === '操作');
  if (actionIdx < 0) {
    return [...columns, ...enriched];
  }
  return [...columns.slice(0, actionIdx), ...enriched, ...columns.slice(actionIdx)];
}

/** 制单人展示：优先读模型富化字段，再回落 practitioner/applicant 选项解析 */
export function resolveInvDocLinkPickerCreator(
  row: Record<string, unknown>,
  practitionerOptions?: ApiOptionItem[],
): string {
  const directKeys = [
    'creatorName',
    'applicantName',
    'applicantId_dictText',
    'practitionerId_dictText',
    'warehouseKeeperName',
  ];
  for (const key of directKeys) {
    const text = String(row[key] ?? '').trim();
    if (text) return text;
  }
  const idRaw = row.applicantId ?? row.practitionerId ?? row.creatorId;
  if (idRaw === undefined || idRaw === null || !practitionerOptions?.length) {
    return '';
  }
  const idStr = String(idRaw);
  const hit = practitionerOptions.find((item) => String(item.value ?? '') === idStr);
  return String(hit?.label ?? '').trim();
}

/** 单据金额：兼容 totalAmount / totalPrice 等聚合字段 */
export function resolveInvDocLinkPickerAmount(row: Record<string, unknown>): string {
  const keys = ['totalAmount', 'totalPrice', 'totalMoney', 'sumTotalPrice', 'amount'];
  for (const key of keys) {
    const display = formatInventoryTotalAmountDisplay(row, key);
    if (display) return display;
  }
  return '';
}

/** 库位展示：优先名称字段，再按 purpose/source 库位 id 解析 */
export function resolveInvDocLinkPickerWarehouse(
  row: Record<string, unknown>,
  locationOptions?: Record<string, unknown>[],
): string {
  const nameKeys = [
    'purposeLocationName',
    'sourceLocationName',
    'warehouseName',
    'locationName',
    'purposeLocationId_dictText',
    'sourceLocationId_dictText',
  ];
  for (const key of nameKeys) {
    const text = String(row[key] ?? '').trim();
    if (text) return text;
  }
  const opts = locationOptions ?? [];
  for (const idKey of ['purposeLocationId', 'sourceLocationId'] as const) {
    const id = row[idKey];
    if (id === undefined || id === null || String(id).trim() === '') continue;
    const name = resolveLocationDisplayNameFromOptions(id, opts);
    if (name) return name;
  }
  return '';
}
