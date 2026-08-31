import type { AdviceLineEditorKind } from './advice-row';
import type { AdviceDrugItem } from './advice.types';

import type { TerminologyOptionDto } from '#/api/doctor-station/advice.types';
import type { DiagnosisItem } from '#/components/doctor-station/diagnosis';

import { formatWesternEditDoseSegment } from '#/utils/doctor-station/medication-dose-display';
import { calcMoneyLineTotal, formatMoneyDisplay } from '#/utils/format/money-scale';

import {
  buildAdviceDiagnosisDisplaySegments,
  buildDiagnosisLookupById,
} from './advice-diagnosis-display';
import { formatMoneyStrYuan, formatUnitPriceYuan, terminologyLabel } from './advice-format';
import { totalAmountUnitDisplayLabel } from './medication-unit-bands';
import { resolveServicePreviewBillQuantity } from './service-quantity-calc';

/** 编辑卡收起态摘要与 tooltip */

/** 编辑卡金额预览：诊疗按单次量×次数，药/材按总量 */
function calcLineTotalFee(item: AdviceDrugItem, kind?: AdviceLineEditorKind): string {
  const price = Number(item.selectedDrugPrice || 0);
  const amount =
    kind === 'SERVICE'
      ? resolveServicePreviewBillQuantity(item.serviceQuantity, item.totalAmount)
      : Number(item.totalAmount ?? 0);
  if (price <= 0 || amount <= 0) {
    return '0.00';
  }
  return formatMoneyDisplay(calcMoneyLineTotal(price, amount));
}

export interface CollapsedSummaryOptions {
  medicationRateOptions: TerminologyOptionDto[];
  medicationRouteOptions: TerminologyOptionDto[];
  /** 就诊诊断候选项：收起态诊断列拼慢/特角标 */
  patientDiagnoses?: DiagnosisItem[];
}

/** 收起态诊断文案（含慢/特） */
export function formatCollapsedDiagnosisText(
  item: Pick<AdviceDrugItem, 'diagnosis' | 'encounterDiagnosisIds'>,
  patientDiagnoses: readonly DiagnosisItem[] = [],
): string {
  const segments = buildAdviceDiagnosisDisplaySegments({
    diagnosisSummary: item.diagnosis || '-',
    encounterDiagnosisIds: item.encounterDiagnosisIds,
    diagnosisById: buildDiagnosisLookupById(patientDiagnoses),
  });
  return segments.map((seg) => (seg.tag ? `${seg.name}（${seg.tag}）` : seg.name)).join('、');
}

export function formatWesternCollapsedSummary(
  item: AdviceDrugItem,
  opts: CollapsedSummaryOptions,
): string {
  const usageLabel = terminologyLabel(opts.medicationRouteOptions, item.usage);
  const freqLabel = terminologyLabel(opts.medicationRateOptions, item.frequency);
  const totalUnitLabel = totalAmountUnitDisplayLabel(item);
  const dosePart = formatWesternEditDoseSegment(item);
  const ta =
    item.totalAmount === undefined ||
    item.totalAmount === null ||
    !Number.isFinite(Number(item.totalAmount))
      ? '—'
      : String(item.totalAmount);
  return `${dosePart}，共${ta}${totalUnitLabel}，${usageLabel}，${freqLabel}`;
}

export function formatWesternCollapsedTooltip(
  item: AdviceDrugItem,
  opts: CollapsedSummaryOptions,
): string {
  const diagnosisText = formatCollapsedDiagnosisText(item, opts.patientDiagnoses ?? []);
  const head = `${item.selectedDrugName || '-'}【${item.selectedDrugSpec || '-'}】单价：${formatUnitPriceYuan(item.selectedDrugPrice)}`;
  const tail = `${diagnosisText} | ${item.feeNature} | ${formatWesternCollapsedSummary(item, opts)} | 总金额：${formatMoneyStrYuan(calcLineTotalFee(item))}`;
  return `${head} | ${tail}`;
}

function formatDeviceCollapsedSummary(item: AdviceDrugItem): string {
  const ta =
    item.totalAmount === undefined ||
    item.totalAmount === null ||
    !Number.isFinite(Number(item.totalAmount))
      ? '—'
      : String(item.totalAmount);
  const u = totalAmountUnitDisplayLabel(item);
  return `数量 ${ta}${u || ''}`;
}

function formatServiceCollapsedSummary(item: AdviceDrugItem): string {
  const qty =
    item.serviceQuantity === undefined ||
    item.serviceQuantity === null ||
    !Number.isFinite(Number(item.serviceQuantity))
      ? '—'
      : String(item.serviceQuantity);
  const n =
    item.totalAmount === undefined ||
    item.totalAmount === null ||
    !Number.isFinite(Number(item.totalAmount))
      ? '—'
      : String(item.totalAmount);
  const dept = String(item.performOrgName ?? item.performOrgId ?? '').trim() || '—';
  return `单次数量 ${qty} | 执行 ${n} 次 | ${dept}`;
}

export function formatNonMedicationCollapsedSummary(
  item: AdviceDrugItem,
  kind: AdviceLineEditorKind,
): string {
  if (kind === 'DEVICE') return formatDeviceCollapsedSummary(item);
  if (kind === 'SERVICE') return formatServiceCollapsedSummary(item);
  return '';
}

export function formatNonMedicationCollapsedTooltip(
  item: AdviceDrugItem,
  _kind: AdviceLineEditorKind,
  middle: string,
): string {
  const head = `${item.selectedDrugName || '-'}【${item.selectedDrugSpec || '-'}】单价：${formatUnitPriceYuan(item.selectedDrugPrice)}`;
  return `${head} | ${middle}`;
}
