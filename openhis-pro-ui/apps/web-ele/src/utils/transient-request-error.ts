/** 后端重启/未就绪时的瞬时网络错误，不应清 token */
export function isTransientRequestError(error: unknown): boolean {
  const err = error as {
    code?: string;
    message?: string;
    response?: { status?: number };
  };
  const axiosCode = err?.code;
  if (axiosCode === 'ERR_NETWORK' || axiosCode === 'ECONNABORTED' || axiosCode === 'ETIMEDOUT') {
    return true;
  }

  const status = err?.response?.status;
  if (status === 502 || status === 503 || status === 504) {
    return true;
  }

  if (!err?.response) {
    const message = String(err?.message ?? '');
    if (
      message.includes('Network Error') ||
      message.includes('timeout') ||
      message.includes('Failed to fetch')
    ) {
      return true;
    }
  }

  return false;
}

/** 明确的鉴权失败，应清 token 并回登录页 */
export function isAuthFailureError(error: unknown): boolean {
  const err = error as {
    code?: number | string;
    message?: string;
    response?: { data?: { code?: number; message?: string }; status?: number };
  };
  const responseData = err?.response?.data ?? {};
  const message = String(responseData.message ?? err?.message ?? '');
  if (message.includes('没有权限')) {
    return false;
  }

  const status = err?.response?.status;
  if (status === 401 || status === 403) {
    return true;
  }

  if (responseData.code === 401) {
    return true;
  }
  if (
    message.includes('Refresh Token') ||
    message.includes('Token无效') ||
    message.includes('token无效') ||
    message.includes('未登录')
  ) {
    return true;
  }

  return false;
}
