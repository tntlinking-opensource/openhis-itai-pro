import fs from 'node:fs';
import path from 'node:path';

import type { Connect } from 'vite';
import type { Plugin } from 'vite';

/** 开发环境本地安装包目录，与院端 nginx alias C:/ZXPlat.Win/ 一致 */
const DEFAULT_DOWNLOADS_DIR = 'C:/ZXPlat.Win';

/** 安装包候选文件名（按优先级） */
const DOWNLOAD_FILE_CANDIDATES = ['ZXPlat.Win.exe', 'ZXPlat.Win-Setup.exe'];

function resolveDownloadFilePath(
  downloadsDir: string,
  fileName: string,
): null | { fileName: string; filePath: string; size: number } {
  const baseDir = path.resolve(downloadsDir);
  const candidates =
    fileName && !DOWNLOAD_FILE_CANDIDATES.includes(fileName)
      ? [fileName, ...DOWNLOAD_FILE_CANDIDATES]
      : DOWNLOAD_FILE_CANDIDATES;

  for (const candidate of candidates) {
    const filePath = path.resolve(baseDir, candidate);
    if (!filePath.startsWith(`${baseDir}${path.sep}`)) {
      continue;
    }
    if (!fs.existsSync(filePath)) {
      continue;
    }
    const stat = fs.statSync(filePath);
    if (!stat.isFile()) {
      continue;
    }
    return { fileName: candidate, filePath, size: stat.size };
  }

  return null;
}

function createDownloadsMiddleware(downloadsDir: string): Connect.NextHandleFunction {
  return (req, res, next) => {
    const url = req.url?.split('?')[0] ?? '';
    if (!url.startsWith('/downloads/')) {
      next();
      return;
    }

    const fileName = decodeURIComponent(url.slice('/downloads/'.length));
    if (!fileName || fileName.includes('..')) {
      res.statusCode = 400;
      res.end('Bad Request');
      return;
    }

    const resolved = resolveDownloadFilePath(downloadsDir, fileName);
    if (!resolved) {
      res.statusCode = 404;
      res.end('Not Found');
      return;
    }

    res.statusCode = 200;
    res.setHeader('Content-Type', 'application/octet-stream');
    res.setHeader('Content-Length', String(resolved.size));
    res.setHeader(
      'Content-Disposition',
      `attachment; filename="${path.basename(resolved.fileName)}"`,
    );

    if (req.method === 'HEAD') {
      res.end();
      return;
    }

    fs.createReadStream(resolved.filePath).pipe(res);
  };
}

/**
 * 开发环境不经 nginx：由 Vite dev server 直接映射 /downloads/* → 本机目录。
 * 生产/打包部署仍走 nginx location /downloads/。
 */
export function zxplatWinDownloadsDevPlugin(downloadsDir = DEFAULT_DOWNLOADS_DIR): Plugin {
  return {
    name: 'Zxplat-win-downloads-dev',
    apply: 'serve',
    enforce: 'pre',
    configureServer(server) {
      const handler = createDownloadsMiddleware(downloadsDir);
      // 插到最前，避免被 Vite SPA fallback 当成前端路由返回 index.html
      server.middlewares.stack.unshift({ route: '', handle: handler });
    },
  };
}
