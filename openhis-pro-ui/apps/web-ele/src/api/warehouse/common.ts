import type { ApiOptionItem, ApiResponse } from '#/types/api-response';

import { publicRequestClient, requestClient } from '../request';
import { mockGetWarehouseByTypeForPurchase, mockSubmitPurchaseForReview } from './purchase.mock';

function useWarehousePurchaseMock(): boolean {
  return import.meta.env.VITE_USE_MOCK_WAREHOUSE_PURCHASE === 'true';
}

/**
 * 根据类型获取仓库（仓库、病区等），供采购、调拨等模块公用
 */
export async function getWarehouseByType(type: string) {
  if (useWarehousePurchaseMock()) {
    return mockGetWarehouseByTypeForPurchase(type);
  }
  return requestClient.post<ApiResponse<ApiOptionItem[]>>(
    `/warehouseInventory/getWarehouseByType`,
    {
      value: type,
    },
  );
}

/** 提交/撤销审核参数 */
export interface SubmitForReviewParams {
  supplyBusNo: string;
  type: string;
}

/**
 * 提交审核 / 撤销审核（采购、调拨、领用、报损共用一套参数，一个调用入口）
 */
export type SubmitForReviewModule = 'draw' | 'inventory' | 'loss' | 'purchase' | 'transfer';

export async function submitForReview(
  module: SubmitForReviewModule,
  params: SubmitForReviewParams,
) {
  if (module === 'purchase' && useWarehousePurchaseMock()) {
    return mockSubmitPurchaseForReview(params);
  }

  let path: string;
  switch (module) {
    case 'draw': {
      path = '/warehouseRequisition/sumbitForReview';
      break;
    }
    case 'inventory': {
      path = '/warehouseInventory/sumbitForReview';
      break;
    }
    case 'loss': {
      path = '/warehouseLoss/sumbitForReview';
      break;
    }
    case 'purchase': {
      path = '/warehousePurchase/sumbitForReview';
      break;
    }
    case 'transfer': {
      path = '/warehouseTransfer/sumbitForReview';
      break;
    }
    default: {
      throw new Error(`submitForReview: unknown module "${module}"`);
    }
  }
  return publicRequestClient.post<unknown>(path, params);
}
