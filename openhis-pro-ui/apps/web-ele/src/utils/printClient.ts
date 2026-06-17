/**
 * OpenHIS.Win 静默打印客户端（与读卡插件同源，默认 http://127.0.0.1:5000）。
 */
import { getYbPluginDataUrl, getYbPluginHealthUrl } from '#/chs/utils/yb-plugin';

export const PRINT_HEALTH_TIMEOUT_MS = 3_000;
export const PRINT_LIST_TIMEOUT_MS = 5_000;
export const PRINT_PDF_TIMEOUT_MS = 90_000;
export const PRINT_HEALTH_CACHE_MS = 5_000;
export const PRINT_BUSY_RETRY_MS = 500;
export const PRINT_BUSY_MAX_RETRIES = 3;

export type PrintClientErrorCode =
  | 'OFFLINE'
  | 'TIMEOUT'
  | 'HTTP'
  | 'PRINT_BUSY'
  | 'PRINTER_NOT_FOUND'
  | 'PRINT_FAILED'
  | 'NO_PRINT_CAPABILITY';

export class PrintClientError extends Error {
  readonly code: PrintClientErrorCode;

  readonly detail?: string;

  readonly retryable?: boolean;

  constructor(message: string, code: PrintClientErrorCode, detail?: string, retryable?: boolean) {
    super(message);
    this.name = 'PrintClientError';
    this.code = code;
    this.detail = detail;
    this.retryable = retryable;
  }
}

export interface PrintHealthResult {
  status: string;
  version?: string;
  port?: number;
  endpoint?: string;
  capabilities?: {
    card?: boolean;
    print?: boolean;
  };
}

export interface PrinterInfo {
  name: string;
  isDefault: boolean;
}

export interface SilentPrintPdfOptions {
  printer?: string;
  copies?: number;
  silent?: boolean;
  jobName?: string;
  clientJobId?: string;
}

let cachedHealth: { at: number; result: PrintHealthResult } | null = null;

function getPluginOrigin(dataUrl = getYbPluginDataUrl()): string {
  return new URL(dataUrl).origin;
}

function getPrintPdfUrl(dataUrl = getYbPluginDataUrl()): string {
  return `${getPluginOrigin(dataUrl)}/api/print/pdf`;
}

function getPrintPrintersUrl(dataUrl = getYbPluginDataUrl()): string {
  return `${getPluginOrigin(dataUrl)}/api/print/printers`;
}

async function fetchWithTimeout(
  url: string,
  init: RequestInit,
  timeoutMs: number,
): Promise<Response> {
  const controller = new AbortController();
  const timer = window.setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(url, { ...init, signal: controller.signal });
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw new PrintClientError(
        `连接 OpenHIS.Win 超时（${Math.round(timeoutMs / 1000)} 秒）`,
        'TIMEOUT',
        url,
        true,
      );
    }
    throw new PrintClientError(`无法连接 OpenHIS.Win（${url}）`, 'OFFLINE', url, true);
  } finally {
    window.clearTimeout(timer);
  }
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
}

async function readPrintError(response: Response): Promise<PrintClientError> {
  let body: { code?: string; message?: string; retryAfterMs?: number } = {};
  try {
    body = (await response.json()) as typeof body;
  } catch {
    // ignore
  }

  const code = body.code;
  const message = body.message ?? `HTTP ${response.status}`;

  if (code === 'PRINT_BUSY') {
    return new PrintClientError(message, 'PRINT_BUSY', undefined, true);
  }
  if (code === 'PRINTER_NOT_FOUND') {
    return new PrintClientError(message, 'PRINTER_NOT_FOUND');
  }
  if (code === 'PRINT_FAILED' || code === 'PRINT_TIMEOUT') {
    return new PrintClientError(message, 'PRINT_FAILED');
  }

  return new PrintClientError(message, 'HTTP', String(response.status));
}

export function formatPrintClientError(error: unknown): string {
  if (error instanceof PrintClientError) {
    switch (error.code) {
      case 'OFFLINE':
        return '未检测到 OpenHIS.Win，请确认托盘程序已启动（端口 5000）';
      case 'TIMEOUT':
        return error.message;
      case 'PRINT_BUSY':
        return '打印机正在处理上一任务，请稍后重试';
      case 'NO_PRINT_CAPABILITY':
        return '本机插件版本过低，请更新 OpenHIS.Win';
      case 'PRINTER_NOT_FOUND':
        return error.message;
      default:
        return error.message;
    }
  }
  if (error instanceof Error) {
    return error.message;
  }
  return '打印失败';
}

export async function checkHealth(options?: {
  force?: boolean;
  pluginUrl?: string;
}): Promise<PrintHealthResult> {
  const pluginUrl = options?.pluginUrl ?? getYbPluginDataUrl();
  const now = Date.now();
  if (!options?.force && cachedHealth && now - cachedHealth.at < PRINT_HEALTH_CACHE_MS) {
    return cachedHealth.result;
  }

  const response = await fetchWithTimeout(
    getYbPluginHealthUrl(pluginUrl),
    { method: 'GET' },
    PRINT_HEALTH_TIMEOUT_MS,
  );

  if (!response.ok) {
    throw new PrintClientError(
      `OpenHIS.Win 健康检查失败（HTTP ${response.status}）`,
      'OFFLINE',
      getYbPluginHealthUrl(pluginUrl),
    );
  }

  const result = (await response.json()) as PrintHealthResult;
  cachedHealth = { at: now, result };
  return result;
}

export async function assertPrintCapability(pluginUrl?: string): Promise<PrintHealthResult> {
  const health = await checkHealth({ pluginUrl });
  if (health.capabilities?.print !== true) {
    throw new PrintClientError(
      'OpenHIS.Win 未声明打印能力（capabilities.print）',
      'NO_PRINT_CAPABILITY',
    );
  }
  return health;
}

let cachedPrinters: { at: number; printers: PrinterInfo[] } | null = null;

export async function listPrinters(options?: {
  force?: boolean;
  pluginUrl?: string;
}): Promise<PrinterInfo[]> {
  const pluginUrl = options?.pluginUrl ?? getYbPluginDataUrl();
  const now = Date.now();
  if (!options?.force && cachedPrinters && now - cachedPrinters.at < 30_000) {
    return cachedPrinters.printers;
  }

  await assertPrintCapability(pluginUrl);

  const response = await fetchWithTimeout(
    getPrintPrintersUrl(pluginUrl),
    { method: 'GET' },
    PRINT_LIST_TIMEOUT_MS,
  );

  if (!response.ok) {
    throw await readPrintError(response);
  }

  const data = (await response.json()) as { printers?: PrinterInfo[] };
  const printers = data.printers ?? [];
  cachedPrinters = { at: now, printers };
  return printers;
}

async function blobToBase64(blob: Blob): Promise<string> {
  const buffer = await blob.arrayBuffer();
  let binary = '';
  const bytes = new Uint8Array(buffer);
  const chunkSize = 0x8000;
  for (let i = 0; i < bytes.length; i += chunkSize) {
    binary += String.fromCharCode(...bytes.subarray(i, i + chunkSize));
  }
  return btoa(binary);
}

export async function silentPrintPdf(
  blob: Blob,
  options?: SilentPrintPdfOptions & { pluginUrl?: string },
): Promise<void> {
  if (!blob || blob.size === 0) {
    throw new PrintClientError('PDF Blob 为空', 'PRINT_FAILED');
  }

  const pluginUrl = options?.pluginUrl ?? getYbPluginDataUrl();
  await assertPrintCapability(pluginUrl);

  const pdfBase64 = await blobToBase64(blob);
  const url = getPrintPdfUrl(pluginUrl);
  const body = JSON.stringify({
    pdfBase64,
    printer: options?.printer,
    copies: options?.copies ?? 1,
    silent: options?.silent ?? true,
    jobName: options?.jobName ?? 'OpenHIS',
    clientJobId: options?.clientJobId,
  });

  let lastErr: PrintClientError | undefined;

  for (let attempt = 0; attempt <= PRINT_BUSY_MAX_RETRIES; attempt++) {
    const response = await fetchWithTimeout(
      url,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body,
      },
      PRINT_PDF_TIMEOUT_MS,
    );

    if (response.status === 409) {
      let retryAfterMs = PRINT_BUSY_RETRY_MS;
      try {
        const j = (await response.json()) as { retryAfterMs?: number };
        if (typeof j.retryAfterMs === 'number' && j.retryAfterMs > 0) {
          retryAfterMs = j.retryAfterMs;
        }
      } catch {
        // ignore
      }
      lastErr = new PrintClientError('打印机忙', 'PRINT_BUSY', undefined, true);
      if (attempt < PRINT_BUSY_MAX_RETRIES) {
        await sleep(retryAfterMs);
        continue;
      }
      throw lastErr;
    }

    if (!response.ok) {
      throw await readPrintError(response);
    }

    return;
  }

  throw lastErr ?? new PrintClientError('打印机忙', 'PRINT_BUSY', undefined, true);
}

export function getPluginOriginForPrint(dataUrl?: string): string {
  return getPluginOrigin(dataUrl ?? getYbPluginDataUrl());
}
