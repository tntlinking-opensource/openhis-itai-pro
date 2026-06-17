/**
 * 渠道配置 — 对接 {@code POST /api/app/channelBooking/*}
 */
import { publicRequestClient } from '#/api/request';

import type { BookingPagedResult } from './booking-types';

export interface ChannelConfigDto {
  id?: number;
  channelCode?: string;
  channelName?: string;
  enabledFlag?: boolean;
  quotaPercent?: number;
  remarkText?: string;
}

export interface PageChannelConfigsRequest {
  keyword?: string;
  pageNo: number;
  pageSize: number;
}

export interface SaveChannelConfigRequest {
  id?: number;
  channelCode: string;
  channelName: string;
  enabledFlag?: boolean;
  quotaPercent?: number;
  remarkText?: string;
}

export async function pageChannelConfigsApi(params: PageChannelConfigsRequest) {
  return publicRequestClient.post<BookingPagedResult<ChannelConfigDto>>(
    '/channelBooking/pageChannelConfigs',
    params,
  );
}

export async function loadChannelConfigApi(params: { id: number }) {
  return publicRequestClient.post<ChannelConfigDto>('/channelBooking/loadChannelConfig', params);
}

export async function saveChannelConfigApi(params: SaveChannelConfigRequest) {
  return publicRequestClient.post<{ id?: number }>('/channelBooking/saveChannelConfig', params);
}

export async function deleteChannelConfigApi(params: { id: number }) {
  return publicRequestClient.post<void>('/channelBooking/deleteChannelConfig', params);
}
