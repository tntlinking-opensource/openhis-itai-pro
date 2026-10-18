import type { EncounterMedicationDispenseSlipRowDto } from '#/api/outpatient-pharmacy-dispense/outpatient-pharmacy-dispense.types';
import type { EncounterDiagnosisRowDto } from '#/api/doctor-station/diagnosis.types';
import type { TcmDiagnosisGroupRowDto } from '#/api/doctor-station/tcm.types';

import { pickMainTcmDiagnosisDisplayName } from '#/components/doctor-station/diagnosis/services/encounter-diagnosis-select';
import { formatMoneyDisplay } from '#/utils/format/money-scale';
import {
  resolvePrescriptionPaperTypeLine1,
  resolvePrescriptionPaperTypeLine2,
} from '#/utils/print/resolve-prescription-paper-type';
import { TCM_RX_PRINT_HERB_WEIGHT_PER_DOSE } from '#/utils/print/outpatient-tcm-rx-print.config';
import {
  buildTcmHerbGridRowsFromSlipRows,
  buildTcmHerbGridRowsFromSlipRowsForRxPrint,
  buildTcmPrescriptionUsageTextFromSlipHead,
  type TcmHerbGridDisplayRow,
} from '#/utils/tcm-prescription-display';

/** 中药处方饮片表格行（与模板 tcmHerbRows 列一致） */
export type OutpatientTcmPrescriptionHerbGridRow = TcmHerbGridDisplayRow;

/** 门诊中药处方 PrintData（字段与模板 field 一致） */
export interface OutpatientTcmPrescriptionPrintData {
  prescriptionNo: string;
  hospitalName: string;
  prescriptionTypeLine1: string;
  prescriptionTypeLine2: string;
  prescriptionDocumentTitle: string;
  patientName: string;
  ageText: string;
  genderEnumText: string;
  orderingDepartmentName: string;
  /** 就诊类型展示（优先 med_type / YbMedType；无则费用性质；模板标题「就诊类型」） */
  feeNatureText: string;
  prescriptionDateText: string;
  encounterBusNo: string;
  patientPhone: string;
  conditionSummaryText: string;
  rpHeading: string;
  tcmHerbRows: OutpatientTcmPrescriptionHerbGridRow[];
  tcmUsageText: string;
  /** 代煎项目名称（与发药单一致，无金额时为空） */
  decoctionActivityName: string;
  /** 代煎费金额展示（有金额才展示代煎行） */
  decoctionChargeAmountLabel: string;
  /** 代煎数量（付数）展示 */
  decoctionChargeQuantityLabel: string;
  /**
   * 代煎费整行文案（与发药单一致，如「代煎中药 X6 代煎费：18.00」）。
   * 模板优先用本字段，避免 formatter 拼装遗漏。
   */
  tcmDecoctionFeeText: string;
  orderingPractitionerName: string;
  pricingPractitionerName: string;
  prescriptionTotalPriceText: string;
  dispensingPractitionerName: string;
  preparerPractitionerName: string;
}

function str(value: null | number | string | undefined): string {
  if (value === null || value === undefined) return '';
  return String(value).trim();
}

/** 中药处方打印源：slip 行 + 就诊诊断（诊断展示由前端合并，与诊断页一致） */
export interface OutpatientTcmPrescriptionPrintSource {
  slipRows: EncounterMedicationDispenseSlipRowDto[];
  encounterDiagnosisRows?: EncounterDiagnosisRowDto[];
  tcmDiagnosisGroups?: TcmDiagnosisGroupRowDto[];
}

function resolveSlipRows(
  source: EncounterMedicationDispenseSlipRowDto[] | OutpatientTcmPrescriptionPrintSource,
): EncounterMedicationDispenseSlipRowDto[] {
  if (Array.isArray(source)) return source;
  return source.slipRows;
}

function resolveConditionSummaryText(
  source: EncounterMedicationDispenseSlipRowDto[] | OutpatientTcmPrescriptionPrintSource,
): string {
  if (Array.isArray(source)) return '';
  const rows = source.encounterDiagnosisRows ?? [];
  const tcmGroups = source.tcmDiagnosisGroups ?? [];
  return pickMainTcmDiagnosisDisplayName(rows, tcmGroups);
}

function buildTcmHerbRows(rows: EncounterMedicationDispenseSlipRowDto[]): TcmHerbGridDisplayRow[] {
  if (TCM_RX_PRINT_HERB_WEIGHT_PER_DOSE === true) {
    return buildTcmHerbGridRowsFromSlipRowsForRxPrint(rows);
  }
  return buildTcmHerbGridRowsFromSlipRows(rows);
}

function buildTcmUsageText(head: EncounterMedicationDispenseSlipRowDto): string {
  return buildTcmPrescriptionUsageTextFromSlipHead(head);
}

/** 同方任一行有代煎金额即取作方头（enrich 可能未落在 rows[0]） */
function pickDecoctionSourceRow(
  rows: EncounterMedicationDispenseSlipRowDto[],
): EncounterMedicationDispenseSlipRowDto | undefined {
  for (const row of rows) {
    if (str(row.decoctionChargeAmount)) return row;
  }
  for (const row of rows) {
    if (row.sufferingFlag === true) return row;
  }
  return rows[0];
}

/** 代煎费展示字段（与门诊发药单一致：有金额才出整行） */
function buildDecoctionPrintFields(rows: EncounterMedicationDispenseSlipRowDto[]): {
  decoctionActivityName: string;
  decoctionChargeAmountLabel: string;
  decoctionChargeQuantityLabel: string;
  tcmDecoctionFeeText: string;
} {
  const empty = {
    decoctionActivityName: '',
    decoctionChargeAmountLabel: '',
    decoctionChargeQuantityLabel: '',
    tcmDecoctionFeeText: '',
  };
  const head = pickDecoctionSourceRow(rows);
  if (!head) return empty;
  const amountRaw = str(head.decoctionChargeAmount);
  if (!amountRaw) return empty;
  const doseQty = str(head.chineseHerbsDoseQuantity);
  const chargeQty = str(head.decoctionChargeQuantity);
  const qty = doseQty || chargeQty;
  const name = str(head.decoctionActivityName) || '代煎中药';
  const amountLabel = formatMoneyDisplay(head.decoctionChargeAmount);
  let feeText = name;
  if (qty) feeText += ` X${qty}`;
  feeText += ` 代煎费：${amountLabel}`;
  return {
    decoctionActivityName: name,
    decoctionChargeAmountLabel: amountLabel,
    decoctionChargeQuantityLabel: qty,
    tcmDecoctionFeeText: feeText,
  };
}

/** 门诊中药处方：slip 行（+ 可选就诊诊断）→ PrintData */
export function buildOutpatientTcmPrescriptionPrintData(
  source: EncounterMedicationDispenseSlipRowDto[] | OutpatientTcmPrescriptionPrintSource,
): OutpatientTcmPrescriptionPrintData {
  const rows = resolveSlipRows(source);
  const head = rows[0];
  if (!head) {
    return {
      prescriptionNo: '',
      hospitalName: '',
      prescriptionTypeLine1: '',
      prescriptionTypeLine2: '',
      prescriptionDocumentTitle: '中药处方笺',
      patientName: '',
      ageText: '',
      genderEnumText: '',
      orderingDepartmentName: '',
      feeNatureText: '',
      prescriptionDateText: '',
      encounterBusNo: '',
      patientPhone: '',
      conditionSummaryText: '',
      rpHeading: 'Rp :',
      tcmHerbRows: [],
      tcmUsageText: '',
      decoctionActivityName: '',
      decoctionChargeAmountLabel: '',
      decoctionChargeQuantityLabel: '',
      tcmDecoctionFeeText: '',
      orderingPractitionerName: '',
      pricingPractitionerName: '',
      prescriptionTotalPriceText: '',
      dispensingPractitionerName: '',
      preparerPractitionerName: '',
    };
  }
  const decoction = buildDecoctionPrintFields(rows);
  return {
    prescriptionNo: str(head.prescriptionNo),
    hospitalName: str(head.hospitalName),
    // 方头分类：与发药单同源（慢病 med_type → 慢病处方）
    prescriptionTypeLine1: resolvePrescriptionPaperTypeLine1({
      prescriptionTypeLine1: head.prescriptionTypeLine1,
      encounterMedicationTypeCode: head.encounterMedicationTypeCode,
    }),
    prescriptionTypeLine2: resolvePrescriptionPaperTypeLine2(head.prescriptionTypeLine2),
    prescriptionDocumentTitle: str(head.prescriptionDocumentTitle) || '中药处方笺',
    patientName: str(head.patientName),
    ageText: str(head.ageText),
    genderEnumText: str(head.genderEnumText),
    orderingDepartmentName: str(head.orderingDepartmentName),
    // 与发药单笺面一致：优先医保医疗类别（med_type）；无则费用性质；再空回落自费
    feeNatureText: str(head.encounterMedicationTypeText) || str(head.feeNatureText) || '自费',
    prescriptionDateText: str(head.prescriptionDateText),
    encounterBusNo: str(head.encounterBusNo),
    patientPhone: str(head.patientPhone),
    conditionSummaryText: resolveConditionSummaryText(source),
    rpHeading: 'Rp :',
    tcmHerbRows: buildTcmHerbRows(rows),
    tcmUsageText: buildTcmUsageText(head),
    ...decoction,
    orderingPractitionerName: str(head.orderingPractitionerName),
    pricingPractitionerName: str(head.pricingPractitionerName),
    prescriptionTotalPriceText: str(head.prescriptionTotalPriceText),
    dispensingPractitionerName: str(head.dispensingPractitionerName),
    preparerPractitionerName: str(head.preparerPractitionerName),
  };
}

/** 判定 slip 组是否中药处方（仅认 tcmFlag） */
export function isTcmPrescriptionSlipGroup(rows: EncounterMedicationDispenseSlipRowDto[]): boolean {
  return rows.some((row) => row.tcmFlag === true);
}
