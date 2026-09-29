import type { ServiceRequestDetailSummaryDto } from '#/api/doctor-station/advice.types';

import type { AdviceRowItem } from './advice.types';

import { ref } from 'vue';

import {
  SERVICE_CATEGORY_DISCHARGE,
  SERVICE_CATEGORY_TRANSFER,
} from '#/utils/print/constants/service-request-category-codes';

import { isNursingLevelAdviceRow } from './advice-list';

/** 跨患者粘贴用的开立内容（不含请求主键、诊断、费别、状态） */
export interface InpatientAdviceClipboardLine {
  productDefinitionId: string;
  productTableName: string;
  displayName: string;
  therapyTypeCode: string;
  /** 源成组键，仅用于粘贴时换新组号；空表示未成组 */
  sourceGroupKey: string;
  resourceType: string;
  rateCode: string;
  methodCode: string;
  speedCode: string;
  skinTestFlag: boolean;
  injectionFlag: boolean;
  remark: string;
  entrustContent: string;
  catalogCategoryCode?: string;
  listDose?: number;
  listDoseUnitCode?: string;
  listFirstDose?: number;
  listFirstDoseUnitCode?: string;
  listDoseSchedule?: string;
  listQuantity?: number;
  listUnitCode?: string;
  listUnitName?: string;
  listExecutionCount?: number;
  dispensePerDuration?: number;
  performOrgId?: string;
  performOrgName?: string;
  /** 源申请单号；非空表示整张申请单行，粘贴时按单重建而非普通诊疗草稿 */
  sourceApplyFormNo?: string;
  serviceRequestDetails?: ServiceRequestDetailSummaryDto[];
  encounterDiagnosisIds?: string;
}

export interface CollectInpatientAdviceClipboardResult {
  lines: InpatientAdviceClipboardLine[];
  skippedCount: number;
}

/** 医生站会话剪贴板；换患者不清，离开医生站或再次复制才替换/清空 */
const clipboardLines = ref<InpatientAdviceClipboardLine[]>([]);

/** 当前剪贴板行（供粘贴按钮禁用态） */
export function useInpatientAdviceClipboardLines() {
  return clipboardLines;
}

/** 用新勾选整份替换剪贴板 */
export function replaceInpatientAdviceClipboard(lines: InpatientAdviceClipboardLine[]) {
  clipboardLines.value = lines.map((line) => ({ ...line }));
}

/** 离开住院医生站时清空 */
export function clearInpatientAdviceClipboard() {
  clipboardLines.value = [];
}

/**
 * 勾选到申请单任一行时补全同单其余诊疗行。
 * 耗材子行无申请单号，不在此处展开。
 */
export function expandServiceApplyFormsInSelection(
  selected: AdviceRowItem[],
  tableRows: readonly AdviceRowItem[],
): AdviceRowItem[] {
  const formNos = new Set(
    selected.map((row) => String(row.applyFormNo ?? '').trim()).filter(Boolean),
  );
  if (formNos.size === 0) {
    return selected;
  }
  const seen = new Set(selected.map((row) => String(row.requestId ?? row.key ?? '').trim()));
  const extra: AdviceRowItem[] = [];
  for (const row of tableRows) {
    if (row.isEditor) {
      continue;
    }
    const formNo = String(row.applyFormNo ?? '').trim();
    const key = String(row.requestId ?? row.key ?? '').trim();
    if (!formNo || !formNos.has(formNo) || !key || seen.has(key)) {
      continue;
    }
    seen.add(key);
    extra.push(row);
  }
  return extra.length > 0 ? [...selected, ...extra] : selected;
}

/** 勾选行展开成组后，抽出可复制内容；护理/转科/出院/皮试伴随/耗材/中药不入剪贴板 */
export function collectInpatientAdviceClipboardLines(
  selected: AdviceRowItem[],
): CollectInpatientAdviceClipboardResult {
  const expanded = expandAdviceSelectionForCopy(selected);
  const lines: InpatientAdviceClipboardLine[] = [];
  let skippedCount = 0;
  for (const row of expanded) {
    const line = toClipboardLine(row);
    if (!line) {
      skippedCount += 1;
      continue;
    }
    lines.push(line);
  }
  return { lines, skippedCount };
}

/** 成组展示行展开成员；同一请求只收一次 */
function expandAdviceSelectionForCopy(selected: AdviceRowItem[]): AdviceRowItem[] {
  const out: AdviceRowItem[] = [];
  const seen = new Set<string>();
  for (const row of selected) {
    if (row.isEditor) {
      continue;
    }
    const members = row.mergedAdviceRows?.length ? row.mergedAdviceRows : [row];
    for (const member of members) {
      if (member.isEditor) {
        continue;
      }
      const key = String(member.requestId ?? member.key ?? '').trim();
      if (!key || seen.has(key)) {
        continue;
      }
      seen.add(key);
      out.push(member);
    }
  }
  return out;
}

/** 单行是否可复制；不可复制返回 null */
function toClipboardLine(row: AdviceRowItem): InpatientAdviceClipboardLine | null {
  if (row.tcmFlag === true) {
    return null;
  }
  if (String(row.basedOnMedicationRequestId ?? '').trim()) {
    return null;
  }
  const resourceType = String(row.resourceType ?? '')
    .trim()
    .toUpperCase();
  if (resourceType === 'DEVICE') {
    return null;
  }
  const category = String(row.serviceCategoryEnum ?? row.catalogCategoryCode ?? '').trim();
  if (
    isNursingLevelAdviceRow(row) ||
    category === SERVICE_CATEGORY_TRANSFER ||
    category === SERVICE_CATEGORY_DISCHARGE
  ) {
    return null;
  }
  const productDefinitionId = String(row.productDefinitionId ?? '').trim();
  const applyFormNo = String(row.applyFormNo ?? '').trim();
  const productTableName =
    String(row.productTableName ?? '').trim() || (applyFormNo ? 'wkf_activity_definition' : '');
  if (!productDefinitionId || !productTableName) {
    return null;
  }
  return {
    productDefinitionId,
    productTableName,
    displayName:
      String(row.adviceDisplayName ?? '').trim() || String(row.content ?? '').trim() || '未命名',
    therapyTypeCode: String(row.therapyTypeCode ?? '').trim(),
    sourceGroupKey: String(row.groupId ?? '').trim(),
    resourceType,
    rateCode: String(row.rateCode ?? '').trim(),
    methodCode: String(row.methodCode ?? '').trim(),
    speedCode: String(row.speedCode ?? '').trim(),
    skinTestFlag: row.skinTestFlag === true,
    injectionFlag: row.injectionFlag === true,
    remark: String(row.remark ?? '').trim(),
    entrustContent: String(row.entrustContent ?? '').trim(),
    catalogCategoryCode: category || undefined,
    listDose: row.listDose,
    listDoseUnitCode: row.listDoseUnitCode,
    listFirstDose: row.listFirstDose,
    listFirstDoseUnitCode: row.listFirstDoseUnitCode,
    listDoseSchedule: row.listDoseSchedule,
    listQuantity: row.listQuantity,
    listUnitCode: row.listUnitCode,
    listUnitName: row.listUnitName,
    listExecutionCount: row.listExecutionCount,
    dispensePerDuration: row.dispensePerDuration,
    performOrgId: row.performOrgId,
    performOrgName: row.performOrgName,
    sourceApplyFormNo: applyFormNo || undefined,
    serviceRequestDetails: row.serviceRequestDetails,
    encounterDiagnosisIds: String(row.encounterDiagnosisIds ?? '').trim() || undefined,
  };
}
