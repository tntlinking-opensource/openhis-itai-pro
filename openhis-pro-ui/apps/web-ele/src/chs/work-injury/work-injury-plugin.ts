/**
 * 工伤 Win 本地桥接（Zxplat.Win :5000 /api/work-injury/invoke）。
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
    if (!response.ok) {
      return false;
    }
    // health 可能返回 enabled=false
    try {
      const json = (await response.json()) as { enabled?: boolean };
      if (json && json.enabled === false) {
        return false;
      }
    } catch {
      // 非 JSON 但 HTTP 200 仍视为可达
    }
    return true;
  } catch {
    return false;
  }
}

/** 调用 Zxplat.Win 工伤 invoke（单步）。 */
export async function invokeWorkInjuryPlugin(
  step: Pick<
    PrepareWorkInjuryInvokeResult,
    'stepToken' | 'infno' | 'fixmedinsCode' | 'operatorNo' | 'input'
  >,
  pluginUrl = DEFAULT_WORK_INJURY_INVOKE_URL,
  timeoutMs = PLUGIN_FETCH_TIMEOUT_MS,
): Promise<WorkInjuryWinInvokeResponse> {
  const url = normalizeInvokeUrl(pluginUrl);
  // 2204 明细放在 input.inputs[]；由 Win BuildInputData 提升为 Si_Busi 顶层 inputs
  // （勿再拆到 HTTP 顶层：Win 只读 input，拆开会导致中心收到空 inputs）
  const rawInput = (step.input ?? {}) as Record<string, unknown>;
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
          input: rawInput,
        }),
      },
      timeoutMs,
    );
  } catch (error) {
    const raw = error instanceof Error ? error.message : String(error ?? '');
    const aborted = error instanceof DOMException && error.name === 'AbortError';
    throw new WorkInjuryPluginError(
      aborted
        ? '工伤 Win 调用超时，请确认 Zxplat.Win 未卡住且专线正常'
        : /failed to fetch|networkerror|load failed|econnrefused|连接/i.test(raw)
          ? '无法连接 Zxplat.Win（127.0.0.1:5000），请启动客户端并启用工伤模块'
          : raw || '工伤 Win 插件网络异常',
      'NETWORK',
      raw || undefined,
    );
  }

  const text = await response.text();
  let json: WorkInjuryWinInvokeResponse | undefined;
  try {
    json = JSON.parse(text) as WorkInjuryWinInvokeResponse;
  } catch {
    json = undefined;
  }

  if (!response.ok) {
    const platformMsg = extractPlatformErrMsg(json?.errMsg) || json?.errMsg?.trim();
    throw new WorkInjuryPluginError(
      platformMsg || `工伤 Win HTTP ${response.status}`,
      response.status === 503 ? 'DLL' : 'HTTP',
      text.slice(0, 300) || undefined,
    );
  }

  if (!json) {
    throw new WorkInjuryPluginError('工伤 Win 返回非 JSON', 'EMPTY', text.slice(0, 200));
  }

  if (json.success === false) {
    const platformMsg = extractPlatformErrMsg(json.errMsg) || json.errMsg || '工伤 Win 调用失败';
    // 2205 空撤 / 2207 单据号已使用 / 2203 已存在结算：平台侧已达目标态，交 ZXPlat 幂等落账
    if (
      isWorkInjury2205EmptyRevoke(step.infno, platformMsg) ||
      isWorkInjury2207BillAlreadyUsed(step.infno, platformMsg) ||
      isWorkInjury2203AlreadySettled(step.infno, platformMsg)
    ) {
      return {
        ...json,
        success: true,
        infcode: '0',
        errMsg: platformMsg,
      };
    }
    throw new WorkInjuryPluginError(platformMsg, 'DLL');
  }
  if (json.infcode !== undefined && json.infcode !== '0' && json.infcode !== 0) {
    const platformMsg =
      extractPlatformErrMsg(json.errMsg) || json.errMsg || `平台 infcode=${String(json.infcode)}`;
    if (
      isWorkInjury2205EmptyRevoke(step.infno, platformMsg) ||
      isWorkInjury2207BillAlreadyUsed(step.infno, platformMsg) ||
      isWorkInjury2203AlreadySettled(step.infno, platformMsg)
    ) {
      return {
        ...json,
        success: true,
        infcode: '0',
        errMsg: platformMsg,
      };
    }
    throw new WorkInjuryPluginError(platformMsg, 'DLL');
  }
  return json;
}

/** 2205 整单撤：无未结处方 / 尚无就诊登记（幂等，可继续 9102→2201）。 */
export function isWorkInjury2205EmptyRevoke(
  infno: string | undefined,
  errMsg: string | undefined,
): boolean {
  if (infno !== '2205' || !errMsg) {
    return false;
  }
  if (errMsg.includes('没有需要删除的未结算的处方')) {
    return true;
  }
  // 新就诊尚未 2201：平台查无登记，撤销无目标
  return (
    errMsg.includes('不能进行撤销') &&
    (errMsg.includes('没有查询到') || errMsg.includes('就诊登记'))
  );
}

/** 2207 单据号已被平台占用（上次 Win 已成功、HIS 未落账）。 */
export function isWorkInjury2207BillAlreadyUsed(
  infno: string | undefined,
  errMsg: string | undefined,
): boolean {
  if (infno !== '2207' || !errMsg) {
    return false;
  }
  return errMsg.includes('单据号') && errMsg.includes('已使用');
}

/** 2203 平台已有结算不可改登记（单边：平台已结、HIS 未落）。 */
export function isWorkInjury2203AlreadySettled(
  infno: string | undefined,
  errMsg: string | undefined,
): boolean {
  if (infno !== '2203' || !errMsg) {
    return false;
  }
  return errMsg.includes('已存在结算信息');
}

/** Si_INIT / Si_Busi 失败时常把整段平台 JSON 塞进 errMsg，抽出可读 err_msg。 */
function extractPlatformErrMsg(errMsg?: string): string {
  const raw = String(errMsg ?? '').trim();
  if (!raw) {
    return '';
  }
  if (raw.startsWith('{')) {
    try {
      const parsed = JSON.parse(raw) as { err_msg?: unknown };
      const nested = String(parsed.err_msg ?? '').trim();
      if (nested) {
        return friendlyWorkInjuryNativeErr(nested);
      }
    } catch {
      // keep raw
    }
  }
  return friendlyWorkInjuryNativeErr(raw);
}

/** .NET SEHException / 平台固定文案 → 可操作提示 */
function friendlyWorkInjuryNativeErr(message: string): string {
  if (/外部组件|External component/i.test(message)) {
    return '工伤读卡组件异常（请确认已放社保卡、勿与省医保读卡同时操作，并重启 Zxplat.Win 后重试）';
  }
  if (/异常错误|未知错误/.test(message)) {
    return '工伤读卡忙冲突（请确认卡已放稳，勿与省医保读卡同时点选，稍后重试查询备案）';
  }
  return message;
}
