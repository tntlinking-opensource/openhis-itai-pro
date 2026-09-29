import type { FormItemOption } from '@vben-core/table-layout-ui';

/** 质控缺陷严重级别 */
export const QC_DEFECT_SEVERITY_OPTIONS: FormItemOption[] = [
  { label: '阻断', value: 'block' },
  { label: '警告', value: 'warn' },
  { label: '提示', value: 'info' },
];

export const QC_DEFECT_SEVERITY_LABEL: Record<string, string> = {
  block: '阻断',
  warn: '警告',
  info: '提示',
};

export const QC_DEFECT_SEVERITY_TAG: Record<string, 'danger' | 'info' | 'warning'> = {
  block: 'danger',
  warn: 'warning',
  info: 'info',
};

/** 质控缺陷状态 */
export const QC_DEFECT_STATUS_OPTIONS: FormItemOption[] = [
  { label: '待整改', value: 'open' },
  { label: '已整改', value: 'rectified' },
  { label: '已关闭', value: 'closed' },
];

export const QC_DEFECT_STATUS_LABEL: Record<string, string> = {
  open: '待整改',
  rectified: '已整改',
  closed: '已关闭',
};

export const QC_DEFECT_STATUS_TAG: Record<string, 'info' | 'success' | 'warning'> = {
  open: 'warning',
  rectified: 'success',
  closed: 'info',
};

/** 病案质量等级（归档） */
export const QUALITY_GRADE_OPTIONS: FormItemOption[] = [
  { label: '甲级', value: 'A' },
  { label: '乙级', value: 'B' },
  { label: '丙级', value: 'C' },
];

/** 质控工作台默认筛选：已接收 + 质控中 */
export const QC_WORKBENCH_DEFAULT_STATUSES = ['PENDING_ARCHIVE', 'ARCHIVED'] as const;

/** 可预览卷宗的状态 */
export const VOLUME_ACCESS_STATUSES = ['ARCHIVED'] as const;
