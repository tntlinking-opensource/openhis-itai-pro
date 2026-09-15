/**
 * 工伤 Win 9002→9001 显式签到（对账/invoke 前调用；对齐院点 NEUSI 惯例）。
 */
const DEFAULT_SIGN_IN_URL = 'http://127.0.0.1:5000/api/work-injury/sign-in';
const DEFAULT_SIGN_OUT_URL = 'http://127.0.0.1:5000/api/work-injury/sign-out';

async function tryWorkInjurySignOut(options?: {
  fixmedinsCode?: string;
  operatorNo?: string;
  operatorName?: string;
  signOutUrl?: string;
}): Promise<void> {
  const url = (options?.signOutUrl ?? DEFAULT_SIGN_OUT_URL).trim();
  try {
    await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        fixmedinsCode: options?.fixmedinsCode,
        operatorNo: options?.operatorNo ?? '1',
        operatorName: options?.operatorName,
      }),
    });
  } catch {
    // 签退失败不阻断后续 9001
  }
}

export async function ensureWorkInjurySignIn(options?: {
  fixmedinsCode?: string;
  operatorNo?: string;
  operatorName?: string;
  signInUrl?: string;
  signOutUrl?: string;
}): Promise<string | undefined> {
  await tryWorkInjurySignOut(options);

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
    json = JSON.parse(text) as { success?: boolean; signNo?: string; errMsg?: string };
  } catch {
    throw new Error(`工伤签到响应非 JSON: ${text.slice(0, 120)}`);
  }

  if (json.success === false) {
    throw new Error(json.errMsg || '工伤签到失败');
  }

  return json.signNo;
}
