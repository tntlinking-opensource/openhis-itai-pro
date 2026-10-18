import { PRINT_JSON_REWRITE_ENABLED } from '#/utils/print/print-json-rewrite.config';

/**
 * 门诊采血贴：合管项目名只留一行。仅国基改 JSON；红旗沿用库模板。
 */

interface PrintElementLike {
  options?: {
    field?: string;
    height?: number;
    textContentWrap?: string;
  };
}

/** 项目行高度上限（约一行 8pt） */
const ITEM_LINE_HEIGHT = 14;

/**
 * 门诊采血贴打印前把项目名锁成一行。
 * 库模板是普通文本，合管拼接名会按字折行。
 */
export function normalizeOpSpecimenBarcodePrintJson(
  printJson: Record<string, unknown>,
): Record<string, unknown> {
  if (PRINT_JSON_REWRITE_ENABLED !== true) {
    return printJson;
  }
  const panels = printJson.panels;
  if (!Array.isArray(panels)) {
    return printJson;
  }
  for (const panel of panels) {
    if (!panel || typeof panel !== 'object') {
      continue;
    }
    const elements = (panel as { printElements?: unknown[] }).printElements;
    if (!Array.isArray(elements)) {
      continue;
    }
    for (const raw of elements) {
      if (!raw || typeof raw !== 'object') {
        continue;
      }
      const element = raw as PrintElementLike;
      if (element.options?.field !== 'itemName') {
        continue;
      }
      element.options.textContentWrap = 'clip';
      const height = element.options.height;
      if (typeof height === 'number' && height > ITEM_LINE_HEIGHT) {
        element.options.height = ITEM_LINE_HEIGHT;
      }
    }
  }
  return printJson;
}
