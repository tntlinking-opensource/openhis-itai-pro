import type { ApiOptionItem, PageApiResponse, PageParams } from '#/types/api-response';

import { publicRequestClient } from '#/api/request';

export interface PagePendingApprovalOrdersParams extends PageParams {
  statusEnum?: string;
  typeEnum?: string;
  busNo?: string;
  keyword?: string;
}

export interface PagePendingApprovalOrdersResponse {
  applicantId_dictText: string;
  applyTime: string;
  approvalTime: string;
  approveId: string;
  approveName_dictText: string;
  busNo: string;
  createBy: string;
  createByName: string;
  hasReturn: string;
  practitionerId: string;
  practitionerId_dictText: string;
  purchaseRequestPageDtoList: unknown[];
  statusEnum: string;
  statusEnumText: string;
  supplierId: string;
  supplierName: string;
  typeEnum: string;
  typeEnumText: string;
}

export const pagePendingApprovalOrders = (params: PagePendingApprovalOrdersParams) => {
  return publicRequestClient.post<PageApiResponse<PagePendingApprovalOrdersResponse>>(
    '/warehouseApproval/pagePendingApprovalOrders',
    params,
  );
};

export const executeApproval = (params: { busNo: string; cancelFlag: string }) => {
  return publicRequestClient.post<unknown>('/warehouseApproval/executeApproval', params);
};

export const init = () => {
  return publicRequestClient.post<WarehouseApprovalInitResponse>('/warehouseApproval/init');
};

export interface WarehouseApprovalInitOption extends ApiOptionItem {
  busNo?: null | string;
  children?: null | WarehouseApprovalInitOption[];
}

export interface WarehouseApprovalInitResponse {
  statusOptions?: WarehouseApprovalInitOption[];
  typeOptions?: WarehouseApprovalInitOption[];
  errorCode?: null | string;
}
