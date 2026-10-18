/**
 * 术语编码体系名称，与后端 CommonConstants.TerminologyCodeSystemName 一致（对应术语库 CodeSystem.name）。
 */
export const TerminologyCodeSystemName = {
  MEDICATION_CATEGORY_CODE: 'medication_category_code',
  ACTIVITY_CATEGORY_CODE: 'activity_category_code',
  /** 诊疗业务类型 → ActivityDefinition.type_enum */
  ACTIVITY_TYPE_CODE: 'activity_type_code',
  PUBLICATION_STATUS: 'publication_status',
  MEDICATION_DOMAIN: 'medication_domain',
  YES_NO_FLAG: 'yes_no_flag',
  RESTRICTED_ENUM: 'restricted_enum',
  PART_ATTRIBUTE_ENUM: 'part_attribute_enum',
  THO_PART_ATTRIBUTE_ENUM: 'tho_part_attribute_enum',
  CHRGITM_LV: 'chrgitm_lv',
  SYSTEM_CATEGORIES: 'system_categories',
  MEDICINE_PROPERTIES: 'medicine_properties',
  DOSE_FORM_CODE: 'dose_form_code',
  MED_CHRGITM_TYPE: 'med_chrgitm_type',
  METHOD_CODE: 'method_code',
  FIN_TYPE_CODE: 'fin_type_code',
  ANTIBIOTIC_TYPE_CODE: 'antibiotic_type_code',
  DDD_CODE: 'ddd_code',
  /** DDD（限定日剂量）单位 → med_medication_definition.ddd_unit_code */
  DDD_UNIT: 'ddd_unit',
  DOSE_FROM_CODE: 'dose_from_code',
  RX_FLAG: 'rx_flag',
  DOSAGE_INSTRUCTION: 'dosage_instruction',
  UNIT_CODE: 'unit_code',
  CONDITION_DEFINITION_SOURCE: 'condition_definition_source',
  DIAG_TYPE: 'diag_type',
  DOSE_UNIT: 'dose_unit',
  /** 耗材分类 → 实体 category_code */
  DEVICE_CATEGORY_CODE: 'device_category_code',
  /** 耗材种类/类型 → 实体 type_code */
  DEVICE_TYPE_CODE: 'device_type_code',
  IN_WAY_CODE: 'in_way_code',
  ADMIT_SOURCE_CODE: 'admit_source_code',
  /** 入院时情况/患者病情（对应 adm_encounter.priority_enum） */
  ADM_COND_CODE: 'adm_cond_code',
  /** 生命体征项编码（护士站/体温单，对应 dia_vital_sign.vital_signs_code） */
  VITAL_SIGNS_CODE: 'vital_signs_code',
  /** 体温单特殊事件（对应 dia_vital_sign_detail term_code=special_event_code） */
  SPECIAL_EVENT_CODE: 'special_event_code',
  /** 手术级别（与手术申请 initSurgeryAnesth.procedureLevelOptions 同源） */
  OPRN_OPRT_LV_CODE: 'oprn_oprt_lv_code',
  /** 手术切口愈合等级（与手术申请切口类别同源） */
  SINC_HEAL_LV_CODE: 'sinc_heal_lv_code',
  /** 麻醉方法（与手术/麻醉申请 anesthesiaMethodOptions 同源） */
  ANST_MTD_CODE: 'anst_mtd_code',
  /** 重症监护病房类型（4101A scs_cutd_ward_type / ICU_TYPE） */
  ICU_TYPE: 'ICU_TYPE',
  /** 输血品种（4101A bld_cat / bld_cat_code） */
  BLD_CAT_CODE: 'bld_cat_code',
} as const;

/**
 * 数据字典页只读术语：不可新增/编辑/删除概念与体系。
 * 与后端 {@code PAGE_READONLY_CODE_SYSTEM_NAMES} 对齐。
 */
export const PAGE_READONLY_CODE_SYSTEM_NAMES = new Set<string>([
  TerminologyCodeSystemName.MEDICATION_CATEGORY_CODE,
  TerminologyCodeSystemName.DEVICE_CATEGORY_CODE,
  TerminologyCodeSystemName.ACTIVITY_CATEGORY_CODE,
]);

/**
 * 数据字典页禁止删除/停用术语。
 * 与后端 {@code PAGE_NO_DELETE_DEACTIVATE_CODE_SYSTEM_NAMES} 对齐。
 */
export const PAGE_NO_DELETE_DEACTIVATE_CODE_SYSTEM_NAMES = new Set<string>([
  TerminologyCodeSystemName.MEDICATION_CATEGORY_CODE,
  TerminologyCodeSystemName.DEVICE_CATEGORY_CODE,
  TerminologyCodeSystemName.ACTIVITY_CATEGORY_CODE,
  TerminologyCodeSystemName.FIN_TYPE_CODE,
  TerminologyCodeSystemName.ACTIVITY_TYPE_CODE,
  TerminologyCodeSystemName.DEVICE_TYPE_CODE,
]);

/** 判断编码体系 name 是否为页面只读预置字典。 */
export function isPageReadonlyCodeSystem(name?: null | string): boolean {
  return !!name && PAGE_READONLY_CODE_SYSTEM_NAMES.has(name);
}

/** 判断编码体系 name 是否禁止删除/停用概念。 */
export function isPageNoDeleteDeactivateCodeSystem(name?: null | string): boolean {
  return !!name && PAGE_NO_DELETE_DEACTIVATE_CODE_SYSTEM_NAMES.has(name);
}
