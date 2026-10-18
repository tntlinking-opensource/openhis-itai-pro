/**
 * 护士站汇总领药：发送成功后是否默认自动打印领药单。
 *
 * <p>本机 localStorage 已有显式偏好时以本机为准；仅「从未设置」时用本默认值。
 * 院点可经 `#/` override 本文件（勿在主线写死某院点编码）。
 */

/** 未设置本机偏好时的默认：产品主线开启（发送后自动打汇总领药单） */
export const DEFAULT_AUTO_PRINT_PICKUP_AFTER_SUBMIT = true;
