import type {
  GainLossDetailQuery,
  GainLossInitResponse,
  GainLossPageQuery,
  GainLossPageRecords,
  MedicationInfoQuery,
} from './gain-loss.types';
import type { PurchaseOrderDetailQuery, PurchaseOrderPageQuery } from './purchase-request';

import { publicRequestClient } from '#/api/request';

import {
  mockAgreeGainLossApproval,
  mockDeleteGainLoss,
  mockInitGainLoss,
  mockInitGainLossBusNo,
  mockPageGainLossDetail,
  mockPageGainLossList,
  mockPageGainLossMedication,
  mockSaveGainLoss,
} from './gain-loss.mock';
import { mockPagePurchaseOrderDetail, mockPagePurchaseOrderList } from './purchase-request.mock';

function useGainLossMock(): boolean {
  return import.meta.env.VITE_USE_MOCK_GAIN_LOSS === 'true';
}

/** 损益单选项初始化 */
export async function initGainLossApi() {
  return useGainLossMock()
    ? mockInitGainLoss()
    : publicRequestClient.get<GainLossInitResponse>('/pharmacy-warehouse/profitLoss-order/init');
}

/** 损益单单据分页列表（左侧） */
export async function pageGainLossListApi(params?: GainLossPageQuery) {
  return useGainLossMock()
    ? mockPageGainLossList(params)
    : publicRequestClient.get<GainLossPageRecords>(
        '/pharmacy-warehouse/profitLoss-order/profitLoss-order-page',
        { params },
      );
}

/** 药品信息（明细选药） */
export async function pageGainLossMedicationApi(params?: MedicationInfoQuery) {
  return useGainLossMock()
    ? mockPageGainLossMedication(params)
    : publicRequestClient.get<GainLossPageRecords>(
        '/pharmacy-warehouse/profitLoss-order/medication-info',
        { params },
      );
}

/** 获取新单据号 */
export async function initGainLossBusNoApi() {
  return useGainLossMock()
    ? mockInitGainLossBusNo()
    : publicRequestClient.get<{ busNo: string }>('/pharmacy-warehouse/profitLoss-order/busNo-init');
}

/** 新增 / 编辑损益单 */
export async function saveGainLossApi(data: Record<string, unknown>[]) {
  return useGainLossMock()
    ? mockSaveGainLoss(data)
    : publicRequestClient.put<unknown>(
        '/pharmacy-warehouse/profitLoss-order/addOrEdit-profitLossOrder',
        data,
      );
}

/** 删除损益单 */
export async function deleteGainLossApi(busNo: string) {
  return useGainLossMock()
    ? mockDeleteGainLoss(busNo)
    : publicRequestClient.delete<unknown>(
        '/pharmacy-warehouse/profitLoss-order/delete-profitLossOrder',
        { params: { busNo } },
      );
}

/** 同意审批 */
export async function agreeGainLossApprovalApi(busNo: string) {
  return useGainLossMock()
    ? mockAgreeGainLossApproval(busNo)
    : publicRequestClient.put<unknown>(
        '/pharmacy-warehouse/profitLoss-order/agree-approval',
        {},
        { params: { busNo } },
      );
}

/** 损益单明细分页 */
export async function pageGainLossDetailApi(params?: GainLossDetailQuery) {
  return useGainLossMock()
    ? mockPageGainLossDetail(params)
    : publicRequestClient.get<GainLossPageRecords>(
        '/pharmacy-warehouse/profitLoss-order/profitLoss-order-detail-page',
        { params },
      );
}

/** 已审核订货单分页（损益导入弹窗，路径与订货一致） */
export async function pagePurchaseOrderForGainLossApi(
  params?: PurchaseOrderPageQuery & {
    pageNo?: number;
    pageSize?: number;
    statusEnum?: number;
  },
) {
  return useGainLossMock()
    ? mockPagePurchaseOrderList(params)
    : publicRequestClient.get<GainLossPageRecords>(
        '/pharmacy-warehouse/purchase-order/purchase-order-page',
        { params },
      );
}

/** 订货单明细（弹窗回填） */
export async function pagePurchaseOrderDetailForGainLossApi(params?: PurchaseOrderDetailQuery) {
  return useGainLossMock()
    ? mockPagePurchaseOrderDetail(params)
    : publicRequestClient.get<GainLossPageRecords>(
        '/pharmacy-warehouse/purchase-order/purchase-order-detail-page',
        { params },
      );
}

export type {
  GainLossDetailQuery,
  GainLossInitResponse,
  GainLossPageQuery,
  GainLossPageRecords,
  MedicationInfoQuery,
} from './gain-loss.types';
