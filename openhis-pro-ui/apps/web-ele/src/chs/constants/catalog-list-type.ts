import type { ApiOptionItem } from '#/types/api-response';

/**
 * 国家医保目录 list_type（与后端 {@code ListType} / 1301·1302·1305·1306 对照一致）。
 * <p>101=西药/中成药（1301），102=中药饮片（1302，院内中草药），勿把 102 标成中成药。</p>
 */
export const YB_CATALOG_LIST_TYPE_OPTIONS: ApiOptionItem[] = [
  { label: '西药/中成药', value: '101', sort: 1 },
  { label: '中药饮片（中草药）', value: '102', sort: 2 },
  { label: '医疗服务项目', value: '201', sort: 3 },
  { label: '医用材料', value: '301', sort: 4 },
];

/**
 * 医保 list_type → 院内 medication_category_code
 * （西药 2 / 中草药 4；101 合包默认西药，中成药需人工改分类）。
 */
export const YB_LIST_TYPE_TO_MEDICATION_CATEGORY_CODE: Record<string, string> = {
  '101': '2',
  '102': '4',
};

/**
 * 医保 list_type → 院内 fin_type_code 优先码
 * （西药 1001 / 中草药 1003；院点字典码不一致时再按 label 兜底）。
 */
export const YB_LIST_TYPE_TO_FIN_TYPE_CODE: Record<string, string> = {
  '101': '1001',
  '102': '1003',
};

/** 财务类型下拉 label 候选（精确优先顺序） */
export const YB_LIST_TYPE_TO_FIN_TYPE_LABELS: Record<string, string[]> = {
  '101': ['西药费', '西药'],
  '102': ['中草药费', '中草药', '中药饮片费', '中药饮片'],
};

const LIST_TYPE_LABEL_MAP = new Map(
  YB_CATALOG_LIST_TYPE_OPTIONS.map((item) => [item.value, item.label]),
);

/** 将 list_type 编码转为展示文案 */
export function formatCatalogListType(code?: string): string {
  if (!code) return '-';
  return LIST_TYPE_LABEL_MAP.get(code) ?? code;
}

/** 药品目录选码：由 list_type 解析药品分类短码 */
export function resolveMedicationCategoryCodeByListType(
  listType?: string | null,
): string | undefined {
  const code = String(listType ?? '').trim();
  if (!code) return undefined;
  return YB_LIST_TYPE_TO_MEDICATION_CATEGORY_CODE[code];
}

/** 药品目录选码：由 list_type 解析财务类型优先码 */
export function resolveFinTypeCodeByListType(listType?: string | null): string | undefined {
  const code = String(listType ?? '').trim();
  if (!code) return undefined;
  return YB_LIST_TYPE_TO_FIN_TYPE_CODE[code];
}

/** 药品目录选码：财务类型下拉 label 候选 */
export function resolveFinTypeLabelsByListType(listType?: string | null): string[] {
  const code = String(listType ?? '').trim();
  if (!code) return [];
  return YB_LIST_TYPE_TO_FIN_TYPE_LABELS[code] ?? [];
}

/** 选码弹窗：按 list_type 解析本地明细回退 source_infno */
export function resolveCatalogDetailSourceInfno(listType?: string | null): string {
  const code = String(listType ?? '').trim();
  if (code === '201') return '1305';
  if (code === '301') return '1306';
  if (code === '102') return '1302';
  return '1301';
}

/** 收费项目等级展示 */
export function formatChrgitmLv(code?: string): string {
  if (!code) return '-';
  const map: Record<string, string> = {
    '01': '甲类',
    '02': '乙类',
    '03': '丙类',
  };
  return map[code] ?? code;
}

/** 有效标志展示 */
export function formatValiFlag(code?: string): string {
  if (!code) return '-';
  if (code === '1') return '有效';
  if (code === '0') return '无效';
  return code;
}

/** 限制使用标志展示 */
export function formatLmtUsedFlag(code?: string): string {
  if (!code) return '-';
  if (code === '1') return '是';
  if (code === '0') return '否';
  return code;
}

/** 1312 vali_flag 筛选 */
export const YB_VALI_FLAG_OPTIONS: ApiOptionItem[] = [
  { label: '有效', value: '1', sort: 1 },
  { label: '无效', value: '0', sort: 2 },
];

/** 1312 chrgitm_lv 筛选 */
export const YB_CHRGITM_LV_OPTIONS: ApiOptionItem[] = [
  { label: '甲类', value: '01', sort: 1 },
  { label: '乙类', value: '02', sort: 2 },
  { label: '丙类', value: '03', sort: 3 },
];

/** 1312 lmt_used_flag 筛选 */
export const YB_LMT_USED_FLAG_OPTIONS: ApiOptionItem[] = [
  { label: '限制使用', value: '1', sort: 1 },
  { label: '非限制', value: '0', sort: 2 },
];

/** 1312 平台返回并入库的核心字段说明（供页面提示） */
export const CATALOG_1312_SCOPE_HINT =
  '1312 按条件从医保平台分页拉取「医保目录编码/名称/等级/限制/有效期」等摘要行写入本地镜像；' +
  '不是 1301 药品明细全量包。增量游标 updt_time 必填，留空时服务端自动取本地最大更新时间或 2020-01-01。';
