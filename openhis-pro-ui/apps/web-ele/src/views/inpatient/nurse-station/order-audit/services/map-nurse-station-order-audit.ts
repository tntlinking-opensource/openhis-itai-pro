import type {
  NurseStationOrderAuditOrderType,
  NurseStationOrderAuditRowDto,
  NurseStationOrderAuditWriteItemDto,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-order-audit.types';

import { formatDateTime } from '@vben/utils';

import { preserveApiIdForRequest } from '#/utils/preserve-api-id';

/** 表格行：与 AdviceOrderDisplayCell 及校对页列展示对齐 */
export interface NurseStationOrderAuditTableRow {
  id: string;
  requestId: string;
  orderType: NurseStationOrderAuditOrderType;
  encounterId: string;
  stopTime: string;
  doctorName: string;
  typeLabel: string;
  /** 药/诊短文案 */
  orderKindLabel: string;
  /** 长临 code（LONG_TERM / TEMPORARY） */
  therapyTypeCode?: string;
  /** 长临展示文案 */
  therapyLabel?: string;
  content: string;
  dosage: string;
  extension?: string;
  executor: string;
  executeTime: string;
  halted?: boolean;
  statusEnum?: string;
}

function orderTypeToShortLabel(orderType: string): string {
  const t = orderType.trim().toUpperCase();
  if (t === 'MED_REQUEST') return '药品';
  if (t === 'SERVICE_REQUEST') return '诊疗';
  return orderType || '医嘱';
}

/** 长临 code → 短文案 */
function therapyEnumToLabel(code: string): string {
  const c = code.trim().toUpperCase();
  if (c === 'LONG_TERM') return '长期';
  if (c === 'TEMPORARY') return '临时';
  if (c === 'DISCHARGE_MEDICATION') return '出院带药';
  return '';
}

/** 长临文案反推 code（therapyEnum 缺失时供颜色 class 使用） */
function resolveTherapyTypeCodeFromLabel(label: string): string {
  const text = label.trim();
  if (text.includes('长期')) return 'LONG_TERM';
  if (text.includes('临时')) return 'TEMPORARY';
  if (text.includes('出院')) return 'DISCHARGE_MEDICATION';
  return '';
}

/** 列表长临 code */
function resolveAuditTherapyTypeCode(dto: NurseStationOrderAuditRowDto): string {
  const code = String(dto.therapyEnum ?? '')
    .trim()
    .toUpperCase();
  if (code) return code;
  return resolveTherapyTypeCodeFromLabel(resolveAuditTherapyLabel(dto));
}

/** 列表长临文案：优先后端 therapyDisplay，否则按 therapyEnum 推导 */
function resolveAuditTherapyLabel(dto: NurseStationOrderAuditRowDto): string {
  const display = String(dto.therapyDisplay ?? '').trim();
  if (display) {
    if (display.includes('长期')) return '长期';
    if (display.includes('临时')) return '临时';
    if (display.includes('出院')) return '出院带药';
    return display;
  }
  return therapyEnumToLabel(String(dto.therapyEnum ?? ''));
}

/** 组装列表「类型」列：长临 + 药/诊 */
function buildAuditTypeLabel(dto: NurseStationOrderAuditRowDto): string {
  const therapy = resolveAuditTherapyLabel(dto);
  const kind = orderTypeToShortLabel(String(dto.orderType ?? ''));
  if (therapy && kind) return `${therapy}/${kind}`;
  return therapy || kind || '—';
}

/** 医嘱名称摘要：名称 + 规格 */
function buildAuditContentTitle(dto: NurseStationOrderAuditRowDto): string {
  const name = String(dto.adviceDisplayName ?? '').trim();
  const spec = String(dto.specificationText ?? '').trim();
  if (name && spec) return `${name}【${spec}】`;
  return name || spec || '—';
}

/** 用法、频次拼成扩展行 */
function buildAuditExtensionLine(dto: NurseStationOrderAuditRowDto): string {
  const usage = String(dto.usageDisplay ?? '').trim();
  const freq = String(dto.frequencyDisplay ?? '').trim();
  if (usage && freq) return `${usage}，${freq}`;
  return usage || freq || '';
}

/** 列表行 DTO → 表格行 */
export function mapAuditRowDtoToTableRow(
  dto: NurseStationOrderAuditRowDto,
): NurseStationOrderAuditTableRow {
  const requestId = preserveApiIdForRequest(dto.requestId) ?? '';
  const orderTypeRaw = String(dto.orderType ?? '')
    .trim()
    .toUpperCase();
  const orderType =
    orderTypeRaw === 'MED_REQUEST' || orderTypeRaw === 'SERVICE_REQUEST'
      ? orderTypeRaw
      : 'MED_REQUEST';
  const listKey = String(dto.listRowKey ?? '').trim();
  const id =
    listKey ||
    (requestId && orderType
      ? `${requestId}\u0001${orderType}`
      : `audit-${requestId || Date.now()}`);
  const authoredRaw = String(dto.authoredTime ?? '').trim();
  const stopRaw = String(dto.stopTime ?? '').trim();
  const statusEnum = String(dto.statusEnum ?? '')
    .trim()
    .toUpperCase();

  return {
    id,
    requestId,
    orderType,
    encounterId: preserveApiIdForRequest(dto.encounterId) ?? '',
    stopTime: stopRaw ? formatDateTime(stopRaw) : '',
    doctorName: String(dto.requesterName ?? '').trim() || '—',
    typeLabel: buildAuditTypeLabel(dto),
    orderKindLabel: orderTypeToShortLabel(String(dto.orderType ?? '')) || '—',
    therapyTypeCode: resolveAuditTherapyTypeCode(dto) || undefined,
    therapyLabel: resolveAuditTherapyLabel(dto) || undefined,
    content: buildAuditContentTitle(dto),
    dosage: String(dto.dosageDisplay ?? '').trim() || '—',
    extension: buildAuditExtensionLine(dto) || undefined,
    executor: String(dto.performerLocationDisplay ?? '').trim() || '—',
    executeTime: authoredRaw ? formatDateTime(authoredRaw) : '—',
    halted: statusEnum === 'STOPPED',
    statusEnum,
  };
}

/** 选中表格行 → 写请求 items */
export function mapTableRowsToWriteItems(
  rows: NurseStationOrderAuditTableRow[],
): NurseStationOrderAuditWriteItemDto[] {
  const items: NurseStationOrderAuditWriteItemDto[] = [];
  for (const row of rows) {
    const requestId = preserveApiIdForRequest(row.requestId);
    if (!requestId) continue;
    items.push({
      orderType: row.orderType,
      requestId,
    });
  }
  return items;
}

/** 接口分组列表 → encounterId → 表格行 */
export function indexAuditGroupsByEncounterId(
  groups: { encounterId?: string; orders?: NurseStationOrderAuditRowDto[] }[] | undefined,
): Map<string, NurseStationOrderAuditTableRow[]> {
  const map = new Map<string, NurseStationOrderAuditTableRow[]>();
  for (const group of groups ?? []) {
    const encounterId = preserveApiIdForRequest(group.encounterId);
    if (!encounterId) continue;
    const rows = (group.orders ?? []).map((dto) => mapAuditRowDtoToTableRow(dto));
    map.set(encounterId, rows);
  }
  return map;
}
