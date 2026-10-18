import { getCrbSsoContextApi } from '#/api/basic/crb-sso';

const SESSION_FLAG_KEY = 'zxplat.crbSso.doctorStationDone';
const LOCAL_TIMEOUT_MS = 3000;

interface FrontSecretKeyResponse {
  result?: boolean;
  secretKey?: string;
  desc?: string;
}

interface FrontLoginResponse {
  result?: boolean;
  desc?: string;
}

/**
 * 门诊或住院医生站进入后：静默调本机传染病前置 SSO（失败不抛、不挡业务）。
 * 院点开关开启且本会话尚未尝试过时执行：本机 GET getSecretKey + POST login。
 */
export async function tryCrbSsoOnDoctorStationEnter(): Promise<void> {
  if (typeof window === 'undefined') {
    return;
  }
  if (sessionStorage.getItem(SESSION_FLAG_KEY) === '1') {
    return;
  }
  // 同会话只尝试一次（含失败），避免反复打本机端口
  sessionStorage.setItem(SESSION_FLAG_KEY, '1');

  let context;
  try {
    context = await getCrbSsoContextApi();
  } catch {
    return;
  }
  if (!context || context.enabled !== true) {
    return;
  }
  const license = String(context.license ?? '').trim();
  const userId = String(context.id ?? '').trim();
  const orgCode = String(context.orgCode ?? '').trim();
  const deptCode = String(context.deptCode ?? '').trim();
  const baseUrl = String(context.clientBaseUrl ?? 'http://127.0.0.1:12306').replace(/\/$/, '');
  if (!license || !userId || !orgCode || !deptCode) {
    return;
  }

  try {
    const secretKey = await fetchSecretKey(baseUrl, license);
    if (!secretKey) {
      return;
    }
    await postFrontLogin(baseUrl, {
      secretKey,
      id: userId,
      orgCode,
      deptCode,
    });
  } catch {
    // 本机未装前置 / CORS / 超时：静默忽略
  }
}

/**
 * GET 本机 getSecretKey。
 *
 * @param baseUrl 前置客户端根地址
 * @param license 授权码
 * @returns secretKey；失败返回空串
 */
async function fetchSecretKey(baseUrl: string, license: string): Promise<string> {
  const url = `${baseUrl}/getSecretKey?license=${encodeURIComponent(license)}`;
  const response = await fetchWithTimeout(url, { method: 'GET' });
  if (!response.ok) {
    return '';
  }
  const body = (await response.json()) as FrontSecretKeyResponse;
  if (body.result !== true) {
    return '';
  }
  return String(body.secretKey ?? '').trim();
}

/**
 * POST 本机 /login。
 *
 * @param baseUrl 前置客户端根地址
 * @param payload SSO 正文
 */
async function postFrontLogin(
  baseUrl: string,
  payload: { secretKey: string; id: string; orgCode: string; deptCode: string },
): Promise<void> {
  const response = await fetchWithTimeout(`${baseUrl}/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json;charset=UTF-8' },
    body: JSON.stringify(payload),
  });
  if (!response.ok) {
    return;
  }
  const body = (await response.json()) as FrontLoginResponse;
  if (body.result !== true) {
    return;
  }
}

/**
 * 带超时的 fetch。
 *
 * @param url 请求地址
 * @param init fetch 选项
 */
async function fetchWithTimeout(url: string, init: RequestInit): Promise<Response> {
  const controller = new AbortController();
  const timer = window.setTimeout(() => controller.abort(), LOCAL_TIMEOUT_MS);
  try {
    return await fetch(url, { ...init, signal: controller.signal });
  } finally {
    window.clearTimeout(timer);
  }
}
