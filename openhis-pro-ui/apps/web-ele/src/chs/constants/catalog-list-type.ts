import type { ApiOptionItem } from '#/types/api-response';

/** 国家医保目录 list_type（与后端 YbCatalogListType / 3301 对照一致） */
export const YB_CATALOG_LIST_TYPE_OPTIONS: ApiOptionItem[] = [
  { label: '西药', value: '101', sort: 1 },
  { label: '中成药', value: '102', sort: 2 },
  { label: '医疗服务项目', value: '201', sort: 3 },
  { label: '医用材料', value: '301', sort: 4 },
];

const LIST_TYPE_LABEL_MAP = new Map(
  YB_CATALOG_LIST_TYPE_OPTIONS.map((item) => [item.value, item.label]),
);

/** 将 list_type 编码转为展示文案 */
export function formatCatalogListType(code?: string): string {
  if (!code) return '-';
  return LIST_TYPE_LABEL_MAP.get(code) ?? code;
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
