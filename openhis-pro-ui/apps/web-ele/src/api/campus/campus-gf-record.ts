/**
 * 公费比例申请记录 — POST /api/app/campusGfRequestRecord/<method>
 */
import type { CampusPagedResult, GfChargeMode, IdLike } from './types';

import { publicRequestClient } from '#/api/request';

export interface GfRatioRequestDto {
  id?: IdLike;
  requestNo?: string;
  chargeModeEnum?: GfChargeMode;
  chargeModeLabel?: string;
  itemCode?: string;
  oldRatio?: number;
  newRatio?: number;
  statusEnum?: string;
  applicantId?: IdLike;
  applicantName?: string;
  applyTime?: string;
  approveTime?: string;
  remark?: string;
}

export function createAdjustRequestApi(params: {
  chargeModeEnum: GfChargeMode;
  itemCode: string;
  newRatio: number;
  oldRatio?: number;
  reason?: string;
}) {
  return publicRequestClient.post<{ id?: IdLike; requestNo?: string }>(
    '/campusGfRequestRecord/createAdjustRequest',
    params,
  );
}

export function pageRequestsApi(params: {
  chargeModeEnum?: GfChargeMode | null;
  statusEnum?: string | null;
  keyword?: string;
  pageNo: number;
  pageSize: number;
}) {
  return publicRequestClient.post<CampusPagedResult<GfRatioRequestDto>>(
    '/campusGfRequestRecord/pageRequests',
    params,
  );
}

export function getRequestDetailApi(params: { requestId: IdLike }) {
  return publicRequestClient.post<GfRatioRequestDto>(
    '/campusGfRequestRecord/getRequestDetail',
    params,
  );
}
