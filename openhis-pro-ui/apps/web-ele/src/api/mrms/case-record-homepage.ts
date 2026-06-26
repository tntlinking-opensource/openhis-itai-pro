/**
 * 病案首页 API（MrmsCaseRecordHomepageAppService）
 */

import type {
  CaseRecordDetailDto,
  HomepageDto,
  HomepagePageDto,
  RefreshAutoFillParams,
  ReturnHomepageParams,
  SaveHomepageParams,
} from './types';

import { publicRequestClient } from '#/api/request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

const BASE = '/mrmsCaseRecordHomepage';

/** 表单扩展字段写入 extension，与 SaveHomepageRequest 标量字段分离 */
const EXTENSION_KEYS: (keyof SaveHomepageParams)[] = [
  'admCatyName',
  'dscgCatyName',
  'transferDeptSummary',
  'deptDirectorName',
  'respNursName',
  'ntlyCode',
  'natyCode',
  'prfsCode',
  'mrgStasCode',
  'currAddr',
  'psnTel',
  'conerName',
  'conerTel',
  'conerAddr',
];

function toSaveHomepageBody(params: SaveHomepageParams) {
  const extension: Record<string, unknown> = { ...(params.extension ?? {}) };
  for (const key of EXTENSION_KEYS) {
    const value = params[key];
    if (value !== undefined && value !== null && value !== '') {
      extension[key] = value;
    }
  }
  return {
    caseRecordId: preserveApiIdForRequest(params.caseRecordId),
    psnName: params.psnName,
    gendCode: params.gendCode,
    brdy: params.brdy,
    certno: params.certno,
    iptNo: params.iptNo,
    admWayCode: params.admWayCode,
    admCatyCode: params.admCatyCode,
    admTime: params.admTime,
    dscgCatyCode: params.dscgCatyCode,
    dscgTime: params.dscgTime,
    iptDays: params.iptDays,
    dscgWayCode: params.dscgWayCode,
    chfdrName: params.chfdrName,
    atddrName: params.atddrName,
    iptDrName: params.iptDrName,
    extension: Object.keys(extension).length > 0 ? extension : undefined,
    diagnoses: params.diagnoses,
    procedures: params.procedures,
  };
}

export async function loadHomepagePageApi(caseRecordId: string) {
  return publicRequestClient.post<HomepagePageDto>(`${BASE}/loadPage`, {
    caseRecordId: preserveApiIdForRequest(caseRecordId),
  });
}

/** @deprecated 使用 loadHomepagePageApi */
export async function getHomepageApi(caseRecordId: string) {
  const page = await loadHomepagePageApi(caseRecordId);
  return page.homepage!;
}

export async function saveHomepageApi(params: SaveHomepageParams) {
  return publicRequestClient.post<HomepageDto>(`${BASE}/saveHomepage`, toSaveHomepageBody(params));
}

export async function refreshAutoFillApi(params: RefreshAutoFillParams) {
  return publicRequestClient.post<HomepageDto>(`${BASE}/refreshAutoFill`, {
    caseRecordId: preserveApiIdForRequest(params.caseRecordId),
    sections: params.sections,
    forceOverwrite: params.forceOverwrite ?? false,
  });
}

export async function submitHomepageApi(caseRecordId: string) {
  return publicRequestClient.post<CaseRecordDetailDto>(`${BASE}/submit`, {
    caseRecordId: preserveApiIdForRequest(caseRecordId),
  });
}

export async function receiveHomepageApi(caseRecordId: string) {
  return publicRequestClient.post<CaseRecordDetailDto>(`${BASE}/receive`, {
    caseRecordId: preserveApiIdForRequest(caseRecordId),
  });
}

export async function returnHomepageApi(params: ReturnHomepageParams) {
  return publicRequestClient.post<CaseRecordDetailDto>(`${BASE}/returnToDoctor`, {
    caseRecordId: preserveApiIdForRequest(params.caseRecordId),
    returnReason: params.returnReason,
  });
}

/** @deprecated 使用 receiveHomepageApi */
export async function receiveCaseRecordApi(params: { caseRecordId: string }) {
  return receiveHomepageApi(params.caseRecordId);
}

/** @deprecated 使用 returnHomepageApi */
export async function returnCaseRecordApi(params: { caseRecordId: string; reason?: string }) {
  return returnHomepageApi({
    caseRecordId: params.caseRecordId,
    returnReason: params.reason ?? '',
  });
}
