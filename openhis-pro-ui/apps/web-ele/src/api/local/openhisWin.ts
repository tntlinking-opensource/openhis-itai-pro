/**
 * OpenHIS.Win 能力探测（/api/health）。
 */
const DEFAULT_PLUGIN_ORIGIN = 'http://127.0.0.1:5000';
const HEALTH_TIMEOUT_MS = 3000;
/** 打印场景 health 探测超时（Win 未启动时尽快失败，避免拖慢打印链路） */
const HIPRINT_HEALTH_TIMEOUT_MS = 1500;
/** health 失败后一段时间内不再请求 5000（避免反复超时占用连接） */
const HIPRINT_HEALTH_NEGATIVE_CACHE_MS = 30_000;

export interface OpenHisWinCapabilities {
  chs?: boolean;
  unionpay?: boolean;
  nativeReady?: boolean;
  hiprint?: boolean;
}

export interface OpenHisWinHiprintHealth {
  enabled?: boolean;
  running?: boolean;
  port?: number;
}

export interface OpenHisWinHealthResponse {
  status?: string;
  version?: string;
  port?: number;
  endpoint?: string;
  capabilities?: OpenHisWinCapabilities;
  hiprint?: OpenHisWinHiprintHealth;
}

export const DEFAULT_HIPRINT_PORT = 17_521;

const DEFAULT_HIPRINT_ORIGIN = `http://127.0.0.1:${DEFAULT_HIPRINT_PORT}`;

function normalizeHiprintOrigin(raw: string): string {
  const trimmed = raw.trim();
  try {
    return new URL(trimmed).origin;
  } catch {
    if (/^https?:\/\//i.test(trimmed)) {
      return trimmed.replace(/\/$/, '');
    }
    return `http://${trimmed.replace(/\/$/, '')}`;
  }
}

/** 环境变量配置的 Hiprint Socket 地址（有则不走 5000 health） */
function getHiprintSocketHostFromEnv(): string | null {
  const fromEnv = import.meta.env.VITE_GLOB_HIPRINT_URL;
  if (typeof fromEnv === 'string' && fromEnv.trim()) {
    return normalizeHiprintOrigin(fromEnv);
  }
  return null;
}

function buildHiprintOriginFromPort(port: number, winOrigin = getOpenHisWinOrigin()): string {
  try {
    const origin = new URL(winOrigin);
    return `${origin.protocol}//${origin.hostname}:${port}`;
  } catch {
    return `http://127.0.0.1:${port}`;
  }
}

let cachedHiprintSocketHost = '';
let hiprintHostResolvePromise: null | Promise<string> = null;
let hiprintHealthNegativeUntil = 0;
let healthFetchPromise: null | Promise<null | OpenHisWinHealthResponse> = null;
let healthFetchOrigin = '';

/** electron-hiprint Socket.IO 地址（同步兜底：env 或默认 17521） */
export function getHiprintSocketHost(): string {
  const fromEnv = getHiprintSocketHostFromEnv();
  if (fromEnv) {
    return fromEnv;
  }
  if (cachedHiprintSocketHost) {
    return cachedHiprintSocketHost;
  }
  return DEFAULT_HIPRINT_ORIGIN;
}

function markHiprintHealthUnavailable(): void {
  hiprintHealthNegativeUntil = Date.now() + HIPRINT_HEALTH_NEGATIVE_CACHE_MS;
}

function applyHiprintHostFromHealth(health: OpenHisWinHealthResponse): string | null {
  const port = health.hiprint?.port;
  if (health.status === 'ok' && typeof port === 'number' && port > 0 && port < 65_536) {
    cachedHiprintSocketHost = buildHiprintOriginFromPort(port);
    hiprintHealthNegativeUntil = 0;
    return cachedHiprintSocketHost;
  }
  return null;
}

/**
 * 后台刷新 Hiprint 地址（不 await、不阻塞业务请求；失败写入负缓存）。
 */
export function prefetchHiprintSocketHost(): void {
  const fromEnv = getHiprintSocketHostFromEnv();
  if (fromEnv || cachedHiprintSocketHost) {
    return;
  }
  if (Date.now() < hiprintHealthNegativeUntil) {
    return;
  }
  if (hiprintHostResolvePromise) {
    return;
  }
  hiprintHostResolvePromise = resolveHiprintSocketHostInternal().finally(() => {
    hiprintHostResolvePromise = null;
  });
}

async function resolveHiprintSocketHostInternal(): Promise<string> {
  const health = await fetchOpenHisWinHealth(undefined, HIPRINT_HEALTH_TIMEOUT_MS);
  const resolved = health ? applyHiprintHostFromHealth(health) : null;
  if (resolved) {
    return resolved;
  }
  markHiprintHealthUnavailable();
  return DEFAULT_HIPRINT_ORIGIN;
}

/**
 * 经 OpenHIS.Win GET /api/health 解析 Hiprint Socket 地址（异步、带缓存）。
 * 有 VITE_GLOB_HIPRINT_URL 时直接返回 env，不请求 5000。
 * Win 不可达时快速回退默认 17521，并在负缓存窗口内不再请求 5000。
 */
export async function resolveHiprintSocketHostAsync(): Promise<string> {
  const fromEnv = getHiprintSocketHostFromEnv();
  if (fromEnv) {
    return fromEnv;
  }
  if (cachedHiprintSocketHost) {
    return cachedHiprintSocketHost;
  }
  if (Date.now() < hiprintHealthNegativeUntil) {
    return DEFAULT_HIPRINT_ORIGIN;
  }
  if (!hiprintHostResolvePromise) {
    hiprintHostResolvePromise = resolveHiprintSocketHostInternal().finally(() => {
      hiprintHostResolvePromise = null;
    });
  }
  return hiprintHostResolvePromise;
}

/** Win health 近期不可达（负缓存窗口内不再请求 5000） */
export function isHiprintHealthRecentlyUnavailable(): boolean {
  return Date.now() < hiprintHealthNegativeUntil;
}

/** 本地插件 origin（可通过 VITE_GLOB_YB_PLUGIN_URL 推导） */
export function getOpenHisWinOrigin(): string {
  const fromEnv = import.meta.env.VITE_GLOB_YB_PLUGIN_URL;
  if (typeof fromEnv === 'string' && fromEnv.trim()) {
    try {
      return new URL(fromEnv.trim()).origin;
    } catch {
      return DEFAULT_PLUGIN_ORIGIN;
    }
  }
  return DEFAULT_PLUGIN_ORIGIN;
}

export function getOpenHisWinHealthUrl(origin = getOpenHisWinOrigin()): string {
  return `${origin}/api/health`;
}

async function fetchOpenHisWinHealthOnce(
  origin: string,
  timeoutMs: number,
): Promise<null | OpenHisWinHealthResponse> {
  const controller = new AbortController();
  const timer = window.setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(getOpenHisWinHealthUrl(origin), {
      method: 'GET',
      signal: controller.signal,
    });
    if (!response.ok) {
      return null;
    }
    return (await response.json()) as OpenHisWinHealthResponse;
  } catch {
    return null;
  } finally {
    window.clearTimeout(timer);
  }
}

/** 探测 OpenHIS.Win 是否在线及银联能力（同 origin 并发复用同一 in-flight 请求） */
export async function fetchOpenHisWinHealth(
  origin = getOpenHisWinOrigin(),
  timeoutMs = HEALTH_TIMEOUT_MS,
): Promise<null | OpenHisWinHealthResponse> {
  if (Date.now() < hiprintHealthNegativeUntil) {
    return null;
  }
  if (healthFetchPromise && healthFetchOrigin === origin) {
    return healthFetchPromise;
  }
  healthFetchOrigin = origin;
  healthFetchPromise = fetchOpenHisWinHealthOnce(origin, timeoutMs)
    .then((result) => {
      if (!result) {
        markHiprintHealthUnavailable();
      }
      return result;
    })
    .finally(() => {
      healthFetchPromise = null;
      healthFetchOrigin = '';
    });
  return healthFetchPromise;
}

/** 是否展示银联扫码入口（Win 在线 + unionpay 能力 + native 就绪） */
export async function isUnionPayWinReady(origin = getOpenHisWinOrigin()): Promise<boolean> {
  const health = await fetchOpenHisWinHealth(origin);
  if (!health?.capabilities?.unionpay) {
    return false;
  }
  return health.capabilities.nativeReady === true;
}

/** 是否已安装 OpenHIS.Win（不要求银联 native） */
export async function isOpenHisWinOnline(origin = getOpenHisWinOrigin()): Promise<boolean> {
  const health = await fetchOpenHisWinHealth(origin);
  return health?.status === 'ok';
}
