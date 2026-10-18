/**
 * 病案首页预览 JSON → 审批对比用的「标签：值」行。
 * 按编辑页章节出标量、诊断行、手术行；费用不参与归档对比。
 */

import type { DiagnosisLineDto, HomepageDto, ProcedureLineDto } from '#/api/mrms/types';

import {
  ABO_OPTIONS,
  ADM_COND_OPTIONS,
  ADM_WAY_OPTIONS,
  CLINICAL_PATH_OPTIONS,
  CLINIC_PATH_MANAGE_OPTIONS,
  CONER_RLTS_OPTIONS,
  DIAG_MATCH_OPTIONS,
  DSCG_WAY_OPTIONS,
  GENDER_OPTIONS,
  HOMEPAGE_DIAG_TYPES,
  MAIN_DIAG_CURE_OPTIONS,
  MEDCAS_QLT_OPTIONS,
  MRG_STAS_OPTIONS,
  NATY_OPTIONS,
  NONE_HAS_OPTIONS,
  NTLY_OPTIONS,
  OUT_ILL_STATUS_OPTIONS,
  PAY_WAY_OPTIONS,
  PRFS_OPTIONS,
  RH_OPTIONS,
  TRT_TYPE_OPTIONS,
  YES_NO_12_OPTIONS,
} from '../mrms.constants';

type Option = { label: string; value: string | number };

type ScalarField = {
  key: keyof HomepageDto;
  label: string;
  options?: Option[];
  /** 主键为空时改读这一列（出生地/籍贯：中文名优先，否则省市区码） */
  fallbackKey?: keyof HomepageDto;
};

const INPATIENT_FIELDS: ScalarField[] = [
  { key: 'payWayCode', label: '付费方式', options: PAY_WAY_OPTIONS },
  { key: 'healthCardNo', label: '健康卡号' },
  { key: 'medcasno', label: '病案号' },
  { key: 'psnName', label: '姓名' },
  { key: 'gendCode', label: '性别', options: GENDER_OPTIONS },
  { key: 'brdy', label: '出生日期' },
  { key: 'ageYear', label: '年龄（岁）' },
  { key: 'ageMonth', label: '年龄（月）' },
  { key: 'ntlyCode', label: '国籍', options: NTLY_OPTIONS },
  { key: 'natyCode', label: '民族', options: NATY_OPTIONS },
  { key: 'birplcName', label: '出生地', fallbackKey: 'birplcCodes' },
  { key: 'naplName', label: '籍贯', fallbackKey: 'naplCodes' },
  { key: 'mrgStasCode', label: '婚姻', options: MRG_STAS_OPTIONS },
  { key: 'certno', label: '身份证号' },
  { key: 'prfsCode', label: '职业', options: PRFS_OPTIONS },
  { key: 'psnTel', label: '本人电话' },
  { key: 'currAddrCodes', label: '现住址' },
  { key: 'currAddrDetail', label: '现住址详细地址' },
  { key: 'currAddrPoscode', label: '现住址邮编' },
  { key: 'resdAddrCodes', label: '户口地址' },
  { key: 'resdAddrDetail', label: '户口详细地址' },
  { key: 'resdAddrPoscode', label: '户口邮编' },
  { key: 'emprAddr', label: '工作单位' },
  { key: 'emprTel', label: '单位电话' },
  { key: 'emprPoscode', label: '单位邮编' },
  { key: 'conerName', label: '联系人' },
  { key: 'conerRltsCode', label: '关系', options: CONER_RLTS_OPTIONS },
  { key: 'conerAddr', label: '联系地址' },
  { key: 'conerTel', label: '联系电话' },
  { key: 'clinicalPathCode', label: '实施临床路径', options: CLINICAL_PATH_OPTIONS },
  { key: 'tcmPrepCode', label: '中药制剂', options: YES_NO_12_OPTIONS },
  { key: 'tcmDeviceCode', label: '中医诊疗设备', options: YES_NO_12_OPTIONS },
  { key: 'tcmTechCode', label: '中医诊疗技术', options: YES_NO_12_OPTIONS },
  { key: 'syndromeNurseCode', label: '辨证施护', options: YES_NO_12_OPTIONS },
  { key: 'admWayCode', label: '入院途径', options: ADM_WAY_OPTIONS },
  { key: 'trtTypeCode', label: '治疗类别', options: TRT_TYPE_OPTIONS },
  { key: 'iptNo', label: '住院号' },
  { key: 'iptDays', label: '住院天数' },
  { key: 'admTime', label: '入院时间' },
  { key: 'admCatyName', label: '入院科室' },
  { key: 'admWard', label: '入院病区' },
  { key: 'admBed', label: '入院床位' },
  { key: 'dscgTime', label: '出院时间' },
  { key: 'dscgCatyName', label: '出院科室' },
  { key: 'dscgWard', label: '出院病区' },
  { key: 'dscgBed', label: '出院床位' },
  { key: 'transferDeptSummary', label: '转科科别' },
  { key: 'spgaNurscareDays', label: '特级护理天数' },
  { key: 'lv1NurscareDays', label: '一级护理天数' },
  { key: 'scdNurscareDays', label: '二级护理天数' },
  { key: 'lv3NurscareDays', label: '三级护理天数' },
];

const DIAGNOSIS_FIELDS: ScalarField[] = [
  { key: 'otpWmDise', label: '门（急）诊诊断（西医）' },
  { key: 'wmDiseCode', label: '门急诊西医编码' },
  { key: 'otpTcmDise', label: '门（急）诊诊断（中医）' },
  { key: 'tcmDiseCode', label: '中医疾病编码' },
  { key: 'mainDiagCureCode', label: '主要诊断治愈好转', options: MAIN_DIAG_CURE_OPTIONS },
  { key: 'rescueCount', label: '抢救次数' },
  { key: 'rescueSuccessCount', label: '抢救成功次数' },
  { key: 'pathologyNo', label: '病理号' },
  { key: 'clinicPathManageCode', label: '临床路径管理', options: CLINIC_PATH_MANAGE_OPTIONS },
  { key: 'diagMatchOpDscg', label: '门诊与出院', options: DIAG_MATCH_OPTIONS },
  { key: 'diagMatchAdmDscg', label: '入院与出院', options: DIAG_MATCH_OPTIONS },
  { key: 'diagMatchPrePost', label: '术前与术后', options: DIAG_MATCH_OPTIONS },
  { key: 'diagMatchClinicPath', label: '临床与病理', options: DIAG_MATCH_OPTIONS },
  { key: 'diagMatchRadioPath', label: '放射与病理', options: DIAG_MATCH_OPTIONS },
];

const APPENDIX_FIELDS: ScalarField[] = [
  { key: 'drugAllergyCode', label: '药物过敏', options: NONE_HAS_OPTIONS },
  { key: 'allergyDrugName', label: '过敏药物' },
  { key: 'brnDamgBfadmComaDura', label: '入院前昏迷' },
  { key: 'brnDamgAfadmComaDura', label: '入院后昏迷' },
  { key: 'ventUsedDura', label: '呼吸机时长' },
  { key: 'autopsyCode', label: '死亡尸检', options: YES_NO_12_OPTIONS },
  { key: 'aboCode', label: '血型', options: ABO_OPTIONS },
  { key: 'rhCode', label: 'Rh', options: RH_OPTIONS },
  { key: 'deptDirectorName', label: '科主任' },
  { key: 'chfdrName', label: '主任医师' },
  { key: 'atddrName', label: '主治医师' },
  { key: 'iptDrName', label: '住院医师' },
  { key: 'respNursName', label: '责任护士' },
  { key: 'trainDrName', label: '进修医师' },
  { key: 'intnDrName', label: '实习医师' },
  { key: 'codrName', label: '编码员' },
  { key: 'medcasQltCode', label: '病案质量', options: MEDCAS_QLT_OPTIONS },
  { key: 'qltctrlDate', label: '质控日期' },
  { key: 'qltctrlDrName', label: '质控医师' },
  { key: 'qltctrlNursName', label: '质控护士' },
  { key: 'dscgWayCode', label: '离院方式', options: DSCG_WAY_OPTIONS },
  { key: 'acpMedinsName', label: '接收医院' },
  { key: 'acpCommunityName', label: '接收社区' },
  { key: 'rinp31Flag', label: '31天再住院', options: NONE_HAS_OPTIONS },
  { key: 'rinp31Purpose', label: '再住院目的' },
];

const DIAG_TYPE_LABEL: Record<string, string> = Object.fromEntries(
  HOMEPAGE_DIAG_TYPES.map((item) => [item.code, item.label]),
);

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

/** 解析首页预览 JSON */
function parseHomepage(raw: unknown): HomepageDto | null {
  if (raw == null) return null;
  if (isRecord(raw)) return raw as HomepageDto;
  if (typeof raw !== 'string' || !raw.trim()) return null;
  try {
    const parsed: unknown = JSON.parse(raw);
    return isRecord(parsed) ? (parsed as HomepageDto) : null;
  } catch {
    return null;
  }
}

function optionLabel(options: Option[] | undefined, code: string): string {
  if (!options) return code;
  const hit = options.find((item) => String(item.value) === code);
  return hit ? String(hit.label) : code;
}

function textOf(value: unknown): string {
  if (value == null) return '';
  if (Array.isArray(value)) {
    return value
      .map((item) => (item == null ? '' : String(item).trim()))
      .filter(Boolean)
      .join('/');
  }
  return String(value).trim();
}

/** 标量展示值：中文名优先，字典码转文案 */
function scalarText(dto: HomepageDto, field: ScalarField): string {
  const own = textOf(dto[field.key]);
  const fallback = field.fallbackKey ? textOf(dto[field.fallbackKey]) : '';
  const value = own || fallback;
  if (!value) return '';
  if (own && field.options) return optionLabel(field.options, own);
  return value;
}

function pushSection(lines: string[], title: string, body: string[]) {
  if (!body.length) return;
  lines.push(`【${title}】`);
  lines.push(...body);
}

function pushScalars(dto: HomepageDto, fields: ScalarField[], out: string[]) {
  for (const field of fields) {
    const value = scalarText(dto, field);
    if (!value) continue;
    out.push(`${field.label}：${value}`);
  }
}

function diagnosisLine(row: DiagnosisLineDto, index: number): string {
  const typeLabel = DIAG_TYPE_LABEL[String(row.diagTypeCode ?? '')] || `诊断${index + 1}`;
  const parts = [textOf(row.diagCode), textOf(row.diagName)].filter(Boolean);
  const cond = textOf(row.admCondCode);
  if (cond) parts.push(optionLabel(ADM_COND_OPTIONS, cond));
  const outcome = textOf(row.outIllStatusCode);
  if (outcome) parts.push(optionLabel(OUT_ILL_STATUS_OPTIONS, outcome));
  const doctor = textOf(row.confirmDrName);
  if (doctor) parts.push(doctor);
  return `${typeLabel}：${parts.join('  ')}`;
}

function procedureLine(row: ProcedureLineDto, index: number): string {
  const parts = [textOf(row.oprnOprtCode), textOf(row.oprnOprtName), textOf(row.oprnTime || row.oprnDate)].filter(
    Boolean,
  );
  const surgeon = textOf(row.operDrName);
  if (surgeon) parts.push(surgeon);
  if (row.mainOprnFlag === '1') parts.push('主手术');
  const title = parts.length ? parts.join('  ') : '（空）';
  return `手术${index + 1}：${title}`;
}

/**
 * 首页预览 → 对比正文。空字段省略；费用整章省略。
 */
export function extractHomepageCompareText(raw: unknown): string {
  const dto = parseHomepage(raw);
  if (!dto) return '';
  const lines: string[] = [];
  const inpatient: string[] = [];
  pushScalars(dto, INPATIENT_FIELDS, inpatient);
  pushSection(lines, '住院信息', inpatient);

  const diagnosis: string[] = [];
  pushScalars(dto, DIAGNOSIS_FIELDS, diagnosis);
  const diagRows = Array.isArray(dto.diagnoses) ? dto.diagnoses : [];
  diagRows.forEach((row, index) => {
    const line = diagnosisLine(row, index);
    if (line.endsWith('：')) return;
    diagnosis.push(line);
  });
  pushSection(lines, '诊断记录', diagnosis);

  const procedures: string[] = [];
  const oprnRows = Array.isArray(dto.procedures) ? dto.procedures : [];
  oprnRows.forEach((row, index) => {
    procedures.push(procedureLine(row, index));
  });
  pushSection(lines, '手术/操作记录', procedures);

  const appendix: string[] = [];
  pushScalars(dto, APPENDIX_FIELDS, appendix);
  pushSection(lines, '病案附页', appendix);
  return lines.join('\n');
}
