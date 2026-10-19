/** 后端重启后会话租户丢失时的错误码 */
export const TENANT_SCOPE_LOST_ERROR_CODE = 'TENANT_REQUIRED';

let restoringScope: null | Promise<void> = null;

function extractErrorPayload(error: unknown): {
  errorCode?: string;
  message?: string;
} {
  const err = error as {
    errorCode?: string;
    message?: string;
    response?: { data?: { errorCode?: string; message?: string } };
  };
  const responseData = err?.response?.data ?? {};
  return {
    errorCode: responseData.errorCode ?? err?.errorCode,
    message: responseData.message ?? err?.message,
  };
}

/** 是否为服务端租户/医院/科室会话丢失（重启后内存会话清空，或库房页缺科室） */
export function isTenantScopeLostError(error: unknown): boolean {
  const { errorCode, message } = extractErrorPayload(error);
  if (errorCode === TENANT_SCOPE_LOST_ERROR_CODE) {
    return true;
  }
  const text = String(message ?? '');
  return (
    text.includes('请先选择租户') ||
    text.includes('请先选择医院') ||
    text.includes('机构上下文缺失')
  );
}

/** 串行恢复登录上下文，避免并发请求重复 selectTenant */
export async function restoreLoginScopeOnce(): Promise<void> {
  if (!restoringScope) {
    restoringScope = import('#/utils/auth/require-login-scope-ready')
      .then(({ resetLoginScopeSync, requireLoginScopeReady }) => {
        resetLoginScopeSync();
        return requireLoginScopeReady();
      })
      .finally(() => {
        restoringScope = null;
      });
  }
  await restoringScope;
}
