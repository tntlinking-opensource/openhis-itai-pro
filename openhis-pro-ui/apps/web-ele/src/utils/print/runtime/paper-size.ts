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

/** hiprint 元素 left/top/width/height 单位为 pt；panel.width/height 为 mm */
const PT_PER_MM = 72 / 25.4;

/**
 * TD-2020A + K1a：介质宽约 25mm；打孔侧先出纸。
 * 单段出纸长度取「引导空白 + 白区」；内容起点约 52mm，落在白色热敏区。
 */
const WRISTBAND_MEDIA_WIDTH_MM = 25;
const WRISTBAND_FEED_LENGTH_MM = 170;
const WRISTBAND_CONTENT_TOP_MM = 52;
/** 沿走纸方向的文字行长（旋 90° 后的可视长度；住院号/诊断需更长） */
const WRISTBAND_CONTENT_LINE_MM = 110;
/** 旋 90° 后占介质宽度的条带高度（4 行字，须 < 介质宽） */
const WRISTBAND_CONTENT_BAND_MM = 23;
/** 热敏 203dpi 下过小易发糊，用加粗大字 */
const WRISTBAND_FONT_PT = 12;

/** 业务场景默认纸张（优先于模板 panel.paperType） */
const SCENARIO_PAGE_SIZE: Partial<Record<PrintScenario, string>> = {
  /** 检查/检验申请单：按场景分 A4 / A5 模板 */
  [PRINT_SCENARIO.examinationPrint]: 'A4',
  [PRINT_SCENARIO.testPrint]: 'A4',
  [PRINT_SCENARIO.examinationPrintA5]: 'A5',
  [PRINT_SCENARIO.testPrintA5]: 'A5',
  /** 门诊处方勿写死，以模板 panel 为准 */
  [PRINT_SCENARIO.pharmacyDailySettlementPrint]: 'A4',
  /** 报表列表：纸张固定 A4，横竖靠 panel 宽高推 landscape */
  [PRINT_SCENARIO.reportListPrint]: 'A4',
  /** 门诊患者费用清单：A4 横向（panel 297×210） */
  [PRINT_SCENARIO.opFeeListPrint]: 'A4',
};

export interface HiprintPageSizeOptions {
  /** Electron pageSize：标准名或自定义微米 { width, height } */
  pageSize?: string | { height: number; width: number };
  /** 是否横向；缺省由模板宽高推断 */
  landscape?: boolean;
}

export function readFirstPanel(printJson: Record<string, unknown>): null | Record<string, unknown> {
  const panels = printJson.panels;
  if (!Array.isArray(panels) || panels.length === 0) {
    return null;
  }
  const panel = panels[0];
  return panel && typeof panel === 'object' ? (panel as Record<string, unknown>) : null;
}

/** hiprint panelPageRule=none：按内容撑高；打印时用实测高度作自定义纸张 */
export function isContentHeightPanel(panel: null | Record<string, unknown>): boolean {
  if (!panel) {
    return false;
  }
  const rule = typeof panel.panelPageRule === 'string' ? panel.panelPageRule.trim() : '';
  return rule === 'none';
}

/** 毫米 → Electron 自定义纸张微米；高度至少 1mm */
export function mmToMicronPageSize(
  widthMm: number,
  heightMm: number,
): { height: number; width: number } {
  return {
    width: Math.round(Math.max(widthMm, 1) * 1000),
    height: Math.round(Math.max(heightMm, 1) * 1000),
  };
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

/**
 * 腕带打印专用走纸模板（短边=宽、长边=走纸）。
 * 版式在 longText.formatter 内横排后整体 rotate(90deg) 写入位图，
 * 避免元素 rotate 撑破纸宽导致一次出两页，也避免纸张级 CSS rotate 不生效。
 */
export function buildWristbandFeedPrintJson(): Record<string, unknown> {
  const contentTopPt = Math.round(WRISTBAND_CONTENT_TOP_MM * PT_PER_MM);
  const boxWidthPt = Math.round((WRISTBAND_MEDIA_WIDTH_MM - 2) * PT_PER_MM);
  const boxHeightPt = Math.round(
    (WRISTBAND_FEED_LENGTH_MM - WRISTBAND_CONTENT_TOP_MM - 2) * PT_PER_MM,
  );
  const lineMm = WRISTBAND_CONTENT_LINE_MM;
  const bandMm = WRISTBAND_CONTENT_BAND_MM;
  const fontPt = WRISTBAND_FONT_PT;

  const formatter = `function(title,value,options,printData){
var d=printData||{};
function e(k){var x=d[k];return x==null?'':String(x);}
return ''
+'<div style="width:100%;height:100%;overflow:visible;position:relative;">'
+'<div style="width:${lineMm}mm;height:${bandMm}mm;transform:rotate(90deg) translate(0,-100%);transform-origin:top left;'
+'font-family:SimHei,黑体,SimSun,宋体,sans-serif;font-size:${fontPt}pt;font-weight:700;line-height:1.25;'
+'color:#000;-webkit-font-smoothing:none;text-rendering:geometricPrecision;box-sizing:border-box;">'
+'<div style="white-space:nowrap;">姓名：'+e('patientName')+'　'+e('genderEnumText')+'　'+e('ageText')+'</div>'
+'<div style="white-space:nowrap;">科室：'+e('departmentName')+'　病房：'+e('wardName')+'　床位：'+e('bedNo')+'</div>'
+'<div style="white-space:nowrap;">住院号：'+e('inpatientNo')+'　过敏：'+e('allergyText')+'</div>'
+'<div style="white-space:nowrap;">诊断：'+e('diagnosisText')+'</div>'
+'</div></div>';
}`;

  return {
    engine: 'Print',
    schemaVersion: 1,
    panels: [
      {
        name: '1',
        index: 0,
        width: WRISTBAND_MEDIA_WIDTH_MM,
        height: WRISTBAND_FEED_LENGTH_MM,
        paperType: 'other',
        paperHeader: 0,
        paperFooter: Math.round(WRISTBAND_FEED_LENGTH_MM * PT_PER_MM),
        paperNumberDisabled: true,
        paperNumberContinue: false,
        printElements: [
          {
            options: {
              left: 3,
              top: contentTopPt,
              width: boxWidthPt,
              height: boxHeightPt,
              field: 'wristbandBody',
              title: '腕带',
              hideTitle: true,
              fontSize: fontPt,
              lineHeight: Math.round(fontPt * 1.3),
              formatter,
            },
            printElementType: { type: 'longText', title: '腕带' },
          },
        ],
      },
    ],
  };
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
  // 自定义微米纸张（瓶签/腕带等）：宽高已表达方向；再设 landscape=true 会被 Electron 二次旋转
  if (pageSize && typeof pageSize === 'object') {
    return { pageSize, landscape: false };
  }
  return { pageSize, landscape: resolveLandscape(panel) };
}
