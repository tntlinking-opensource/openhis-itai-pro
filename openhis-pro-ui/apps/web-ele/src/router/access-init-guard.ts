/** 路由守卫专用：轻量超时工具，避免静态 import access-init 拉取菜单与全量路由 */

/** 应用启动后台预拉菜单：超时即放弃，不阻塞首屏（应对每日后端重启） */
export const BOOTSTRAP_PREFETCH_TIMEOUT_MS = 3000;

/** 路由守卫内菜单初始化：超时回登录页，避免串行接口 10s×N */
export const ROUTER_ACCESS_INIT_TIMEOUT_MS = 5000;

/** 首次导航命中兜底 404 时的路由名 */
const NOT_FOUND_ROUTE_NAMES = new Set(['FallbackNotFound', 'NotFound']);

/** 菜单初始化超时（后端冷启动/重启时尚未就绪） */
export class AccessInitTimeoutError extends Error {
  constructor(message = '菜单初始化超时') {
    super(message);
    this.name = 'AccessInitTimeoutError';
  }
}

/** 为 Promise 附加超时；可选 onTimeout 供 access-init 清理悬挂初始化 */
export function withAccessInitTimeout<T>(
  promise: Promise<T>,
  timeoutMs: number,
  onTimeout?: () => void,
): Promise<T> {
  return new Promise((resolve, reject) => {
    const timer = window.setTimeout(() => {
      onTimeout?.();
      reject(new AccessInitTimeoutError());
    }, timeoutMs);

    promise.then(
      (value) => {
        window.clearTimeout(timer);
        resolve(value);
      },
      (error) => {
        window.clearTimeout(timer);
        reject(error);
      },
    );
  });
}

export function isNotFoundRouteName(name: unknown): boolean {
  return typeof name === 'string' && NOT_FOUND_ROUTE_NAMES.has(name);
}
