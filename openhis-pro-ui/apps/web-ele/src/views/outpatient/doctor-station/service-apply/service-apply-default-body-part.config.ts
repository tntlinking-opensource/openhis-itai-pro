/**
 * 检查申请单加入清单时，是否预填部位树的首个可选值。
 *
 * 产品默认 true（检查行默认带一条部位明细）。
 * 院点若不预填：覆盖为 false，部位留空，由医生自行选择。
 *
 * 注意：本常量须以 `#/views/.../service-apply-default-body-part.config` 导入，
 * 相对路径不走院点 override。
 */
export const SERVICE_APPLY_EXAM_DEFAULT_FIRST_BODY_PART = true;
