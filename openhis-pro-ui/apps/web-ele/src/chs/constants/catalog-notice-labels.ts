/**
 * 医保文件（目录通知）列表展示文案：种类 / 品类 / 动作。
 * 存储与接口仍用英文枚举码，仅 UI 转中文。
 */

const DOC_KIND_LABELS: Record<string, string> = {
  CATALOG_DELTA: '目录增减废',
  PAYMENT_MAP: '支付对照',
};

const CATEGORY_LABELS: Record<string, string> = {
  DRUG: '药品',
  SERVICE: '服务',
  SERVICE_F: 'F服务',
  DEVICE: '耗材',
  FORMULA: '配方颗粒',
  HERBAL: '饮片',
  PREP: '制剂',
  OTHER: '其他',
};

const ACTION_LABELS: Record<string, string> = {
  ADD: '新增',
  STOP: '停用',
  MODIFY: '变更',
  PROVINCE_MODIFY: '省变更',
  SET_PAY_CLASS: '支付对照',
  UNKNOWN: '未知',
};

/** 种类筛选（含「全部」） */
export const NOTICE_DOC_KIND_FILTER_OPTIONS = [
  { label: '全部', value: '' },
  { label: '目录增减废', value: 'CATALOG_DELTA' },
  { label: '支付对照', value: 'PAYMENT_MAP' },
];

/** 品类筛选（含「全部」） */
export const NOTICE_CATEGORY_FILTER_OPTIONS = [
  { label: '全部', value: '' },
  { label: '药品', value: 'DRUG' },
  { label: '服务', value: 'SERVICE' },
  { label: 'F服务', value: 'SERVICE_F' },
  { label: '耗材', value: 'DEVICE' },
  { label: '配方颗粒', value: 'FORMULA' },
  { label: '饮片', value: 'HERBAL' },
  { label: '制剂', value: 'PREP' },
  { label: '其他', value: 'OTHER' },
];

export function formatNoticeDocKind(code?: string | null): string {
  if (code == null || code === '') return '—';
  return DOC_KIND_LABELS[code] ?? code;
}

export function formatNoticeCategory(code?: string | null): string {
  if (code == null || code === '') return '—';
  return CATEGORY_LABELS[code] ?? code;
}

export function formatNoticeAction(code?: string | null): string {
  if (code == null || code === '') return '—';
  return ACTION_LABELS[code] ?? code;
}

const APPLY_STATUS_LABELS: Record<string, string> = {
  NOT_APPLIED: '未应用',
  APPLYING: '应用中',
  PARTIAL: '部分应用',
  APPLIED: '已应用',
  REVOKED: '已撤销',
};

export function formatNoticeApplyStatus(code?: string | null): string {
  if (code == null || code === '') return '—';
  return APPLY_STATUS_LABELS[code] ?? code;
}

/**
 * 扫描行动作汇总：新增 n / 停用 n / …
 * 仅展示计数 > 0 的段；全 0 时显示「—」。
 */
export function formatNoticeActionSummary(row: {
  addCount?: number | null;
  stopCount?: number | null;
  modifyCount?: number | null;
  setPayClassCount?: number | null;
}): string {
  const parts: string[] = [];
  const add = Number(row.addCount ?? 0);
  const stop = Number(row.stopCount ?? 0);
  const modify = Number(row.modifyCount ?? 0);
  const pay = Number(row.setPayClassCount ?? 0);
  if (add > 0) parts.push(`新增${add}`);
  if (stop > 0) parts.push(`停用${stop}`);
  if (modify > 0) parts.push(`变更${modify}`);
  if (pay > 0) parts.push(`对照${pay}`);
  if (parts.length === 0) return '—';
  return parts.join('/');
}
