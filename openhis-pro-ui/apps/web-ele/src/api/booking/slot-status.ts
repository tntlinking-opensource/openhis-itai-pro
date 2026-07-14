import type { SlotDetailDto, SlotListItemDto } from '#/api/booking/slot-manage';

/** 号源状态筛选项 code（与 slotManage statusCode / 本页前端收窄一致） */
export const SLOT_STATUS_FILTER_AVAILABLE = 'AVAILABLE';
export const SLOT_STATUS_FILTER_FULL = 'FULL';
export const SLOT_STATUS_FILTER_BUSY_UNAVAILABLE = 'BUSY_UNAVAILABLE';

export type SlotStatusFilterCode =
  | ''
  | typeof SLOT_STATUS_FILTER_AVAILABLE
  | typeof SLOT_STATUS_FILTER_BUSY_UNAVAILABLE
  | typeof SLOT_STATUS_FILTER_FULL;

export const SLOT_STATUS_FILTER_OPTIONS: Array<{ label: string; value: SlotStatusFilterCode }> = [
  { label: '可约', value: SLOT_STATUS_FILTER_AVAILABLE },
  { label: '已满', value: SLOT_STATUS_FILTER_FULL },
  { label: '停诊', value: SLOT_STATUS_FILTER_BUSY_UNAVAILABLE },
];

function normalizeSlotStatusCode(row: Pick<SlotListItemDto, 'statusCode'>): string {
  return String(row.statusCode ?? '')
    .trim()
    .toUpperCase();
}

export function isSlotBlocked(row: Pick<SlotListItemDto, 'statusCode'>): boolean {
  const code = normalizeSlotStatusCode(row);
  return code === 'BUSY_UNAVAILABLE' || code === 'CLOSED' || code === 'BLOCKED';
}

export function isSlotFull(row: Pick<SlotListItemDto, 'remainingCount' | 'statusCode'>): boolean {
  if (isSlotBlocked(row)) {
    return false;
  }
  const remaining = Number(row.remainingCount ?? 0);
  return Number.isFinite(remaining) && remaining <= 0;
}

export function isSlotAvailable(
  row: Pick<SlotListItemDto, 'remainingCount' | 'statusCode'>,
): boolean {
  return !isSlotBlocked(row) && !isSlotFull(row);
}

export function matchesSlotStatusFilter(
  row: Pick<SlotListItemDto, 'remainingCount' | 'statusCode'>,
  filter: SlotStatusFilterCode,
): boolean {
  if (!filter) {
    return true;
  }
  if (filter === SLOT_STATUS_FILTER_BUSY_UNAVAILABLE) {
    return isSlotBlocked(row);
  }
  if (filter === SLOT_STATUS_FILTER_FULL) {
    return isSlotFull(row);
  }
  if (filter === SLOT_STATUS_FILTER_AVAILABLE) {
    return isSlotAvailable(row);
  }
  return true;
}

export function resolveSlotStatusFilterServerCode(
  filter: SlotStatusFilterCode,
): string | undefined {
  if (filter === SLOT_STATUS_FILTER_BUSY_UNAVAILABLE) {
    return SLOT_STATUS_FILTER_BUSY_UNAVAILABLE;
  }
  return undefined;
}

export function resolveSlotStatusTagType(
  row: Pick<SlotDetailDto, 'remainingCount' | 'statusCode'>,
): 'danger' | 'info' | 'success' {
  if (isSlotBlocked(row)) {
    return 'danger';
  }
  if (isSlotFull(row)) {
    return 'info';
  }
  return 'success';
}

/** 号源展示文案（UI 用；逻辑判断请用 statusCode + 上方工具函数） */
export function resolveSlotStatusDisplayLabel(
  row: Pick<SlotListItemDto, 'displayStatus' | 'remainingCount' | 'statusCode'>,
): string {
  if (isSlotBlocked(row)) {
    return '停诊';
  }
  if (isSlotFull(row)) {
    return '已满';
  }
  return String(row.displayStatus ?? '').trim() || '可约';
}
