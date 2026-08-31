/**
 * 编辑器页边距 + 页眉页脚 → OpenPDF PrintConfig（与医生站、模板页共用）。
 */
import type { EmrPrintConfigPayload } from '#/api/emr/document';

import {
  adjustPageMarginMmForPdfBand,
  DEFAULT_EMR_FOOTER_TEMPLATE,
  estimateChromeBandHeightMmForPdf,
  measureEmrChromeBandHeightMm,
  prepareChromeTemplateForPdf,
  sanitizeChromeTemplateForPdf,
  type EmrPageChrome,
} from './emr-page-chrome';

export type EmrPrintPageMargins = {
  bottom: string;
  left: string;
  right: string;
  top: string;
};

function parseMarginMm(raw: string): number | undefined {
  const m = String(raw ?? '')
    .trim()
    .match(/^([\d.]+)\s*mm$/i);
  if (!m) return undefined;
  const n = Number(m[1]);
  return Number.isFinite(n) && n > 0 ? n : undefined;
}

/** 屏上分页稳定后的实测边距（与 planEmrPageLayout 同源） */
export interface ScreenPrintMetrics {
  contentInsetsMm: { bottomMm: number; topMm: number };
  bandHeightsMm?: { footerMm?: number; headerMm?: number };
}

export interface BuildEmrPrintConfigOptions {
  isOutpatientRecord?: boolean;
  outpatientSmokeFreeFooter?: string;
  /** 传入时 PDF 正文区顶/底与编辑器屏显对齐，避免预览多页 */
  screenMetrics?: ScreenPrintMetrics | null;
}

function pxToMm(px: number): number {
  return Math.round(((px * 25.4) / 96) * 100) / 100;
}

/** 读取 ProseMirror padding 与首页页眉/页脚带实测高度（打印前调用） */
export function collectScreenPrintMetricsFromDom(pm: HTMLElement): ScreenPrintMetrics | null {
  const style = getComputedStyle(pm);
  const topPx = Number.parseFloat(style.paddingTop) || 0;
  const bottomPx = Number.parseFloat(style.paddingBottom) || 0;
  if (topPx <= 0 && bottomPx <= 0) return null;

  let headerMm: number | undefined;
  let footerMm: number | undefined;
  const layer = pm.parentElement?.querySelector(':scope > .simple-editor__papers');
  if (layer instanceof HTMLElement) {
    const paper = layer.querySelector(':scope > .simple-editor__paper');
    if (paper instanceof HTMLElement) {
      const header = paper.querySelector(':scope > .simple-editor__page-header:not([hidden])');
      const footer = paper.querySelector(':scope > .simple-editor__page-footer:not([hidden])');
      if (header instanceof HTMLElement) {
        headerMm = measureEmrChromeBandHeightMm(header);
      }
      if (footer instanceof HTMLElement) {
        footerMm = measureEmrChromeBandHeightMm(footer);
      }
    }
  }

  return {
    contentInsetsMm: { topMm: pxToMm(topPx), bottomMm: pxToMm(bottomPx) },
    bandHeightsMm: { headerMm, footerMm },
  };
}

/**
 * 屏上页边距与页眉页脚配置 → PDF printConfig。
 * - 页眉保留 emrImg / 局部字号 span；页脚去 HTML
 * - 修正 PDF 边距与 band 高度重复叠加
 */
export function buildEmrPrintConfigPayload(
  margins: EmrPrintPageMargins,
  chrome: EmrPageChrome,
  options: BuildEmrPrintConfigOptions = {},
): EmrPrintConfigPayload | undefined {
  const topMm = parseMarginMm(margins.top);
  const rightMm = parseMarginMm(margins.right);
  const bottomMm = parseMarginMm(margins.bottom);
  const leftMm = parseMarginMm(margins.left);

  const isOutpatientRecord = Boolean(options.isOutpatientRecord);
  const smokeFreeFooter =
    options.outpatientSmokeFreeFooter?.trim() ||
    '为了您和家人的健康，请勿吸烟！如需戒烟帮助，请咨询戒烟医生。';

  const measuredHeaderMm = options.screenMetrics?.bandHeightsMm?.headerMm;
  const measuredFooterMm = options.screenMetrics?.bandHeightsMm?.footerMm;

  const headerTemplate = chrome.header.enabled
    ? prepareChromeTemplateForPdf(chrome.header.template)
    : '';
  let headerHeightMm = headerTemplate
    ? estimateChromeBandHeightMmForPdf(headerTemplate, chrome.header.heightMm)
    : 0;
  if (measuredHeaderMm != null && measuredHeaderMm > 0) {
    headerHeightMm = measuredHeaderMm;
  }
  const header =
    chrome.header.enabled && headerTemplate
      ? {
          enabled: true,
          heightMm: headerHeightMm,
          template: headerTemplate,
        }
      : { enabled: false };

  const footerTemplateRaw = chrome.footer.enabled
    ? sanitizeChromeTemplateForPdf(chrome.footer.template)
    : '';
  let footerTemplate = footerTemplateRaw;
  let footerHeightMm = footerTemplate
    ? estimateChromeBandHeightMmForPdf(footerTemplate, chrome.footer.heightMm)
    : 0;
  if (measuredFooterMm != null && measuredFooterMm > 0) {
    footerHeightMm = measuredFooterMm;
  }

  let footer = chrome.footer.enabled
    ? {
        enabled: true,
        heightMm: footerHeightMm,
        template: footerTemplate,
        showPageNumber: true,
      }
    : { enabled: false };

  if (isOutpatientRecord && footer.enabled) {
    const t = footerTemplate || DEFAULT_EMR_FOOTER_TEMPLATE;
    footerTemplate = t.includes('请勿吸烟') ? t : `${smokeFreeFooter}　${t}`;
    footerHeightMm = Math.max(footerHeightMm, 20);
    footer = {
      ...footer,
      heightMm: footerHeightMm,
      template: footerTemplate,
    };
  }

  if (
    topMm == null &&
    rightMm == null &&
    bottomMm == null &&
    leftMm == null &&
    !header.enabled &&
    !footer.enabled
  ) {
    return undefined;
  }

  let resolvedTop = adjustPageMarginMmForPdfBand(topMm, header.enabled ? headerHeightMm : 0);
  let resolvedBottom = bottomMm;
  if (isOutpatientRecord && (resolvedBottom == null || resolvedBottom < 28)) {
    resolvedBottom = 28;
  }
  resolvedBottom = adjustPageMarginMmForPdfBand(
    resolvedBottom,
    footer.enabled ? footerHeightMm : 0,
  );

  const screenInsets = options.screenMetrics?.contentInsetsMm;
  const marginPayload: NonNullable<EmrPrintConfigPayload['margins']> = {
    ...(resolvedTop != null ? { topMm: resolvedTop } : {}),
    ...(rightMm != null ? { rightMm } : {}),
    ...(resolvedBottom != null ? { bottomMm: resolvedBottom } : {}),
    ...(leftMm != null ? { leftMm } : {}),
    ...(screenInsets
      ? {
          contentTopMm: screenInsets.topMm,
          contentBottomMm: screenInsets.bottomMm,
        }
      : {}),
  };

  return {
    ...(Object.keys(marginPayload).length > 0 ? { margins: marginPayload } : {}),
    header,
    footer,
  };
}
