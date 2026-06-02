/**
 * 急诊分诊：类型与数据访问。
 * 后端 Controller 就绪后，将下列 *Api 函数改为 requestClient 调用（路径建议 /emg/triage/**）。
 */
import type { PageApiResponse, PageParams } from '#/types/api-response';

/** 下拉选项 */
export interface SelectOptionDto {
  value: string;
  label: string;
  code?: null | string;
}

export interface InitResult {
  triageLevelOptions: SelectOptionDto[];
  triageAreaOptions: SelectOptionDto[];
  triageStatusOptions: SelectOptionDto[];
  assessmentTypeOptions: SelectOptionDto[];
}

export interface PatientSearchRowDto {
  id: number;
  busNo?: null | string;
  name?: null | string;
  /** 姓名拼音简码，检索用 */
  pinyinCode?: null | string;
  /** 姓名五笔简码，检索用 */
  wubiCode?: null | string;
  gender?: null | string;
  age?: null | number;
  idCard?: null | string;
  /** 出生日期 YYYY-MM-DD，列表展示用；无则可由身份证推算 */
  birthDate?: null | string;
  phone?: null | string;
}

export interface OrganizationOptionDto {
  id: number;
  busNo?: null | string;
  name?: null | string;
  classEnum?: null | number;
  typeEnum?: null | number;
}

export interface PageQueueParams extends PageParams {
  statusEnum?: null | number;
  triageLevelEnum?: null | number;
  organizationId?: null | number;
  triageTimeFrom?: null | string;
  triageTimeTo?: null | string;
}

/** 分诊队列行（与后端分诊读模型字段对齐） */
export interface QueueRow {
  id: number;
  patientName?: null | string;
  /** 年龄可展示为数字或如「2月」「3岁零5个月」等 */
  patientAge?: null | number | string;
  patientGender?: null | string;
  triageLevelEnum?: null | number;
  triageLevelName?: null | string;
  triageTime?: null | string;
  chiefComplaint?: null | string;
  organizationName?: null | string;
  locationName?: null | string;
  statusEnum?: null | number;
  statusName?: null | string;
  waitMinutes?: null | number;
  encounterId?: null | number;
}

export function defaultInit(): InitResult {
  return {
    triageLevelOptions: [
      { value: '1', label: '一级（危急）', code: 'LEVEL_1' },
      { value: '2', label: '二级（急重）', code: 'LEVEL_2' },
      { value: '3', label: '三级（急症）', code: 'LEVEL_3' },
      { value: '4', label: '四级（亚急症）', code: 'LEVEL_4' },
    ],
    triageAreaOptions: [
      { value: '1', label: '抢救区', code: 'RESCUE' },
      { value: '2', label: '急诊区', code: 'EMERGENCY' },
      { value: '3', label: '观察区', code: 'OBSERVATION' },
      { value: '4', label: '普通区', code: 'GENERAL' },
    ],
    triageStatusOptions: [
      { value: '1', label: '待就诊', code: 'WAITING' },
      { value: '2', label: '就诊中', code: 'TREATING' },
      { value: '3', label: '已就诊', code: 'TREATED' },
      { value: '4', label: '已重新分诊', code: 'REASSIGNED' },
      { value: '5', label: '已修改', code: 'MODIFIED' },
    ],
    assessmentTypeOptions: [
      { value: 'PAIN', label: '疼痛评估' },
      { value: 'TRAUMA', label: '创伤评估' },
      { value: 'GCS', label: 'GCS' },
    ],
  };
}

const MOCK_PATIENTS: PatientSearchRowDto[] = [
  {
    id: 1001,
    busNo: 'MZ20260001',
    name: '张三',
    pinyinCode: 'zs',
    wubiCode: 'xd',
    gender: '男',
    age: 35,
    idCard: '110101199001011234',
    birthDate: '1990-01-01',
    phone: '13800138000',
  },
  {
    id: 1002,
    busNo: 'MZ20260002',
    name: '李四',
    pinyinCode: 'ls',
    wubiCode: 'sa',
    gender: '女',
    age: 40,
    idCard: '310101198505051111',
    birthDate: '1985-05-05',
    phone: '13900139000',
  },
];

/** 按姓名/拼音/五笔/身份证/病历号/电话 匹配（演示 mock；对接接口后由后端分词/检索） */
export function mockSearchPatients(keyword: string): PatientSearchRowDto[] {
  const raw = keyword.trim();
  if (!raw) {
    return [...MOCK_PATIENTS];
  }
  const k = raw.toLowerCase();
  return MOCK_PATIENTS.filter((p) => {
    const nameHit = (p.name && (p.name.includes(raw) || p.name.toLowerCase().includes(k))) ?? false;
    const pinyinHit = (p.pinyinCode ?? '').toLowerCase().includes(k);
    const wubiHit = (p.wubiCode ?? '').toLowerCase().includes(k);
    const idCardHit = (p.idCard ?? '').toLowerCase().includes(k);
    const busHit = (p.busNo ?? '').toLowerCase().includes(k);
    const phoneHit = (p.phone ?? '').includes(raw);
    return nameHit || pinyinHit || wubiHit || idCardHit || busHit || phoneHit;
  });
}

export function mockOrganizationList(): OrganizationOptionDto[] {
  return [
    { id: 501, busNo: 'ORG01', name: '急诊内科' },
    { id: 502, busNo: 'ORG02', name: '急诊外科' },
    { id: 503, busNo: 'ORG03', name: '急诊儿科' },
  ];
}

let mockQueue: QueueRow[] = [
  {
    id: 9001,
    patientName: '王五',
    patientAge: 45,
    patientGender: '男',
    triageLevelEnum: 2,
    triageLevelName: '二级（急重）',
    triageTime: '2026-04-12 08:30:00',
    chiefComplaint: '胸痛2小时',
    organizationName: '急诊内科',
    statusEnum: 1,
    statusName: '待就诊',
    waitMinutes: 35,
    encounterId: 7001,
  },
  {
    id: 9002,
    patientName: '赵六',
    patientAge: 8,
    patientGender: '女',
    triageLevelEnum: 3,
    triageLevelName: '三级（急症）',
    triageTime: '2026-04-12 09:10:00',
    chiefComplaint: '发热、咳嗽',
    organizationName: '急诊儿科',
    statusEnum: 1,
    statusName: '待就诊',
    waitMinutes: 12,
    encounterId: 7002,
  },
  {
    id: 9003,
    patientName: '孙七',
    patientAge: 72,
    patientGender: '男',
    triageLevelEnum: 1,
    triageLevelName: '一级（危急）',
    triageTime: '2026-04-12 09:45:00',
    chiefComplaint: '意识模糊',
    organizationName: '急诊内科',
    statusEnum: 2,
    statusName: '就诊中',
    waitMinutes: 5,
    encounterId: 7003,
  },
];

export function getMockQueueSnapshot(): QueueRow[] {
  return [...mockQueue];
}

export function pushMockQueueRow(row: QueueRow) {
  mockQueue = [row, ...mockQueue];
}

function filterQueue(rows: QueueRow[], p: PageQueueParams): QueueRow[] {
  return rows.filter((r) => {
    if (p.triageLevelEnum != null && r.triageLevelEnum !== p.triageLevelEnum) {
      return false;
    }
    if (p.statusEnum != null && r.statusEnum !== p.statusEnum) {
      return false;
    }
    if (p.organizationId != null) {
      const org = mockOrganizationList().find((o) => o.id === p.organizationId);
      if (org && r.organizationName !== org.name) {
        return false;
      }
    }
    return true;
  });
}

/** 分诊台初始化：字典与选项 */
export async function getInitApi(): Promise<InitResult> {
  return defaultInit();
}

/** 按姓名/号/证/电话检索患者 */
export async function searchPatientsApi(
  keyword: string,
  pageNo: number,
  _pageSize: number,
): Promise<PageApiResponse<PatientSearchRowDto>> {
  const list = mockSearchPatients(keyword);
  return {
    content: list,
    pageNo: pageNo >= 1 ? pageNo : 1,
    pageSize: list.length,
    totalCount: list.length,
    totalElements: list.length,
  };
}

/** 急诊科室/区域等可选项 */
export async function getOrganizationListApi(): Promise<OrganizationOptionDto[]> {
  return mockOrganizationList();
}

export async function createEmergencyEncounterApi(_body: Record<string, unknown>): Promise<number> {
  return Date.now() % 1_000_000_000;
}

/** 创建分诊记录 */
export async function createRecordApi(
  _body: Record<string, unknown>,
): Promise<Record<string, unknown>> {
  return { id: Date.now() % 1_000_000 };
}

/** 分诊候诊/队列分页 */
export async function getQueuePageApi(params: PageQueueParams): Promise<PageApiResponse<QueueRow>> {
  const filtered = filterQueue(mockQueue, params);
  const pageIndex = Math.max(0, (params.pageNo ?? 1) - 1);
  const start = pageIndex * params.pageSize;
  const slice = filtered.slice(start, start + params.pageSize);
  return {
    content: slice,
    pageNo: params.pageNo,
    pageSize: params.pageSize,
    totalCount: filtered.length,
    totalElements: filtered.length,
  };
}
