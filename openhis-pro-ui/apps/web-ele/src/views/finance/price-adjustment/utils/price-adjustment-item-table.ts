/** 药品/耗材：批号多行、进价与售价双侧调价 */
const LOGISTICS_ITEM_TABLES = new Set(['med_medication_definition', 'adm_device_definition']);

/** 诊疗/医疗服务：一对一、仅调前/目标售价 */
const CLINICAL_ITEM_TABLES = new Set(['wkf_activity_definition', 'adm_healthcare_service']);

export type PriceAdjustmentStyle = 'clinical' | 'logistics';

function normalizeItemTable(itemTable: unknown): string {
  return String(itemTable ?? '').trim();
}

/** 是否物流类项目表（药品/耗材） */
export function isLogisticsItemTable(itemTable: unknown): boolean {
  return LOGISTICS_ITEM_TABLES.has(normalizeItemTable(itemTable));
}

/** 是否诊疗类项目表 */
export function isClinicalItemTable(itemTable: unknown): boolean {
  return CLINICAL_ITEM_TABLES.has(normalizeItemTable(itemTable));
}

/** 按 itemTable 解析调价口径（对齐后端 PriceAdjustmentItemTableSupport） */
export function resolveAdjustmentStyle(itemTable: unknown): PriceAdjustmentStyle | undefined {
  if (isClinicalItemTable(itemTable)) return 'clinical';
  if (isLogisticsItemTable(itemTable)) return 'logistics';
  return undefined;
}
