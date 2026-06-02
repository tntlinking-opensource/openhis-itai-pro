import type { ApiOptionItem } from '#/types/api-response';

import { publicRequestClient } from '#/api/request';

import {
  mockAgreeReturnInboundApproval,
  mockDeleteReturnInbound,
  mockInitReturnInbound,
  mockInitReturnInboundBusNo,
  mockPageReturnInboundDetail,
  mockPageReturnInboundList,
  mockPageReturnInboundMedication,
  mockSaveReturnInbound,
} from './return-inbound.mock';

function useReturnInboundMock(): boolean {
  return (
    import.meta.env.VITE_USE_MOCK_RETURN_INBOUND === 'true' ||
    import.meta.env.VITE_USE_MOCK_PURCHASE_REQUEST === 'true'
  );
}

export interface ReturnInboundInitResponse {
  cabinetListOptions: ApiOptionItem[];
  practitionerOption: ApiOptionItem[];
  supplierOption: ApiOptionItem[];
  supplyCategoryOptions: ApiOptionItem[];
  supplyStatusOptions: ApiOptionItem[];
  supplyTypeOptions: ApiOptionItem[];
}

export interface ReturnInboundPageQuery {
  locationId?: number | string;
  keyword?: string;
}

export interface ReturnInboundPageRecords {
  records: Record<string, any>[];
  total?: number;
}

export interface ReturnInboundDetailQuery {
  busNo?: string;
  pageNo?: number;
  pageSize?: number;
}

export interface ReturnInboundMedicationQuery {
  locationId?: number | string;
  pageNo?: number;
  pageSize?: number;
  keyword?: string;
  supplierId?: number | string;
  typeEnum?: number;
}

export async function initReturnInboundApi() {
  return useReturnInboundMock()
    ? mockInitReturnInbound()
    : publicRequestClient.get<ReturnInboundInitResponse>('/pharmacy-warehouse/purchase-order/init');
}

export async function pageReturnInboundListApi(params?: ReturnInboundPageQuery) {
  return useReturnInboundMock()
    ? mockPageReturnInboundList(params)
    : publicRequestClient.get<ReturnInboundPageRecords>(
        '/pharmacy-warehouse/purchase-order/purchase-order-page',
        { params },
      );
}

export async function pageReturnInboundDetailApi(params?: ReturnInboundDetailQuery) {
  return useReturnInboundMock()
    ? mockPageReturnInboundDetail(params)
    : publicRequestClient.get<ReturnInboundPageRecords>(
        '/pharmacy-warehouse/purchase-order/purchase-order-detail-page',
        { params },
      );
}

export async function pageReturnInboundMedicationApi(params?: ReturnInboundMedicationQuery) {
  return useReturnInboundMock()
    ? mockPageReturnInboundMedication(params)
    : publicRequestClient.get<ReturnInboundPageRecords>(
        '/pharmacy-warehouse/purchase-order/medication-info',
        { params },
      );
}

export async function initReturnInboundBusNoApi() {
  return useReturnInboundMock()
    ? mockInitReturnInboundBusNo()
    : publicRequestClient.get<{ busNo: string }>('/pharmacy-warehouse/purchase-order/busNo-init');
}

export async function saveReturnInboundApi(data: Record<string, any>[]) {
  return useReturnInboundMock()
    ? mockSaveReturnInbound(data)
    : publicRequestClient.put<unknown>(
        '/pharmacy-warehouse/purchase-order/addOrEdit-purchaseOrder',
        data,
      );
}

export async function deleteReturnInboundApi(busNo: string) {
  return useReturnInboundMock()
    ? mockDeleteReturnInbound(busNo)
    : publicRequestClient.delete<unknown>(
        `/pharmacy-warehouse/purchase-order/delete-purchaseOrder`,
        {
          params: { busNo },
        },
      );
}

export async function agreeReturnInboundApprovalApi(busNo: string) {
  return useReturnInboundMock()
    ? mockAgreeReturnInboundApproval(busNo)
    : publicRequestClient.put<unknown>(
        '/pharmacy-warehouse/purchase-order/agree-approval',
        {},
        { params: { busNo } },
      );
}
