import type { ActionRecord } from '../components/types';
import type { NurseAdviceExecuteRow, NursePatient, SupplyItem } from '../components/types';

import type {
  NurseStationPatientQueueItemDto,
  NurseStationTreatmentLineDto,
  OrderExecutionSheetItemDto,
} from '#/api/outpatient-nurse-station/outpatient-nurse-station.types';

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

/** 护士执行表行是否为药品医嘱（requestTable / MR: 行主键，勿用 type 展示文案） */
export function isNurseExecuteMedicationRow(
  row: Pick<NurseAdviceExecuteRow, 'key' | 'requestTable'>,
): boolean {
  const table = String(row.requestTable ?? '').trim();
  if (table && isMedicationRequestTable(table)) {
    return true;
  }
  return row.key.startsWith('MR:');
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

function trimDisplayText(value: unknown): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value).trim();
}

/** 药品单次剂量与总量行（对齐医生站 BR-ORD-022 展示口径） */
function buildMedicationDosageText(row: NurseStationTreatmentLineDto): string {
  const qtyRaw = row.quantity;
  const unit = trimDisplayText(row.unitName ?? row.unitCode);
  const right =
    qtyRaw !== null && qtyRaw !== undefined && trimDisplayText(qtyRaw) !== '' && unit
      ? `共${qtyRaw}${unit}`
      : qtyRaw !== null && qtyRaw !== undefined && trimDisplayText(qtyRaw) !== ''
        ? `共${qtyRaw}`
        : '';
  const doseRaw = row.dose;
  const doseU = trimDisplayText(row.doseUnitName ?? row.doseUnitCode);
  const left =
    doseRaw !== null && doseRaw !== undefined && trimDisplayText(doseRaw) !== '' && doseU
      ? `${doseRaw}${doseU}/次`
      : doseRaw !== null && doseRaw !== undefined && trimDisplayText(doseRaw) !== ''
        ? `${doseRaw}/次`
        : '';
  if (left && right) {
    return `${left}，${right}。`;
  }
  if (left) {
    return `${left}。`;
  }
  if (right) {
    return `${right}。`;
  }
  return '';
}

/** 药品用法、频次与天数行（对齐医生站 extension 口径） */
function buildMedicationUsageText(row: NurseStationTreatmentLineDto): string {
  const method = trimDisplayText(row.methodName ?? row.methodCode);
  const rate = trimDisplayText(row.rateName ?? row.rateCode);
  const durRaw = row.dispensePerDuration;
  let dayPart = '';
  if (durRaw !== null && durRaw !== undefined) {
    const d = Number(durRaw);
    if (Number.isFinite(d) && d > 0) {
      dayPart = ` (${Math.trunc(d)}天)`;
    }
  }
  if (method && rate) {
    return `${method}, ${rate}${dayPart}`;
  }
  if (method) {
    return `${method}${dayPart}`;
  }
  if (rate) {
    return `${rate}${dayPart}`;
  }
  return dayPart.trim();
}

function formatDisplayTotalPriceYuan(
  value: NurseStationTreatmentLineDto['displayTotalPrice'],
): string {
  if (value === null || value === undefined || String(value).trim() === '') {
    return '';
  }
  const n = typeof value === 'number' ? value : Number(String(value).trim());
  if (!Number.isFinite(n)) {
    return '';
  }
  return `${n.toFixed(2)}元`;
}

function buildChargeExtension(row: NurseStationTreatmentLineDto): string {
  const parts: string[] = [];
  const chargeText = (row.chargeStatusEnumText ?? '').trim();
  const chargeCode = (row.chargeStatusEnum ?? '').trim();
  if (chargeText) {
    parts.push(chargeText);
  } else if (chargeCode) {
    parts.push(chargeCode);
  }
  const priceText = formatDisplayTotalPriceYuan(row.displayTotalPrice);
  if (priceText) {
    parts.push(priceText);
  }
  return parts.join('；');
}

function buildMedicationExtension(row: NurseStationTreatmentLineDto): string {
  const parts: string[] = [];
  const usage = buildMedicationUsageText(row);
  if (usage) {
    parts.push(usage);
  }
  const charge = buildChargeExtension(row);
  if (charge) {
    parts.push(charge);
  }
  return parts.join('；');
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
  return '';
}

function buildExtension(row: NurseStationTreatmentLineDto): string {
  return buildChargeExtension(row);
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
    extension: isMedication ? buildMedicationExtension(row) : buildExtension(row),
    dosageText: isMedication ? buildMedicationDosageText(row) : buildDosageText(row),
    executedCountDisplay: isMedication ? undefined : buildExecutedCountDisplay(row),
    performerDisplay: '—',
    requestId: String(row.requestId ?? '').trim() || undefined,
    requestTable: normalizeNurseStationRequestTableForApi(table) || undefined,
    netPerformCount,
    canCancelExecute: !isMedication && netPerformCount > 0,
    groupId: row.groupId != null ? String(row.groupId) : undefined,
    prescriptionNo: (row.prescriptionNo ?? '').trim() || undefined,
    medCategoryCodeText: (row.medCategoryCodeText ?? '').trim() || undefined,
    serviceCategoryCode: (row.serviceCategoryCode ?? '').trim() || undefined,
    serviceCategoryCodeText: (row.serviceCategoryCodeText ?? '').trim() || undefined,
  };
}

function nurseMedicationLineSortSeq(raw: NurseStationTreatmentLineDto['sortNumber']): number {
  if (raw === null || raw === undefined) {
    return Number.MAX_SAFE_INTEGER;
  }
  const n = typeof raw === 'number' ? raw : Number(raw);
  return Number.isFinite(n) ? n : Number.MAX_SAFE_INTEGER;
}

function nurseMedicationGroupSortKey(row: NurseStationTreatmentLineDto): string {
  const gid = String(row.groupId ?? '').trim();
  return gid === '' ? '\uFFFF' : gid;
}

/** 药品行排序：与医生站 BR-ORD-018 一致（sortNumber 升序 → 成组聚邻 → requestId） */
function sortNurseStationMedicationLines(
  lines: NurseStationTreatmentLineDto[],
): NurseStationTreatmentLineDto[] {
  return [...lines].toSorted((a, b) => {
    const sa = nurseMedicationLineSortSeq(a.sortNumber);
    const sb = nurseMedicationLineSortSeq(b.sortNumber);
    if (sa !== sb) {
      return sa - sb;
    }

    const gcmp = nurseMedicationGroupSortKey(a).localeCompare(nurseMedicationGroupSortKey(b));
    if (gcmp !== 0) {
      return gcmp;
    }

    return String(a.requestId ?? '').localeCompare(String(b.requestId ?? ''));
  });
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
  for (const line of sortNurseStationMedicationLines(medicationLines)) {
    rows.push(mapTreatmentLineToExecuteRow(line));
  }
  return rows;
}

const DISPENSE_PENDING_CODES = ['DRAFT', 'PREPARATION', 'IN_PROGRESS'];

/** 解析侧栏耗材发放展示态与是否可取消执行 */
function resolveSupplyDisplayStatus(
  row: NurseStationTreatmentLineDto,
): Pick<SupplyItem, 'status' | 'canCancelExecute'> {
  const code = String(row.dispenseStatusEnum ?? '')
    .trim()
    .toUpperCase();
  const statusText = (row.dispenseStatusEnumText ?? '').trim();
  if (code === 'PREPARED' || statusText.includes('已配')) {
    return { status: '已配药', canCancelExecute: true };
  }
  if (
    code === 'COMPLETED' ||
    code === 'PART_COMPLETED' ||
    statusText.includes('已发') ||
    statusText.includes('部分发')
  ) {
    return { status: '已发放', canCancelExecute: true };
  }
  if (
    DISPENSE_PENDING_CODES.includes(code) ||
    statusText.includes('待配') ||
    statusText.includes('待发')
  ) {
    return { status: '待发放', canCancelExecute: false };
  }
  if (isDispenseDoneCode(code)) {
    return { status: '已发放', canCancelExecute: false };
  }
  return { status: '待发放', canCancelExecute: false };
}

const DISPENSE_DONE_HINTS = ['COMPLETED', 'DISPENSED', 'DONE', 'CLOSED', 'FINISHED', 'PREPARED'];

/** 根据发放状态码推断是否为终态/已处理（非待执行勾选） */
function isDispenseDoneCode(code: string): boolean {
  const u = code.trim().toUpperCase();
  if (!u) {
    return false;
  }
  return DISPENSE_DONE_HINTS.some((h) => u.includes(h));
}

/** 从侧栏耗材项解析 IF-NUR-05 所需发放单 id */
export function resolveSupplyDispenseIdForApi(item: SupplyItem): string {
  const fromField = String(item.dispenseId ?? '').trim();
  if (/^\d+$/.test(fromField)) {
    return fromField;
  }
  const raw = String(item.id ?? '').trim();
  if (/^\d+$/.test(raw)) {
    return raw;
  }
  if (raw.startsWith('DD:')) {
    return raw.slice(3);
  }
  return '';
}

/** 耗材发放行映射为侧栏耗材卡片 */
export function mapDeviceLineToSupplyItem(row: NurseStationTreatmentLineDto): SupplyItem {
  const dispenseId = String(row.dispenseId ?? '').trim();
  const id = dispenseId || `DR:${String(row.requestId)}`;
  const { status, canCancelExecute } = resolveSupplyDisplayStatus(row);
  const qtyRaw = row.quantity;
  const quantity =
    typeof qtyRaw === 'number' ? qtyRaw : Number.parseFloat(String(qtyRaw ?? '0')) || 0;
  return {
    id,
    dispenseId: dispenseId || undefined,
    name: (row.itemName ?? '').trim() || '—',
    spec: (row.specText ?? '').trim() || '—',
    usage: (row.deviceCategoryCode ?? '').trim() || '耗材',
    quantity,
    status,
    canCancelExecute,
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
