/**
 * 开立医嘱选品目录额外排除的诊疗 category_code。
 *
 * 产品默认空：检验/检查/治疗可在医嘱下拉与申请单两边开立。
 * 院点可按需覆盖排除子集（如仅检验 22 / 检查 23，治疗仍可下拉开立）。
 *
 * 申请单选品请勿传本列表（与 categoryEnum 自相矛盾会搜空）。
 * 注意：本常量须以 `#/views/.../advice-catalog-exclude-service-apply-categories.config` 导入，
 * 相对路径不走院点 override。
 */
export const ADVICE_CATALOG_EXCLUDE_SERVICE_APPLY_CATEGORY_CODES: readonly string[] = [];
