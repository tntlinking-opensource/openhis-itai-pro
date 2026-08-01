/**
 * Campus 模块共用类型与常量
 */
export type IdLike = string | number;

export type GfChargeMode = 'SELF_PAY_GF' | 'AFTER_YB_GF';

export const GF_CHARGE_MODE_OPTIONS: Array<{ label: string; value: GfChargeMode; tip: string }> = [
  { label: '自费公费', value: 'SELF_PAY_GF', tip: '自费路径下按本比例拆公费' },
  { label: '医保后公费', value: 'AFTER_YB_GF', tip: '医保结算后对自付段再拆公费' },
];

export function gfChargeModeLabel(mode?: string | null): string {
  if (mode === 'AFTER_YB_GF') return '医保后公费';
  if (mode === 'SELF_PAY_GF') return '自费公费';
  return mode || '—';
}

export interface CampusPagedResult<T> {
  content?: T[];
  totalCount?: number;
  pageNo?: number;
  pageSize?: number;
}

export function unwrapCampusPage<T>(res: CampusPagedResult<T> | null | undefined): {
  list: T[];
  total: number;
} {
  return {
    list: Array.isArray(res?.content) ? res!.content! : [],
    total: Number(res?.totalCount ?? 0),
  };
}

export function unwrapRecords<T>(res: { records?: T[] } | null | undefined): T[] {
  return Array.isArray(res?.records) ? res!.records! : [];
}

/** 雪花 ID 一律字符串，避免精度丢失 */
export function toIdString(value: unknown): string {
  if (value == null) return '';
  return String(value).trim();
}

/** 甲乙丙自付比例类别 */
export const GF_CLASS_OPTIONS: Array<{ label: string; value: string }> = [
  { label: '甲类', value: 'A' },
  { label: '乙类', value: 'B' },
  { label: '丙类', value: 'C' },
];

export function gfClassLabel(classEnum?: string | null): string {
  const hit = GF_CLASS_OPTIONS.find((o) => o.value === classEnum);
  return hit?.label ?? classEnum ?? '—';
}

export function gfRequestStatusLabel(status?: string | null): string {
  if (status === 'PENDING') return '待审批';
  if (status === 'APPROVED') return '已通过';
  if (status === 'REJECTED') return '已驳回';
  return status || '—';
}
