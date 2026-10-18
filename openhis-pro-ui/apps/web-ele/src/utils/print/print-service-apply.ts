import type { ServiceRequestDetailSummaryDto } from '#/api/doctor-station/advice.types';
import type { TerminologyOptionDto } from '#/api/doctor-station/advice.types';
import type { EncounterDiagnosisRowDto } from '#/api/doctor-station/diagnosis.types';
import type { NurseStationTreatmentLineDto } from '#/api/outpatient-nurse-station/outpatient-nurse-station.types';
import type { AdviceRowItem } from '#/views/outpatient/doctor-station/advice/services/advice.types';
import type {
  ServiceApplyKind,
  ServiceApplyPatientSummary,
  ServiceApplySelectedItem,
} from '#/views/outpatient/doctor-station/service-apply/service-apply.types';

import {
  SERVICE_APPLY_BODY_STRUCTURE_TABLE,
  SERVICE_APPLY_SPECIMEN_TABLE,
} from '#/utils/service-apply-display';
import {
  SERVICE_APPLY_CATEGORY_BY_KIND,
  SERVICE_APPLY_KIND_LABEL,
} from '#/views/outpatient/doctor-station/service-apply/service-apply.constants';
import { buildServiceApplyDiagnosisOptions } from '#/views/outpatient/doctor-station/service-apply/service-apply.service';
import { rowLooksDraftForSign } from '#/views/outpatient/doctor-station/advice/services/advice-row';

import type { ExaminationPrintSource } from './mappers/examination-print';
import type { TestPrintSource } from './mappers/test-print';
import type { ServiceApplyPaperSize } from './service-apply-paper-preference';
import {
  SERVICE_CATEGORY_PROOF,
  SERVICE_CATEGORY_TEST,
  SERVICE_CATEGORY_TREATMENT,
} from './constants/service-request-category-codes';
import { expandActivityDefinitionChildrenForPrint } from './expand-activity-definition-children-for-print';
import { normalizeServiceApplyPrintJson } from './normalize-service-apply-print-json';
import {
  isBloodCollectionFeePrintItem,
  isVenousBloodCollectionPrintItem,
} from './nurse-station-print-targets';
import { resolvePrintHospitalName } from './resolve-print-hospital-name';
import { PRINT_SCENARIO, print } from './runtime';
import { PRINT_ERROR_CODE } from './runtime/types';
import {
  getServiceApplyPaperSizePreference,
  SERVICE_APPLY_PAPER_SIZE,
} from './service-apply-paper-preference';

/** 用户在选机/纸张弹窗点叉或取消后，中断后续批量打印 */
function isPrintUserCancelled(result: { errorCode?: string; ok: boolean }): boolean {
  return !result.ok && result.errorCode === PRINT_ERROR_CODE.USER_CANCELLED;
}

/** 申请单整单号前缀（与后端 AggregateTypeConstants.REQUISITION_APPLICATION 一致） */
const APPLY_FORM_NO_PREFIX = 'AR';

export interface ServiceApplyPrintMeta {
  departmentName?: string;
  hospitalName?: string;
  orderingPractitionerName?: string;
}

export interface SchedulePrintServiceApplyFormsOptions {
  applyKind: ServiceApplyKind;
  encounterId: string;
  patientId: string;
  prescriptionNo: string;
  patient: ServiceApplyPatientSummary;
  items: ServiceApplySelectedItem[];
  performOrgId: string;
  performOrgName: string;
  encounterDiagnosisIds: string[];
  encounterDiagnosisRows: EncounterDiagnosisRowDto[];
  bodyStructureOptions: TerminologyOptionDto[];
  specimenDefinitionOptions: TerminologyOptionDto[];
  printMeta?: ServiceApplyPrintMeta;
  /** 申请单纸张；缺省时读本地偏好，并在打印机弹窗中可选 */
  paperSize?: ServiceApplyPaperSize;
  /** 为 false 时打印机弹窗不展示纸张（用 paperSize 或默认 A4） */
  askPaperSize?: boolean;
}

export interface PrintServiceApplyFromAdviceRowsOptions {
  encounterId: string;
  patientId: string;
  patient: ServiceApplyPatientSummary;
  rows: AdviceRowItem[];
  printMeta?: ServiceApplyPrintMeta;
  /** 申请单纸张；缺省时读本地偏好，并在打印机弹窗中可选 */
  paperSize?: ServiceApplyPaperSize;
  /** 为 false 时打印机弹窗不展示纸张（用 paperSize 或默认 A4） */
  askPaperSize?: boolean;
}

/** 门诊护士站：按患者处置单诊疗行直打检查/检验/治疗申请单 */
export interface PrintServiceApplyFromNurseTreatmentLinesOptions {
  applyKind: ServiceApplyKind;
  encounterId: string;
  patientId?: string;
  patient: ServiceApplyPatientSummary;
  /** 当前就诊处置单 serviceLines（不按勾选过滤，按类目全打） */
  serviceLines: NurseStationTreatmentLineDto[];
  printMeta?: ServiceApplyPrintMeta;
  /** 开单日期展示（缺省取当天） */
  orderDateText?: string;
  /**
   * 导诊台：同一检查/检验/治疗类目且同一执行科室合并一张。
   * 护士站默认仍按 AR 单号合单，无 AR 则按 requestId 逐张。
   */
  mergeByApplyKind?: boolean;
  /** 申请单纸张；缺省时读本地偏好，并在打印机弹窗中可选 */
  paperSize?: ServiceApplyPaperSize;
  /** 为 false 时打印机弹窗不展示纸张（用 paperSize 或默认 A4） */
  askPaperSize?: boolean;
}

function str(value: null | number | string | undefined): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value).trim();
}

function formatServiceApplyOrderDateText(): string {
  const now = new Date();
  const year = now.getFullYear();
  const month = `${now.getMonth() + 1}`.padStart(2, '0');
  const day = `${now.getDate()}`.padStart(2, '0');
  return `${year}年${month}月${day}日`;
}

function formatAdviceStartTimeToOrderDateText(startTime: string): string {
  const trimmed = str(startTime);
  if (!trimmed) {
    return formatServiceApplyOrderDateText();
  }
  const match = trimmed.match(/^(\d{4})-(\d{2})-(\d{2})/);
  if (match) {
    return `${match[1]}年${match[2]}月${match[3]}日`;
  }
  return trimmed;
}

function resolveDiagnosisText(
  encounterDiagnosisIds: string[],
  encounterDiagnosisRows: SchedulePrintServiceApplyFormsOptions['encounterDiagnosisRows'],
): string {
  const selectedIds = (encounterDiagnosisIds ?? []).map((id) => str(id)).filter(Boolean);
  if (selectedIds.length === 0) {
    return '';
  }
  const options = buildServiceApplyDiagnosisOptions(encounterDiagnosisRows);
  return selectedIds
    .map((id) => options.find((item) => item.value === id)?.label ?? '')
    .filter(Boolean)
    .join('、');
}

function formatServiceRequestDetailDisplayText(
  details: ServiceRequestDetailSummaryDto[] | null | undefined,
): string {
  if (!details?.length) {
    return '';
  }
  return details
    .map((item) => {
      const name = str(item.targetDisplayName);
      const remark = str(item.remark);
      if (name && remark) {
        return `${name}（${remark}）`;
      }
      return name || remark;
    })
    .filter((part) => part.length > 0)
    .join('、');
}

function inferServiceApplyKindFromAdviceRow(row: AdviceRowItem): ServiceApplyKind | null {
  const code = str(row.catalogCategoryCode).toUpperCase();
  if (code === SERVICE_APPLY_CATEGORY_BY_KIND.lab || code === '22') {
    return 'lab';
  }
  if (code === SERVICE_APPLY_CATEGORY_BY_KIND.exam || code === '23') {
    return 'exam';
  }
  if (code === SERVICE_APPLY_CATEGORY_BY_KIND.treatment || code === '21') {
    return 'treatment';
  }
  const table = str(row.serviceRequestDetails?.[0]?.targetTable);
  if (table === SERVICE_APPLY_SPECIMEN_TABLE) {
    return 'lab';
  }
  if (table === SERVICE_APPLY_BODY_STRUCTURE_TABLE) {
    return 'exam';
  }
  return null;
}

/** 打印场景：检验/检查按纸张选 A4 或 A5 模板；治疗复用检查场景 */
function resolveServiceApplyPrintScenario(
  applyKind: ServiceApplyKind,
  paperSize: ServiceApplyPaperSize,
) {
  const useA5 = paperSize === SERVICE_APPLY_PAPER_SIZE.a5;
  if (applyKind === 'lab') {
    return {
      scenarioCode: useA5 ? PRINT_SCENARIO.testPrintA5 : PRINT_SCENARIO.testPrint,
      jobName: `${SERVICE_APPLY_KIND_LABEL.lab}申请单`,
    };
  }
  return {
    scenarioCode: useA5 ? PRINT_SCENARIO.examinationPrintA5 : PRINT_SCENARIO.examinationPrint,
    jobName: `${SERVICE_APPLY_KIND_LABEL[applyKind]}申请单`,
  };
}

/** 解析初始纸张：显式入参 > 本地偏好 > A4（实际确认在打印机弹窗） */
function resolveInitialServiceApplyPaperSize(options: {
  paperSize?: ServiceApplyPaperSize;
  askPaperSize?: boolean;
}): ServiceApplyPaperSize {
  if (options.paperSize) {
    return options.paperSize;
  }
  if (options.askPaperSize === false) {
    return SERVICE_APPLY_PAPER_SIZE.a4;
  }
  return getServiceApplyPaperSizePreference();
}

function adviceRowToSelectedItem(row: AdviceRowItem): ServiceApplySelectedItem {
  const details = row.serviceRequestDetails ?? [];
  return {
    rowKey: row.key,
    catalogRowKey: '',
    activityDefinitionId: str(row.productDefinitionId),
    activityName: str(row.adviceDisplayName) || str(row.content),
    quantity: row.listQuantity ?? 1,
    unitCode: str(row.listUnitCode),
    unitPrice: 0,
    remark: row.remark,
    detailItems: details
      .map((detail) => ({
        targetTable: str(detail.targetTable),
        targetId: str(detail.targetId),
        remark: str(detail.remark) || undefined,
      }))
      .filter((detail) => detail.targetTable && detail.targetId),
    packageChildren: row.packageChildren,
  };
}

/**
 * 国基业务打印：仅检验申请单在多子项时拆成子项行；单子项仍打父项开立名；检查/治疗仍打父项。
 *
 * @param items 申请单已选行（页面仍是父项）
 * @param applyKind 申请种类
 * @return 打印用行
 */
function expandServiceApplyItemsForPrint(
  items: ServiceApplySelectedItem[],
  applyKind?: ServiceApplyKind,
): ServiceApplySelectedItem[] {
  if (applyKind !== 'lab') {
    return items;
  }
  return expandActivityDefinitionChildrenForPrint(
    items,
    (item) => item.packageChildren,
    (item, child, childIndex) => {
      const childName = str(child.childrenName);
      const childId = str(child.childrenId);
      let quantity = item.quantity;
      if (child.childrenNum !== null && child.childrenNum !== undefined && child.childrenNum > 0) {
        quantity = item.quantity * child.childrenNum;
      }
      return {
        ...item,
        rowKey: `${item.rowKey}-child-${childId || String(childIndex)}`,
        activityDefinitionId: childId || item.activityDefinitionId,
        activityName: childName || item.activityName,
        quantity,
      };
    },
  );
}

function buildFormPrintSource(
  options: SchedulePrintServiceApplyFormsOptions,
): ExaminationPrintSource & TestPrintSource {
  const meta = options.printMeta ?? {};
  return {
    applyKind: options.applyKind,
    hospitalName: str(meta.hospitalName) || resolvePrintHospitalName(),
    patientName: str(options.patient.patientName),
    genderEnumText: str(options.patient.genderText),
    ageText: str(options.patient.ageText),
    encounterNo: str(options.patient.encounterNo),
    departmentName: str(meta.departmentName),
    bedNo: '',
    orderingPractitionerName: str(meta.orderingPractitionerName),
    orderDateText: formatServiceApplyOrderDateText(),
    diagnosisText: resolveDiagnosisText(
      options.encounterDiagnosisIds,
      options.encounterDiagnosisRows,
    ),
    performOrgName: str(options.performOrgName),
    lines: [...options.items],
    bodyStructureOptions: options.bodyStructureOptions,
    specimenDefinitionOptions: options.specimenDefinitionOptions,
  };
}

/**
 * 同一申请单多项目合并一张打印（检查/治疗 / 检验；纸张 A4 或 A5）。
 * 纸张与打印机在同一弹窗选择（已记住则可跳过）。
 */
export async function printServiceApplyForms(options: SchedulePrintServiceApplyFormsOptions) {
  if (options.items.length === 0) {
    return [];
  }
  const paperSize = resolveInitialServiceApplyPaperSize(options);
  const showServiceApplyPaperSize = options.askPaperSize !== false && !options.paperSize;
  const { scenarioCode, jobName } = resolveServiceApplyPrintScenario(options.applyKind, paperSize);
  const encounterId = str(options.encounterId);
  const patientId = str(options.patientId);
  const prescriptionNo = str(options.prescriptionNo);
  const performOrgId = str(options.performOrgId);
  const source = buildFormPrintSource({
    ...options,
    items: expandServiceApplyItemsForPrint(options.items, options.applyKind),
  });
  const result = await print({
    scenarioCode,
    source,
    bizRef: {
      scenarioCode,
      encounterId,
      patientId,
      prescriptionNo,
      performOrgId,
      paperSize,
    },
    jobName,
    selectPrinter: 'auto',
    showServiceApplyPaperSize,
    serviceApplyPaperSize: paperSize,
    transformPrintJson: (printJson) => normalizeServiceApplyPrintJson(printJson),
  });
  return [result];
}

/** 开立医嘱列表：按申请单整单合并打印（同一 AR 单号多行 → 一张） */
export async function printServiceApplyFromAdviceRows(
  options: PrintServiceApplyFromAdviceRowsOptions,
) {
  const encounterId = str(options.encounterId);
  const patientId = str(options.patientId);
  const meta = options.printMeta ?? {};
  const printableRows = options.rows.filter(
    (row) => row.applyFormNo?.trim() && !rowLooksDraftForSign(row),
  );
  if (printableRows.length === 0) {
    return [];
  }

  let paperSize = resolveInitialServiceApplyPaperSize(options);
  const showServiceApplyPaperSize = options.askPaperSize !== false && !options.paperSize;

  const formGroups = new Map<string, AdviceRowItem[]>();
  for (const row of printableRows) {
    const formNo = str(row.applyFormNo);
    const bucket = formGroups.get(formNo);
    if (bucket) {
      bucket.push(row);
    } else {
      formGroups.set(formNo, [row]);
    }
  }

  const results = [];
  for (const [prescriptionNo, groupRows] of formGroups) {
    const applyKind = inferServiceApplyKindFromAdviceRow(groupRows[0]);
    if (!applyKind) {
      continue;
    }
    // 批量打印：上一张弹窗改过纸张时，按最新偏好对齐场景
    if (showServiceApplyPaperSize) {
      paperSize = getServiceApplyPaperSizePreference();
    }
    const { scenarioCode, jobName } = resolveServiceApplyPrintScenario(applyKind, paperSize);
    const headRow = groupRows[0];
    const diagnosisSummary = str(headRow.diagnosisSummary);
    const lines = expandServiceApplyItemsForPrint(
      groupRows.map((row) => adviceRowToSelectedItem(row)),
      applyKind,
    );
    const serviceRequestIds = [
      ...new Set(groupRows.map((row) => str(row.requestId)).filter((id) => id.length > 0)),
    ];
    const shared: ExaminationPrintSource & TestPrintSource = {
      applyKind,
      hospitalName: str(meta.hospitalName) || resolvePrintHospitalName(),
      patientName: str(options.patient.patientName),
      genderEnumText: str(options.patient.genderText),
      ageText: str(options.patient.ageText),
      encounterNo: str(options.patient.encounterNo),
      departmentName: str(meta.departmentName),
      bedNo: '',
      orderingPractitionerName: str(meta.orderingPractitionerName) || str(headRow.orderingDoctor),
      orderDateText: formatAdviceStartTimeToOrderDateText(headRow.startTime),
      diagnosisText: diagnosisSummary === '—' ? '' : diagnosisSummary,
      performOrgName: str(headRow.performOrgName),
      lines,
      lineDetailDisplayTexts: groupRows.map((row) =>
        formatServiceRequestDetailDisplayText(row.serviceRequestDetails),
      ),
    };
    const result = await print({
      scenarioCode,
      source: shared,
      bizRef: {
        scenarioCode,
        encounterId,
        patientId,
        prescriptionNo,
        performOrgId: str(headRow.performOrgId),
        serviceRequestIds,
        paperSize,
      },
      jobName,
      selectPrinter: 'auto',
      showServiceApplyPaperSize,
      serviceApplyPaperSize: paperSize,
      transformPrintJson: (printJson) => normalizeServiceApplyPrintJson(printJson),
    });
    results.push(result);
    if (isPrintUserCancelled(result)) {
      break;
    }
  }
  return results;
}

/** 申请种类 → 处置单 serviceCategoryCode（21 治疗 / 22 检验 / 23 检查） */
function resolveServiceCategoryCodeByApplyKind(applyKind: ServiceApplyKind): string {
  if (applyKind === 'lab') {
    return SERVICE_CATEGORY_PROOF;
  }
  if (applyKind === 'exam') {
    return SERVICE_CATEGORY_TEST;
  }
  return SERVICE_CATEGORY_TREATMENT;
}

/** 是否 AR 前缀申请单整单号 */
function isApplyFormPrescriptionNo(prescriptionNo?: null | string): boolean {
  const value = str(prescriptionNo);
  return value.length > 0 && value.startsWith(APPLY_FORM_NO_PREFIX);
}

/** 申请单打印分组：导诊按类目+执行科室一张；护士站同 AR 一张，无 AR 则按 requestId 一张 */
function resolveNurseServiceApplyPrintGroupKey(
  line: NurseStationTreatmentLineDto,
  options: Pick<
    PrintServiceApplyFromNurseTreatmentLinesOptions,
    'applyKind' | 'mergeByApplyKind'
  >,
): string {
  if (options.mergeByApplyKind) {
    const orgKey = str(line.performOrgId) || 'none';
    return `kind:${options.applyKind}:org:${orgKey}`;
  }
  if (isApplyFormPrescriptionNo(line.prescriptionNo)) {
    return str(line.prescriptionNo);
  }
  return `sr:${str(line.requestId)}`;
}

/** 退费中/已退费行不打申请单 */
function isRefundedNurseTreatmentLine(line: NurseStationTreatmentLineDto): boolean {
  const display = str(line.displayStatusEnum).toUpperCase();
  if (display === 'REFUNDED' || display === 'REFUNDING') {
    return true;
  }
  const charge = str(line.chargeStatusEnum).toUpperCase();
  return charge === 'REFUNDED' || charge === 'REFUNDING';
}

/**
 * 护士站处置单中可打申请单的诊疗行（与打印过滤一致）。
 * 有 requestId、类目匹配、非退费；检验排除采血费；静脉采血并入检验单、不打治疗单。
 */
export function listPrintableNurseServiceApplyLines(
  serviceLines: NurseStationTreatmentLineDto[] | null | undefined,
  applyKind: ServiceApplyKind,
): NurseStationTreatmentLineDto[] {
  const categoryCode = resolveServiceCategoryCodeByApplyKind(applyKind);
  return (serviceLines ?? []).filter((line) => {
    if (!str(line.requestId)) {
      return false;
    }
    if (isRefundedNurseTreatmentLine(line)) {
      return false;
    }
    if (isVenousBloodCollectionPrintItem(line.itemName)) {
      return applyKind === 'lab';
    }
    if (str(line.serviceCategoryCode) !== categoryCode) {
      return false;
    }
    if (applyKind === 'lab' && isBloodCollectionFeePrintItem(line.itemName)) {
      return false;
    }
    return true;
  });
}

/** 当前患者是否有可打印的检查/检验/治疗申请单 */
export function hasPrintableNurseServiceApply(
  serviceLines: NurseStationTreatmentLineDto[] | null | undefined,
  applyKind: ServiceApplyKind,
): boolean {
  return listPrintableNurseServiceApplyLines(serviceLines, applyKind).length > 0;
}

/** 处置单诊疗行 → 申请单打印行 */
function nurseTreatmentLineToSelectedItem(
  line: NurseStationTreatmentLineDto,
): ServiceApplySelectedItem {
  const details = line.serviceRequestDetails ?? [];
  const quantityRaw = line.quantity;
  let quantity = 1;
  if (quantityRaw !== null && quantityRaw !== undefined && String(quantityRaw).trim() !== '') {
    const n = typeof quantityRaw === 'number' ? quantityRaw : Number(String(quantityRaw).trim());
    if (Number.isFinite(n) && n > 0) {
      quantity = n;
    }
  }
  return {
    rowKey: str(line.requestId) || `sr-${str(line.itemName)}`,
    catalogRowKey: '',
    activityDefinitionId: str(line.activityDefinitionId),
    activityName: str(line.itemName),
    quantity,
    executionCount: 1,
    unitCode: str(line.unitCode),
    unitPrice: 0,
    remark: str(line.remark) || undefined,
    detailItems: details
      .map((detail) => ({
        targetTable: str(detail.targetTable),
        targetId: str(detail.targetId),
        remark: str(detail.remark) || undefined,
      }))
      .filter((detail) => detail.targetTable && detail.targetId),
    packageChildren: line.packageChildren ?? undefined,
  };
}

/**
 * 门诊护士站：按患者处置单直打检查/检验/治疗申请单。
 * 不按勾选过滤；同 AR 单号多行合并一张；无 AR 时按 requestId 逐张。
 * 导诊台传 mergeByApplyKind 时，同类目且同一执行科室合并一张，单上带执行科室。
 */
export async function printServiceApplyFromNurseTreatmentLines(
  options: PrintServiceApplyFromNurseTreatmentLinesOptions,
) {
  const encounterId = str(options.encounterId);
  const patientId = str(options.patientId);
  const meta = options.printMeta ?? {};
  const kindLabel = SERVICE_APPLY_KIND_LABEL[options.applyKind];
  const printableLines = listPrintableNurseServiceApplyLines(
    options.serviceLines,
    options.applyKind,
  );
  if (printableLines.length === 0) {
    return [];
  }

  let paperSize = resolveInitialServiceApplyPaperSize(options);
  const showServiceApplyPaperSize = options.askPaperSize !== false && !options.paperSize;
  const fallbackOrderDateText = formatAdviceStartTimeToOrderDateText(options.orderDateText ?? '');

  const formGroups = new Map<string, NurseStationTreatmentLineDto[]>();
  for (const line of printableLines) {
    const formNo = resolveNurseServiceApplyPrintGroupKey(line, options);
    const bucket = formGroups.get(formNo);
    if (bucket) {
      bucket.push(line);
    } else {
      formGroups.set(formNo, [line]);
    }
  }

  const results = [];
  for (const [groupKey, groupLines] of formGroups) {
    if (showServiceApplyPaperSize) {
      paperSize = getServiceApplyPaperSizePreference();
    }
    const { scenarioCode, jobName } = resolveServiceApplyPrintScenario(
      options.applyKind,
      paperSize,
    );
    const headLine = groupLines[0];
    if (!headLine) {
      continue;
    }
    const prescriptionNo = isApplyFormPrescriptionNo(headLine.prescriptionNo)
      ? str(headLine.prescriptionNo)
      : '';
    const lines = expandServiceApplyItemsForPrint(
      groupLines.map((line) => nurseTreatmentLineToSelectedItem(line)),
      options.applyKind,
    );
    const serviceRequestIds = [
      ...new Set(groupLines.map((line) => str(line.requestId)).filter((id) => id.length > 0)),
    ];
    const shared: ExaminationPrintSource & TestPrintSource = {
      applyKind: options.applyKind,
      hospitalName: str(meta.hospitalName) || resolvePrintHospitalName(),
      patientName: str(options.patient.patientName),
      genderEnumText: str(options.patient.genderText),
      ageText: str(options.patient.ageText),
      encounterNo: str(options.patient.encounterNo),
      departmentName: str(meta.departmentName),
      bedNo: '',
      orderingPractitionerName: str(meta.orderingPractitionerName),
      orderDateText: fallbackOrderDateText,
      diagnosisText: '',
      performOrgName: str(headLine.performOrgName),
      lines,
      lineDetailDisplayTexts: groupLines.map((line) =>
        formatServiceRequestDetailDisplayText(line.serviceRequestDetails),
      ),
    };
    const result = await print({
      scenarioCode,
      source: shared,
      bizRef: {
        scenarioCode,
        encounterId,
        patientId,
        prescriptionNo: prescriptionNo || groupKey,
        serviceRequestIds,
        paperSize,
      },
      jobName: jobName || `${kindLabel}申请单`,
      selectPrinter: 'auto',
      showServiceApplyPaperSize,
      serviceApplyPaperSize: paperSize,
      transformPrintJson: (printJson) => normalizeServiceApplyPrintJson(printJson),
    });
    results.push(result);
    if (isPrintUserCancelled(result)) {
      break;
    }
  }
  return results;
}
