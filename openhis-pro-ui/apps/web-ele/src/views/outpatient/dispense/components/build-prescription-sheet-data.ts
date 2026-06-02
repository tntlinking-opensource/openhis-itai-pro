import type { PrescriptionSheetData } from './prescription-sheet.types';
import type { DispensePrescriptionBlock, DispenseQueuePatient } from './types';

import { decimalAdd } from '#/utils/decimal-util';

export interface BuildPrescriptionSheetDataOptions {
  hospitalName?: string;
  feeNatureLabel?: string;
  /** 调配人展示名 */
  dispensingName?: string;
  /** 发药人展示名 */
  deliverName?: string;
  /** 划价人展示名 */
  pricingClerkName?: string;
}

/** 从开立时间等字段截取 YYYY-MM-DD */
function toDatePart(isoLike: string): string {
  const t = (isoLike ?? '').trim();
  if (t.length >= 10) return t.slice(0, 10);
  return t;
}

/** 明细金额累加：非数字占位（如「—」）不计入，避免 Decimal 抛错 */
function amountToSumNumber(raw: string | undefined): number {
  const s = (raw ?? '').trim().replace(/,/g, '');
  if (!s) return 0;
  const n = Number(s);
  return Number.isFinite(n) ? n : 0;
}

/**
 * 将待发药队列患者 + 处方块组装为纸质处方单数据。
 * 金额按明细行 amount 累加（元，保留两位）。
 */
export function buildPrescriptionSheetData(
  patient: DispenseQueuePatient,
  block: DispensePrescriptionBlock,
  options?: BuildPrescriptionSheetDataOptions,
): PrescriptionSheetData {
  const hospitalName = options?.hospitalName ?? '医疗机构';
  const feeNatureLabel = options?.feeNatureLabel ?? '自费';
  const items = block.items ?? [];
  const firstDx = items[0]?.diagnosis?.trim() ?? '';
  const diagnosis = firstDx || items.map((i) => i.diagnosis?.trim()).find(Boolean) || '—';

  let total = 0;
  for (const line of items) {
    total = decimalAdd(total, amountToSumNumber(line.amount), 2);
  }
  const lines = items.map((line) => {
    const spec = (line.spec ?? '').trim();
    const bracketed = spec ? `(${spec})` : '';
    const qty = (line.quantity ?? '').trim();
    const title = `${line.itemName}${bracketed}${qty ? ` X${qty}` : ''}`;
    const freq = (line.freq ?? '').trim();
    const usage = freq ? `用法用量：${freq}` : undefined;
    return { title, usage };
  });

  return {
    hospitalName,
    prescriptionNo: block.rxNo,
    patientName: patient.patientName,
    patientAgeLabel: patient.ageLabel,
    patientGenderLabel: patient.genderLabel,
    deptName: block.deptName,
    feeNatureLabel,
    prescriptionDate: toDatePart(block.issuedAt),
    outpatientNoLabel: patient.visitNo.trim() || '—',
    prescribingDoctorName: block.doctorName,
    diagnosis,
    lines,
    footerDoctorName: block.doctorName,
    footerDispensingName: options?.dispensingName?.trim() || '—',
    footerDeliverName: options?.deliverName?.trim() || '—',
    totalAmountLabel: `${total.toFixed(2)} 元`,
    footerPricingClerkName: options?.pricingClerkName?.trim() || '—',
  };
}
