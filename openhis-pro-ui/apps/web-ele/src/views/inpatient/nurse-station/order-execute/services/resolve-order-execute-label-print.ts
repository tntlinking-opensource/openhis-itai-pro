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

function ageTextOf(patient: NurseStationModulePatient): string {
  if (patient.age == null) {
    return '';
  }
  return `${patient.age}岁`;
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

/** 查询日对应执行时点文案 */
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
    return dayjs(timeLabel).format('YYYY-MM-DD HH:mm:ss');
  }
  if (timeLabel.length >= 8 && timeLabel.includes(':')) {
    // 仅 HH:mm 时拼查询日
    const hhmm = timeLabel.length <= 5 ? `${timeLabel}:00` : timeLabel;
    return `${queryDate} ${hhmm}`;
  }
  return `${queryDate} 00:00:00`;
}

/** 由列表行组装输液贴药品行（成组用 groupItems / 成员行，无组则单行） */
function buildInfusionMedicationLinesFromExecuteRow(
  row: NurseStationOrderExecuteTableRow,
): NurseStationTreatmentLineDto[] {
  const orderId = String(row.orderId ?? '').trim();
  const groupId = String(row.groupId ?? '').trim() || (orderId ? `r:${orderId}` : '');
  const methodCode = String(row.methodCode ?? '').trim();
  const methodName = resolveUsageDisplayText(row);
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
      lines.push({
        requestId,
        requestTable: 'MED_REQUEST',
        groupId,
        itemName: String(item.selectedDrugName ?? '').trim(),
        specText: String(item.selectedDrugSpec ?? '').trim(),
        dosageDisplay:
          String(item.dosageDisplay ?? '').trim() ||
          String(member?.dosage ?? item.singleDose ?? '').trim(),
        methodCode: String(member?.methodCode ?? methodCode).trim() || methodCode,
        methodName: String(item.usage ?? '').trim() || methodName,
        infusionFlag: true,
        quantity: 1,
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
      lines.push({
        requestId,
        requestTable: 'MED_REQUEST',
        groupId,
        itemName: String(member.content ?? '').trim(),
        specText: '',
        dosageDisplay: String(member.dosage ?? '').trim(),
        methodCode: String(member.methodCode ?? methodCode).trim() || methodCode,
        methodName: resolveUsageDisplayText(member) || methodName,
        infusionFlag: true,
        quantity: 1,
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
  lines.push({
    requestId: orderId,
    requestTable: 'MED_REQUEST',
    groupId,
    itemName: String(row.content ?? '').trim(),
    specText: '',
    dosageDisplay: String(row.dosage ?? '').trim(),
    methodCode,
    methodName,
    infusionFlag: true,
    quantity: 1,
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
  return {
    patientName: str(patient.name),
    genderEnumText: str(patient.gender),
    ageText: ageTextOf(patient),
    registerTimeText: `${queryDate} 00:00:00`,
    medicationLines,
    executorName: '',
    executeTimeText: resolveExecuteTimeTextForQueryDate(row, queryDate),
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
  return {
    bedNo: str(patient.bedNo),
    patientName: str(patient.name),
    ageText: ageTextOf(patient),
    drugName,
    specText,
    doseText,
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
