/** 三大目录编辑业务码，对齐后端 CatalogEditabilityConstants */
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

const MEDICATION_STRUCTURE_LOCKED_FIELDS = new Set([
  'busNo',
  'categoryCode',
  'typeCode',
  'ybType',
  'totalVolume',
  'doseFormCode',
  'doseUnitCode',
  'purchaseUnitCode',
  'splitUnitCode',
  'packageUnitCode',
  'stockUnitCode',
  'doseUnitRatio',
  'purchaseUnitRatio',
  'stockUnitRatio',
  'packageUnitRatio',
  'partAttributeEnum',
  'thoPartAttributeEnum',
  'purchasePrice',
  'retailPrice',
  'mrSettlementTypeCode',
]);

const DEVICE_STRUCTURE_LOCKED_FIELDS = new Set([
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
  'busNo',
  'categoryCode',
  'typeEnum',
  'packageUnitCode',
  'itemTypeCode',
  'ybType',
  'purchasePrice',
  'retailPrice',
  'chrgitmLv',
  'mrSettlementTypeCode',
]);

export const MEDICATION_CATALOG_LOCKABLE_FORM_FIELDS = [
  ...MEDICATION_STRUCTURE_LOCKED_FIELDS,
  'chrgitmLv',
];

export const DEVICE_CATALOG_LOCKABLE_FORM_FIELDS = [...DEVICE_STRUCTURE_LOCKED_FIELDS, 'chrgitmLv'];

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

/** 药品目录字段是否应禁用 */
export function isMedicationCatalogFieldLocked(
  editabilityCode: string | undefined,
  fieldName: string,
): boolean {
  if (!isCatalogStructureLocked(editabilityCode)) {
    return false;
  }
  if (fieldName === 'chrgitmLv') {
    return (
      String(editabilityCode ?? '')
        .trim()
        .toUpperCase() === CATALOG_EDITABILITY_LOCK_CLINICAL
    );
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
  if (fieldName === 'chrgitmLv') {
    return (
      String(editabilityCode ?? '')
        .trim()
        .toUpperCase() === CATALOG_EDITABILITY_LOCK_CLINICAL
    );
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
