import type {
  CaseRecordDetailDto,
  CaseRecordListItemDto,
  DiagnosisLineDto,
  HomepageDto,
  ProcedureLineDto,
  RefreshSectionCode,
  SaveHomepageParams,
} from '#/api/mrms/types';

import type { HomepageFormModel } from './homepage-form.types';
import type { HomepageListRow } from './homepage-list.types';

import dayjs from 'dayjs';

import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import { HOMEPAGE_EXTENSION_KEYS, HOMEPAGE_FEE_AMOUNT_KEYS } from './homepage-form.types';

/** 空表单骨架 */
export function createEmptyHomepageForm(
  caseRecordId?: string,
  encounterId?: string,
): HomepageFormModel {
  return {
    caseRecordId: caseRecordId || '',
    encounterId: encounterId || '',
    medcasno: '',
    payWayCode: '',
    healthCardNo: '',
    visitTimes: 1,
    psnName: '',
    gendCode: '',
    brdy: '',
    ageYear: undefined,
    ageMonth: undefined,
    certno: '',
    ntlyCode: '156',
    natyCode: '01',
    prfsCode: '',
    mrgStasCode: '',
    birplcCodes: [],
    naplCodes: [],
    currAddrCodes: [],
    currAddrDetail: '',
    currAddrPoscode: '',
    resdAddrCodes: [],
    resdAddrDetail: '',
    resdAddrPoscode: '',
    emprAddr: '',
    emprTel: '',
    emprPoscode: '',
    psnTel: '',
    conerName: '',
    conerRltsCode: '',
    conerTel: '',
    conerAddr: '',
    clinicalPathCode: '',
    tcmPrepCode: '',
    tcmDeviceCode: '',
    tcmTechCode: '',
    syndromeNurseCode: '',
    iptNo: '',
    admWayCode: '',
    trtTypeCode: '',
    admCatyCode: '',
    admCatyName: '',
    admWard: '',
    admBed: '',
    admTime: '',
    dscgCatyCode: '',
    dscgCatyName: '',
    dscgWard: '',
    dscgBed: '',
    dscgTime: '',
    iptDays: undefined,
    transferDeptSummary: '',
    spgaNurscareDays: undefined,
    lv1NurscareDays: undefined,
    scdNurscareDays: undefined,
    lv3NurscareDays: undefined,
    otpWmDise: '',
    wmDiseCode: '',
    otpTcmDise: '',
    tcmDiseCode: '',
    brnDamgBfadmComaDura: '',
    brnDamgAfadmComaDura: '',
    ventUsedDura: '',
    drugAllergyCode: '1',
    allergyDrugName: '',
    pathologyNo: '',
    autopsyCode: '',
    aboCode: '',
    rhCode: '',
    dscgWayCode: '',
    deptDirectorName: '',
    chfdrName: '',
    atddrName: '',
    iptDrName: '',
    respNursName: '',
    trainDrName: '',
    intnDrName: '',
    codrName: '',
    medcasQltCode: '',
    qltctrlDate: '',
    qltctrlDrName: '',
    qltctrlNursName: '',
    acpMedinsName: '',
    acpCommunityName: '',
    rinp31Flag: '1',
    rinp31Purpose: '',
    diagnoses: [],
    procedures: [],
  };
}

/** 由出生日期与入院日估算整岁年龄 */
export function calcAgeYear(brdy?: string, admTime?: string): number | undefined {
  if (!brdy) {
    return undefined;
  }
  const birth = dayjs(brdy);
  const base = admTime ? dayjs(admTime) : dayjs();
  if (!birth.isValid() || !base.isValid()) {
    return undefined;
  }
  return Math.max(0, base.diff(birth, 'year'));
}

/** 计算住院天数（即进不计出：入院日计、出院日不计；同日至少 1） */
export function calcIptDays(admTime?: string, dscgTime?: string): number | undefined {
  if (!admTime || !dscgTime) {
    return undefined;
  }
  const start = dayjs(admTime).startOf('day');
  const end = dayjs(dscgTime).startOf('day');
  if (!start.isValid() || !end.isValid()) {
    return undefined;
  }
  const days = end.diff(start, 'day');
  return days < 1 ? 1 : days;
}

/** 现住址拼成 currAddr 文本（便于保存兼容） */
export function syncCurrAddrText(form: HomepageFormModel) {
  const detail = form.currAddrDetail?.trim() ?? '';
  if (detail) {
    form.currAddr = detail;
  }
}

/** HIS AdministrativeGender → 首页性别码（1男 2女） */
function normalizeHomepageGenderCode(code?: string | null): string | undefined {
  const raw = String(code ?? '').trim();
  if (!raw) {
    return undefined;
  }
  if (raw === 'MALE') {
    return '1';
  }
  if (raw === 'FEMALE') {
    return '2';
  }
  return raw;
}

/** 未知值转字符串 */
function str(v: unknown): string | undefined {
  if (v === null || v === undefined || v === '') {
    return undefined;
  }
  return String(v);
}

/** 金额类 extension 值转 number */
function moneyNum(v: unknown): number | undefined {
  if (v === null || v === undefined || v === '') {
    return undefined;
  }
  const n = Number(v);
  if (!Number.isFinite(n)) {
    return undefined;
  }
  return n;
}

/** 从 extension 回填扩展字段 */
function pickExtensionFields(ext: Record<string, unknown>): Partial<HomepageFormModel> {
  const out: Partial<HomepageFormModel> = {};
  const feeKeySet = new Set<string>(HOMEPAGE_FEE_AMOUNT_KEYS as string[]);
  for (const key of HOMEPAGE_EXTENSION_KEYS) {
    if (!(key in ext)) {
      continue;
    }
    const raw = ext[key as string];
    // 费用金额键统一转 number，便于 InputNumber 回显
    if (feeKeySet.has(key as string)) {
      (out as Record<string, unknown>)[key as string] = moneyNum(raw);
      continue;
    }
    (out as Record<string, unknown>)[key as string] = raw;
  }
  return out;
}

function cloneDiagnoses(rows?: DiagnosisLineDto[]): DiagnosisLineDto[] {
  if (!rows?.length) {
    return [];
  }
  return rows.map((r) => ({ ...r }));
}

function cloneProcedures(rows?: ProcedureLineDto[]): ProcedureLineDto[] {
  if (!rows?.length) {
    return [];
  }
  return rows.map((r) => ({ ...r }));
}

/**
 * 回填入院途径：仅保留 4401 合法码 1/2/3/9；
 * 丢弃误写入的入院类型码（如登记默认 6=常规）。
 */
function sanitizeAdmWayCode(code?: string): string {
  const v = (code ?? '').trim();
  if (!v) {
    return '';
  }
  if (v === '1' || v === '2' || v === '3' || v === '9') {
    return v;
  }
  return '';
}

/** HomepageDto → 表单模型 */
export function homepageToFormModel(
  homepage: HomepageDto,
  encounterId?: string,
): HomepageFormModel {
  const base = createEmptyHomepageForm(String(homepage.caseRecordId ?? ''), encounterId);
  const ext = homepage.extension ?? {};
  const form: HomepageFormModel = {
    ...base,
    caseRecordId: String(homepage.caseRecordId ?? ''),
    encounterId: encounterId || base.encounterId,
    psnName: homepage.psnName,
    gendCode: normalizeHomepageGenderCode(homepage.gendCode),
    brdy: homepage.brdy,
    certno: homepage.certno,
    ntlyCode: homepage.ntlyCode ?? str(ext.ntlyCode) ?? base.ntlyCode,
    natyCode: homepage.natyCode ?? str(ext.natyCode) ?? base.natyCode,
    prfsCode: homepage.prfsCode ?? str(ext.prfsCode),
    mrgStasCode: homepage.mrgStasCode ?? str(ext.mrgStasCode),
    currAddr: homepage.currAddr ?? str(ext.currAddr),
    psnTel: homepage.psnTel ?? str(ext.psnTel),
    conerName: homepage.conerName ?? str(ext.conerName),
    conerTel: homepage.conerTel ?? str(ext.conerTel),
    conerRltsCode: homepage.conerRltsCode ?? str(ext.conerRltsCode),
    conerAddr: homepage.conerAddr ?? str(ext.conerAddr),
    trtTypeCode: homepage.trtTypeCode ?? str(ext.trtTypeCode),
    spgaNurscareDays: homepage.spgaNurscareDays,
    lv1NurscareDays: homepage.lv1NurscareDays,
    scdNurscareDays: homepage.scdNurscareDays,
    lv3NurscareDays: homepage.lv3NurscareDays,
    otpWmDise: homepage.otpWmDise,
    wmDiseCode: homepage.wmDiseCode,
    otpTcmDise: homepage.otpTcmDise,
    tcmDiseCode: homepage.tcmDiseCode,
    brnDamgBfadmComaDura: homepage.brnDamgBfadmComaDura,
    brnDamgAfadmComaDura: homepage.brnDamgAfadmComaDura,
    ventUsedDura: homepage.ventUsedDura,
    iptNo: homepage.iptNo,
    admWayCode: sanitizeAdmWayCode(homepage.admWayCode),
    admCatyCode: homepage.admCatyCode,
    admCatyName: homepage.admCatyName ?? str(ext.admCatyName),
    dscgCatyCode: homepage.dscgCatyCode,
    dscgCatyName: homepage.dscgCatyName ?? str(ext.dscgCatyName),
    admTime: homepage.admTime,
    dscgTime: homepage.dscgTime,
    iptDays: homepage.iptDays,
    dscgWayCode: homepage.dscgWayCode,
    transferDeptSummary: homepage.transferDeptSummary ?? str(ext.transferDeptSummary),
    deptDirectorName: homepage.deptDirectorName ?? str(ext.deptDirectorName),
    chfdrName: homepage.chfdrName,
    atddrName: homepage.atddrName,
    iptDrName: homepage.iptDrName,
    respNursName: homepage.respNursName ?? str(ext.respNursName),
    extension: homepage.extension,
    diagnoses: cloneDiagnoses(homepage.diagnoses),
    procedures: cloneProcedures(homepage.procedures),
    ...pickExtensionFields(ext),
  };
  // 兼容旧 extension 护理天数字段名
  if (form.spgaNurscareDays == null && ext.nurseDaysSpecial != null) {
    form.spgaNurscareDays = Number(ext.nurseDaysSpecial);
  }
  if (form.lv1NurscareDays == null && ext.nurseDaysL1 != null) {
    form.lv1NurscareDays = Number(ext.nurseDaysL1);
  }
  if (form.scdNurscareDays == null && ext.nurseDaysL2 != null) {
    form.scdNurscareDays = Number(ext.nurseDaysL2);
  }
  if (form.lv3NurscareDays == null && ext.nurseDaysL3 != null) {
    form.lv3NurscareDays = Number(ext.nurseDaysL3);
  }
  if (!form.currAddrDetail && form.currAddr) {
    form.currAddrDetail = form.currAddr;
  }
  form.ageYear = calcAgeYear(form.brdy, form.admTime);
  return form;
}

export function newDiagnosisRow(diagTypeCode: string, sortNo: number): DiagnosisLineDto {
  return { diagTypeCode, sortNo, diagCode: '', diagName: '', admCondCode: '' };
}

export function newProcedureRow(sortNo: number): ProcedureLineDto {
  return {
    sortNo,
    oprnOprtCode: '',
    oprnOprtName: '',
    oprnLevelCode: '',
    oprnIncisionCode: '',
    healGradeCode: '',
    anstWayCode: '',
    operDrName: '',
    anstDrName: '',
  };
}

export function sectionToRefreshCode(section: string): RefreshSectionCode {
  if (
    section === 'basic' ||
    section === 'inpatient' ||
    section === 'diagnosis' ||
    section === 'procedure' ||
    section === 'staff' ||
    section === 'fee'
  ) {
    return section;
  }
  return 'basic';
}

/** 主档冗余补全 */
export function mergeCaseRecordIntoFormModel(
  form: HomepageFormModel,
  detail: CaseRecordDetailDto | null | undefined,
): HomepageFormModel {
  if (!detail) {
    return form;
  }
  return {
    ...form,
    caseRecordId: form.caseRecordId || String(detail.caseRecordId ?? ''),
    medcasno: form.medcasno || detail.medcasno,
    visitTimes: form.visitTimes ?? detail.patnIptCnt,
    patientId: form.patientId || (detail.patientId ? String(detail.patientId) : undefined),
  };
}

/** 深拷贝表单 */
export function cloneHomepageForm(form: HomepageFormModel): HomepageFormModel {
  return {
    ...form,
    birplcCodes: [...(form.birplcCodes ?? [])],
    naplCodes: [...(form.naplCodes ?? [])],
    currAddrCodes: [...(form.currAddrCodes ?? [])],
    resdAddrCodes: [...(form.resdAddrCodes ?? [])],
    diagnoses: cloneDiagnoses(form.diagnoses),
    procedures: cloneProcedures(form.procedures),
    extension: form.extension ? { ...form.extension } : undefined,
  };
}

/** 组装保存请求体（P0 标量直传，其余进 extension） */
export function toSaveHomepageParams(form: HomepageFormModel): SaveHomepageParams {
  syncCurrAddrText(form);
  const extension: Record<string, unknown> = { ...(form.extension ?? {}) };
  for (const key of HOMEPAGE_EXTENSION_KEYS) {
    const value = form[key];
    if (value !== undefined && value !== null && value !== '') {
      extension[key as string] = value;
    }
  }
  return {
    caseRecordId: form.caseRecordId || undefined,
    encounterId: form.encounterId || undefined,
    psnName: form.psnName,
    gendCode: form.gendCode,
    brdy: form.brdy,
    certno: form.certno,
    ntlyCode: form.ntlyCode,
    natyCode: form.natyCode,
    prfsCode: form.prfsCode,
    mrgStasCode: form.mrgStasCode,
    currAddr: form.currAddr,
    psnTel: form.psnTel,
    conerName: form.conerName,
    conerTel: form.conerTel,
    conerRltsCode: form.conerRltsCode,
    conerAddr: form.conerAddr,
    trtTypeCode: form.trtTypeCode,
    spgaNurscareDays: form.spgaNurscareDays,
    lv1NurscareDays: form.lv1NurscareDays,
    scdNurscareDays: form.scdNurscareDays,
    lv3NurscareDays: form.lv3NurscareDays,
    otpWmDise: form.otpWmDise,
    wmDiseCode: form.wmDiseCode,
    otpTcmDise: form.otpTcmDise,
    tcmDiseCode: form.tcmDiseCode,
    brnDamgBfadmComaDura: form.brnDamgBfadmComaDura,
    brnDamgAfadmComaDura: form.brnDamgAfadmComaDura,
    ventUsedDura: form.ventUsedDura,
    iptNo: form.iptNo,
    admWayCode: form.admWayCode,
    admCatyCode: form.admCatyCode,
    admCatyName: form.admCatyName,
    admTime: form.admTime,
    dscgCatyCode: form.dscgCatyCode,
    dscgCatyName: form.dscgCatyName,
    dscgTime: form.dscgTime,
    iptDays: form.iptDays,
    transferDeptSummary: form.transferDeptSummary,
    dscgWayCode: form.dscgWayCode,
    deptDirectorName: form.deptDirectorName,
    chfdrName: form.chfdrName,
    atddrName: form.atddrName,
    iptDrName: form.iptDrName,
    respNursName: form.respNursName,
    diagnoses: form.diagnoses,
    procedures: form.procedures,
    extension,
  };
}

/** 后端 chs_upload_status_code → 列表展示 code（空=none；其余对齐 ChsUploadStatus） */
function mapHomepageUploadStatus(chs?: string | null): string {
  if (!chs) {
    return 'none';
  }
  if (chs === 'success' || chs === 'failed' || chs === 'pending' || chs === 'skipped') {
    return chs;
  }
  return 'none';
}

/** CaseRecordListItemDto → 列表行视图 */
export function mapListItemToRow(item: CaseRecordListItemDto): HomepageListRow {
  const encounterId = String(item.encounterId ?? '');
  const caseRecordId = item.caseRecordId ? String(item.caseRecordId) : undefined;
  const discharged = item.dscgTime ? '1' : '0';
  return {
    id: caseRecordId || encounterId,
    encounterId,
    caseRecordId,
    patientId: item.patientId ? String(item.patientId) : undefined,
    patientName: item.psnName ?? '',
    genderEnum: item.gendCode?.trim() || undefined,
    ageText: item.ageText?.trim() || undefined,
    bedName: item.bedName?.trim() || undefined,
    wardName: item.wardName?.trim() || undefined,
    iptNo: item.iptNo ?? '',
    medcasno: item.medcasno ?? '',
    certno: item.certno,
    admDeptName: item.admCatyName || item.dscgCatyName,
    organizationId: preserveApiIdForRequest(item.organizationId),
    insuranceTypeText: item.insuranceTypeText?.trim() || undefined,
    admTime: item.admTime,
    dscgTime: item.dscgTime,
    dischargeDiagnosisText: item.mainDiagName,
    dischargedCode: discharged,
    settledCode: item.settledCode === '1' ? '1' : '0',
    homepageUploadStatusCode: mapHomepageUploadStatus(item.chsUploadStatusCode),
    settleUploadStatusCode: item.settleUploadStatusCode || 'none',
    orderUploadStatusCode: item.orderUploadStatusCode || 'none',
    statusCode: item.statusCode,
    clinicalArchived: item.clinicalArchived === true,
  };
}
