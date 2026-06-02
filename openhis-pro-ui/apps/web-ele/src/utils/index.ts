/**
 * 入库审核状态选项
 */
export const LOCATION_STATUS_OPTIONS = [
  { sort: 0, value: 1, label: '待审核' },
  { sort: 0, value: 2, label: '审核中' },
  { sort: 0, value: 3, label: '同意' },
  { sort: 0, value: 4, label: '驳回' },
  { sort: 0, value: 9, label: '已撤回' },
  { sort: 0, value: 10, label: '待审请' },
  { sort: 0, value: 11, label: '已失效' },
  { sort: 0, value: 12, label: '已发送' },
] as const;

export type LocationStatus = (typeof LOCATION_STATUS_OPTIONS)[number]['value'];

export const LOCATION_STATUS_MAP = Object.fromEntries(
  LOCATION_STATUS_OPTIONS.map((item) => [item.value, item.label]),
) as Record<LocationStatus, string>;

export * from './pinyin-util';

