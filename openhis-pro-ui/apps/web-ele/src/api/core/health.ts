import { useAppConfig } from '@vben/hooks';

export interface HealthInfo {
  status?: string;
  version?: string;
  timestamp?: string;
}

/**
 * 公开健康检查（无需登录）。失败时静默返回 null，避免登录页弹错误提示。
 */
export async function fetchHealthInfo(): Promise<HealthInfo | null> {
  try {
    const { apiURL } = useAppConfig(import.meta.env, import.meta.env.PROD);
    const base = String(apiURL ?? '/api').replace(/\/$/, '');
    const response = await fetch(`${base}/health`, {
      method: 'GET',
      headers: { Accept: 'application/json' },
    });
    if (!response.ok) {
      return null;
    }
    const body = (await response.json()) as {
      code?: number;
      data?: HealthInfo;
      version?: string;
    };
    if (body?.data && typeof body.data === 'object') {
      return body.data;
    }
    if (typeof body?.version === 'string') {
      return { version: body.version };
    }
    return null;
  } catch {
    return null;
  }
}
