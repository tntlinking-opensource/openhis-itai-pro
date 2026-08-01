/** 库存单据打印：公共字符串/金额/日期格式化（mapper 内用） */
export function invPrintStr(value: null | number | string | undefined): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value).trim();
}

/** 金额展示串，空或非法返回 '' */
export function invPrintAmountText(value: null | number | string | undefined): string {
  if (value === null || value === undefined || value === '') {
    return '';
  }
  const num = typeof value === 'number' ? value : Number.parseFloat(String(value).trim());
  if (!Number.isFinite(num)) {
    return '';
  }
  return num.toFixed(2);
}

/** 数量+单位，如「10盒」 */
export function invPrintQtyUnitText(
  qty: null | number | string | undefined,
  unitLabel: null | string | undefined,
): string {
  const q = invPrintStr(qty);
  const u = invPrintStr(unitLabel);
  if (!q && !u) {
    return '';
  }
  if (!u) {
    return q;
  }
  if (!q) {
    return u;
  }
  return `${q}${u}`;
}

/**
 * 生产日~效期：模板 formatter 按 ~ 拆两行。
 * 仅一端有值时仍返回单端。
 */
export function invPrintExpiryRangeText(
  start: null | string | undefined,
  end: null | string | undefined,
): string {
  const s = invPrintStr(start).slice(0, 10);
  const e = invPrintStr(end).slice(0, 10);
  if (s && e) {
    return `${s}~${e}`;
  }
  return s || e;
}

/** 具体单据标题：位置名 + 单据名，如「中心药库进货单」 */
export function buildInvDocumentTitle(locationName: string, docKindLabel: string): string {
  const loc = invPrintStr(locationName);
  const kind = invPrintStr(docKindLabel) || '单据';
  if (!loc) {
    return kind;
  }
  return `${loc}${kind}`;
}
