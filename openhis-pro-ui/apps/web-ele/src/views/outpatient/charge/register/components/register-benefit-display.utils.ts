/** 待遇确认展示区通用格式化 */

import { formatPsnInsuStas, formatPsnType } from '#/chs/constants/benefit-display';
import { formatMoneyYuanWithSymbol } from '#/utils/format/money-scale';

export {
  formatCumTypeCode,
  formatFundPayType,
  formatPsnInsuStas,
  formatPsnType,
} from '#/chs/constants/benefit-display';

export function displayBenefitDash(value: null | number | string | undefined): string {
  const text = String(value ?? '').trim();
  return text || '—';
}

export function formatBenefitBalc(value: null | number | string | undefined): string {
  if (value === undefined || value === null || value === '') {
    return '—';
  }
  const n = Number(value);
  if (Number.isFinite(n)) {
    return formatMoneyYuanWithSymbol(n);
  }
  const text = String(value).trim();
  return text ? `¥${text}` : '—';
}

export function formatTreatmentFlag(flag?: string): string {
  const value = String(flag ?? '').trim();
  if (value === '1') {
    return '享受';
  }
  if (value === '0') {
    return '不享受';
  }
  return value || '—';
}

/** 2001 trt_enjymnt_flag=1 表示享受待遇 */
export function isTreatmentEnjoyed(flag?: string): boolean {
  return String(flag ?? '').trim() === '1';
}

export function formatCvlservFlag(flag?: string): string {
  const value = String(flag ?? '').trim();
  if (value === '1') {
    return '是';
  }
  if (value === '0') {
    return '否';
  }
  return value || '—';
}

export function formatBenefitGend(code?: string): string {
  const value = String(code ?? '').trim();
  if (value === '1') {
    return '男';
  }
  if (value === '2') {
    return '女';
  }
  return value || '—';
}

/** 字典未接入时保留原码值，避免空白 */
export function formatBenefitCode(code?: null | string): string {
  const text = String(code ?? '').trim();
  return text || '—';
}

/** 1101 人员类别 */
export function formatBenefitPsnType(code?: null | string): string {
  return formatPsnType(code);
}

/** 1101 参保状态 */
export function formatBenefitPsnInsuStas(code?: null | string): string {
  return formatPsnInsuStas(code);
}

export function benefitCredentialLabel(mediumType?: string): string {
  return mediumType === '02' ? '就诊凭证' : '社保卡号';
}

export function benefitAreaAlertType(areaKind?: string): 'error' | 'info' | 'success' | 'warning' {
  if (areaKind === 'CROSS_PROVINCE' || areaKind === 'IN_PROVINCE') {
    return 'warning';
  }
  if (areaKind === 'LOCAL') {
    return 'success';
  }
  return 'info';
}
