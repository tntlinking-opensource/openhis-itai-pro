import type { ApiOptionItem } from '#/types/api-response';

import { publicRequestClient } from '#/api/request';

import {
  mockAgreePurchaseOrderApproval,
  mockDeletePurchaseOrder,
  mockInitPurchaseOrder,
  mockInitPurchaseOrderBusNo,
  mockPagePurchaseOrderDetail,
  mockPagePurchaseOrderList,
  mockPagePurchaseOrderMedication,
  mockSavePurchaseOrder,
} from './purchase-request.mock';

function usePurchaseRequestMock(): boolean {
  return import.meta.env.VITE_USE_MOCK_PURCHASE_REQUEST === 'true';
}

/** 药库订货单初始化 */
export interface PurchaseOrderInitResponse {
  cabinetListOptions: ApiOptionItem[];
  practitionerOption: ApiOptionItem[];
  supplierOption: ApiOptionItem[];
  supplyCategoryOptions: ApiOptionItem[];
  supplyStatusOptions: ApiOptionItem[];
  supplyTypeOptions: ApiOptionItem[];
}

export interface PurchaseOrderPageQuery {
  locationId?: number | string;
  keyword?: string;
  /** 申请日期起（含），YYYY-MM-DD */
  applyTimeBegin?: string;
  /** 申请日期止（含），YYYY-MM-DD */
  applyTimeEnd?: string;
}

export interface PurchaseOrderPageRecords {
  records: Record<string, any>[];
  total?: number;
}

export interface PurchaseOrderDetailQuery {
  busNo?: string;
  pageNo?: number;
  pageSize?: number;
}

export interface MedicationInfoQuery {
  locationId?: number | string;
  pageNo?: number;
  pageSize?: number;
  keyword?: string;
  supplierId?: number | string;
  typeEnum?: number;
}

/**
 * 订货单选项初始化
 */
export async function initPurchaseOrderApi() {
  return usePurchaseRequestMock()
    ? mockInitPurchaseOrder()
    : publicRequestClient.get<PurchaseOrderInitResponse>('/pharmacy-warehouse/purchase-order/init');
}

/**
 * 订货单单据分页列表（左侧）
 */
export async function pagePurchaseOrderListApi(params?: PurchaseOrderPageQuery) {
  return usePurchaseRequestMock()
    ? mockPagePurchaseOrderList(params)
    : publicRequestClient.get<PurchaseOrderPageRecords>(
        '/pharmacy-warehouse/purchase-order/purchase-order-page',
        { params },
      );
}

/**
 * 根据供应商等条件查询药品信息
 */
export async function pagePurchaseOrderMedicationApi(params?: MedicationInfoQuery) {
  return usePurchaseRequestMock()
    ? mockPagePurchaseOrderMedication(params)
    : publicRequestClient.get<PurchaseOrderPageRecords>(
        '/pharmacy-warehouse/purchase-order/medication-info',
        { params },
      );
}

/** 获取新单据号 */
export async function initPurchaseOrderBusNoApi() {
  return usePurchaseRequestMock()
    ? mockInitPurchaseOrderBusNo()
    : publicRequestClient.get<{ busNo: string }>('/pharmacy-warehouse/purchase-order/busNo-init');
}

/** 新增 / 编辑订货单 */
export async function savePurchaseOrderApi(data: Record<string, any>[]) {
  return usePurchaseRequestMock()
    ? mockSavePurchaseOrder(data)
    : publicRequestClient.put<unknown>(
        '/pharmacy-warehouse/purchase-order/addOrEdit-purchaseOrder',
        data,
      );
}

/** 删除单据 */
export async function deletePurchaseOrderApi(busNo: string) {
  return usePurchaseRequestMock()
    ? mockDeletePurchaseOrder(busNo)
    : publicRequestClient.delete<unknown>(
        `/pharmacy-warehouse/purchase-order/delete-purchaseOrder`,
        { params: { busNo } },
      );
}

/** 同意审批 */
export async function agreePurchaseOrderApprovalApi(busNo: string) {
  return usePurchaseRequestMock()
    ? mockAgreePurchaseOrderApproval(busNo)
    : publicRequestClient.put<unknown>(
        `/pharmacy-warehouse/purchase-order/agree-approval`,
        {},
        { params: { busNo } },
      );
}

/** 单据明细分页 */
export async function pagePurchaseOrderDetailApi(params?: PurchaseOrderDetailQuery) {
  return usePurchaseRequestMock()
    ? mockPagePurchaseOrderDetail(params)
    : publicRequestClient.get<PurchaseOrderPageRecords>(
        '/pharmacy-warehouse/purchase-order/purchase-order-detail-page',
        { params },
      );
}
