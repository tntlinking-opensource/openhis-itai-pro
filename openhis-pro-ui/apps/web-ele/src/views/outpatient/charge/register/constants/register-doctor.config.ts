/**
 * 门诊挂号「医生」是否前端强制必填。
 *
 * 主线默认 false（可选，挂到科室；BR-REG-001）。
 * 院点若需必填：在 projects/project-H{code}/overrides/views/outpatient/charge/register/constants/
 * register-doctor.config.ts 覆盖为 true。
 * 注意：本常量须以 `#/views/.../register-doctor.config` 导入，相对路径不走院点 override。
 */
export const REGISTRATION_DOCTOR_REQUIRED = false;
