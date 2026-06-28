/**
 * 1901 平台字典 type — 取自基线规范「字典表」章节括号内英文代码。
 * @see whale-doc/chs-jilin/参考/医疗保障信息平台定点基线版医药机构接口规范-AI.md#字典表
 */
export const YB_PLATFORM_DICT_TYPE_MED_TYPE = 'med_type';

/** 1901 同步/查询默认 type */
export const YB_PLATFORM_DICT_DEFAULT = YB_PLATFORM_DICT_TYPE_MED_TYPE;

/** 国标字典表定义的常用 type（不含医保区划） */
export const YB_PLATFORM_DICT_TYPE_OPTIONS = [
  { label: '医疗类别 (med_type)', value: 'med_type' },
  { label: '险种类型 (insutype)', value: 'insutype' },
  { label: '人员类别 (psn_type)', value: 'psn_type' },
  { label: '性别 (gend)', value: 'gend' },
  { label: '就诊凭证类型 (mdtrt_cert_type)', value: 'mdtrt_cert_type' },
  { label: '支付地点 (pay_loc)', value: 'pay_loc' },
  { label: '清算类别 (clr_type)', value: 'clr_type' },
  { label: '目录类别 (list_type)', value: 'list_type' },
  { label: '诊断类别 (diag_type)', value: 'diag_type' },
] as const;

export const PLATFORM_DICT_SYNC_HINT =
  '1901 的 type 须取自基线规范「字典表」括号内英文代码（如 med_type）。国标字典表不含医保区划 type，参保地 label 暂无法经 1901 同步；可先同步 med_type 验证链路。';
