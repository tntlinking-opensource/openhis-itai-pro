import { queryOrderApi, type UnionPayWinBaseResponse } from '#/api/local/unionPayWin';

export interface PollUnionPayOrderOptions {
  merTradeNo: string;
  tradeNo?: string;
  /** 首次查单延迟（毫秒） */
  initialDelayMs?: number;
  /** 轮询间隔（毫秒） */
  intervalMs?: number;
  /** 最大轮询时长（毫秒） */
  timeoutMs?: number;
  /** 外部取消信号 */
  isAborted?: () => boolean;
}

const DEFAULT_INITIAL_DELAY_MS = 2_000;
const DEFAULT_INTERVAL_MS = 2_000;
const DEFAULT_TIMEOUT_MS = 300_000;

/** 被扫超时后轮询 Win query-order（纯函数，可在 util 中调用） */
export async function pollUnionPayOrder(
  options: PollUnionPayOrderOptions,
): Promise<UnionPayWinBaseResponse | null> {
  const initialDelayMs = options.initialDelayMs ?? DEFAULT_INITIAL_DELAY_MS;
  const intervalMs = options.intervalMs ?? DEFAULT_INTERVAL_MS;
  const timeoutMs = options.timeoutMs ?? DEFAULT_TIMEOUT_MS;
  const startedAt = Date.now();

  await sleep(initialDelayMs);
  while (!options.isAborted?.()) {
    if (Date.now() - startedAt >= timeoutMs) {
      return null;
    }

    try {
      const result = await queryOrderApi({
        merTradeNo: options.merTradeNo,
        tradeNo: options.tradeNo,
      });
      if (result.success) {
        return result;
      }
      if (result.respCode && result.respCode !== '00' && result.queryStatus === '5') {
        return null;
      }
    } catch {
      // 网络抖动继续轮询
    }

    await sleep(intervalMs);
  }

  return null;
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => {
    window.setTimeout(resolve, ms);
  });
}
