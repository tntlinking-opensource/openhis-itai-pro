/** 医嘱数量单位展示：优先 unitName，回落 unitCode；EA（each 遗留占位）统一展示为「次」。 */
export function resolveAdviceQuantityUnitLabel(
  unitName?: null | string,
  unitCode?: null | string,
): string {
  const name = String(unitName ?? '').trim();
  if (name) {
    return name;
  }
  const code = String(unitCode ?? '').trim();
  if (!code) {
    return '';
  }
  if (code.toUpperCase() === 'EA') {
    return '次';
  }
  return code;
}
