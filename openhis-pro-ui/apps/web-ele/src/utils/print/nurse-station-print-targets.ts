import type { NurseAdviceExecuteRow } from '#/views/outpatient/nurse-station/components/types';

import { BUILT_IN_ACTIVITY_NAME_BLOOD_COLLECTION } from '#/constants/domain/built-in-activity-definition';
import {
  INHALATION_METHOD_CODES,
  INFUSION_METHOD_CODES,
  INJECTION_METHOD_CODES,
  normalizeMedicationMethodCode,
  ORAL_METHOD_CODES,
} from './constants/medication-method-codes';
import {
  isNurseExecuteRowCompleted,
  type NurseExecuteDisplayStatusCode,
} from '#/utils/nurse-station/nurse-execute-status';
import {
  SERVICE_CATEGORY_PROOF,
  SERVICE_CATEGORY_TEST,
} from './constants/service-request-category-codes';
import { SPECIMEN_BARCODE_SPLIT_URGENT_FROM_MERGE } from '#/utils/print/constants/specimen-barcode-merge.config';

import {
  resolveSpecimenBarcodeUrgencyText,
  SERVICE_REQUEST_PRIORITY_EM,
} from './constants/service-request-priority';

export interface SpecimenBarcodePrintTarget {
  serviceRequestId: string;
  /**
   * 条码内容（与 LIS 外发 applyNo 一致）：优先锚点 SR.busNo，缺省回落 serviceRequestId。
   * 合管组取组内 serviceRequestId 最小者的 busNo。
   */
  barcode: string;
  /** 检验项目名称（content / name；合管时为多项拼接） */
  itemName: string;
  /**
   * 本组全部检验申请主键（含锚点）。合管一张贴时须全部写入 bizRef，
   * 否则只记锚点、组内其余行仍显示未打，护士会以为漏打。
   */
  memberServiceRequestIds: string[];
  /** 合管检验设备主键；无合管配置时为 undefined */
  specimenTubeMergeDeviceId?: string;
  encounterId?: string;
  bedNo?: string;
  patientName?: string;
  /**
   * 开立数量对应贴数（如检验单「2次」→ 2）；合管组取组内最大；缺省按 1。
   */
  labelCount?: number;
  /**
   * 医嘱优先级 code；合管组内任一条为 EM 则整组为 EM（拆急院点：急项已单独成组，不参与非急合管）。
   */
  priorityEnum?: string;
}

/** 合管组追加成员主键（去重保序） */
function appendSpecimenBarcodeMemberId(target: SpecimenBarcodePrintTarget, requestId: string): void {
  if (!requestId || target.memberServiceRequestIds.includes(requestId)) {
    return;
  }
  target.memberServiceRequestIds.push(requestId);
}

/** 雪花主键升序比较（与后端 Long.compare / LIS 合管锚点一致） */
function compareSnowflakeIdAsc(left: string, right: string): number {
  try {
    const a = BigInt(left);
    const b = BigInt(right);
    if (a < b) {
      return -1;
    }
    if (a > b) {
      return 1;
    }
    return 0;
  } catch {
    return left.localeCompare(right);
  }
}

/** 解析采血贴条码：优先 busNo（LIS applyNo），空则回落 requestId */
export function resolveSpecimenBarcodeValue(
  busNo?: null | string,
  serviceRequestId?: null | string,
): string {
  const applyNo = String(busNo ?? '').trim();
  if (applyNo) {
    return applyNo;
  }
  return String(serviceRequestId ?? '').trim();
}

/**
 * 解析采血贴张数：开立数量展示（如「2次」）或数值；非法/空回落 1。
 */
export function resolveSpecimenBarcodeLabelCount(raw?: number | string | null): number {
  if (typeof raw === 'number' && Number.isFinite(raw)) {
    return Math.max(1, Math.floor(raw));
  }
  const text = String(raw ?? '').trim();
  if (!text) {
    return 1;
  }
  const match = text.match(/^(\d+(?:\.\d+)?)/u);
  if (!match) {
    return 1;
  }
  const n = Number(match[1]);
  if (!Number.isFinite(n) || n < 1) {
    return 1;
  }
  return Math.max(1, Math.floor(n));
}

export interface TreatmentExecutePrintTarget {
  serviceRequestIds: string[];
}

export interface MedicationLabelPrintGroup {
  groupId?: string;
  medicationRequestIds: string[];
}

/**
 * 是否合管派生「采血费」（名称精确匹配；类目虽为 22，不得打采血贴）。
 *
 * @param name 项目展示名（content / itemName / title）
 */
export function isBloodCollectionFeePrintItem(name?: null | string): boolean {
  return String(name ?? '').trim() === BUILT_IN_ACTIVITY_NAME_BLOOD_COLLECTION;
}

/**
 * 是否静脉采血诊疗（名称包含「静脉采血」）。
 * 收费类目常为治疗，申请单打印并入检验单，不单独打治疗单。
 */
export function isVenousBloodCollectionPrintItem(name?: null | string): boolean {
  return String(name ?? '').trim().includes('静脉采血');
}

/**
 * 判断执行行是否为可打采血贴的检验诊疗（serviceCategoryCode=22，排除采血费）。
 */
export function isLabSpecimenExecuteRow(row: NurseAdviceExecuteRow): boolean {
  if (row.rowType !== 'SERVICE' || !row.key.startsWith('SR:')) {
    return false;
  }
  if ((row.serviceCategoryCode ?? '').trim() !== SERVICE_CATEGORY_PROOF) {
    return false;
  }
  // 采血费只计费，不进入医技列表/采血贴
  if (isBloodCollectionFeePrintItem(row.content ?? row.name)) {
    return false;
  }
  return true;
}

function isServiceExecuteRow(row: NurseAdviceExecuteRow): boolean {
  return row.rowType === 'SERVICE' && Boolean(row.requestId) && row.key.startsWith('SR:');
}

function isMedicationExecuteRowKey(row: NurseAdviceExecuteRow): boolean {
  return (
    row.rowType === 'MEDICATION' &&
    Boolean(row.requestId) &&
    (row.key.startsWith('MR:') || row.key.startsWith('merged-med-'))
  );
}

function isExecuteRowPending(row: NurseAdviceExecuteRow): boolean {
  const code = String(row.statusEnum ?? '')
    .trim()
    .toUpperCase() as NurseExecuteDisplayStatusCode;
  if (!code) {
    return true;
  }
  if (code === 'REFUNDED' || code === 'REFUNDING') {
    return false;
  }
  return !isNurseExecuteRowCompleted(code);
}

function filterServiceExecuteRows(
  rows: NurseAdviceExecuteRow[],
  selectedKeys?: string[],
): NurseAdviceExecuteRow[] {
  const serviceRows = rows.filter(isServiceExecuteRow);
  if (selectedKeys && selectedKeys.length > 0) {
    return serviceRows.filter((row) => selectedKeys.includes(row.key));
  }
  return serviceRows.filter(isExecuteRowPending);
}

/** 合管设备主键；无合管配置时为空 */
export function resolveSpecimenMergeDeviceId(deviceId?: null | string): string {
  return String(deviceId ?? '').trim();
}

/**
 * 合管组序号：有 deviceId 但缺 groupNo 时回落 1，避免不同组误并。
 */
export function resolveSpecimenTubeMergeGroupNo(
  groupNo?: null | number | string,
  hasDeviceId?: boolean,
): number {
  const raw = groupNo;
  if (raw !== null && raw !== undefined && String(raw).trim() !== '') {
    const n = Number(raw);
    if (Number.isFinite(n) && n >= 1) {
      return Math.floor(n);
    }
  }
  if (hasDeviceId) {
    return 1;
  }
  return 1;
}

/**
 * 采血贴合管聚类键：`merge:{deviceId}:{groupNo}`；有就诊时再拼 `:enc:{encounterId}`。
 * 院点开启「拆急」时：priorityEnum=EM 视为无合管。
 * 打印中心多患者勾选时必须带 encounterId，否则同设备会跨患者误并成一张。
 */
export function resolveSpecimenBarcodeMergeClusterKey(
  deviceId?: null | string,
  groupNo?: null | number | string,
  priorityEnum?: null | string,
  encounterId?: null | string,
): string {
  const mergeId = resolveSpecimenMergeDeviceId(deviceId);
  if (!mergeId) {
    return '';
  }
  if (
    SPECIMEN_BARCODE_SPLIT_URGENT_FROM_MERGE &&
    String(priorityEnum ?? '').trim() === SERVICE_REQUEST_PRIORITY_EM
  ) {
    return '';
  }
  const gn = resolveSpecimenTubeMergeGroupNo(groupNo, true);
  const enc = String(encounterId ?? '').trim();
  if (enc) {
    return `merge:${mergeId}:${gn}:enc:${enc}`;
  }
  return `merge:${mergeId}:${gn}`;
}

/**
 * 打印合管分组键：有合管且有就诊 → 按就诊合管；有合管无就诊 → 逐条（防跨患者误并）；无合管 → 逐条。
 */
function resolveSpecimenBarcodePrintGroupKey(
  requestId: string,
  encounterId: string,
  mergeClusterKey: string,
): string {
  if (!mergeClusterKey) {
    return `sr:${requestId}`;
  }
  if (encounterId) {
    // mergeClusterKey 已含 enc 时直接用；未含则补上（兼容旧调用只传 device/group）
    if (mergeClusterKey.includes(':enc:')) {
      return mergeClusterKey;
    }
    return `${mergeClusterKey}:enc:${encounterId}`;
  }
  return `sr:${requestId}`;
}

/**
 * 采血贴用的合管设备主键（仅 deviceId，不含组号）。
 * 列表聚类/打印分组请用 {@link resolveSpecimenBarcodeMergeClusterKey}。
 */
export function resolveSpecimenBarcodeMergeDeviceId(
  deviceId?: null | string,
  priorityEnum?: null | string,
): string {
  const mergeId = resolveSpecimenMergeDeviceId(deviceId);
  if (!mergeId) {
    return '';
  }
  if (
    SPECIMEN_BARCODE_SPLIT_URGENT_FROM_MERGE &&
    String(priorityEnum ?? '').trim() === SERVICE_REQUEST_PRIORITY_EM
  ) {
    return '';
  }
  return mergeId;
}

/**
 * 同一合管设备的行排到一起。
 * 组的位置跟首行，组内保持原顺序；无合管的行原地保留。
 */
export function clusterRowsBySpecimenMerge<T>(rows: T[], mergeIdOf: (row: T) => string): T[] {
  const clustered: T[] = [];
  const consumed = new Set<number>();
  for (let index = 0; index < rows.length; index += 1) {
    if (consumed.has(index)) {
      continue;
    }
    const mergeId = mergeIdOf(rows[index] as T);
    clustered.push(rows[index] as T);
    consumed.add(index);
    if (!mergeId) {
      continue;
    }
    // 把后面同一合管设备的行收到这一组
    for (let later = index + 1; later < rows.length; later += 1) {
      if (consumed.has(later)) {
        continue;
      }
      if (mergeIdOf(rows[later] as T) !== mergeId) {
        continue;
      }
      clustered.push(rows[later] as T);
      consumed.add(later);
    }
  }
  return clustered;
}

/**
 * 操作列 rowspan：合管组只在首行占位，续行返回 0（表格隐藏该格）。
 */
export function specimenMergeOperateRowSpan<T>(
  rows: T[],
  rowIndex: number,
  mergeIdOf: (row: T) => string,
): number {
  const mergeId = mergeIdOf(rows[rowIndex] as T);
  if (!mergeId) {
    return 1;
  }
  if (rowIndex > 0 && mergeIdOf(rows[rowIndex - 1] as T) === mergeId) {
    return 0;
  }
  let span = 1;
  for (let next = rowIndex + 1; next < rows.length; next += 1) {
    if (mergeIdOf(rows[next] as T) !== mergeId) {
      break;
    }
    span += 1;
  }
  return span;
}

/** 采血条码：合管设备一组一张（锚点=组内最小 requestId，条码=其 busNo/applyNo）；张数按开立数量，合管取组内最大；急标组内任一 EM（拆急院点：EM 行单独成贴） */
export function groupSpecimenBarcodePrintTargets(
  rows: NurseAdviceExecuteRow[],
  selectedKeys?: string[],
): SpecimenBarcodePrintTarget[] {
  const keyed = filterServiceExecuteRows(rows, selectedKeys).filter(isLabSpecimenExecuteRow);
  const groupMap = new Map<string, SpecimenBarcodePrintTarget>();
  for (const row of keyed) {
    const requestId = String(row.requestId ?? '').trim();
    if (!requestId) {
      continue;
    }
    const barcode = resolveSpecimenBarcodeValue(row.busNo, requestId);
    const itemName = String(row.content ?? row.name ?? '').trim();
    const encounterId = String(
      (row as { encounterId?: null | string }).encounterId ?? '',
    ).trim();
    const mergeDeviceId = resolveSpecimenBarcodeMergeDeviceId(
      row.specimenTubeMergeDeviceId,
      row.priorityEnum,
    );
    const mergeClusterKey = resolveSpecimenBarcodeMergeClusterKey(
      row.specimenTubeMergeDeviceId,
      row.specimenTubeMergeGroupNo,
      row.priorityEnum,
      encounterId || undefined,
    );
    const labelCount = resolveSpecimenBarcodeLabelCount(row.dosageText);
    const priorityEnum = String(row.priorityEnum ?? '').trim() || undefined;
    const groupKey = resolveSpecimenBarcodePrintGroupKey(
      requestId,
      encounterId,
      mergeClusterKey,
    );
    const existing = groupMap.get(groupKey);
    if (!existing) {
      groupMap.set(groupKey, {
        serviceRequestId: requestId,
        barcode,
        itemName,
        memberServiceRequestIds: [requestId],
        labelCount,
        priorityEnum,
        ...(encounterId ? { encounterId } : {}),
        ...(mergeDeviceId ? { specimenTubeMergeDeviceId: mergeDeviceId } : {}),
      });
      continue;
    }
    appendSpecimenBarcodeMemberId(existing, requestId);
    // 合管锚点：与 LIS MedSync 一致，取组内最小 serviceRequestId
    if (compareSnowflakeIdAsc(requestId, existing.serviceRequestId) < 0) {
      existing.serviceRequestId = requestId;
      existing.barcode = barcode;
    }
    if (itemName && !existing.itemName.includes(itemName)) {
      existing.itemName = existing.itemName ? `${existing.itemName}、${itemName}` : itemName;
    }
    existing.labelCount = Math.max(existing.labelCount ?? 1, labelCount);
    if (priorityEnum === SERVICE_REQUEST_PRIORITY_EM) {
      existing.priorityEnum = SERVICE_REQUEST_PRIORITY_EM;
    }
  }
  return [...groupMap.values()];
}

/** 住院打印中心采血贴：同一就诊内按合管设备一组一张（无 device 则逐条）；禁止跨患者合管 */
export function groupPrintPendingSpecimenBarcodeRows(
  rows: readonly {
    orderId?: string;
    /** 申请业务号（LIS applyNo）；空则条码回落 orderId */
    busNo?: string;
    title?: string;
    specimenTubeMergeDeviceId?: string;
    specimenTubeMergeGroupNo?: number | string;
    encounterId?: string;
    bedNo?: string;
    patientName?: string;
    /** 检验开立数量展示（如「2次」；与申请单数量列同源 dosageDisplay） */
    dosageDisplay?: string;
    /** 医嘱优先级 code（EM=急） */
    priorityEnum?: string;
  }[],
): SpecimenBarcodePrintTarget[] {
  const groupMap = new Map<string, SpecimenBarcodePrintTarget>();
  for (const row of rows) {
    const requestId = String(row.orderId ?? '').trim();
    if (!requestId) {
      continue;
    }
    const itemName = String(row.title ?? '').trim();
    // 采血费类目同为 22，合管/医技待打列表须跳过，避免单独打一张「采血费」贴
    if (isBloodCollectionFeePrintItem(itemName)) {
      continue;
    }
    const barcode = resolveSpecimenBarcodeValue(row.busNo, requestId);
    const encounterId = String(row.encounterId ?? '').trim();
    const mergeDeviceId = resolveSpecimenBarcodeMergeDeviceId(
      row.specimenTubeMergeDeviceId,
      row.priorityEnum,
    );
    const mergeClusterKey = resolveSpecimenBarcodeMergeClusterKey(
      row.specimenTubeMergeDeviceId,
      row.specimenTubeMergeGroupNo,
      row.priorityEnum,
      encounterId || undefined,
    );
    const labelCount = resolveSpecimenBarcodeLabelCount(row.dosageDisplay);
    const priorityEnum = String(row.priorityEnum ?? '').trim() || undefined;
    const groupKey = resolveSpecimenBarcodePrintGroupKey(
      requestId,
      encounterId,
      mergeClusterKey,
    );
    const existing = groupMap.get(groupKey);
    if (!existing) {
      groupMap.set(groupKey, {
        serviceRequestId: requestId,
        barcode,
        itemName,
        memberServiceRequestIds: [requestId],
        labelCount,
        priorityEnum,
        encounterId: encounterId || undefined,
        bedNo: String(row.bedNo ?? '').trim() || undefined,
        patientName: String(row.patientName ?? '').trim() || undefined,
        ...(mergeDeviceId ? { specimenTubeMergeDeviceId: mergeDeviceId } : {}),
      });
      continue;
    }
    appendSpecimenBarcodeMemberId(existing, requestId);
    if (compareSnowflakeIdAsc(requestId, existing.serviceRequestId) < 0) {
      existing.serviceRequestId = requestId;
      existing.barcode = barcode;
    }
    if (itemName && !existing.itemName.includes(itemName)) {
      existing.itemName = existing.itemName ? `${existing.itemName}、${itemName}` : itemName;
    }
    existing.labelCount = Math.max(existing.labelCount ?? 1, labelCount);
    if (priorityEnum === SERVICE_REQUEST_PRIORITY_EM) {
      existing.priorityEnum = SERVICE_REQUEST_PRIORITY_EM;
    }
  }
  return [...groupMap.values()];
}

export { resolveSpecimenBarcodeUrgencyText };

/** 处置执行单：一张 A4 汇总所选（或全部待执行）非检验处置行 */
export function groupTreatmentExecutePrintTargets(
  rows: NurseAdviceExecuteRow[],
  selectedKeys?: string[],
): TreatmentExecutePrintTarget | null {
  const keyed = filterServiceExecuteRows(rows, selectedKeys).filter(
    (row) => !isLabSpecimenExecuteRow(row),
  );
  const serviceRequestIds: string[] = [];
  for (const row of keyed) {
    const requestId = String(row.requestId).trim();
    if (requestId && !serviceRequestIds.includes(requestId)) {
      serviceRequestIds.push(requestId);
    }
  }
  if (!serviceRequestIds.length) {
    return null;
  }
  return { serviceRequestIds };
}

/** 成组合并行展开为成员行，便于收集全部 medicationRequestId */
function expandMedicationExecuteRows(rows: NurseAdviceExecuteRow[]): NurseAdviceExecuteRow[] {
  const expanded: NurseAdviceExecuteRow[] = [];
  for (const row of rows) {
    const members = row.mergedMedicationMembers;
    if (members && members.length > 0) {
      expanded.push(...members);
      continue;
    }
    expanded.push(row);
  }
  return expanded;
}

function filterMedicationExecuteRows(
  rows: NurseAdviceExecuteRow[],
  selectedKeys?: string[],
): NurseAdviceExecuteRow[] {
  const medicationRows = rows.filter(isMedicationExecuteRowKey);
  if (selectedKeys && selectedKeys.length > 0) {
    return medicationRows.filter((row) => selectedKeys.includes(row.key));
  }
  return medicationRows.filter(isExecuteRowPending);
}

function isSkinTestMedicationRow(row: NurseAdviceExecuteRow): boolean {
  return row.skinTestFlag === true;
}

function isNebulizationMedicationRow(row: NurseAdviceExecuteRow): boolean {
  return INHALATION_METHOD_CODES.has(normalizeMedicationMethodCode(row.methodCode));
}

function isInjectionMedicationRow(row: NurseAdviceExecuteRow): boolean {
  if (row.infusionFlag === true) {
    return false;
  }
  const methodCode = normalizeMedicationMethodCode(row.methodCode);
  if (INFUSION_METHOD_CODES.has(methodCode)) {
    return false;
  }
  return INJECTION_METHOD_CODES.has(methodCode);
}

function isOralMedicationRow(row: NurseAdviceExecuteRow): boolean {
  return ORAL_METHOD_CODES.has(normalizeMedicationMethodCode(row.methodCode));
}

/** 药品标签分组（按输液组 + labelScope 过滤） */
export function groupMedicationLabelPrintTargets(
  rows: NurseAdviceExecuteRow[],
  selectedKeys: string[] | undefined,
  scope: 'injection' | 'nebulization' | 'oral' | 'skin_test',
): MedicationLabelPrintGroup[] {
  const medicationRows = expandMedicationExecuteRows(rows.filter(isMedicationExecuteRowKey));
  const keyed =
    selectedKeys && selectedKeys.length > 0
      ? medicationRows.filter((row) => selectedKeys.includes(row.key))
      : medicationRows.filter(isExecuteRowPending);
  const filtered = keyed.filter((row) => {
    if (scope === 'skin_test') {
      return isSkinTestMedicationRow(row);
    }
    if (scope === 'nebulization') {
      return isNebulizationMedicationRow(row);
    }
    if (scope === 'injection') {
      return isInjectionMedicationRow(row);
    }
    return isOralMedicationRow(row);
  });

  const map = new Map<string, MedicationLabelPrintGroup>();
  for (const row of filtered) {
    const requestId = String(row.requestId).trim();
    if (!requestId) {
      continue;
    }
    const groupKey = row.groupId?.trim() ? `g:${row.groupId}` : `r:${requestId}`;
    const existing = map.get(groupKey) ?? {
      groupId: row.groupId?.trim() || undefined,
      medicationRequestIds: [],
    };
    if (!existing.medicationRequestIds.includes(requestId)) {
      existing.medicationRequestIds.push(requestId);
    }
    map.set(groupKey, existing);
  }
  return [...map.values()];
}

/** 检查类处置行（serviceCategoryCode=23） */
export function isExamExecuteRow(row: NurseAdviceExecuteRow): boolean {
  if (row.rowType !== 'SERVICE' || !row.key.startsWith('SR:')) {
    return false;
  }
  return (row.serviceCategoryCode ?? '').trim() === SERVICE_CATEGORY_TEST;
}

/** 治疗/处置申请单（非检验/非检查处置） */
export function groupProcedureApplyPrintTargets(
  rows: NurseAdviceExecuteRow[],
  selectedKeys?: string[],
): TreatmentExecutePrintTarget | null {
  const keyed = filterServiceExecuteRows(rows, selectedKeys).filter(
    (row) => !isLabSpecimenExecuteRow(row) && !isExamExecuteRow(row),
  );
  const serviceRequestIds: string[] = [];
  for (const row of keyed) {
    const requestId = String(row.requestId).trim();
    if (requestId && !serviceRequestIds.includes(requestId)) {
      serviceRequestIds.push(requestId);
    }
  }
  if (!serviceRequestIds.length) {
    return null;
  }
  return { serviceRequestIds };
}

/** 护士站处方打印：从执行行解析药品申请主键 */
export function groupMedicationExecuteRequestIds(
  rows: NurseAdviceExecuteRow[],
  selectedKeys?: string[],
): string[] {
  const ids: string[] = [];
  for (const row of expandMedicationExecuteRows(filterMedicationExecuteRows(rows, selectedKeys))) {
    const requestId = String(row.requestId).trim();
    if (requestId && !ids.includes(requestId)) {
      ids.push(requestId);
    }
  }
  return ids;
}
