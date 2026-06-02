import type { ApiOptionItem, GetInfoParams, PageParams } from '#/types/api-response';

import { publicRequestClient } from '#/api/request';

import {
  mockCreatePurchase,
  mockDeleteWarehousePurchaseApi,
  mockGetPurchaseById,
  mockInitPharmacyWarehouseSuplyApi,
  mockPageWarehousePurchaseApi,
  mockUpdatePurchase,
} from './purchase.mock';

function useWarehousePurchaseMock(): boolean {
  return import.meta.env.VITE_USE_MOCK_WAREHOUSE_PURCHASE === 'true';
}

/**
 * 初始化采购入库选项响应类型
 */
export interface InitPharmacyWarehouseSuplyResponse {
  statusOptions: Array<ApiOptionItem>;
  warehouseTypeOptions: Array<ApiOptionItem>;
  warehouseOptions: Array<ApiOptionItem>;
  supplierOptions: Array<ApiOptionItem>;
  practitionerOptions: Array<ApiOptionItem>;
  categoryOptions: Array<ApiOptionItem>;
}

/**
 * 分页查询采购入库请求参数
 */
export interface PageWarehousePurchaseParams extends PageParams {
  keyword?: string;
  busNo?: string;
  supplierId?: string;
  typeEnum?: string;
  occurrenceTime?: [string, string];
  dateRange?: {
    endTime?: string;
    startTime?: string;
  };
  statusEnum?: string;
  practitionerId?: string;
}

/**
 * 采购入库列表项
 */
export interface WarehousePurchaseItem {
  supplyBusNo: string;
  practitionerId: null | string;
  applyTime: string;
  supplierId: string;
  typeEnum: number;
  locationTypeEnum: number;
  createTime: string;
  statusEnum: string;
  statusEnumText: string;
}

/**
 * 分页响应数据
 */
export interface PageWarehousePurchaseResponse {
  content: WarehousePurchaseItem[];
  pageable: {
    offset: number;
    paged: boolean;
    pageNumber: number;
    pageSize: number;
    sort: {
      empty: boolean;
      sorted: boolean;
      unsorted: boolean;
    };
    unpaged: boolean;
  };
  last: boolean;
  totalCount: number;
  totalPages: number;
  size: number;
  number: number;
  sort: {
    empty: boolean;
    sorted: boolean;
    unsorted: boolean;
  };
  first: boolean;
  numberOfElements: number;
  empty: boolean;
}

/**
 * 创建采购入库 - 明细项
 */
export interface CreatePurchaseSupplyItem {
  busNo?: string;
  lotNumber?: string;
  itemId: string;
  name?: string;
  unitCode?: string;
  itemTable?: string;
  salePrice?: number;
  totalSalePrice?: number;
  itemQuantity?: number;
  price?: number;
  totalPrice?: number;
  expirationDate?: string;
  traceNo?: string;
  invoiceNo?: string;
  remake?: string;
  unitList?: ApiOptionItem[];
}

/**
 * 创建采购入库请求参数
 */
export interface CreatePurchaseParams {
  isSubmit?: boolean; // 是否提交审核
  practitionerId?: string;
  applyTime?: string;
  supplierId?: string;
  typeEnum?: string;
  locationTypeEnum?: string;
  locationId?: string;
  supplyList: CreatePurchaseSupplyItem[];
}

export interface ReturnPurchaseParams {
  supplierId: string;
  originalBusNo: string;
  remark: string;
  busNo?: string;
  reasonCode: string;
  applicantId: string;
  items: {
    inventoryItemId: string;
    itemId: string;
    itemName: string;
    itemTable: string;
    lotNumber: string;
    note: string;
    price: string;
    quantity: string;
    reason?: string;
    salePrice: string;
    sourceLocationId: string;
    sourceLocationStoreId: string;
    unitCode: string;
  }[];
}

export interface UpdatePurchaseParams {
  isSubmit?: boolean;
  supplierId: string;
  deleteIds: string[];
  locationTypeEnum: string;
  locationId: string;
  supplyBusNo: string;
  supplyList: {
    approvalTime: string;
    approverId: string;
    busNo: string;
    endTime: string;
    expirationDate: string;
    id: string;
    invoiceNo: string;
    itemId: string;
    itemQuantity: number;
    itemTable: string;
    locationId: string;
    lotNumber: string;
    name: string;
    packagingConditionEnum: string;
    partPrice: number;
    partSalePrice: number;
    price: number;
    purposeTypeEnum: string;
    remake: string;
    salePrice: number;
    startTime: string;
    statusEnum: string;
    totalPrice: number;
    totalSalePrice: number;
    traceNo: string;
    unitCode: string;
  }[];
  practitionerId: string;
  applyTime: string;
  categoryEnum: string;
}
export interface PurchaseSupplyItemDetail {
  id: string;
  busNo: string;
  totalVolume: string;
  name: string;
  lotNumber: string;
  manufacturerText: string;
  itemId: string;
  itemTable: string;
  itemQuantity: number;
  price: number;
  totalPrice: number;
  locationStoreName: string;
  startDate: string;
  expirationDate: string;
  remake: string;
  defUnitCode: string;
  minUnitCode: string;
  minUnitCode_dictText: string;
  unitCode: string;
  unitCode_dictText: string;
  startTime: string;
  endTime: string;
}

export interface PurchaseDetailResponse {
  supplyBusNo: string;
  practitionerId: string;
  applyTime: string;
  supplierId: string;
  categoryEnum: string;
  category_enumText: string;
  locationTypeEnum: string;
  locationType_enumText: string;
  locationId: string;
  locationText: string;
  supplyList: PurchaseSupplyItemDetail[];
  startTime: string;
  endTime: string;
  invoiceNo: string;
  traceNo: string;
}
/**
 * 初始化采购入库选项
 */
export async function initPharmacyWarehouseSuplyApi() {
  return useWarehousePurchaseMock()
    ? mockInitPharmacyWarehouseSuplyApi()
    : publicRequestClient.post<InitPharmacyWarehouseSuplyResponse>('/warehousePurchase/init', {});
}

/**
 * 分页查询采购入库
 */
export async function pageWarehousePurchaseApi(params: PageWarehousePurchaseParams) {
  return useWarehousePurchaseMock()
    ? mockPageWarehousePurchaseApi(params)
    : publicRequestClient.post<PageWarehousePurchaseResponse>(
        '/warehousePurchase/pagePurchaseOrders',
        params,
      );
}

export async function createPurchase(params: CreatePurchaseParams) {
  return useWarehousePurchaseMock()
    ? mockCreatePurchase(params)
    : publicRequestClient.post<{ busNo: string }>('/warehousePurchase/createPurchaseOrder', params);
}

export async function getPurchaseById(params: GetInfoParams) {
  return useWarehousePurchaseMock()
    ? mockGetPurchaseById(params)
    : publicRequestClient.post<PurchaseDetailResponse>(
        `/warehousePurchase/findPurchaseByBusNo`,
        params,
      );
}

export async function deleteWarehousePurchaseApi(id: string) {
  return useWarehousePurchaseMock()
    ? mockDeleteWarehousePurchaseApi(id)
    : publicRequestClient.post<any>(`/warehousePurchase/deletePurchaseOrder`, id);
}

/**
 * 退库申请
 */
export async function returnPurchase(params: ReturnPurchaseParams) {
  return publicRequestClient.post<any>(`/warehousePurchase/createReturnRequest`, params);
}

/**
 * 更新采购入库
 */
export async function updatePurchase(params: UpdatePurchaseParams) {
  return useWarehousePurchaseMock()
    ? mockUpdatePurchase(params)
    : publicRequestClient.post<any>(`/warehousePurchase/updatePurchaseOrder`, params);
}
