/**
 * 患者管理「证件号」是否前端强制必填。
 *
 * 主线默认 true（产品）。
 * 院点若允许无证建档/改档：覆盖为 false；年龄与出生日期在产品侧已必填，无证须手填并由年龄↔生日联动。
 * 领域主档 `id_card` 本就可空（DM-ADM-001）；本开关仅管患者管理页表单。
 * 注意：本常量须以 `#/views/basic/patient/constants/patient-id-card.config` 导入，相对路径不走院点 override。
 */
export const PATIENT_ARCHIVE_ID_CARD_REQUIRED = true;
