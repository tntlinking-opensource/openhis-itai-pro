import type { ApiOptionItem, PageParams } from '#/types/api-response';

import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { preserveApiIdForRequest } from '#/utils/preserve-api-id';

import { publicRequestClient } from '../request';

/** 患者列表项 / 详情（详情含 patientIdInfoList 其他证件列表） */
export interface Patient {
  activeFlag: boolean;
  activeFlagText?: string;
  address?: string;
  addressCity?: string;
  addressDistrict?: string;
  addressProvince?: string;
  addressStreet?: string;
  id: string;
  name: string;
  age: null | number;
  birthDate: null | string;
  genderEnum: string;
  genderEnumText?: string;
  phone?: string;
  linkName?: string;
  linkTelcom?: string;
  workCompany?: string;
  idCard?: string;
  nationalityCode?: string;
  bloodAbo?: number;
  bloodAboText?: null | string;
  bloodRh?: number;
  bloodRhText?: null | string;
  maritalStatusEnum?: number;
  maritalStatusEnumText?: null | string;
  prfsEnum?: null | number;
  prfsEnumText?: null | string;
  linkRelationCode?: number;
  linkRelationCodeText?: null | string;
  busNo?: string;
  createTime?: string;
  deceasedDate?: null | string;
  pyStr?: null | string;
  wbStr?: null | string;
  /** 其他证件/标识列表（身份证以外，来自 adm_patient_identifier），编辑回显时由 getPatient 返回 */
  patientIdInfoList?: PatientIdInfo[];
}

export interface GetPatientPageParams extends PageParams {
  /** 检索关键字（模糊匹配姓名、病历号、拼音、证件号） */
  keyword: string;
}

/** 患者管理页初始化返回：性别、婚姻、职业、血型、证件类型等下拉选项 */
export interface PatientInitResponse {
  maritalStatus?: ApiOptionItem[];
  occupationType?: ApiOptionItem[];
  sex?: ApiOptionItem[];
  bloodTypeABO?: ApiOptionItem[];
  bloodTypeRH?: ApiOptionItem[];
  familyRelationshipType?: ApiOptionItem[];
  /** 证件类型（字典 psn_cert_type / IdentityDocumentType），用于「其他证件」下拉，排除身份证即为其他证件类型 */
  identityDocumentType?: ApiOptionItem[];
  whetherStatus?: ApiOptionItem[];
  /** 学籍-学历层次 */
  educationLevelOptions?: ApiOptionItem[];
  /** 学籍-学习形式 */
  studyModeOptions?: ApiOptionItem[];
  /** 学籍-在校状态 */
  studentStatusOptions?: ApiOptionItem[];
  /** 学籍-体检结果 */
  physicalExamResultOptions?: ApiOptionItem[];
  /** 学生开关配置值（等于 "1" 时显示学籍信息按钮） */
  studentSwitchValue?: string;
}

/** 患者学籍（adm_patient_student，与患者一对一） */
export interface PatientStudentDto {
  /** 学籍主键，无记录时为空 */
  id?: string;
  patientId: string;
  /** 姓名（来自患者） */
  name: string;
  gender: number;
  idNumber: string;
  pyStr?: string;
  wbStr?: string;
  age?: null | number;
  studentId?: string;
  phone?: string;
  college?: string;
  major?: string;
  educationLevel?: null | number;
  enrollmentDate?: string;
  graduationDate?: string;
  grade?: string;
  studyMode?: null | number;
  studentStatus?: null | number;
  physicalExamResult?: null | number;
  counselor?: string;
  counselorPhone?: string;
}

export interface SavePatientStudentParams {
  patientId: string;
  age?: null | number;
  studentId?: string;
  phone?: string;
  college?: string;
  major?: string;
  educationLevel?: null | number;
  enrollmentDate?: string;
  graduationDate?: string;
  grade?: string;
  studyMode?: null | number;
  studentStatus?: null | number;
  physicalExamResult?: null | number;
  counselor?: string;
  counselorPhone?: string;
}

export async function getPatientListApi(params: GetPatientPageParams) {
  const data = await publicRequestClient.post<unknown>('/patient/getPatientPage', params);
  return parsePagedRowsFromApiData<Patient>(data);
}

/** 按主键获取患者详情（含其他证件列表 patientIdInfoList），用于编辑回显 */
export async function getPatientApi(id: number | string) {
  return publicRequestClient.post<Patient>('/patient/getPatient', preserveApiIdForRequest(id));
}

/** 患者管理页初始化：拉取性别、婚姻、职业、血型、证件类型等枚举选项 */
export async function patientInitApi() {
  return publicRequestClient.post<PatientInitResponse>('/patient/init');
}

/** 患者其他证件/标识单条（对应 adm_patient_identifier 一行，身份证以外的证件） */
export interface PatientIdInfo {
  id?: number;
  /** 标识用途：1常用 2正式 3临时 4次要 5旧 */
  useEnum?: number;
  /** 证件类型编码（与 identityDocumentType 的 value 一致，如 "2" 户口簿、"3" 护照） */
  typeCode: string;
  /** 证件/标识号 */
  identifierNo: string;
  /** 有效开始时间，格式 yyyy-MM-dd HH:mm:ss */
  startTime?: string;
  /** 有效结束时间，格式 yyyy-MM-dd HH:mm:ss */
  endTime?: string;
}

export interface CreatePatientParams {
  /** 基本信息 */
  name: string;
  genderEnum?: string;
  age?: number;

  /** 联系方式 */
  phone?: string;

  /** 证件与国籍 */
  idCard?: string;
  nationalityCode?: string;

  /** 工作单位 */
  workCompany?: string;

  /** 地址相关 */
  addressProvince?: string;
  addressCity?: string;
  addressDistrict?: string;
  addressStreet?: string;
  address?: string;

  /** 联系人信息 */
  linkName?: string;
  linkTelcom?: string;
  linkRelationCode?: number;

  /** 其他可选字段（后端有就传，没有就不传） */
  maritalStatusEnum?: number;
  bloodRh?: number;
  bloodAbo?: number;
  prfsEnum?: number;
  deceasedDate?: string;
  birthDate?: string;
  activeFlag?: boolean;
  patientIdInfoList?: PatientIdInfo[];
}

export interface EditPatientParams extends CreatePatientParams {
  id: string;
}

export async function createPatientApi(params: CreatePatientParams) {
  return publicRequestClient.post('/patient/createPatient', params);
}

export async function editPatientApi(params: EditPatientParams) {
  return publicRequestClient.post('/patient/editPatient', params);
}

export async function deletePatientApi(id: number | string) {
  return publicRequestClient.post('/patient/deletePatient', preserveApiIdForRequest(id));
}

/** 学籍回显（需患者已维护证件号） */
export async function getPatientStudentApi(patientId: number | string) {
  return publicRequestClient.post<PatientStudentDto>(
    '/patient/getPatientStudent',
    preserveApiIdForRequest(patientId),
  );
}

export async function savePatientStudentApi(params: SavePatientStudentParams) {
  return publicRequestClient.post<PatientStudentDto>('/patient/savePatientStudent', params);
}

export async function deletePatientStudentApi(patientId: number | string) {
  return publicRequestClient.post(
    '/patient/deletePatientStudent',
    preserveApiIdForRequest(patientId),
  );
}
