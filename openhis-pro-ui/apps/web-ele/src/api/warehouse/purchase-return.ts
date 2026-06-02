/* eslint-disable @typescript-eslint/no-explicit-any -- 退货接口字段与后端宽松对齐 */
import type { ApiOptionItem } from '#/types/api-response';

import { publicRequestClient } from '#/api/request';

import {
  mockAgreePurchaseReturnApproval,
  mockDeletePurchaseReturnOrder,
  mockInitPurchaseReturn,
  mockInitPurchaseReturnBusNo,
  mockPagePurchaseReturnDetail,
  mockPagePurchaseReturnList,
  mockPagePurchaseReturnMedication,
  mockPageStockInOrderDetail,
  mockPageStockInOrderList,
  mockSavePurchaseReturn,
} from './purchase-return.mock';

function usePurchaseReturnMock(): boolean {
  return import.meta.env.VITE_USE_MOCK_PURCHASE_RETURN === 'true';
}

/** 退货工作站 init（结构与订货 init 相近） */
export interface PurchaseReturnInitResponse {
  acceptanceResultoryOptions?: ApiOptionItem[];
  cabinetListOptions?: Record<string, any>[];
  packagingConditionOptions?: ApiOptionItem[];
  practitionerOption?: ApiOptionItem[];
  supplierOption?: ApiOptionItem[];
  supplyCategoryOptions?: ApiOptionItem[];
  supplyStatusOptions?: ApiOptionItem[];
  supplyTypeOptions?: ApiOptionItem[];
}

export interface PurchaseReturnPageRecords {
  records: Record<string, any>[];
  total?: number;
}

/** init */
export async function initPurchaseReturnApi() {
  return usePurchaseReturnMock()
    ? mockInitPurchaseReturn()
    : publicRequestClient.get<PurchaseReturnInitResponse>('/pharmacy-warehouse/return-order/init');
}

/** 左侧单据分页列表 */
export async function pagePurchaseReturnListApi(params?: Record<string, any>) {
  return usePurchaseReturnMock()
    ? mockPagePurchaseReturnList(params)
    : publicRequestClient.get<PurchaseReturnPageRecords>(
        '/pharmacy-warehouse/return-order/return-order-page',
        { params },
      );
}

/** 明细表格分页（退货明细接口） */
export async function pagePurchaseReturnDetailApi(params?: {
  busNo?: string;
  pageNo?: number;
  pageSize?: number;
}) {
  return usePurchaseReturnMock()
    ? mockPagePurchaseReturnDetail(params)
    : publicRequestClient.get<PurchaseReturnPageRecords>(
        '/pharmacy-warehouse/return-order/return-order-detail-page',
        { params },
      );
}

/** 选药品 */
export async function pagePurchaseReturnMedicationApi(params?: Record<string, any>) {
  return usePurchaseReturnMock()
    ? mockPagePurchaseReturnMedication(params)
    : publicRequestClient.get<PurchaseReturnPageRecords>(
        '/pharmacy-warehouse/return-order/medication-info',
        { params },
      );
}

/** 新单据号 */
export async function initPurchaseReturnBusNoApi() {
  return usePurchaseReturnMock()
    ? mockInitPurchaseReturnBusNo()
    : publicRequestClient.get<{ busNo: string }>('/pharmacy-warehouse/return-order/busNo-init');
}

/** 保存退货单 */
export async function savePurchaseReturnApi(data: Record<string, any>[]) {
  return usePurchaseReturnMock()
    ? mockSavePurchaseReturn(data)
    : publicRequestClient.put<unknown>(
        '/pharmacy-warehouse/return-order/addOrEdit-returnOrder',
        data,
      );
}

export async function deletePurchaseReturnOrderApi(busNo: string) {
  return usePurchaseReturnMock()
    ? mockDeletePurchaseReturnOrder(busNo)
    : publicRequestClient.delete<unknown>('/pharmacy-warehouse/return-order/delete-returnOrder', {
        params: { busNo },
      });
}

export async function agreePurchaseReturnApprovalApi(busNo: string) {
  return usePurchaseReturnMock()
    ? mockAgreePurchaseReturnApproval(busNo)
    : publicRequestClient.put<unknown>(
        '/pharmacy-warehouse/return-order/agree-approval',
        {},
        { params: { busNo } },
      );
}

/** 进货单导入弹窗：列表 */
export async function pageStockInOrderListApi(params?: Record<string, any>) {
  return usePurchaseReturnMock()
    ? mockPageStockInOrderList(params)
    : publicRequestClient.get<PurchaseReturnPageRecords>(
        '/pharmacy-warehouse/stockIn-order/stockIn-order-page',
        { params },
      );
}

/** 进货单导入弹窗：明细 */
export async function pageStockInOrderDetailApi(params?: Record<string, any>) {
  return usePurchaseReturnMock()
    ? mockPageStockInOrderDetail(params)
    : publicRequestClient.get<PurchaseReturnPageRecords>(
        '/pharmacy-warehouse/stockIn-order/stockIn-order-detail-page',
        { params },
      );
}
