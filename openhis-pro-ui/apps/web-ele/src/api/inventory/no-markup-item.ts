import type { ApiResponse, PageApiResponse, PageParams } from '#/types/api-response';

import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import { publicRequestClient } from '../request';

/** 不加价清单一行。主键一律用字符串，避免雪花 ID 被转成 Number。 */
export interface NoMarkupItem {
  id?: string;
  itemTable?: string;
  itemId?: string;
  itemBusNo?: string;
  itemName?: string;
  kindLabel?: string;
}

export interface PageNoMarkupItemParams extends PageParams {
  keyword?: string;
  /** 药品、耗材；空表示全部 */
  kindLabel?: string;
}

export interface SaveNoMarkupItemParams {
  id?: string;
  itemTable?: string;
  itemId?: string;
}

const SITE =
  String(import.meta.env.VITE_ZXPLAT_PROJECT ?? 'H22010202493').trim() || 'H22010202493';
const BASE = `/${SITE.toLowerCase()}NoMarkupItem`;

/** 已维护的不加价清单 */
export async function pageNoMarkupItemApi(params: PageNoMarkupItemParams) {
  return publicRequestClient.post<ApiResponse<PageApiResponse<NoMarkupItem>>>(
    `${BASE}/page`,
    params,
  );
}

/** 尚未加入清单的在用药品/耗材 */
export async function pageNoMarkupCandidatesApi(params: PageNoMarkupItemParams) {
  return publicRequestClient.post<ApiResponse<PageApiResponse<NoMarkupItem>>>(
    `${BASE}/pageCandidates`,
    params,
  );
}

/** 加入不加价清单 */
export async function addNoMarkupItemApi(params: SaveNoMarkupItemParams) {
  return publicRequestClient.post<ApiResponse<NoMarkupItem>>(`${BASE}/add`, {
    itemTable: params.itemTable,
    itemId: preserveApiIdForRequest(params.itemId),
  });
}

/** 从清单移出，之后进货恢复自动加价 */
export async function removeNoMarkupItemApi(id: number | string) {
  return publicRequestClient.post<ApiResponse<null>>(`${BASE}/remove`, {
    id: preserveApiIdForRequest(id),
  });
}
