import type {
  ApiOptionItem,
  GetInfoParams,
  PageApiResponse,
  PageParams,
} from '#/types/api-response';

import { publicRequestClient } from '#/api/request';

/**
 * 领用列表项
 */
export interface DrawRequisitionItem {
  supplyBusNo?: string;
  busNo?: string;
  statusEnum?: string;
  statusEnumText?: string;
  typeEnum?: string;
  typeEnumText?: string;
  applyTime?: string;
  createTime?: string;
  practitionerName?: string;
  applicantName?: string;
  approverName?: string;
  approvalTime?: string;
}

export interface InitWarehouseDrawOptionsResponse {
  categoryOptions: ApiOptionItem[];
  drugTypeOptions: ApiOptionItem[];
  practitionerOptions: ApiOptionItem[];
  statusOptions: ApiOptionItem[];
  supplierOptions: ApiOptionItem[];
  warehouseOptions: ApiOptionItem[];
  warehouseTypeOptions: ApiOptionItem[];
  workFlowOptions: ApiOptionItem[];
  organizationOptions: ApiOptionItem[];
}

export interface PageWarehouseDrawParams extends PageParams {
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
  applicantId?: string;
}

export interface CreateOutboundRequestItem {
  totalVolume: string;
  quantity: number;
  totalPrice: number;
  salePrice: number;
  itemTable: string;
  manufacturerId: string;
  remark: string;
  lotNumber: string;
  sourceLocationId: string;
  itemId: string;
  ybNo: string;
  totalQuantity: number;
  price: number;
  unitCode: string;
  name: string;
}
export interface CreateOutboundRequestParams {
  purposeLocationId: string;
  locationTypeEnum: string;
  locationId: string;
  supplyBusNo: string;
  isSubmit: boolean;
  applicantId: string;
  applyTime: string;
  practitionerId: string;
  items: CreateOutboundRequestItem[];
  categoryEnum: string;
}

/**
 * 领用详情明细项（find 接口 supplyList 结构）
 */
export interface DrawDetailSupplyItem {
  id?: string;
  defUnitCode?: string;
  busNo?: string;
  totalVolume: string;
  name: string;
  lotNumber: string;
  manufacturerText?: string;
  endTime: string;
  itemId: string;
  itemQuantity: number;
  itemTable?: string;
  price: number;
  startTime: string;
  remark: string;
  totalPrice: number;
  traceNo: string;
  unitCode: string;
  unitCode_dictText?: string;
  minUnitCode?: string;
  minUnitCode_dictText?: string;
  totalQuantity?: number;
  partPercent?: number;
}

/**
 * 领用详情响应（findRequisitionByBusNo）
 */
export interface DrawDetailResponse {
  supplyBusNo?: string;
  applyTime?: string;
  locationId?: string;
  locationTypeEnum?: string;
  categoryEnum?: string;
  applicantId?: string;
  practitionerId?: string;
  purposeLocationId?: string;
  supplierId?: string;
  supplyList?: DrawDetailSupplyItem[];
}

export interface UpdateDrawRequisitionInfoSupplyItem {
  reason?: string;
  itemQuantity: number;
  supplierId?: string;
  totalPrice: number;
  itemTable: string;
  lotNumber: string;
  itemId: string;
  price: number;
  unitCode: string;
  name: string;
  startTime: string;
  id?: string;
  endTime: string;
}
export interface UpdateDrawRequisitionInfoParams {
  purposeLocationId: string;
  deleteIds: string[];
  locationTypeEnum: string;
  locationId: string;
  supplyBusNo: string;
  supplyList: UpdateDrawRequisitionInfoSupplyItem[];
  isSubmit: boolean;
  applyTime: string;
  practitionerId: string;
  categoryEnum: string;
}
export async function initDrawRequisitionApi() {
  return publicRequestClient.post<InitWarehouseDrawOptionsResponse>(
    '/warehouseRequisition/init',
    {},
  );
}

export async function pageDrawRequisitionApi(params: PageWarehouseDrawParams) {
  return publicRequestClient.post<PageApiResponse<DrawRequisitionItem>>(
    '/warehouseRequisition/pageRequisitionOrders',
    params,
  );
}

export async function createOutboundRequestApi(params: CreateOutboundRequestParams) {
  return publicRequestClient.post<{ busNo: string }>(
    '/warehouseRequisition/createOutboundRequest',
    params,
  );
}

export async function updateDrawRequisitionInfoApi(params: UpdateDrawRequisitionInfoParams) {
  return publicRequestClient.post<any>('/warehouseRequisition/updateRequisitionRequest', params);
}

export function findRequisitionByBusNoApi(params: GetInfoParams) {
  return publicRequestClient.post<DrawDetailResponse>(
    '/warehouseRequisition/findRequisitionByBusNo',
    params,
  );
}

export function deleteRequisitionRequestApi(params: GetInfoParams) {
  return publicRequestClient.post('/warehouseRequisition/deleteRequisitionRequest', params);
}
