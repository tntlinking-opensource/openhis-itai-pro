import { httpProbeUnionPayBpcModule } from '#/api/payment/union-pay-bpc';

/** 会话内缓存：false=包未起；true=包已装配；undefined=尚未探测或探测结果不确定 */
let cachedModuleAvailable: boolean | undefined;
let probing: null | Promise<boolean | undefined> = null;

/** 银联 BPC 包未部署/未启用（与框架 AppService 路由行为一致）。 */
export function isUnionPayBpcModuleUnavailableError(error: unknown): boolean {
  if (!error || typeof error !== 'object') {
    return false;
  }
  const err = error as { errorCode?: string; message?: string };
  if (err.errorCode === 'MODULE_NOT_AVAILABLE') {
    return true;
  }
  if (err.errorCode === 'NOT_FOUND') {
    const message = String(err.message ?? '');
    return message.includes('unionPayBpc') || message.includes('/unionPayBpc/');
  }
  return false;
}

/**
 * 探测 unionPayBpc 包是否已起（退费链路最优先门禁）。
 *
 * @returns false=包未起，直接普通退；true=包已起；undefined=网络等不确定，由后续渠道/BPC 再判
 */
export async function probeUnionPayBpcModuleAvailable(): Promise<boolean | undefined> {
  if (cachedModuleAvailable !== undefined) {
    return cachedModuleAvailable;
  }
  if (!probing) {
    probing = runProbe().finally(() => {
      probing = null;
    });
  }
  return probing;
}

async function runProbe(): Promise<boolean | undefined> {
  try {
    await httpProbeUnionPayBpcModule();
    cachedModuleAvailable = true;
    return true;
  } catch (error) {
    if (isUnionPayBpcModuleUnavailableError(error)) {
      cachedModuleAvailable = false;
      return false;
    }
    return undefined;
  }
}

/** 测试或切换环境后清缓存 */
export function resetUnionPayBpcModuleProbeCache(): void {
  cachedModuleAvailable = undefined;
  probing = null;
}
