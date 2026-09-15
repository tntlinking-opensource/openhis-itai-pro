/**
 * 病案首页：卫统 / HQMS CSV 导出开关（主线默认关；院点 overrides 打开）。
 *
 * 主线默认关闭；国基院点在
 * projects/project-H22010202493/overrides/views/mrms/homepage/homepage-weistat-export.config.ts
 * override 为 true。
 *
 * 注意：须以 `#/views/mrms/homepage/homepage-weistat-export.config` 导入，相对路径不走院点 override。
 */
export const MRMS_HOMEPAGE_WEISTAT_EXPORT_ENABLED = false;

/**
 * HQMS CSV 导出入口（与卫统分开关、分管道）。
 */
export const MRMS_HOMEPAGE_HQMS_EXPORT_ENABLED = false;
