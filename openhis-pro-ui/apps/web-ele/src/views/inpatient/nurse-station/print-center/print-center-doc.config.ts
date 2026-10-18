import type { PrintDocType } from './print-center.types';

/**
 * 打印中心 / 执行页不展示的单据 code。
 * 检查单、检验单主线默认隐藏；国基、红旗经院点 override 展示。
 * 院点经 `#/` override 本文件。相对路径不走院点 override。
 */
export const PRINT_CENTER_HIDDEN_DOC_TYPES: readonly PrintDocType[] = [
  'exam_apply',
  'lab_apply',
];
