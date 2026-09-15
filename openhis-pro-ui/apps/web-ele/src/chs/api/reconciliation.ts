/**
 * 对账清算 — 对接 {@code POST /api/app/insuranceReconciliation/<method>}
 */
import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';
import { chsLongRequestConfig } from '#/chs/constants/request-timeout';

const BASE = '/insuranceReconciliation';

export interface ReconciliationContractRequest {
  contractBusNo: string;
  contractId?: number | string;
}

export interface QueryClearOrg3206AParams extends ReconciliationContractRequest {
  pageNum?: number;
  pageSize?: number;
}

export interface ClearOrg3206AResult {
  pages?: number;
  pageNum?: number;
  pageSize?: number;
  clrOptins?: Array<Record<string, unknown>>;
}

export interface SummarizeClearAmountParams extends ReconciliationContractRequest {
  begndate: string;
  enddate: string;
  /** 可选：仅返回该险种分组 */
  insutype?: string;
  /** 可选：仅返回该清算类别分组（11 门诊 / 21 住院） */
  clrType?: string;
}

export interface ClearAmountSummary {
  /** 险种代码 */
  insutype?: string;
  /** 清算类别（11 门诊 / 21 住院） */
  clrType?: string;
  medfeeSumamt?: number | string;
  medSumfee?: number | string;
  fundAppySum?: number | string;
  cashPayamt?: number | string;
  acctPay?: number | string;
  setlCount?: number;
}

export interface ApplyJilinClear3203AParams extends ReconciliationContractRequest {
  clrType: string;
  medfeeSumamt?: number | string;
  medSumfee?: number | string;
  fundAppySum?: number | string;
  cashPayamt?: number | string;
  acctPay?: number | string;
  begndate: string;
  enddate: string;
  clrOptins: string;
}

export interface ApplyJilinClear3203AResult {
  clrAppyEvtId?: string;
  localRecordId?: number | string;
}

export interface QueryClearStatus3205AParams extends ReconciliationContractRequest {
  clrOptins: string;
  /** 平台按状态查询；不传则由调用方按 10/20/50 分次拉取 */
  clrStas?: string;
  clrYm?: string;
}

export interface ClearStatus3205ARow {
  fixmedinsName?: string;
  fixmedinsCode?: string;
  clrStas?: string;
  optTime?: string;
  medfeeSumamt?: number | string;
  hiAgreSumfee?: number | string;
  fundAppySum?: number | string;
  cashPayamt?: number | string;
  acctPay?: number | string;
  begndate?: string;
  enddate?: string;
  clrYm?: string;
  clrOptins?: string;
  opterId?: string;
  opterName?: string;
  optinsNo?: string;
  clrAppyEvtId?: string;
}

export interface RevokeJilinClear3204AParams extends ReconciliationContractRequest {
  clrAppyEvtId: string;
  clrOptins: string;
}

export interface ReconDaily3201Params extends ReconciliationContractRequest {
  insutype: string;
  clrType: string;
  setlOptins: string;
  stmtBegndate: string;
  stmtEnddate: string;
  medfeeSumamt?: number | string;
  fundPaySumamt?: number | string;
  acctPay?: number | string;
  fixmedinsSetlCnt?: number;
}

export interface ReconDaily3201Result {
  setlOptins?: string;
  stmtRslt?: string;
  stmtRsltDscr?: string;
  localRecordId?: number | string;
}

export interface ListLocalReconRecordsParams {
  contractBusNo: string;
  monthFilter?: string;
}

export interface LocalClearApplyRecord {
  id?: number | string;
  contractBusNo?: string;
  clrType?: string;
  clrOptins?: string;
  begndate?: string;
  enddate?: string;
  medfeeSumamt?: number | string;
  fundAppySum?: number | string;
  acctPay?: number | string;
  cashPayamt?: number | string;
  clrAppyEvtId?: string;
  clrStas?: string;
  status?: string;
  createdAt?: string;
}

export interface LocalReconDailyRecord {
  id?: number | string;
  contractBusNo?: string;
  insutype?: string;
  clrType?: string;
  setlOptins?: string;
  stmtBegndate?: string;
  stmtEnddate?: string;
  medfeeSumamt?: number | string;
  fundPaySumamt?: number | string;
  acctPay?: number | string;
  fixmedinsSetlCnt?: number;
  stmtRslt?: string;
  stmtRsltDscr?: string;
  createdAt?: string;
}

export async function queryClearOrganizationsApi(
  params: QueryClearOrg3206AParams,
): Promise<ClearOrg3206AResult> {
  const res = await requestClient.post<ApiResponse<ClearOrg3206AResult>>(
    `${BASE}/queryClearOrganizations`,
    params,
    chsLongRequestConfig(),
  );
  return res.data ?? {};
}

export async function summarizeClearAmountsApi(
  params: SummarizeClearAmountParams,
): Promise<ClearAmountSummary[]> {
  const res = await requestClient.post<ApiResponse<ClearAmountSummary[]>>(
    `${BASE}/summarizeClearAmounts`,
    params,
    chsLongRequestConfig(),
  );
  return res.data ?? [];
}

/** 将分组汇总折叠为一条区间合计（月清算表单预填） */
export function mergeClearAmountSummaries(rows: ClearAmountSummary[]): ClearAmountSummary {
  let medfeeSumamt = 0;
  let medSumfee = 0;
  let fundAppySum = 0;
  let cashPayamt = 0;
  let acctPay = 0;
  let setlCount = 0;
  for (const row of rows) {
    medfeeSumamt += Number(row.medfeeSumamt ?? 0);
    medSumfee += Number(row.medSumfee ?? row.medfeeSumamt ?? 0);
    fundAppySum += Number(row.fundAppySum ?? 0);
    cashPayamt += Number(row.cashPayamt ?? 0);
    acctPay += Number(row.acctPay ?? 0);
    setlCount += Number(row.setlCount ?? 0);
  }
  return { medfeeSumamt, medSumfee, fundAppySum, cashPayamt, acctPay, setlCount };
}

export async function applyJilinClearApi(
  params: ApplyJilinClear3203AParams,
): Promise<ApplyJilinClear3203AResult> {
  const res = await requestClient.post<ApiResponse<ApplyJilinClear3203AResult>>(
    `${BASE}/applyJilinClear`,
    params,
    chsLongRequestConfig(),
  );
  return res.data ?? {};
}

export async function queryClearApplyStatusApi(
  params: QueryClearStatus3205AParams,
): Promise<ClearStatus3205ARow[]> {
  const res = await requestClient.post<ApiResponse<ClearStatus3205ARow[]>>(
    `${BASE}/queryClearApplyStatus`,
    params,
    chsLongRequestConfig(),
  );
  return res.data ?? [];
}

export async function revokeJilinClearApi(params: RevokeJilinClear3204AParams): Promise<void> {
  await requestClient.post(`${BASE}/revokeJilinClear`, params, chsLongRequestConfig());
}

export async function reconcileDailyLedgerApi(
  params: ReconDaily3201Params,
): Promise<ReconDaily3201Result> {
  const res = await requestClient.post<ApiResponse<ReconDaily3201Result>>(
    `${BASE}/reconcileDailyLedger`,
    params,
    chsLongRequestConfig(),
  );
  return res.data ?? {};
}

export async function listLocalClearAppliesApi(
  params: ListLocalReconRecordsParams,
): Promise<LocalClearApplyRecord[]> {
  const res = await requestClient.post<ApiResponse<LocalClearApplyRecord[]>>(
    `${BASE}/listLocalClearApplies`,
    params,
  );
  return res.data ?? [];
}

export async function listLocalReconDailyRecordsApi(
  params: ListLocalReconRecordsParams,
): Promise<LocalReconDailyRecord[]> {
  const res = await requestClient.post<ApiResponse<LocalReconDailyRecord[]>>(
    `${BASE}/listLocalReconDailyRecords`,
    params,
  );
  return res.data ?? [];
}

export interface ReconDetailRow {
  setlId?: string;
  mdtrtId?: string;
  psnNo?: string;
  medfeeSumamt?: number | string;
  fundPaySumamt?: number | string;
  acctPay?: number | string;
  refdSetlFlag?: string;
  source?: string;
}

export interface ReconDetail3202Params extends ReconciliationContractRequest {
  setlOptins: string;
  stmtBegndate: string;
  stmtEnddate: string;
  /** 与 3201 行一致时仅上传该险种明细 */
  insutype?: string;
  clrType: string;
  refdSetlFlag?: string;
  medfeeSumamt?: number | string;
  fundPaySumamt?: number | string;
  cashPayamt?: number | string;
  fixmedinsSetlCnt?: number;
}

export interface ReconDetailResultRow {
  psnNo?: string;
  mdtrtId?: string;
  setlId?: string;
  msgid?: string;
  stmtRslt?: string;
  refdSetlFlag?: string;
  memo?: string;
  medfeeSumamt?: number | string;
  fundPaySumamt?: number | string;
  acctPay?: number | string;
  /** 本地补齐：人员姓名 */
  psnName?: string;
  /** 本地补齐：结算时间 */
  setlTime?: string;
  /** 本地补齐：CONFIRMED / REVOKED */
  localStatus?: string;
  /** 本地是否命中结算回执 */
  localExists?: boolean;
  localMedfeeSumamt?: number | string;
  localFundPaySumamt?: number | string;
  localAcctPay?: number | string;
}

export interface ReconDetail3202Result {
  localRecordId?: number | string;
  uploadFileQuryNo?: string;
  uploadFilename?: string;
  resultFileQuryNo?: string;
  resultFilename?: string;
  dldEndtime?: string;
  rowCount?: number;
  mismatchCount?: number;
  resultRows?: ReconDetailResultRow[];
}

export interface LocalReconDetailRecord {
  id?: number | string;
  contractBusNo?: string;
  setlOptins?: string;
  clrType?: string;
  stmtBegndate?: string;
  stmtEnddate?: string;
  rowCount?: number;
  mismatchCount?: number;
  uploadFileQuryNo?: string;
  resultFileQuryNo?: string;
  createdAt?: string;
}

export async function previewReconDetailRowsApi(
  params: SummarizeClearAmountParams,
): Promise<ReconDetailRow[]> {
  const res = await requestClient.post<ApiResponse<ReconDetailRow[]>>(
    `${BASE}/previewReconDetailRows`,
    params,
    chsLongRequestConfig(),
  );
  return res.data ?? [];
}

export async function reconcileDetailLedgerApi(
  params: ReconDetail3202Params,
): Promise<ReconDetail3202Result> {
  const res = await requestClient.post<ApiResponse<ReconDetail3202Result>>(
    `${BASE}/reconcileDetailLedger`,
    params,
    chsLongRequestConfig(),
  );
  return res.data ?? {};
}

export async function listLocalReconDetailRecordsApi(
  params: ListLocalReconRecordsParams,
): Promise<LocalReconDetailRecord[]> {
  const res = await requestClient.post<ApiResponse<LocalReconDetailRecord[]>>(
    `${BASE}/listLocalReconDetailRecords`,
    params,
  );
  return res.data ?? [];
}

export interface QueryCrossProvinceRecon3209AParams extends ReconciliationContractRequest {
  clrType: string;
  begndate: string;
  enddate: string;
  clrOptins: string;
}

export interface CrossProvinceReconRow {
  setlId?: string;
  insuAdmdvs?: string;
}

export async function queryCrossProvinceReconFailuresApi(
  params: QueryCrossProvinceRecon3209AParams,
): Promise<CrossProvinceReconRow[]> {
  const res = await requestClient.post<ApiResponse<CrossProvinceReconRow[]>>(
    `${BASE}/queryCrossProvinceReconFailures`,
    params,
    chsLongRequestConfig(),
  );
  return res.data ?? [];
}

/** 工伤对账 invoke 首步（prepareInvoke；phase=RECON|RECON_DETAIL|RECON_CONFIRM）。 */
export interface StartWorkInjuryReconParams {
  contractId: string | number;
  /** 1321 明细对账时可传就诊 id，便于后端加载上下文 */
  encounterId?: string | number;
  operatorId?: string | number;
  formContext?: Record<string, unknown>;
  /** RECON=1320；RECON_DETAIL=1321；RECON_CONFIRM=1322 */
  phase?: 'RECON' | 'RECON_CONFIRM' | 'RECON_DETAIL';
}

export interface WorkInjuryReconPrepareStep {
  sessionId?: string;
  stepToken?: string;
  infno?: string;
  fixmedinsCode?: string;
  operatorNo?: string;
  input?: Record<string, unknown>;
  nextAction?: string;
  errMsg?: string;
}

export async function startWorkInjuryReconApi(
  params: StartWorkInjuryReconParams,
): Promise<WorkInjuryReconPrepareStep> {
  const phase = params.phase ?? 'RECON';
  const res = await requestClient.post<ApiResponse<WorkInjuryReconPrepareStep>>(
    '/workInjuryInvoke/prepareInvoke',
    {
      phase,
      contractId: params.contractId,
      encounterId: params.encounterId,
      operatorId: params.operatorId,
      context: params.formContext,
    },
  );
  return res.data ?? {};
}

export interface WorkInjuryReconSettlementRow {
  settlementRecordId?: number | string;
  encounterId?: number | string;
  iptOtpNo?: string;
  mdtrtId?: string;
  setlId?: string;
  psnName?: string;
  setlTime?: string;
  medfeeSumamt?: number | string;
  fundPaySumamt?: number | string;
  psnCashPay?: number | string;
  msgid2207?: string;
  status?: string;
  posted2207?: boolean;
  /** 关联 fin_payment_reconciliation.id */
  paymentId?: number | string;
  paymentStatus?: string;
  /** 收银明细汇总：charge_amount 合计 */
  paymentMedfee?: number | string;
  /** 收银明细：INSURANCE_FUND */
  paymentHifp?: number | string;
  /** 收银明细：CASH */
  paymentCash?: number | string;
  /** 可选 1321 平台回执（仅参考） */
  platformMedfee?: number;
  platformHifp?: number;
  platformCash?: number;
  detailStatus?: 'pending' | 'balanced' | 'unbalanced' | 'error';
  detailErrMsg?: string;
}

/** 工伤对账按日汇总（settlement ↔ payment + 持久化覆盖层） */
export interface WorkInjuryReconDaySummaryRow {
  day?: string;
  medfeeSumamt?: number | string;
  fundPaySumamt?: number | string;
  psnCashPay?: number | string;
  paymentMedfee?: number | string;
  paymentHifp?: number | string;
  paymentCash?: number | string;
  setlCount?: number;
  balancedCount?: number;
  unbalancedCount?: number;
  /** pending / balanced / unbalanced */
  localStatus?: string;
  confirmed?: boolean;
  platformMedfee?: number | string;
  platformHifp?: number | string;
  platformCash?: number | string;
  reconScope?: string;
}

export async function listWorkInjuryReconSettlementsApi(params: {
  contractId?: string | number;
  contractBusNo?: string;
  day: string;
}): Promise<WorkInjuryReconSettlementRow[]> {
  const res = await requestClient.post<ApiResponse<WorkInjuryReconSettlementRow[]>>(
    '/workInjuryInvoke/listReconSettlementsByDay',
    {
      contractId: params.contractId,
      contractBusNo: params.contractBusNo,
      day: params.day,
    },
    chsLongRequestConfig(),
  );
  return res.data ?? [];
}

export async function summarizeWorkInjuryReconByDayRangeApi(params: {
  contractId?: string | number;
  contractBusNo?: string;
  begndate: string;
  enddate: string;
}): Promise<WorkInjuryReconDaySummaryRow[]> {
  const res = await requestClient.post<ApiResponse<WorkInjuryReconDaySummaryRow[]>>(
    '/workInjuryInvoke/summarizeReconByDayRange',
    {
      contractId: params.contractId,
      contractBusNo: params.contractBusNo,
      begndate: params.begndate,
      enddate: params.enddate,
    },
    chsLongRequestConfig(),
  );
  return res.data ?? [];
}

/** 工伤对账：确认某日账平 */
export async function confirmWorkInjuryReconDailyBalancedApi(params: {
  contractId?: string | number;
  contractBusNo?: string;
  day: string;
}): Promise<void> {
  await requestClient.post('/workInjuryInvoke/confirmReconDailyBalanced', {
    contractId: params.contractId,
    contractBusNo: params.contractBusNo,
    day: params.day,
  });
}

/** 工伤对账：保存某日平台 1320 金额 */
export async function saveWorkInjuryReconDailyPlatformApi(params: {
  contractId?: string | number;
  contractBusNo?: string;
  day: string;
  platformMedfee?: number;
  platformHifp?: number;
  platformCash?: number;
  reconScope?: string;
}): Promise<void> {
  await requestClient.post('/workInjuryInvoke/saveReconDailyPlatform', {
    contractId: params.contractId,
    contractBusNo: params.contractBusNo,
    day: params.day,
    platformMedfee: params.platformMedfee,
    platformHifp: params.platformHifp,
    platformCash: params.platformCash,
    reconScope: params.reconScope,
  });
}
