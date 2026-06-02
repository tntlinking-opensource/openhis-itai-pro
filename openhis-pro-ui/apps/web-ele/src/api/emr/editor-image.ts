/**
 * 编辑器图片上传（whale-emr DocumentAppService.uploadEditorImage）
 */

import { publicRequestClient } from '#/api/request';

export interface EditorImageUploadDto {
  /** 相对站点根路径，如 /uploads/emr/2026/05/26/xxx.png */
  url: string;
}

export interface UploadEditorImageParams {
  /** data URL 或纯 Base64 */
  contentBase64: string;
  fileName?: string;
  mimeType?: string;
}

/** 将相对资源路径转为浏览器可访问 URL（开发态走 Vite /uploads 代理） */
export function resolveEmrAssetUrl(url: string): string {
  const raw = url.trim();
  if (!raw) {
    return '';
  }
  if (raw.startsWith('http://') || raw.startsWith('https://') || raw.startsWith('data:')) {
    return raw;
  }
  if (raw.startsWith('/')) {
    return raw;
  }
  return `/${raw}`;
}

function readFileAsDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.addEventListener('load', () => resolve(String(reader.result ?? '')));
    reader.addEventListener('error', () => reject(reader.error ?? new Error('read failed')));
    reader.readAsDataURL(file);
  });
}

export async function uploadEditorImageApi(file: File): Promise<string> {
  const contentBase64 = await readFileAsDataUrl(file);
  const dto = await publicRequestClient.post<EditorImageUploadDto>('/document/uploadEditorImage', {
    contentBase64,
    fileName: file.name,
    mimeType: file.type || undefined,
  } satisfies UploadEditorImageParams);
  return resolveEmrAssetUrl(dto.url);
}
