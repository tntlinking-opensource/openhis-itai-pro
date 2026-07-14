import type { ApiOptionItem } from '#/types/api-response';

/** OptionDto → 下拉选项（value 一律 string）。 */
export function optionDtosToSelectItems(
  list?: { label?: string; value?: null | number | string }[],
): ApiOptionItem[] {
  return (list ?? []).map((o) => ({
    label: String(o.label ?? ''),
    value: String(o.value ?? ''),
  }));
}

/** 备份盘点单号下拉：value=单号，busNo=抓拍 HH:mm:ss，label 由页面拼接。 */
export function backupBusNoOptionsToSelectItems(
  list?: { busNo?: string; label?: string; value?: null | number | string }[],
): ApiOptionItem[] {
  return (list ?? []).map((o) => {
    const value = String(o.value ?? '');
    const timeSuffix = String(o.busNo ?? '').trim();
    const label = timeSuffix ? `${value} ${timeSuffix}` : String(o.label ?? value);
    return { label, value };
  });
}

/** 金额/数量展示：空则空白，否则本地化数字。 */
export function fmtReportNumber(v: unknown, fractionDigits = 2): string {
  if (v === null || v === undefined || v === '') return '';
  const n = typeof v === 'number' ? v : Number(v);
  if (!Number.isFinite(n)) return String(v);
  return n.toLocaleString('zh-CN', {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  });
}
