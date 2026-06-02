import type {
  ApiOptionItem,
  GetInfoParams,
  PageApiResponse,
  PageParams,
} from '#/types/api-response';

import { publicRequestClient } from '../request';

export interface PageTransferRequestParams extends PageParams {
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

export interface InitWarehouseTransferOptionsResponse {
  categoryOptions: ApiOptionItem[];
  drugTypeOptions: ApiOptionItem[];
  practitionerOptions: ApiOptionItem[];
  statusOptions: ApiOptionItem[];
  supplierOptions: ApiOptionItem[];
  warehouseOptions: ApiOptionItem[];
  warehouseTypeOptions: ApiOptionItem[];
  workFlowOptions: ApiOptionItem[];
}

export interface TransferRequestItem {
  id?: string;
  totalVolume: string;
  supplierId: string;
  itemQuantity: number;
  totalPrice: number;
  traceNo: string;
  itemTable: string;
  manufacturerId: string;
  remark: string;
  lotNumber: string;
  sourceInventoryItemId: string;
  name: string;
  itemId: string;
  price: number;
  unitCode: string;
  startTime: string;
  endTime: string;
}
export interface CreateTransferRequestParams {
  categoryEnum: string;
  sourceTypeEnum: string;
  purposeLocationId: string;
  applicantId: string;
  items: TransferRequestItem[];
  sourceLocationId: string;
  purposeTypeEnum: string;
  isSubmit: boolean;
}

export interface TransferPageResponse {
  applyTime: string;
  approvalTime: string;
  approveId: string;
  approveName: string;
  createBy: string;
  createByName: string;
  practitionerId: string;
  practitionerName: string;
  statusEnum: string;
  statusEnumText: string;
  supplierId: string;
  supplierName: string;
  supplyBusNo: string;
  transferRequestPageDtoList: TransferRequestItem[];
  typeEnum: string;
  typeEnumText: string;
}

export interface TransferDetailSupplyItem {
  id?: string;
  defUnitCode: string;
  busNo: string;
  totalVolume: string;
  name: string;
  lotNumber: string;
  manufacturerText: string;
  endTime: string;
  itemId: string;
  itemQuantity: number;
  itemTable: string;
  price: number;
  startTime: string;
  purposeLocationStoreName: string;
  remark: string;
  sourceLocationStoreName: string;
  totalPrice: number;
  traceNo: string;
  unitCode: string;
  unitCode_dictText: string;
  minUnitCode: string;
  minUnitCode_dictText: string;
  totalQuantity: number;
  partPercent: number;
}

export interface TransferDetailResponse {
  applicantId: string;
  applyTime: string;
  purposeLocationId: string;
  purposeTypeEnum: string;
  sourceLocationId: string;
  sourceTypeEnum: string;
  supplyBusNo: string;
  supplyList: TransferDetailSupplyItem[];
  categoryEnum: string;
}

export interface UpdateTransferSupplyItem {
  id?: string;
  itemId: string;
  itemQuantity: number;
  totalPrice: number;
  traceNo: string;
  itemTable: string;
  remark: string;
  lotNumber: string;
  productionDate?: string;
  price: number;
  unitCode: string;
  name: string;
  expirationDate?: string;
}

export interface UpdateTransferRequestParams {
  categoryEnum: string;
  sourceLocationType: string;
  deleteIds: string[];
  supplyBusNo: string;
  purposeLocationId: string;
  supplyList: UpdateTransferSupplyItem[];
  applyTime: string;
  sourceLocationId: string;
  purposeTypeEnum: string;
  isSubmit?: boolean;
}

export const initTransferRequestOptions = () => {
  return publicRequestClient.post<InitWarehouseTransferOptionsResponse>('/warehouseTransfer/init');
};

export const pageTransferRequest = (params: PageTransferRequestParams) => {
  return publicRequestClient.post<PageApiResponse<TransferPageResponse>>(
    '/warehouseTransfer/pageTransferRequest',
    params,
  );
};

export const createTransferRequest = (params: CreateTransferRequestParams) => {
  return publicRequestClient.post('/warehouseTransfer/createTransferRequest', params);
};

export const findTransferByBusNo = (pararm: GetInfoParams) => {
  return publicRequestClient.post<TransferDetailResponse>(
    `/warehouseTransfer/findTransferByBusNo`,
    pararm,
  );
};

export const deleteTransferRequest = (params: GetInfoParams) => {
  return publicRequestClient.post(`/warehouseTransfer/deleteTransferRequest`, params);
};

export const updateTransferRequest = (params: UpdateTransferRequestParams) => {
  return publicRequestClient.post(`/warehouseTransfer/updateTransferRequest`, params);
};
