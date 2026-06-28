import type { TerminologyOptionDto } from '#/api/doctor-station/advice.types';
import type {
  ServiceApplyKind,
  ServiceApplySelectedItem,
} from '#/views/outpatient/doctor-station/service-apply/service-apply.types';

import { detailTargetIds } from '#/views/outpatient/doctor-station/service-apply/service-apply.service';

/** 检验申请单 PrintData（与 test-print 模板 field 一致） */
export interface TestPrintData {
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
  serviceItemNameText: string;
  specimenTypeText: string;
  precautionText: string;
  labResultText: string;
  labDepartmentName: string;
  practitionerSignName: string;
}

/** 检验申请单 mapper 入参（P：门诊医生站申请弹窗签发后） */
export interface TestPrintSource {
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
  /** 列表打印：与 lines 下标对应的样本文案 */
  lineDetailDisplayTexts?: string[];
  bodyStructureOptions?: TerminologyOptionDto[];
  specimenDefinitionOptions?: TerminologyOptionDto[];
  /** 列表打印等场景：已解析的样本文案，优先于术语树（仅单 line 时生效） */
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

function resolveSourceLines(source: TestPrintSource): ServiceApplySelectedItem[] {
  if (source.lines?.length) {
    return source.lines;
  }
  if (source.line) {
    return [source.line];
  }
  return [];
}

function buildLineSpecimenTypeText(
  source: TestPrintSource,
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

/** 检验申请单行 → PrintData（支持同一申请单多项目合并一张） */
export function buildTestPrintData(source: TestPrintSource): TestPrintData {
  const orderingPractitionerName = str(source.orderingPractitionerName);
  const lines = resolveSourceLines(source);
  const serviceItemNameText = joinUniqueNonEmpty(lines.map((line) => line.activityName));
  const specimenTypeText = joinUniqueNonEmpty(
    lines.map((line, index) => buildLineSpecimenTypeText(source, line, index)),
  );
  const precautionText = joinUniqueNonEmpty(lines.map((line) => line.remark));
  return {
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
    serviceItemNameText,
    specimenTypeText,
    precautionText,
    labResultText: '',
    labDepartmentName: str(source.performOrgName),
    practitionerSignName: orderingPractitionerName,
  };
}
