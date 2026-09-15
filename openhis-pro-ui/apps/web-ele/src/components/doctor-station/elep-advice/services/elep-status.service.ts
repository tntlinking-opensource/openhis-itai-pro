import type { ElepLocalStatus } from '#/api/doctor-station/elep.types';

/** 本地状态 code → 展示文案（仅模板用，禁止反查） */
const STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  PRE_CHECKED: '预核验',
  SIGNED: '已签名',
  UPLOADED: '已上传',
  REVOKED: '已撤销',
  FAILED: '失败',
};

const STATUS_TAG: Record<string, 'danger' | 'info' | 'success' | 'warning'> = {
  DRAFT: 'info',
  PRE_CHECKED: 'warning',
  SIGNED: 'warning',
  UPLOADED: 'success',
  REVOKED: 'info',
  FAILED: 'danger',
};

/** 电子处方医疗类别 code（与后端 ElepPrescriptionConstants 对齐） */
export const ELEP_MED_TYPE = {
  GENERAL: '11',
  CHRONIC_SPECIAL: '14',
  CHRONIC: '140104',
  SPECIAL: '140201',
} as const;

const CHRONIC_MED_TYPE_CODES = new Set<string>([
  ELEP_MED_TYPE.CHRONIC_SPECIAL,
  ELEP_MED_TYPE.CHRONIC,
  ELEP_MED_TYPE.SPECIAL,
]);

export const ELEP_MED_TYPE_OPTIONS: Array<{ value: string; label: string }> = [
  { value: ELEP_MED_TYPE.GENERAL, label: '普通门诊' },
  { value: ELEP_MED_TYPE.CHRONIC_SPECIAL, label: '门诊慢特病' },
  { value: ELEP_MED_TYPE.CHRONIC, label: '门诊慢病' },
  { value: ELEP_MED_TYPE.SPECIAL, label: '门诊特病' },
];

/** 医生可见的上传步骤（不含技术编号） */
export const ELEP_FLOW_STEPS = ['开方', '审方', '预核验', '签名', '上传'] as const;

/**
 * 契约下拉展示名：只用费用性质中文，不把定点编号甩给医生。
 *
 * @param item 费用性质选项
 */
export function resolveElepContractLabel(
  item?: {
    busNo?: string;
    label?: string;
    name?: string;
  } | null,
): string {
  const text = (item?.label || item?.name || '').trim();
  if (text && !looksLikeElepContractBusNo(text)) return text;
  return '医保';
}

/**
 * 是否像定点/契约编号（如 H22010402403-229900）。
 *
 * @param text 展示文案
 */
export function looksLikeElepContractBusNo(text?: string | null): boolean {
  const value = String(text ?? '').trim();
  if (!value) return false;
  return /^H\d{6,}/i.test(value) || /^\d{8,}-\d+$/.test(value);
}

/**
 * 医疗类别 code → 中文。
 *
 * @param medType 医疗类别 code
 */
export function resolveElepMedTypeLabel(medType?: string | null): string {
  const code = String(medType ?? '').trim();
  if (!code) return '普通门诊';
  return ELEP_MED_TYPE_OPTIONS.find((item) => item.value === code)?.label ?? '普通门诊';
}

/**
 * 当前上传步骤下标（0 开方 … 4 上传）。
 *
 * @param status 本地状态 code
 * @param pharCode 审方药师医保码
 */
export function resolveElepFlowStepIndex(status?: string | null, pharCode?: string | null): number {
  if (status === 'UPLOADED') return 4;
  if (status === 'SIGNED') return 3;
  if (status === 'PRE_CHECKED') return 2;
  if (hasElepReviewed(pharCode)) return 1;
  return 0;
}

/**
 * 侧栏处方标题：诊断名，没有则「新开处方」。
 *
 * @param diagName 主诊断名
 * @param unsaved 尚未落库
 */
export function resolveElepQueueTitle(diagName?: string | null, unsaved?: boolean): string {
  const name = String(diagName ?? '').trim();
  if (name) return name;
  return unsaved ? '新开处方' : '未选诊断';
}

/**
 * 列表时间短展示。
 *
 * @param value ISO 或后端时间串
 */
export function formatElepTimeShort(value?: string | null): string {
  const text = (value ?? '').trim();
  if (!text) return '';
  const date = new Date(text);
  if (Number.isNaN(date.getTime())) return text;
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const hour = String(date.getHours()).padStart(2, '0');
  const minute = String(date.getMinutes()).padStart(2, '0');
  return `${month}-${day} ${hour}:${minute}`;
}

/**
 * 本方是否慢病医疗类别（须填病种，仍用挂号 map(11)）。
 *
 * @param medType 医疗类别 code
 */
export function isElepChronicMedType(medType?: string | null): boolean {
  return CHRONIC_MED_TYPE_CODES.has(String(medType ?? '').trim());
}

/**
 * 状态 code 转展示文案。
 *
 * @param status 本地状态 code
 */
export function resolveElepStatusLabel(status?: ElepLocalStatus | string | null): string {
  if (!status) return '—';
  return STATUS_LABEL[String(status).trim()] ?? String(status);
}

/**
 * 状态 code 转 Tag 类型。
 *
 * @param status 本地状态 code
 */
export function resolveElepStatusTagType(
  status?: ElepLocalStatus | string | null,
): 'danger' | 'info' | 'success' | 'warning' {
  if (!status) return 'info';
  return STATUS_TAG[String(status).trim()] ?? 'info';
}

/**
 * 开方时间展示。
 *
 * @param value ISO 或后端时间串
 */
export function formatElepTime(value?: string | null): string {
  const text = (value ?? '').trim();
  if (!text) return '—';
  const date = new Date(text);
  if (Number.isNaN(date.getTime())) return text;
  return date.toLocaleString('zh-CN', { hour12: false });
}

/**
 * 是否已审方：以药师医保码为准，不用文案。
 *
 * @param pharCode 审方药师医保码
 */
export function hasElepReviewed(pharCode?: string | null): boolean {
  return Boolean(String(pharCode ?? '').trim());
}

/**
 * 草稿/失败且未审方可审方。
 *
 * @param status 本地状态 code
 * @param pharCode 审方药师医保码
 */
export function canElepReview(status?: string | null, pharCode?: string | null): boolean {
  return (status === 'DRAFT' || status === 'FAILED') && !hasElepReviewed(pharCode);
}

/**
 * 已审方的草稿才可预核验。
 *
 * @param status 本地状态 code
 * @param pharCode 审方药师医保码
 */
export function canElepPreCheck(status?: string | null, pharCode?: string | null): boolean {
  return (status === 'DRAFT' || status === 'FAILED') && hasElepReviewed(pharCode);
}

/**
 * 预核验后可签名。
 *
 * @param status 本地状态 code
 */
export function canElepSign(status?: string | null): boolean {
  return status === 'PRE_CHECKED';
}

/**
 * 签名后可上传。
 *
 * @param status 本地状态 code
 */
export function canElepUpload(status?: string | null): boolean {
  return status === 'SIGNED';
}

/**
 * 已上传可撤销。
 *
 * @param status 本地状态 code
 */
export function canElepRevoke(status?: string | null): boolean {
  return status === 'UPLOADED';
}

/**
 * 已上传可刷平台状态。
 *
 * @param status 本地状态 code
 */
export function canElepQueryPlatform(status?: string | null): boolean {
  return status === 'UPLOADED';
}

/**
 * 草稿/失败可改本页诊断药品。
 *
 * @param status 本地状态 code
 */
export function canElepEditDraft(status?: string | null): boolean {
  return !status || status === 'DRAFT' || status === 'FAILED';
}

/**
 * 预核验后且尚未落 PDF 时可生成。
 *
 * @param status 本地状态 code
 * @param pdfFilePath 已有 PDF 路径
 */
export function canElepGeneratePdf(status?: string | null, pdfFilePath?: string | null): boolean {
  return status === 'PRE_CHECKED' && !(pdfFilePath ?? '').trim();
}
