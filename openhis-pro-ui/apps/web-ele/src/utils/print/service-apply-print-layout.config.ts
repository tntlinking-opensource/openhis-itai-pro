/**
 * 检查/检验申请单打印前是否改写成国基 A4 单栏版式。
 *
 * 产品默认 false：沿用库模板，不在前端改 JSON。
 * 国基 override 为 true。
 *
 * 须以 `#/utils/print/service-apply-print-layout.config` 导入。
 */
export const SERVICE_APPLY_PRINT_REWRITE_SINGLE_COLUMN = false;
