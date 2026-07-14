import type {
  DailySettlementChannelRowDto,
  DailySettlementSummaryDto,
} from '#/api/outpatient/daily-settlement';

/** 门诊费用日结单 PrintData（与 op-dailySettlement-print 模板 field 一致） */
export interface OutpatientDailySettlementPrintData {
  hospitalName: string;
  cashierName: string;
  settlementPeriodText: string;
  westernMedicineAmountText: string;
  outsourcedDrugAmountText: string;
  herbalMedicineAmountText: string;
  labTestAmountText: string;
  surgeryAmountText: string;
  diagnosisAmountText: string;
  treatmentAmountText: string;
  nursingAmountText: string;
  bedAmountText: string;
  registrationFeeAmountText: string;
  consultationFeeAmountText: string;
  otherFeeAmountText: string;
  anesthesiaAmountText: string;
  dietAmountText: string;
  bloodTransfusionAmountText: string;
  selfPreparedOrderAmountText: string;
  medicalConsumablesAmountText: string;
  circulatingMaterialAmountText: string;
  materialAmountText: string;
  officeSuppliesAmountText: string;
  examinationAmountText: string;
  proprietaryChineseMedicineAmountText: string;
  ctAmountText: string;
  electrodiagnosisAmountText: string;
  totalIncomeAmountText: string;
  wechatAmountText: string;
  alipayAmountText: string;
  onlineBankingAmountText: string;
  selfPayCashTotalAmountText: string;
  selfPayVisitCountText: string;
  provincialInsuranceVisitCountText: string;
  municipalInsuranceVisitCountText: string;
  provincialInsuranceCashAmountText: string;
  provincialInsurancePoolingAmountText: string;
  provincialInsuranceAccountAmountText: string;
  provincialInsuranceFundAmountText: string;
  municipalInsuranceCashAmountText: string;
  municipalInsurancePoolingAmountText: string;
  municipalInsuranceAccountAmountText: string;
  municipalInsuranceFundAmountText: string;
  provincialInsuranceTotalAmountText: string;
  municipalInsuranceTotalAmountText: string;
  cashIncomeTotalAmountText: string;
  physicalExamVisitCountText: string;
}

/** 日结 mapper 入参（P：日结预览接口 rows + summary） */
export interface OutpatientDailySettlementPrintSource {
  hospitalName?: string;
  cashierName?: string;
  settlementPeriodText?: string;
  rows?: DailySettlementChannelRowDto[];
  summary?: DailySettlementSummaryDto;
}

type FinTypePrintField = keyof Pick<
  OutpatientDailySettlementPrintData,
  | 'anesthesiaAmountText'
  | 'bedAmountText'
  | 'bloodTransfusionAmountText'
  | 'circulatingMaterialAmountText'
  | 'consultationFeeAmountText'
  | 'ctAmountText'
  | 'diagnosisAmountText'
  | 'dietAmountText'
  | 'electrodiagnosisAmountText'
  | 'examinationAmountText'
  | 'herbalMedicineAmountText'
  | 'labTestAmountText'
  | 'materialAmountText'
  | 'medicalConsumablesAmountText'
  | 'nursingAmountText'
  | 'officeSuppliesAmountText'
  | 'otherFeeAmountText'
  | 'outsourcedDrugAmountText'
  | 'proprietaryChineseMedicineAmountText'
  | 'registrationFeeAmountText'
  | 'selfPreparedOrderAmountText'
  | 'surgeryAmountText'
  | 'treatmentAmountText'
  | 'westernMedicineAmountText'
>;

/** fin_type_code → 模板 field（与 PrintJson 静态标签一致） */
const FIN_TYPE_CODE_TO_FIELD: Record<string, FinTypePrintField> = {
  '1001': 'westernMedicineAmountText',
  '1002': 'outsourcedDrugAmountText',
  '1003': 'herbalMedicineAmountText',
  '1004': 'labTestAmountText',
  '1005': 'surgeryAmountText',
  '1007': 'diagnosisAmountText',
  '1008': 'treatmentAmountText',
  '1009': 'nursingAmountText',
  '1010': 'bedAmountText',
  '1011': 'registrationFeeAmountText',
  '1012': 'consultationFeeAmountText',
  '1013': 'otherFeeAmountText',
  '2001': 'anesthesiaAmountText',
  '2002': 'dietAmountText',
  '2003': 'bloodTransfusionAmountText',
  '2004': 'selfPreparedOrderAmountText',
  '2005': 'medicalConsumablesAmountText',
  '2006': 'circulatingMaterialAmountText',
  '2007': 'materialAmountText',
  '2008': 'officeSuppliesAmountText',
  '2009': 'examinationAmountText',
  '2010': 'proprietaryChineseMedicineAmountText',
  '2011': 'ctAmountText',
  '2012': 'electrodiagnosisAmountText',
};

function str(value: null | number | string | undefined): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value).trim();
}

function formatMoneyText(value: null | number | string | undefined, unit = '元'): string {
  const raw = str(value);
  if (!raw) {
    return `0.00 ${unit}`;
  }
  if (raw.includes(unit)) {
    return raw;
  }
  return `${raw} ${unit}`;
}

function formatCountText(value: null | number | string | undefined, unit = '人'): string {
  const raw = str(value);
  if (!raw) {
    return `0 ${unit}`;
  }
  if (raw.includes(unit)) {
    return raw;
  }
  return `${raw} ${unit}`;
}

function createEmptyFinTypeAmounts(): Record<FinTypePrintField, string> {
  return {
    westernMedicineAmountText: '0.00 元',
    outsourcedDrugAmountText: '0.00 元',
    herbalMedicineAmountText: '0.00 元',
    labTestAmountText: '0.00 元',
    surgeryAmountText: '0.00 元',
    diagnosisAmountText: '0.00 元',
    treatmentAmountText: '0.00 元',
    nursingAmountText: '0.00 元',
    bedAmountText: '0.00 元',
    registrationFeeAmountText: '0.00 元',
    consultationFeeAmountText: '0.00 元',
    otherFeeAmountText: '0.00 元',
    anesthesiaAmountText: '0.00 元',
    dietAmountText: '0.00 元',
    bloodTransfusionAmountText: '0.00 元',
    selfPreparedOrderAmountText: '0.00 元',
    medicalConsumablesAmountText: '0.00 元',
    circulatingMaterialAmountText: '0.00 元',
    materialAmountText: '0.00 元',
    officeSuppliesAmountText: '0.00 元',
    examinationAmountText: '0.00 元',
    proprietaryChineseMedicineAmountText: '0.00 元',
    ctAmountText: '0.00 元',
    electrodiagnosisAmountText: '0.00 元',
  };
}

function mapFinTypeRows(
  rows: DailySettlementChannelRowDto[] | undefined,
): Record<FinTypePrintField, string> {
  const amounts = createEmptyFinTypeAmounts();
  for (const row of rows ?? []) {
    const code = str(row.categoryCode);
    const field = FIN_TYPE_CODE_TO_FIELD[code];
    if (!field) {
      continue;
    }
    const unit = str(row.unit) || '元';
    amounts[field] = formatMoneyText(row.amount, unit);
  }
  return amounts;
}

/** 日结预览 → PrintData */
export function buildOutpatientDailySettlementPrintData(
  source: OutpatientDailySettlementPrintSource,
): OutpatientDailySettlementPrintData {
  const finTypeAmounts = mapFinTypeRows(source.rows);
  const summary = source.summary;
  return {
    hospitalName: str(source.hospitalName),
    cashierName: str(source.cashierName),
    settlementPeriodText: str(source.settlementPeriodText) || str(summary?.dateRange),
    ...finTypeAmounts,
    totalIncomeAmountText: formatMoneyText(summary?.totalIncome),
    wechatAmountText: formatMoneyText(summary?.wechatAmount),
    alipayAmountText: formatMoneyText(summary?.alipayAmount),
    onlineBankingAmountText: formatMoneyText(summary?.onlineBankAmount),
    selfPayCashTotalAmountText: formatMoneyText(summary?.selfPayCashTotal),
    selfPayVisitCountText: formatCountText(summary?.selfPayCount),
    provincialInsuranceVisitCountText: formatCountText(summary?.provincialInsuranceCount),
    municipalInsuranceVisitCountText: formatCountText(summary?.municipalInsuranceCount),
    provincialInsuranceCashAmountText: formatMoneyText(summary?.provincialInsuranceCash),
    provincialInsurancePoolingAmountText: formatMoneyText(summary?.provincialInsuranceFund),
    provincialInsuranceAccountAmountText: formatMoneyText(summary?.provincialInsuranceAccount),
    provincialInsuranceFundAmountText: formatMoneyText(summary?.provincialInsuranceFundTotal),
    municipalInsuranceCashAmountText: formatMoneyText(summary?.municipalInsuranceCash),
    municipalInsurancePoolingAmountText: formatMoneyText(summary?.municipalInsuranceFund),
    municipalInsuranceAccountAmountText: formatMoneyText(summary?.municipalInsuranceAccount),
    municipalInsuranceFundAmountText: formatMoneyText(summary?.municipalInsuranceFundTotal),
    provincialInsuranceTotalAmountText: formatMoneyText(summary?.provincialInsuranceTotal),
    municipalInsuranceTotalAmountText: formatMoneyText(summary?.municipalInsuranceTotal),
    cashIncomeTotalAmountText: formatMoneyText(summary?.totalCashIncome),
    physicalExamVisitCountText: formatCountText(summary?.physicalExamCount),
  };
}
