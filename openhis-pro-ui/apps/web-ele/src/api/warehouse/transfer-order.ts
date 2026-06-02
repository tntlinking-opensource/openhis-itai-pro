import type { ApiOptionItem } from '#/types/api-response';

import { publicRequestClient } from '#/api/request';

import {
  mockAgreeTransferOrderApproval,
  mockDeleteTransferOrder,
  mockInitTransferOrder,
  mockInitTransferOrderBusNo,
  mockPageTransferOrderDetail,
  mockPageTransferOrderList,
  mockPageTransferOrderMedication,
  mockSaveTransferOrder,
} from './transfer-order.mock';

function useTransferOrderMock(): boolean {
  return (
    import.meta.env.VITE_USE_MOCK_TRANSFER_ORDER === 'true' ||
    import.meta.env.VITE_USE_MOCK_PURCHASE_REQUEST === 'true'
  );
}

export interface TransferOrderInitResponse {
  cabinetListOptions: ApiOptionItem[];
  practitionerOption: ApiOptionItem[];
  supplierOption: ApiOptionItem[];
  supplyCategoryOptions: ApiOptionItem[];
  supplyStatusOptions: ApiOptionItem[];
  supplyTypeOptions: ApiOptionItem[];
}

export interface TransferOrderPageQuery {
  locationId?: number | string;
  keyword?: string;
}

export interface TransferOrderPageRecords {
  records: Record<string, any>[];
  total?: number;
}

export interface TransferOrderDetailQuery {
  busNo?: string;
  pageNo?: number;
  pageSize?: number;
}

export interface TransferOrderMedicationQuery {
  locationId?: number | string;
  pageNo?: number;
  pageSize?: number;
  keyword?: string;
  supplierId?: number | string;
  typeEnum?: number;
}

export async function initTransferOrderApi() {
  return useTransferOrderMock()
    ? mockInitTransferOrder()
    : publicRequestClient.get<TransferOrderInitResponse>('/pharmacy-warehouse/purchase-order/init');
}

export async function pageTransferOrderListApi(params?: TransferOrderPageQuery) {
  return useTransferOrderMock()
    ? mockPageTransferOrderList(params)
    : publicRequestClient.get<TransferOrderPageRecords>(
        '/pharmacy-warehouse/purchase-order/purchase-order-page',
        { params },
      );
}

export async function pageTransferOrderMedicationApi(params?: TransferOrderMedicationQuery) {
  return useTransferOrderMock()
    ? mockPageTransferOrderMedication(params)
    : publicRequestClient.get<TransferOrderPageRecords>(
        '/pharmacy-warehouse/purchase-order/medication-info',
        { params },
      );
}

export async function initTransferOrderBusNoApi() {
  return useTransferOrderMock()
    ? mockInitTransferOrderBusNo()
    : publicRequestClient.get<{ busNo: string }>('/pharmacy-warehouse/purchase-order/busNo-init');
}

export async function saveTransferOrderApi(data: Record<string, any>[]) {
  return useTransferOrderMock()
    ? mockSaveTransferOrder(data)
    : publicRequestClient.put<unknown>(
        '/pharmacy-warehouse/purchase-order/addOrEdit-purchaseOrder',
        data,
      );
}

export async function deleteTransferOrderApi(busNo: string) {
  return useTransferOrderMock()
    ? mockDeleteTransferOrder(busNo)
    : publicRequestClient.delete<unknown>(
        `/pharmacy-warehouse/purchase-order/delete-purchaseOrder`,
        {
          params: { busNo },
        },
      );
}

export async function agreeTransferOrderApprovalApi(busNo: string) {
  return useTransferOrderMock()
    ? mockAgreeTransferOrderApproval(busNo)
    : publicRequestClient.put<unknown>(
        '/pharmacy-warehouse/purchase-order/agree-approval',
        {},
        { params: { busNo } },
      );
}

export async function pageTransferOrderDetailApi(params?: TransferOrderDetailQuery) {
  return useTransferOrderMock()
    ? mockPageTransferOrderDetail(params)
    : publicRequestClient.get<TransferOrderPageRecords>(
        '/pharmacy-warehouse/purchase-order/purchase-order-detail-page',
        { params },
      );
}
