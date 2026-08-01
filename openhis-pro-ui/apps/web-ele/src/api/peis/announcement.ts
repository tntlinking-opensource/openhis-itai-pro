/**
 * 小程序首页公告 — {@code POST /api/app/peisAnnouncement/<method>}
 */
import { publicRequestClient } from '#/api/request';

export interface HomeNoticeDto {
  id?: string | number;
  title?: string;
  contentHtml?: string;
  updatedAt?: string;
}

export interface SaveHomeNoticeRequest {
  title?: string;
  contentHtml: string;
}

export function getHomeNoticeApi() {
  return publicRequestClient.post<HomeNoticeDto>('/peisAnnouncement/getHomeNotice', {});
}

export function saveHomeNoticeApi(params: SaveHomeNoticeRequest) {
  return publicRequestClient.post<HomeNoticeDto>('/peisAnnouncement/saveHomeNotice', params);
}
