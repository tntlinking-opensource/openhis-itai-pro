/**
 * 选项项接口（格式化后，用于前端组件）
 */
export interface OptionItem {
  label: string;
  value: number | string;
}

/**
 * 将接口返回的选项数组转换为标准格式 { label, value }
 * @param options 选项数组，每个项包含 label 和 value 字段
 * @returns 格式化后的选项数组
 */
export function formatOptions<T extends { label: string; value: number | string }>(
  options: null | T[] | undefined,
): OptionItem[] {
  if (!options || !Array.isArray(options)) {
    return [];
  }
  return options.map((item) => ({
    label: item.label,
    value: item.value,
  }));
}
