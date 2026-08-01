/**
 * 供应商类型编码，与后端 {@code SupplierType#getCode()} 一致。
 * 表单/筛选须用 code，禁止用展示文案驱动逻辑。
 */
export const SupplierTypeCode = {
  /** 生产厂家 */
  MANUFACTURER: '1',
  /** 供应商 */
  SUPPLIER: '2',
} as const;

export type SupplierTypeCodeValue = (typeof SupplierTypeCode)[keyof typeof SupplierTypeCode];

/**
 * 将类型选项 value 规范为与后端 code 对齐的可比较值（init 下拉多为数字/字符串 code）。
 */
export function normalizeSupplierTypeValue(
  value: number | string | null | undefined,
): string | undefined {
  if (value === null || value === undefined || value === '') {
    return undefined;
  }
  return String(value).trim();
}
