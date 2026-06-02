import type {
  ApiOptionItem,
  GetInfoParams,
  PageApiResponse,
  PageParams,
} from '#/types/api-response';

import { publicRequestClient } from '../request';

export interface PageWarehouseInventoryParams extends PageParams {
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
}

export interface InitWarehouseInventoryOptionsResponse {
  categoryOptions: ApiOptionItem[];
  drugTypeOptions: ApiOptionItem[];
  practitionerOptions: ApiOptionItem[];
  statusOptions: ApiOptionItem[];
  supplierOptions: ApiOptionItem[];
  warehouseOptions: ApiOptionItem[];
  warehouseTypeOptions: ApiOptionItem[];
  workFlowOptions: ApiOptionItem[];
  profitReasonOptions: ApiOptionItem[];
}

export interface WarehouseInventoryItem {
  supplyBusNo: string;
  statusEnum?: string;
  statusEnumText?: string;
  typeEnumText?: string;
  supplierName?: string;
  createTime?: string;
  isBatches: boolean;
}

export interface BatchCreateInventoryDetailItem {
  version?: string;
  unitCode_dictText: string;
  itemQuantity: number;
  totalPrice: number;
  itemTable: string;
  remark: string;
  lotNumber: string;
  manufacturer: string;
  volume: string;
  itemId: string;
  totalQuantity: number;
  reasonCode_dictText: string;
  price: number;
  unitCode: string;
  name: string;
  reasonCode: string;
  inventoryId?: string;
}
export interface BatchCreateInventoryParams {
  purposeTypeEnum: string;
  purposeLocationId: string;
  details: BatchCreateInventoryDetailItem[];
  applyTime: string;
  categoryEnum: string;
  isBatches: boolean;
  dateRange?: {
    endTime?: string;
    startTime?: string;
  };
}

export interface InventoryDetailSupplyItem {
  id?: string;
  name?: string;
  itemId?: string;
  totalVolume?: string;
  unitCode?: string;
  unitCode_dictText?: string;
  minUnitCode?: string;
  minUnitCode_dictText?: string;
  itemQuantity?: number;
  totalQuantity?: number;
  price?: number;
  totalPrice?: number;
  lotNumber?: string;
  reasonCode?: string;
  reasonCode_dictText?: string;
  remark?: string;
  manufacturer?: string;
  manufacturerText?: string;
  itemTable?: string;
  partPercent?: number;
  version?: string;
  defUnitCode?: string;
}

export interface InventoryDetailResponse {
  supplyBusNo?: string;
  categoryEnum?: string;
  purposeTypeEnum?: string;
  purposeLocationId?: string;
  applyTime?: string;
  supplyList?: InventoryDetailSupplyItem[];
  /** 是否为批量盘点单 */
  isBatches?: boolean;
}

export interface BatchUpdateInventoryParams {
  deleteIds: string[];
  purposeTypeEnum: string;
  supplyBusNo: string;
  details: {
    id: string;
    inventoryId?: string;
    itemId: string;
    itemQuantity: number;
    itemTable: string;
    lotNumber: string;
    manufacturer: string;
    name: string;
    price: number;
    reasonCode: string;
    reasonCode_dictText: string;
    remark: string;
    totalPrice: number;
    totalQuantity: number;
    unitCode: string;
    unitCode_dictText: string;
    version?: string;
    volume: string;
  }[];
  applyTime: string;
  categoryEnum: string;
  isBatches: boolean;
}

export interface GetBatchInventoryParams extends PageParams {
  sortDirection: string;
  dateRange: {
    endTime: string;
    startTime: string;
  };
  purposeLocationId: string;
  itemTable: string;
  sortBy: string;
  keyword: string;
}

export async function initPharmacyWarehouseSuplyApi() {
  return publicRequestClient.post<InitWarehouseInventoryOptionsResponse>(
    '/warehouseInventory/init',
    {},
  );
}

export async function pageWarehouseInventoryApi(params: PageWarehouseInventoryParams) {
  return publicRequestClient.post<PageApiResponse<WarehouseInventoryItem>>(
    '/warehouseInventory/pageInventoryOrders',
    params,
  );
}

export async function batchCreateInventoryApi(params: BatchCreateInventoryParams) {
  return publicRequestClient.post<{ busNo: string }>(
    '/warehouseInventory/batchCreateInventory',
    params,
  );
}

export async function batchUpdateInventoryApi(params: BatchUpdateInventoryParams) {
  return publicRequestClient.post<{ busNo: string }>(
    '/warehouseInventory/updateInventoryRequest',
    params,
  );
}

export async function findInventoryByBusNoApi(params: GetInfoParams) {
  return publicRequestClient.post<InventoryDetailResponse>(
    '/warehouseInventory/findInventoryByBusNo',
    params,
  );
}

export async function deleteInventoryApi(params: GetInfoParams) {
  return publicRequestClient.post('/warehouseInventory/deleteInventoryRequest', params);
}

export async function getBatchInventoryApi(params: Partial<GetBatchInventoryParams>) {
  return publicRequestClient.post<PageApiResponse<InventoryDetailSupplyItem>>(
    '/warehouseInventory/batchInventory',
    params,
  );
}
