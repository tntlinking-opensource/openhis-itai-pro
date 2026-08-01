import type { PrintScenario } from '../constants/print-scenario';

import { PRINT_SCENARIO } from '../constants/print-scenario';

/** electron-hiprint / Electron 内置纸张名 */
const STANDARD_PAGE_SIZE_NAMES = new Set([
  'A0',
  'A1',
  'A2',
  'A3',
  'A4',
  'A5',
  'A6',
  'Legal',
  'Letter',
  'Tabloid',
]);

/** 业务场景默认纸张（优先于模板 panel.paperType；门诊处方勿写死，以模板为准） */
const SCENARIO_PAGE_SIZE: Partial<Record<PrintScenario, string>> = {
  [PRINT_SCENARIO.examinationPrint]: 'A5',
  [PRINT_SCENARIO.testPrint]: 'A5',
  [PRINT_SCENARIO.pharmacyDailySettlementPrint]: 'A4',
};

export interface HiprintPageSizeOptions {
  /** Electron pageSize：标准名或自定义微米 { width, height } */
  pageSize?: string | { height: number; width: number };
  /** 是否横向；缺省由模板宽高推断 */
  landscape?: boolean;
}

function readFirstPanel(printJson: Record<string, unknown>): null | Record<string, unknown> {
  const panels = printJson.panels;
  if (!Array.isArray(panels) || panels.length === 0) {
    return null;
  }
  const panel = panels[0];
  return panel && typeof panel === 'object' ? (panel as Record<string, unknown>) : null;
}

function resolveLandscape(panel: null | Record<string, unknown>): boolean | undefined {
  if (!panel) {
    return undefined;
  }
  const width = panel.width;
  const height = panel.height;
  if (typeof width === 'number' && typeof height === 'number' && width > 0 && height > 0) {
    return width > height;
  }
  return undefined;
}

function resolvePageSizeFromPanel(
  panel: Record<string, unknown>,
): HiprintPageSizeOptions['pageSize'] {
  const paperType = typeof panel.paperType === 'string' ? panel.paperType.trim() : '';
  if (paperType && STANDARD_PAGE_SIZE_NAMES.has(paperType)) {
    return paperType;
  }
  const width = panel.width;
  const height = panel.height;
  if (typeof width === 'number' && typeof height === 'number' && width > 0 && height > 0) {
    // electron 自定义纸张：毫米 × 1000 → 微米
    return { width: Math.round(width * 1000), height: Math.round(height * 1000) };
  }
  return undefined;
}

/** 解析 print2 纸张参数：scenarioCode 默认 > 模板 panel */
export function resolveHiprintPageSize(
  printJson: Record<string, unknown>,
  scenarioCode?: string,
): HiprintPageSizeOptions {
  const panel = readFirstPanel(printJson);
  const trimmedScenarioCode = scenarioCode?.trim();
  const scenarioSize =
    trimmedScenarioCode && trimmedScenarioCode in SCENARIO_PAGE_SIZE
      ? SCENARIO_PAGE_SIZE[trimmedScenarioCode as PrintScenario]
      : undefined;
  if (scenarioSize) {
    // 标准纸名（A4/A5…）靠 landscape 表达横竖
    return { pageSize: scenarioSize, landscape: resolveLandscape(panel) };
  }
  const pageSize = panel ? resolvePageSizeFromPanel(panel) : undefined;
  // 自定义微米纸张（瓶签等）：宽高已表达方向；再设 landscape=true 会被 Electron 二次旋转，方向反了
  if (pageSize && typeof pageSize === 'object') {
    return { pageSize, landscape: false };
  }
  return { pageSize, landscape: resolveLandscape(panel) };
}
