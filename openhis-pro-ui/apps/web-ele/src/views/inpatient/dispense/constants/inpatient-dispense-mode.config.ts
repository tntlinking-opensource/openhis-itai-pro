/**
 * 住院发药页默认模式。
 *
 * 主线默认 `'summary'`（按汇总单发药）。
 * 院点若需默认按患者发药：在
 * `projects/project-H{code}/overrides/views/inpatient/dispense/constants/`
 * `inpatient-dispense-mode.config.ts` 覆盖为 `'patient'`。
 * 注意：本常量须以 `#/views/.../inpatient-dispense-mode.config` 导入，相对路径不走院点 override。
 */
export type InpatientDispenseMode = 'patient' | 'summary';

/** 产品默认：按汇总单发药 */
export const INPATIENT_DISPENSE_DEFAULT_MODE: InpatientDispenseMode = 'summary';
