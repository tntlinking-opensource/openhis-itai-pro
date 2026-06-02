import type { FormItemOption } from '@vben-core/table-layout-ui';

import type { CaseRecordStatusCode } from '#/api/mrms/types';

/** 病案状态展示 */
export const CASE_RECORD_STATUS_OPTIONS: FormItemOption[] = [
  { label: '草稿', value: 'draft' },
  { label: '已提交', value: 'submitted' },
  { label: '已接收', value: 'received' },
  { label: '已退回', value: 'returned' },
  { label: '质控中', value: 'qcing' },
  { label: '已归档', value: 'archived' },
];

export const CASE_RECORD_STATUS_LABEL: Record<string, string> = {
  draft: '草稿',
  submitted: '已提交',
  received: '已接收',
  returned: '已退回',
  qcing: '质控中',
  archived: '已归档',
};

export const CASE_RECORD_STATUS_TAG: Record<string, 'danger' | 'info' | 'success' | 'warning'> = {
  draft: 'info',
  submitted: 'warning',
  received: 'success',
  returned: 'danger',
  qcing: 'warning',
  archived: 'success',
};

/** 首页诊断行类型 */
export const HOMEPAGE_DIAG_TYPES = [
  { code: 'discharge_main', label: '出院主诊断' },
  { code: 'discharge_other', label: '出院其他诊断' },
  { code: 'admission_main', label: '入院主诊断' },
  { code: 'admission_other', label: '入院其他诊断' },
  { code: 'pathology', label: '病理诊断' },
  { code: 'injury_poison', label: '损伤中毒外部原因' },
] as const;

export type HomepageDiagTypeCode = (typeof HOMEPAGE_DIAG_TYPES)[number]['code'];

/** 左侧锚点章节 */
export const HOMEPAGE_ANCHOR_SECTIONS = [
  { id: 'sec-basic', label: '基本信息', section: 'basic' as const },
  { id: 'sec-inpatient', label: '住院信息', section: 'inpatient' as const },
  { id: 'sec-diagnosis', label: '诊断编码', section: 'diagnosis' as const },
  { id: 'sec-procedure', label: '手术操作', section: 'procedure' as const },
  { id: 'sec-staff', label: '医护人员', section: 'staff' as const },
];

export const GENDER_OPTIONS: FormItemOption[] = [
  { label: '男', value: '1' },
  { label: '女', value: '2' },
  { label: '未知', value: '0' },
];

/** 医生可编辑状态 */
export const DOCTOR_EDITABLE_STATUS: CaseRecordStatusCode[] = ['draft', 'returned'];

/** 病案室可操作状态 */
export const RECORD_ROOM_RECEIVE_STATUS: CaseRecordStatusCode[] = ['submitted'];
export const RECORD_ROOM_RETURN_STATUS: CaseRecordStatusCode[] = ['submitted', 'received'];
