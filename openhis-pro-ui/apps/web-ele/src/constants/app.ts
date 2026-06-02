/** 登录后默认首页（概览） */
export const DEFAULT_HOME_PATH = '/analytics';

/** 历史默认首页，登录后不再跳转至此 */
const LEGACY_HOME_PATHS = new Set(['/emr/workbench']);

export function resolvePostLoginPath(redirectParam?: string): string {
  if (!redirectParam) {
    return DEFAULT_HOME_PATH;
  }
  const path = decodeURIComponent(redirectParam);
  if (path.startsWith('/auth/') || LEGACY_HOME_PATHS.has(path)) {
    return DEFAULT_HOME_PATH;
  }
  return path;
}
