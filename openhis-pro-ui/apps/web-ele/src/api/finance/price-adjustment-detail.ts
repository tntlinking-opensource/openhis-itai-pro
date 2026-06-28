/**
 * 调价明细 — POST /api/app/priceAdjustmentDetail/<methodName>
 */
import type { PageParams } from '#/types/api-response';

import type {
  PriceAdjustmentDetailDto,
  PriceAdjustmentLineDto,
} from '#/api/finance/price-adjustment';

import { publicRequestClient } from '#/api/request';

const BASE = '/priceAdjustmentDetail';

export interface InitPriceAdjustmentDetailResponse {
  defaultApproveTimeFrom?: string;
  defaultApproveTimeTo?: string;
  itemTableTabOptions?: Array<{ label?: string; value?: string }>;
  adjustmentReasonOptions?: Array<{ label?: string; value?: string }>;
  defaultPageSize?: number;
}

export interface PagePriceAdjustmentApprovedLineParams extends PageParams {
  approveTimeFrom: string;
  approveTimeTo: string;
  itemTable?: string;
  itemNameKeyword?: string;
  itemCodeKeyword?: string;
  busNo?: string;
}

export interface PriceAdjustmentApprovedLineListRowDto {
  id?: number | string;
  busNo?: string;
  approveTime?: string;
  itemTable?: string;
  itemTableText?: string;
  itemId?: number | string;
  itemName?: string;
  itemCode?: string;
  adjustmentStyle?: string;
  adjustmentStyleText?: string;
  adjustmentReason?: string;
  adjustmentReasonText?: string;
  totalImpact?: number | string;
  operatorId?: number | string;
  operatorName?: string;
  approverId?: number | string;
  approverName?: string;
}

export interface PagePriceAdjustmentApprovedLineResponse {
  content?: PriceAdjustmentApprovedLineListRowDto[];
  pageNo?: number;
  pageSize?: number;
  totalCount?: number;
}

export interface GetPriceAdjustmentApprovedLineParams {
  id: string;
}

export interface GetPriceAdjustmentApprovedLineResponse {
  line?: PriceAdjustmentLineDto;
  totalImpact?: number | string;
  approveTime?: string;
  approverId?: number | string;
  approverName?: string;
}

export type { PriceAdjustmentDetailDto, PriceAdjustmentLineDto };

export async function initPriceAdjustmentDetailApi() {
  return publicRequestClient.post<InitPriceAdjustmentDetailResponse>(
    `${BASE}/initPriceAdjustmentDetail`,
    {},
  );
}

export async function pagePriceAdjustmentApprovedLineApi(
  params: PagePriceAdjustmentApprovedLineParams,
) {
  return publicRequestClient.post<PagePriceAdjustmentApprovedLineResponse>(
    `${BASE}/pagePriceAdjustmentApprovedLine`,
    params,
  );
}

export async function getPriceAdjustmentApprovedLineApi(
  params: GetPriceAdjustmentApprovedLineParams,
) {
  return publicRequestClient.post<GetPriceAdjustmentApprovedLineResponse>(
    `${BASE}/getPriceAdjustmentApprovedLine`,
    params,
  );
}
