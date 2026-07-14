/** 院端默认：C:/OpenHis.Win/OpenHis.Win.exe，经 nginx /downloads/OpenHis.Win.exe 对外 */
const DEFAULT_DOWNLOAD_PATH = '/downloads/OpenHis.Win.exe';
const DEFAULT_FILE_NAME = 'OpenHis.Win.exe';

/** 小于该体积视为下载到了 HTML/错误页，而非真实安装包 */
const MIN_INSTALLER_BYTES = 100_000;

function joinBaseUrl(path: string): string {
  const base = import.meta.env.BASE_URL ?? '/';
  if (path.startsWith('/')) {
    return `${base.replace(/\/$/, '')}${path}`;
  }
  return `${base}${path}`;
}

/** 解析 OpenHIS.Win 安装包下载地址（默认走 nginx 静态映射，可用 VITE_GLOB_OPENHIS_WIN_DOWNLOAD_URL 覆盖） */
export function resolveOpenHisWinDownloadUrl(): string {
  const fromEnv = import.meta.env.VITE_GLOB_OPENHIS_WIN_DOWNLOAD_URL;
  if (typeof fromEnv === 'string' && fromEnv.trim()) {
    return fromEnv.trim();
  }
  return joinBaseUrl(DEFAULT_DOWNLOAD_PATH);
}

/** 从下载地址推断保存文件名 */
export function resolveOpenHisWinDownloadFileName(): string {
  const url = resolveOpenHisWinDownloadUrl();
  const segment = url.split('/').pop()?.split('?')[0]?.trim();
  return segment || DEFAULT_FILE_NAME;
}

/** 触发浏览器下载 OpenHIS.Win 安装包（先 HEAD 校验，避免下到 index.html） */
export async function triggerOpenHisWinDownload(): Promise<void> {
  const url = resolveOpenHisWinDownloadUrl();
  const response = await fetch(url, { method: 'HEAD' });
  const contentType = response.headers.get('content-type') ?? '';
  const contentLength = Number(response.headers.get('content-length') ?? '0');

  if (!response.ok) {
    throw new Error(
      `下载失败（HTTP ${response.status}），请确认 C:/OpenHis.Win/${resolveOpenHisWinDownloadFileName()} 是否存在`,
    );
  }

  if (contentType.includes('text/html')) {
    throw new Error('下载地址返回了网页而非安装包，开发环境请重启 Vite 后再试');
  }

  if (contentLength > 0 && contentLength < MIN_INSTALLER_BYTES) {
    throw new Error(`安装包体积异常（${contentLength} 字节），请检查服务器上的安装包文件`);
  }

  window.location.assign(url);
}
