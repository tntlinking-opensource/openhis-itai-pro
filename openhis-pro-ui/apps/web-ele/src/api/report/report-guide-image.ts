/**
 * 套打定位纸上传（ReportDesignAppService.uploadGuideImage）
 */
import { publicRequestClient } from '#/api/request';

export interface GuideImageUploadDto {
  /** 相对站点根路径，如 /uploads/report-guides/2026/06/13/xxx.png */
  url: string;
}

export interface UploadGuideImageParams {
  contentBase64: string;
  fileName?: string;
  mimeType?: string;
}

const MAX_GUIDE_IMAGE_BYTES = 10 * 1024 * 1024;
const ACCEPT_MIME = 'image/png,image/jpeg,image/jpg,image/webp,image/svg+xml,image/bmp,image/gif';

function readFileAsDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.addEventListener('load', () => resolve(String(reader.result ?? '')));
    reader.addEventListener('error', () => reject(reader.error ?? new Error('read failed')));
    reader.readAsDataURL(file);
  });
}

/** 上传定位纸图，返回可写入 guideImageUrl 的相对路径 */
export async function uploadGuideImageApi(file: File): Promise<string> {
  if (!file.type.startsWith('image/')) {
    throw new Error('请上传图片文件');
  }
  if (file.size > MAX_GUIDE_IMAGE_BYTES) {
    throw new Error('定位纸图片请小于 10MB');
  }
  const contentBase64 = await readFileAsDataUrl(file);
  const dto = await publicRequestClient.post<GuideImageUploadDto>(
    '/reportDesign/uploadGuideImage',
    {
      contentBase64,
      fileName: file.name,
      mimeType: file.type || undefined,
    } satisfies UploadGuideImageParams,
  );
  return dto.url?.trim() ?? '';
}

export { ACCEPT_MIME, MAX_GUIDE_IMAGE_BYTES };
