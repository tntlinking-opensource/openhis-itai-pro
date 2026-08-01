import type { NurseStationModulePatient } from '../components/nurse-station-module-patient.types';
import type { PrintPendingRow } from './print-center.types';

import type { NurseStationOrderExecutePatientGroupDto } from '#/api/inpatient/nurse-station/inpatient-nurse-station-order-execute.types';
import type { NurseStationTreatmentLineDto } from '#/api/outpatient-nurse-station/outpatient-nurse-station.types';
import type { InpatientDispenseSheetPrintSource } from '#/utils/print/mappers/inpatient-dispense-sheet';
import type { InpatientDispenseSummaryPrintSource } from '#/utils/print/mappers/inpatient-dispense-summary';
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
import type { InpatientExamApplyPrintSource } from '#/utils/print/mappers/inpatient-exam-apply';
import type { InpatientLabApplyPrintSource } from '#/utils/print/mappers/inpatient-lab-apply';
import type {
  InpatientTempOrderPrintSource,
  InpatientTempOrderRow,
} from '#/utils/print/mappers/inpatient-temp-order';
import type { InpatientWristbandPrintSource } from '#/utils/print/mappers/inpatient-wristband';
import type { OutpatientInfusionLabelPrintSource } from '#/utils/print/mappers/outpatient-infusion-label';

import dayjs from 'dayjs';

import type { NurseStationCostDetailLineDto } from '#/api/inpatient/nurse-station/inpatient-nurse-station-cost-detail.types';

import { httpQueryNurseStationInpatientCostDetail } from '#/api/inpatient/nurse-station/inpatient-nurse-station-cost-detail.http';
import {
  httpGetMedicationSummarySlipDetail,
  httpGetMedicationSummarySlipPatientDetail,
  httpPagePendingDispenseSummaryLines,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-dispense-summary.http';
import { httpListNurseStationNursingRecords } from '#/api/inpatient/nurse-station/inpatient-nurse-station-nursing-record.http';
import { httpListNurseStationOrderExecute } from '#/api/inpatient/nurse-station/inpatient-nurse-station-order-execute.http';
import {
  INFUSION_METHOD_CODES,
  METHOD_CODE_IV_INJECTION,
  ORAL_METHOD_CODES,
  normalizeMedicationMethodCode,
} from '#/utils/print/constants/medication-method-codes';
import {
  SERVICE_CATEGORY_PROOF,
  SERVICE_CATEGORY_TEST,
} from '#/utils/print/constants/service-request-category-codes';
import { decimalAdd } from '#/utils/format/decimal-util';
import { formatMoneyDisplay } from '#/utils/format/money-scale';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { resolvePrintHospitalName } from '#/utils/print/resolve-print-hospital-name';
import {
  SERVICE_APPLY_BODY_STRUCTURE_TABLE,
  SERVICE_APPLY_SPECIMEN_TABLE,
} from '#/utils/service-apply-display';
import { formatCostDetailQuantity } from '../cost-details/services/map-nurse-station-cost-detail';
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

/** 空白巡视行（手写区） */
function blankPatrolRow() {
  return {
    groupNo: '',
    drugName: '',
    specText: '',
    doseText: '',
    dripRate: '',
    timeText: '',
    signText: '',
  };
}

/** 输液巡视卡：患者头信息 + 当日输液组药明细（滴速/时间/签名留空） */
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

  const res = await httpPagePendingDispenseSummaryLines({
    encounterIds: [encounterId],
    medicineCategoryTab: 'west',
    therapyFilter: 'all',
    queryDeadline: executionDeadline,
    pageNo: 1,
    pageSize: 500,
  });

  for (const group of res.patientGroups ?? []) {
    if (str(group.encounterId) !== encounterId) {
      continue;
    }
    for (const order of group.orderRows ?? []) {
      if (!isInfusionMethodCode(order.methodCode)) {
        continue;
      }
      const orderGroupKey = str(order.groupId) || `r:${str(order.medReqId)}`;
      let groupNo = groupKeyToNo.get(orderGroupKey);
      if (groupNo === undefined) {
        groupSeq += 1;
        groupNo = groupSeq;
        groupKeyToNo.set(orderGroupKey, groupNo);
      }
      const point = order.timePoints?.[0];
      const doseFromPoint =
        point?.quantity !== undefined && point?.quantity !== null
          ? `${point.quantity}${str(point.unitCodeText) || str(point.unitCode)}`
          : '';
      patrolRows.push({
        groupNo: String(groupNo),
        drugName: str(order.medicationName),
        specText: str(order.specificationText),
        doseText: str(order.dosageInstruction) || doseFromPoint,
        dripRate: '',
        timeText: '',
        signText: '',
      });
    }
  }

  // 无输液药品不得出纸
  if (groupSeq <= 0 || patrolRows.length <= 0) {
    throw new Error('当前患者没有可打印的输液药品');
  }

  // 补空白行，便于手写补录
  const minRows = 20;
  while (patrolRows.length < minRows) {
    patrolRows.push(blankPatrolRow());
  }

  return {
    hospitalName: resolvePrintHospitalName(),
    documentTitle: '输液巡视卡',
    wardName: str(patient?.wardName),
    bedNo: str(row.bedNo) || str(patient?.bedNo),
    patientName: str(row.patientName) || str(patient?.name),
    ageText: ageTextOf(patient),
    executeDateText: dayjs().format('YYYY-MM-DD'),
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
    departmentName: str(patient?.wardName) || str(patient?.regionName),
    bedNo: formatWristbandBedNo(str(row.bedNo) || str(patient?.bedNo)),
    inpatientNo: str(patient?.encounterBusNo),
    allergyText: '',
    diagnosisText: str(patient?.diagnosis),
  };
}

/** 口服药贴：按待打行的 encounter + medReqId 取一条口服药 */
export async function buildOralMedLabelPrintSource(
  patients: NurseStationModulePatient[],
  row: PrintPendingRow,
  executionDeadline: string,
): Promise<InpatientOralMedLabelPrintSource> {
  const patient = resolvePatient(patients, row);
  const encounterId = preserveApiIdForRequest(row.encounterId || patient?.id);
  if (!encounterId) {
    throw new Error('缺少就诊信息');
  }

  const res = await httpPagePendingDispenseSummaryLines({
    encounterIds: [encounterId],
    medicineCategoryTab: 'west',
    therapyFilter: 'all',
    queryDeadline: executionDeadline,
    pageNo: 1,
    pageSize: 500,
  });

  const targetOrderId = str(row.orderId);
  let matched:
    | {
        medicationName?: string;
        specificationText?: string;
        dosageInstruction?: string;
        frequencyCodeText?: string;
        timePoints?: Array<{
          quantity?: number | string;
          unitCode?: string;
          unitCodeText?: string;
        }>;
      }
    | undefined;

  for (const group of res.patientGroups ?? []) {
    if (str(group.encounterId) !== encounterId) {
      continue;
    }
    for (const order of group.orderRows ?? []) {
      if (!isOralMethodCode(order.methodCode)) {
        continue;
      }
      if (targetOrderId && str(order.medReqId) !== targetOrderId) {
        continue;
      }
      matched = order;
      break;
    }
    if (matched) {
      break;
    }
  }

  if (!matched) {
    throw new Error('未找到可打印的口服药');
  }

  const point = matched.timePoints?.[0];
  const doseFromPoint =
    point?.quantity !== undefined && point?.quantity !== null
      ? `${point.quantity}${str(point.unitCodeText) || str(point.unitCode)}`
      : '';
  const doseText =
    str(matched.dosageInstruction) ||
    [doseFromPoint, str(matched.frequencyCodeText)].filter(Boolean).join(' ');

  return {
    bedNo: str(row.bedNo) || str(patient?.bedNo),
    patientName: str(row.patientName) || str(patient?.name),
    ageText: ageTextOf(patient),
    drugName: str(matched.medicationName),
    specText: str(matched.specificationText),
    doseText,
  };
}

/** 费用数量展示：空值不输出破折号 */
function feeQuantityText(line: NurseStationCostDetailLineDto): string {
  const text = formatCostDetailQuantity(line).trim();
  if (!text || text === '—') {
    return '';
  }
  return text;
}

/** 费用清单：与费用明细页相同接口，默认 DETAIL 明细按费用块分组 */
export async function buildFeeListPrintSource(
  patients: NurseStationModulePatient[],
  dateFrom: string,
  dateTo: string,
): Promise<InpatientFeeListPrintSource> {
  const patient = patients[0];
  if (!patient) {
    throw new Error('请选择患者');
  }
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
    statusEnum: 'PENDING_SETTLEMENT',
  });

  const categoryNameByCode = new Map<string, string>();
  for (const item of res.totals?.categoryTotals ?? []) {
    const code = str(item.categoryCode);
    if (!code) {
      continue;
    }
    categoryNameByCode.set(code, str(item.categoryDisplay) || code);
  }

  const feeRows: InpatientFeeListFeeRow[] = [];
  const byBlock = new Map<
    string,
    { display: string; lines: NurseStationCostDetailLineDto[]; amount: number }
  >();
  for (const line of res.detailLines ?? []) {
    const code = str(line.expenseBlockCode) || 'OTHER';
    const display = categoryNameByCode.get(code) || code;
    const bucket = byBlock.get(code) ?? { display, lines: [], amount: 0 };
    bucket.lines.push(line);
    bucket.amount = decimalAdd(bucket.amount, line.totalPrice ?? 0);
    byBlock.set(code, bucket);
  }

  for (const [, bucket] of byBlock) {
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
        quantityUnitText: feeQuantityText(line),
        insuranceLevelText: '',
        unitPriceText: moneyText(line.unitPrice),
        amountText: moneyText(line.totalPrice),
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
    wardName: str(patient.wardName),
    roomNo: '',
    bedNo: str(patient.bedNo),
    departmentName: str(patient.regionName),
    admitAtText: str(patient.admissionAt),
    dischargeAtText: str(patient.dischargeAt),
    inpatientDaysText: '',
    admissionDiagnosis: str(patient.diagnosis),
    prepaidAmountText: prepaid,
    totalAmountText: grand,
    balanceAmountText: moneyText(patient.availableBalance ?? patient.accountBalance),
    feeRows,
    printedAtText: printedAtNow(),
  };
}

/** 临嘱/长嘱执行记录：仅取已执行列表组装 orderRows（可按执行日区间过滤） */
export async function buildOrderExecutePrintSource(
  patients: NurseStationModulePatient[],
  selectedRows: PrintPendingRow[],
  executionDeadline: string,
  therapyType: 'long' | 'temp',
  dateFrom?: string,
  dateTo?: string,
): Promise<InpatientTempOrderPrintSource> {
  const patient = patients[0];
  if (!patient) {
    throw new Error('请选择患者');
  }
  const encounterId = preserveApiIdForRequest(patient.id);
  if (!encounterId) {
    throw new Error('患者就诊信息不完整');
  }

  const executedRes = await httpListNurseStationOrderExecute({
    encounterIds: [encounterId],
    therapyType,
    executionDeadline,
    executeTab: 'executed',
  });
  const groups: NurseStationOrderExecutePatientGroupDto[] = executedRes.groups ?? [];

  const selectedKeys = new Set(
    selectedRows.map((row) => str(row.listRowKey) || str(row.id)).filter(Boolean),
  );
  const fromDay = str(dateFrom);
  const toDay =
    str(dateTo) || (executionDeadline ? dayjs(executionDeadline).format('YYYY-MM-DD') : '');
  const orderRows: InpatientTempOrderRow[] = [];
  for (const group of groups) {
    for (const order of group.orders ?? []) {
      const key = str(order.listRowKey) || `${str(order.orderId)}-${str(order.orderType)}`;
      if (selectedKeys.size > 0 && !selectedKeys.has(key)) {
        continue;
      }
      const slots = order.executedSlots ?? [];
      if (slots.length <= 0) {
        continue;
      }
      const opened = str(order.reqOpenedTime);
      const startDateText = opened ? dayjs(opened).format('MM-DD') : '';
      const startTimeText = opened ? dayjs(opened).format('HH:mm') : '';
      for (const slot of slots) {
        const executedAt = str(slot.recordedTime) || str(slot.planTime);
        // 有执行日区间时，只打落在区间内的执行点
        if (fromDay && toDay && executedAt) {
          const day = dayjs(executedAt).isValid() ? dayjs(executedAt).format('YYYY-MM-DD') : '';
          if (!day || day < fromDay || day > toDay) {
            continue;
          }
        }
        orderRows.push({
          startDateText,
          startTimeText,
          orderContent: str(order.adviceDisplayName),
          orderDoctorName: str(order.requesterName),
          executeNurseName: str(slot.operatorName),
          executeAtText: executedAt ? dayjs(executedAt).format('MM-DD HH:mm') : '',
        });
      }
    }
  }

  return {
    hospitalName: resolvePrintHospitalName(),
    documentTitle: therapyType === 'long' ? '长嘱执行记录' : '临嘱执行记录',
    inpatientNo: str(patient.encounterBusNo),
    patientName: str(patient.name),
    genderEnumText: str(patient.gender),
    ageText: ageTextOf(patient),
    departmentName: str(patient.regionName),
    wardName: str(patient.wardName),
    roomNo: '',
    bedNo: str(patient.bedNo),
    orderRows,
    doctorSignLabel: '医生：',
    nurseSignLabel: '护士：',
    printedAtText: printedAtNow(),
  };
}
/** 临时医嘱执行记录 */
export async function buildTempOrderPrintSource(
  patients: NurseStationModulePatient[],
  selectedRows: PrintPendingRow[],
  executionDeadline: string,
  dateFrom?: string,
  dateTo?: string,
): Promise<InpatientTempOrderPrintSource> {
  return buildOrderExecutePrintSource(
    patients,
    selectedRows,
    executionDeadline,
    'temp',
    dateFrom,
    dateTo,
  );
}

/** 长期医嘱执行记录（模板：ip-longOrder-print） */
export async function buildLongOrderPrintSource(
  patients: NurseStationModulePatient[],
  selectedRows: PrintPendingRow[],
  executionDeadline: string,
  dateFrom?: string,
  dateTo?: string,
): Promise<InpatientTempOrderPrintSource> {
  return buildOrderExecutePrintSource(
    patients,
    selectedRows,
    executionDeadline,
    'long',
    dateFrom,
    dateTo,
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
/** 摆药单 */
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
    httpListNurseStationOrderExecute({ ...listBody, executeTab: 'pending' }),
    httpListNurseStationOrderExecute({ ...listBody, executeTab: 'executed' }),
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
      quantity: 1,
      unitName: '',
      sortNumber: order.sortNumber ?? medicationLines.length + 1,
      // 住院按选定日打印，禁止再按用药天数展开多张
      dispensePerDuration: 1,
    });
  }

  if (medicationLines.length <= 0) {
    throw new Error('未找到可打印的输液药品');
  }

  const executeTimeText = str(row.timeText)
    ? `${date} ${str(row.timeText).length <= 5 ? `${str(row.timeText)}:00` : str(row.timeText)}`
    : `${date} 00:00:00`;

  return {
    patientName: str(patient?.name) || str(row.patientName),
    genderEnumText: str(patient?.gender),
    ageText: ageTextOf(patient),
    registerTimeText: `${date} 00:00:00`,
    medicationLines,
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

  const listBody = {
    encounterIds,
    therapyType: 'all' as const,
    medicineCategoryTab: 'west' as const,
    executionDeadline,
  };
  const [pendingRes, executedRes] = await Promise.all([
    httpListNurseStationOrderExecute({ ...listBody, executeTab: 'pending' }),
    httpListNurseStationOrderExecute({ ...listBody, executeTab: 'executed' }),
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
