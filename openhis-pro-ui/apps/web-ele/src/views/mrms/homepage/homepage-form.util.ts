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
import rawRegionData from 'province-city-china/dist/level.json';

import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import { HOMEPAGE_FEE_AMOUNT_KEYS } from './homepage-form.types';
import { HOMEPAGE_EMPTY_DISPLAY } from './homepage.constants';

/** province-city-china level 节点 */
interface RegionLevelItem {
  code: string;
  name: string;
  children?: RegionLevelItem[];
}
/** 空表单骨架 */
export function createEmptyHomepageForm(
  caseRecordId?: string,
  encounterId?: string,
): HomepageFormModel {
  return {
    caseRecordId: caseRecordId || '',
    encounterId: encounterId || '',
    medcasno: '',
    patientId: undefined,
    organizationId: '',
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
    birplcName: '',
    naplCodes: [],
    naplName: '',
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
    clinicalPathCode: undefined,
    tcmPrepCode: undefined,
    tcmDeviceCode: undefined,
    tcmTechCode: undefined,
    syndromeNurseCode: undefined,
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
    mainDiagCureCode: '',
    rescueCount: undefined,
    rescueSuccessCount: undefined,
    clinicPathManageCode: '',
    diagMatchOpDscg: '',
    diagMatchAdmDscg: '',
    diagMatchPrePost: '',
    diagMatchClinicPath: '',
    diagMatchRadioPath: '',
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

/** 将现住址详址写入 currAddr 全文列（与详址字段保持一致） */
export function syncCurrAddrText(form: HomepageFormModel) {
  const detail = form.currAddrDetail?.trim() ?? '';
  if (detail) {
    form.currAddr = detail;
  }
}

/**
 * 按省市区 code 路径拼连续中文地名（无逗号；卫统 CSD/GG、HQMS A22）。
 *
 * @param codes 级联 code
 * @returns 如「吉林省长春市南关区」；无法解析则空串
 */
export function joinRegionPlaceName(codes: string[] | undefined): string {
  if (!codes?.length) {
    return '';
  }
  const names: string[] = [];
  // 沿 codes 逐级下钻 province-city-china
  let nodes = rawRegionData as RegionLevelItem[];
  for (const code of codes) {
    const hit = nodes.find((n) => n.code === code);
    if (!hit) {
      break;
    }
    names.push(hit.name);
    nodes = hit.children ?? [];
  }
  return names.join('');
}

/**
 * 出生地/籍贯选区后写入中文地名列（与 codes 同步）。
 *
 * @param form 首页表单
 */
export function syncBirthNativePlaceNames(form: HomepageFormModel) {
  form.birplcName = joinRegionPlaceName(form.birplcCodes);
  form.naplName = joinRegionPlaceName(form.naplCodes);
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

/**
 * 空白码值视为未填（中医信息无系统默认值）。
 *
 * @param v 码或空
 * @return trim 后非空串；否则 undefined
 */
function emptyToUndef(v: string | undefined): string | undefined {
  if (v === undefined || v === null) {
    return undefined;
  }
  const t = String(v).trim();
  if (!t) {
    return undefined;
  }
  return t;
}

/** 金额类快照值转 number */
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

/**
 * 解析省市区 codes：接口多为 string[]；列存为「码/码/码」。
 *
 * @param raw 接口值
 * @returns 级联选择器用的 code 数组
 */
function parseRegionCodes(raw: unknown): string[] {
  if (Array.isArray(raw)) {
    return raw.map((x) => String(x)).filter((x) => x.length > 0);
  }
  if (typeof raw !== 'string' || !raw.trim()) {
    return [];
  }
  return raw
    .trim()
    .split('/')
    .map((x) => x.trim())
    .filter((x) => x.length > 0);
}

/**
 * 从 HomepageDto 正式列取升列字段。
 *
 * @param homepage 首页 DTO
 * @returns 表单偏字段
 */
function pickPromotedScalarFields(homepage: HomepageDto): Partial<HomepageFormModel> {
  return {
    payWayCode: homepage.payWayCode,
    healthCardNo: homepage.healthCardNo,
    ageYear: homepage.ageYear,
    ageMonth: homepage.ageMonth,
    birplcCodes: parseRegionCodes(homepage.birplcCodes),
    birplcName: homepage.birplcName ?? '',
    naplCodes: parseRegionCodes(homepage.naplCodes),
    naplName: homepage.naplName ?? '',
    currAddrCodes: parseRegionCodes(homepage.currAddrCodes),
    currAddrDetail: homepage.currAddrDetail,
    currAddrPoscode: homepage.currAddrPoscode,
    resdAddrCodes: parseRegionCodes(homepage.resdAddrCodes),
    resdAddrDetail: homepage.resdAddrDetail,
    resdAddrPoscode: homepage.resdAddrPoscode,
    emprAddr: homepage.emprAddr,
    emprTel: homepage.emprTel,
    emprPoscode: homepage.emprPoscode,
    admWard: homepage.admWard,
    admBed: homepage.admBed,
    dscgWard: homepage.dscgWard,
    dscgBed: homepage.dscgBed,
    drugAllergyCode: homepage.drugAllergyCode,
    allergyDrugName: homepage.allergyDrugName,
    pathologyNo: homepage.pathologyNo,
    mainDiagCureCode: homepage.mainDiagCureCode,
    rescueCount: homepage.rescueCount,
    rescueSuccessCount: homepage.rescueSuccessCount,
    clinicPathManageCode: homepage.clinicPathManageCode,
    diagMatchOpDscg: homepage.diagMatchOpDscg,
    diagMatchAdmDscg: homepage.diagMatchAdmDscg,
    diagMatchPrePost: homepage.diagMatchPrePost,
    diagMatchClinicPath: homepage.diagMatchClinicPath,
    diagMatchRadioPath: homepage.diagMatchRadioPath,
    autopsyCode: homepage.autopsyCode,
    aboCode: homepage.aboCode,
    rhCode: homepage.rhCode,
    trainDrName: homepage.trainDrName,
    intnDrName: homepage.intnDrName,
    codrName: homepage.codrName,
    medcasQltCode: homepage.medcasQltCode,
    qltctrlDate: homepage.qltctrlDate,
    qltctrlDrName: homepage.qltctrlDrName,
    qltctrlNursName: homepage.qltctrlNursName,
    acpMedinsName: homepage.acpMedinsName,
    acpCommunityName: homepage.acpCommunityName,
    rinp31Flag: homepage.rinp31Flag,
    rinp31Purpose: homepage.rinp31Purpose,
  };
}

/**
 * 费用快照 Map → 表单金额字段。
 *
 * @param fees 后端 fees 快照结果
 * @returns 费用偏字段
 */
function pickLiveFeeFields(fees?: Record<string, number>): Partial<HomepageFormModel> {
  if (!fees) {
    return {};
  }
  const out: Partial<HomepageFormModel> = {};
  for (const key of HOMEPAGE_FEE_AMOUNT_KEYS) {
    const raw = fees[key as string];
    if (raw === undefined || raw === null) {
      continue;
    }
    (out as Record<string, unknown>)[key as string] = moneyNum(raw);
  }
  return out;
}

/**
 * 深拷贝诊断行：Long 主键保持 string；出院 needYbUp 空值默认「是」。
 *
 * @param rows 源诊断行
 * @return 新数组（永不为 null）
 */
function cloneDiagnoses(rows?: DiagnosisLineDto[]): DiagnosisLineDto[] {
  if (!rows?.length) {
    return [];
  }
  // 诊断医生主键必须保持 string，禁止被 Number 抹平；出院上传默认「是」
  return rows.map((r) => {
    const diagType = String(r.diagTypeCode ?? '');
    const isDischarge = diagType.startsWith('discharge');
    const needYb = String(r.needYbUp ?? '').trim();
    return {
      ...r,
      id: r.id != null && r.id !== '' ? String(r.id) : r.id,
      confirmDrId:
        r.confirmDrId != null && r.confirmDrId !== '' ? String(r.confirmDrId) : r.confirmDrId,
      needYbUp: isDischarge ? needYb || '1' : needYb,
    };
  });
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
  const form: HomepageFormModel = {
    ...base,
    caseRecordId: String(homepage.caseRecordId ?? ''),
    // 主档病案号：saveHomepage 发号后回传；load 场景常再由 mergeCaseRecord 补全
    medcasno: homepage.medcasno || base.medcasno,
    encounterId: encounterId || base.encounterId,
    psnName: homepage.psnName,
    gendCode: normalizeHomepageGenderCode(homepage.gendCode),
    brdy: homepage.brdy,
    certno: homepage.certno,
    ntlyCode: homepage.ntlyCode ?? base.ntlyCode,
    natyCode: homepage.natyCode ?? base.natyCode,
    prfsCode: homepage.prfsCode,
    mrgStasCode: homepage.mrgStasCode,
    currAddr: homepage.currAddr,
    psnTel: homepage.psnTel,
    conerName: homepage.conerName,
    conerTel: homepage.conerTel,
    conerRltsCode: homepage.conerRltsCode,
    conerAddr: homepage.conerAddr,
    trtTypeCode: homepage.trtTypeCode,
    // 中医信息：只认正式列
    clinicalPathCode: emptyToUndef(homepage.clinicalPathCode),
    tcmPrepCode: emptyToUndef(homepage.tcmPrepCode),
    tcmDeviceCode: emptyToUndef(homepage.tcmDeviceCode),
    tcmTechCode: emptyToUndef(homepage.tcmTechCode),
    syndromeNurseCode: emptyToUndef(homepage.syndromeNurseCode),
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
    admCatyName: homepage.admCatyName,
    dscgCatyCode: homepage.dscgCatyCode,
    dscgCatyName: homepage.dscgCatyName,
    admTime: homepage.admTime,
    dscgTime: homepage.dscgTime,
    iptDays: homepage.iptDays,
    dscgWayCode: homepage.dscgWayCode,
    transferDeptSummary: homepage.transferDeptSummary,
    deptDirectorName: homepage.deptDirectorName,
    chfdrName: homepage.chfdrName,
    atddrName: homepage.atddrName,
    iptDrName: homepage.iptDrName,
    respNursName: homepage.respNursName,
    diagnoses: cloneDiagnoses(homepage.diagnoses),
    procedures: cloneProcedures(homepage.procedures),
    ...pickPromotedScalarFields(homepage),
    ...pickLiveFeeFields(homepage.fees),
  };
  if (!form.currAddrDetail && form.currAddr) {
    form.currAddrDetail = form.currAddr;
  }
  // 年龄优先用正式列；空则按出生/入院推算展示
  if (form.ageYear == null) {
    form.ageYear = calcAgeYear(form.brdy, form.admTime);
  }
  return form;
}

/**
 * 新建诊断行默认值（出院上传缺省「是」）。
 *
 * @param diagTypeCode HomepageDiagType code
 * @param sortNo 排序号
 */
export function newDiagnosisRow(diagTypeCode: string, sortNo: number): DiagnosisLineDto {
  return {
    diagTypeCode,
    sortNo,
    diagCode: '',
    diagName: '',
    admCondCode: '',
    outIllStatusCode: '',
    confirmTime: '',
    diagRemark: '',
    confirmDrId: '',
    confirmDrName: '',
    needYbUp: diagTypeCode.startsWith('discharge') ? '1' : '',
  };
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
  const codes = sectionsToRefreshCodes(section);
  return codes[0] ?? 'basic';
}

/**
 * 章节导入 → 后端 refresh 分区列表。
 * 住院信息 UI 合并了人口学+入出院，须同时刷 basic + inpatient。
 */
export function sectionsToRefreshCodes(section: string): RefreshSectionCode[] {
  if (section === 'basic' || section === 'inpatient') {
    return ['basic', 'inpatient'];
  }
  if (
    section === 'diagnosis' ||
    section === 'procedure' ||
    section === 'staff' ||
    section === 'fee' ||
    section === 'all'
  ) {
    return [section];
  }
  return ['basic', 'inpatient'];
}

/** 主档冗余补全 */
export function mergeCaseRecordIntoFormModel(
  form: HomepageFormModel,
  detail: CaseRecordDetailDto | null | undefined,
): HomepageFormModel {
  if (!detail) {
    return form;
  }
  // 入院科室：优先保留已有（列表带入），否则用 loadPage 回填
  const organizationId =
    preserveApiIdForRequest(form.organizationId) ||
    preserveApiIdForRequest(detail.organizationId) ||
    '';
  return {
    ...form,
    caseRecordId: form.caseRecordId || String(detail.caseRecordId ?? ''),
    medcasno: form.medcasno || detail.medcasno,
    visitTimes: form.visitTimes ?? detail.patnIptCnt,
    patientId: form.patientId || (detail.patientId ? String(detail.patientId) : undefined),
    organizationId,
  };
}

/**
 * 未填值展示占位（与 {@link HOMEPAGE_EMPTY_DISPLAY} 一致）。
 *
 * @param value 原始展示值
 * @returns 非空 trim 后原文，否则 `-`
 */
export function displayHomepageEmpty(value?: null | number | string): string {
  if (value == null) {
    return HOMEPAGE_EMPTY_DISPLAY;
  }
  const text = String(value).trim();
  if (!text) {
    return HOMEPAGE_EMPTY_DISPLAY;
  }
  return text;
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
  };
}

/** 组装保存请求体（正式列标量直传；费用不提交） */
export function toSaveHomepageParams(form: HomepageFormModel): SaveHomepageParams {
  syncCurrAddrText(form);
  syncBirthNativePlaceNames(form);
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
    // 中医信息无默认值；空串让后端清空列（null 会跳过写入导致清不掉）
    clinicalPathCode: form.clinicalPathCode?.trim() ? form.clinicalPathCode.trim() : '',
    tcmPrepCode: form.tcmPrepCode?.trim() ? form.tcmPrepCode.trim() : '',
    tcmDeviceCode: form.tcmDeviceCode?.trim() ? form.tcmDeviceCode.trim() : '',
    tcmTechCode: form.tcmTechCode?.trim() ? form.tcmTechCode.trim() : '',
    syndromeNurseCode: form.syndromeNurseCode?.trim() ? form.syndromeNurseCode.trim() : '',
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
    payWayCode: form.payWayCode,
    healthCardNo: form.healthCardNo,
    ageYear: form.ageYear,
    ageMonth: form.ageMonth,
    birplcCodes: form.birplcCodes,
    birplcName: form.birplcName,
    naplCodes: form.naplCodes,
    naplName: form.naplName,
    currAddrCodes: form.currAddrCodes,
    currAddrDetail: form.currAddrDetail,
    currAddrPoscode: form.currAddrPoscode,
    resdAddrCodes: form.resdAddrCodes,
    resdAddrDetail: form.resdAddrDetail,
    resdAddrPoscode: form.resdAddrPoscode,
    emprAddr: form.emprAddr,
    emprTel: form.emprTel,
    emprPoscode: form.emprPoscode,
    admWard: form.admWard,
    admBed: form.admBed,
    dscgWard: form.dscgWard,
    dscgBed: form.dscgBed,
    drugAllergyCode: form.drugAllergyCode,
    allergyDrugName: form.allergyDrugName,
    pathologyNo: form.pathologyNo,
    mainDiagCureCode: form.mainDiagCureCode,
    rescueCount: form.rescueCount,
    rescueSuccessCount: form.rescueSuccessCount,
    clinicPathManageCode: form.clinicPathManageCode,
    diagMatchOpDscg: form.diagMatchOpDscg,
    diagMatchAdmDscg: form.diagMatchAdmDscg,
    diagMatchPrePost: form.diagMatchPrePost,
    diagMatchClinicPath: form.diagMatchClinicPath,
    diagMatchRadioPath: form.diagMatchRadioPath,
    autopsyCode: form.autopsyCode,
    aboCode: form.aboCode,
    rhCode: form.rhCode,
    trainDrName: form.trainDrName,
    intnDrName: form.intnDrName,
    codrName: form.codrName,
    medcasQltCode: form.medcasQltCode,
    qltctrlDate: form.qltctrlDate,
    qltctrlDrName: form.qltctrlDrName,
    qltctrlNursName: form.qltctrlNursName,
    acpMedinsName: form.acpMedinsName,
    acpCommunityName: form.acpCommunityName,
    rinp31Flag: form.rinp31Flag,
    rinp31Purpose: form.rinp31Purpose,
    diagnoses: form.diagnoses,
    procedures: form.procedures,
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

/**
 * 后端 settle_upload_status_code（0/1/2）→ 列表展示 code。
 * 空或「0」归一为 none，便于 Tag/文案统一。
 */
function mapSettleUploadStatus(raw?: string | null): string {
  if (!raw || raw === '0') {
    return 'none';
  }
  if (raw === '1' || raw === '2' || raw === 'failed') {
    return raw;
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
    psnNo: item.psnNo?.trim() || undefined,
    setlId: item.setlId?.trim() || undefined,
    admDeptName: item.admCatyName || item.dscgCatyName,
    organizationId: preserveApiIdForRequest(item.organizationId),
    insuranceTypeText: item.insuranceTypeText?.trim() || undefined,
    admTime: item.admTime,
    dscgTime: item.dscgTime,
    dischargeDiagnosisText: item.mainDiagName,
    dischargedCode: discharged,
    settledCode: item.settledCode === '1' ? '1' : '0',
    homepageUploadStatusCode: mapHomepageUploadStatus(item.chsUploadStatusCode),
    settleUploadStatusCode: mapSettleUploadStatus(item.settleUploadStatusCode),
    orderUploadStatusCode: item.orderUploadStatusCode || 'none',
    statusCode: item.statusCode,
    clinicalArchived: item.clinicalArchived === true,
  };
}
