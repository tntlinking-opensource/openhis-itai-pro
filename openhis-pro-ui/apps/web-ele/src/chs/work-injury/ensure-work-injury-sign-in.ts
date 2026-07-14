/**

 * 工伤 Win 9001 显式签到（日初 / invoke 前可选调用）。

 */

const DEFAULT_SIGN_IN_URL = 'http://127.0.0.1:5000/api/work-injury/sign-in';

export async function ensureWorkInjurySignIn(options?: {
  fixmedinsCode?: string;

  operatorNo?: string;

  operatorName?: string;

  signInUrl?: string;
}): Promise<string | undefined> {
  const url = (options?.signInUrl ?? DEFAULT_SIGN_IN_URL).trim();

  const response = await fetch(url, {
    method: 'POST',

    headers: { 'Content-Type': 'application/json' },

    body: JSON.stringify({
      fixmedinsCode: options?.fixmedinsCode,

      operatorNo: options?.operatorNo ?? '1',

      operatorName: options?.operatorName,
    }),
  });

  const text = await response.text();

  if (!response.ok) {
    throw new Error(`工伤签到 HTTP ${response.status}: ${text.slice(0, 120)}`);
  }

  let json: { success?: boolean; signNo?: string; errMsg?: string };

  try {
    json = JSON.parse(text) as typeof json;
  } catch {
    throw new Error('工伤签到返回非 JSON');
  }

  if (json.success === false) {
    throw new Error(json.errMsg || '工伤 9001 签到失败');
  }

  return json.signNo;
}
