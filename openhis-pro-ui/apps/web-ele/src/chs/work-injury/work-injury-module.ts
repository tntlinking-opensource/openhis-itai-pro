import { httpWorkInjuryModuleCapability } from './work-injury.http';

/** 会话内缓存：false=包未起；true=包已装配；undefined=尚未探测或探测不确定 */
let cachedModuleAvailable: boolean | undefined;
let probing: null | Promise<boolean | undefined> = null;

/** 工伤扩展模块未部署/未启用（与框架 AppService 路由一致）。 */
export function isWorkInjuryModuleUnavailableError(error: unknown): boolean {
  if (!error || typeof error !== 'object') {
    return false;
  }
  const err = error as { errorCode?: string; message?: string };
  if (err.errorCode === 'MODULE_NOT_AVAILABLE') {
    return true;
  }
  if (err.errorCode === 'NOT_FOUND') {
    const message = String(err.message ?? '');
    return /workInjury/i.test(message);
  }
  return false;
}

/**
 * 探测工伤模块是否已装配。
 *
 * @returns false=未装配（业务应跳过工伤链路）；true=已装配；undefined=网络等不确定
 */
export async function probeWorkInjuryModuleAvailable(): Promise<boolean | undefined> {
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
    const cap = await httpWorkInjuryModuleCapability();
    cachedModuleAvailable = cap?.enabled === true;
    return cachedModuleAvailable;
  } catch (error) {
    if (isWorkInjuryModuleUnavailableError(error)) {
      cachedModuleAvailable = false;
      return false;
    }
    return undefined;
  }
}

/** 测试或切换环境后清缓存 */
export function resetWorkInjuryModuleProbeCache(): void {
  cachedModuleAvailable = undefined;
  probing = null;
}
