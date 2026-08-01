/**
 * 门诊挂号「联系电话」是否前端强制必填。
 *
 * 主线默认 false（可选）。
 * 院点若需必填：在 projects/project-H{code}/overrides/views/outpatient/charge/register/constants/
 * register-phone.config.ts 覆盖为 true。
 * 注意：本常量须以 `#/views/.../register-phone.config` 导入，相对路径不走院点 override。
 */
export const REGISTRATION_PHONE_REQUIRED = false;
