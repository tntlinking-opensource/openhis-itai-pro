/** 病案首页工作台：筛选选项与行操作定义 */

export interface HomepageSelectOption {
  label: string;
  value: string;
}

/**
 * 未填值统一展示占位（编辑态 Select/Input placeholder、只读空串回显）。
 * 国标纸质首页惯例：空白处印「-」。
 */
export const HOMEPAGE_EMPTY_DISPLAY = '-';

/** 空串表示「全部」 */
export const ALL_OPTION_VALUE = '';

/**
 * 科室筛选项由住院登记 `organizationOptions` 动态加载（名称精确匹配 `admCatyName`）。
 * 仅保留「全部」占位；完整列表见 `index.vue` 的 `deptOptions`。
 */
export const DEPT_ALL_OPTION: HomepageSelectOption = {
  label: '全部',
  value: ALL_OPTION_VALUE,
};

/**
 * 4401/结算上传状态筛选（对齐领域枚举 ChsUploadStatus + 前端 none=未上传）。
 * code 驱动逻辑，label 仅展示。
 */
export const UPLOAD_STATUS_OPTIONS: HomepageSelectOption[] = [
  { label: '全部', value: ALL_OPTION_VALUE },
  { label: '未上传', value: 'none' },
  { label: '待上传', value: 'pending' },
  { label: '上传成功', value: 'success' },
  { label: '上传失败', value: 'failed' },
  { label: '无需上传', value: 'skipped' },
];

/**
 * 结算清单上传状态筛选（对齐 MrmsSettleUploadFlags：none/0/1/2）。
 */
export const SETTLE_UPLOAD_STATUS_OPTIONS: HomepageSelectOption[] = [
  { label: '全部', value: ALL_OPTION_VALUE },
  { label: '未上传', value: 'none' },
  { label: '已上传', value: '1' },
  { label: '已提交', value: '2' },
];

export const YES_NO_DISCHARGE_OPTIONS: HomepageSelectOption[] = [
  { label: '全部', value: ALL_OPTION_VALUE },
  { label: '已出院', value: '1' },
  { label: '未出院', value: '0' },
];

export const YES_NO_SETTLE_OPTIONS: HomepageSelectOption[] = [
  { label: '全部', value: ALL_OPTION_VALUE },
  { label: '已结算', value: '1' },
  { label: '未结算', value: '0' },
];

/** 行内入口（临床/费用/手术等次要入口进「更多」） */
export type HomepageRowActionCode =
  | 'emr'
  | 'order'
  | 'homepage'
  | 'settle-list'
  | 'bill'
  | 'temperature'
  | 'nursing'
  | 'surgery';

export interface HomepageRowActionDef {
  code: HomepageRowActionCode;
  label: string;
}

/** 主操作：病案详情 + 结算清单（独立预览页） */
export const HOMEPAGE_PRIMARY_ACTIONS: HomepageRowActionDef[] = [
  { code: 'homepage', label: '病案详情' },
  { code: 'settle-list', label: '结算清单' },
];

/** 次要入口：全部收进「更多」，避免操作列挤爆 */
export const HOMEPAGE_MORE_ACTIONS: HomepageRowActionDef[] = [
  { code: 'emr', label: '病历' },
  { code: 'order', label: '医嘱' },
  { code: 'temperature', label: '体温单' },
  { code: 'nursing', label: '护理记录' },
  { code: 'bill', label: '费用清单' },
  { code: 'surgery', label: '手术记录' },
];

/** 全量（文案解析用） */
export const HOMEPAGE_ROW_ACTIONS: HomepageRowActionDef[] = [
  ...HOMEPAGE_PRIMARY_ACTIONS,
  ...HOMEPAGE_MORE_ACTIONS,
];

/** 上传医保入口（对齐老系统 InHosEmrDocList：4401 / 4101A / 4102 / 4103） */
export type HomepageInsActionCode =
  | 'one-click-report'
  | 'upload-homepage'
  | 'upload-settle'
  /** 4101A 上传 + 4102 提交审核（4401 未开通时的主入口） */
  | 'settle-upload-commit'
  | 'settle-submit'
  | 'settle-pending'
  | 'query-settle';

export interface HomepageInsActionDef {
  code: HomepageInsActionCode;
  label: string;
  /** 对应国家医保交易码（展示/提示用） */
  infno: string;
}

/** 行内主入口：一键上报 = 4401 → 4101A → 4102 正式提交（须 4401 已开通） */
export const HOMEPAGE_INS_ONE_CLICK: HomepageInsActionDef = {
  code: 'one-click-report',
  label: '一键上报',
  infno: '4401→4101A→4102',
};

/** 4401 未开通时的主入口：一键上传 = 清单上传(4101A) + 提交审核(4102) */
export const HOMEPAGE_INS_SETTLE_PRIMARY: HomepageInsActionDef = {
  code: 'settle-upload-commit',
  label: '一键上传',
  infno: '4101A→4102',
};

/**
 * 医保「更多」分传（与一键并列；4102 提交/待提交、单侧 4401/4101A、4103 查询）。
 * 展示时须按 {@link MRMS_HOMEPAGE_4401_UPLOAD_ENABLED} 过滤掉 `upload-homepage`。
 */
export const HOMEPAGE_INS_MORE_ACTIONS: HomepageInsActionDef[] = [
  { code: 'upload-homepage', label: '首页上传', infno: '4401' },
  { code: 'upload-settle', label: '结算上传', infno: '4101A' },
  { code: 'settle-submit', label: '提交审核', infno: '4102→1' },
  { code: 'settle-pending', label: '撤回审核', infno: '4102→0' },
  { code: 'query-settle', label: '查询结算', infno: '4103' },
];

/** 全量（文案解析用） */
export const HOMEPAGE_INS_ACTIONS: HomepageInsActionDef[] = [
  HOMEPAGE_INS_ONE_CLICK,
  ...HOMEPAGE_INS_MORE_ACTIONS,
];

export const UPLOAD_STATUS_LABEL: Record<string, string> = {
  none: '未上传',
  pending: '待上传',
  success: '上传成功',
  failed: '上传失败',
  skipped: '无需上传',
};

/**
 * 结算清单上传标志展示（对齐后端 {@code MrmsSettleUploadFlags}：0/1/2）。
 * 禁止把裸 code 直接渲染到表格。
 */
export const SETTLE_UPLOAD_STATUS_LABEL: Record<string, string> = {
  none: '未上传',
  '0': '未上传',
  '1': '已上传',
  '2': '已提交',
  failed: '上传失败',
};

/** 上传状态 → Tag type（按 code；首页 4401 ChsUploadStatus） */
export function uploadStatusTagType(code?: string): 'danger' | 'info' | 'success' | 'warning' {
  if (code === 'success') {
    return 'success';
  }
  if (code === 'failed') {
    return 'danger';
  }
  if (code === 'pending') {
    return 'warning';
  }
  // none / skipped / 未知
  return 'info';
}

/**
 * 结算上传标志 → Tag type：已传/已提交绿，失败红，未传灰。
 *
 * @param code 0/1/2/none/failed
 */
export function settleUploadStatusTagType(
  code?: string,
): 'danger' | 'info' | 'success' | 'warning' {
  if (code === '1' || code === '2') {
    return 'success';
  }
  if (code === 'failed') {
    return 'danger';
  }
  return 'info';
}

/**
 * 结算上传标志 → 展示文案（未知码回落「未上传」，不透出裸数字）。
 *
 * @param code 主档 settle_upload_status_code
 */
export function settleUploadStatusLabel(code?: string): string {
  const fallback = '未上传';
  if (!code) {
    return fallback;
  }
  const label = SETTLE_UPLOAD_STATUS_LABEL[code];
  if (label) {
    return label;
  }
  return fallback;
}
