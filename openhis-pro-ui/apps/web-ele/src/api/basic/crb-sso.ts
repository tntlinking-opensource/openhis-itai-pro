import { publicRequestClient } from '../request';

/**
 * 当前院点：门诊 / 住院医生站本机 SSO 上下文。
 */
export interface CrbSsoContext {
  enabled?: boolean;
  license?: string;
  id?: string;
  orgCode?: string;
  deptCode?: string;
  clientBaseUrl?: string;
}

const SITE =
  String(import.meta.env.VITE_ZXPLAT_PROJECT ?? 'H22010402403').trim() || 'H22010402403';
const BASE = `/${SITE.toLowerCase()}CrbSso`;

/**
 * 拉取当前登录用户 + 当前登录科室的本机 SSO 参数。
 *
 * @returns 后端 DTO；未启用时 enabled=false
 */
export async function getCrbSsoContextApi(): Promise<CrbSsoContext> {
  return publicRequestClient.post<CrbSsoContext>(`${BASE}/getSsoContext`, {});
}
