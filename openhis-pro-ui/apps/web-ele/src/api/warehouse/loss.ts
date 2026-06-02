import type {
  ApiOptionItem,
  GetInfoParams,
  PageApiResponse,
  PageParams,
} from '#/types/api-response';

import { publicRequestClient } from '#/api/request';

/**
 * 报损列表项
 */
export interface LossReportItem {
  busNo?: string;
  supplyBusNo?: string;
  statusEnum?: string;
  statusEnumText?: string;
  typeEnum?: string;
  typeEnumText?: string;
  createTime?: string;
  applyTime?: string;
  [key: string]: any;
}

/**
 * 初始化报损选项响应
 */
export interface InitLossReportResponse {
  statusOptions?: ApiOptionItem[];
  typeOptions?: ApiOptionItem[];
  categoryOptions?: ApiOptionItem[];
  practitionerOptions?: ApiOptionItem[];
  supplierOptions?: ApiOptionItem[];
  warehouseTypeOptions?: ApiOptionItem[];
  warehouseOptions?: ApiOptionItem[];
  [key: string]: any;
}

export interface PageLossReportParams extends PageParams {
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

export interface LossRequestItem {
  itemId: string;
  reason: string;
  itemQuantity: number;
  supplierId: string;
  startTime: string;
  totalPrice: number;
  price: number;
  unitCode: string;
  itemTable: string;
  name: string;
  lotNumber: string;
  endTime: string;
  traceNo: string;
  remark: string;
}
export interface CreateLossRequestParams {
  supplierId: string;
  locationTypeEnum: string;
  locationId: string;
  supplyBusNo: string;
  lossRequestItems: LossRequestItem[];
  isSubmit: boolean;
  applyTime: string;
  practitionerId: string;
  categoryEnum: string;
}

export interface LossDetailItem extends LossRequestItem {
  id?: string;
  defUnitCode?: string;
  unitCode_dictText?: string;
  minUnitCode?: string;
  minUnitCode_dictText?: string;
  totalQuantity?: number;
  partPercent?: number;
  volume?: string;
}

export interface LossDetailResponse {
  applyTime: string;
  locationId: string;
  locationText?: string;
  locationTypeEnum: null | number | string;
  locationTypeEnumText?: null | string;
  practitionerId: string;
  supplierId: string;
  supplyBusNo: string;
  busNo?: string;
  categoryEnum?: string;
  supplyList?: Array<
    LossDetailItem & {
      busNo?: string;
      locationStoreName?: string;
      manufacturerText?: string;
      supplierName?: string;
      totalVolume?: string;
    }
  >;
}

export interface UpdateLossRequestItem extends LossRequestItem {
  id?: string;
}

export interface UpdateLossRequestParams {
  supplyBusNo: string;
  supplierId: string;
  locationTypeEnum: string;
  locationId: string;
  deleteIds: string[];
  supplyList: UpdateLossRequestItem[];
  isSubmit: boolean;
  applyTime: string;
  practitionerId: string;
  categoryEnum: string;
}

export async function pageLossReportApi(params: PageLossReportParams) {
  return publicRequestClient.post<PageApiResponse<LossReportItem>>(
    '/warehouseLoss/pageLossReport',
    params,
  );
}

export async function initLossReportApi() {
  return publicRequestClient.post<InitLossReportResponse>('/warehouseLoss/init', {});
}

export async function createLossRequestApi(params: CreateLossRequestParams) {
  return publicRequestClient.post<{ busNo: string }>('/warehouseLoss/createLossRequest', params);
}

export async function findLossByBusNoApi(params: GetInfoParams) {
  return publicRequestClient.post<LossDetailResponse>('/warehouseLoss/findLossByBusNo', params);
}

export async function deleteLossRequestApi(params: GetInfoParams) {
  return publicRequestClient.post('/warehouseLoss/deleteLossRequest', params);
}

export async function updateLossRequestApi(params: UpdateLossRequestParams) {
  return publicRequestClient.post('/warehouseLoss/updateLossRequest', params);
}
