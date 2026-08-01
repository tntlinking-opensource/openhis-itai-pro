/** 三大目录编辑业务码，对齐后端 CatalogEditabilityConstants
 *
 * 编辑（药/材）：先 LOCK_CLINICAL（开过）再 LOCK_INVENTORY（入库）。
 * LOCK_INVENTORY：名称/分类/业务单位/拆零/价格等不可改；临床与规格段仍可改。
 * LOCK_CLINICAL：在入库锁基础上另锁药品性质/规格/剂型/剂量单位与倍数。
 * 拆分属性、医保等级、追溯码单位/倍数两种锁定态均可改。
 * 追溯码贴码单位与倍数游离于五类业务单位，与发布/业务引用锁定无关。
 * 删除：任一锁定码均整单禁删（与编辑优先级无关）。
 */
export const CATALOG_EDITABILITY_OK = 'OK';
export const CATALOG_EDITABILITY_LOCK_CLINICAL = 'LOCK_CLINICAL';
export const CATALOG_EDITABILITY_LOCK_INVENTORY = 'LOCK_INVENTORY';

export type CatalogEditabilityCode =
  | typeof CATALOG_EDITABILITY_OK
  | typeof CATALOG_EDITABILITY_LOCK_CLINICAL
  | typeof CATALOG_EDITABILITY_LOCK_INVENTORY;

export interface CatalogEditabilityDto {
  editabilityCode?: string;
  editabilityMessage?: string;
}

/** 仅医生开立（LOCK_CLINICAL）后锁定的临床与规格字段 */
const MEDICATION_CLINICAL_LOCKED_FIELDS = new Set([
  'pharmacologyCategoryCode',
  'totalVolume',
  'doseFormCode',
  'doseUnitCode',
  'doseUnitRatio',
]);

/** 入库或开立后均锁定的结构/价格字段（不含临床与规格段） */
const MEDICATION_STRUCTURE_LOCKED_FIELDS = new Set([
  'name',
  'merchandiseName',
  'busNo',
  'categoryCode',
  'typeCode',
  'ybType',
  'purchaseUnitCode',
  'splitUnitCode',
  'packageUnitCode',
  'stockUnitCode',
  'purchaseUnitRatio',
  'stockUnitRatio',
  'packageUnitRatio',
  'purchasePrice',
  'retailPrice',
  'mrSettlementTypeCode',
]);

const DEVICE_STRUCTURE_LOCKED_FIELDS = new Set([
  'name',
  'merchandiseName',
  'busNo',
  'categoryCode',
  'typeCode',
  'itemTypeCode',
  'ybType',
  'totalVolume',
  'doseUnitCode',
  'doseUnitRatio',
  'purchaseUnitCode',
  'splitUnitCode',
  'packageUnitCode',
  'stockUnitCode',
  'purchaseUnitRatio',
  'stockUnitRatio',
  'packageUnitRatio',
  'purchasePrice',
  'retailPrice',
  'unitCode',
  'minUnitCode',
  'mrSettlementTypeCode',
]);

const SERVICE_STRUCTURE_LOCKED_FIELDS = new Set([
  'name',
  'busNo',
  'categoryCode',
  'typeEnum',
  'packageUnitCode',
  'itemTypeCode',
  'ybType',
  'retailPrice',
  'mrSettlementTypeCode',
]);

export const MEDICATION_CATALOG_LOCKABLE_FORM_FIELDS = [
  ...MEDICATION_STRUCTURE_LOCKED_FIELDS,
  ...MEDICATION_CLINICAL_LOCKED_FIELDS,
];

export const DEVICE_CATALOG_LOCKABLE_FORM_FIELDS = [...DEVICE_STRUCTURE_LOCKED_FIELDS];

export const SERVICE_CATALOG_LOCKABLE_FORM_FIELDS = [...SERVICE_STRUCTURE_LOCKED_FIELDS];

/** 是否存在业务引用导致的结构/价格锁定 */
export function isCatalogStructureLocked(editabilityCode?: string | null): boolean {
  const code = String(editabilityCode ?? CATALOG_EDITABILITY_OK)
    .trim()
    .toUpperCase();
  return code !== CATALOG_EDITABILITY_OK && code !== '';
}

/** 从详情 DTO 解析可编辑性状态 */
export function applyCatalogEditabilityFromDetail(
  detail: CatalogEditabilityDto | null | undefined,
): { code: string; message: string } {
  const raw = String(detail?.editabilityCode ?? CATALOG_EDITABILITY_OK).trim();
  const upper = raw.toUpperCase();
  let code = CATALOG_EDITABILITY_OK;
  if (upper === CATALOG_EDITABILITY_LOCK_CLINICAL) {
    code = CATALOG_EDITABILITY_LOCK_CLINICAL;
  } else if (upper === CATALOG_EDITABILITY_LOCK_INVENTORY) {
    code = CATALOG_EDITABILITY_LOCK_INVENTORY;
  }
  return {
    code,
    message: String(detail?.editabilityMessage ?? '').trim(),
  };
}

/** 是否医生开立锁定（临床与规格段） */
export function isCatalogClinicalLocked(editabilityCode?: string | null): boolean {
  const code = String(editabilityCode ?? CATALOG_EDITABILITY_OK)
    .trim()
    .toUpperCase();
  return code === CATALOG_EDITABILITY_LOCK_CLINICAL;
}

/** 药品目录字段是否应禁用 */
export function isMedicationCatalogFieldLocked(
  editabilityCode: string | undefined,
  fieldName: string,
): boolean {
  if (!isCatalogStructureLocked(editabilityCode)) {
    return false;
  }
  if (MEDICATION_CLINICAL_LOCKED_FIELDS.has(fieldName)) {
    return isCatalogClinicalLocked(editabilityCode);
  }
  return MEDICATION_STRUCTURE_LOCKED_FIELDS.has(fieldName);
}

/** 器材目录字段是否应禁用 */
export function isDeviceCatalogFieldLocked(
  editabilityCode: string | undefined,
  fieldName: string,
): boolean {
  if (!isCatalogStructureLocked(editabilityCode)) {
    return false;
  }
  return DEVICE_STRUCTURE_LOCKED_FIELDS.has(fieldName);
}

/** 诊疗目录字段是否应禁用 */
export function isServiceCatalogFieldLocked(
  editabilityCode: string | undefined,
  fieldName: string,
): boolean {
  if (!isCatalogStructureLocked(editabilityCode)) {
    return false;
  }
  return SERVICE_STRUCTURE_LOCKED_FIELDS.has(fieldName);
}

/** 合并 componentProps.disabled，用于表单 schema */
export function mergeCatalogFieldDisabled<T extends Record<string, unknown>>(
  fieldName: string,
  componentProps: T,
  isFieldLocked: (field: string) => boolean,
): T {
  if (!isFieldLocked(fieldName)) {
    return componentProps;
  }
  return { ...componentProps, disabled: true };
}

/** 批量生成 formApi.updateSchema 补丁（仅 disabled，不覆盖其它 props） */
export function buildCatalogEditabilitySchemaPatches(
  catalog: 'device' | 'medication' | 'service',
  editabilityCode: string,
): Array<{ componentProps: { disabled: boolean }; fieldName: string }> {
  const fields =
    catalog === 'medication'
      ? MEDICATION_CATALOG_LOCKABLE_FORM_FIELDS
      : catalog === 'device'
        ? DEVICE_CATALOG_LOCKABLE_FORM_FIELDS
        : SERVICE_CATALOG_LOCKABLE_FORM_FIELDS;
  const isLocked =
    catalog === 'medication'
      ? (field: string) => isMedicationCatalogFieldLocked(editabilityCode, field)
      : catalog === 'device'
        ? (field: string) => isDeviceCatalogFieldLocked(editabilityCode, field)
        : (field: string) => isServiceCatalogFieldLocked(editabilityCode, field);
  return fields.map((fieldName) => ({
    fieldName,
    componentProps: { disabled: isLocked(fieldName) },
  }));
}
