/**
 * 住院护士站患者队列静默轮询开关（院点可经 `#/` override 本文件）。
 *
 * <p>进入护士站即轮询，与是否勾选患者无关；刷新「校/执」供页签气泡。</p>
 */

export interface NurseStationQueuePollConfig {
  /** 是否启用静默轮询 */
  enabled: boolean;
  /** 页可见时轮询间隔（毫秒） */
  intervalMs: number;
}

/** 国基院点默认开启；其它院点关闭（仍可被 overrides 覆盖） */
const projectId = String(import.meta.env.VITE_ZXPLAT_PROJECT ?? '').trim();

export const NURSE_STATION_QUEUE_POLL: NurseStationQueuePollConfig = {
  enabled: projectId === 'H22010202493',
  /** 国基联调暂用 5s；验收后改回 15_000 */
  intervalMs: 5_000,
};
