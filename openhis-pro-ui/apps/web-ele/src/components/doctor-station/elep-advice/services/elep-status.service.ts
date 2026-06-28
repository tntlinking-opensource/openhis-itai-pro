import type { ElepLocalStatus } from '#/api/doctor-station/elep.types';

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

export function resolveElepStatusLabel(status?: ElepLocalStatus | string | null): string {
  if (!status) return '—';
  return STATUS_LABEL[String(status).trim()] ?? String(status);
}

export function resolveElepStatusTagType(
  status?: ElepLocalStatus | string | null,
): 'danger' | 'info' | 'success' | 'warning' {
  if (!status) return 'info';
  return STATUS_TAG[String(status).trim()] ?? 'info';
}

export function formatElepTime(value?: string | null): string {
  const text = (value ?? '').trim();
  if (!text) return '—';
  const date = new Date(text);
  if (Number.isNaN(date.getTime())) return text;
  return date.toLocaleString('zh-CN', { hour12: false });
}

export function canElepPreCheck(status?: string | null): boolean {
  return status === 'DRAFT' || status === 'FAILED';
}

export function canElepSign(status?: string | null): boolean {
  return status === 'PRE_CHECKED';
}

export function canElepUpload(status?: string | null): boolean {
  return status === 'SIGNED';
}

export function canElepRevoke(status?: string | null): boolean {
  return status === 'UPLOADED';
}

export function canElepQueryPlatform(status?: string | null): boolean {
  return status === 'UPLOADED';
}

export function canElepEditDraft(status?: string | null): boolean {
  return status === 'DRAFT' || status === 'FAILED';
}

export function canElepGeneratePdf(status?: string | null, pdfFilePath?: string | null): boolean {
  return status === 'PRE_CHECKED' && !(pdfFilePath ?? '').trim();
}
