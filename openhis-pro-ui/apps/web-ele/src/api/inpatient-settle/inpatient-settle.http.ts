import type {
  ReverseInpatientSettleRequest,
  ReverseInpatientSettleResponse,
  RecallInpatientDischargeRequest,
  RecallInpatientDischargeResponse,
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

import { chsLongRequestConfig } from '#/chs/constants/request-timeout';
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

/** 住院预结算：生成支付草稿与应收合计（医保链路可能含 2405/重新入院，须长超时） */
export async function httpPreSettleInpatientCharges(
  body: PreSettleInpatientChargesRequest,
): Promise<PreSettleInpatientChargesResponse> {
  return publicRequestClient.post<PreSettleInpatientChargesResponse>(
    `${BASE}/preSettleInpatientCharges`,
    body,
    chsLongRequestConfig(),
  );
}

/** 确认住院结算（正式结算；医保可能 5201/2404/2405/重新入院/2304，须长超时） */
export async function httpConfirmInpatientSettle(
  body: ConfirmInpatientSettleRequest,
): Promise<ConfirmInpatientSettleResponse> {
  return publicRequestClient.post<ConfirmInpatientSettleResponse>(
    `${BASE}/confirmInpatientSettle`,
    body,
    chsLongRequestConfig(),
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

/** 住院反结（IF-IP-RVS-01；未离院；含医保撤销时可能较长） */
export async function httpReverseInpatientSettle(
  body: ReverseInpatientSettleRequest,
): Promise<ReverseInpatientSettleResponse> {
  return publicRequestClient.post<ReverseInpatientSettleResponse>(
    `${BASE}/reverseInpatientSettle`,
    body,
    chsLongRequestConfig(),
  );
}

/** 住院出院召回（IF-IP-RCL-01；已离院，内嵌反结） */
export async function httpRecallInpatientDischarge(
  body: RecallInpatientDischargeRequest,
): Promise<RecallInpatientDischargeResponse> {
  return publicRequestClient.post<RecallInpatientDischargeResponse>(
    `${BASE}/recallInpatientDischarge`,
    body,
    chsLongRequestConfig(),
  );
}
