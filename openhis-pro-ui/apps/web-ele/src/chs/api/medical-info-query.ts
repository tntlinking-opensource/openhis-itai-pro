/**
 * 医保信息查询 5201–5206 — 对接 {@code POST /api/app/insuranceMedicalInfoQuery/<method>}
 */
import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';
import { chsLongRequestConfig } from '#/chs/constants/request-timeout';

const BASE = '/insuranceMedicalInfoQuery';

export interface MedicalInfoContractRequest {
  contractBusNo: string;
  contractId?: number | string;
  psnNo: string;
}

export interface QueryMdtrt5201Params extends MedicalInfoContractRequest {
  begntime: string;
  endtime: string;
  medType?: string;
  mdtrtId?: string;
}

export interface QueryDise5202Params extends MedicalInfoContractRequest {
  mdtrtId: string;
}

export interface QuerySetl5203Params extends MedicalInfoContractRequest {
  mdtrtId: string;
  setlId: string;
}

export interface QueryFee5204Params extends MedicalInfoContractRequest {
  mdtrtId: string;
  setlId?: string;
}

export interface QueryOpspDrug5205Params extends MedicalInfoContractRequest {
  begntime: string;
  endtime?: string;
}

export interface QueryCum5206Params extends MedicalInfoContractRequest {
  cumYm?: string;
}

export interface MedicalInfoQueryResult {
  infno?: string;
  rows?: Array<Record<string, unknown>>;
}

export interface Setl5203Result {
  infno?: string;
  header?: Record<string, unknown>;
  setlDetails?: Array<Record<string, unknown>>;
}

async function postMedicalInfo<T>(method: string, body: unknown): Promise<T> {
  const res = await requestClient.post<ApiResponse<T>>(
    `${BASE}/${method}`,
    body,
    chsLongRequestConfig(),
  );
  return res.data as T;
}

export function queryMdtrt5201Api(params: QueryMdtrt5201Params) {
  return postMedicalInfo<MedicalInfoQueryResult>('queryMdtrt5201', params);
}

export function queryDise5202Api(params: QueryDise5202Params) {
  return postMedicalInfo<MedicalInfoQueryResult>('queryDise5202', params);
}

export function querySetl5203Api(params: QuerySetl5203Params) {
  return postMedicalInfo<Setl5203Result>('querySetl5203', params);
}

export function queryFee5204Api(params: QueryFee5204Params) {
  return postMedicalInfo<MedicalInfoQueryResult>('queryFee5204', params);
}

export function queryOpspDrug5205Api(params: QueryOpspDrug5205Params) {
  return postMedicalInfo<MedicalInfoQueryResult>('queryOpspDrug5205', params);
}

export function queryCum5206Api(params: QueryCum5206Params) {
  return postMedicalInfo<MedicalInfoQueryResult>('queryCum5206', params);
}
