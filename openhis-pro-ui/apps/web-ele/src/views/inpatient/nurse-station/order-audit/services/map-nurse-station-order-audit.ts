import type {
  NurseStationOrderAuditOrderType,
  NurseStationOrderAuditRowDto,
  NurseStationOrderAuditWriteItemDto,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-order-audit.types';

import { formatDateTime } from '@vben/utils';

import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

/** 表格行：与 AdviceOrderDisplayCell 及校对页列展示对齐 */
export interface NurseStationOrderAuditTableRow {
  id: string;
  requestId: string;
  orderType: NurseStationOrderAuditOrderType;
  encounterId: string;
  stopTime: string;
  doctorName: string;
  typeLabel: string;
  /** 类型列文案（后端 orderKindDisplay，与 Tab 无关） */
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
  tcmFlag?: boolean;
  prescriptionNo?: string;
  lineRequestIds?: string[];
}

/** 解析写操作锚点 orderType（禁止空值默认成药品） */
function resolveAuditOrderType(orderTypeRaw: string): NurseStationOrderAuditOrderType | null {
  const t = orderTypeRaw.trim().toUpperCase();
  if (t === 'MED_REQUEST') return 'MED_REQUEST';
  if (t === 'SERVICE_REQUEST') return 'SERVICE_REQUEST';
  return null;
}

/** 类型列：优先读后端 orderKindDisplay，回落本地推导 */
function resolveAuditOrderKindLabel(dto: NurseStationOrderAuditRowDto): string {
  const fromApi = String(dto.orderKindDisplay ?? '').trim();
  if (fromApi) return fromApi;
  const category = String(dto.categoryEnum ?? '').trim();
  if (category === '34') return '出院';
  if (category === '32') return '转科';
  if (category === '26') return '护理';
  const orderType = resolveAuditOrderType(String(dto.orderType ?? ''));
  if (orderType === 'MED_REQUEST') return '药品';
  if (orderType === 'SERVICE_REQUEST') return '诊疗';
  return '—';
}

/** 长临 code → 短文案 */
function therapyEnumToLabel(code: string): string {
  const c = code.trim().toUpperCase();
  if (c === 'LONG_TERM') return '长期';
  if (c === 'TEMPORARY') return '临时';
  if (c === 'DISCHARGE_MEDICATION') return '出院带药';
  return '';
}

/** 列表长临 code */
function resolveAuditTherapyTypeCode(dto: NurseStationOrderAuditRowDto): string {
  return String(dto.therapyEnum ?? '')
    .trim()
    .toUpperCase();
}

/** 列表长临文案：优先 therapyDisplay，回落 therapyEnum */
function resolveAuditTherapyLabel(dto: NurseStationOrderAuditRowDto): string {
  const fromApi = String(dto.therapyDisplay ?? '').trim();
  if (fromApi) return fromApi;
  return therapyEnumToLabel(String(dto.therapyEnum ?? ''));
}

/** 组装列表「类型」列：长临 + 类型 */
function buildAuditTypeLabel(dto: NurseStationOrderAuditRowDto): string {
  const therapy = resolveAuditTherapyLabel(dto);
  const kind = resolveAuditOrderKindLabel(dto);
  if (therapy && kind && kind !== '—') return `${therapy}/${kind}`;
  return therapy || kind || '—';
}

/** 医嘱名称摘要：名称 + 规格；中药方折叠行仅用方级展示名 */
function buildAuditContentTitle(dto: NurseStationOrderAuditRowDto): string {
  if (dto.tcmFlag === true) {
    const collapsedName = String(dto.adviceDisplayName ?? '').trim();
    if (collapsedName) return collapsedName;
  }
  const name = String(dto.adviceDisplayName ?? '').trim();
  const spec = String(dto.specificationText ?? '').trim();
  if (name && spec) return `${name}【${spec}】`;
  return name || spec || '—';
}

/** 剂量/数量：中药方展示各味单次量 + 付数 */
function buildAuditDosageDisplay(dto: NurseStationOrderAuditRowDto): string {
  if (dto.tcmFlag === true) {
    const singleDose = String(dto.dosageDisplay ?? '').trim();
    const fuCount = dto.chineseHerbsDoseQuantity;
    const fuText = fuCount != null && String(fuCount).trim() ? `${String(fuCount).trim()}付` : '';
    if (singleDose && fuText) {
      return `${singleDose} ${fuText}`;
    }
    return singleDose || fuText || '—';
  }
  return String(dto.dosageDisplay ?? '').trim() || '—';
}

function mapAuditLineRequestIds(
  ids: NurseStationOrderAuditRowDto['lineRequestIds'],
): string[] | undefined {
  if (!ids?.length) return undefined;
  const mapped = ids
    .map((id) => preserveApiIdForRequest(id) ?? String(id ?? '').trim())
    .filter((id) => id.length > 0);
  return mapped.length > 0 ? mapped : undefined;
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
): NurseStationOrderAuditTableRow | null {
  const requestId = preserveApiIdForRequest(dto.requestId) ?? '';
  const orderType = resolveAuditOrderType(String(dto.orderType ?? ''));
  if (!orderType) {
    return null;
  }
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
  const orderKindLabel = resolveAuditOrderKindLabel(dto);

  return {
    id,
    requestId,
    orderType,
    encounterId: preserveApiIdForRequest(dto.encounterId) ?? '',
    stopTime: stopRaw ? formatDateTime(stopRaw) : '',
    doctorName: String(dto.requesterName ?? '').trim() || '—',
    typeLabel: buildAuditTypeLabel(dto),
    orderKindLabel,
    therapyTypeCode: resolveAuditTherapyTypeCode(dto) || undefined,
    therapyLabel: resolveAuditTherapyLabel(dto) || undefined,
    content: buildAuditContentTitle(dto),
    dosage: buildAuditDosageDisplay(dto),
    extension: buildAuditExtensionLine(dto) || undefined,
    executor: String(dto.performerLocationDisplay ?? '').trim() || '—',
    executeTime: authoredRaw ? formatDateTime(authoredRaw) : '—',
    halted: statusEnum === 'STOPPED',
    statusEnum,
    tcmFlag: dto.tcmFlag === true ? true : undefined,
    prescriptionNo: String(dto.prescriptionNo ?? '').trim() || undefined,
    lineRequestIds: mapAuditLineRequestIds(dto.lineRequestIds),
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
    const rows: NurseStationOrderAuditTableRow[] = [];
    for (const dto of group.orders ?? []) {
      const row = mapAuditRowDtoToTableRow(dto);
      if (row) {
        rows.push(row);
      }
    }
    map.set(encounterId, rows);
  }
  return map;
}
