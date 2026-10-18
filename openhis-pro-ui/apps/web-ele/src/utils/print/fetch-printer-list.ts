import { connectHiprintClient, resolveHiprintHostAsync } from './runtime/hiprint-client';
import { isHiprintHealthRecentlyUnavailable } from '#/api/local/zxplatWin';

const DEFAULT_FETCH_TIMEOUT_MS = 5000;
const OFFLINE_FETCH_TIMEOUT_MS = 1500;

export interface HiprintPrinterItem {
  name: string;
  displayName: string;
  isDefault: boolean;
}

interface RawHiprintPrinter {
  name?: string;
  displayName?: string;
  isDefault?: boolean;
}

interface HiwebSocketLike {
  opened?: boolean;
  printerList?: RawHiprintPrinter[];
  getPrinterList?: () => RawHiprintPrinter[];
  refreshPrinterList?: () => void;
  socket?: {
    off?: (event: string, handler: (data: unknown) => void) => void;
    once: (event: string, handler: (data: unknown) => void) => void;
  };
}

function getHiwebSocket(): HiwebSocketLike | undefined {
  return (window as Window & { hiwebSocket?: HiwebSocketLike }).hiwebSocket;
}

/** 将 hiprint 原始打印机对象规范化为下拉选项 */
function normalizePrinterList(raw: unknown): HiprintPrinterItem[] {
  if (!Array.isArray(raw)) {
    return [];
  }
  const items: HiprintPrinterItem[] = [];
  const seen = new Set<string>();
  for (const entry of raw) {
    if (!entry || typeof entry !== 'object') {
      continue;
    }
    const record = entry as RawHiprintPrinter;
    const name = record.name?.trim();
    if (!name || seen.has(name)) {
      continue;
    }
    seen.add(name);
    items.push({
      name,
      displayName: record.displayName?.trim() || name,
      isDefault: record.isDefault === true,
    });
  }
  return items;
}

/** 等待 socket 推送 printerList（连接时可能已推送过，先读缓存再 refresh） */
function waitForPrinterList(
  socket: NonNullable<HiwebSocketLike['socket']>,
  refresh: () => void,
  timeoutMs: number,
): Promise<HiprintPrinterItem[]> {
  return new Promise((resolve) => {
    let settled = false;
    const finish = (list: unknown) => {
      if (settled) {
        return;
      }
      settled = true;
      window.clearTimeout(timer);
      socket.off?.('printerList', onList);
      resolve(normalizePrinterList(list));
    };
    const onList = (list: unknown) => finish(list);
    const timer = window.setTimeout(() => finish([]), timeoutMs);
    socket.once('printerList', onList);
    refresh();
  });
}

/** 拉取本机 electron-hiprint 打印机列表 */
export async function fetchHiprintPrinterList(
  timeoutMs = DEFAULT_FETCH_TIMEOUT_MS,
): Promise<HiprintPrinterItem[]> {
  const host = await resolveHiprintHostAsync();
  const connectTimeoutMs = isHiprintHealthRecentlyUnavailable()
    ? OFFLINE_FETCH_TIMEOUT_MS
    : DEFAULT_FETCH_TIMEOUT_MS;
  const connected = await connectHiprintClient(host, connectTimeoutMs);
  if (!connected) {
    throw new Error(`无法连接 Hiprint 打印客户端（${host}），请确认 Zxplat.Win 已启动`);
  }

  const hiwebSocket = getHiwebSocket();
  if (!hiwebSocket) {
    throw new Error('Hiprint 客户端未就绪，请稍后重试');
  }

  const cached = normalizePrinterList(hiwebSocket.getPrinterList?.() ?? hiwebSocket.printerList);
  if (cached.length > 0) {
    return cached;
  }

  if (hiwebSocket.socket && typeof hiwebSocket.refreshPrinterList === 'function') {
    const refreshed = await waitForPrinterList(
      hiwebSocket.socket,
      () => hiwebSocket.refreshPrinterList?.(),
      timeoutMs,
    );
    if (refreshed.length > 0) {
      return refreshed;
    }
  }

  return normalizePrinterList(hiwebSocket.getPrinterList?.() ?? hiwebSocket.printerList);
}

/** 判断指定打印机是否仍在本机列表中 */
export function isPrinterAvailable(
  printerName: string | undefined,
  printers: HiprintPrinterItem[],
): boolean {
  const name = printerName?.trim();
  if (!name) {
    return false;
  }
  return printers.some((item) => item.name === name);
}

/** 取系统默认打印机名称（hiprint 列表 isDefault） */
export function pickDefaultPrinterName(printers: HiprintPrinterItem[]): string | undefined {
  return printers.find((item) => item.isDefault)?.name ?? printers[0]?.name;
}
