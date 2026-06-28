import type { ServiceRequestDetailSummaryDto } from '#/api/doctor-station/advice.types';
import type { TerminologyOptionDto } from '#/api/doctor-station/advice.types';
import type { EncounterDiagnosisRowDto } from '#/api/doctor-station/diagnosis.types';
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
import { SERVICE_APPLY_CATEGORY_BY_KIND } from '#/views/outpatient/doctor-station/service-apply/service-apply.constants';
import { buildServiceApplyDiagnosisOptions } from '#/views/outpatient/doctor-station/service-apply/service-apply.service';
import { rowLooksDraftForSign } from '#/views/outpatient/doctor-station/advice/services/advice-row';

import type { ExaminationPrintSource } from './mappers/examination-print';
import type { TestPrintSource } from './mappers/test-print';
import { resolvePrintHospitalName } from './resolve-print-hospital-name';
import { PRINT_SCENARIO, print } from './runtime';

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
  encounterDiagnosisId: string;
  encounterDiagnosisRows: EncounterDiagnosisRowDto[];
  bodyStructureOptions: TerminologyOptionDto[];
  specimenDefinitionOptions: TerminologyOptionDto[];
  printMeta?: ServiceApplyPrintMeta;
}

export interface PrintServiceApplyFromAdviceRowsOptions {
  encounterId: string;
  patientId: string;
  patient: ServiceApplyPatientSummary;
  rows: AdviceRowItem[];
  printMeta?: ServiceApplyPrintMeta;
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
  encounterDiagnosisId: string,
  encounterDiagnosisRows: SchedulePrintServiceApplyFormsOptions['encounterDiagnosisRows'],
): string {
  const selectedId = str(encounterDiagnosisId);
  if (!selectedId) {
    return '';
  }
  const options = buildServiceApplyDiagnosisOptions(encounterDiagnosisRows);
  const hit = options.find((item) => item.value === selectedId);
  return hit?.label ?? '';
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
  const table = str(row.serviceRequestDetails?.[0]?.targetTable);
  if (table === SERVICE_APPLY_SPECIMEN_TABLE) {
    return 'lab';
  }
  if (table === SERVICE_APPLY_BODY_STRUCTURE_TABLE) {
    return 'exam';
  }
  return null;
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
  };
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
      options.encounterDiagnosisId,
      options.encounterDiagnosisRows,
    ),
    performOrgName: str(options.performOrgName),
    lines: [...options.items],
    bodyStructureOptions: options.bodyStructureOptions,
    specimenDefinitionOptions: options.specimenDefinitionOptions,
  };
}

/**
 * 同一申请单多项目合并一张打印（检查 examination-print / 检验 test-print）。
 */
export async function printServiceApplyForms(options: SchedulePrintServiceApplyFormsOptions) {
  if (options.items.length === 0) {
    return [];
  }
  const scenarioCode =
    options.applyKind === 'lab' ? PRINT_SCENARIO.testPrint : PRINT_SCENARIO.examinationPrint;
  const jobName = options.applyKind === 'lab' ? '检验申请单' : '检查申请单';
  const encounterId = str(options.encounterId);
  const patientId = str(options.patientId);
  const prescriptionNo = str(options.prescriptionNo);
  const performOrgId = str(options.performOrgId);
  const source = buildFormPrintSource(options);
  const result = await print({
    scenarioCode,
    source,
    bizRef: {
      encounterId,
      patientId,
      prescriptionNo,
      performOrgId,
    },
    jobName,
    selectPrinter: 'auto',
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
    const scenarioCode =
      applyKind === 'lab' ? PRINT_SCENARIO.testPrint : PRINT_SCENARIO.examinationPrint;
    const jobName = applyKind === 'lab' ? '检验申请单' : '检查申请单';
    const headRow = groupRows[0];
    const diagnosisSummary = str(headRow.diagnosisSummary);
    const lines = groupRows.map((row) => adviceRowToSelectedItem(row));
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
        encounterId,
        patientId,
        prescriptionNo,
        performOrgId: str(headRow.performOrgId),
      },
      jobName,
      selectPrinter: 'auto',
    });
    results.push(result);
  }
  return results;
}
