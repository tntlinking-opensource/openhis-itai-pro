/**
 * 下拉选项关键字匹配（名称 / 编号 / 拼音 / 五笔）。
 * 用于 Element Plus filterable Select 的自定义 filterMethod。
 */

export interface SelectOptionKeywordFields {
  busNo?: null | string;
  label?: null | string;
  pyStr?: null | string;
  value?: null | number | string;
  wbStr?: null | string;
}

/**
 * 判断选项是否命中关键字（空关键字视为全部命中）。
 *
 * @param option 下拉项
 * @param keyword 用户输入
 */
export function matchSelectOptionKeyword(
  option: SelectOptionKeywordFields,
  keyword: string,
): boolean {
  const q = String(keyword ?? '')
    .trim()
    .toLowerCase();
  if (!q) {
    return true;
  }
  // 与术语下拉口径对齐：名称、编号、拼音、五笔、value
  const fields = [option.label, option.busNo, option.pyStr, option.wbStr, option.value];
  for (const field of fields) {
    if (field != null && String(field).toLowerCase().includes(q)) {
      return true;
    }
  }
  return false;
}
