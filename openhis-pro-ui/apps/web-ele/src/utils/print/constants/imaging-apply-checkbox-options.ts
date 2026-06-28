/** 影像检查申请单：扫描方式勾选（模板 scanMethodText） */
export const IMAGING_SCAN_METHOD_OPTIONS = [
  { key: 'dr', text: 'DR拍片' },
  { key: 'ct_plain', text: 'CT平扫' },
  { key: 'ct_enhanced', text: 'CT增强' },
] as const;

/** 影像检查申请单：碘过敏史（模板 iodineAllergyText） */
export const IMAGING_IODINE_ALLERGY_OPTIONS = [
  { key: 'no', text: '无' },
  { key: 'yes', text: '有' },
] as const;

/** 影像检查申请单：检查目的（模板 examPurposeText） */
export const IMAGING_EXAM_PURPOSE_OPTIONS = [
  { key: 'physical', text: '体检' },
  { key: 'trauma', text: '外伤' },
  { key: 'pain', text: '疼痛' },
  { key: 'follow_up', text: '术后复查' },
] as const;

/** 影像检查申请单：超声第一行（模板 ultrasoundRow1Text） */
export const IMAGING_ULTRASOUND_ROW1_OPTIONS = [
  { key: 'hepatobiliary', text: '肝胆胰脾' },
  { key: 'renal_bladder', text: '双肾膀胱' },
  { key: 'thyroid', text: '甲状腺' },
  { key: 'breast', text: '乳腺' },
] as const;

/** 影像检查申请单：超声第二行（模板 ultrasoundRow2Text） */
export const IMAGING_ULTRASOUND_ROW2_OPTIONS = [
  { key: 'heart', text: '心脏' },
  { key: 'gyn_obs', text: '妇科/产科' },
  { key: 'lower_limb_vessel', text: '下肢血管' },
] as const;

/**
 * 诊疗 activityTypeCode → 扫描方式 key（dr | ct_plain | ct_enhanced）。
 * 随院内术语/类目维护；未配置时该行勾选留空，项目名称写入 examSiteText。
 */
export const ACTIVITY_TYPE_TO_SCAN_METHOD_KEY: Record<string, string> = {};

/**
 * 身体部位 targetId（dia_body_structure）→ 超声勾选 key。
 * 优先于 label 精确匹配；随术语表维护。
 */
export const BODY_STRUCTURE_TARGET_TO_ULTRASOUND_KEY: Record<string, string> = {};

export type ImagingCheckboxOption = { key: string; text: string };
