/**
 * 医保读卡本地插件（OpenHIS.Win，默认 http://127.0.0.1:5000）。
 * 9001 签到：浏览器优先取 MAC/IP；失败时由服务端 sign_mac / yb_plugin_url 回退。
 */
const DEFAULT_PLUGIN_DATA_URL = 'http://127.0.0.1:5000/api/data/';
export const DEFAULT_CARD_READER_HOST = 'ddjk.jlhs.gov.cn';
export const DEFAULT_CARD_READER_PORT = 20215;
const PLUGIN_FETCH_TIMEOUT_MS = 8_000;
const PLUGIN_SIGN_MAC_TIMEOUT_MS = 65_000;
const PLUGIN_HEALTH_TIMEOUT_MS = 3_000;

export const YB_PLUGIN_FUNCTION_ID = {
  SOCIAL_CARD: 1,
  ID_CARD: 2,
  EC_QUERY: 3,
  DSD_CARD: 4,
  SIGN_MAC: 5,
  LOCAL_IP: 6,
} as const;

export interface YbPluginInitInput {
  FunctionId: number;
  IP?: string;
  PORT?: number;
  TIMEOUT?: number;
  LOG_PATH?: string;
  SFZ_DRIVER_TYPE?: number;
}

export interface SignEndpointPayload {
  mac: string;
  ip: string;
}

export type YbPluginErrorCode = 'NETWORK' | 'HTTP' | 'EMPTY' | 'DLL' | 'PARSE';

export class YbPluginError extends Error {
  readonly code: YbPluginErrorCode;

  readonly detail?: string;

  constructor(message: string, code: YbPluginErrorCode, detail?: string) {
    super(message);
    this.name = 'YbPluginError';
    this.code = code;
    this.detail = detail;
  }
}

export interface ResolveSignEndpointResult {
  fromPlugin: boolean;
  /** 健康检查是否通过（与 MAC 获取失败区分） */
  pluginOnline: boolean;
  mac?: string;
  ip?: string;
  /** 插件不可用时的简要原因（用于页面提示） */
  fallbackReason?: string;
}

/** 读卡插件数据接口地址（可通过 VITE_GLOB_YB_PLUGIN_URL 覆盖） */
export function getYbPluginDataUrl(): string {
  const fromEnv = import.meta.env.VITE_GLOB_YB_PLUGIN_URL;
  if (typeof fromEnv === 'string' && fromEnv.trim()) {
    return normalizeDataUrl(fromEnv.trim());
  }
  return DEFAULT_PLUGIN_DATA_URL;
}

/** 健康检查地址（OpenHIS.Win GET /api/health） */
export function getYbPluginHealthUrl(dataUrl = getYbPluginDataUrl()): string {
  try {
    const origin = new URL(dataUrl).origin;
    return `${origin}/api/health`;
  } catch {
    return 'http://127.0.0.1:5000/api/health';
  }
}

function normalizeDataUrl(url: string): string {
  if (url.endsWith('/api/data') || url.endsWith('/api/data/')) {
    return url.endsWith('/') ? url : `${url}/`;
  }
  if (!url.endsWith('/')) {
    return `${url}/`;
  }
  return url;
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
      throw new YbPluginError(
        `连接读卡插件超时（${Math.round(timeoutMs / 1000)} 秒）`,
        'NETWORK',
        url,
      );
    }
    throw error;
  } finally {
    window.clearTimeout(timer);
  }
}

/** 探测 OpenHIS.Win 是否在线 */
export async function isYbPluginOnline(pluginUrl = getYbPluginDataUrl()): Promise<boolean> {
  try {
    const response = await fetchWithTimeout(
      getYbPluginHealthUrl(pluginUrl),
      { method: 'GET' },
      PLUGIN_HEALTH_TIMEOUT_MS,
    );
    return response.ok;
  } catch {
    return false;
  }
}

/** 将异常转为面向用户的简短说明 */
export function formatYbPluginError(error: unknown, pluginUrl = getYbPluginDataUrl()): string {
  if (error instanceof YbPluginError) {
    return error.message;
  }
  if (error instanceof TypeError) {
    return `无法连接 ${pluginUrl}，请确认 OpenHIS.Win 已启动并监听 5000 端口`;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return '读卡插件调用失败';
}

function parsePluginPlainText(text: string, functionId: number): string {
  const trimmed = text.trim();
  if (!trimmed || trimmed === ' ') {
    throw new YbPluginError(
      functionId === YB_PLUGIN_FUNCTION_ID.SIGN_MAC ? '未获取到签到 MAC' : '未获取到本机 IP',
      'EMPTY',
    );
  }

  if (!trimmed.startsWith('{')) {
    return trimmed;
  }

  let json: Record<string, unknown>;
  try {
    json = JSON.parse(trimmed) as Record<string, unknown>;
  } catch {
    throw new YbPluginError('读卡插件返回了无法解析的 JSON', 'PARSE');
  }

  const errText =
    (typeof json.error === 'string' && json.error) ||
    (typeof json.ErrorMessage === 'string' && json.ErrorMessage) ||
    (typeof json.message === 'string' && json.message && json.code !== 0 && json.code !== '0'
      ? json.message
      : '');

  if (errText) {
    throw new YbPluginError(`读卡插件：${errText}`, 'DLL');
  }

  const code = json.Code ?? json.code;
  if (code !== undefined && code !== 0 && code !== '0') {
    const msg =
      (typeof json.ErrorMessage === 'string' && json.ErrorMessage) ||
      (typeof json.message === 'string' && json.message) ||
      '调用失败';
    throw new YbPluginError(`读卡插件：${msg}`, 'DLL');
  }

  if (typeof json.data === 'string' && json.data.trim()) {
    return json.data.trim();
  }

  throw new YbPluginError('读卡插件未返回有效的 MAC/IP', 'EMPTY');
}

/** 调用本地医保读卡插件 */
export async function invokeYbPlugin(
  data: YbPluginInitInput,
  pluginUrl = getYbPluginDataUrl(),
  timeoutMs = PLUGIN_FETCH_TIMEOUT_MS,
): Promise<string> {
  const url = normalizeDataUrl(pluginUrl);
  let response: Response;
  try {
    response = await fetchWithTimeout(
      url,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data),
      },
      timeoutMs,
    );
  } catch (error) {
    if (error instanceof YbPluginError) {
      throw error;
    }
    throw new YbPluginError(formatYbPluginError(error, url), 'NETWORK', url);
  }

  const text = await response.text();
  if (!response.ok) {
    throw new YbPluginError(
      `读卡插件 HTTP ${response.status}`,
      'HTTP',
      text.slice(0, 200) || undefined,
    );
  }

  return parsePluginPlainText(text, data.FunctionId);
}

/** 获取 9001 签到 mac/ip（FunctionId 5/6） */
export async function fetchSignEndpointFromPlugin(
  host = DEFAULT_CARD_READER_HOST,
  port = DEFAULT_CARD_READER_PORT,
  pluginUrl = getYbPluginDataUrl(),
): Promise<SignEndpointPayload> {
  const mac = await invokeYbPlugin(
    {
      FunctionId: YB_PLUGIN_FUNCTION_ID.SIGN_MAC,
      IP: host,
      PORT: port,
      TIMEOUT: 60,
      LOG_PATH: 'C:/neu_log/',
      SFZ_DRIVER_TYPE: 0,
    },
    pluginUrl,
    PLUGIN_SIGN_MAC_TIMEOUT_MS,
  );
  const ip = await invokeYbPlugin({ FunctionId: YB_PLUGIN_FUNCTION_ID.LOCAL_IP }, pluginUrl);
  return { mac, ip };
}

/**
 * 签到前解析终端 MAC/IP：优先本机插件；失败则交给服务端（sign_mac / 服务端调插件）。
 */
export async function resolveSignEndpointForSignIn(options?: {
  pluginUrl?: string;
  cardReaderHost?: string;
  cardReaderPort?: number;
}): Promise<ResolveSignEndpointResult> {
  const pluginUrl = options?.pluginUrl ?? getYbPluginDataUrl();

  const online = await isYbPluginOnline(pluginUrl);
  if (!online) {
    return {
      fromPlugin: false,
      pluginOnline: false,
      fallbackReason: `OpenHIS.Win 未响应（${getYbPluginHealthUrl(pluginUrl)}）`,
    };
  }

  try {
    const endpoint = await fetchSignEndpointFromPlugin(
      options?.cardReaderHost ?? DEFAULT_CARD_READER_HOST,
      options?.cardReaderPort ?? DEFAULT_CARD_READER_PORT,
      pluginUrl,
    );
    return {
      fromPlugin: true,
      pluginOnline: true,
      mac: endpoint.mac,
      ip: endpoint.ip,
    };
  } catch (error) {
    return {
      fromPlugin: false,
      pluginOnline: true,
      fallbackReason: formatYbPluginError(error, pluginUrl),
    };
  }
}
