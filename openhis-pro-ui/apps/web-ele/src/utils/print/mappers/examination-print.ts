import type { TerminologyOptionDto } from '#/api/doctor-station/advice.types';
import type {
  ServiceApplyKind,
  ServiceApplySelectedItem,
} from '#/views/outpatient/doctor-station/service-apply/service-apply.types';

import { appendRehabTreatmentCourseRemark } from '#/utils/service-apply-display';
import { SERVICE_APPLY_KIND_LABEL } from '#/views/outpatient/doctor-station/service-apply/service-apply.constants';
import { detailTargetIds } from '#/views/outpatient/doctor-station/service-apply/service-apply.service';

import {
  type ServiceApplyPrintItemLine,
  toServiceApplyItemLine,
} from '../service-apply-print-items';

/** 检查/治疗申请单 PrintData（与 examination-print A5 共用模板 field 一致） */
export interface ExaminationPrintData {
  documentTitle: string;
  hospitalName: string;
  patientName: string;
  genderEnumText: string;
  ageText: string;
  encounterNo: string;
  departmentName: string;
  bedNo: string;
  orderingPractitionerName: string;
  orderDateText: string;
  diagnosisText: string;
  /** 项目区：每项名称 + 部位，纵向排列，一列排满再向右开列 */
  applyItemLines: ServiceApplyPrintItemLine[];
  serviceItemNameText: string;
  /** 部位/标本（检查=部位，治疗=部位，共用模板字段） */
  detailText: string;
  precautionText: string;
  resultText: string;
  performDepartmentName: string;
  practitionerSignName: string;
  /** 兼容旧字段名（模板已改用 detailText / performDepartmentName） */
  examSiteText: string;
  examResultText: string;
  examDepartmentName: string;
}

/** 检查申请单 mapper 入参（P：门诊医生站申请弹窗签发后） */
export interface ExaminationPrintSource {
  applyKind: ServiceApplyKind;
  hospitalName?: string;
  patientName?: string;
  genderEnumText?: string;
  ageText?: string;
  encounterNo?: string;
  departmentName?: string;
  bedNo?: string;
  orderingPractitionerName?: string;
  orderDateText?: string;
  diagnosisText?: string;
  performOrgName?: string;
  /** 单项目（与 lines 二选一，lines 优先） */
  line?: ServiceApplySelectedItem;
  /** 同一申请单多项目 */
  lines?: ServiceApplySelectedItem[];
  /** 列表打印：与 lines 下标对应的部位/样本文案 */
  lineDetailDisplayTexts?: string[];
  bodyStructureOptions?: TerminologyOptionDto[];
  specimenDefinitionOptions?: TerminologyOptionDto[];
  /** 列表打印等场景：已解析的部位文案，优先于术语树（仅单 line 时生效） */
  detailDisplayText?: string;
}

function str(value: null | number | string | undefined): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value).trim();
}

function findTerminologyLabel(options: TerminologyOptionDto[], targetId: string): string {
  for (const opt of options) {
    const value = String(opt.value ?? '').trim();
    if (value === targetId) {
      return str(opt.label) || value;
    }
    if (opt.children?.length) {
      const nested = findTerminologyLabel(opt.children, targetId);
      if (nested) {
        return nested;
      }
    }
  }
  return '';
}

function resolveDetailDisplayText(
  applyKind: ServiceApplyKind,
  line: ServiceApplySelectedItem,
  bodyStructureOptions: TerminologyOptionDto[],
  specimenDefinitionOptions: TerminologyOptionDto[],
): string {
  const ids = detailTargetIds(line.detailItems ?? []);
  const options = applyKind === 'lab' ? specimenDefinitionOptions : bodyStructureOptions;
  const labels = ids
    .map((id) => findTerminologyLabel(options, id))
    .filter((label) => label.length > 0);
  return labels.join('、');
}

function resolveSourceLines(source: ExaminationPrintSource): ServiceApplySelectedItem[] {
  if (source.lines?.length) {
    return source.lines;
  }
  if (source.line) {
    return [source.line];
  }
  return [];
}

function buildLineExamSiteText(
  source: ExaminationPrintSource,
  line: ServiceApplySelectedItem,
  lineIndex: number,
): string {
  const preset = str(source.lineDetailDisplayTexts?.[lineIndex]);
  if (preset) {
    return preset;
  }
  if (source.lines?.length) {
    return resolveDetailDisplayText(
      source.applyKind,
      line,
      source.bodyStructureOptions ?? [],
      source.specimenDefinitionOptions ?? [],
    );
  }
  if (lineIndex === 0 && str(source.detailDisplayText)) {
    return str(source.detailDisplayText);
  }
  return resolveDetailDisplayText(
    source.applyKind,
    line,
    source.bodyStructureOptions ?? [],
    source.specimenDefinitionOptions ?? [],
  );
}

function joinUniqueNonEmpty(parts: string[], separator = '\n'): string {
  const seen = new Set<string>();
  const ordered: string[] = [];
  for (const part of parts) {
    const text = str(part);
    if (!text || seen.has(text)) {
      continue;
    }
    seen.add(text);
    ordered.push(text);
  }
  return ordered.join(separator);
}

function resolveDocumentTitle(applyKind: ServiceApplyKind): string {
  return `${SERVICE_APPLY_KIND_LABEL[applyKind] || '检查'}申请单`;
}

/** 检查/治疗申请单行 → PrintData（支持同一申请单多项目合并一张） */
export function buildExaminationPrintData(source: ExaminationPrintSource): ExaminationPrintData {
  const orderingPractitionerName = str(source.orderingPractitionerName);
  const lines = resolveSourceLines(source);
  const applyItemLines = lines
    .map((line, index) =>
      toServiceApplyItemLine(
        source.applyKind,
        str(line.activityName),
        buildLineExamSiteText(source, line, index),
      ),
    )
    .filter((line): line is ServiceApplyPrintItemLine => line !== null);
  const serviceItemNameText = joinUniqueNonEmpty(lines.map((line) => line.activityName));
  const detailText = joinUniqueNonEmpty(
    lines.map((line, index) => buildLineExamSiteText(source, line, index)),
  );
  const lineRemarkText = joinUniqueNonEmpty(lines.map((line) => line.remark));
  // 治疗申请单且执行科室展示为康复科时，追加固定疗程说明
  const precautionText = appendRehabTreatmentCourseRemark(
    lineRemarkText,
    source.applyKind,
    source.performOrgName,
  );
  const performDepartmentName = str(source.performOrgName);
  return {
    documentTitle: resolveDocumentTitle(source.applyKind),
    hospitalName: str(source.hospitalName),
    patientName: str(source.patientName),
    genderEnumText: str(source.genderEnumText),
    ageText: str(source.ageText),
    encounterNo: str(source.encounterNo),
    departmentName: str(source.departmentName),
    bedNo: str(source.bedNo),
    orderingPractitionerName,
    orderDateText: str(source.orderDateText),
    diagnosisText: str(source.diagnosisText),
    applyItemLines,
    serviceItemNameText,
    detailText,
    precautionText,
    resultText: '',
    performDepartmentName,
    practitionerSignName: orderingPractitionerName,
    examSiteText: detailText,
    examResultText: '',
    examDepartmentName: performDepartmentName,
  };
}
