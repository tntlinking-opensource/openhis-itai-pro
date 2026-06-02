import type { TerminologyOptionDto } from '#/api/doctor-station/advice.types';

import type { AdviceDrugItem } from './advice.types';
import type { AdviceLineEditorKind } from './advice-row';
import { formatMoneyStrYuan, formatUnitPriceYuan, terminologyLabel } from './advice-format';
import { totalAmountUnitDisplayLabel } from './medication-unit-bands';

/** 编辑卡收起态摘要与 tooltip */

function calcLineTotalFee(item: AdviceDrugItem, kind?: AdviceLineEditorKind): string {
  const price = Number(item.selectedDrugPrice || 0);
  const amount =
    kind === 'SERVICE' ? Number(item.serviceQuantity ?? 0) : Number(item.totalAmount ?? 0);
  return price > 0 && amount > 0 ? (price * amount).toFixed(2) : '0.00';
}

export interface CollapsedSummaryOptions {
  medicationRateOptions: TerminologyOptionDto[];
  medicationRouteOptions: TerminologyOptionDto[];
}

export function formatWesternCollapsedSummary(
  item: AdviceDrugItem,
  opts: CollapsedSummaryOptions,
): string {
  const usageLabel = terminologyLabel(opts.medicationRouteOptions, item.usage);
  const freqLabel = terminologyLabel(opts.medicationRateOptions, item.frequency);
  const totalUnitLabel = totalAmountUnitDisplayLabel(item);
  const sd =
    item.singleDose === undefined ||
    item.singleDose === null ||
    !Number.isFinite(Number(item.singleDose))
      ? '—'
      : String(item.singleDose);
  const ta =
    item.totalAmount === undefined ||
    item.totalAmount === null ||
    !Number.isFinite(Number(item.totalAmount))
      ? '—'
      : String(item.totalAmount);
  return `${sd}${item.doseMainUnit || ''}/次，共${ta}${totalUnitLabel}，${usageLabel}，${freqLabel}`;
}

export function formatWesternCollapsedTooltip(
  item: AdviceDrugItem,
  opts: CollapsedSummaryOptions,
): string {
  const head = `${item.selectedDrugName || '-'}【${item.selectedDrugSpec || '-'}】单价：${formatUnitPriceYuan(item.selectedDrugPrice)}`;
  const tail = `${item.diagnosis || '-'} | ${item.feeNature} | ${formatWesternCollapsedSummary(item, opts)} | 总金额：${formatMoneyStrYuan(calcLineTotalFee(item))}`;
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
  const u = totalAmountUnitDisplayLabel(item);
  const n =
    item.totalAmount === undefined ||
    item.totalAmount === null ||
    !Number.isFinite(Number(item.totalAmount))
      ? '—'
      : String(item.totalAmount);
  const dept = String(item.performOrgName ?? item.performOrgId ?? '').trim() || '—';
  return `数量 ${qty}${u || ''} | 执行 ${n} 次 | ${dept}`;
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
