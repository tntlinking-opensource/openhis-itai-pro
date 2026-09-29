/**
 * 住院病案首页 PrintData（中医/西医模板共用字段；variant 控制诊断列铺法）
 *
 * SOURCE：form=P（详情页当前表单）；hospitalName/orgCode=C（登录机构抬头）；variant=C
 * 地址省市区字段在映射时去掉「省/市/区」等后缀，模板已印固定字样。
 */
import type { FormItemOption } from '@vben-core/table-layout-ui';

import type { DiagnosisLineDto, ProcedureLineDto } from '#/api/mrms/types';
import type { HomepageFormModel } from '#/views/mrms/homepage/homepage-form.types';

import dayjs from 'dayjs';
import rawRegionData from 'province-city-china/dist/level.json';

import {
  CONER_RLTS_OPTIONS,
  NATY_OPTIONS,
  NTLY_OPTIONS,
  PRFS_OPTIONS,
} from '#/views/mrms/mrms.constants';

import { invPrintAmountText } from './inv-common';

const DIAG_ROW_COUNT = 11;
const OP_ROW_COUNT = 8;
/** 与详情页 HOMEPAGE_EMPTY_DISPLAY 一致：空值印横杠 */
const EMPTY = '-';

interface RegionNode {
  code: string;
  name: string;
  children?: RegionNode[];
}

/** 住院病案首页 PrintData（模板 HTML {{field}}） */
export interface InpatientHomepagePrintData {
  [key: string]: string;
  hospitalName: string;
  orgCode: string;
  payMethodMark: string;
  healthCardNo: string;
  iptCount: string;
  medcasno: string;
  psnName: string;
  gendMark: string;
  birthY: string;
  birthM: string;
  birthD: string;
  ageText: string;
  nationality: string;
  ageMonth: string;
  birthWeight: string;
  admitWeight: string;
  birthProv: string;
  birthCity: string;
  birthCounty: string;
  nativeProv: string;
  nativeCity: string;
  nation: string;
  certno: string;
  occupation: string;
  marriageMark: string;
  currProv: string;
  currCity: string;
  currCounty: string;
  psnTel: string;
  currZip: string;
  regProv: string;
  regCity: string;
  regCounty: string;
  regZip: string;
  workAddr: string;
  workTel: string;
  workZip: string;
  conerName: string;
  conerRlts: string;
  conerAddr: string;
  conerTel: string;
  admWayMark: string;
  trtTypeMark: string;
  admY: string;
  admM: string;
  admD: string;
  admH: string;
  admCatyName: string;
  admBed: string;
  transferDeptSummary: string;
  dscgY: string;
  dscgM: string;
  dscgD: string;
  dscgH: string;
  dscgCatyName: string;
  dscgBed: string;
  iptDays: string;
  otpTcmDise: string;
  tcmDiseCode: string;
  otpWmDise: string;
  wmDiseCode: string;
  clinicalPathMark: string;
  tcmPrepMark: string;
  tcmDeviceMark: string;
  tcmTechMark: string;
  syndromeNurseMark: string;
  mainDiagName: string;
  mainDiagCode: string;
  mainAdmCond: string;
  injPoisonName: string;
  injPoisonCode: string;
  pathologyName: string;
  pathologyCode: string;
  pathologyNo: string;
  allergyMark: string;
  allergyDrug: string;
  autopsyMark: string;
  bloodTypeMark: string;
  rhMark: string;
  deptDirectorName: string;
  chfdrName: string;
  atddrName: string;
  iptDrName: string;
  respNursName: string;
  refresherDrName: string;
  internDrName: string;
  coderName: string;
  qualityMark: string;
  qcDoctorName: string;
  qcNurseName: string;
  qcY: string;
  qcM: string;
  qcD: string;
  dscgWayMark: string;
  transferHosp1: string;
  transferHosp2: string;
  readmitMark: string;
  readmitPurpose: string;
  comaBfDay: string;
  comaBfHour: string;
  comaBfMin: string;
  comaAfDay: string;
  comaAfHour: string;
  comaAfMin: string;
  medfeeSumamt: string;
  selfPayAmt: string;
  fee1: string;
  fee2: string;
  fee3: string;
  fee4: string;
  fee5: string;
  fee6: string;
  fee7: string;
  fee8: string;
  fee9: string;
  fee9a: string;
  fee10: string;
  fee10a: string;
  fee10b: string;
  fee11: string;
  fee12: string;
  fee13: string;
  fee13a: string;
  fee14: string;
  fee15: string;
  fee16: string;
  fee17: string;
  fee18: string;
  fee19: string;
  fee20: string;
  fee21: string;
  fee22: string;
  fee23: string;
  fee24: string;
  /**
   * 中医信息勾选码（中医定稿模板已绑的 field 名保持兼容：*Mark 与正式列 camelCase 双写同值）。
   * clinicalPath：1中医 2西医 3否；其余：1是 2否。西医场景置空。
   */
  clinicalPathMark: string;
  tcmPrepMark: string;
  tcmDeviceMark: string;
  tcmTechMark: string;
  syndromeNurseMark: string;
  clinicalPathCode: string;
  tcmPrepCode: string;
  tcmDeviceCode: string;
  tcmTechCode: string;
  syndromeNurseCode: string;
}

/** 住院病案首页 mapper 入参 */
export interface InpatientHomepagePrintSource {
  /** P 详情页当前表单 */
  form: HomepageFormModel;
  /** C 医疗机构抬头 */
  hospitalName?: string;
  /** C 组织机构代码 */
  orgCode?: string;
  /** 模板变体：中医双列 / 西医单列 */
  variant?: 'tcm' | 'wm';
}

let regionNameIndex: Map<string, string> | null = null;

/** 空值转原始串（内部用，不印横杠） */
function str(value: null | number | string | undefined): string {
  if (value === null || value === undefined) {
    return '';
  }
  const text = String(value).trim();
  if (text === '—' || text === '-') {
    return '';
  }
  return text;
}

/** 打印展示：空则横杠 */
function dash(value: null | number | string | undefined): string {
  return str(value) || EMPTY;
}

/** 金额打印：空则横杠 */
function dashAmount(value: null | number | string | undefined): string {
  return invPrintAmountText(value) || EMPTY;
}

/** 勾选框码：有值印码，无值印横杠（方框内也要有 -） */
function mark(value: null | number | string | undefined): string {
  return str(value) || EMPTY;
}

/** 字典 code → 展示文案；未命中则回退 code */
function optionLabel(options: FormItemOption[], code?: null | number | string): string {
  const raw = str(code);
  if (!raw) {
    return '';
  }
  const hit = options.find((item) => String(item.value) === raw);
  return hit?.label ?? raw;
}

/** 性别勾选码：HIS MALE/FEMALE 归一到国标 1/2 */
function genderMark(code?: string): string {
  const raw = str(code);
  if (raw === 'MALE') {
    return '1';
  }
  if (raw === 'FEMALE') {
    return '2';
  }
  if (raw === '0') {
    return '';
  }
  return raw;
}

/** 拆日期为年/月/日/时 */
function dateParts(raw?: string): { d: string; h: string; m: string; y: string } {
  const text = str(raw);
  if (!text) {
    return { y: '', m: '', d: '', h: '' };
  }
  const parsed = dayjs(text);
  if (!parsed.isValid()) {
    return { y: '', m: '', d: '', h: '' };
  }
  return {
    y: parsed.format('YYYY'),
    m: parsed.format('MM'),
    d: parsed.format('DD'),
    h: parsed.format('HH'),
  };
}

/** 昏迷时长「天/时/分」拆成三格 */
function parseDuration(raw?: string): { day: string; hour: string; min: string } {
  const text = str(raw);
  if (!text) {
    return { day: '', hour: '', min: '' };
  }
  const slash = text.split(/[/／]/).map((part) => part.trim());
  const slashOk =
    slash.length >= 2 &&
    slash.length <= 3 &&
    slash.every((part) => part === '' || /^\d+$/.test(part));
  if (slashOk) {
    return {
      day: slash[0] ?? '',
      hour: slash[1] ?? '',
      min: slash[2] ?? '',
    };
  }
  return {
    day: text.match(/(\d+)\s*天/)?.[1] ?? '',
    hour: text.match(/(\d+)\s*时/)?.[1] ?? '',
    min: text.match(/(\d+)\s*分/)?.[1] ?? '',
  };
}

/** 省市区 code → 名称（与 SelectArea 同源 level.json） */
function regionName(code?: string): string {
  const raw = str(code);
  if (!raw) {
    return '';
  }
  if (!regionNameIndex) {
    regionNameIndex = new Map();
    const walk = (nodes: RegionNode[]) => {
      for (const node of nodes) {
        regionNameIndex!.set(node.code, node.name);
        if (node.children?.length) {
          walk(node.children);
        }
      }
    };
    walk(rawRegionData as RegionNode[]);
  }
  return regionNameIndex.get(raw) ?? '';
}

/**
 * 去掉行政区划后缀，避免与模板「省（区、市）/市/县」重复。
 * 例：吉林省→吉林，长春市→长春，朝阳区→朝阳。
 */
function stripRegionSuffix(name: string): string {
  const raw = str(name);
  if (!raw) {
    return '';
  }
  return raw
    .replace(/特别行政区$/, '')
    .replace(/维吾尔自治区$/, '')
    .replace(/壮族自治区$/, '')
    .replace(/回族自治区$/, '')
    .replace(/自治区$/, '')
    .replace(/自治州$/, '')
    .replace(/地区$/, '')
    .replace(/自治县$/, '')
    .replace(/[省市州县区盟]$/, '');
}

/** 省/市/区 code 数组拆成三栏名称（打印用，无省市区后缀） */
function splitRegion(codes?: string[]): { city: string; county: string; prov: string } {
  const list = codes ?? [];
  return {
    prov: stripRegionSuffix(regionName(list[0])),
    city: stripRegionSuffix(regionName(list[1])),
    county: stripRegionSuffix(regionName(list[2])),
  };
}

/** 诊断行是否有可打内容 */
function hasDiagContent(row: DiagnosisLineDto): boolean {
  return Boolean(str(row.diagName) || str(row.diagCode));
}

/** 中医疾病目录（术语 condition_definition_source.tcm_dx） */
const TCM_DISEASE_SOURCE = 'tcm_dx';
/** 中医证候目录（术语 condition_definition_source.tcm_syndrome） */
const TCM_SYNDROME_SOURCE = 'tcm_syndrome';

/** 出院诊断表只打出院诊断；未填出院诊断时留空，不回退入院诊断。 */
function pickTableDiagnoses(form: HomepageFormModel): DiagnosisLineDto[] {
  const list = form.diagnoses ?? [];
  const dischargeMain = list.filter(
    (row) => row.diagTypeCode === 'discharge_main' && hasDiagContent(row),
  );
  const dischargeOther = list.filter(
    (row) => row.diagTypeCode === 'discharge_other' && hasDiagContent(row),
  );
  return [...dischargeMain, ...dischargeOther];
}

/** 取指定类型首条诊断 */
function findSpecialDiag(list: DiagnosisLineDto[], typeCode: string): DiagnosisLineDto | undefined {
  return list.find((row) => row.diagTypeCode === typeCode);
}

/** 诊断空槽：有数据时下方空白行不印横杠；完全无诊断时仅首行横杠 */
function emptyDiagSlot(): string {
  return '';
}

/**
 * 出院诊断表：
 * - tcm：左列中医主病/主证，右列西医主要/其他
 * - wm：国标双列出院诊断（左主要/其他，右继续其他），仅铺西医码
 * 空白行不填「-」，仅在整表无诊断时首行填「-」。
 */
function buildDiagFields(
  rows: DiagnosisLineDto[],
  variant: 'tcm' | 'wm' = 'tcm',
): Record<string, string> {
  const out: Record<string, string> = {
    mainDiagName: emptyDiagSlot(),
    mainDiagCode: emptyDiagSlot(),
    mainAdmCond: emptyDiagSlot(),
  };
  for (let i = 0; i < DIAG_ROW_COUNT; i += 1) {
    out[`othDiagNameL${i}`] = emptyDiagSlot();
    out[`othDiagCodeL${i}`] = emptyDiagSlot();
    out[`othAdmCondL${i}`] = emptyDiagSlot();
    out[`othDiagNameR${i}`] = emptyDiagSlot();
    out[`othDiagCodeR${i}`] = emptyDiagSlot();
    out[`othAdmCondR${i}`] = emptyDiagSlot();
  }

  const tcmDiseases: DiagnosisLineDto[] = [];
  const tcmSyndromes: DiagnosisLineDto[] = [];
  const western: DiagnosisLineDto[] = [];
  for (const row of rows) {
    const source = str(row.sourceEnum);
    if (source === TCM_DISEASE_SOURCE) {
      tcmDiseases.push(row);
      continue;
    }
    if (source === TCM_SYNDROME_SOURCE) {
      tcmSyndromes.push(row);
      continue;
    }
    western.push(row);
  }

  let filled = false;

  if (variant === 'wm') {
    // 西医首页双列：先铺满左列（主要+其他），溢出到右列其他诊断
    if (western[0]) {
      out.mainDiagName = dash(western[0].diagName);
      out.mainDiagCode = dash(western[0].diagCode);
      out.mainAdmCond = dash(western[0].admCondCode);
      filled = true;
    }
    let wi = 1;
    for (let i = 1; i < DIAG_ROW_COUNT; i += 1) {
      const row = western[wi];
      if (!row) {
        break;
      }
      out[`othDiagNameL${i}`] = dash(row.diagName);
      out[`othDiagCodeL${i}`] = dash(row.diagCode);
      out[`othAdmCondL${i}`] = dash(row.admCondCode);
      wi += 1;
      filled = true;
    }
    for (let i = 0; i < DIAG_ROW_COUNT; i += 1) {
      const row = western[wi];
      if (!row) {
        break;
      }
      out[`othDiagNameR${i}`] = dash(row.diagName);
      out[`othDiagCodeR${i}`] = dash(row.diagCode);
      out[`othAdmCondR${i}`] = dash(row.admCondCode);
      wi += 1;
      filled = true;
    }
    if (!filled) {
      out.mainDiagName = EMPTY;
      out.mainDiagCode = EMPTY;
      out.mainAdmCond = EMPTY;
    }
    return out;
  }

  // 中医首页：左列固定位主病/主证，右列西医
  if (tcmDiseases[0]) {
    out.mainDiagName = dash(tcmDiseases[0].diagName);
    out.mainDiagCode = dash(tcmDiseases[0].diagCode);
    out.mainAdmCond = dash(tcmDiseases[0].admCondCode);
    filled = true;
  }
  if (tcmSyndromes[0]) {
    out.othDiagNameL1 = dash(tcmSyndromes[0].diagName);
    out.othDiagCodeL1 = dash(tcmSyndromes[0].diagCode);
    out.othAdmCondL1 = dash(tcmSyndromes[0].admCondCode);
    filled = true;
  }
  const leftExtra = [...tcmDiseases.slice(1), ...tcmSyndromes.slice(1)];
  for (let i = 0; i < leftExtra.length; i += 1) {
    const slot = i + 2;
    if (slot >= DIAG_ROW_COUNT) {
      break;
    }
    const row = leftExtra[i];
    out[`othDiagNameL${slot}`] = dash(row.diagName);
    out[`othDiagCodeL${slot}`] = dash(row.diagCode);
    out[`othAdmCondL${slot}`] = dash(row.admCondCode);
    filled = true;
  }

  for (let i = 0; i < DIAG_ROW_COUNT; i += 1) {
    const row = western[i];
    if (!row) {
      break;
    }
    out[`othDiagNameR${i}`] = dash(row.diagName);
    out[`othDiagCodeR${i}`] = dash(row.diagCode);
    out[`othAdmCondR${i}`] = dash(row.admCondCode);
    filled = true;
  }
  if (!filled) {
    out.mainDiagName = EMPTY;
    out.mainDiagCode = EMPTY;
    out.mainAdmCond = EMPTY;
  }
  return out;
}

/** 切口愈合两码合一（模板单列） */
function incisionHealText(row: ProcedureLineDto): string {
  const incision = str(row.oprnIncisionCode);
  const heal = str(row.healGradeCode);
  if (incision && heal) {
    return `${incision}/${heal}`;
  }
  return incision || heal;
}

/**
 * 手术表最多 8 行：无手术仅首行横杠；有手术则只填有数据的行，下方空白行不印「-」。
 * （Ⅰ助/Ⅱ助详情无字段，有手术行时助栏仍印横杠）
 */
function buildOpFields(rows: ProcedureLineDto[]): Record<string, string> {
  const sorted = [...rows]
    .filter((row) => Boolean(str(row.oprnOprtCode) || str(row.oprnOprtName)))
    .sort((a, b) => (a.sortNo ?? 0) - (b.sortNo ?? 0));
  const out: Record<string, string> = {};
  const fillCount = sorted.length > 0 ? Math.min(sorted.length, OP_ROW_COUNT) : 1;
  for (let i = 0; i < OP_ROW_COUNT; i += 1) {
    if (i >= fillCount) {
      out[`opCode${i}`] = '';
      out[`opDate${i}`] = '';
      out[`opLevel${i}`] = '';
      out[`opName${i}`] = '';
      out[`opSurgeon${i}`] = '';
      out[`opAssist1_${i}`] = '';
      out[`opAssist2_${i}`] = '';
      out[`opIncisionHeal${i}`] = '';
      out[`opAnesth${i}`] = '';
      out[`opAnesthDr${i}`] = '';
      continue;
    }
    const row = sorted[i];
    if (!row) {
      out[`opCode${i}`] = EMPTY;
      out[`opDate${i}`] = EMPTY;
      out[`opLevel${i}`] = EMPTY;
      out[`opName${i}`] = EMPTY;
      out[`opSurgeon${i}`] = EMPTY;
      out[`opAssist1_${i}`] = EMPTY;
      out[`opAssist2_${i}`] = EMPTY;
      out[`opIncisionHeal${i}`] = EMPTY;
      out[`opAnesth${i}`] = EMPTY;
      out[`opAnesthDr${i}`] = EMPTY;
      continue;
    }
    const dateText = str(row.oprnDate);
    const parsed =
      dateText && dayjs(dateText).isValid() ? dayjs(dateText).format('YYYY-MM-DD') : '';
    out[`opCode${i}`] = dash(row.oprnOprtCode);
    out[`opDate${i}`] = dash(parsed);
    out[`opLevel${i}`] = dash(row.oprnLevelCode);
    out[`opName${i}`] = dash(row.oprnOprtName);
    out[`opSurgeon${i}`] = dash(row.operDrName);
    out[`opAssist1_${i}`] = EMPTY;
    out[`opAssist2_${i}`] = EMPTY;
    out[`opIncisionHeal${i}`] = dash(incisionHealText(row));
    out[`opAnesth${i}`] = dash(row.anstWayCode);
    out[`opAnesthDr${i}`] = dash(row.anstDrName);
  }
  return out;
}

/** 详情表单 → 住院病案首页 PrintData（中医/西医共用底稿；中医模板另绑五 Mark） */
export function buildInpatientHomepagePrintData(
  source: InpatientHomepagePrintSource,
): InpatientHomepagePrintData {
  const form = source.form;
  const birth = dateParts(form.brdy);
  const adm = dateParts(form.admTime);
  const dscg = dateParts(form.dscgTime);
  const qc = dateParts(form.qltctrlDate);
  const birthPlace = splitRegion(form.birplcCodes);
  const nativePlace = splitRegion(form.naplCodes);
  const currAddr = splitRegion(form.currAddrCodes);
  const regAddr = splitRegion(form.resdAddrCodes);
  const diagnoses = form.diagnoses ?? [];
  const injury = findSpecialDiag(diagnoses, 'injury_poison');
  const pathology = findSpecialDiag(diagnoses, 'pathology');
  const comaBf = parseDuration(form.brnDamgBfadmComaDura);
  const comaAf = parseDuration(form.brnDamgAfadmComaDura);
  const allergyCode = str(form.drugAllergyCode);
  // 现住址/户口地址：只取表单省市区级联三栏，不拼详址

  return {
    hospitalName: dash(source.hospitalName),
    orgCode: dash(source.orgCode),
    payMethodMark: mark(form.payWayCode),
    healthCardNo: dash(form.healthCardNo),
    iptCount: dash(form.visitTimes),
    medcasno: dash(form.medcasno),
    psnName: dash(form.psnName),
    gendMark: mark(genderMark(form.gendCode)),
    birthY: dash(birth.y),
    birthM: dash(birth.m),
    birthD: dash(birth.d),
    ageText: dash(form.ageYear),
    nationality: dash(optionLabel(NTLY_OPTIONS, form.ntlyCode)),
    ageMonth: dash(form.ageMonth),
    birthWeight: EMPTY,
    admitWeight: EMPTY,
    birthProv: dash(birthPlace.prov),
    birthCity: dash(birthPlace.city),
    birthCounty: dash(birthPlace.county),
    nativeProv: dash(nativePlace.prov),
    nativeCity: dash(nativePlace.city),
    nation: dash(optionLabel(NATY_OPTIONS, form.natyCode)),
    certno: dash(form.certno),
    occupation: dash(optionLabel(PRFS_OPTIONS, form.prfsCode)),
    marriageMark: mark(form.mrgStasCode),
    currProv: dash(currAddr.prov),
    currCity: dash(currAddr.city),
    currCounty: dash(currAddr.county),
    psnTel: dash(form.psnTel),
    currZip: dash(form.currAddrPoscode),
    regProv: dash(regAddr.prov),
    regCity: dash(regAddr.city),
    regCounty: dash(regAddr.county),
    regZip: dash(form.resdAddrPoscode),
    workAddr: dash(form.emprAddr),
    workTel: dash(form.emprTel),
    workZip: dash(form.emprPoscode),
    conerName: dash(form.conerName),
    conerRlts: dash(optionLabel(CONER_RLTS_OPTIONS, form.conerRltsCode)),
    conerAddr: dash(form.conerAddr),
    conerTel: dash(form.conerTel),
    admWayMark: mark(form.admWayCode),
    trtTypeMark: mark(form.trtTypeCode),
    admY: dash(adm.y),
    admM: dash(adm.m),
    admD: dash(adm.d),
    admH: dash(adm.h),
    admCatyName: dash(form.admCatyName),
    admBed: dash(form.admBed),
    transferDeptSummary: dash(form.transferDeptSummary),
    dscgY: dash(dscg.y),
    dscgM: dash(dscg.m),
    dscgD: dash(dscg.d),
    dscgH: dash(dscg.h),
    dscgCatyName: dash(form.dscgCatyName),
    dscgBed: dash(form.dscgBed),
    iptDays: dash(form.iptDays),
    otpTcmDise: dash(form.otpTcmDise),
    tcmDiseCode: dash(form.tcmDiseCode),
    otpWmDise: dash(form.otpWmDise),
    wmDiseCode: dash(form.wmDiseCode),
    clinicalPathMark: mark(form.clinicalPathCode),
    tcmPrepMark: mark(form.tcmPrepCode),
    tcmDeviceMark: mark(form.tcmDeviceCode),
    tcmTechMark: mark(form.tcmTechCode),
    syndromeNurseMark: mark(form.syndromeNurseCode),
    ...buildDiagFields(pickTableDiagnoses(form), source.variant === 'wm' ? 'wm' : 'tcm'),
    injPoisonName: dash(injury?.diagName),
    injPoisonCode: dash(injury?.diagCode),
    pathologyName: dash(pathology?.diagName),
    pathologyCode: dash(pathology?.diagCode),
    pathologyNo: dash(form.pathologyNo),
    allergyMark: mark(allergyCode),
    allergyDrug: allergyCode === '2' ? dash(form.allergyDrugName) : EMPTY,
    autopsyMark: mark(form.autopsyCode),
    bloodTypeMark: mark(form.aboCode),
    rhMark: mark(form.rhCode),
    deptDirectorName: dash(form.deptDirectorName),
    chfdrName: dash(form.chfdrName),
    atddrName: dash(form.atddrName),
    iptDrName: dash(form.iptDrName),
    respNursName: dash(form.respNursName),
    refresherDrName: dash(form.trainDrName),
    internDrName: dash(form.intnDrName),
    coderName: dash(form.codrName),
    qualityMark: mark(form.medcasQltCode),
    qcDoctorName: dash(form.qltctrlDrName),
    qcNurseName: dash(form.qltctrlNursName),
    qcY: dash(qc.y),
    qcM: dash(qc.m),
    qcD: dash(qc.d),
    dscgWayMark: mark(form.dscgWayCode),
    transferHosp1: dash(form.acpMedinsName),
    transferHosp2: dash(form.acpCommunityName),
    readmitMark: mark(form.rinp31Flag),
    readmitPurpose: str(form.rinp31Flag) === '2' ? dash(form.rinp31Purpose) : EMPTY,
    comaBfDay: dash(comaBf.day),
    comaBfHour: dash(comaBf.hour),
    comaBfMin: dash(comaBf.min),
    comaAfDay: dash(comaAf.day),
    comaAfHour: dash(comaAf.hour),
    comaAfMin: dash(comaAf.min),
    // 费用：首页快照 4401 键 → 模板 fee1～fee24（中医/西医共用；无纸质子项）
    medfeeSumamt: dashAmount(form.medfee_sumamt),
    selfPayAmt: dashAmount(form.selfpay_amt),
    fee1: dashAmount(form.ordn_med_servfee),
    fee2: dashAmount(form.ordn_trt_oprt_fee),
    fee3: dashAmount(form.nurs_fee),
    fee4: dashAmount(form.com_med_serv_oth_fee),
    fee5: dashAmount(form.palg_diag_fee),
    fee6: dashAmount(form.lab_diag_fee),
    fee7: dashAmount(form.rdhy_diag_fee),
    fee8: dashAmount(form.clnc_dise_fee),
    fee9: dashAmount(form.nsrgtrt_item_fee),
    fee9a: dashAmount(form.clnc_phys_trt_fee),
    fee10: dashAmount(form.rgtrt_trt_fee),
    fee10a: dashAmount(form.anst_fee),
    fee10b: dashAmount(form.rgtrt_fee),
    fee11: dashAmount(form.rhab_fee),
    fee12: dashAmount(form.tcm_trt_fee),
    fee13: dashAmount(form.wm_fee),
    fee13a: dashAmount(form.abtl_medn_fee),
    fee14: dashAmount(form.tcmpat_fee),
    fee15: dashAmount(form.tcmherb_fee),
    fee16: dashAmount(form.blo_fee),
    fee17: dashAmount(form.albu_fee),
    fee18: dashAmount(form.glon_fee),
    fee19: dashAmount(form.clotfac_fee),
    fee20: dashAmount(form.cyki_fee),
    fee21: dashAmount(form.exam_dspo_matl_fee),
    fee22: dashAmount(form.trt_dspo_matl_fee),
    fee23: dashAmount(form.oprn_dspo_matl_fee),
    fee24: dashAmount(form.oth_fee),
    ...buildOpFields(form.procedures ?? []),
    // 中医信息：定稿模板 field 名不改；*Mark 与正式列 camelCase 双写同值
    clinicalPathMark: str(form.clinicalPathCode),
    tcmPrepMark: str(form.tcmPrepCode),
    tcmDeviceMark: str(form.tcmDeviceCode),
    tcmTechMark: str(form.tcmTechCode),
    syndromeNurseMark: str(form.syndromeNurseCode),
    clinicalPathCode: str(form.clinicalPathCode),
    tcmPrepCode: str(form.tcmPrepCode),
    tcmDeviceCode: str(form.tcmDeviceCode),
    tcmTechCode: str(form.tcmTechCode),
    syndromeNurseCode: str(form.syndromeNurseCode),
  };
}

/**
 * 西医住院病案首页 PrintData（ip-homepage-wm-print）：中医五字段置空。
 *
 * @param source 同 {@link buildInpatientHomepagePrintData}；会强制 variant=wm
 */
export function buildInpatientHomepageWmPrintData(
  source: InpatientHomepagePrintSource,
): InpatientHomepagePrintData {
  const data = buildInpatientHomepagePrintData({ ...source, variant: 'wm' });
  data.clinicalPathMark = '';
  data.tcmPrepMark = '';
  data.tcmDeviceMark = '';
  data.tcmTechMark = '';
  data.syndromeNurseMark = '';
  data.clinicalPathCode = '';
  data.tcmPrepCode = '';
  data.tcmDeviceCode = '';
  data.tcmTechCode = '';
  data.syndromeNurseCode = '';
  return data;
}
