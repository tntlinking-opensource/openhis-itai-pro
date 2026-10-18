import type { AdmissionDiagnosisFormState } from '#/components/admission/admission-diagnosis.types';

/** 住院证 PrintData（与 ip-admissionCertificate-print 模板 field 一致） */
export interface InpatientAdmissionCertificatePrintData {
  hospitalName: string;
  documentTitle: string;
  departmentName: string;
  inpatientNo: string;
  patientName: string;
  genderText: string;
  ageText: string;
  address: string;
  phone: string;
  diagnosisText: string;
  conditionText: string;
  admissionTimeText: string;
  /** 费用性质（医保/自费等） */
  paymentMethodText: string;
  /** 预交金额展示文案（如 2,000.00 元） */
  prepayAmountText: string;
  /** 模板 longText 占位；表格由模板 formatter 读其它字段渲染 */
  certificateTable: string;
}

/** 住院证 mapper 入参 */
export interface InpatientAdmissionCertificatePrintSource {
  hospitalName?: string;
  documentTitle?: string;
  departmentName?: string;
  inpatientNo?: string;
  patientName?: string;
  genderText?: string;
  ageText?: string;
  address?: string;
  phone?: string;
  diagnosisText?: string;
  conditionText?: string;
  admissionTimeText?: string;
  paymentMethodText?: string;
  /** 预交金额数字或已格式化文案；空则不打金额 */
  prepayAmountText?: string;
  prepayAmount?: null | number | string;
}

function str(value: null | number | string | undefined): string {
  if (value === null || value === undefined) {
    return '';
  }
  const text = String(value).trim();
  if (text === '—' || text === '-') {
    return '';
  }
  return text;
}

/** 预交金额展示：数字格式化为千分位 + 元；已含「元」则原样 */
export function formatAdmissionCertificatePrepayAmount(value: unknown): string {
  if (value === null || value === undefined) {
    return '';
  }
  const raw = String(value).trim();
  if (!raw) {
    return '';
  }
  if (raw.includes('元')) {
    return raw;
  }
  const num = Number(raw.replaceAll(',', ''));
  if (!Number.isFinite(num) || num <= 0) {
    return '';
  }
  return `${num.toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })} 元`;
}

/** 从入院诊断表单拼打印文案（只读展示 / 西医目录名 / 中医病+证） */
export function resolveAdmissionCertificateDiagnosisText(
  state: AdmissionDiagnosisFormState | null | undefined,
  westernOptions: Array<{ label?: string; value?: string }> = [],
): string {
  if (!state) {
    return '';
  }
  const readonlyDisplay = str(state.readonlyDisplay);
  if (readonlyDisplay) {
    return readonlyDisplay;
  }
  const description = str(state.diagnosisDescription);
  if (state.medicineType === 'tcm') {
    const parts = [str(state.tcmDiseaseName), str(state.tcmSyndromeName)].filter(Boolean);
    if (description) {
      parts.push(description);
    }
    return parts.join('，');
  }
  const westernId = String(state.westernDefinitionId ?? '').trim();
  const westernLabel =
    westernOptions.find((item) => String(item.value ?? '').trim() === westernId)?.label?.trim() ||
    '';
  const parts = [str(westernLabel)].filter(Boolean);
  if (description) {
    parts.push(description);
  }
  return parts.join('，');
}

/** 性别枚举/文案 → 住院证短文案（男/女） */
export function resolveAdmissionCertificateGenderText(value: unknown): string {
  const raw = str(value as string);
  if (!raw) {
    return '';
  }
  const upper = raw.toUpperCase();
  if (upper === 'MALE' || raw === '1' || raw.includes('男')) {
    return '男';
  }
  if (upper === 'FEMALE' || raw === '2' || raw.includes('女')) {
    return '女';
  }
  return raw;
}

/** 住院证 → PrintData */
export function buildInpatientAdmissionCertificatePrintData(
  source: InpatientAdmissionCertificatePrintSource,
): InpatientAdmissionCertificatePrintData {
  const prepayAmountText =
    str(source.prepayAmountText) || formatAdmissionCertificatePrepayAmount(source.prepayAmount);
  return {
    hospitalName: str(source.hospitalName),
    documentTitle: str(source.documentTitle) || '住院证',
    departmentName: str(source.departmentName),
    inpatientNo: str(source.inpatientNo),
    patientName: str(source.patientName),
    genderText: str(source.genderText),
    ageText: str(source.ageText),
    address: str(source.address),
    phone: str(source.phone),
    diagnosisText: str(source.diagnosisText),
    conditionText: str(source.conditionText),
    admissionTimeText: str(source.admissionTimeText),
    paymentMethodText: str(source.paymentMethodText),
    prepayAmountText,
    certificateTable: '',
  };
}
