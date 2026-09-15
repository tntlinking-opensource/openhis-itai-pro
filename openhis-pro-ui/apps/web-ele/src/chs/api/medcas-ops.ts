/**
 * 医保 · 住院业务上报 API（对接 MrmsCaseRecordUploadAppService）。
 * 日常一键上报在病案首页；本模块供运维补传/流水查询（对齐进销存运维）。
 */
import type {
  CaseRecordUploadSummaryDto,
  ListUploadLogsParams,
  OneClickReportResultDto,
  QueryUploadPageParams,
  RetryUploadParams,
  SettleListQuery4103Dto,
  Upload4401Params,
  UploadLogDto,
  UploadResultDto,
} from '#/api/mrms/types';

import { publicRequestClient } from '#/api/request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

const BASE = '/mrmsCaseRecordUpload';

export type {
  CaseRecordUploadSummaryDto,
  ListUploadLogsParams,
  OneClickReportResultDto,
  QueryUploadPageParams,
  RetryUploadParams,
  SettleListQuery4103Dto,
  Upload4401Params,
  UploadLogDto,
  UploadResultDto,
};

/** 病案上报通用入参（合同可省略，后端从医保账户解析） */
export interface MedcasReportParams {
  caseRecordId: string;
  contractBusNo?: string;
}

/** 4102 状态修改 */
export interface ModifySettleListStatusParams extends MedcasReportParams {
  /** 0 撤回审核 / 1 提交审核 */
  stasType: '0' | '1' | string;
}

/** 4103 平台查询：psn_no+setl_id 或病案主键/病案号 */
export interface QuerySettleList4103Params {
  contractBusNo?: string;
  caseRecordId?: string;
  medcasno?: string;
  psnNo?: string;
  setlId?: string;
}

/** 4401 上传/补传 */
export async function upload4401Api(params: Upload4401Params) {
  return publicRequestClient.post<UploadResultDto>(`${BASE}/upload`, {
    caseRecordId: preserveApiIdForRequest(params.caseRecordId),
    contractBusNo: params.contractBusNo,
  });
}

/** 4101A 结算清单上传 */
export async function uploadSettleListApi(params: MedcasReportParams) {
  return publicRequestClient.post<UploadResultDto>(`${BASE}/uploadSettleList`, {
    caseRecordId: preserveApiIdForRequest(params.caseRecordId),
    contractBusNo: params.contractBusNo,
  });
}

/** 4102 提交/撤回审核 */
export async function modifySettleListStatusApi(params: ModifySettleListStatusParams) {
  return publicRequestClient.post<UploadResultDto>(`${BASE}/modifySettleListStatus`, {
    caseRecordId: preserveApiIdForRequest(params.caseRecordId),
    contractBusNo: params.contractBusNo,
    stasType: params.stasType,
  });
}

/** 一键上报 4401→4101A→4102 */
export async function reportOneClickApi(params: MedcasReportParams) {
  return publicRequestClient.post<OneClickReportResultDto>(`${BASE}/reportOneClick`, {
    caseRecordId: preserveApiIdForRequest(params.caseRecordId),
    contractBusNo: params.contractBusNo,
  });
}

/** 【4103】平台结算清单查询 */
export async function querySettleList4103Api(params: QuerySettleList4103Params) {
  return publicRequestClient.post<SettleListQuery4103Dto>(`${BASE}/querySettleList`, {
    contractBusNo: params.contractBusNo,
    caseRecordId: params.caseRecordId ? preserveApiIdForRequest(params.caseRecordId) : undefined,
    medcasno: params.medcasno,
    psnNo: params.psnNo,
    setlId: params.setlId,
  });
}

/** 按上传日志重试 */
export async function retryUploadApi(params: RetryUploadParams) {
  return publicRequestClient.post<UploadResultDto>(`${BASE}/retry`, {
    uploadLogId: preserveApiIdForRequest(params.uploadLogId),
  });
}

/** 查询某病案上传流水 */
export async function queryUploadLogsApi(params: ListUploadLogsParams) {
  return publicRequestClient.post<UploadLogDto[]>(`${BASE}/queryUploadLogs`, {
    caseRecordId: preserveApiIdForRequest(params.caseRecordId),
  });
}

/** 上报台分页（待传/失败/成功；按交易号分流 4401 与结算清单） */
export async function queryUploadPageApi(params: QueryUploadPageParams) {
  const data = await publicRequestClient.post(`${BASE}/queryUploadPage`, {
    pageNo: params.pageNo ?? 1,
    pageSize: params.pageSize ?? 20,
    statusCode: params.statusCode,
    keyword: params.keyword,
    infno: params.infno,
    dscgTimeFrom: params.dscgTimeFrom,
    dscgTimeTo: params.dscgTimeTo,
  });
  return parsePagedRowsFromApiData<CaseRecordUploadSummaryDto>(data);
}

/** 4401 组装预检 */
export async function previewAssembled4401Api(params: Upload4401Params) {
  return publicRequestClient.post<Record<string, unknown>>(`${BASE}/previewAssembled`, {
    caseRecordId: preserveApiIdForRequest(params.caseRecordId),
    contractBusNo: params.contractBusNo,
  });
}

/** 4101A 组装预检 */
export async function previewSettleListApi(params: MedcasReportParams) {
  return publicRequestClient.post<Record<string, unknown>>(`${BASE}/previewSettleList`, {
    caseRecordId: preserveApiIdForRequest(params.caseRecordId),
    contractBusNo: params.contractBusNo,
  });
}
