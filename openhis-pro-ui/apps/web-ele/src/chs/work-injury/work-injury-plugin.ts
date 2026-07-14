/**
 * 工伤 Win 本地桥接（OpenHIS.Win :5000 /api/work-injury/invoke）。
 */
import type {
  PrepareWorkInjuryInvokeResult,
  WorkInjuryWinInvokeResponse,
} from './work-injury.types';

const DEFAULT_WORK_INJURY_INVOKE_URL = 'http://127.0.0.1:5000/api/work-injury/invoke';
const PLUGIN_FETCH_TIMEOUT_MS = 120_000;

export class WorkInjuryPluginError extends Error {
  constructor(
    message: string,
    readonly code: 'NETWORK' | 'HTTP' | 'DLL' | 'EMPTY' = 'DLL',
    readonly detail?: string,
  ) {
    super(message);
    this.name = 'WorkInjuryPluginError';
  }
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
  } finally {
    window.clearTimeout(timer);
  }
}

function normalizeInvokeUrl(url?: string): string {
  const trimmed = (url ?? DEFAULT_WORK_INJURY_INVOKE_URL).trim();
  return trimmed.endsWith('/') ? trimmed.slice(0, -1) : trimmed;
}

/** 探测工伤 Win 模块是否在线。 */
export async function isWorkInjuryPluginOnline(
  baseUrl = 'http://127.0.0.1:5000/api/work-injury/health',
): Promise<boolean> {
  try {
    const response = await fetchWithTimeout(baseUrl, { method: 'GET' }, 3_000);
    return response.ok;
  } catch {
    return false;
  }
}

/** 调用 OpenHIS.Win 工伤 invoke（单步）。 */
export async function invokeWorkInjuryPlugin(
  step: Pick<
    PrepareWorkInjuryInvokeResult,
    'stepToken' | 'infno' | 'fixmedinsCode' | 'operatorNo' | 'input'
  >,
  pluginUrl = DEFAULT_WORK_INJURY_INVOKE_URL,
  timeoutMs = PLUGIN_FETCH_TIMEOUT_MS,
): Promise<WorkInjuryWinInvokeResponse> {
  const url = normalizeInvokeUrl(pluginUrl);
  let response: Response;
  try {
    response = await fetchWithTimeout(
      url,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          stepToken: step.stepToken,
          infno: step.infno,
          fixmedinsCode: step.fixmedinsCode,
          operatorNo: step.operatorNo,
          input: step.input ?? {},
        }),
      },
      timeoutMs,
    );
  } catch (error) {
    throw new WorkInjuryPluginError(
      error instanceof Error ? error.message : '工伤 Win 插件网络异常',
      'NETWORK',
    );
  }

  const text = await response.text();
  if (!response.ok) {
    throw new WorkInjuryPluginError(
      `工伤 Win HTTP ${response.status}`,
      'HTTP',
      text.slice(0, 200) || undefined,
    );
  }

  let json: WorkInjuryWinInvokeResponse;
  try {
    json = JSON.parse(text) as WorkInjuryWinInvokeResponse;
  } catch {
    throw new WorkInjuryPluginError('工伤 Win 返回非 JSON', 'EMPTY', text.slice(0, 200));
  }

  if (json.success === false) {
    throw new WorkInjuryPluginError(json.errMsg || '工伤 Win 调用失败', 'DLL');
  }
  if (json.infcode !== undefined && json.infcode !== '0' && json.infcode !== 0) {
    throw new WorkInjuryPluginError(json.errMsg || `平台 infcode=${String(json.infcode)}`, 'DLL');
  }
  return json;
}
