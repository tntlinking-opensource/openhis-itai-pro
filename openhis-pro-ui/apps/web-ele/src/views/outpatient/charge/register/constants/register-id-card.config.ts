/**
 * 门诊挂号「身份证号」是否前端强制必填。
 *
 * 主线默认 true（产品）。
 * 院点若允许无证挂号：覆盖为 false；年龄在产品侧已必填，无证须手填年龄并由年龄回显生日。
 * 注意：本常量须以 `#/views/.../register-id-card.config` 导入，相对路径不走院点 override。
 */
export const REGISTRATION_ID_CARD_REQUIRED = true;
