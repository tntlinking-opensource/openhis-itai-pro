/**
 * 住院输液贴打印前是否改写成国基紧凑贴面（姓名/床位/频次 + 规格/剂量/总量）。
 *
 * 产品/红旗默认 false：沿用库模板（姓名/年龄/入院时间 + 规格/总量/用法）。
 * 国基 override 为 true。
 *
 * 须以 `#/utils/print/ip-infusion-label-layout.config` 导入。
 */
export const IP_INFUSION_LABEL_REWRITE_COMPACT = false;
