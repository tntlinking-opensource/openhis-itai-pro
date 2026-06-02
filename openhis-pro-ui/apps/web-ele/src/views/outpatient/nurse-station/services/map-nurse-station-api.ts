import type { ActionRecord } from '../components/types';
import type {
  NurseStationPatientQueueItemDto,
  NurseStationTreatmentLineDto,
  OrderExecutionSheetItemDto,
} from '#/api/outpatient-nurse-station/outpatient-nurse-station.types';

import type { NurseAdviceExecuteRow, NursePatient, SupplyItem } from '../components/types';

import { formatDateTime } from '@vben/utils';

/** 处置单 DTO 展示短码 → cln_procedure / IF-NUR-03B 持久化 request_table */
export function normalizeNurseStationRequestTableForApi(requestTable: string): string {
  const raw = requestTable.trim();
  if (!raw) {
    return '';
  }
  const u = raw.toUpperCase();
  if (u === 'SERVICE' || u === 'WKF_SERVICE_REQUEST' || u.endsWith('SERVICE_REQUEST')) {
    return 'wkf_service_request';
  }
  if (u === 'MEDICATION' || u === 'MED_MEDICATION_REQUEST' || u.includes('MEDICATION_REQUEST')) {
    return 'med_medication_request';
  }
  return raw;
}

/** 判断处置行是否属于诊疗类请求表 */
function isServiceRequestTable(requestTable: string): boolean {
  const u = requestTable.trim().toUpperCase();
  return u === 'SERVICE' || u.endsWith('SERVICE_REQUEST');
}

/** 判断处置行是否属于药品请求表 */
function isMedicationRequestTable(requestTable: string): boolean {
  const u = requestTable.trim().toUpperCase();
  return u === 'MEDICATION' || u.includes('MEDICATION_REQUEST');
}

/** 判断处置行是否属于耗材请求表 */
function isDeviceRequestTable(requestTable: string): boolean {
  const u = requestTable.trim().toUpperCase();
  return u === 'DEVICE' || u.includes('DEVICE_REQUEST');
}

/** 表格行主键：与批量执行请求中的 id 列表一一对应 */
export function treatmentLineToExecuteKey(row: NurseStationTreatmentLineDto): string {
  const rid = String(row.requestId ?? '').trim();
  const table = String(row.requestTable ?? '').trim();
  if (isServiceRequestTable(table)) {
    return `SR:${rid}`;
  }
  if (isMedicationRequestTable(table)) {
    return `MR:${rid}`;
  }
  const dispenseId = String(row.dispenseId ?? '').trim();
  if (dispenseId) {
    return `DD:${dispenseId}`;
  }
  return `DR:${rid}`;
}

/** 从勾选行主键解析出批量执行三类 id 列表 */
export function parseExecuteKeysToBatchIds(keys: string[]): {
  deviceDispenseIds: string[];
  medicationRequestIds: string[];
  serviceRequestIds: string[];
} {
  const serviceRequestIds: string[] = [];
  const medicationRequestIds: string[] = [];
  const deviceDispenseIds: string[] = [];
  for (const key of keys) {
    if (key.startsWith('SR:')) {
      serviceRequestIds.push(key.slice(3));
      continue;
    }
    if (key.startsWith('MR:')) {
      medicationRequestIds.push(key.slice(3));
      continue;
    }
    if (key.startsWith('DD:')) {
      deviceDispenseIds.push(key.slice(3));
    }
  }
  return { deviceDispenseIds, medicationRequestIds, serviceRequestIds };
}

function serviceLineDone(row: NurseStationTreatmentLineDto): boolean {
  const planned = row.plannedPerformCount;
  if (planned === null || planned === undefined || planned <= 0) {
    return false;
  }
  const performed = row.performCount ?? 0;
  const cancelled = row.cancelCount ?? 0;
  const net = performed - cancelled;
  return net >= planned;
}

function medicationLineDone(row: NurseStationTreatmentLineDto): boolean {
  const plan = row.medicationExecuteNum;
  if (plan === null || plan === undefined || plan <= 0) {
    return false;
  }
  const performed = row.performCount ?? 0;
  const cancelled = row.cancelCount ?? 0;
  const net = performed - cancelled;
  return net >= plan;
}

function buildExecutedCountDisplay(row: NurseStationTreatmentLineDto): string {
  const table = String(row.requestTable ?? '');
  const performed = row.performCount ?? 0;
  const cancelled = row.cancelCount ?? 0;
  const net = performed - cancelled;
  if (isServiceRequestTable(table)) {
    const planned = row.plannedPerformCount;
    if (planned !== null && planned !== undefined && planned > 0) {
      return `${net}/${planned}`;
    }
  }
  if (isMedicationRequestTable(table)) {
    const plan = row.medicationExecuteNum;
    if (plan !== null && plan !== undefined && plan > 0) {
      return `${net}/${plan}`;
    }
  }
  return `${performed}`;
}

function lineDone(row: NurseStationTreatmentLineDto): boolean {
  const table = String(row.requestTable ?? '');
  if (isServiceRequestTable(table)) {
    return serviceLineDone(row);
  }
  if (isMedicationRequestTable(table)) {
    return medicationLineDone(row);
  }
  return false;
}

function lineStatusLabel(row: NurseStationTreatmentLineDto): string {
  const displayText = String(row.displayStatusEnumText ?? '').trim();
  if (displayText) {
    return displayText;
  }
  const chargeCode = String(row.displayStatusEnum ?? row.chargeStatusEnum ?? '')
    .trim()
    .toUpperCase();
  if (chargeCode === 'REFUNDING') {
    return '待退费';
  }
  if (chargeCode === 'REFUNDED') {
    return '已退费';
  }
  if (lineDone(row)) {
    return '已完成';
  }
  return '待执行';
}

function typeLabelFromRow(row: NurseStationTreatmentLineDto): string {
  const table = String(row.requestTable ?? '');
  if (isServiceRequestTable(table)) {
    return '处置';
  }
  if (isMedicationRequestTable(table)) {
    return '药品';
  }
  if (isDeviceRequestTable(table)) {
    return '耗材';
  }
  return '项目';
}

function buildContent(row: NurseStationTreatmentLineDto): string {
  const name = (row.itemName ?? '').trim();
  const spec = (row.specText ?? '').trim();
  if (name && spec) {
    return `${name}【${spec}】`;
  }
  return name || spec || '—';
}

function buildDosageText(row: NurseStationTreatmentLineDto): string {
  const table = String(row.requestTable ?? '');
  if (isServiceRequestTable(table)) {
    const p = row.plannedPerformCount;
    const c = row.performCount ?? 0;
    const a = row.cancelCount ?? 0;
    if (p !== null && p !== undefined) {
      return `计划${p}次，已完成${c}次，取消${a}次。`;
    }
  }
  if (isMedicationRequestTable(table)) {
    const n = row.medicationExecuteNum;
    if (n !== null && n !== undefined) {
      return `执行次数上限 ${n}。`;
    }
  }
  return '';
}

function buildExtension(row: NurseStationTreatmentLineDto): string {
  const parts: string[] = [];
  const chargeText = (row.chargeStatusEnumText ?? '').trim();
  const chargeCode = (row.chargeStatusEnum ?? '').trim();
  if (chargeText) {
    parts.push(`计费 ${chargeText}`);
  } else if (chargeCode) {
    parts.push(`计费状态 ${chargeCode}`);
  }
  const price = row.displayTotalPrice;
  if (price !== null && price !== undefined && String(price) !== '') {
    parts.push(`金额 ${String(price)}`);
  }
  return parts.join('；');
}

/** 将处置单诊疗/药品行映射为护士执行表行 */
export function mapTreatmentLineToExecuteRow(
  row: NurseStationTreatmentLineDto,
): NurseAdviceExecuteRow {
  const status = lineStatusLabel(row);
  const table = String(row.requestTable ?? '');
  const performed = row.performCount ?? 0;
  const cancelled = row.cancelCount ?? 0;
  const netPerformCount = performed - cancelled;
  const isMedication = isMedicationRequestTable(table);
  return {
    key: treatmentLineToExecuteKey(row),
    status,
    type: typeLabelFromRow(row),
    content: buildContent(row),
    startTime: '',
    signer: '—',
    department: '—',
    stopOrVoid: '',
    extension: buildExtension(row),
    dosageText: buildDosageText(row),
    executedCountDisplay: buildExecutedCountDisplay(row),
    performerDisplay: '—',
    requestId: String(row.requestId ?? '').trim() || undefined,
    requestTable: normalizeNurseStationRequestTableForApi(table) || undefined,
    netPerformCount,
    canCancelExecute: !isMedication && netPerformCount > 0,
  };
}

/** 仅映射诊疗与药品行；耗材行在侧栏以发放维度展示 */
export function mapTreatmentSheetToExecuteRows(
  serviceLines: NurseStationTreatmentLineDto[],
  medicationLines: NurseStationTreatmentLineDto[],
): NurseAdviceExecuteRow[] {
  const rows: NurseAdviceExecuteRow[] = [];
  for (const line of serviceLines) {
    rows.push(mapTreatmentLineToExecuteRow(line));
  }
  for (const line of medicationLines) {
    rows.push(mapTreatmentLineToExecuteRow(line));
  }
  return rows;
}

const DISPENSE_DONE_HINTS = ['COMPLETED', 'DISPENSED', 'DONE', 'CLOSED', 'FINISHED'];

/** 根据发放状态码推断侧栏卡片是否已发放 */
function isDispenseDoneCode(code: string): boolean {
  const u = code.trim().toUpperCase();
  if (!u) {
    return false;
  }
  return DISPENSE_DONE_HINTS.some((h) => u.includes(h));
}

/** 耗材发放行映射为侧栏耗材卡片 */
export function mapDeviceLineToSupplyItem(row: NurseStationTreatmentLineDto): SupplyItem {
  const dispenseId = String(row.dispenseId ?? '').trim();
  const id = dispenseId || `DR:${String(row.requestId)}`;
  const code = String(row.dispenseStatusEnum ?? '').trim();
  const statusText = (row.dispenseStatusEnumText ?? '').trim();
  const status: SupplyItem['status'] =
    statusText && (statusText.includes('已发') || statusText.includes('完成'))
      ? '已发放'
      : isDispenseDoneCode(code)
        ? '已发放'
        : '待发放';
  const qtyRaw = row.quantity;
  const quantity =
    typeof qtyRaw === 'number' ? qtyRaw : Number.parseFloat(String(qtyRaw ?? '0')) || 0;
  return {
    id,
    name: (row.itemName ?? '').trim() || '—',
    spec: (row.specText ?? '').trim() || '—',
    usage: (row.deviceCategoryCode ?? '').trim() || '耗材',
    quantity,
    status,
  };
}

export function mapDeviceLinesToSupplyItems(rows: NurseStationTreatmentLineDto[]): SupplyItem[] {
  return rows.map(mapDeviceLineToSupplyItem);
}

/** 队列 DTO → 护士站患者卡片数据 */
export function mapQueueItemToNursePatient(
  dto: NurseStationPatientQueueItemDto,
  index: number,
): NursePatient {
  const encounterTime = (dto.encounterTime ?? '').trim();
  const dayPart = encounterTime.length >= 10 ? encounterTime.slice(0, 10) : encounterTime;
  return {
    id: String(dto.encounterId),
    name: (dto.patientName ?? '').trim() || '—',
    encounterBusNo: (dto.slipNo ?? '').trim() || undefined,
    genderEnumText: (dto.genderEnumText ?? '').trim() || undefined,
    gender: '',
    age: (dto.ageText ?? '').trim() || '—',
    admissionTime: dayPart || '—',
    status: (dto.slipNo ?? '').trim() ? `流水 ${dto.slipNo}` : '—',
    priority: '普通',
    queueNumber: index + 1,
    isRegistered: true,
    patientType: 'outpatient',
    pendingExecuteCount: 0,
    registrationTime: encounterTime || undefined,
    paymentType: undefined,
    phone: undefined,
    medicalRecordNo: undefined,
    doctor: undefined,
  };
}

function formatRequestExtensionsHint(ext: OrderExecutionSheetItemDto['requestExtensions']): string {
  if (!ext) {
    return '';
  }
  if (ext.entries?.length) {
    try {
      return JSON.stringify(ext.entries[0]);
    } catch {
      return '';
    }
  }
  const rawText = ext.attributes?.rawText;
  if (typeof rawText === 'string') {
    return rawText.trim();
  }
  if (ext.attributes && Object.keys(ext.attributes).length > 0) {
    try {
      return JSON.stringify(ext.attributes);
    } catch {
      return '';
    }
  }
  return '';
}

/** 执行单读模型行 → 侧栏/抽屉展示用本地记录形状 */
export function mapOrderExecutionToActionRecord(row: OrderExecutionSheetItemDto): ActionRecord {
  const timeRaw = (row.occurrenceTime ?? row.recordedTime ?? '').trim();
  const time = timeRaw ? formatDateTime(timeRaw) : '—';
  const contentHint = formatRequestExtensionsHint(row.requestExtensions);
  const target = contentHint.length > 40 ? `${contentHint.slice(0, 40)}…` : contentHint || '—';
  return {
    id: `srv-${row.procedureId}`,
    time,
    operator: '—',
    type: '执行',
    action: (row.resultText ?? row.categoryEnum ?? '执行登记').trim() || '执行登记',
    target,
    result: '成功',
  };
}
