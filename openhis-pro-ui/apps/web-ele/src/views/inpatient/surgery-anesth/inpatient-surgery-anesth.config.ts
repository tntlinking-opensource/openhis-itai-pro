/**
 * 住院医生站 / 护士站是否展示手术、麻醉。
 *
 * 主线默认开启。红旗医院在
 * `projects/project-H22010402403/overrides/views/inpatient/surgery-anesth/`
 * 覆盖为 false。
 * 须以 `#/views/inpatient/surgery-anesth/inpatient-surgery-anesth.config` 导入，相对路径不走院点 override。
 */
export const INPATIENT_SURGERY_ANESTH_ENABLED = true;
