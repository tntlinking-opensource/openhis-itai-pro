/**
 * 药品页面展示名：有商品名则括号追加，无则不拼空括号。
 * 仅用于查处方 / 发药笺等页面展示；打印勿用本口径。
 */

/** 去空白；null/undefined → 空串 */
function trimText(value: null | string | undefined): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value).trim();
}

/**
 * 拼药品页面展示名：`通用名(商品名)`；无商品名或与通用名相同时仅通用名。
 *
 * @param productName 通用名 / medicationName
 * @param merchandiseName 商品名；空则不拼括号
 */
export function formatMedicationNameWithMerchandise(
  productName?: null | string,
  merchandiseName?: null | string,
): string {
  const name = trimText(productName);
  const merchandise = trimText(merchandiseName);
  // 无商品名：不拼空括号
  if (!merchandise) {
    return name;
  }
  // 仅有商品名
  if (!name) {
    return merchandise;
  }
  // 与通用名相同：不重复括号
  if (merchandise === name) {
    return name;
  }
  return `${name}(${merchandise})`;
}
