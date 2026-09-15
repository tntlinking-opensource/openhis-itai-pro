import type { NurseStationModulePatient } from '../../components/nurse-station-module-patient.types';
import type { NurseStationOrderExecuteTableRow } from './map-nurse-station-order-execute';

import type {
  AdvicePackageChildItemDto,
  ServiceRequestDetailSummaryDto,
} from '#/api/doctor-station/advice.types';
import type { NurseStationTreatmentLineDto } from '#/api/outpatient-nurse-station/outpatient-nurse-station.types';
import type { InpatientExamApplyPrintSource } from '#/utils/print/mappers/inpatient-exam-apply';
import type { InpatientLabApplyPrintSource } from '#/utils/print/mappers/inpatient-lab-apply';
import type { InpatientOralMedLabelPrintSource } from '#/utils/print/mappers/inpatient-oral-med-label';
import type { OutpatientInfusionLabelPrintSource } from '#/utils/print/mappers/outpatient-infusion-label';

import dayjs from 'dayjs';

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
import { resolvePrintHospitalName } from '#/utils/print/resolve-print-hospital-name';
import {
  SERVICE_APPLY_BODY_STRUCTURE_TABLE,
  SERVICE_APPLY_SPECIMEN_TABLE,
} from '#/utils/service-apply-display';

/** 右键可打的贴签类型（输液贴 / 口服药贴） */
export type OrderExecuteLabelPrintDocType = 'infusion_label' | 'oral_med_label';

/** 右键可打的行打印类型（贴签 + 检查/检验申请单） */
export type OrderExecuteRowPrintDocType =
  | OrderExecuteLabelPrintDocType
  | 'exam_apply'
  | 'lab_apply';

/** 是否静脉注射途径（术语 404） */
export function isIvInjectionMethodCode(methodCode?: null | string): boolean {
  const raw = String(methodCode ?? '').trim();
  if (!raw) {
    return false;
  }
  const normalized = normalizeMedicationMethodCode(raw);
  return normalized === METHOD_CODE_IV_INJECTION || raw === METHOD_CODE_IV_INJECTION;
}

/** 是否静滴/输液途径（术语 405 / IVD） */
export function isInfusionDripMethodCode(methodCode?: null | string): boolean {
  const raw = String(methodCode ?? '').trim();
  if (!raw) {
    return false;
  }
  const normalized = normalizeMedicationMethodCode(raw);
  return INFUSION_METHOD_CODES.has(normalized) || INFUSION_METHOD_CODES.has(raw);
}

/** 是否口服途径（术语 1 / PO） */
export function isOralMethodCodeForExecuteRow(methodCode?: null | string): boolean {
  const raw = String(methodCode ?? '').trim();
  if (!raw) {
    return false;
  }
  const normalized = normalizeMedicationMethodCode(raw);
  return ORAL_METHOD_CODES.has(normalized) || ORAL_METHOD_CODES.has(raw);
}

/**
 * 按用法 code 决定右键贴签（禁止用 usageDisplay）：
 * 口服 → 口服药贴；静脉注射(404) / 静滴(405) / infusionFlag → 输液贴。
 */
export function resolveExecuteRowLabelPrintDocType(
  row: Pick<NurseStationOrderExecuteTableRow, 'infusionFlag' | 'methodCode' | 'orderType'>,
): OrderExecuteLabelPrintDocType | null {
  if (row.orderType !== 'MED_REQUEST') {
    return null;
  }
  if (isOralMethodCodeForExecuteRow(row.methodCode)) {
    return 'oral_med_label';
  }
  if (
    isIvInjectionMethodCode(row.methodCode) ||
    isInfusionDripMethodCode(row.methodCode) ||
    row.infusionFlag === true
  ) {
    return 'infusion_label';
  }
  return null;
}

/**
 * 右键行打印类型：检查(23) / 检验(22) 优先；其余回落贴签判定。
 * 分流仅用 orderType + categoryEnum / methodCode，禁止用展示名。
 */
export function resolveExecuteRowPrintDocType(
  row: Pick<
    NurseStationOrderExecuteTableRow,
    'categoryEnum' | 'infusionFlag' | 'methodCode' | 'orderType'
  >,
): OrderExecuteRowPrintDocType | null {
  if (row.orderType === 'SERVICE_REQUEST') {
    const category = String(row.categoryEnum ?? '').trim();
    if (category === SERVICE_CATEGORY_TEST) {
      return 'exam_apply';
    }
    if (category === SERVICE_CATEGORY_PROOF) {
      return 'lab_apply';
    }
    return null;
  }
  return resolveExecuteRowLabelPrintDocType(row);
}

/** 右键打印查询日：优先截止日当天有计划点的日期，否则取行内首个计划日 */
export function resolveExecuteRowLabelPrintQueryDate(
  row: Pick<NurseStationOrderExecuteTableRow, 'planSchedules'>,
  executionDeadline: string,
): string {
  const deadlineDay = dayjs(executionDeadline).isValid()
    ? dayjs(executionDeadline).format('YYYY-MM-DD')
    : dayjs().format('YYYY-MM-DD');
  for (const schedule of row.planSchedules ?? []) {
    const date = String(schedule.date ?? '').trim();
    if (date === deadlineDay && (schedule.slots?.length ?? 0) > 0) {
      return deadlineDay;
    }
  }
  for (const schedule of row.planSchedules ?? []) {
    const date = String(schedule.date ?? '').trim();
    if (date && (schedule.slots?.length ?? 0) > 0) {
      return date;
    }
  }
  return deadlineDay;
}

function str(value: null | number | string | undefined): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value).trim();
}

/** 瓶签滴速：优先展示名（与列表扩展行一致），否则回落编码 */
function resolveInfusionSpeedPrintText(
  speedDisplay?: null | string,
  speedCode?: null | string,
): string {
  return str(speedDisplay) || str(speedCode);
}

/** 从执行行（含成组成员）取首条非空滴速 */
function resolveSpeedPrintTextFromExecuteRow(row: NurseStationOrderExecuteTableRow): string {
  for (const member of row.mergedMedicationMembers ?? []) {
    const text = resolveInfusionSpeedPrintText(member.speedDisplay, member.speedCode);
    if (text) {
      return text;
    }
  }
  return resolveInfusionSpeedPrintText(row.speedDisplay, row.speedCode);
}

function ageTextOf(patient: NurseStationModulePatient): string {
  if (patient.age == null) {
    return '';
  }
  return `${patient.age}岁`;
}

/** 入院时间展示（优先 admissionAt，回落入科时间） */
function formatAdmissionTimeText(patient: NurseStationModulePatient): string {
  const raw = str(patient.admissionAt) || str(patient.inWardAt);
  if (!raw) {
    return '';
  }
  const parsed = dayjs(raw);
  if (!parsed.isValid()) {
    return raw;
  }
  return parsed.format('YYYY-MM-DD HH:mm');
}

/** 去掉病房/床后缀，便于拼「301-6床」 */
function stripRoomBedSuffix(raw: string): string {
  return raw.replace(/病房$/u, '').replace(/房$/u, '').replace(/床$/u, '').trim();
}

/**
 * 病房床位展示：仅病房 + 床号，形如「301-6床」。
 * ModulePatient.wardName 为病房名（非病区）；不拼病区。
 */
function formatWardBedText(patient: NurseStationModulePatient): string {
  const room = stripRoomBedSuffix(str(patient.wardName));
  const bed = stripRoomBedSuffix(str(patient.bedNo));
  if (room && bed) {
    return `${room}-${bed}床`;
  }
  if (bed) {
    return `${bed}床`;
  }
  return room;
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

/** 套餐子项名称拼接（检验单「明细」列） */
export function joinPackageChildrenDetailText(
  children?: AdvicePackageChildItemDto[] | null,
): string {
  if (!children?.length) {
    return '';
  }
  return children
    .map((item) => str(item.childrenName))
    .filter((name) => name.length > 0)
    .join('、');
}

/** 申请明细展示名拼接；可按 targetTable 过滤标本/部位 */
export function joinServiceRequestDetailNames(
  details?: null | ServiceRequestDetailSummaryDto[],
  targetTable?: string,
): string {
  if (!details?.length) {
    return '';
  }
  const parts: string[] = [];
  for (const item of details) {
    if (targetTable && str(item.targetTable) !== targetTable) {
      continue;
    }
    const name = str(item.targetDisplayName);
    const remark = str(item.remark);
    let part = name;
    if (name && remark) {
      part = `${name}（${remark}）`;
    } else if (!name) {
      part = remark;
    }
    if (part) {
      parts.push(part);
    }
  }
  return parts.join('、');
}

/** 从扩展行「用法，频次」取用法文案（仅展示用，不参与分支） */
function resolveUsageDisplayText(row: NurseStationOrderExecuteTableRow): string {
  const fromGroup = String(row.groupItems?.[0]?.usage ?? '').trim();
  if (fromGroup) {
    return fromGroup;
  }
  const extension = String(row.extension ?? '').trim();
  if (!extension) {
    return '';
  }
  return extension.split(/[，,]/)[0]?.trim() || extension;
}

/** 查询日对应执行时点文案（瓶贴宽度紧，只印到分钟） */
function resolveExecuteTimeTextForQueryDate(
  row: NurseStationOrderExecuteTableRow,
  queryDate: string,
): string {
  const schedule = (row.planSchedules ?? []).find(
    (item) => String(item.date ?? '').trim() === queryDate,
  );
  const slot = schedule?.slots?.[0] ?? row.planSchedules?.[0]?.slots?.[0];
  const timeLabel = String(slot?.timeLabel ?? slot?.planTime ?? '').trim();
  if (timeLabel && dayjs(timeLabel).isValid()) {
    return dayjs(timeLabel).format('YYYY-MM-DD HH:mm');
  }
  if (timeLabel.includes(':')) {
    // 仅 HH:mm 时拼查询日
    const hhmm = timeLabel.length <= 5 ? timeLabel : timeLabel.slice(0, 5);
    return `${queryDate} ${hhmm}`;
  }
  return `${queryDate} 00:00`;
}

/**
 * 查询日计划槽位数（频次展开结果；rateCode 未回传时作每日次数回落）。
 * 同组取各成员当日槽位的最大个数。
 */
function resolveTimesPerDayFromExecuteRowSlots(
  row: NurseStationOrderExecuteTableRow,
  queryDate: string,
): number {
  let maxSlots = 0;
  const rows = [row, ...(row.mergedMedicationMembers ?? [])];
  for (const item of rows) {
    const schedule = (item.planSchedules ?? []).find(
      (s) => String(s.date ?? '').trim() === queryDate,
    );
    const count = schedule?.slots?.length ?? 0;
    if (count > maxSlots) {
      maxSlots = count;
    }
  }
  return maxSlots > 0 ? maxSlots : 0;
}

/** 执行行剂量展示（与列表「剂量」列同源；瓶贴总量列印此串） */
function resolveExecuteRowDoseDisplayText(
  row: Pick<NurseStationOrderExecuteTableRow, 'dosage' | 'perDoseDisplay'>,
  groupItem?: { dosageDisplay?: string; singleDose?: number | string },
  member?: Pick<NurseStationOrderExecuteTableRow, 'dosage' | 'perDoseDisplay'>,
): string {
  // 瓶贴只印每次量：优先成员/行上的后端 dosageDisplay 原文
  const fromMemberPerDose = String(member?.perDoseDisplay ?? '').trim();
  if (fromMemberPerDose) {
    return fromMemberPerDose;
  }
  const fromRowPerDose = String(row.perDoseDisplay ?? '').trim();
  if (fromRowPerDose) {
    return fromRowPerDose;
  }
  const fromGroup = String(groupItem?.dosageDisplay ?? '').trim();
  if (fromGroup) {
    return fromGroup;
  }
  const fromMember = String(member?.dosage ?? '').trim();
  if (fromMember && fromMember !== '—') {
    return fromMember;
  }
  const fromSingle = String(groupItem?.singleDose ?? '').trim();
  if (fromSingle) {
    return fromSingle;
  }
  const fromRow = String(row.dosage ?? '').trim();
  if (fromRow && fromRow !== '—') {
    return fromRow;
  }
  return '';
}

/** 由列表行组装输液贴药品行（成组用 groupItems / 成员行，无组则单行） */
function buildInfusionMedicationLinesFromExecuteRow(
  row: NurseStationOrderExecuteTableRow,
): NurseStationTreatmentLineDto[] {
  const orderId = String(row.orderId ?? '').trim();
  const groupId = String(row.groupId ?? '').trim() || (orderId ? `r:${orderId}` : '');
  const methodCode = String(row.methodCode ?? '').trim();
  const methodName = resolveUsageDisplayText(row);
  // 组内频次一致；取首条非空 rateCode（与门诊瓶签拆张同源）
  const rateCode =
    String(row.rateCode ?? '').trim() ||
    String(row.mergedMedicationMembers?.[0]?.rateCode ?? '').trim();
  const lines: NurseStationTreatmentLineDto[] = [];

  const groupItems = row.groupItems ?? [];
  const members = row.mergedMedicationMembers ?? [];

  if (groupItems.length > 0) {
    groupItems.forEach((item, index) => {
      const member = members[index];
      const requestId = String(member?.orderId ?? orderId).trim();
      if (!requestId) {
        return;
      }
      const doseText = resolveExecuteRowDoseDisplayText(row, item, member);
      lines.push({
        requestId,
        requestTable: 'MED_REQUEST',
        groupId,
        itemName: String(item.selectedDrugName ?? '').trim(),
        specText: String(item.selectedDrugSpec ?? '').trim(),
        dosageDisplay: doseText,
        methodCode: String(member?.methodCode ?? methodCode).trim() || methodCode,
        methodName: String(item.usage ?? '').trim() || methodName,
        rateCode: String(member?.rateCode ?? rateCode).trim() || rateCode || undefined,
        infusionFlag: true,
        // 住院无发药 quantity；总量列走 dosageDisplay（每次量，按频次拆张后每张一份）
        unitName: '',
        sortNumber: index + 1,
        // 住院执行页按选定日打一张，禁止再按用药天数展开
        dispensePerDuration: 1,
      });
    });
    return lines;
  }

  if (members.length > 0) {
    members.forEach((member, index) => {
      const requestId = String(member.orderId ?? '').trim();
      if (!requestId) {
        return;
      }
      const doseText = resolveExecuteRowDoseDisplayText(member);
      lines.push({
        requestId,
        requestTable: 'MED_REQUEST',
        groupId,
        itemName: String(member.content ?? '').trim(),
        specText: '',
        dosageDisplay: doseText,
        methodCode: String(member.methodCode ?? methodCode).trim() || methodCode,
        methodName: resolveUsageDisplayText(member) || methodName,
        rateCode: String(member.rateCode ?? rateCode).trim() || rateCode || undefined,
        infusionFlag: true,
        unitName: '',
        sortNumber: index + 1,
        dispensePerDuration: 1,
      });
    });
    return lines;
  }

  if (!orderId) {
    return lines;
  }
  const doseText = resolveExecuteRowDoseDisplayText(row);
  lines.push({
    requestId: orderId,
    requestTable: 'MED_REQUEST',
    groupId,
    itemName: String(row.content ?? '').trim(),
    specText: '',
    dosageDisplay: doseText,
    methodCode,
    methodName,
    rateCode: rateCode || undefined,
    infusionFlag: true,
    unitName: '',
    sortNumber: 1,
    dispensePerDuration: 1,
  });
  return lines;
}

/**
 * 用执行页已加载的列表行组装输液贴 PrintSource（零接口请求）。
 */
export function buildInfusionPrintSourceFromExecuteRow(
  row: NurseStationOrderExecuteTableRow,
  patient: NurseStationModulePatient,
  queryDate: string,
): OutpatientInfusionLabelPrintSource {
  const medicationLines = buildInfusionMedicationLinesFromExecuteRow(row);
  const hasRateCode = medicationLines.some((line) => String(line.rateCode ?? '').trim());
  const slotTimesPerDay = resolveTimesPerDayFromExecuteRowSlots(row, queryDate);
  return {
    patientName: str(patient.name),
    genderEnumText: str(patient.gender),
    ageText: ageTextOf(patient),
    // 住院模板标题「入院时间」；field 仍为 registerTimeText（与门诊共用 PrintData）
    registerTimeText: formatAdmissionTimeText(patient),
    wardBedText: formatWardBedText(patient),
    medicationLines,
    // 模板 field=speedCode；值用展示名（同列表「滴速：xx」）
    speedCode: resolveSpeedPrintTextFromExecuteRow(row),
    executorName: '',
    executeTimeText: resolveExecuteTimeTextForQueryDate(row, queryDate),
    // 无 rateCode 时用当日槽位数回落（BID 通常 2 槽 → 2 张）；有 code 则走门诊同源解析
    timesPerDay: !hasRateCode && slotTimesPerDay > 0 ? slotTimesPerDay : undefined,
  };
}

/**
 * 用执行页已加载的列表行组装口服药贴 PrintSource（零接口请求）。
 */
export function buildOralMedLabelPrintSourceFromExecuteRow(
  row: NurseStationOrderExecuteTableRow,
  patient: NurseStationModulePatient,
): InpatientOralMedLabelPrintSource {
  const groupItem = row.groupItems?.[0];
  const drugName = str(groupItem?.selectedDrugName) || str(row.content);
  const specText = str(groupItem?.selectedDrugSpec);
  const doseText = str(row.dosage) || str(groupItem?.dosageDisplay) || str(groupItem?.singleDose);
  const freqText = str(row.frequencyDisplay) || str(groupItem?.frequency);
  return {
    bedNo: str(patient.bedNo),
    patientName: str(patient.name),
    ageText: ageTextOf(patient),
    drugName,
    specText,
    doseText,
    freqText,
  };
}

function buildServiceApplyCommonFromExecuteRow(
  row: NurseStationOrderExecuteTableRow,
  patient: NurseStationModulePatient,
) {
  const deptDisplay = str(row.executeDeptDisplay);
  return {
    hospitalName: resolvePrintHospitalName(),
    inpatientNo: str(patient.encounterBusNo),
    patientName: str(patient.name),
    genderEnumText: str(patient.gender),
    ageText: ageTextOf(patient),
    departmentName: str(patient.regionName) || str(patient.wardName),
    bedNo: str(patient.bedNo),
    diagnosisText: str(patient.diagnosis),
    performOrgName: deptDisplay === '—' ? '' : deptDisplay,
    orderingPractitionerName: str(row.requesterName),
    orderDateText: formatApplyOrderDateText(row.reqOpenedTime),
  };
}

/** 右键：列表行 → 住院检查单 PrintSource（零接口） */
export function buildExamApplyPrintSourceFromExecuteRow(
  row: NurseStationOrderExecuteTableRow,
  patient: NurseStationModulePatient,
): InpatientExamApplyPrintSource {
  const bodySiteText = joinServiceRequestDetailNames(
    row.serviceRequestDetails,
    SERVICE_APPLY_BODY_STRUCTURE_TABLE,
  );
  const remarkText = str(row.remark) || bodySiteText;
  return {
    ...buildServiceApplyCommonFromExecuteRow(row, patient),
    examPurposeText: '',
    itemRows: [
      {
        seqNo: '1',
        itemName: str(row.content),
        quantityText: str(row.dosage) || '1次',
        remarkText,
      },
    ],
  };
}

/** 右键：列表行 → 住院检验单 PrintSource（零接口） */
export function buildLabApplyPrintSourceFromExecuteRow(
  row: NurseStationOrderExecuteTableRow,
  patient: NurseStationModulePatient,
): InpatientLabApplyPrintSource {
  return {
    ...buildServiceApplyCommonFromExecuteRow(row, patient),
    itemRows: [
      {
        seqNo: '1',
        itemName: str(row.content),
        detailText: joinPackageChildrenDetailText(row.packageChildren),
        specimenTypeText: joinServiceRequestDetailNames(
          row.serviceRequestDetails,
          SERVICE_APPLY_SPECIMEN_TABLE,
        ),
        quantityText: str(row.dosage) || '1次',
        remarkText: str(row.remark),
      },
    ],
  };
}
