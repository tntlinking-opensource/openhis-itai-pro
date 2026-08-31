/** 库存单据打印：公共字符串/金额/日期格式化（mapper 内用） */
import { formatPurchaseMoneyDisplay } from '#/utils/format/money-scale';

export function invPrintStr(value: null | number | string | undefined): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value).trim();
}

/** 金额展示串（固定两位），空或非法返回 '' */
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

/**
 * 进价侧金额打印串（单价/总额/盈亏）：与 {@link formatPurchaseMoneyDisplay} 同口径。
 * 售价 / 非进价请继续用 {@link invPrintAmountText}。
 */
export function invPrintPurchaseMoneyText(
  value: null | number | string | undefined,
  categoryCode?: null | string,
): string {
  if (value === null || value === undefined || value === '') {
    return '';
  }
  return formatPurchaseMoneyDisplay(value, categoryCode ?? undefined);
}

/** @deprecated 请改用 {@link invPrintPurchaseMoneyText} */
export function invPrintPurchaseUnitPriceText(
  value: null | number | string | undefined,
  categoryCode?: null | string,
): string {
  return invPrintPurchaseMoneyText(value, categoryCode);
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
 * 打印用有效期（仅效期日，不含生产日）。
 * 若传入 `start~end` / `start～end` 预拼串，取末段。
 */
export function invPrintExpiryDateText(
  expiryDate?: null | string,
  expiryText?: null | string,
): string {
  const fromDate = invPrintStr(expiryDate).slice(0, 10);
  if (fromDate) {
    return fromDate;
  }
  const raw = invPrintStr(expiryText);
  if (!raw) {
    return '';
  }
  const parts = raw
    .split(/[~～\n]+/)
    .map((p) => p.trim())
    .filter(Boolean);
  if (parts.length === 0) {
    return '';
  }
  return parts[parts.length - 1]!.slice(0, 10);
}

/**
 * @deprecated 打印已改为仅展示有效期，请用 {@link invPrintExpiryDateText}
 */
export function invPrintExpiryRangeText(
  start: null | string | undefined,
  end: null | string | undefined,
): string {
  return invPrintExpiryDateText(end, start && end ? `${start}~${end}` : start || end || '');
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

/**
 * 打印用项目编码（药品/耗材 busNo）。
 * 优先 medicinePick.busNo → productCatalog.productBusNo → 行上 productBusNo；
 * 不用单据号字段 row.busNo（常为进货单号/请领单号）。
 */
export function resolveInvPrintProductBusNo(row?: null | Record<string, unknown>): string {
  if (!row) {
    return '';
  }
  const pick = row.medicinePick;
  if (pick && typeof pick === 'object') {
    const fromPick = invPrintStr(
      (pick as { busNo?: unknown; productBusNo?: unknown }).busNo ??
        (pick as { productBusNo?: unknown }).productBusNo,
    );
    if (fromPick) {
      return fromPick;
    }
  }
  const catalog = row.productCatalog;
  if (catalog && typeof catalog === 'object') {
    const fromCat = invPrintStr((catalog as { productBusNo?: unknown }).productBusNo);
    if (fromCat) {
      return fromCat;
    }
  }
  return invPrintStr(row.productBusNo);
}
