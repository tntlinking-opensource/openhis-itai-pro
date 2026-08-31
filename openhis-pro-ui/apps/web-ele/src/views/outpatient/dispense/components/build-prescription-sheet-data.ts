import type {
  PrescriptionSheetData,
  PrescriptionSheetLine,
  PrescriptionSheetStockLine,
} from './prescription-sheet.types';
import type {
  DispensePrescriptionBlock,
  DispensePrescriptionLine,
  DispenseQueuePatient,
} from './types';

import type { TcmHerbGridDisplayRow } from '#/utils/tcm-prescription-display';
import { formatMoneyYuan, sumMoneyThenSettle } from '#/utils/format/money-scale';
import { unionDiagnosisDisplayNamesOrDash } from '#/utils/diagnosis/union-diagnosis-display-names';

export interface BuildPrescriptionSheetDataOptions {
  hospitalName?: string;
  feeNatureLabel?: string;
  /**
   * 调配人展示名兜底（优先用处方块 preparerPractitionerName）。
   * 对应后端 preparerPractitionerName。
   */
  preparerName?: string;
  /**
   * 发药人展示名兜底（优先用处方块 dispensingPractitionerName）。
   * 对应后端 dispensingPractitionerName。
   */
  dispenserName?: string;
  /**
   * 划价人展示名兜底（优先用处方块 pricingPractitionerName）。
   * 对应后端 pricingPractitionerName。
   */
  pricingClerkName?: string;
  /** 中药处方：中医主诊断（病证合并） */
  tcmDiagnosisText?: string;
  /**
   * 页眉副标题覆盖（医生站传「处方单/中药处方单」；发药工作站默认「发药单/中药发药单」）。
   */
  documentTitle?: string;
  /** 扫码回填的追溯码（优先于行上原值） */
  traceCodeByLineId?: Record<string, string>;
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

/** 处方块状态 → 笺面展示文案（与列表明细 Tag 一致） */
function blockStatusToLabel(status: DispensePrescriptionBlock['status']): string {
  if (status === 'blocking') return '拦截';
  if (status === 'partial') return '部分可发';
  if (status === 'prepared') return '已配药';
  if (status === 'completed') return '已发';
  return '待发';
}

/** 空值统一为「—」，与笺面其他字段兜底一致 */
function displayOrDash(raw: string | undefined): string {
  const t = (raw ?? '').trim();
  return t || '—';
}

/** 仅待发可改批号（已配药一键重试不会再走 savePrepare） */
function blockAllowsLotChange(status: DispensePrescriptionBlock['status']): boolean {
  return status === 'pending';
}

/**
 * 明细行 → 笺面批号可改能力（本库有候选 + 处方可发）。
 *
 * @param line 处方明细
 * @param blockCanChange 方头状态是否允许
 */
function resolveLineLotPick(
  line: DispensePrescriptionLine,
  blockCanChange: boolean,
): {
  canChangeLot: boolean;
  lotPickOptions?: DispensePrescriptionLine['inventoryPickOptions'];
} {
  const opts = line.inventoryPickOptions;
  if (!blockCanChange || !opts?.length || line.lineKind === 'decoctionFee') {
    return { canChangeLot: false };
  }
  return { canChangeLot: true, lotPickOptions: opts };
}

/**
 * 笺面「发放药房」只取发放行库位名（接口 dispenseLocationName），
 * 不用库存下拉选中项——下拉含模板候选多库房，默认第一项常与发放行不一致。
 */
function resolveDispensePharmacyLabel(line: DispensePrescriptionLine): string {
  return displayOrDash(line.pharmacy);
}

/** 基础信息「发放药房」：去掉「 / 批号 xxx」后缀，仅保留药房名 */
function toPharmacyNameOnly(pharmacyLabel: string): string {
  const t = pharmacyLabel.trim();
  if (!t || t === '—') return '—';
  const cut = t.indexOf(' / 批号');
  if (cut > 0) return t.slice(0, cut).trim() || '—';
  return t;
}

/**
 * 中药笺面四列网格：药名+克数，右上角附特殊煎法（与打印模版 tcmHerbRows.note* 一致）。
 * 优先用处方块已装配网格；缺失时按明细行 quantity / dosageInstructionText 兜底。
 */
function resolveHerbGridRows(block: DispensePrescriptionBlock): TcmHerbGridDisplayRow[] {
  const source = block.tcmHerbGridRows ?? [];
  if (source.length > 0) {
    return source.map((row) => ({
      col1: row.col1,
      note1: row.note1 ?? '',
      col2: row.col2,
      note2: row.note2 ?? '',
      col3: row.col3,
      note3: row.note3 ?? '',
      col4: row.col4,
      note4: row.note4 ?? '',
    }));
  }
  // 兜底：药材行「药名 + 数量」拼成四列，附带特殊煎法
  const cells = (block.items ?? [])
    .filter((line) => line.lineKind !== 'decoctionFee')
    .map((line) => {
      const name = (line.itemName ?? '').trim();
      const qty = (line.quantity ?? '').trim();
      if (!name || name === '—') {
        return null;
      }
      const herbDisplay = qty && qty !== '—' ? `${name} ${qty}` : name;
      const rawNote = (line.dosageInstructionText ?? '').trim();
      const decoctionNote =
        rawNote && rawNote !== '—' ? (rawNote.startsWith('(') ? rawNote : `(${rawNote})`) : '';
      return { herbDisplay, decoctionNote };
    })
    .filter((cell): cell is { herbDisplay: string; decoctionNote: string } => cell != null);
  const gridRows: TcmHerbGridDisplayRow[] = [];
  for (let i = 0; i < cells.length; i += 4) {
    gridRows.push({
      col1: cells[i]?.herbDisplay ?? '',
      note1: cells[i]?.decoctionNote ?? '',
      col2: cells[i + 1]?.herbDisplay ?? '',
      note2: cells[i + 1]?.decoctionNote ?? '',
      col3: cells[i + 2]?.herbDisplay ?? '',
      note3: cells[i + 2]?.decoctionNote ?? '',
      col4: cells[i + 3]?.herbDisplay ?? '',
      note4: cells[i + 3]?.decoctionNote ?? '',
    });
  }
  return gridRows;
}

/**
 * 将待发药队列患者 + 处方块组装为发药单数据。
 * 明细字段与列表明细对齐；金额按明细行 amount 累加（元，保留两位）。
 */
export function buildPrescriptionSheetData(
  patient: DispenseQueuePatient,
  block: DispensePrescriptionBlock,
  options?: BuildPrescriptionSheetDataOptions,
): PrescriptionSheetData {
  const hospitalName = options?.hospitalName ?? '医疗机构';
  // 费用性质：接口（处方块）优先；未返回时补偿取患者队列费用性质；再空回落自费
  const feeNatureLabel =
    options?.feeNatureLabel?.trim() ||
    block.feeNatureLabel?.trim() ||
    patient.feeNatureLabel?.trim() ||
    '自费';
  // 就诊类型：处方块 encounterMedicationTypeText（med_type）；无则笺面仍展示费用性质
  const visitTypeLabel = block.visitTypeLabel?.trim() || undefined;
  const isHerbal = block.rxType === 'herbal';
  const items = block.items ?? [];

  let diagnosis: string;
  if (isHerbal) {
    diagnosis = options?.tcmDiagnosisText?.trim() || '—';
  } else {
    // 西药方头：同方各明细诊断去重后顿号拼接（并集展示）
    diagnosis = unionDiagnosisDisplayNamesOrDash(...items.map((line) => line.diagnosis));
  }

  const settledDrugTotal = sumMoneyThenSettle(items.map((line) => amountToSumNumber(line.amount)));
  // 中药代煎费：与药材金额一并计入笺面合计
  const decoctionAmountRaw = isHerbal ? block.decoctionChargeAmountLabel : undefined;
  const decoctionAmountNum = amountToSumNumber(decoctionAmountRaw);
  const settledTotal = sumMoneyThenSettle([settledDrugTotal, decoctionAmountNum]);
  const showDecoction =
    isHerbal && (block.sufferingFlag === true || Boolean(decoctionAmountRaw?.trim()));
  const decoctionActivityName = showDecoction
    ? block.decoctionActivityName?.trim() || '代煎中药'
    : undefined;
  const decoctionChargeAmountLabel = showDecoction ? decoctionAmountRaw?.trim() || '—' : undefined;
  const decoctionChargeQuantityLabel = showDecoction
    ? block.decoctionChargeQuantityLabel?.trim() || undefined
    : undefined;

  // 西药按明细行展开；中药笺面用四列饮片网格，不走明细行版式
  const blockCanChangeLot = blockAllowsLotChange(block.status);
  const preferServerTrace = block.status === 'completed';
  const lines: PrescriptionSheetLine[] = isHerbal
    ? []
    : items.map((line) => {
        const freq = (line.freq ?? '').trim();
        const dosage = (line.dosageInstructionText ?? '').trim();
        const lotPick = resolveLineLotPick(line, blockCanChangeLot);
        // 已发：只展示落库码；待发才允许本地扫码缓存盖住
        const traceRaw = preferServerTrace
          ? line.traceCode
          : options?.traceCodeByLineId?.[line.id] || line.traceCode;
        return {
          dispenseId: line.id,
          itemName: displayOrDash(line.itemName),
          quantity: displayOrDash(line.quantity),
          spec: displayOrDash(line.spec),
          dosageInstructionText: dosage || undefined,
          batchNo: displayOrDash(line.batchNo),
          unitPrice: displayOrDash(line.unitPrice),
          traceCode: displayOrDash(traceRaw),
          factory: displayOrDash(line.factory),
          usage: freq && freq !== '—' ? freq : undefined,
          remainingStockText: displayOrDash(line.remainingStockText),
          ...lotPick,
        };
      });
  const tcmHerbGridRows = isHerbal ? resolveHerbGridRows(block) : undefined;

  // 库存弹窗：中西药均按药品明细装配（不含代煎费）；中药笺面改批号也走此弹窗
  const stockLines: PrescriptionSheetStockLine[] = items
    .filter((line) => (line.lineKind ?? 'medication') === 'medication')
    .map((line) => {
      const lotPick = resolveLineLotPick(line, blockCanChangeLot);
      return {
        dispenseId: line.id,
        itemName: displayOrDash(line.itemName),
        remainingStockText: displayOrDash(line.remainingStockText),
        batchNo: displayOrDash(line.batchNo),
        ...lotPick,
      };
    });

  // 发放药房：按发放行库位名去重（与 med_medication_dispense.location_id 一致）
  const pharmacyUniques: string[] = [];
  for (const line of items) {
    const label = toPharmacyNameOnly(resolveDispensePharmacyLabel(line));
    if (label === '—' || pharmacyUniques.includes(label)) continue;
    pharmacyUniques.push(label);
  }
  const dispensePharmacyLabel = pharmacyUniques.length > 0 ? pharmacyUniques.join('、') : '—';

  // 笺面签名区与打印 PrintData 同源：开单/调配/发药/划价（划价=收费人，禁止回落开单医生）
  const orderingDoctorName = (block.doctorName ?? '').trim() || '—';
  const preparerName =
    (block.preparerPractitionerName ?? '').trim() || options?.preparerName?.trim() || '—';
  const dispenserName =
    (block.dispensingPractitionerName ?? '').trim() || options?.dispenserName?.trim() || '—';
  const pricingName =
    (block.pricingPractitionerName ?? '').trim() || options?.pricingClerkName?.trim() || '—';
  // 手机号：处方块（发药 slip）优先，其次队列患者（医生站）
  const patientPhone =
    (block.patientPhone ?? '').trim() || (patient.patientPhone ?? '').trim() || '—';

  return {
    rxType: block.rxType,
    status: block.status,
    statusLabel: blockStatusToLabel(block.status),
    documentTitle: options?.documentTitle?.trim() || (isHerbal ? '中药发药单' : '发药单'),
    hospitalName,
    prescriptionTypeLine1: block.prescriptionTypeLine1?.trim() || undefined,
    prescriptionTypeLine2: block.prescriptionTypeLine2?.trim() || undefined,
    prescriptionNo: block.rxNo,
    patientName: patient.patientName,
    patientAgeLabel: patient.ageLabel,
    patientGenderLabel: patient.genderLabel || '—',
    deptName: block.deptName,
    feeNatureLabel,
    visitTypeLabel,
    prescriptionDate: toDatePart(block.issuedAt),
    outpatientNoLabel: patient.visitNo.trim() || '—',
    prescribingDoctorName: orderingDoctorName,
    diagnosis,
    patientPhone,
    dispensePharmacyLabel,
    stockLines,
    lines,
    tcmHerbGridRows,
    tcmUsageText: isHerbal ? block.tcmUsageText?.trim() || undefined : undefined,
    decoctionActivityName,
    decoctionChargeAmountLabel,
    decoctionChargeQuantityLabel,
    footerDoctorName: orderingDoctorName,
    footerDispensingName: preparerName,
    footerDeliverName: dispenserName,
    totalAmountLabel: formatMoneyYuan(settledTotal),
    footerPricingClerkName: pricingName,
  };
}
