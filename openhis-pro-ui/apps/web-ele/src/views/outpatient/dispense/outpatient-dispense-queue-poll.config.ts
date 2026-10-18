/**
 * 门诊发药患者队列静默轮询开关（院点可经 `#/` override 本文件）。
 *
 * <p>进入发药页即轮询，与是否选中患者无关；无 loading、不清当前选中。</p>
 */

export interface OutpatientDispenseQueuePollConfig {
  /** 是否启用静默轮询 */
  enabled: boolean;
  /** 页可见时轮询间隔（毫秒） */
  intervalMs: number;
}

/** 国基院点默认开启；其它院点关闭（仍可被 overrides 覆盖） */
const projectId = String(import.meta.env.VITE_ZXPLAT_PROJECT ?? '').trim();

export const OUTPATIENT_DISPENSE_QUEUE_POLL: OutpatientDispenseQueuePollConfig = {
  enabled: projectId === 'H22010202493',
  /** 与护士站联调间隔一致；验收后改回 15_000 */
  intervalMs: 5_000,
};
