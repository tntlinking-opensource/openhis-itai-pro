import type {
  ConfirmDirectNurseStationPricingRequest,
  ConfirmDirectNurseStationPricingResponse,
  ConfirmSupplementNurseStationPricingRequest,
  ConfirmSupplementNurseStationPricingResponse,
  ListNurseStationPricedOrdersRequest,
  ListNurseStationPricedOrdersResponse,
  ListNurseStationSupplementParentOrdersRequest,
  ListNurseStationSupplementParentOrdersResponse,
  PageNurseStationPricingCatalogRequest,
  PageNurseStationPricingCatalogResponse,
} from './inpatient-nurse-station-inpatient-billing.types';

import { publicRequestClient } from '#/api/request';

/** 住院护士站 · 住院记账（划价） */
const BASE = '/nurseStationInpatientBilling';

/** 划价选品目录分页（诊疗 + 耗材，不含药品） */
export async function httpPageNurseStationPricingCatalog(
  body: PageNurseStationPricingCatalogRequest,
): Promise<PageNurseStationPricingCatalogResponse> {
  return publicRequestClient.post<PageNurseStationPricingCatalogResponse>(
    `${BASE}/pageNurseStationPricingCatalog`,
    body,
  );
}

/** 批量直接划价确认（单批最多 50 行，禁止父锚点） */
export async function httpConfirmDirectNurseStationPricing(
  body: ConfirmDirectNurseStationPricingRequest,
): Promise<ConfirmDirectNurseStationPricingResponse> {
  return publicRequestClient.post<ConfirmDirectNurseStationPricingResponse>(
    `${BASE}/confirmDirectNurseStationPricing`,
    body,
  );
}

/** 查询已划价列表（直接划价 + 医嘱补费统一展示） */
export async function httpListNurseStationPricedOrders(
  body: ListNurseStationPricedOrdersRequest,
): Promise<ListNurseStationPricedOrdersResponse> {
  return publicRequestClient.post<ListNurseStationPricedOrdersResponse>(
    `${BASE}/listNurseStationPricedOrders`,
    body,
  );
}

/** 医嘱补费父医嘱列表（药/诊，已校对或已停止） */
export async function httpListNurseStationSupplementParentOrders(
  body: ListNurseStationSupplementParentOrdersRequest,
): Promise<ListNurseStationSupplementParentOrdersResponse> {
  return publicRequestClient.post<ListNurseStationSupplementParentOrdersResponse>(
    `${BASE}/listNurseStationSupplementParentOrders`,
    body,
  );
}

/** 医嘱补费确认（每行须携带 parentRequestAnchor） */
export async function httpConfirmSupplementNurseStationPricing(
  body: ConfirmSupplementNurseStationPricingRequest,
): Promise<ConfirmSupplementNurseStationPricingResponse> {
  return publicRequestClient.post<ConfirmSupplementNurseStationPricingResponse>(
    `${BASE}/confirmSupplementNurseStationPricing`,
    body,
  );
}
