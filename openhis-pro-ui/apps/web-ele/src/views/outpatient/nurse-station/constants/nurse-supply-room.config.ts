/**
 * 门诊护士站「耗材房」是否前端强制必选。
 *
 * 主线默认 false（可选；未选时后端回落医生签发库位）。
 * 院点若需必选：在 projects/project-H{code}/overrides/views/outpatient/nurse-station/constants/
 * nurse-supply-room.config.ts 覆盖为 true。
 * 注意：本常量须以 `#/views/.../nurse-supply-room.config` 导入，相对路径不走院点 override。
 */
export const NURSE_SUPPLY_ROOM_REQUIRED = false;
