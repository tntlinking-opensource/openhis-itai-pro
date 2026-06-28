/** 从请求异常中解析后端返回的可展示错误文案 */
export function resolveRequestErrorMessage(
  error: unknown,
  fallback = '请求失败，请稍后重试',
): string {
  const err = error as {
    data?: { error?: string; message?: string; msg?: string };
    message?: string;
    msg?: string;
    response?: { data?: { error?: string; message?: string; msg?: string } };
  };

  const responseData = err?.response?.data ?? err?.data ?? {};

  return (
    responseData.message ||
    responseData.msg ||
    responseData.error ||
    err?.message ||
    err?.msg ||
    fallback
  );
}
