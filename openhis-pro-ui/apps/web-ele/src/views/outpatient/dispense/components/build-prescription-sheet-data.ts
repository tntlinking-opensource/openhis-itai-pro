import type { PrescriptionSheetData, PrescriptionSheetLine } from './prescription-sheet.types';
import type { DispensePrescriptionBlock, DispenseQueuePatient } from './types';

import type { TcmHerbGridDisplayRow } from '#/utils/tcm-prescription-display';
import { formatMoneyYuan, sumMoneyThenSettle } from '#/utils/format/money-scale';

export interface BuildPrescriptionSheetDataOptions {
  hospitalName?: string;
  feeNatureLabel?: string;
  /** 调配人展示名 */
  dispensingName?: string;
  /** 发药人展示名 */
  deliverName?: string;
  /** 划价人展示名 */
  pricingClerkName?: string;
  /** 中药处方：中医主诊断（病证合并） */
  tcmDiagnosisText?: string;
}

/** 从开立时间等字段截取 YYYY-MM-DD */
function toDatePart(isoLike: string): string {
  const t = (isoLike ?? '').trim();
  if (t.length >= 10) return t.slice(0, 10);
  return t;
}

/** 明细金额累加：非数字占位（如「—」）不计入，避免 Decimal 抛错 */
function amountToSumNumber(raw: string | undefined): number {
  const s = (raw ?? '').trim().replaceAll(',', '');
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
  const isHerbal = block.rxType === 'herbal';
  const items = block.items ?? [];

  let diagnosis: string;
  if (isHerbal) {
    diagnosis = options?.tcmDiagnosisText?.trim() || '—';
  } else {
    const firstDx = items[0]?.diagnosis?.trim() ?? '';
    diagnosis = firstDx || items.map((i) => i.diagnosis?.trim()).find(Boolean) || '—';
  }

  const settledTotal = sumMoneyThenSettle(items.map((line) => amountToSumNumber(line.amount)));

  let lines: PrescriptionSheetLine[];
  let tcmHerbGridRows: TcmHerbGridDisplayRow[] | undefined;
  let tcmUsageText: string | undefined;

  if (isHerbal) {
    tcmHerbGridRows = block.tcmHerbGridRows ?? [];
    tcmUsageText = block.tcmUsageText?.trim() || undefined;
    lines = [];
  } else {
    lines = items.map((line) => {
      const spec = (line.spec ?? '').trim();
      const bracketed = spec ? `(${spec})` : '';
      const qty = (line.quantity ?? '').trim();
      const title = `${line.itemName}${bracketed}${qty ? ` X${qty}` : ''}`;
      const freq = (line.freq ?? '').trim();
      const usage = freq ? `用法用量：${freq}` : undefined;
      return { title, usage };
    });
  }

  return {
    rxType: block.rxType,
    documentTitle: isHerbal ? '中药处方笺' : '处方单',
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
    tcmHerbGridRows,
    tcmUsageText,
    footerDoctorName: block.doctorName,
    footerDispensingName: options?.dispensingName?.trim() || '—',
    footerDeliverName: options?.deliverName?.trim() || '—',
    totalAmountLabel: formatMoneyYuan(settledTotal),
    footerPricingClerkName: options?.pricingClerkName?.trim() || '—',
  };
}
