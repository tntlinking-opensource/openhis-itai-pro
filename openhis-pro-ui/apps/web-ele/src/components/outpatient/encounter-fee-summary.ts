import type { DoctorStationAdviceRowDto } from '#/api/doctor-station/advice.types';
import type { TcmRxHeaderDto } from '#/api/doctor-station/tcm.types';

import { resolveServiceApplyFormNo } from '#/utils/service-apply-form-group-display';
import {
  calcMoneyLineTotal,
  formatMoneyDisplay,
  sumMoneyThenCalc,
  sumMoneyThenSettle,
} from '#/utils/format/money-scale';

/** 信息条固定展示的费用桶（金额为 0 也保留，避免缺项） */
const FIXED_FEE_LABELS = ['西药', '中成药', '中药', '申请单', '耗材'] as const;
const FIXED_FEE_LABEL_SET = new Set<string>(FIXED_FEE_LABELS);

const EXCLUDED_REQUEST_STATUS = new Set(['CANCELLED', 'STOPPED']);
const EXCLUDED_CHARGE_STATUS = new Set(['REFUNDED', 'REFUNDING']);

/** 检验/检查/治疗等申请单类目（activity_category_code 与申请单类别码） */
const APPLY_CATEGORY_CODES = new Set([
  '21',
  '22',
  '23',
  '24',
  '25',
  '43',
  '44',
  '45',
  '49',
  'PROOF',
  'TEST',
  'TREATMENT',
]);

export interface EncounterFeeItem {
  label: string;
  amountText: string;
}

export interface EncounterFeeSummaryView {
  items: EncounterFeeItem[];
  totalText: string;
}

/** 医嘱/中药写库后通知患者信息条重拉费用 */
const feeRefreshListeners = new Set<() => void>();

export function onEncounterFeeRefresh(listener: () => void): () => void {
  feeRefreshListeners.add(listener);
  return () => {
    feeRefreshListeners.delete(listener);
  };
}

export function notifyEncounterFeeRefresh(): void {
  for (const listener of feeRefreshListeners) {
    listener();
  }
}

function statusCode(value: unknown): string {
  return String(value ?? '')
    .trim()
    .toUpperCase();
}

function isExcludedMoneyRow(statusEnum: unknown, chargeStatusEnum: unknown): boolean {
  if (EXCLUDED_REQUEST_STATUS.has(statusCode(statusEnum))) return true;
  return EXCLUDED_CHARGE_STATUS.has(statusCode(chargeStatusEnum));
}

function adviceLineAmount(row: DoctorStationAdviceRowDto): number {
  const raw = row.lineDisplayTotalPrice ?? row.lineTotalAmount;
  const amount = Number(raw);
  return Number.isFinite(amount) ? amount : 0;
}

function adviceDisplayName(row: DoctorStationAdviceRowDto): string {
  return String(row.adviceDisplayName ?? row.contentSummary ?? '').trim();
}

/** 申请单行及其带出的耗材/采血费归入申请单，不重复计入耗材 */
function collectApplyRequestIds(rows: readonly DoctorStationAdviceRowDto[]): Set<string> {
  const ids = new Set<string>();
  for (const row of rows) {
    if (!resolveServiceApplyFormNo(row.prescriptionNo)) continue;
    const requestId = String(row.requestId ?? '').trim();
    if (requestId) ids.add(requestId);
  }
  return ids;
}

function isApplyFormRow(row: DoctorStationAdviceRowDto, applyRequestIds: ReadonlySet<string>): boolean {
  if (resolveServiceApplyFormNo(row.prescriptionNo)) return true;
  const parentId = String(row.basedOnServiceRequestId ?? '').trim();
  if (parentId && applyRequestIds.has(parentId)) return true;
  const category = String(row.catalogCategoryCode ?? '').trim().toUpperCase();
  return APPLY_CATEGORY_CODES.has(category);
}

function feeLabelOfAdviceRow(
  row: DoctorStationAdviceRowDto,
  applyRequestIds: ReadonlySet<string>,
): string {
  if (adviceDisplayName(row) === '代煎中药') return '中药';
  if (isApplyFormRow(row, applyRequestIds)) return '申请单';
  const category = String(row.catalogCategoryCode ?? '').trim();
  if (category === '2') return '西药';
  if (category === '1') return '中成药';
  if (category === '3') return '外购药品';
  if (category === '4') return '中药';
  if (category === '11') return '耗材';
  const resourceType = String(row.resourceType ?? '').trim().toUpperCase();
  if (resourceType === 'DEVICE') return '耗材';
  const name = String(row.catalogCategoryName ?? '').trim();
  if (name === '中草药') return '中药';
  if (name) return name;
  return '其他';
}

/** 中药方：单剂（剂量×单价）再乘付数，与中医处方页合计口径一致 */
function sumTcmPrescriptionAmount(header: TcmRxHeaderDto): number {
  if (isExcludedMoneyRow(header.statusEnum, header.chargeStatusEnum)) return 0;
  const lineAmounts: number[] = [];
  for (const line of header.lines ?? []) {
    if (isExcludedMoneyRow(line.lineStatusEnum, line.chargeStatusEnum)) continue;
    lineAmounts.push(calcMoneyLineTotal(line.retailUnitPrice, line.dose));
  }
  const singleDose = sumMoneyThenCalc(lineAmounts);
  const doseQty = Number(header.chineseHerbsDoseQuantity);
  if (Number.isFinite(doseQty) && doseQty > 0) {
    return calcMoneyLineTotal(singleDose, doseQty);
  }
  return singleDose;
}

/**
 * 汇总本次就诊费用：西药、中成药、中药、申请单、耗材固定列出，其余有金额的类目跟在后面。
 */
export function buildEncounterFeeSummary(
  adviceRows: readonly DoctorStationAdviceRowDto[],
  tcmPrescriptions: readonly TcmRxHeaderDto[],
): EncounterFeeSummaryView {
  const activeRows = adviceRows.filter(
    (row) => !isExcludedMoneyRow(row.statusEnum, row.chargeStatusEnum),
  );
  const applyRequestIds = collectApplyRequestIds(activeRows);
  const amounts = new Map<string, number[]>();
  const addAmount = (label: string, amount: number) => {
    const bucket = amounts.get(label);
    if (bucket) {
      bucket.push(amount);
      return;
    }
    amounts.set(label, [amount]);
  };

  for (const row of activeRows) {
    addAmount(feeLabelOfAdviceRow(row, applyRequestIds), adviceLineAmount(row));
  }
  for (const header of tcmPrescriptions) {
    addAmount('中药', sumTcmPrescriptionAmount(header));
  }

  const settled = new Map<string, number>();
  for (const [label, parts] of amounts) {
    settled.set(label, sumMoneyThenSettle(parts));
  }

  const items: EncounterFeeItem[] = FIXED_FEE_LABELS.map((label) => ({
    label,
    amountText: formatMoneyDisplay(settled.get(label) ?? 0),
  }));
  const extraLabels = [...settled.keys()]
    .filter((label) => !FIXED_FEE_LABEL_SET.has(label))
    .filter((label) => (settled.get(label) ?? 0) !== 0)
    .sort((left, right) => left.localeCompare(right, 'zh-CN'));
  for (const label of extraLabels) {
    items.push({
      label,
      amountText: formatMoneyDisplay(settled.get(label) ?? 0),
    });
  }

  const total = sumMoneyThenSettle([...settled.values()]);
  return {
    items,
    totalText: formatMoneyDisplay(total),
  };
}
