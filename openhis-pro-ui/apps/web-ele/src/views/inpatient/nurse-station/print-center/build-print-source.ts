import type { NurseStationModulePatient } from '../components/nurse-station-module-patient.types';
import type { PrintPendingRow } from './print-center.types';

import type { InpatientDoctorStationAdviceRowDto } from '#/api/inpatient/doctor-station/inpatient-doctor-station-advice.types';
import { httpListInpatientDoctorStationAdvice } from '#/api/inpatient/doctor-station/inpatient-doctor-station-advice.http';
import type {
  NurseStationOrderExecutePatientGroupDto,
  NurseStationOrderExecuteRowDto,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-order-execute.types';
import type { NurseStationTreatmentLineDto } from '#/api/outpatient-nurse-station/outpatient-nurse-station.types';
import type { InpatientDispenseSheetPrintSource } from '#/utils/print/mappers/inpatient-dispense-sheet';
import type { InpatientDispenseSummaryPrintSource } from '#/utils/print/mappers/inpatient-dispense-summary';
import {
  formatOrderDispensePatientHeadText,
  formatOrderDispenseTotalQuantityText,
  shouldUsePackageBatchDispenseQuantity,
  type InpatientOrderDispenseSheetPatientBlock,
  type InpatientOrderDispenseSheetPrintSource,
} from '#/utils/print/mappers/inpatient-order-dispense-sheet';
import { resolveStandingOrderPackageBatchSiteEnabled } from '#/utils/print/standing-order-package-batch-site';
import type { InpatientFeeDetailPrintSource } from '#/utils/print/mappers/inpatient-fee-detail';
import type {
  InpatientFeeListFeeRow,
  InpatientFeeListPrintSource,
} from '#/utils/print/mappers/inpatient-fee-list';
import type {
  InpatientNursingRecordPrintSource,
  InpatientNursingRecordRow,
} from '#/utils/print/mappers/inpatient-nursing-record';
import type { InpatientPatrolCardPrintSource } from '#/utils/print/mappers/inpatient-patrol-card';
import type { InpatientOralMedLabelPrintSource } from '#/utils/print/mappers/inpatient-oral-med-label';
import type {
  InpatientOralMedSheetDrugRow,
  InpatientOralMedSheetPatientBlock,
  InpatientOralMedSheetPrintSource,
} from '#/utils/print/mappers/inpatient-oral-med-sheet';
import type { InpatientExamApplyPrintSource } from '#/utils/print/mappers/inpatient-exam-apply';
import type { InpatientLabApplyPrintSource } from '#/utils/print/mappers/inpatient-lab-apply';
import type {
  InpatientTempOrderPrintSource,
  InpatientTempOrderRow,
} from '#/utils/print/mappers/inpatient-temp-order';
import type { InpatientBedCardPrintSource } from '#/utils/print/mappers/inpatient-bed-card';
import type { InpatientWristbandPrintSource } from '#/utils/print/mappers/inpatient-wristband';
import type { OutpatientInfusionLabelPrintSource } from '#/utils/print/mappers/outpatient-infusion-label';

import dayjs from 'dayjs';

import type {
  NurseStationCostDetailLineDto,
  NurseStationCostDetailStatusFilter,
  NurseStationCostSummaryLineDto,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-cost-detail.types';

import { httpQueryNurseStationInpatientCostDetail } from '#/api/inpatient/nurse-station/inpatient-nurse-station-cost-detail.http';
import { httpGetPatientDetail } from '#/api/inpatient/nurse-station/inpatient-nurse-station.http';
import type { PatientDetailDto } from '#/api/inpatient/nurse-station/inpatient-nurse-station.types';
import {
  httpGetMedicationSummarySlipDetail,
  httpGetMedicationSummarySlipPatientDetail,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-dispense-summary.http';
import { httpListNurseStationNursingRecords } from '#/api/inpatient/nurse-station/inpatient-nurse-station-nursing-record.http';
import { httpListNurseStationOrderAudit } from '#/api/inpatient/nurse-station/inpatient-nurse-station-order-audit.http';
import type { NurseStationOrderAuditRowDto } from '#/api/inpatient/nurse-station/inpatient-nurse-station-order-audit.types';
import { httpListNurseStationOrderExecuteForPrint } from '#/api/inpatient/nurse-station/inpatient-nurse-station-order-execute.http';
import {
  INFUSION_METHOD_CODES,
  METHOD_CODE_IV_INJECTION,
  ORAL_METHOD_CODES,
  normalizeMedicationMethodCode,
} from '#/utils/print/constants/medication-method-codes';
import {
  SERVICE_CATEGORY_DISCHARGE,
  SERVICE_CATEGORY_ENTRUST,
  SERVICE_CATEGORY_NURSING,
  SERVICE_CATEGORY_PROOF,
  SERVICE_CATEGORY_TEST,
  SERVICE_CATEGORY_TRANSFER,
} from '#/utils/print/constants/service-request-category-codes';
import { buildMedicationContentTitleWithPackageSplit } from '#/utils/doctor-station/package-split-ratio-display';
import { decimalAdd } from '#/utils/format/decimal-util';
import { formatMoneyDisplay } from '#/utils/format/money-scale';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { parseTcmCollapsedHerbDisplay } from '#/utils/tcm-prescription-display';
import { resolvePrintHospitalName } from '#/utils/print/resolve-print-hospital-name';
import {
  SERVICE_APPLY_BODY_STRUCTURE_TABLE,
  SERVICE_APPLY_SPECIMEN_TABLE,
} from '#/utils/service-apply-display';
import {
  formatCostDetailQuantity,
  formatCostSummaryQuantity,
} from '../cost-details/services/map-nurse-station-cost-detail';
import {
  joinPackageChildrenDetailText,
  joinServiceRequestDetailNames,
} from '../order-execute/services/resolve-order-execute-label-print';

function str(value: null | number | string | undefined): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value).trim();
}

function moneyText(value: null | number | string | undefined): string {
  if (value === null || value === undefined || value === '') {
    return '';
  }
  return formatMoneyDisplay(value);
}

function printedAtNow(): string {
  return `打印时间：${dayjs().format('YYYY-MM-DD HH:mm')}`;
}

function ageTextOf(patient?: NurseStationModulePatient): string {
  if (patient?.age == null) {
    return '';
  }
  return `${patient.age}岁`;
}

/**
 * 费用打印单头：页面临时挂起的患者队列余额/床位等可能已过期，
 * 打印前按就诊重拉详情并覆盖可变字段（失败则回落原入参）。
 */
async function resolveFreshFeePrintPatient(
  patient: NurseStationModulePatient,
): Promise<NurseStationModulePatient> {
  const encounterId = preserveApiIdForRequest(patient.id);
  if (!encounterId) {
    return patient;
  }
  try {
    const res = await httpGetPatientDetail({ encounterId });
    const detail = res.detail;
    if (!detail) {
      return patient;
    }
    return mergeFeePrintPatientFromDetail(patient, detail);
  } catch (error) {
    console.warn('[print-center] 费用打印刷新患者详情失败，沿用页面患者快照', error);
    return patient;
  }
}

/** 将患者详情中的可变字段合并进费用打印用患者快照 */
function mergeFeePrintPatientFromDetail(
  patient: NurseStationModulePatient,
  detail: PatientDetailDto,
): NurseStationModulePatient {
  const diagnosisFromList = Array.isArray(detail.diagnosisList)
    ? detail.diagnosisList
        .map((line) => String(line ?? '').trim())
        .filter(Boolean)
        .join('；')
    : '';
  const diagnosis = str(detail.diagnosisSummary) || diagnosisFromList || str(patient.diagnosis);
  const ageFromText = str(detail.ageText).match(/\d+/);
  const ageParsed = ageFromText ? Number.parseInt(ageFromText[0] ?? '', 10) : Number.NaN;
  return {
    ...patient,
    patientId: preserveApiIdForRequest(detail.patientId) ?? patient.patientId,
    encounterBusNo: str(detail.encounterBusNo) || patient.encounterBusNo,
    admissionAt: str(detail.admissionTime) || str(detail.startTime) || patient.admissionAt,
    inWardAt: str(detail.startTime) || patient.inWardAt,
    dischargeAt: str(detail.endTime) || patient.dischargeAt,
    bedNo: str(detail.bedName) || patient.bedNo,
    name: str(detail.patientName) || patient.name,
    gender: str(detail.genderEnumText) || patient.gender,
    age: Number.isFinite(ageParsed) ? ageParsed : patient.age,
    nurseLevel: str(detail.nursingLevelName) || patient.nurseLevel,
    feeType: str(detail.contractDisplay) || patient.feeType,
    attendingDoctorName: str(detail.attendingDoctorName) || patient.attendingDoctorName,
    diagnosis,
    organizationName: str(detail.organizationName) || patient.organizationName,
    organizationId: preserveApiIdForRequest(detail.organizationId) ?? patient.organizationId,
    regionName: str(detail.wardName) || patient.regionName,
    wardName: str(detail.houseName) || patient.wardName,
    accountId: preserveApiIdForRequest(detail.accountId) ?? patient.accountId,
    accountBalance: detail.accountBalance ?? patient.accountBalance,
    accountBalanceDisplayMode:
      detail.accountBalanceDisplayMode ?? patient.accountBalanceDisplayMode,
    accountBalanceSummaryText:
      detail.accountBalanceSummaryText ?? patient.accountBalanceSummaryText,
    accountBalanceSubText: detail.accountBalanceSubText ?? patient.accountBalanceSubText,
    availableBalance: detail.availableBalance ?? patient.availableBalance,
    pendingSettlementTotal: detail.pendingSettlementTotal ?? patient.pendingSettlementTotal,
    lowBalanceWarning: detail.lowBalanceWarning === true || patient.lowBalanceWarning,
    statusEnum: str(detail.statusEnum) || patient.statusEnum,
  };
}

/** 病区/病房打印文案：占位「-」打成空串 */
function printLocationText(value: null | string | undefined): string {
  const text = str(value);
  return text === '-' ? '' : text;
}

function resolvePatient(
  patients: NurseStationModulePatient[],
  row?: PrintPendingRow,
): NurseStationModulePatient | undefined {
  if (row?.encounterId) {
    const byId = patients.find((p) => str(p.id) === str(row.encounterId));
    if (byId) {
      return byId;
    }
  }
  if (row) {
    const matched = patients.find(
      (p) => str(p.name) === str(row.patientName) && str(p.bedNo) === str(row.bedNo),
    );
    if (matched) {
      return matched;
    }
  }
  return patients[0];
}

function isInfusionMethodCode(methodCode?: null | string): boolean {
  const normalized = normalizeMedicationMethodCode(methodCode);
  if (!normalized) {
    return false;
  }
  return INFUSION_METHOD_CODES.has(normalized) || INFUSION_METHOD_CODES.has(str(methodCode));
}

function isOralMethodCode(methodCode?: null | string): boolean {
  const normalized = normalizeMedicationMethodCode(methodCode);
  if (!normalized) {
    return false;
  }
  return ORAL_METHOD_CODES.has(normalized) || ORAL_METHOD_CODES.has(str(methodCode));
}

/** 医嘱执行行是否输液药品（优先 infusionFlag，其次 methodCode；字段未回传时不误杀） */
function isInfusionExecuteOrder(order: {
  infusionFlag?: boolean | null;
  methodCode?: null | string;
  orderType?: null | string;
}): boolean {
  if (str(order.orderType).toUpperCase() !== 'MED_REQUEST') {
    return false;
  }
  if (order.infusionFlag === true) {
    return true;
  }
  const methodCode = str(order.methodCode);
  if (methodCode) {
    // 静滴 405/IVD，或静脉注射 404（执行页右键打印输液贴口径）
    const normalized = normalizeMedicationMethodCode(methodCode);
    return (
      isInfusionMethodCode(methodCode) ||
      normalized === METHOD_CODE_IV_INJECTION ||
      methodCode === METHOD_CODE_IV_INJECTION
    );
  }
  if (order.infusionFlag === false) {
    return false;
  }
  // 旧后端未回传途径字段时先纳入，避免空列表
  return true;
}

/** 计划点是否落在查询日（锚定 planTime 日期） */
function isSlotOnQueryDate(slot: { planTime?: string }, queryDate: string): boolean {
  const planTime = str(slot.planTime);
  if (!planTime || !queryDate) {
    return false;
  }
  if (planTime.startsWith(queryDate)) {
    return true;
  }
  const parsed = dayjs(planTime);
  if (!parsed.isValid()) {
    return false;
  }
  return parsed.format('YYYY-MM-DD') === queryDate;
}

/** 槽位归属日：优先计划点，已执行可回落 recordedTime */
function resolveOralSlotDay(slot: { planTime?: string; recordedTime?: string }): string {
  const raw = str(slot.planTime) || str(slot.recordedTime);
  if (!raw) {
    return '';
  }
  if (/^\d{4}-\d{2}-\d{2}/.test(raw)) {
    return raw.slice(0, 10);
  }
  const parsed = dayjs(raw);
  if (!parsed.isValid()) {
    return '';
  }
  return parsed.format('YYYY-MM-DD');
}

/** 槽位是否落在查询闭区间（含起止日） */
function isOralSlotInQueryRange(
  slot: { planTime?: string; recordedTime?: string },
  dateFrom: string,
  dateTo: string,
): boolean {
  const day = resolveOralSlotDay(slot);
  if (!day || !dateFrom) {
    return false;
  }
  const to = dateTo || dateFrom;
  return day >= dateFrom && day <= to;
}

/** 腕带床号展示（无「床」后缀时补上） */
function formatWristbandBedNo(bedNo?: string): string {
  const raw = str(bedNo);
  if (!raw) {
    return '';
  }
  if (raw.endsWith('床')) {
    return raw;
  }
  return `${raw}床`;
}

/** 输液巡视卡：患者头信息 + 查询日输液组药明细（含待执行/已执行；滴速/时间/签名留空） */
export async function buildPatrolCardPrintSource(
  patients: NurseStationModulePatient[],
  row: PrintPendingRow,
  executionDeadline: string,
): Promise<InpatientPatrolCardPrintSource> {
  const patient = resolvePatient(patients, row);
  const encounterId = preserveApiIdForRequest(row.encounterId || patient?.id);

  const patrolRows: Array<{
    groupNo: string;
    drugName: string;
    specText: string;
    doseText: string;
    dripRate: string;
    timeText: string;
    signText: string;
  }> = [];
  const groupKeyToNo = new Map<string, number>();
  let groupSeq = 0;

  if (!encounterId) {
    throw new Error('缺少就诊信息，无法打印输液巡视卡');
  }

  const date = executionDeadline
    ? dayjs(executionDeadline).format('YYYY-MM-DD')
    : dayjs().format('YYYY-MM-DD');
  const deadline = str(executionDeadline) || dayjs(date).endOf('day').format('YYYY-MM-DD HH:mm:ss');
  const listBody = {
    encounterIds: [encounterId],
    therapyType: 'all' as const,
    medicineCategoryTab: 'west' as const,
    executionDeadline: deadline,
  };
  const [pendingRes, executedRes] = await Promise.all([
    httpListNurseStationOrderExecuteForPrint({ ...listBody, executeTab: 'pending' }),
    httpListNurseStationOrderExecuteForPrint({ ...listBody, executeTab: 'executed' }),
  ]);

  type PatrolOrder = {
    adviceDisplayName?: string;
    dosageDisplay?: string;
    groupId?: string;
    orderId?: string;
    orderType?: null | string;
    sortNumber?: number;
    specificationText?: string;
    speedCode?: string;
    speedDisplay?: string;
    tcmFlag?: boolean;
  };

  /** 同就诊下全部药嘱（待执行+已执行），供成组补全 */
  const allEncounterOrders: PatrolOrder[] = [];
  for (const group of [...(pendingRes.groups ?? []), ...(executedRes.groups ?? [])]) {
    if (str(group.encounterId) !== encounterId) {
      continue;
    }
    for (const order of group.orders ?? []) {
      allEncounterOrders.push(order);
    }
  }

  /** 按 orderId 去重：查询日有槽位的输液药 */
  const byOrderId = new Map<string, PatrolOrder>();

  function collectDaySlotInfusion(
    groups: { encounterId?: string; orders?: NurseStationOrderExecutePatientGroupDto['orders'] }[],
    tab: 'executed' | 'pending',
  ) {
    for (const group of groups) {
      if (str(group.encounterId) !== encounterId) {
        continue;
      }
      for (const order of group.orders ?? []) {
        if (!isInfusionExecuteOrder(order)) {
          continue;
        }
        const slots = tab === 'pending' ? (order.pendingSlots ?? []) : (order.executedSlots ?? []);
        if (!slots.some((slot) => isSlotOnQueryDate(slot, date))) {
          continue;
        }
        const orderId = str(order.orderId);
        if (!orderId || byOrderId.has(orderId)) {
          continue;
        }
        byOrderId.set(orderId, order);
      }
    }
  }

  collectDaySlotInfusion(pendingRes.groups ?? [], 'pending');
  collectDaySlotInfusion(executedRes.groups ?? [], 'executed');

  // 成组补全：组内任一条有当日槽位 → 同组西药成员一并打出（避免溶媒因无槽位/infusionFlag 漏打）
  const activeGroupIds = new Set<string>();
  for (const order of byOrderId.values()) {
    const gid = str(order.groupId);
    if (gid) {
      activeGroupIds.add(gid);
    }
  }
  for (const order of allEncounterOrders) {
    const gid = str(order.groupId);
    if (!gid || !activeGroupIds.has(gid)) {
      continue;
    }
    if (str(order.orderType).toUpperCase() !== 'MED_REQUEST' || order.tcmFlag === true) {
      continue;
    }
    const orderId = str(order.orderId);
    if (!orderId || byOrderId.has(orderId)) {
      continue;
    }
    byOrderId.set(orderId, order);
  }

  const sortedOrders = [...byOrderId.values()].slice().sort((a, b) => {
    const groupA = str(a.groupId);
    const groupB = str(b.groupId);
    if (groupA !== groupB) {
      return groupA.localeCompare(groupB);
    }
    return (a.sortNumber ?? 0) - (b.sortNumber ?? 0);
  });

  /** 成组滴速：取组内首条非空 speedDisplay/speedCode（溶媒行常为空） */
  const groupDripRate = new Map<string, string>();
  for (const order of sortedOrders) {
    const key = str(order.groupId) || `r:${str(order.orderId)}`;
    const rate = str(order.speedDisplay) || str(order.speedCode);
    if (!rate || groupDripRate.has(key)) {
      continue;
    }
    groupDripRate.set(key, rate);
  }

  for (const order of sortedOrders) {
    const orderGroupKey = str(order.groupId) || `r:${str(order.orderId)}`;
    let groupNo = groupKeyToNo.get(orderGroupKey);
    if (groupNo === undefined) {
      groupSeq += 1;
      groupNo = groupSeq;
      groupKeyToNo.set(orderGroupKey, groupNo);
    }
    patrolRows.push({
      groupNo: String(groupNo),
      drugName: str(order.adviceDisplayName),
      specText: str(order.specificationText),
      doseText: str(order.dosageDisplay),
      dripRate: groupDripRate.get(orderGroupKey) || '',
      timeText: '',
      signText: '',
    });
  }

  // 无输液药品不得出纸
  if (groupSeq <= 0 || patrolRows.length <= 0) {
    throw new Error('当前患者没有可打印的输液药品');
  }

  return {
    hospitalName: resolvePrintHospitalName(),
    documentTitle: '输液巡视卡',
    wardName: str(patient?.wardName),
    bedNo: str(row.bedNo) || str(patient?.bedNo),
    patientName: str(row.patientName) || str(patient?.name),
    ageText: ageTextOf(patient),
    executeDateText: date,
    totalGroupsText: `共 ${groupSeq} 组`,
    familySignLabel: '患者家属签字：____________',
    patrolRows,
  };
}

/** 腕带：患者基本信息 */
export function buildWristbandPrintSource(
  patients: NurseStationModulePatient[],
  row: PrintPendingRow,
): InpatientWristbandPrintSource {
  const patient = resolvePatient(patients, row);
  return {
    patientName: str(row.patientName) || str(patient?.name),
    genderEnumText: str(patient?.gender),
    ageText: ageTextOf(patient),
    // 科室 = organizationName；队列偶发缺省时回落病区名（regionName），绝不用 wardName（病房）
    departmentName: str(patient?.organizationName) || printLocationText(patient?.regionName),
    // 病房 = 模块患者 wardName（houseName 映射）
    wardName: printLocationText(patient?.wardName),
    bedNo: formatWristbandBedNo(str(row.bedNo) || str(patient?.bedNo)),
    inpatientNo: str(patient?.encounterBusNo),
    allergyText: '',
    diagnosisText: str(patient?.diagnosis),
  };
}

/** 入院日期展示：YYYY-MM-DD（缺省空） */
function formatAdmissionDateText(raw?: string): string {
  const value = str(raw);
  if (!value) {
    return '';
  }
  const parsed = dayjs(value);
  if (!parsed.isValid()) {
    return value.length >= 10 ? value.slice(0, 10) : value;
  }
  return parsed.format('YYYY-MM-DD');
}

/** 床头卡：姓名/性别/住院号/年龄/入院日期/主治医生/医保类别 */
export function buildBedCardPrintSource(
  patients: NurseStationModulePatient[],
  row: PrintPendingRow,
): InpatientBedCardPrintSource {
  const patient = resolvePatient(patients, row);
  return {
    patientName: str(row.patientName) || str(patient?.name),
    genderEnumText: str(patient?.gender),
    inpatientNo: str(patient?.encounterBusNo),
    ageText: ageTextOf(patient),
    admissionDateText: formatAdmissionDateText(patient?.admissionAt || patient?.inWardAt),
    attendingDoctorName: str(patient?.attendingDoctorName),
    insuranceCategoryText: str(patient?.feeType),
  };
}

/** 口服药贴：按待打行的 encounter + orderId 取查询区间内口服药（与待打列表同源） */
export async function buildOralMedLabelPrintSource(
  patients: NurseStationModulePatient[],
  row: PrintPendingRow,
  dateFrom: string,
  dateTo?: string,
): Promise<InpatientOralMedLabelPrintSource> {
  const patient = resolvePatient(patients, row);
  const encounterId = preserveApiIdForRequest(row.encounterId || patient?.id);
  if (!encounterId) {
    throw new Error('缺少就诊信息');
  }

  const fromDay = str(dateFrom) || str(dateTo) || dayjs().format('YYYY-MM-DD');
  const toDay = str(dateTo) || fromDay;
  const rangeFrom = fromDay <= toDay ? fromDay : toDay;
  const rangeTo = fromDay <= toDay ? toDay : fromDay;
  const executionDeadline = dayjs(rangeTo).endOf('day').format('YYYY-MM-DD HH:mm:ss');
  const listBody = {
    encounterIds: [encounterId],
    therapyType: 'all' as const,
    executionDeadline,
    planTimeFrom: `${rangeFrom} 00:00:00`,
  };
  const [westPending, westExecuted, tcmPending, tcmExecuted] = await Promise.all([
    httpListNurseStationOrderExecuteForPrint({
      ...listBody,
      medicineCategoryTab: 'west',
      executeTab: 'pending',
    }),
    httpListNurseStationOrderExecuteForPrint({
      ...listBody,
      medicineCategoryTab: 'west',
      executeTab: 'executed',
    }),
    httpListNurseStationOrderExecuteForPrint({
      ...listBody,
      medicineCategoryTab: 'tcm',
      executeTab: 'pending',
    }),
    httpListNurseStationOrderExecuteForPrint({
      ...listBody,
      medicineCategoryTab: 'tcm',
      executeTab: 'executed',
    }),
  ]);

  const targetOrderId = str(row.orderId);
  let matched:
    | {
        adviceDisplayName?: string;
        specificationText?: string;
        dosageDisplay?: string;
      }
    | undefined;

  /** 在某一 Tab 中匹配目标口服药嘱（有 orderId 时不强制槽位，避免打印丢行） */
  function findOralOrder(
    groups: { encounterId?: string; orders?: NurseStationOrderExecutePatientGroupDto['orders'] }[],
    tab: 'executed' | 'pending',
  ) {
    if (matched) {
      return;
    }
    for (const group of groups) {
      if (str(group.encounterId) !== encounterId) {
        continue;
      }
      for (const order of group.orders ?? []) {
        if (!isOralMethodCode(order.methodCode)) {
          continue;
        }
        if (str(order.orderType).toUpperCase() !== 'MED_REQUEST') {
          continue;
        }
        if (targetOrderId && str(order.orderId) !== targetOrderId) {
          continue;
        }
        if (!targetOrderId) {
          const slots =
            tab === 'pending' ? (order.pendingSlots ?? []) : (order.executedSlots ?? []);
          if (!slots.some((slot) => isOralSlotInQueryRange(slot, rangeFrom, rangeTo))) {
            continue;
          }
        }
        matched = order;
        return;
      }
    }
  }

  findOralOrder(westPending.groups ?? [], 'pending');
  findOralOrder(westExecuted.groups ?? [], 'executed');
  findOralOrder(tcmPending.groups ?? [], 'pending');
  findOralOrder(tcmExecuted.groups ?? [], 'executed');

  if (!matched) {
    throw new Error('未找到可打印的口服药');
  }

  return {
    bedNo: str(row.bedNo) || str(patient?.bedNo),
    patientName: str(row.patientName) || str(patient?.name),
    ageText: ageTextOf(patient),
    drugName: str(matched.adviceDisplayName),
    specText: str(matched.specificationText),
    doseText: str(matched.dosageDisplay),
  };
}

/** 汇总数量展示：空值不输出破折号 */
function feeSummaryQuantityText(line: NurseStationCostSummaryLineDto): string {
  const text = formatCostSummaryQuantity(line).trim();
  if (!text || text === '—') {
    return '';
  }
  return text;
}

/** 冲销成对剔除后仍可能残留负净额：费用清单不打印该行 */
function isPositiveFeeAmount(value: null | number | string | undefined): boolean {
  const amount = Number(value);
  return Number.isFinite(amount) && amount > 0;
}

/**
 * 住院天数：即进不计出（入院日计、出院日不计）；在院未出院含当日。
 *
 * @param admissionAt 入院时间
 * @param dischargeAt 出院时间；空则按在院含当日
 */
function calcInpatientDaysText(admissionAt?: string, dischargeAt?: string): string {
  const admissionRaw = str(admissionAt);
  if (!admissionRaw) {
    return '';
  }
  const admission = dayjs(admissionRaw);
  if (!admission.isValid()) {
    return '';
  }
  const dischargeRaw = str(dischargeAt);
  let hasDischarge = false;
  let end = dayjs();
  if (dischargeRaw && dayjs(dischargeRaw).isValid()) {
    hasDischarge = true;
    end = dayjs(dischargeRaw);
  }
  const diff = end.startOf('day').diff(admission.startOf('day'), 'day');
  // 已出院：即进不计出（11→13=2）
  if (hasDischarge) {
    return String(Math.max(1, diff));
  }
  // 在院：含当日
  return String(Math.max(1, diff + 1));
}

/**
 * 汇总行分组键与展示名：优先产品实际分类（中成药/西药/检验等），
 * 无分类时回落费用大类（药品/诊疗/耗材）。
 */
function resolveFeeListGroupMeta(line: NurseStationCostSummaryLineDto): {
  groupKey: string;
  display: string;
} {
  const categoryCode = str(line.categoryCode);
  const categoryDisplay = str(line.categoryDisplay);
  if (categoryCode || categoryDisplay) {
    return {
      groupKey: `CAT:${categoryCode || categoryDisplay}`,
      display: categoryDisplay || categoryCode,
    };
  }
  const blockCode = str(line.expenseBlockCode) || 'OTHER';
  const blockDisplay = str(line.expenseBlockDisplay) || blockCode;
  return {
    groupKey: `BLOCK:${blockCode}`,
    display: blockDisplay,
  };
}

/** 费用清单：SUMMARY 同品合并；按实际分类分组；成对剔除冲销留痕后再排除非正净额 */
export async function buildFeeListPrintSource(
  patients: NurseStationModulePatient[],
  dateFrom: string,
  dateTo: string,
  /** 与费用明细页筛选一致；默认待结算（打印中心入口） */
  statusEnum: NurseStationCostDetailStatusFilter = 'PENDING_SETTLEMENT',
): Promise<InpatientFeeListPrintSource> {
  const seedPatient = patients[0];
  if (!seedPatient) {
    throw new Error('请选择患者');
  }
  // 打印当下重拉患者单头（余额/床位等），避免左侧队列挂起导致单头金额不准
  const patient = await resolveFreshFeePrintPatient(seedPatient);
  const encounterId = preserveApiIdForRequest(patient.id);
  const patientId = preserveApiIdForRequest(patient.patientId ?? patient.id);
  if (!encounterId || !patientId) {
    throw new Error('患者就诊信息不完整');
  }

  const res = await httpQueryNurseStationInpatientCostDetail({
    encounterId,
    patientId,
    dateFrom,
    dateTo,
    presentationMode: 'SUMMARY',
    statusEnum,
    // 一进一退成对剔除：原正向行 + refund_id 冲销行都不进清单（冲销可在日期窗外）
    excludeOffsetPairs: true,
  });

  // 保持 SUMMARY 行顺序（后端已按费用大类排序），分类按首次出现入桶
  const groupOrder: string[] = [];
  const byGroup = new Map<
    string,
    { display: string; lines: NurseStationCostSummaryLineDto[]; amount: number }
  >();

  for (const line of res.summaryLines ?? []) {
    if (!isPositiveFeeAmount(line.lineTotalAmount)) {
      continue;
    }
    const { groupKey, display } = resolveFeeListGroupMeta(line);
    let bucket = byGroup.get(groupKey);
    if (!bucket) {
      bucket = { display, lines: [], amount: 0 };
      byGroup.set(groupKey, bucket);
      groupOrder.push(groupKey);
    }
    bucket.lines.push(line);
    bucket.amount = decimalAdd(bucket.amount, line.lineTotalAmount ?? 0);
  }

  const feeRows: InpatientFeeListFeeRow[] = [];
  for (const groupKey of groupOrder) {
    const bucket = byGroup.get(groupKey);
    if (!bucket || bucket.lines.length <= 0) {
      continue;
    }
    feeRows.push({
      rowType: 'category',
      categoryName: bucket.display,
      seqNo: '',
      itemName: '',
      specText: '',
      quantityUnitText: '',
      insuranceLevelText: '',
      unitPriceText: '',
      amountText: '',
    });
    let seq = 0;
    for (const line of bucket.lines) {
      seq += 1;
      feeRows.push({
        rowType: 'item',
        seqNo: String(seq),
        itemName: str(line.itemName),
        specText: str(line.itemSpec),
        quantityUnitText: feeSummaryQuantityText(line),
        insuranceLevelText: str(line.chrgitmLvText),
        unitPriceText: moneyText(line.unitPrice),
        amountText: moneyText(line.lineTotalAmount),
      });
    }
    feeRows.push({
      rowType: 'subtotal',
      categoryName: bucket.display,
      seqNo: '',
      itemName: '',
      specText: '',
      quantityUnitText: '',
      insuranceLevelText: '',
      unitPriceText: '',
      amountText: moneyText(bucket.amount),
    });
  }

  const grand = moneyText(res.totals?.grandTotal);
  const prepaid = moneyText(patient.accountBalance);
  return {
    hospitalName: resolvePrintHospitalName(),
    documentTitle: '住院费用清单',
    inpatientNo: str(patient.encounterBusNo),
    patientName: str(patient.name),
    genderEnumText: str(patient.gender),
    ageText: ageTextOf(patient),
    // 模块 regionName=病区；wardName 历史字段实为病房（house）
    wardName: str(patient.regionName),
    roomNo: str(patient.wardName),
    bedNo: str(patient.bedNo),
    departmentName: str(patient.organizationName),
    admitAtText: str(patient.admissionAt),
    dischargeAtText: str(patient.dischargeAt),
    inpatientDaysText: calcInpatientDaysText(patient.admissionAt, patient.dischargeAt),
    admissionDiagnosis: str(patient.diagnosis),
    prepaidAmountText: prepaid,
    totalAmountText: grand,
    balanceAmountText: moneyText(patient.availableBalance ?? patient.accountBalance),
    feeRows,
    printedAtText: printedAtNow(),
  };
}

/** 明细数量展示：空值不输出破折号 */
function feeDetailQuantityText(line: NurseStationCostDetailLineDto): string {
  const text = formatCostDetailQuantity(line).trim();
  if (!text || text === '—') {
    return '';
  }
  return text.replace(/\s+/g, '');
}

/**
 * 明细行分组键与展示名：优先产品实际分类，无分类时回落费用大类块。
 */
function resolveFeeDetailGroupMeta(line: NurseStationCostDetailLineDto): {
  groupKey: string;
  display: string;
} {
  const categoryCode = str(line.categoryCode);
  const categoryDisplay = str(line.categoryDisplay);
  if (categoryCode || categoryDisplay) {
    return {
      groupKey: `CAT:${categoryCode || categoryDisplay}`,
      display: categoryDisplay || categoryCode,
    };
  }
  const blockCode = str(line.expenseBlockCode) || 'OTHER';
  let blockDisplay = blockCode;
  if (blockCode === 'MEDICATION') {
    blockDisplay = '药品';
  } else if (blockCode === 'SERVICE') {
    blockDisplay = '诊疗';
  } else if (blockCode === 'DEVICE') {
    blockDisplay = '耗材';
  }
  return {
    groupKey: `BLOCK:${blockCode}`,
    display: blockDisplay,
  };
}

/** 业务时间排序键（升序流水；无效时间排后） */
function feeDetailTimeSortKey(line: NurseStationCostDetailLineDto): number {
  const raw = str(line.displayDateTime) || str(line.businessDateTime);
  if (!raw) {
    return Number.MAX_SAFE_INTEGER;
  }
  const ts = dayjs(raw).valueOf();
  if (Number.isNaN(ts)) {
    return Number.MAX_SAFE_INTEGER;
  }
  return ts;
}

/**
 * 费用明细单：复用费用明细 DETAIL 流水；按实际分类分组；无分类合计。
 * 流水含正负金额（退费/冲销行一并打印），不做冲销成对剔除、不丢弃负净额。
 */
export async function buildFeeDetailPrintSource(
  patients: NurseStationModulePatient[],
  dateFrom: string,
  dateTo: string,
  /** 与费用明细页筛选一致；默认待结算（打印中心入口） */
  statusEnum: NurseStationCostDetailStatusFilter = 'PENDING_SETTLEMENT',
): Promise<InpatientFeeDetailPrintSource> {
  const seedPatient = patients[0];
  if (!seedPatient) {
    throw new Error('请选择患者');
  }
  // 打印当下重拉患者单头（余额/床位等），避免左侧队列挂起导致单头金额不准
  const patient = await resolveFreshFeePrintPatient(seedPatient);
  const encounterId = preserveApiIdForRequest(patient.id);
  const patientId = preserveApiIdForRequest(patient.patientId ?? patient.id);
  if (!encounterId || !patientId) {
    throw new Error('患者就诊信息不完整');
  }

  const res = await httpQueryNurseStationInpatientCostDetail({
    encounterId,
    patientId,
    dateFrom,
    dateTo,
    presentationMode: 'DETAIL',
    statusEnum,
  });

  const groupOrder: string[] = [];
  const byGroup = new Map<string, { display: string; lines: NurseStationCostDetailLineDto[] }>();

  for (const line of res.detailLines ?? []) {
    const { groupKey, display } = resolveFeeDetailGroupMeta(line);
    let bucket = byGroup.get(groupKey);
    if (!bucket) {
      bucket = { display, lines: [] };
      byGroup.set(groupKey, bucket);
      groupOrder.push(groupKey);
    }
    bucket.lines.push(line);
  }

  const feeRows: InpatientFeeListFeeRow[] = [];
  for (const groupKey of groupOrder) {
    const bucket = byGroup.get(groupKey);
    if (!bucket || bucket.lines.length <= 0) {
      continue;
    }
    bucket.lines.sort((a, b) => feeDetailTimeSortKey(a) - feeDetailTimeSortKey(b));
    feeRows.push({
      rowType: 'category',
      categoryName: bucket.display,
      seqNo: '',
      itemName: '',
      specText: '',
      quantityUnitText: '',
      insuranceLevelText: '',
      unitPriceText: '',
      amountText: '',
    });
    let seq = 0;
    for (const line of bucket.lines) {
      seq += 1;
      feeRows.push({
        rowType: 'item',
        seqNo: String(seq),
        itemName: str(line.itemName),
        specText: str(line.itemSpec),
        quantityUnitText: feeDetailQuantityText(line),
        insuranceLevelText: str(line.chrgitmLvText),
        unitPriceText: moneyText(line.unitPrice),
        amountText: moneyText(line.totalPrice),
      });
    }
  }

  const grand = moneyText(res.totals?.grandTotal);
  const prepaid = moneyText(patient.accountBalance);
  return {
    hospitalName: resolvePrintHospitalName(),
    documentTitle: '住院费用明细单',
    inpatientNo: str(patient.encounterBusNo),
    patientName: str(patient.name),
    genderEnumText: str(patient.gender),
    ageText: ageTextOf(patient),
    wardName: str(patient.regionName),
    roomNo: str(patient.wardName),
    bedNo: str(patient.bedNo),
    departmentName: str(patient.organizationName),
    admitAtText: str(patient.admissionAt),
    dischargeAtText: str(patient.dischargeAt),
    inpatientDaysText: calcInpatientDaysText(patient.admissionAt, patient.dischargeAt),
    admissionDiagnosis: str(patient.diagnosis),
    prepaidAmountText: prepaid,
    totalAmountText: grand,
    balanceAmountText: moneyText(patient.availableBalance ?? patient.accountBalance),
    feeRows,
    printedAtText: printedAtNow(),
  };
}

/** 长/临打印内容拼装所需字段（校对列表 / 医生站归档） */
interface LongTempOrderContentSource {
  adviceDisplayName?: string;
  specificationText?: string;
  packageSplitRatioText?: null | string;
  dosageDisplay?: string;
  usageDisplay?: string;
  frequencyDisplay?: string;
  categoryEnum?: string;
  orderKindDisplay?: string;
  orderType?: string;
  tcmFlag?: boolean;
}

/** 长/临打印行源（含开停嘱痕迹字段） */
interface LongTempOrderPrintLineSource extends LongTempOrderContentSource {
  listRowKey?: string;
  requestId?: string;
  reqOpenedTime?: string;
  stopTime?: string;
  requesterName?: string;
  performerCheckName?: string;
  stopperName?: string;
  stopCheckerName?: string;
}

/** 病案归档打印数据源：护士已校对 / 医生站已开立 */
export type OrderPrintDataSource = 'nurse_audited' | 'doctor_advice';

/**
 * 打印用单次用量：保留「一次几片」，去掉「每日几片」日总量。
 * 频次「每日几次」走 frequencyDisplay，不在此拼。
 */
function buildPrintPerDoseText(order: LongTempOrderContentSource): string {
  let dose = str(order.dosageDisplay).replace(/\/次$/u, '');
  // 「一次一片，每日三片」→「一次一片」
  dose = dose.replace(/，\s*每日[^\s，,]+/gu, '');
  // 整段只剩「每日三片」则丢弃
  if (!dose || /^每日/.test(dose)) {
    return '';
  }
  // 「1次」「2次」易与频次重复，交由频次列表达
  if (/^\d+\s*次$/u.test(dose)) {
    return '';
  }
  if (dose.startsWith('一次')) {
    return dose;
  }
  return dose;
}

/** 名称里是否已带频次（日1次 / 每日一次 等） */
function nameAlreadyHasFrequency(name: string, freq: string): boolean {
  if (!freq) {
    return false;
  }
  if (name.includes(freq)) {
    return true;
  }
  if (
    /每日\s*一\s*次|每天\s*一\s*次|qd/iu.test(freq) &&
    /日\s*1\s*次|每日\s*一\s*次|每天\s*一\s*次/u.test(name)
  ) {
    return true;
  }
  if (
    /每日\s*两\s*次|每日\s*二\s*次|每天\s*两\s*次|bid/iu.test(freq) &&
    /日\s*2\s*次|每日\s*[两二]\s*次/u.test(name)
  ) {
    return true;
  }
  if (/每日\s*三\s*次|tid/iu.test(freq) && /日\s*3\s*次|每日\s*三\s*次/u.test(name)) {
    return true;
  }
  const m = freq.match(/(\d+)\s*次/u);
  if (m && new RegExp(`日\\s*${m[1]}\\s*次`, 'u').test(name)) {
    return true;
  }
  return false;
}

/** 打印「类型」列：优先后端 orderKindDisplay，回落 categoryEnum / orderType（嘱托=52） */
function resolvePrintOrderKindText(order: LongTempOrderContentSource): string {
  const fromApi = str(order.orderKindDisplay);
  if (fromApi) {
    return fromApi;
  }
  const category = str(order.categoryEnum);
  if (category === SERVICE_CATEGORY_DISCHARGE) {
    return '出院';
  }
  if (category === SERVICE_CATEGORY_TRANSFER) {
    return '转科';
  }
  if (category === SERVICE_CATEGORY_NURSING) {
    return '护理';
  }
  if (category === SERVICE_CATEGORY_ENTRUST) {
    return '嘱托';
  }
  if (category === SERVICE_CATEGORY_PROOF) {
    return '检验';
  }
  if (category === SERVICE_CATEGORY_TEST) {
    return '检查';
  }
  if (order.tcmFlag === true) {
    return '中药';
  }
  const orderType = str(order.orderType).toUpperCase();
  if (orderType === 'MED_REQUEST') {
    return '药品';
  }
  if (orderType === 'SERVICE_REQUEST') {
    return '诊疗';
  }
  return '—';
}

/** 是否护理级别医嘱（categoryEnum=26；BR-IP-ORD-026） */
function isNursingLevelOrder(order: Pick<LongTempOrderContentSource, 'categoryEnum'>): boolean {
  return str(order.categoryEnum) === SERVICE_CATEGORY_NURSING;
}

/**
 * 医嘱内容单元格：
 * - 嘱托（categoryEnum=52）/ 护理级别（26）：只打展示名，不拼用法频次
 * - 其它：第 1 行药品+规格；第 2 行用量+用法+频次
 */
function buildLongTempOrderContentLine(order: LongTempOrderContentSource): string {
  const name = str(order.adviceDisplayName);
  // 嘱托 / 护理级别：名称即全文，禁止再拼用量/用法/频次
  if (str(order.categoryEnum) === SERVICE_CATEGORY_ENTRUST || isNursingLevelOrder(order)) {
    return name;
  }

  const nameLine =
    order.tcmFlag === true
      ? name
      : buildMedicationContentTitleWithPackageSplit(
          name,
          str(order.specificationText),
          order.packageSplitRatioText,
        ) || name;

  if (!nameLine) {
    return '';
  }

  const dose = buildPrintPerDoseText(order);
  const usage = str(order.usageDisplay);
  const freq = str(order.frequencyDisplay);
  const parts: string[] = [];
  if (dose && !nameLine.includes(dose)) {
    parts.push(dose);
  }
  if (usage && !nameLine.includes(usage)) {
    parts.push(usage);
  }
  if (freq && !nameAlreadyHasFrequency(nameLine, freq)) {
    parts.push(freq);
  }
  const usageLine = parts.join(' ');
  if (usageLine) {
    return `${nameLine}\n${usageLine}`;
  }
  return nameLine;
}

/** 已校对 / 医生站行 → 打印明细行 */
function mapOrderLineToPrintRow(order: LongTempOrderPrintLineSource): InpatientTempOrderRow {
  const opened = str(order.reqOpenedTime);
  const stopRaw = str(order.stopTime);
  return {
    startAtText: opened && dayjs(opened).isValid() ? dayjs(opened).format('MM-DD HH:mm') : '',
    orderKindText: resolvePrintOrderKindText(order),
    orderContent: buildLongTempOrderContentLine(order),
    orderDoctorName: str(order.requesterName),
    startCheckerName: str(order.performerCheckName),
    stopTimeText: stopRaw && dayjs(stopRaw).isValid() ? dayjs(stopRaw).format('MM-DD HH:mm') : '',
    stopDoctorName: str(order.stopperName),
    // 停嘱校对护士：取 stopCheckerName（与开嘱校对 performerCheckName 并列）
    stopCheckerName: str(order.stopCheckerName),
  };
}

/** 校对列表行 → 统一打印行源 */
function toPrintLineSourceFromAudit(
  order: NurseStationOrderAuditRowDto,
): LongTempOrderPrintLineSource {
  return {
    listRowKey: order.listRowKey,
    requestId: order.requestId,
    adviceDisplayName: order.adviceDisplayName,
    specificationText: order.specificationText,
    packageSplitRatioText: order.packageSplitRatioText,
    dosageDisplay: order.dosageDisplay,
    usageDisplay: order.usageDisplay,
    frequencyDisplay: order.frequencyDisplay,
    categoryEnum: order.categoryEnum,
    orderKindDisplay: order.orderKindDisplay,
    orderType: order.orderType,
    tcmFlag: order.tcmFlag,
    reqOpenedTime: order.reqOpenedTime,
    stopTime: order.stopTime,
    requesterName: order.requesterName,
    performerCheckName: order.performerCheckName,
    stopperName: order.stopperName,
    stopCheckerName: order.stopCheckerName,
  };
}

/**
 * 医生站产品表名 → 打印 orderType（与校对列表 MED_REQUEST / SERVICE_REQUEST 对齐）。
 *
 * @param productTableName 产品表名
 * @return 打印用 orderType
 */
function resolveDoctorAdviceOrderType(productTableName?: string): string {
  const table = str(productTableName).toLowerCase();
  if (table.includes('medication')) {
    return 'MED_REQUEST';
  }
  if (table.includes('service') || table.includes('activity') || table.includes('device')) {
    return 'SERVICE_REQUEST';
  }
  return '';
}

/** 长/临打印行稳定键（待打勾选、护士站打印中心与 listRowKey 同源） */
function resolveLongTempOrderPrintLineKey(order: LongTempOrderPrintLineSource): string {
  return str(order.listRowKey) || `${str(order.requestId)}-${str(order.orderType) || 'ORDER'}`;
}

/**
 * 病案医生站行与护士已校对行合并键（禁止用医生站 listRowKey：其为 requestId+productTableName，
 * 校对列表为 requestId+MED_REQUEST/SERVICE_REQUEST，直接比 listRowKey 永远对不上）。
 */
function resolveLongTempOrderPrintMergeKey(order: LongTempOrderPrintLineSource): string {
  const requestId = str(order.requestId);
  const orderType = str(order.orderType);
  if (requestId && orderType) {
    return `${requestId}-${orderType}`;
  }
  return resolveLongTempOrderPrintLineKey(order);
}

/** 已校对打印行建索引（合并键 + 校对 listRowKey 双登记） */
function indexAuditedOrdersForPrintMerge(
  orders: LongTempOrderPrintLineSource[],
): Map<string, LongTempOrderPrintLineSource> {
  const map = new Map<string, LongTempOrderPrintLineSource>();
  for (const order of orders) {
    const mergeKey = resolveLongTempOrderPrintMergeKey(order);
    if (mergeKey && !map.has(mergeKey)) {
      map.set(mergeKey, order);
    }
    const listKey = str(order.listRowKey);
    if (listKey && !map.has(listKey)) {
      map.set(listKey, order);
    }
  }
  return map;
}

/**
 * 病案归档取数：医生站已开立全量 + 已校对行同源（校对人/停嘱校对人及展示字段与护士站打印中心一致）。
 *
 * @param doctorOrders 医生站已开立（排除草稿/作废）
 * @param auditedOrders 护士已校对 Tab（含已停嘱校对）
 */
function mergeDoctorAdviceWithAuditedPrintLines(
  doctorOrders: LongTempOrderPrintLineSource[],
  auditedOrders: LongTempOrderPrintLineSource[],
): LongTempOrderPrintLineSource[] {
  const auditedByKey = indexAuditedOrdersForPrintMerge(auditedOrders);
  return doctorOrders.map((order) => {
    const mergeKey = resolveLongTempOrderPrintMergeKey(order);
    const listKey = str(order.listRowKey);
    const audited =
      (mergeKey ? auditedByKey.get(mergeKey) : undefined) ??
      (listKey ? auditedByKey.get(listKey) : undefined);
    if (!audited) {
      return order;
    }
    // 已校对行整行替换，保证纸面与护士站打印中心同源
    return audited;
  });
}

/** 医生站医嘱行 → 统一打印行源（未校对医嘱无校对人字段） */
function toPrintLineSourceFromDoctorAdvice(
  order: InpatientDoctorStationAdviceRowDto,
): LongTempOrderPrintLineSource {
  const orderType = resolveDoctorAdviceOrderType(order.productTableName);
  const categoryEnum = str(order.serviceCategoryEnum) || str(order.catalogCategoryCode);
  const catalogName = str(order.adviceDisplayName);
  const entrust = str(order.entrustContent);
  const summary = str(order.contentSummary);
  // 嘱托双锚：类目 52 或目录名「嘱托」（BR-IP-ORD-028⑥）
  const isEntrust = categoryEnum === SERVICE_CATEGORY_ENTRUST || catalogName === '嘱托';
  // 嘱托：内容列优先医生录入正文，禁止只显示目录名「嘱托」（BR-IP-ORD-028④）
  const adviceDisplayName = isEntrust
    ? entrust || summary || catalogName
    : catalogName || entrust || summary;
  return {
    listRowKey: order.listRowKey,
    requestId: order.requestId,
    adviceDisplayName,
    specificationText: order.specificationText,
    packageSplitRatioText: order.packageSplitRatioText,
    dosageDisplay: order.dosageDisplay,
    usageDisplay: order.methodName,
    frequencyDisplay: order.rateName,
    categoryEnum,
    // 嘱托类型列固定「嘱托」，禁止回落「诊疗」
    orderKindDisplay: isEntrust ? '嘱托' : order.catalogCategoryName,
    orderType,
    tcmFlag: order.tcmFlag === true,
    reqOpenedTime: order.reqOpenedTime,
    stopTime: order.stopTime,
    requesterName: order.authoredByName,
    stopperName: order.stopperName,
  };
}

/** 拉取已校对列表（含已停嘱校对行；西药+中药 Tab 合并，与校对页同口径） */
async function loadAuditedOrdersForPrint(
  encounterId: string,
  therapyType: 'long' | 'temp',
): Promise<LongTempOrderPrintLineSource[]> {
  const base = {
    encounterIds: [encounterId],
    auditTab: 'passed' as const,
    therapyType,
  };
  const [westRes, tcmRes] = await Promise.all([
    httpListNurseStationOrderAudit({ ...base, medicineCategoryTab: 'west' }),
    httpListNurseStationOrderAudit({ ...base, medicineCategoryTab: 'tcm' }),
  ]);
  const orders: LongTempOrderPrintLineSource[] = [];
  const seen = new Set<string>();
  for (const group of [...(westRes.groups ?? []), ...(tcmRes.groups ?? [])]) {
    for (const order of group.orders ?? []) {
      const mapped = toPrintLineSourceFromAudit(order);
      const key = resolveLongTempOrderPrintMergeKey(mapped);
      if (!key || seen.has(key)) {
        continue;
      }
      seen.add(key);
      orders.push(mapped);
    }
  }
  return orders;
}

/**
 * 病案归档：拉取医生站已开立医嘱（排除草稿/作废；含已停嘱，不依赖护士校对）。
 *
 * @param encounterId 就诊主键
 * @param patientId 患者主键
 * @param therapyType long / temp
 * @return 统一打印行源
 */
async function loadDoctorAdviceOrdersForPrint(
  encounterId: string,
  patientId: string,
  therapyType: 'long' | 'temp',
): Promise<LongTempOrderPrintLineSource[]> {
  const therapyTypeCode = therapyType === 'long' ? 'LONG_TERM' : 'TEMPORARY';
  const res = await httpListInpatientDoctorStationAdvice({
    encounterId,
    patientId,
    therapyTypeCode,
  });
  const rawRows = res.content ?? res.rows ?? [];
  const orders: LongTempOrderPrintLineSource[] = [];
  const seen = new Set<string>();
  // 仅保留已进入临床流程的医嘱；草稿/作废不进纸单
  for (const order of rawRows) {
    const status = str(order.statusEnum).toUpperCase();
    if (status === 'DRAFT' || status === 'CANCELLED' || status === 'ENTERED_IN_ERROR') {
      continue;
    }
    const mapped = toPrintLineSourceFromDoctorAdvice(order);
    const key =
      str(mapped.listRowKey) || `${str(mapped.requestId)}-${str(mapped.orderType) || 'ORDER'}`;
    if (!key || seen.has(key)) {
      continue;
    }
    seen.add(key);
    orders.push(mapped);
  }
  return orders;
}

/**
 * 长期/临时医嘱打印：默认取护士已校对列表；病案传 doctor_advice 取医生站已开立（含已停嘱）。
 * 有日期区间时按开嘱日过滤；内容含规格/用法用量/频次。
 * 长期单：护理级别（category=26）始终进明细；勾选/日期收窄不丢；无校对行时用患者 nurseLevel 兜底。
 *
 * @param orderOpenedTimeAsc 病案首页侧栏传 true：明细按开立时间正序；护士站打印中心不传保持原序
 * @param orderPrintDataSource nurse_audited=护士站打印中心；doctor_advice=病案归档（医生站全量 + 已校对字段合并）
 */
export async function buildOrderExecutePrintSource(
  patients: NurseStationModulePatient[],
  selectedRows: PrintPendingRow[],
  executionDeadline: string,
  therapyType: 'long' | 'temp',
  dateFrom?: string,
  dateTo?: string,
  orderOpenedTimeAsc = false,
  orderPrintDataSource: OrderPrintDataSource = 'nurse_audited',
): Promise<InpatientTempOrderPrintSource> {
  const patient = patients[0];
  if (!patient) {
    throw new Error('请选择患者');
  }
  const encounterId = preserveApiIdForRequest(patient.id);
  if (!encounterId) {
    throw new Error('患者就诊信息不完整');
  }

  let sourceOrders: LongTempOrderPrintLineSource[];
  // 病案归档：医生站已开立全量 + 已校对行合并（校对人等同护士站打印中心）
  if (orderPrintDataSource === 'doctor_advice') {
    const patientId = preserveApiIdForRequest(patient.patientId);
    if (!patientId) {
      throw new Error('患者主键缺失，无法打印医嘱');
    }
    const [doctorOrders, auditedOrders] = await Promise.all([
      loadDoctorAdviceOrdersForPrint(encounterId, patientId, therapyType),
      loadAuditedOrdersForPrint(encounterId, therapyType),
    ]);
    sourceOrders = mergeDoctorAdviceWithAuditedPrintLines(doctorOrders, auditedOrders);
  } else {
    sourceOrders = await loadAuditedOrdersForPrint(encounterId, therapyType);
  }
  // 病案首页：按原始开立时间正序（禁止用 MM-DD 展示文案排序，避免跨年错序）
  let orderedSourceOrders = sourceOrders;
  if (orderOpenedTimeAsc && sourceOrders.length > 1) {
    orderedSourceOrders = [...sourceOrders].sort((left, right) => {
      const leftOpened = str(left.reqOpenedTime);
      const rightOpened = str(right.reqOpenedTime);
      if (!leftOpened && !rightOpened) {
        return 0;
      }
      if (!leftOpened) {
        return -1;
      }
      if (!rightOpened) {
        return 1;
      }
      return leftOpened.localeCompare(rightOpened);
    });
  }

  const selectedKeys = new Set(
    selectedRows.map((row) => str(row.listRowKey) || str(row.id)).filter(Boolean),
  );
  const fromDay = str(dateFrom);
  const toDay =
    str(dateTo) || (executionDeadline ? dayjs(executionDeadline).format('YYYY-MM-DD') : '');
  const orderRows: InpatientTempOrderRow[] = [];
  let hasNursingLevelRow = false;
  for (const order of orderedSourceOrders) {
    const nursingLevel = therapyType === 'long' && isNursingLevelOrder(order);
    const key = str(order.listRowKey) || `${str(order.requestId)}-${str(order.orderType)}`;
    // 长期护理级别：不受待打勾选限制，保证纸单明细能看到
    if (!nursingLevel && selectedKeys.size > 0 && !selectedKeys.has(key)) {
      continue;
    }
    const opened = str(order.reqOpenedTime);
    // 有日期区间时按开嘱日纳入；长期护理级别不按区间裁掉
    if (!nursingLevel && fromDay && toDay && opened && dayjs(opened).isValid()) {
      const day = dayjs(opened).format('YYYY-MM-DD');
      if (day < fromDay || day > toDay) {
        continue;
      }
    }
    const row = mapOrderLineToPrintRow(order);
    if (nursingLevel) {
      // 无活动名时回落患者队列护理级别展示名
      if (!row.orderContent) {
        row.orderContent = str(patient.nurseLevel);
      }
      if (!row.orderContent) {
        continue;
      }
      hasNursingLevelRow = true;
    }
    orderRows.push(row);
  }

  // 已校对列表无护理级别行时，用患者卡片当前护理级别补一行（仅长期单）
  const nurseLevelFallback = str(patient.nurseLevel);
  if (therapyType === 'long' && !hasNursingLevelRow && nurseLevelFallback) {
    orderRows.unshift({
      startAtText: '',
      orderKindText: '护理',
      orderContent: nurseLevelFallback,
      orderDoctorName: '',
      startCheckerName: '',
      stopTimeText: '',
      stopDoctorName: '',
      stopCheckerName: '',
    });
  }

  return {
    hospitalName: resolvePrintHospitalName(),
    documentTitle: therapyType === 'long' ? '长期医嘱' : '临时医嘱',
    inpatientNo: str(patient.encounterBusNo),
    patientName: str(patient.name),
    genderEnumText: str(patient.gender),
    ageText: ageTextOf(patient),
    // 与费用清单同口径：organizationName=科室；regionName=病区；wardName 历史字段=病房
    departmentName: str(patient.organizationName),
    wardName: printLocationText(patient.regionName),
    roomNo: printLocationText(patient.wardName),
    bedNo: str(patient.bedNo),
    orderRows,
    doctorSignLabel: '医生：',
    nurseSignLabel: '护士：',
    printedAtText: printedAtNow(),
  };
}
/** 临时医嘱 */
export async function buildTempOrderPrintSource(
  patients: NurseStationModulePatient[],
  selectedRows: PrintPendingRow[],
  executionDeadline: string,
  dateFrom?: string,
  dateTo?: string,
  orderOpenedTimeAsc = false,
  orderPrintDataSource: OrderPrintDataSource = 'nurse_audited',
): Promise<InpatientTempOrderPrintSource> {
  return buildOrderExecutePrintSource(
    patients,
    selectedRows,
    executionDeadline,
    'temp',
    dateFrom,
    dateTo,
    orderOpenedTimeAsc,
    orderPrintDataSource,
  );
}

/** 长期医嘱（模板：ip-longOrder-print） */
export async function buildLongOrderPrintSource(
  patients: NurseStationModulePatient[],
  selectedRows: PrintPendingRow[],
  executionDeadline: string,
  dateFrom?: string,
  dateTo?: string,
  orderOpenedTimeAsc = false,
  orderPrintDataSource: OrderPrintDataSource = 'nurse_audited',
): Promise<InpatientTempOrderPrintSource> {
  return buildOrderExecutePrintSource(
    patients,
    selectedRows,
    executionDeadline,
    'long',
    dateFrom,
    dateTo,
    orderOpenedTimeAsc,
    orderPrintDataSource,
  );
}

/** 护理记录日期时间 → 模板列「MM-DD\\nHH:mm」 */
function formatNursingRecordDatetime(
  recordDate?: null | string,
  recordTime?: null | string,
): string {
  const date = str(recordDate);
  const time = str(recordTime).slice(0, 5);
  const md = date.length >= 10 ? date.slice(5, 10) : date;
  if (!md && !time) {
    return '';
  }
  if (!time) {
    return md;
  }
  if (!md) {
    return time;
  }
  return `${md}\n${time}`;
}

/** 护理记录单 PrintSource */
export async function buildNursingRecordPrintSource(
  patients: NurseStationModulePatient[],
  dateFrom: string,
  dateTo: string,
): Promise<InpatientNursingRecordPrintSource> {
  const patient = patients[0];
  if (!patient) {
    throw new Error('请选择患者');
  }
  const encounterId = preserveApiIdForRequest(patient.id);
  const patientId = preserveApiIdForRequest(patient.patientId ?? patient.id);
  if (!encounterId || !patientId) {
    throw new Error('患者就诊信息不完整');
  }

  const res = await httpListNurseStationNursingRecords({
    encounterId,
    patientId,
    // 与列表页一致：传 yyyy-MM-dd（后端 LocalDate），勿带时分秒
    recordedAtFrom: dateFrom,
    recordedAtTo: dateTo,
    sortAsc: true,
  });

  const header = res.paperHeader;
  const recordRows: InpatientNursingRecordRow[] = (res.rows ?? []).map((row) => ({
    recordDatetime: formatNursingRecordDatetime(row.recordDate, row.recordTime),
    temperature: str(row.temperature),
    pulse: str(row.pulse),
    heartRate: str(row.heartRate),
    respiratory: str(row.respiratory),
    bloodPressure: str(row.bloodPressure),
    consciousness: str(row.consciousness),
    pupilLeft: str(row.pupilLeft),
    pupilRight: str(row.pupilRight),
    intakeName: str(row.intakeName) || str(row.intakeRoute),
    intakeAmount: str(row.intakeAmount),
    outputName: str(row.outputName),
    outputAmount: str(row.outputAmount),
    outputColorCharacter: str(row.outputColorCharacter),
    conditionMeasure: str(row.conditionMeasure),
    nurseSignature: str(row.nurseSignature),
  }));

  const hospitalName = resolvePrintHospitalName();
  const fromMd = dayjs(dateFrom).format('MM-DD');
  const toMd = dayjs(dateTo).format('MM-DD');
  return {
    weekRangeLabel: `${fromMd} ~ ${toMd}`,
    documentTitle: `${hospitalName} 护理记录单`,
    patientName: str(header?.name) || str(patient.name),
    genderEnumText: str(header?.gender) || str(patient.gender),
    ageText: str(header?.age) || ageTextOf(patient),
    wardName: str(header?.wardName) || str(patient.wardName),
    bedNo: str(header?.bedNo) || str(patient.bedNo),
    inpatientNo: str(header?.inpatientNo) || str(patient.encounterBusNo),
    admitAtText: str(header?.admitAt) || str(patient.admissionAt),
    dischargeAtText: str(header?.dischargeAt) || str(patient.dischargeAt),
    recordRows,
  };
}

/** 汇总领药单（getMedicationSummarySlipDetail） */
export async function buildDispenseSummaryPrintSource(
  summaryNo: string,
): Promise<InpatientDispenseSummaryPrintSource> {
  const res = await httpGetMedicationSummarySlipDetail({ summaryNo });
  const header = res.header;
  const lines = res.lines ?? [];
  let total = 0;
  const drugRows = lines.map((line, index) => {
    total = decimalAdd(total, line.totalPrice ?? 0);
    const qty =
      str(line.displayQuantityText) ||
      `${str(line.itemQuantity)}${str(line.unitCodeText) || str(line.unitCode)}`;
    return {
      seqNo: String(index + 1),
      drugName: str(line.medicationName),
      specText: str(line.specificationText),
      unitPriceText: moneyText(line.unitPrice),
      quantityUnitText: qty,
    };
  });
  return {
    hospitalName: resolvePrintHospitalName(),
    documentTitle: '汇总领药单',
    docNo: str(res.summaryNo) || summaryNo,
    // 模板字段 wardName 展示「药房」← purposeLocationName
    wardName: str(header?.purposeLocationName),
    applicantName: str(header?.applicantName),
    dispenseAtText: str(header?.dispenseTime) || str(header?.applyTime),
    generatedAtText: str(header?.applyTime),
    orderCountText: String(lines.length),
    drugCountText: String(lines.length),
    totalAmountText: moneyText(total),
    drugRows,
    // 模板字段 dispenserName 展示「领药人」← receiverName
    dispenserName: str(header?.receiverName),
    receiverSignLabel: '签收：',
    printedAtText: printedAtNow(),
  };
}

/**
 * 医嘱执行摆药单：一张连续多人（当日已执行全部药品，含口服）。
 * 每名患者一块：患者信息（表外）→ 药品表；溢出分页且每页有医院头。
 */
export async function buildOrderDispenseSheetPrintSource(
  patients: NurseStationModulePatient[],
  selectedRows: PrintPendingRow[],
  queryDate: string,
): Promise<InpatientOrderDispenseSheetPrintSource> {
  const date = queryDate || dayjs().format('YYYY-MM-DD');
  const byEncounter = new Map<string, PrintPendingRow[]>();
  for (const row of selectedRows) {
    const encounterId = str(row.encounterId);
    if (!encounterId) {
      continue;
    }
    const bucket = byEncounter.get(encounterId);
    if (bucket) {
      bucket.push(row);
    } else {
      byEncounter.set(encounterId, [row]);
    }
  }

  const patientBlocks: InpatientOrderDispenseSheetPatientBlock[] = [];
  const encounterIds = [...byEncounter.keys()];
  if (encounterIds.length > 0) {
    const preservedIds = encounterIds
      .map((id) => preserveApiIdForRequest(id))
      .filter((id): id is string => Boolean(id));
    const executionDeadline = dayjs(date).endOf('day').format('YYYY-MM-DD HH:mm:ss');
    const listBody = {
      encounterIds: preservedIds,
      therapyType: 'all' as const,
      executionDeadline,
      executeTab: 'executed' as const,
    };
    const [westRes, tcmRes, packageBatchSiteEnabled] = await Promise.all([
      httpListNurseStationOrderExecuteForPrint({ ...listBody, medicineCategoryTab: 'west' }),
      httpListNurseStationOrderExecuteForPrint({ ...listBody, medicineCategoryTab: 'tcm' }),
      resolveStandingOrderPackageBatchSiteEnabled(),
    ]);

    for (const encounterId of encounterIds) {
      const orderRows = byEncounter.get(encounterId) ?? [];
      const head = orderRows[0];
      if (!head) {
        continue;
      }
      const patient = resolvePatient(patients, head);
      if (!patient) {
        continue;
      }
      const selectedOrderKeys = new Set(
        orderRows
          .map((item) => str(item.listRowKey) || str(item.orderId))
          .filter((key) => key.length > 0),
      );
      const filterBySelection = selectedOrderKeys.size > 0;
      const seenOrderKeys = new Set<string>();
      const drugOnly: Array<{
        drugName: string;
        doseText: string;
        specText: string;
        totalQuantityText: string;
      }> = [];

      function appendGroups(groups: NurseStationOrderExecutePatientGroupDto[]) {
        for (const group of groups) {
          if (str(group.encounterId) !== encounterId) {
            continue;
          }
          for (const order of group.orders ?? []) {
            if (str(order.orderType).toUpperCase() !== 'MED_REQUEST') {
              continue;
            }
            const orderKey =
              str(order.listRowKey) ||
              `${encounterId}-${str(order.orderId)}-${str(order.orderType)}`;
            if (!orderKey || seenOrderKeys.has(orderKey)) {
              continue;
            }
            if (
              filterBySelection &&
              !selectedOrderKeys.has(orderKey) &&
              !selectedOrderKeys.has(str(order.orderId))
            ) {
              continue;
            }
            const slotsOnDay = (order.executedSlots ?? []).filter((slot) => {
              const at = str(slot.recordedTime) || str(slot.planTime);
              if (!at) {
                return false;
              }
              if (at.startsWith(date)) {
                return true;
              }
              return dayjs(at).isValid() && dayjs(at).format('YYYY-MM-DD') === date;
            });
            if (slotsOnDay.length <= 0) {
              continue;
            }
            seenOrderKeys.add(orderKey);
            const packageBatchQuantity = shouldUsePackageBatchDispenseQuantity({
              packageBatchSiteEnabled,
              isOral: isOralMethodCode(order.methodCode),
              therapyEnum: order.therapyEnum,
              packageSplitRatioText: order.packageSplitRatioText,
              packageUnitDisplay: order.packageUnitDisplay,
            });
            drugOnly.push({
              drugName: str(order.adviceDisplayName),
              specText: str(order.specificationText),
              doseText: str(order.dosageDisplay),
              totalQuantityText: formatOrderDispenseTotalQuantityText(
                order.dosageDisplay,
                slotsOnDay.length,
                {
                  orderTotalQuantityText: order.quantityDisplay,
                  packageBatchQuantity,
                  packageUnitDisplay: order.packageUnitDisplay,
                },
              ),
            });
          }
        }
      }

      appendGroups(westRes.groups ?? []);
      appendGroups(tcmRes.groups ?? []);
      drugOnly.sort((a, b) => a.drugName.localeCompare(b.drugName, 'zh-CN'));
      if (drugOnly.length <= 0) {
        continue;
      }

      const admissionRaw = str(patient.admissionAt) || str(patient.inWardAt);
      const admissionAtText = admissionRaw
        ? dayjs(admissionRaw).isValid()
          ? dayjs(admissionRaw).format('YYYY-MM-DD HH:mm')
          : admissionRaw
        : '';
      patientBlocks.push({
        patientHeadText: formatOrderDispensePatientHeadText({
          regionName: str(patient.regionName),
          roomName: str(patient.wardName),
          bedNo: str(patient.bedNo),
          patientName: str(patient.name),
          ageText: ageTextOf(patient),
          admissionAtText,
        }),
        drugRows: drugOnly,
      });
    }
  }

  return {
    hospitalName: resolvePrintHospitalName(),
    documentTitle: '摆药单',
    executeDateText: date,
    patientBlocks,
  };
}

/**
 * 中药用药单付数文案（如「5付」）。
 * 只用方级 chineseHerbsDoseQuantity，禁止用各味剂量串或开立总量克数冒充付数。
 */
function formatOralMedSheetTcmFuText(
  chineseHerbsDoseQuantity: null | number | string | undefined,
): string {
  const raw = str(chineseHerbsDoseQuantity);
  if (!raw) {
    return '';
  }
  const numeric = Number(raw);
  if (!Number.isFinite(numeric) || numeric <= 0) {
    return '';
  }
  const normalized = String(numeric).replace(/(\.\d*?[1-9])0+$|\.0+$/u, '$1');
  return `${normalized}付`;
}

/**
 * 中药折叠方拆成逐味用药单行：每味一行药名+简化剂量；用法写在方头。
 * 频次/付数只落方头，tcmGroupRowSpan 供模板 rowspan 合并；味数对不上时仍按药名拆行。
 */
function buildOralMedSheetTcmHerbRows(
  order: Pick<
    NurseStationOrderExecuteRowDto,
    | 'adviceDisplayName'
    | 'chineseHerbsDoseQuantity'
    | 'dosageDisplay'
    | 'frequencyDisplay'
    | 'remark'
    | 'specificationText'
    | 'usageDisplay'
  >,
): InpatientOralMedSheetDrugRow[] {
  const parsed = parseTcmCollapsedHerbDisplay(order.adviceDisplayName, order.dosageDisplay);
  const fuText =
    formatOralMedSheetTcmFuText(order.chineseHerbsDoseQuantity) || parsed.doseCountText;
  // 优先用药名/剂量一一对应的配对；对不上则只拆药名
  let herbs = parsed.pairs;
  if (herbs.length <= 0) {
    herbs = str(order.adviceDisplayName)
      .split(/\s+/)
      .filter(Boolean)
      .map((name) => ({ name, dose: '' }));
  }
  if (herbs.length <= 0) {
    return [
      {
        drugName: str(order.adviceDisplayName),
        specText: str(order.specificationText),
        doseText: '',
        usageText: str(order.usageDisplay),
        freqText: str(order.frequencyDisplay),
        totalQuantityText: fuText,
        remarkText: str(order.remark),
        tcmGroupRowSpan: 1,
      },
    ];
  }
  const usageText = str(order.usageDisplay);
  const freqText = str(order.frequencyDisplay);
  const remarkText = str(order.remark);
  const specText = str(order.specificationText);
  const rows: InpatientOralMedSheetDrugRow[] = [];
  const groupSpan = herbs.length;
  // 按方内药名顺序逐味落行；频次/付数只写方头，续行用 rowspan 合并
  for (let index = 0; index < herbs.length; index += 1) {
    const herb = herbs[index];
    const isHead = index === 0;
    rows.push({
      drugName: herb?.name ?? '',
      specText: isHead ? specText : '',
      doseText: herb?.dose ?? '',
      usageText: isHead ? usageText : '',
      freqText: isHead ? freqText : '',
      totalQuantityText: isHead ? fuText : '',
      remarkText: isHead ? remarkText : '',
      tcmGroupRowSpan: isHead ? groupSpan : 0,
    });
  }
  return rows;
}

/**
 * 患者用药单：一张连续多人（当日已执行全部药品，勾选打印）。
 * 每名患者一块：患者信息（表外）→ 药品表（含备注）→ 患者签字；同医嘱当日多次执行合计为一行。
 * 院点长嘱不可拆（inpatientStandingOrderSplittable=0）时口服长嘱总量按「执行次数 × 包装单位」。
 * 中药（tcmFlag）：按味拆行并打简化单次量；同方频次/总量 rowspan 合并；西药口径不变。
 */
export async function buildOralMedSheetPrintSource(
  patients: NurseStationModulePatient[],
  selectedRows: PrintPendingRow[],
  queryDate: string,
): Promise<InpatientOralMedSheetPrintSource> {
  const date = queryDate || dayjs().format('YYYY-MM-DD');
  const byEncounter = new Map<string, PrintPendingRow[]>();
  for (const row of selectedRows) {
    const encounterId = str(row.encounterId);
    if (!encounterId) {
      continue;
    }
    const bucket = byEncounter.get(encounterId);
    if (bucket) {
      bucket.push(row);
    } else {
      byEncounter.set(encounterId, [row]);
    }
  }

  const patientBlocks: InpatientOralMedSheetPatientBlock[] = [];
  const encounterIds = [...byEncounter.keys()];
  if (encounterIds.length <= 0) {
    return {
      hospitalName: resolvePrintHospitalName(),
      documentTitle: '患者用药单',
      executeDateText: date,
      patientBlocks,
    };
  }

  const preservedIds = encounterIds
    .map((id) => preserveApiIdForRequest(id))
    .filter((id): id is string => Boolean(id));
  const executionDeadline = dayjs(date).endOf('day').format('YYYY-MM-DD HH:mm:ss');
  const listBody = {
    encounterIds: preservedIds,
    therapyType: 'all' as const,
    executionDeadline,
    executeTab: 'executed' as const,
  };
  const [westRes, tcmRes, packageBatchSiteEnabled] = await Promise.all([
    httpListNurseStationOrderExecuteForPrint({ ...listBody, medicineCategoryTab: 'west' }),
    httpListNurseStationOrderExecuteForPrint({ ...listBody, medicineCategoryTab: 'tcm' }),
    resolveStandingOrderPackageBatchSiteEnabled(),
  ]);
  const hospitalName = resolvePrintHospitalName();

  for (const encounterId of encounterIds) {
    const orderRows = byEncounter.get(encounterId) ?? [];
    const head = orderRows[0];
    if (!head) {
      continue;
    }
    const patient = resolvePatient(patients, head);
    if (!patient) {
      continue;
    }
    const selectedOrderKeys = new Set(
      orderRows
        .map((item) => str(item.listRowKey) || str(item.orderId))
        .filter((key) => key.length > 0),
    );
    const filterBySelection = selectedOrderKeys.size > 0;
    const seenOrderKeys = new Set<string>();
    // 西药按药名排序；中药按方内味序成块，禁止与西药混排打散方剂
    const westDrugRows: InpatientOralMedSheetDrugRow[] = [];
    const tcmDrugRows: InpatientOralMedSheetDrugRow[] = [];

    function appendGroups(groups: NurseStationOrderExecutePatientGroupDto[]) {
      for (const group of groups) {
        if (str(group.encounterId) !== encounterId) {
          continue;
        }
        for (const order of group.orders ?? []) {
          if (str(order.orderType).toUpperCase() !== 'MED_REQUEST') {
            continue;
          }
          const orderKey =
            str(order.listRowKey) || `${encounterId}-${str(order.orderId)}-${str(order.orderType)}`;
          if (!orderKey || seenOrderKeys.has(orderKey)) {
            continue;
          }
          if (
            filterBySelection &&
            !selectedOrderKeys.has(orderKey) &&
            !selectedOrderKeys.has(str(order.orderId))
          ) {
            continue;
          }
          const slotsOnDay = (order.executedSlots ?? []).filter((slot) => {
            const at = str(slot.recordedTime) || str(slot.planTime);
            if (!at) {
              return false;
            }
            if (at.startsWith(date)) {
              return true;
            }
            return dayjs(at).isValid() && dayjs(at).format('YYYY-MM-DD') === date;
          });
          if (slotsOnDay.length <= 0) {
            continue;
          }
          seenOrderKeys.add(orderKey);
          if (order.tcmFlag === true) {
            tcmDrugRows.push(...buildOralMedSheetTcmHerbRows(order));
            continue;
          }
          const isOral = isOralMethodCode(order.methodCode);
          const packageBatchQuantity = shouldUsePackageBatchDispenseQuantity({
            packageBatchSiteEnabled,
            isOral,
            therapyEnum: order.therapyEnum,
            packageSplitRatioText: order.packageSplitRatioText,
            packageUnitDisplay: order.packageUnitDisplay,
          });
          westDrugRows.push({
            drugName: str(order.adviceDisplayName),
            specText: str(order.specificationText),
            doseText: str(order.dosageDisplay),
            usageText: str(order.usageDisplay),
            freqText: str(order.frequencyDisplay),
            totalQuantityText: formatOrderDispenseTotalQuantityText(
              order.dosageDisplay,
              slotsOnDay.length,
              {
                orderTotalQuantityText: order.quantityDisplay,
                packageBatchQuantity,
                packageUnitDisplay: order.packageUnitDisplay,
              },
            ),
            remarkText: str(order.remark),
          });
        }
      }
    }

    appendGroups(westRes.groups ?? []);
    appendGroups(tcmRes.groups ?? []);
    westDrugRows.sort((a, b) => a.drugName.localeCompare(b.drugName, 'zh-CN'));
    const drugOnly = [...westDrugRows, ...tcmDrugRows];
    if (drugOnly.length <= 0) {
      continue;
    }

    const admissionRaw = str(patient.admissionAt) || str(patient.inWardAt);
    const admissionAtText = admissionRaw
      ? dayjs(admissionRaw).isValid()
        ? dayjs(admissionRaw).format('YYYY-MM-DD HH:mm')
        : admissionRaw
      : '';
    const bedNo = str(patient.bedNo) || str(head.bedNo);
    const patientName = str(patient.name) || str(head.patientName);
    patientBlocks.push({
      patientHeadText: formatOrderDispensePatientHeadText({
        regionName: str(patient.regionName),
        roomName: str(patient.wardName),
        bedNo,
        patientName,
        ageText: ageTextOf(patient),
        admissionAtText,
      }),
      patientSignLabel: '患者签字：____________',
      drugRows: drugOnly,
    });
  }

  return {
    hospitalName,
    documentTitle: '患者用药单',
    executeDateText: date,
    patientBlocks,
  };
}

/** 摆药单（汇总领药·按汇总单号） */
export async function buildDispenseSheetPrintSource(
  summaryNo: string,
): Promise<InpatientDispenseSheetPrintSource> {
  const res = await httpGetMedicationSummarySlipPatientDetail({ summaryNo });
  const header = res.header;
  const drugRows = [];
  for (const group of res.patientGroups ?? []) {
    const patientText = `${str(group.bedDisplay)} ${str(group.patientName)}`.trim();
    for (const line of group.lines ?? []) {
      const qty =
        str(line.displayQuantityText) ||
        `${str(line.itemQuantity)}${str(line.unitCodeText) || str(line.unitCode)}`;
      drugRows.push({
        patientText,
        drugName: str(line.medicationName),
        specText: str(line.specificationText),
        quantityUnitText: qty,
        doseText: str(line.dosageInstruction),
        usageText: str(line.methodCodeText),
        freqText: str(line.frequencyCodeText),
      });
    }
  }
  return {
    hospitalName: resolvePrintHospitalName(),
    documentTitle: '摆药单',
    periodText: str(header?.applyTime) || str(header?.dispenseTime),
    preparedByName: str(header?.applicantName),
    wardName: str(header?.purposeLocationName),
    drugRows,
    printedAtText: printedAtNow(),
  };
}

/** 输液贴：按待打行的 encounter + group，从已校对执行列表取当日输液成组药品 */
export async function buildInfusionPrintSource(
  patients: NurseStationModulePatient[],
  row: PrintPendingRow,
  queryDate: string,
): Promise<OutpatientInfusionLabelPrintSource> {
  const patient = resolvePatient(patients, row);
  const encounterId = preserveApiIdForRequest(row.encounterId || patient?.id);
  if (!encounterId) {
    throw new Error('缺少就诊信息');
  }

  const date = queryDate || dayjs().format('YYYY-MM-DD');
  const executionDeadline = dayjs(date).endOf('day').format('YYYY-MM-DD HH:mm:ss');
  const listBody = {
    encounterIds: [encounterId],
    therapyType: 'all' as const,
    medicineCategoryTab: 'west' as const,
    executionDeadline,
  };
  const [pendingRes, executedRes] = await Promise.all([
    httpListNurseStationOrderExecuteForPrint({ ...listBody, executeTab: 'pending' }),
    httpListNurseStationOrderExecuteForPrint({ ...listBody, executeTab: 'executed' }),
  ]);

  const targetGroupId = str(row.groupId);
  const targetOrderId = str(row.orderId);
  const matchedByOrderId = new Map<
    string,
    {
      order: {
        adviceDisplayName?: string;
        dosageDisplay?: string;
        groupId?: string;
        infusionFlag?: boolean;
        methodCode?: string;
        orderId?: string;
        orderType?: string;
        sortNumber?: number;
        specificationText?: string;
        speedCode?: string;
        speedDisplay?: string;
        usageDisplay?: string;
      };
      hasDaySlot: boolean;
    }
  >();

  /** 收集目标组在查询日有槽位的输液药嘱 */
  function collectFromGroups(
    groups: { encounterId?: string; orders?: NurseStationOrderExecutePatientGroupDto['orders'] }[],
    tab: 'executed' | 'pending',
  ) {
    for (const group of groups) {
      if (str(group.encounterId) !== encounterId) {
        continue;
      }
      for (const order of group.orders ?? []) {
        if (!isInfusionExecuteOrder(order)) {
          continue;
        }
        const orderGroupKey = str(order.groupId) || `r:${str(order.orderId)}`;
        const matched =
          (targetGroupId &&
            (orderGroupKey === targetGroupId || str(order.groupId) === targetGroupId)) ||
          (targetOrderId && str(order.orderId) === targetOrderId);
        if (!matched) {
          continue;
        }
        const slots = tab === 'pending' ? (order.pendingSlots ?? []) : (order.executedSlots ?? []);
        const onDay = slots.some((slot) => isSlotOnQueryDate(slot, date));
        if (!onDay) {
          continue;
        }
        const orderId = str(order.orderId);
        if (!orderId) {
          continue;
        }
        const existing = matchedByOrderId.get(orderId);
        if (existing) {
          existing.hasDaySlot = true;
          continue;
        }
        matchedByOrderId.set(orderId, { order, hasDaySlot: true });
      }
    }
  }

  collectFromGroups(pendingRes.groups ?? [], 'pending');
  collectFromGroups(executedRes.groups ?? [], 'executed');

  const medicationLines: NurseStationTreatmentLineDto[] = [];
  const sortedOrders = [...matchedByOrderId.values()]
    .filter((item) => item.hasDaySlot)
    .slice()
    .sort((a, b) => (a.order.sortNumber ?? 0) - (b.order.sortNumber ?? 0));
  for (const { order } of sortedOrders) {
    medicationLines.push({
      requestId: str(order.orderId),
      groupId: str(order.groupId) || targetGroupId,
      itemName: str(order.adviceDisplayName),
      specText: str(order.specificationText),
      infusionFlag: true,
      methodCode: str(order.methodCode),
      methodName: str(order.usageDisplay),
      // 住院执行列表无发药 quantity；瓶贴总量列印 dosageDisplay（与列表剂量列一致）
      dosageDisplay: str(order.dosageDisplay),
      unitName: '',
      sortNumber: order.sortNumber ?? medicationLines.length + 1,
      // 住院按选定日打印，禁止再按用药天数展开多张
      dispensePerDuration: 1,
    });
  }

  if (medicationLines.length <= 0) {
    throw new Error('未找到可打印的输液药品');
  }

  // 模板 field=speedCode；优先组内首条 speedDisplay（同列表扩展行）
  let speedPrintText = '';
  for (const { order } of sortedOrders) {
    const text = str(order.speedDisplay) || str(order.speedCode);
    if (text) {
      speedPrintText = text;
      break;
    }
  }

  // 瓶贴宽度紧，执行时间只印到分钟
  const timeRaw = str(row.timeText);
  let executeTimeText = `${date} 00:00`;
  if (timeRaw) {
    const hhmm = timeRaw.length <= 5 ? timeRaw : timeRaw.slice(0, 5);
    executeTimeText = `${date} ${hhmm}`;
  }

  // 仅病房 + 床号，形如「301-6床」；不拼病区
  const stripRoomBedSuffix = (raw: string) =>
    raw.replace(/病房$/u, '').replace(/房$/u, '').replace(/床$/u, '').trim();
  const room = stripRoomBedSuffix(str(patient?.wardName));
  const bed = stripRoomBedSuffix(str(row.bedNo) || str(patient?.bedNo));
  let wardBedText = '';
  if (room && bed) {
    wardBedText = `${room}-${bed}床`;
  } else if (bed) {
    wardBedText = `${bed}床`;
  } else {
    wardBedText = room;
  }

  const admissionRaw = str(patient?.admissionAt) || str(patient?.inWardAt);
  let admissionTimeText = '';
  if (admissionRaw) {
    const parsed = dayjs(admissionRaw);
    admissionTimeText = parsed.isValid() ? parsed.format('YYYY-MM-DD HH:mm') : admissionRaw;
  }

  return {
    patientName: str(patient?.name) || str(row.patientName),
    genderEnumText: str(patient?.gender),
    ageText: ageTextOf(patient),
    // 住院模板标题「入院时间」；field 仍为 registerTimeText
    registerTimeText: admissionTimeText,
    wardBedText,
    medicationLines,
    speedCode: speedPrintText,
    executorName: '',
    executeTimeText,
  };
}

function formatApplyOrderDateText(raw?: string): string {
  const value = str(raw);
  if (!value) {
    return '';
  }
  const parsed = dayjs(value);
  if (!parsed.isValid()) {
    return value;
  }
  return parsed.format('YYYY年MM月DD日 HH:mm');
}

function collectUniqueNonEmpty(values: string[]): string[] {
  const seen = new Set<string>();
  const ordered: string[] = [];
  for (const value of values) {
    const text = str(value);
    if (!text || seen.has(text)) {
      continue;
    }
    seen.add(text);
    ordered.push(text);
  }
  return ordered;
}

/**
 * 检查/检验申请单：按就诊合并所选医嘱行为一张单。
 * 返回按 encounter 分组的 source 列表（一张单一就诊）。
 */
export async function buildServiceApplyPrintSources(
  patients: NurseStationModulePatient[],
  selectedRows: PrintPendingRow[],
  executionDeadline: string,
  kind: 'exam' | 'lab',
  dateFrom?: string,
  dateTo?: string,
): Promise<
  Array<{
    encounterId: string;
    bedNo: string;
    patientName: string;
    source: InpatientExamApplyPrintSource | InpatientLabApplyPrintSource;
  }>
> {
  const categoryCode = kind === 'exam' ? SERVICE_CATEGORY_TEST : SERVICE_CATEGORY_PROOF;
  const selectedOrderIds = new Set(
    selectedRows.map((row) => str(row.orderId)).filter((id) => id.length > 0),
  );
  const selectedKeys = new Set(
    selectedRows.map((row) => str(row.listRowKey)).filter((id) => id.length > 0),
  );
  const encounterIds = [
    ...new Set(
      selectedRows
        .map((row) => preserveApiIdForRequest(row.encounterId) || str(row.encounterId))
        .filter((id): id is string => Boolean(id)),
    ),
  ];
  if (encounterIds.length <= 0 || (selectedOrderIds.size <= 0 && selectedKeys.size <= 0)) {
    return [];
  }

  const fromDay = str(dateFrom);
  const toDay = str(dateTo);
  const listBody = {
    encounterIds,
    therapyType: 'all' as const,
    medicineCategoryTab: 'west' as const,
    executionDeadline,
    // 与待打列表同源：按开嘱日闭区间交后端过滤
    reqOpenedTimeFrom: fromDay ? `${fromDay} 00:00:00` : undefined,
    reqOpenedTimeTo: toDay ? `${toDay} 23:59:59` : undefined,
  };
  const [pendingRes, executedRes] = await Promise.all([
    httpListNurseStationOrderExecuteForPrint({ ...listBody, executeTab: 'pending' }),
    httpListNurseStationOrderExecuteForPrint({ ...listBody, executeTab: 'executed' }),
  ]);

  type OrderBucket = {
    encounterId: string;
    order: NonNullable<NurseStationOrderExecutePatientGroupDto['orders']>[number];
  };
  const byOrderKey = new Map<string, OrderBucket>();

  function mergeGroups(groups: NurseStationOrderExecutePatientGroupDto[]) {
    for (const group of groups) {
      const encounterId = str(group.encounterId);
      for (const order of group.orders ?? []) {
        if (str(order.orderType).toUpperCase() !== 'SERVICE_REQUEST') {
          continue;
        }
        if (str(order.categoryEnum) !== categoryCode) {
          continue;
        }
        const orderId = str(order.orderId);
        const listRowKey = str(order.listRowKey) || `${orderId}-${str(order.orderType)}`;
        const matched =
          (orderId && selectedOrderIds.has(orderId)) ||
          (listRowKey && selectedKeys.has(listRowKey));
        if (!matched) {
          continue;
        }
        const mapKey = listRowKey || orderId;
        if (!mapKey || byOrderKey.has(mapKey)) {
          continue;
        }
        byOrderKey.set(mapKey, {
          encounterId: encounterId || str(order.encounterId),
          order,
        });
      }
    }
  }

  mergeGroups(pendingRes.groups ?? []);
  mergeGroups(executedRes.groups ?? []);

  const byEncounter = new Map<string, OrderBucket[]>();
  for (const bucket of byOrderKey.values()) {
    if (!bucket.encounterId) {
      continue;
    }
    const list = byEncounter.get(bucket.encounterId) ?? [];
    list.push(bucket);
    byEncounter.set(bucket.encounterId, list);
  }

  const hospitalName = resolvePrintHospitalName();
  const results: Array<{
    encounterId: string;
    bedNo: string;
    patientName: string;
    source: InpatientExamApplyPrintSource | InpatientLabApplyPrintSource;
  }> = [];

  for (const [encounterId, buckets] of byEncounter.entries()) {
    const patient = patients.find((item) => str(item.id) === encounterId);
    const pendingMeta = selectedRows.find((row) => str(row.encounterId) === encounterId);
    const itemOrders = buckets.map((item) => item.order);
    const quantityFallback = '1次';
    const openTimes = itemOrders
      .map((order) => str(order.reqOpenedTime))
      .filter(Boolean)
      .sort((a, b) => a.localeCompare(b));
    const performOrgName = collectUniqueNonEmpty(
      itemOrders.map((order) => str(order.performerCheckOrgDisplay)),
    ).join('、');
    const orderingPractitionerName = collectUniqueNonEmpty(
      itemOrders.map((order) => str(order.requesterName)),
    ).join('、');

    const common = {
      hospitalName,
      inpatientNo: str(patient?.encounterBusNo),
      patientName: str(patient?.name) || str(pendingMeta?.patientName),
      genderEnumText: str(patient?.gender),
      ageText: ageTextOf(patient),
      departmentName: str(patient?.regionName) || str(patient?.wardName),
      bedNo: str(patient?.bedNo) || str(pendingMeta?.bedNo),
      diagnosisText: str(patient?.diagnosis),
      performOrgName,
      orderingPractitionerName,
      orderDateText: formatApplyOrderDateText(openTimes[0]),
    };

    if (kind === 'exam') {
      results.push({
        encounterId,
        bedNo: common.bedNo,
        patientName: common.patientName,
        source: {
          ...common,
          examPurposeText: '',
          itemRows: itemOrders.map((order, index) => {
            const bodySiteText = joinServiceRequestDetailNames(
              order.serviceRequestDetails,
              SERVICE_APPLY_BODY_STRUCTURE_TABLE,
            );
            const remarkText = str(order.remark) || bodySiteText;
            return {
              seqNo: String(index + 1),
              itemName: str(order.adviceDisplayName),
              quantityText: str(order.dosageDisplay) || quantityFallback,
              remarkText,
            };
          }),
        },
      });
      continue;
    }

    results.push({
      encounterId,
      bedNo: common.bedNo,
      patientName: common.patientName,
      source: {
        ...common,
        itemRows: itemOrders.map((order, index) => ({
          seqNo: String(index + 1),
          itemName: str(order.adviceDisplayName),
          detailText: joinPackageChildrenDetailText(order.packageChildren),
          specimenTypeText: joinServiceRequestDetailNames(
            order.serviceRequestDetails,
            SERVICE_APPLY_SPECIMEN_TABLE,
          ),
          quantityText: str(order.dosageDisplay) || quantityFallback,
          remarkText: str(order.remark),
        })),
      },
    });
  }

  return results;
}

export { resolvePatient };
