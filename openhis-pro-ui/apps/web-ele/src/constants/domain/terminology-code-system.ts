/**
 * 术语编码体系名称，与后端 CommonConstants.TerminologyCodeSystemName 一致（对应术语库 CodeSystem.name）。
 */
export const TerminologyCodeSystemName = {
  MEDICATION_CATEGORY_CODE: 'medication_category_code',
  ACTIVITY_CATEGORY_CODE: 'activity_category_code',
  PUBLICATION_STATUS: 'publication_status',
  MEDICATION_DOMAIN: 'medication_domain',
  YES_NO_FLAG: 'yes_no_flag',
  RESTRICTED_ENUM: 'restricted_enum',
  PART_ATTRIBUTE_ENUM: 'part_attribute_enum',
  THO_PART_ATTRIBUTE_ENUM: 'tho_part_attribute_enum',
  CHRGITM_LV: 'chrgitm_lv',
  MED_CATEGORY_CODE: 'med_category_code',
  SYSTEM_CATEGORIES: 'system_categories',
  MEDICINE_PROPERTIES: 'medicine_properties',
  DOSE_FORM_CODE: 'dose_form_code',
  MED_CHRGITM_TYPE: 'med_chrgitm_type',
  METHOD_CODE: 'method_code',
  FIN_TYPE_CODE: 'fin_type_code',
  ANTIBIOTIC_TYPE_CODE: 'antibiotic_type_code',
  DDD_CODE: 'ddd_code',
  DOSE_FROM_CODE: 'dose_from_code',
  RX_FLAG: 'rx_flag',
  DOSAGE_INSTRUCTION: 'dosage_instruction',
  UNIT_CODE: 'unit_code',
  RATE_CODE: 'rate_code',
  CONDITION_DEFINITION_SOURCE: 'condition_definition_source',
  DIAG_TYPE: 'diag_type',
  PURCHASE_UNIT: 'purchase_unit',
  SALES_UNIT: 'sales_unit',
  SPLIT_UNIT: 'split_unit',
  STOCK_UNIT: 'stock_unit',
  DOSE_UNIT: 'dose_unit',
  /** 耗材分类 → 实体 category_code */
  DEVICE_CATEGORY_CODE: 'device_category_code',
  /** 耗材种类/类型 → 实体 type_code */
  DEVICE_TYPE_CODE: 'device_type_code',
} as const;
