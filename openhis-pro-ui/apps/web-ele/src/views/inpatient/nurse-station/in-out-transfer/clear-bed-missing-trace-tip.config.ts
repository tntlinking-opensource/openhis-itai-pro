/**
 * 清床前是否拉取并展示缺追溯码软提示（IF-IP-NUR-ATD-06a）。
 *
 * <p>主线默认开启；国基等经 `#/` override 关闭。</p>
 * 须以 `#/views/inpatient/nurse-station/in-out-transfer/clear-bed-missing-trace-tip.config` 导入。
 */
export interface ClearBedMissingTraceTipConfig {
  /** 为 true 时清床前调用 listMissingDispenseTraceTips 并拼进确认文案 */
  enabled: boolean;
}

export const CLEAR_BED_MISSING_TRACE_TIP: ClearBedMissingTraceTipConfig = {
  enabled: true,
};
