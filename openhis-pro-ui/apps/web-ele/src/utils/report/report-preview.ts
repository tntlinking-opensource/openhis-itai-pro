import type { LayoutPage } from '#/types/report-layout';

import { isCustomPageSize, pageSizeMm } from '#/utils/report/overlay-layout';

export interface PreviewLayoutMetaDto {
  layoutMode?: string;
  pageSize?: string;
  orientation?: string;
  guideImageUrl?: string;
}

const PAGE_ASPECT: Record<string, number> = {
  A4: 210 / 297,
  A5: 148 / 210,
};

export function pageAspectRatio(
  pageSize?: string | null,
  orientation?: string | null,
  page?: LayoutPage | null,
): number {
  if (isCustomPageSize(page)) {
    return pageAspectRatioFromPage(page);
  }
  const key = (pageSize ?? 'A4').toUpperCase();
  let ratio = PAGE_ASPECT[key] ?? PAGE_ASPECT.A4;
  if (orientation === 'landscape') {
    ratio = 1 / ratio;
  }
  return ratio;
}

export function pageAspectRatioFromPage(page?: LayoutPage | null): number {
  const { widthMm, heightMm } = pageSizeMm(page);
  return widthMm / heightMm;
}

/** 是否已上传/配置可用定位纸（出厂示意路径不算） */
export function hasUserGuideImageUrl(rawUrl?: string | null): boolean {
  const trimmed = rawUrl?.trim();
  if (!trimmed) {
    return false;
  }
  if (trimmed.startsWith('/assets/print-guides/')) {
    return false;
  }
  return (
    trimmed.startsWith('http://') ||
    trimmed.startsWith('https://') ||
    trimmed.startsWith('data:') ||
    trimmed.startsWith('/uploads/')
  );
}

export function resolveGuideImageUrl(rawUrl?: string | null): string {
  if (!hasUserGuideImageUrl(rawUrl)) {
    return '';
  }
  return rawUrl!.trim();
}

export function isOverlayPreview(meta?: PreviewLayoutMetaDto | null): boolean {
  return meta?.layoutMode === 'overlay';
}
