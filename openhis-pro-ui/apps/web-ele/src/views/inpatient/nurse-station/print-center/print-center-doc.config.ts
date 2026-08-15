import type { PrintDocType } from './print-center.types';

/**
 * 打印中心不展示的单据 code（主线默认不隐藏）。
 * 院点若需隐藏汇总领药单 / 批次摆药单等：在
 * projects/project-H{code}/overrides/views/inpatient/nurse-station/print-center/
 * print-center-doc.config.ts 覆盖本常量。
 * 注意：须以 `#/views/.../print-center-doc.config` 导入，相对路径不走院点 override。
 */
export const PRINT_CENTER_HIDDEN_DOC_TYPES: readonly PrintDocType[] = [];
