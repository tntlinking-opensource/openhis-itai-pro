import type { TerminologyOptionDto } from '#/api/doctor-station/advice.types';

import { calcMoneyLineTotal, formatMoneyDisplay } from '#/utils/format/money-scale';

import type { AdviceDrugItem } from './advice.types';
import type { AdviceLineEditorKind } from './advice-row';
import { isInpatientLongTermMedicationTherapy } from './advice-row';
import { formatMoneyStrYuan, formatUnitPriceYuan, terminologyLabel } from './advice-format';
import { totalAmountUnitDisplayLabel } from './medication-unit-bands';

/** 编辑卡收起态摘要与 tooltip */

function calcLineTotalFee(item: AdviceDrugItem): string {
  const price = Number(item.selectedDrugPrice || 0);
  const amount = Number(item.totalAmount ?? 0);
  if (price <= 0 || amount <= 0) {
    return '0.00';
  }
  return formatMoneyDisplay(calcMoneyLineTotal(price, amount));
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
  const dosePart = `${sd}${item.doseMainUnit || ''}/次`;
  if (isInpatientLongTermMedicationTherapy(item.therapyEnum)) {
    return `${dosePart}，${usageLabel}，${freqLabel}`;
  }
  return `${dosePart}，共${ta}${totalUnitLabel}，${usageLabel}，${freqLabel}`;
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

function formatServiceCollapsedSummary(
  item: AdviceDrugItem,
  opts?: CollapsedSummaryOptions,
): string {
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
  const freqLabel = terminologyLabel(opts?.medicationRateOptions, item.frequency);
  const longTerm = isInpatientLongTermMedicationTherapy(item.therapyEnum);
  if (longTerm) {
    return `数量 ${qty}${u || ''} | 频次 ${freqLabel} | ${dept}`;
  }
  return `数量 ${qty}${u || ''} | 频次 ${freqLabel} | 执行 ${n} 次 | ${dept}`;
}

export function formatNonMedicationCollapsedSummary(
  item: AdviceDrugItem,
  kind: AdviceLineEditorKind,
  opts?: CollapsedSummaryOptions,
): string {
  if (kind === 'DEVICE') return formatDeviceCollapsedSummary(item);
  if (kind === 'SERVICE') return formatServiceCollapsedSummary(item, opts);
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
