import type {
  AutoGenRuleMutationRequest,
  AutoGenRuleMutationResponse,
  CancelNurseStationPricingRequest,
  CancelNurseStationPricingResponse,
  ConfirmDirectNurseStationPricingRequest,
  ConfirmDirectNurseStationPricingResponse,
  ConfirmSupplementNurseStationPricingRequest,
  ConfirmSupplementNurseStationPricingResponse,
  CreateAutoGenRuleRequest,
  CreateAutoGenRuleResponse,
  ListAutoRollConfigsRequest,
  ListAutoRollConfigsResponse,
  ListNurseStationPricedOrdersRequest,
  ListNurseStationPricedOrdersResponse,
  ListNurseStationSupplementParentOrdersRequest,
  ListNurseStationSupplementParentOrdersResponse,
  PageNurseStationPricingCatalogRequest,
  PageNurseStationPricingCatalogResponse,
  UpdateAutoGenRuleRequest,
  UpdateAutoGenRuleResponse,
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

/** 取消划价（已划价列表多选整行还原，单批最多 50 行） */
export async function httpCancelNurseStationPricing(
  body: CancelNurseStationPricingRequest,
): Promise<CancelNurseStationPricingResponse> {
  return publicRequestClient.post<CancelNurseStationPricingResponse>(
    `${BASE}/cancelNurseStationPricing`,
    body,
  );
}

/** 自动计费配置列表（含护理级别只读区） */
export async function httpListAutoRollConfigs(
  body: ListAutoRollConfigsRequest,
): Promise<ListAutoRollConfigsResponse> {
  return publicRequestClient.post<ListAutoRollConfigsResponse>(`${BASE}/listAutoRollConfigs`, body);
}

/** 新增自动计费配置 */
export async function httpCreateAutoGenRule(
  body: CreateAutoGenRuleRequest,
): Promise<CreateAutoGenRuleResponse> {
  return publicRequestClient.post<CreateAutoGenRuleResponse>(`${BASE}/createAutoGenRule`, body);
}

/** 更新自动计费配置 */
export async function httpUpdateAutoGenRule(
  body: UpdateAutoGenRuleRequest,
): Promise<UpdateAutoGenRuleResponse> {
  return publicRequestClient.post<UpdateAutoGenRuleResponse>(`${BASE}/updateAutoGenRule`, body);
}

/** 删除自动计费配置（软删） */
export async function httpDeleteAutoGenRule(
  body: AutoGenRuleMutationRequest,
): Promise<AutoGenRuleMutationResponse> {
  return publicRequestClient.post<AutoGenRuleMutationResponse>(`${BASE}/deleteAutoGenRule`, body);
}

/** 启用自动计费配置 */
export async function httpActivateAutoGenRule(
  body: AutoGenRuleMutationRequest,
): Promise<AutoGenRuleMutationResponse> {
  return publicRequestClient.post<AutoGenRuleMutationResponse>(`${BASE}/activateAutoGenRule`, body);
}

/** 停用自动计费配置 */
export async function httpSuspendAutoGenRule(
  body: AutoGenRuleMutationRequest,
): Promise<AutoGenRuleMutationResponse> {
  return publicRequestClient.post<AutoGenRuleMutationResponse>(`${BASE}/suspendAutoGenRule`, body);
}
