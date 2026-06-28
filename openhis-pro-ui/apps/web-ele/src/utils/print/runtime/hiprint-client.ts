import type { HiprintPageSizeOptions } from './paper-size';

import { autoConnect, defaultElementTypeProvider, hiprint } from 'vue-plugin-hiprint';

import $ from 'jquery';

import {
  getHiprintSocketHost,
  isHiprintHealthRecentlyUnavailable,
  prefetchHiprintSocketHost,
  resolveHiprintSocketHostAsync,
} from '#/api/local/openhisWin';

import { resolveHiprintPageSize } from './paper-size';
import { PRINT_ERROR_CODE } from './types';

const DEFAULT_CONNECT_TIMEOUT_MS = 3000;
const WARMUP_CONNECT_TIMEOUT_MS = 1500;
/** 打印前等待 health 解析的上限；超时则用同步兜底地址，避免拖住业务 */
const HIPRINT_HOST_WAIT_MS = 800;
const DEFAULT_PRINT_TIMEOUT_MS = 60_000;

export interface HiprintPrintRequest {
  PrintJson: Record<string, unknown>;
  printData: Record<string, unknown>;
  scenarioCode?: string;
  printer?: string;
  copies?: number;
  silent?: boolean;
  jobName?: string;
  pageSize?: HiprintPageSizeOptions['pageSize'];
  landscape?: boolean;
}

export interface HiprintPrintSuccess {
  ok: true;
  printer?: string;
  copies?: number;
}

export interface HiprintPrintFailure {
  ok: false;
  code: string;
  message: string;
  retryAfterMs?: number;
}

export type HiprintPrintResponse = HiprintPrintFailure | HiprintPrintSuccess;

interface HiprintPrintTemplate {
  print2: (
    data: Record<string, unknown>,
    options?: {
      copies?: number;
      landscape?: boolean;
      pageSize?: HiprintPageSizeOptions['pageSize'];
      printer?: string;
      silent?: boolean;
      title?: string;
    },
  ) => void;
  on: (event: string, handler: (data?: unknown) => void) => void;
  off?: (event: string, handler: (data?: unknown) => void) => void;
}

let runtimeInited = false;
let lastHost = '';
let printLockCssPrefetched = false;

function ensureJquery(): void {
  const w = window as Window & { $?: typeof $; jQuery?: typeof $ };
  w.$ = $;
  w.jQuery = $;
}

/** 预取 print-lock.css，减轻 print2 内同步 XHR 等待 */
function prefetchPrintLockCss(): void {
  if (printLockCssPrefetched) {
    return;
  }
  printLockCssPrefetched = true;
  const link = document.querySelector(
    'link[media=print][href*="print-lock"]',
  ) as HTMLLinkElement | null;
  if (link?.href) {
    fetch(link.href).catch(() => undefined);
  }
}

/** 初始化 hiprint 运行时（electron-hiprint Socket.IO） */
function ensureHiprintPrintRuntime(host: string): void {
  ensureJquery();
  prefetchPrintLockCss();
  if (runtimeInited && lastHost === host) {
    return;
  }
  hiprint.init({
    // hiprint 导出的小写构造函数名，非标准 PascalCase
    providers: [new defaultElementTypeProvider()], // eslint-disable-line new-cap
    lang: 'cn',
    host,
  });
  runtimeInited = true;
  lastHost = host;
}

/** 打印用 Socket 地址（同步兜底，优先 env / 缓存 / 默认 17521） */
export function resolveHiprintHost(): string {
  return getHiprintSocketHost();
}

/** 打印用 Socket 地址（经 5000 /api/health 解析 Hiprint 端口，env 优先；超时快速兜底） */
export async function resolveHiprintHostAsync(): Promise<string> {
  const fallback = getHiprintSocketHost();
  const resolved = await Promise.race([
    resolveHiprintSocketHostAsync(),
    new Promise<string>((resolve) => {
      window.setTimeout(() => resolve(fallback), HIPRINT_HOST_WAIT_MS);
    }),
  ]);
  return resolved;
}

export interface HiprintProbeResult {
  ready: boolean;
  host: string;
  reason?: string;
}

/** 探测 hiprint 是否可连接（经 5000 health 解析地址后直连 Socket） */
export async function probeHiprintClient(): Promise<HiprintProbeResult> {
  const host = await resolveHiprintHostAsync();
  const ready = await connectHiprintClient(host, resolveConnectTimeoutMs());
  return ready
    ? { ready: true, host }
    : {
        ready: false,
        host,
        reason: `无法连接 Hiprint 打印客户端（${host}），请确认 OpenHIS.Win 已启动且 Hiprint 子进程在运行`,
      };
}

/** 探测 electron-hiprint 是否可连接 */
export async function isHiprintClientReady(): Promise<boolean> {
  const probe = await probeHiprintClient();
  return probe.ready;
}

/**
 * 后台预热打印客户端：先用默认地址尝试连接，health 解析在后台进行，不阻塞任何业务请求。
 */
export function schedulePrintClientWarmup(): void {
  if (isHiprintSocketOpened()) {
    return;
  }
  queueMicrotask(() => {
    const fallbackHost = getHiprintSocketHost();
    void connectHiprintClient(fallbackHost, WARMUP_CONNECT_TIMEOUT_MS).catch(() => undefined);
    prefetchHiprintSocketHost();
  });
}

/** 应用启动后可调用，提前建立 Socket 连接（不阻塞） */
export function warmupHiprintClient(): void {
  schedulePrintClientWarmup();
}

function resolveConnectTimeoutMs(): number {
  return isHiprintHealthRecentlyUnavailable()
    ? WARMUP_CONNECT_TIMEOUT_MS
    : DEFAULT_CONNECT_TIMEOUT_MS;
}

function isHiprintSocketOpened(): boolean {
  const w = window as Window & { hiwebSocket?: { opened?: boolean } };
  return w.hiwebSocket?.opened === true;
}

/** 建立 hiprint Socket 连接 */
export function connectHiprintClient(
  host: string,
  timeoutMs = DEFAULT_CONNECT_TIMEOUT_MS,
): Promise<boolean> {
  ensureHiprintPrintRuntime(host);
  if (isHiprintSocketOpened()) {
    return Promise.resolve(true);
  }
  return new Promise((resolve) => {
    let settled = false;
    const timer = window.setTimeout(() => {
      if (settled) {
        return;
      }
      settled = true;
      resolve(isHiprintSocketOpened());
    }, timeoutMs);
    autoConnect((status) => {
      if (!status) {
        return;
      }
      if (settled) {
        return;
      }
      settled = true;
      window.clearTimeout(timer);
      resolve(true);
    });
  });
}

function toHiprintTemplateJson(raw: Record<string, unknown>): Record<string, unknown> {
  if (Array.isArray(raw.panels)) {
    return raw;
  }
  return { panels: [] };
}

function extractErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }
  if (error && typeof error === 'object' && 'msg' in error) {
    const msg = (error as { msg?: unknown }).msg;
    if (typeof msg === 'string' && msg.trim()) {
      return msg;
    }
  }
  return '打印失败';
}

function printOnce(
  template: HiprintPrintTemplate,
  printData: Record<string, unknown>,
  options: {
    copies?: number;
    landscape?: boolean;
    pageSize?: HiprintPageSizeOptions['pageSize'];
    printer?: string;
    silent?: boolean;
    title?: string;
  },
  timeoutMs = DEFAULT_PRINT_TIMEOUT_MS,
): Promise<void> {
  return new Promise((resolve, reject) => {
    let settled = false;
    const onSuccess = () => {
      if (settled) {
        return;
      }
      settled = true;
      window.clearTimeout(timer);
      cleanup();
      resolve();
    };
    const onError = (error?: unknown) => {
      if (settled) {
        return;
      }
      settled = true;
      window.clearTimeout(timer);
      cleanup();
      reject(new Error(extractErrorMessage(error)));
    };
    const cleanup = () => {
      template.off?.('printSuccess', onSuccess);
      template.off?.('printError', onError);
    };
    const timer = window.setTimeout(() => {
      onError(new Error('打印超时'));
    }, timeoutMs);

    template.on('printSuccess', onSuccess);
    template.on('printError', onError);
    try {
      template.print2(printData, {
        printer: options.printer ?? '',
        title: options.title ?? 'OpenHIS',
        pageSize: options.pageSize,
        landscape: options.landscape,
        copies: options.copies ?? 1,
        silent: options.silent ?? true,
      });
    } catch (error) {
      onError(error);
    }
  });
}

/** 经 electron-hiprint（17521）静默直打 */
export async function postHiprintPrint(
  request: HiprintPrintRequest,
): Promise<HiprintPrintResponse> {
  const host = await resolveHiprintHostAsync();
  const connected = await connectHiprintClient(host, resolveConnectTimeoutMs());
  if (!connected) {
    return {
      ok: false,
      code: PRINT_ERROR_CODE.CLIENT_OFFLINE,
      message: `无法连接 Hiprint 打印客户端（${host}），请确认 electron-hiprint 已启动`,
    };
  }

  const template = new hiprint.PrintTemplate({
    template: toHiprintTemplateJson(request.PrintJson),
  }) as unknown as HiprintPrintTemplate;

  const copies = Math.max(1, request.copies ?? 1);
  const printer = request.printer?.trim() || undefined;
  const title = request.jobName?.trim() || 'OpenHIS';
  const pageOpts =
    request.pageSize !== undefined || request.landscape !== undefined
      ? { pageSize: request.pageSize, landscape: request.landscape }
      : resolveHiprintPageSize(request.PrintJson, request.scenarioCode);

  try {
    await printOnce(template, request.printData, {
      printer,
      title,
      pageSize: pageOpts.pageSize,
      landscape: pageOpts.landscape,
      copies,
      silent: request.silent ?? true,
    });
    return {
      ok: true,
      printer,
      copies,
    };
  } catch (error) {
    const message = extractErrorMessage(error);
    const busy = /忙|busy/i.test(message);
    const printerMissing =
      /printer|打印机/i.test(message) && /not found|找不到|未找到|不存在|不可用/i.test(message);
    return {
      ok: false,
      code: busy
        ? PRINT_ERROR_CODE.PRINT_BUSY
        : printerMissing
          ? PRINT_ERROR_CODE.PRINTER_NOT_FOUND
          : PRINT_ERROR_CODE.PRINT_FAILED,
      message,
      retryAfterMs: busy ? 500 : undefined,
    };
  }
}
