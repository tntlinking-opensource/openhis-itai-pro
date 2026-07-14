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
}

export interface ClearAmountSummary {
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
  clrStas: string;
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
): Promise<ClearAmountSummary> {
  const res = await requestClient.post<ApiResponse<ClearAmountSummary>>(
    `${BASE}/summarizeClearAmounts`,
    params,
    chsLongRequestConfig(),
  );
  return res.data ?? {};
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

/** 工伤对账 invoke 首步（1320；后续 prepare/submit 走 health workInjuryInvoke）。 */
export interface StartWorkInjuryReconParams {
  contractId: string | number;
  operatorId?: string | number;
  formContext?: Record<string, unknown>;
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
  const res = await requestClient.post<ApiResponse<WorkInjuryReconPrepareStep>>(
    `${BASE}/startWorkInjuryRecon`,
    params,
  );
  return res.data ?? {};
}
