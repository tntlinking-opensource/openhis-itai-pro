import type {
  ReverseInpatientSettleRequest,
  ReverseInpatientSettleResponse,
  ConfirmInpatientSettleRequest,
  ConfirmInpatientSettleResponse,
  LoadInpatientSettlePageInitResponse,
  LoadInpatientSettleSummaryRequest,
  LoadInpatientSettleSummaryResponse,
  LoadSettledInpatientPaymentViewRequest,
  LoadSettledInpatientPaymentViewResponse,
  PageInpatientSettleQueueRequest,
  PageInpatientSettleQueueResponse,
  PreSettleInpatientChargesRequest,
  PreSettleInpatientChargesResponse,
  QueryInpatientSettleChargesRequest,
  QueryInpatientSettleChargesResponse,
} from './inpatient-settle.types';

import { publicRequestClient } from '#/api/request';

const BASE = '/inpatientSettle';

/** 加载结算页初始化选项（队列页签、费用呈现、支付渠道） */
export async function httpLoadInpatientSettlePageInit(): Promise<LoadInpatientSettlePageInitResponse> {
  return publicRequestClient.post<LoadInpatientSettlePageInitResponse>(
    `${BASE}/loadInpatientSettlePageInit`,
    {},
  );
}

/** 分页查询结算患者队列 */
export async function httpPageInpatientSettleQueue(
  body: PageInpatientSettleQueueRequest,
): Promise<PageInpatientSettleQueueResponse> {
  return publicRequestClient.post<PageInpatientSettleQueueResponse>(
    `${BASE}/pageInpatientSettleQueue`,
    body,
  );
}

/** 加载住院结算摘要（IF-IP-STL-03；须 encounterId + patientId） */
export async function httpLoadInpatientSettleSummary(
  body: LoadInpatientSettleSummaryRequest,
): Promise<LoadInpatientSettleSummaryResponse> {
  return publicRequestClient.post<LoadInpatientSettleSummaryResponse>(
    `${BASE}/loadInpatientSettleSummary`,
    body,
  );
}

/** 查询结算专用费用（明细或大类汇总） */
export async function httpQueryInpatientSettleCharges(
  body: QueryInpatientSettleChargesRequest,
): Promise<QueryInpatientSettleChargesResponse> {
  return publicRequestClient.post<QueryInpatientSettleChargesResponse>(
    `${BASE}/queryInpatientSettleCharges`,
    body,
  );
}

/** 住院预结算：生成支付草稿与应收合计 */
export async function httpPreSettleInpatientCharges(
  body: PreSettleInpatientChargesRequest,
): Promise<PreSettleInpatientChargesResponse> {
  return publicRequestClient.post<PreSettleInpatientChargesResponse>(
    `${BASE}/preSettleInpatientCharges`,
    body,
  );
}

/** 确认住院结算（正式结算） */
export async function httpConfirmInpatientSettle(
  body: ConfirmInpatientSettleRequest,
): Promise<ConfirmInpatientSettleResponse> {
  return publicRequestClient.post<ConfirmInpatientSettleResponse>(
    `${BASE}/confirmInpatientSettle`,
    body,
  );
}

/** 查阅已结算支付单及费用快照 */
export async function httpLoadSettledInpatientPaymentView(
  body: LoadSettledInpatientPaymentViewRequest,
): Promise<LoadSettledInpatientPaymentViewResponse> {
  return publicRequestClient.post<LoadSettledInpatientPaymentViewResponse>(
    `${BASE}/loadSettledInpatientPaymentView`,
    body,
  );
}

/** 住院反结（IF-IP-RVS-01） */
export async function httpReverseInpatientSettle(
  body: ReverseInpatientSettleRequest,
): Promise<ReverseInpatientSettleResponse> {
  return publicRequestClient.post<ReverseInpatientSettleResponse>(
    `${BASE}/reverseInpatientSettle`,
    body,
  );
}
