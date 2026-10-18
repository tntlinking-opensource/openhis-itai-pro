/**
 * 病案首页 API（MrmsCaseRecordHomepageAppService：列表 / 编辑 / 查档）
 */

import type { PageApiResponse, PageParams } from '#/types/api-response';

import type {
  CaseRecordDetailDto,
  CaseRecordListItemDto,
  HomepageDto,
  HomepagePageDto,
  PdfGenerateResultDto,
  QueryCaseRecordParams,
  RefreshAutoFillParams,
  ReturnHomepageParams,
  SaveHomepageParams,
} from './types';

import { publicRequestClient } from '#/api/request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

const BASE = '/mrmsCaseRecordHomepage';

/** 组装保存请求体（正式列标量直传；费用不提交） */
function toSaveHomepageBody(params: SaveHomepageParams) {
  return {
    caseRecordId: params.caseRecordId ? preserveApiIdForRequest(params.caseRecordId) : undefined,
    encounterId: params.encounterId ? preserveApiIdForRequest(params.encounterId) : undefined,
    psnName: params.psnName,
    gendCode: params.gendCode,
    brdy: params.brdy,
    certno: params.certno,
    ntlyCode: params.ntlyCode,
    natyCode: params.natyCode,
    prfsCode: params.prfsCode,
    mrgStasCode: params.mrgStasCode,
    currAddr: params.currAddr,
    psnTel: params.psnTel,
    conerName: params.conerName,
    conerTel: params.conerTel,
    conerRltsCode: params.conerRltsCode,
    conerAddr: params.conerAddr,
    trtTypeCode: params.trtTypeCode,
    clinicalPathCode: params.clinicalPathCode,
    tcmPrepCode: params.tcmPrepCode,
    tcmDeviceCode: params.tcmDeviceCode,
    tcmTechCode: params.tcmTechCode,
    syndromeNurseCode: params.syndromeNurseCode,
    spgaNurscareDays: params.spgaNurscareDays,
    lv1NurscareDays: params.lv1NurscareDays,
    scdNurscareDays: params.scdNurscareDays,
    lv3NurscareDays: params.lv3NurscareDays,
    otpWmDise: params.otpWmDise,
    wmDiseCode: params.wmDiseCode,
    otpTcmDise: params.otpTcmDise,
    tcmDiseCode: params.tcmDiseCode,
    brnDamgBfadmComaDura: params.brnDamgBfadmComaDura,
    brnDamgAfadmComaDura: params.brnDamgAfadmComaDura,
    ventUsedDura: params.ventUsedDura,
    iptNo: params.iptNo,
    admWayCode: params.admWayCode,
    admCatyCode: params.admCatyCode,
    admCatyName: params.admCatyName,
    admTime: params.admTime,
    dscgCatyCode: params.dscgCatyCode,
    dscgCatyName: params.dscgCatyName,
    dscgTime: params.dscgTime,
    iptDays: params.iptDays,
    transferDeptSummary: params.transferDeptSummary,
    dscgWayCode: params.dscgWayCode,
    deptDirectorName: params.deptDirectorName,
    chfdrName: params.chfdrName,
    atddrName: params.atddrName,
    iptDrName: params.iptDrName,
    respNursName: params.respNursName,
    payWayCode: params.payWayCode,
    healthCardNo: params.healthCardNo,
    ageYear: params.ageYear,
    ageMonth: params.ageMonth,
    birplcCodes: params.birplcCodes,
    birplcName: params.birplcName,
    naplCodes: params.naplCodes,
    naplName: params.naplName,
    currAddrCodes: params.currAddrCodes,
    currAddrDetail: params.currAddrDetail,
    currAddrPoscode: params.currAddrPoscode,
    resdAddrCodes: params.resdAddrCodes,
    resdAddrDetail: params.resdAddrDetail,
    resdAddrPoscode: params.resdAddrPoscode,
    emprAddr: params.emprAddr,
    emprTel: params.emprTel,
    emprPoscode: params.emprPoscode,
    admWard: params.admWard,
    admBed: params.admBed,
    dscgWard: params.dscgWard,
    dscgBed: params.dscgBed,
    drugAllergyCode: params.drugAllergyCode,
    allergyDrugName: params.allergyDrugName,
    pathologyNo: params.pathologyNo,
    mainDiagCureCode: params.mainDiagCureCode,
    rescueCount: params.rescueCount,
    rescueSuccessCount: params.rescueSuccessCount,
    clinicPathManageCode: params.clinicPathManageCode,
    diagMatchOpDscg: params.diagMatchOpDscg,
    diagMatchAdmDscg: params.diagMatchAdmDscg,
    diagMatchPrePost: params.diagMatchPrePost,
    diagMatchClinicPath: params.diagMatchClinicPath,
    diagMatchRadioPath: params.diagMatchRadioPath,
    autopsyCode: params.autopsyCode,
    aboCode: params.aboCode,
    rhCode: params.rhCode,
    trainDrName: params.trainDrName,
    intnDrName: params.intnDrName,
    codrName: params.codrName,
    medcasQltCode: params.medcasQltCode,
    qltctrlDate: params.qltctrlDate,
    qltctrlDrName: params.qltctrlDrName,
    qltctrlNursName: params.qltctrlNursName,
    acpMedinsName: params.acpMedinsName,
    acpCommunityName: params.acpCommunityName,
    rinp31Flag: params.rinp31Flag,
    rinp31Purpose: params.rinp31Purpose,
    diagnoses: params.diagnoses,
    procedures: params.procedures,
  };
}

/** 主档分页（首页工作台 / 质控等工作台共用） */
export async function queryCaseRecordPageApi(params: QueryCaseRecordParams) {
  return publicRequestClient.post<PageApiResponse<CaseRecordListItemDto>>(
    `${BASE}/queryPage`,
    params,
  );
}

/** 按就诊查档（医生站） */
export async function lookupByEncounterApi(encounterId: string) {
  return publicRequestClient.post<CaseRecordDetailDto>(`${BASE}/lookupByEncounter`, {
    encounterId: preserveApiIdForRequest(encounterId),
  });
}

/** 加载首页：已建档传 caseRecordId；未建档传 encounterId（服务端 bootstrap） */
export async function loadHomepagePageApi(params: { caseRecordId?: string; encounterId?: string }) {
  return publicRequestClient.post<HomepagePageDto>(`${BASE}/loadPage`, {
    caseRecordId: params.caseRecordId ? preserveApiIdForRequest(params.caseRecordId) : undefined,
    encounterId: params.encounterId ? preserveApiIdForRequest(params.encounterId) : undefined,
  });
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

/** 提交病案归档：DRAFT → PENDING_ARCHIVE，进入归档审批 */
export async function submitHomepageApi(caseRecordId: string) {
  return publicRequestClient.post<CaseRecordDetailDto>(`${BASE}/submitArchive`, {
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

export async function generateHomepagePdfApi(caseRecordId: string) {
  return publicRequestClient.post<PdfGenerateResultDto>(`${BASE}/generateHomepagePdf`, {
    caseRecordId: preserveApiIdForRequest(caseRecordId),
  });
}

export type { PageParams };
