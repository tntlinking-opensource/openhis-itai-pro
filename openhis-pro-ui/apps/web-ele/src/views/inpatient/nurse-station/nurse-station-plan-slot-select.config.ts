/**
 * 住院护士站：医嘱执行 / 领药汇总加载后是否默认勾选全部可操作时点。
 *
 * 产品/红旗默认 true：加载后全选，护士可再取消个别点。
 * 国基 override 为 false：加载后不勾选，须按需勾选或点工具栏全选。
 *
 * 须以 `#/views/inpatient/nurse-station/nurse-station-plan-slot-select.config` 导入。
 */
export const NURSE_STATION_DEFAULT_SELECT_ALL_PLAN_SLOTS = true;
