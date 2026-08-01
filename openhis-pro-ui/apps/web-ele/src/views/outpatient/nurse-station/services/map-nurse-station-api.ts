import type {
  ActionRecord,
  NurseAdviceExecuteRow,
  NursePatient,
  SupplyItem,
} from '../components/types';

import type {
  NurseStationPatientQueueItemDto,
  NurseStationTreatmentLineDto,
  OrderExecutionSheetItemDto,
} from '#/api/outpatient-nurse-station/outpatient-nurse-station.types';

import { formatDateTime } from '@vben/utils';

import {
  mergeConsecutiveRowsByDrugGroup,
  type DrugGroupDisplayItem,
  type DrugGroupKey,
} from '#/utils/doctor-station/drug-group-table';
import { formatMedicationDoseSummaryLine } from '#/utils/doctor-station/medication-dose-display';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { formatMoneyYuan } from '#/utils/format/money-scale';
import {
  canCancelSupplyDispense,
  resolveNurseExecuteDisplayStatusCode,
  resolveSupplyDispenseStatusCode,
  type SupplyDispenseStatusCode,
} from '#/utils/nurse-station/nurse-execute-status';
import { HERBAL_MEDICATION_CATEGORY_CODE } from '#/views/outpatient/doctor-station/advice/services/advice-tab.constants';
import { buildServiceApplyFormExtensionText } from '#/utils/service-apply-display';

function isRecord(v: unknown): v is Record<string, unknown> {
  return v !== null && typeof v === 'object' && !Array.isArray(v);
}

/** 解析 IF-NUR-02 处置单响应（兼容 publicRequestClient 已解包或仍多包一层 data） */
export function parseTreatmentSheetFromApiData(data: unknown): {
  deviceLines: NurseStationTreatmentLineDto[];
  medicationLines: NurseStationTreatmentLineDto[];
  serviceLines: NurseStationTreatmentLineDto[];
} {
  if (!isRecord(data)) {
    return { serviceLines: [], medicationLines: [], deviceLines: [] };
  }
  const body =
    isRecord(data.data) &&
    !Array.isArray(data.serviceLines) &&
    !Array.isArray(data.medicationLines) &&
    !Array.isArray(data.deviceLines)
      ? data.data
      : data;
  const readLines = (key: string): NurseStationTreatmentLineDto[] => {
    const raw = body[key];
    return Array.isArray(raw) ? (raw as NurseStationTreatmentLineDto[]) : [];
  };
  return {
    serviceLines: readLines('serviceLines'),
    medicationLines: readLines('medicationLines'),
    deviceLines: readLines('deviceLines'),
  };
}

/** 检验/检查/治疗申请单号前缀（与后端 AggregateTypeConstants.REQUISITION_APPLICATION 一致） */
export const NURSE_STATION_APPLY_FORM_NO_PREFIX = 'AR';

/** 判断 prescriptionNo 是否为 AR 前缀申请单整单号 */
export function isNurseStationApplyFormPrescriptionNo(prescriptionNo?: null | string): boolean {
  const value = String(prescriptionNo ?? '').trim();
  return value.length > 0 && value.startsWith(NURSE_STATION_APPLY_FORM_NO_PREFIX);
}

/** 解析护士执行表「申请单号」列展示值 */
export function resolveNurseExecuteApplyFormNo(prescriptionNo?: null | string): string | undefined {
  if (!isNurseStationApplyFormPrescriptionNo(prescriptionNo)) {
    return undefined;
  }
  return String(prescriptionNo).trim();
}
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

/** 护士执行表行是否为药品医嘱（requestTable / MR: / 成组合并行主键，勿用 type 展示文案） */
export function isNurseExecuteMedicationRow(
  row: Pick<NurseAdviceExecuteRow, 'key' | 'requestTable' | 'rowType'>,
): boolean {
  if (row.rowType === 'MEDICATION') {
    return true;
  }
  const table = String(row.requestTable ?? '').trim();
  if (table && isMedicationRequestTable(table)) {
    return true;
  }
  return row.key.startsWith('MR:') || row.key.startsWith('merged-med-');
}

/** 判断处置行是否属于耗材请求表 */
function isDeviceRequestTable(requestTable: string): boolean {
  const u = requestTable.trim().toUpperCase();
  return u === 'DEVICE' || u.includes('DEVICE_REQUEST');
}

/** 表格行主键：与批量执行请求中的 id 列表一一对应 */
export function treatmentLineToExecuteKey(row: NurseStationTreatmentLineDto): string {
  const rid = preserveApiIdForRequest(row.requestId) ?? '';
  const table = String(row.requestTable ?? '').trim();
  if (isServiceRequestTable(table)) {
    return `SR:${rid}`;
  }
  if (isMedicationRequestTable(table)) {
    return `MR:${rid}`;
  }
  const dispenseId = preserveApiIdForRequest(row.dispenseId) ?? '';
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

function rowTypeFromRequestTable(requestTable: string): NurseAdviceExecuteRow['rowType'] {
  if (isMedicationRequestTable(requestTable)) {
    return 'MEDICATION';
  }
  if (isServiceRequestTable(requestTable)) {
    return 'SERVICE';
  }
  if (isDeviceRequestTable(requestTable)) {
    return 'DEVICE';
  }
  return 'OTHER';
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

/** 药品单次剂量与总量行（对齐医生站：计量+最小单位括号，单位相同时无括号） */
function buildMedicationDosageText(row: NurseStationTreatmentLineDto): string {
  return formatMedicationDoseSummaryLine({
    dosageDisplay: row.dosageDisplay,
    dose: row.dose,
    doseUnitCode: row.doseUnitCode,
    doseUnitName: row.doseUnitName,
    splitDose: row.splitDose,
    splitDoseUnitCode: row.splitDoseUnitCode,
    splitDoseUnitName: row.splitDoseUnitName,
    quantity: row.quantity,
    unitCode: row.unitCode,
    unitName: row.unitName,
  });
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
  return formatMoneyYuan(n);
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
  const charge = buildChargeExtension(row);
  return buildServiceApplyFormExtensionText(
    {
      remark: row.remark,
      serviceRequestDetails: row.serviceRequestDetails,
    },
    charge ? [charge] : [],
  );
}

/** 将处置单诊疗/药品行映射为护士执行表行 */
export function mapTreatmentLineToExecuteRow(
  row: NurseStationTreatmentLineDto,
): NurseAdviceExecuteRow {
  const statusEnum = resolveNurseExecuteDisplayStatusCode(row);
  const status = lineStatusLabel(row);
  const table = String(row.requestTable ?? '');
  const performed = row.performCount ?? 0;
  const cancelled = row.cancelCount ?? 0;
  const netPerformCount = performed - cancelled;
  const isMedication = isMedicationRequestTable(table);
  return {
    key: treatmentLineToExecuteKey(row),
    status,
    statusEnum,
    rowType: rowTypeFromRequestTable(table),
    type: typeLabelFromRow(row),
    content: buildContent(row),
    startTime: '',
    signer: '—',
    department: '—',
    stopOrVoid: '',
    extension: isMedication ? buildMedicationExtension(row) : buildExtension(row),
    dosageText: isMedication ? buildMedicationDosageText(row) : buildDosageText(row),
    executedCountDisplay: isMedication ? undefined : buildExecutedCountDisplay(row),
    performerDisplay: String(row.lastPerformerDisplay ?? '').trim() || '—',
    requestId: preserveApiIdForRequest(row.requestId) ?? undefined,
    requestTable: normalizeNurseStationRequestTableForApi(table) || undefined,
    netPerformCount,
    canCancelExecute: !isMedication && netPerformCount > 0,
    groupId: row.groupId === null || row.groupId === undefined ? undefined : String(row.groupId),
    prescriptionNo: (row.prescriptionNo ?? '').trim() || undefined,
    applyFormNo: resolveNurseExecuteApplyFormNo(row.prescriptionNo),
    medCategoryCodeText: (row.medCategoryCodeText ?? '').trim() || undefined,
    infusionFlag: row.infusionFlag ?? undefined,
    skinTestFlag: row.skinTestFlag ?? undefined,
    tcmFlag: row.tcmFlag ?? undefined,
    methodCode: (row.methodCode ?? '').trim() || undefined,
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

function nurseMedicationGroupMergeKey(row: NurseStationTreatmentLineDto): DrugGroupKey {
  const gid = String(row.groupId ?? '').trim();
  return gid === '' ? '' : gid;
}

/** 药品行 → 成组括线展示项（与医生站 advice-list 字段对齐） */
function nurseStationTreatmentLineToDrugGroupItem(
  row: NurseStationTreatmentLineDto,
): DrugGroupDisplayItem {
  return {
    selectedDrugName: trimDisplayText(row.itemName),
    selectedDrugSpec: trimDisplayText(row.specText),
    pharmacy: '',
    singleDose: row.dose ?? '',
    doseSubUnit: trimDisplayText(row.doseUnitName ?? row.doseUnitCode),
    totalAmount: row.quantity ?? '',
    totalAmountUnit: trimDisplayText(row.unitName ?? row.unitCode),
    usage: trimDisplayText(row.methodName ?? row.methodCode),
    frequency: trimDisplayText(row.rateName ?? row.rateCode),
    medicationDays: row.dispensePerDuration ?? '',
    dosageDisplay: trimDisplayText(row.dosageDisplay) || undefined,
    splitDose: row.splitDose ?? undefined,
    splitDoseUnitCode: trimDisplayText(row.splitDoseUnitCode) || undefined,
    splitDoseUnitName: trimDisplayText(row.splitDoseUnitName) || undefined,
    doseUnitCode: trimDisplayText(row.doseUnitCode) || undefined,
  };
}

/** 相同 groupId 且连续的药品行合并为一行，挂 groupItems 供 AdviceOrderDisplayCell 括线展示 */
function mergeMedicationExecuteRowsForDisplay(
  lines: NurseStationTreatmentLineDto[],
): NurseAdviceExecuteRow[] {
  const sorted = sortNurseStationMedicationLines(lines);
  return mergeConsecutiveRowsByDrugGroup(sorted, {
    getGroupKey: nurseMedicationGroupMergeKey,
    toGroupItem: nurseStationTreatmentLineToDrugGroupItem,
    buildMergedRow: ({ slice, groupItems }) => {
      const members = slice.map((line) => mapTreatmentLineToExecuteRow(line));
      const firstMember = members[0];
      if (firstMember === undefined) {
        throw new Error('mergeMedicationExecuteRowsForDisplay: empty slice');
      }
      if (slice.length === 1) {
        return { ...firstMember };
      }
      const gid = String(slice[0]?.groupId ?? '').trim();
      const idPart = slice
        .map((line) => preserveApiIdForRequest(line.requestId) ?? '')
        .filter((part) => part.length > 0)
        .join('&');
      return {
        ...firstMember,
        key: gid ? `merged-med-${gid}-${idPart}` : `merged-med-${idPart}`,
        groupItems,
        mergedMedicationMembers: members,
      };
    },
  });
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

/** 诊疗行按申请单号聚邻（与后端 IF-NUR-02 BR-NUR-015 一致） */
function sortNurseStationServiceLinesForApplyForm(
  lines: NurseStationTreatmentLineDto[],
): NurseStationTreatmentLineDto[] {
  return [...lines].toSorted((left, right) => {
    const leftFormKey = resolveServiceLineApplyFormSortKey(left.prescriptionNo);
    const rightFormKey = resolveServiceLineApplyFormSortKey(right.prescriptionNo);
    const formCompare = leftFormKey.localeCompare(rightFormKey);
    if (formCompare !== 0) {
      return formCompare;
    }
    const leftSort = left.sortNumber ?? Number.MAX_SAFE_INTEGER;
    const rightSort = right.sortNumber ?? Number.MAX_SAFE_INTEGER;
    if (leftSort !== rightSort) {
      return leftSort - rightSort;
    }
    return String(left.requestId ?? '').localeCompare(String(right.requestId ?? ''));
  });
}

/** 申请单聚邻排序键：AR 前缀申请单优先成组 */
function resolveServiceLineApplyFormSortKey(prescriptionNo?: null | string): string {
  if (isNurseStationApplyFormPrescriptionNo(prescriptionNo)) {
    return `0_${String(prescriptionNo).trim()}`;
  }
  const trimmed = String(prescriptionNo ?? '').trim();
  if (trimmed) {
    return `1_${trimmed}`;
  }
  return '2_';
}

/** 门诊护士站项目列表是否排除中药饮片（对齐 IF-DOR-10 BR-ORD-026 / 药房发药域） */
export function isNurseStationTcmMedicationLine(row: NurseStationTreatmentLineDto): boolean {
  if (row.tcmFlag === true) {
    return true;
  }
  if (row.tcmFlag === false) {
    return false;
  }
  return String(row.medCategoryCode ?? '').trim() === HERBAL_MEDICATION_CATEGORY_CODE;
}

function filterNurseStationExecuteMedicationLines(
  medicationLines: NurseStationTreatmentLineDto[],
): NurseStationTreatmentLineDto[] {
  return medicationLines.filter((line) => !isNurseStationTcmMedicationLine(line));
}

/** 仅映射诊疗与药品行；耗材行在侧栏以发放维度展示 */
export function mapTreatmentSheetToExecuteRows(
  serviceLines: NurseStationTreatmentLineDto[],
  medicationLines: NurseStationTreatmentLineDto[],
): NurseAdviceExecuteRow[] {
  const rows: NurseAdviceExecuteRow[] = [];
  for (const line of sortNurseStationServiceLinesForApplyForm(serviceLines)) {
    rows.push(mapTreatmentLineToExecuteRow(line));
  }
  rows.push(
    ...mergeMedicationExecuteRowsForDisplay(
      filterNurseStationExecuteMedicationLines(medicationLines),
    ),
  );
  return rows;
}

const SUPPLY_STATUS_DISPLAY: Record<SupplyDispenseStatusCode, SupplyItem['status']> = {
  PENDING: '待发放',
  PREPARED: '已配药',
  COMPLETED: '已发放',
  PART_COMPLETED: '已发放',
};

/** 解析侧栏耗材发放展示态与是否可取消执行 */
function resolveSupplyDisplayStatus(
  row: NurseStationTreatmentLineDto,
): Pick<SupplyItem, 'canCancelExecute' | 'status' | 'statusCode'> {
  const statusCode = resolveSupplyDispenseStatusCode(row);
  return {
    statusCode,
    status: SUPPLY_STATUS_DISPLAY[statusCode] ?? '待发放',
    canCancelExecute: canCancelSupplyDispense(statusCode),
  };
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
  const { status, statusCode, canCancelExecute } = resolveSupplyDisplayStatus(row);
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
    statusCode,
    canCancelExecute,
  };
}

export function mapDeviceLinesToSupplyItems(rows: NurseStationTreatmentLineDto[]): SupplyItem[] {
  return rows.map((row) => mapDeviceLineToSupplyItem(row));
}

/** 队列 DTO → 护士站患者卡片数据 */
export function mapQueueItemToNursePatient(
  dto: NurseStationPatientQueueItemDto,
  index: number,
): NursePatient {
  const encounterTime = (dto.encounterTime ?? '').trim();
  const dayPart = encounterTime.length >= 10 ? encounterTime.slice(0, 10) : encounterTime;
  return {
    id: preserveApiIdForRequest(dto.encounterId) ?? '',
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
    paymentType: (dto.contractDisplay ?? '').trim() || undefined,
    phone: undefined,
    medicalRecordNo: undefined,
    doctor: undefined,
  };
}

/** 执行单读模型行 → 侧栏/抽屉展示用本地记录形状（展示列由后端 IF-NUR-03 直接下发） */
export function mapOrderExecutionToActionRecord(row: OrderExecutionSheetItemDto): ActionRecord {
  const timeRaw = (row.occurrenceTime ?? row.recordedTime ?? '').trim();
  const time = timeRaw ? formatDateTime(timeRaw) : '—';
  const targetRaw = String(row.orderItemDisplayName ?? '').trim();
  const target = targetRaw.length > 40 ? `${targetRaw.slice(0, 40)}…` : targetRaw || '—';
  const operator = String(row.operatorDisplayName ?? '').trim() || '—';
  const operatorLabel = String(row.operatorRoleDisplayText ?? '').trim() || '执行人';
  const action = String(row.actionDisplayText ?? '').trim() || '执行登记';
  return {
    id: `srv-${row.procedureId}`,
    time,
    operator,
    operatorLabel,
    type: 'EXECUTE',
    action,
    target,
    result: '成功',
  };
}
