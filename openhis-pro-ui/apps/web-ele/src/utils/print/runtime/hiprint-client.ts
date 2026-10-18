import type { HiprintPageSizeOptions } from './paper-size';

import { $, installHiprintJquery } from '#/hiprint/core/jquery';
import { markHiprintEngineInited } from '#/hiprint/core/engine-state';

import { autoConnect, defaultElementTypeProvider, hiprint } from 'vue-plugin-hiprint';

import {
  getHiprintSocketHost,
  isHiprintHealthRecentlyUnavailable,
  prefetchHiprintSocketHost,
  resolveHiprintSocketHostAsync,
} from '#/api/local/zxplatWin';

import { PRINT_SCENARIO } from '../constants/print-scenario';
import {
  buildWristbandFeedPrintJson,
  isContentHeightPanel,
  mmToMicronPageSize,
  readFirstPanel,
  resolveHiprintPageSize,
} from './paper-size';
import type { HiprintDuplexMode } from './print-page-selection';
import { PRINT_ERROR_CODE } from './types';

const DEFAULT_CONNECT_TIMEOUT_MS = 3000;
const WARMUP_CONNECT_TIMEOUT_MS = 1500;
/** 打印前等待 health 解析的上限；超时则用同步兜底地址，避免拖住业务 */
const HIPRINT_HOST_WAIT_MS = 800;
const DEFAULT_PRINT_TIMEOUT_MS = 60_000;

export interface HiprintPrintRequest {
  PrintJson: Record<string, unknown>;
  printData: Record<string, unknown>;
  scenarioCode?: string;
  printer?: string;
  copies?: number;
  /**
   * 针式联单：同一 PrintData 连打 N 页，print2 的 copies 固定为 1。
   */
  repeatPrintData?: boolean;
  silent?: boolean;
  jobName?: string;
  pageSize?: HiprintPageSizeOptions['pageSize'];
  landscape?: boolean;
  /** 双面正反：simplex / shortEdge / longEdge，透传 electron-hiprint */
  duplexMode?: HiprintDuplexMode;
}

export interface HiprintPrintSuccess {
  ok: true;
  printer?: string;
  copies?: number;
}

export interface HiprintPrintFailure {
  ok: false;
  code: string;
  message: string;
  retryAfterMs?: number;
}

export type HiprintPrintResponse = HiprintPrintFailure | HiprintPrintSuccess;

interface HiprintPrint2Options {
  copies?: number;
  /** 双面正反，透传客户端 */
  duplexMode?: HiprintDuplexMode;
  landscape?: boolean;
  pageSize?: HiprintPageSizeOptions['pageSize'];
  printer?: string;
  silent?: boolean;
  styleHandler?: () => string;
  title?: string;
}

interface HiprintPrintTemplate {
  print2: (
    data: Record<string, unknown> | Record<string, unknown>[],
    options?: HiprintPrint2Options,
  ) => void;
  /** 把已渲染 HTML 送给 electron-hiprint（一折一折打，避免第二页被裁） */
  printByHtml2?: (html: HTMLElement, options?: HiprintPrint2Options) => void;
  /** 生成打印 HTML（jQuery 包装）；panelPageRule=none 时用于实测内容高度 */
  getHtml?: (data: Record<string, unknown>) => JQueryLike;
  on: (event: string, handler: (data?: unknown) => void) => void;
  off?: (event: string, handler: (data?: unknown) => void) => void;
}

interface JQueryLike {
  find: (sel: string) => JQueryLike;
  first: () => JQueryLike;
  attr: (name: string) => null | string | undefined;
  css: (name: string) => string;
  each: (fn: (this: HTMLElement, index: number, el: HTMLElement) => void) => void;
  get?: (index: number) => HTMLElement | undefined;
  length: number;
}

const PT_TO_MM = 25.4 / 72;
const PX_TO_MM = 25.4 / 96;

function isVisuallyEmptyPrintElement(el: HTMLElement): boolean {
  if (el.querySelector('img, canvas, svg, table')) {
    const table = el.querySelector('table');
    if (table) {
      const rows = table.querySelectorAll('tbody tr, tr');
      // 仅表头、无数据行时仍算有内容（表头也要占位）；完全空表才跳过
      return rows.length === 0;
    }
    return false;
  }
  return !String(el.textContent ?? '')
    .replace(/\u00a0/g, ' ')
    .trim();
}

/**
 * 实测内容底边（mm）。
 * 不用 paper 的 original-height：定高模板短内容时纸仍是 297，页码钉在底部。
 * 取 .hiprint-printElement 最大底边，排除页码。
 */
function measurePrintContentHeightMm(
  template: HiprintPrintTemplate,
  printData: Record<string, unknown>,
): number | undefined {
  if (typeof template.getHtml !== 'function') {
    return undefined;
  }
  const host = document.createElement('div');
  host.style.cssText =
    'position:absolute;left:-99999px;top:0;visibility:hidden;pointer-events:none;';
  document.body.append(host);
  try {
    const $html = template.getHtml(printData) as unknown as {
      appendTo?: (target: HTMLElement) => void;
      get?: (index: number) => HTMLElement | undefined;
      0?: HTMLElement;
    };
    if (typeof $html.appendTo === 'function') {
      $html.appendTo(host);
    } else {
      const root = $html[0] ?? $html.get?.(0);
      if (!root) {
        return undefined;
      }
      host.append(root);
    }

    const paper =
      host.querySelector('.hiprint-printPaper') ?? host.querySelector('.hiprint-printTemplate');
    if (!paper) {
      return undefined;
    }

    const paperRect = paper.getBoundingClientRect();
    let maxBottomPx = 0;
    const elements = paper.querySelectorAll('.hiprint-printElement');
    elements.forEach((node) => {
      const el = node as HTMLElement;
      if (el.closest('.hiprint-paperNumber')) {
        return;
      }
      if (isVisuallyEmptyPrintElement(el)) {
        return;
      }
      // 表格外层可能仍是设计高度，改量实际 table 底边
      const table = el.querySelector('table');
      const target = table ?? el;
      const rect = target.getBoundingClientRect();
      maxBottomPx = Math.max(maxBottomPx, rect.bottom - paperRect.top);
    });

    // 兜底：用 style top/height（pt），避免个别元素未布局
    if (maxBottomPx <= 0) {
      let maxBottomPt = 0;
      elements.forEach((node) => {
        const el = node as HTMLElement;
        if (isVisuallyEmptyPrintElement(el)) {
          return;
        }
        const topPt = Number.parseFloat(el.style.top || '0');
        const heightPt = Number.parseFloat(el.style.height || '0');
        if (Number.isFinite(topPt) && Number.isFinite(heightPt)) {
          maxBottomPt = Math.max(maxBottomPt, topPt + heightPt);
        }
      });
      if (maxBottomPt > 0) {
        return maxBottomPt * PT_TO_MM;
      }
      return undefined;
    }

    return maxBottomPx * PX_TO_MM;
  } catch {
    return undefined;
  } finally {
    host.remove();
  }
}

/** 内容高度纸：锁纸高并隐藏页码。勿写 @page size（短纸宽>高会被当成横向，整页旋转） */
function buildContentHeightPrintStyleHandler(heightMm: number): () => string {
  const h = Math.max(heightMm, 1).toFixed(2);
  return () =>
    `<style>
.hiprint-printPaper{height:${h}mm !important;min-height:0 !important;max-height:${h}mm !important;overflow:hidden !important;}
.hiprint-paperNumber,.hiprint-paperNumber-disabled{display:none !important;}
</style>`;
}

/** 联单定高：每折固定高度；overflow:hidden 会让 Chromium 丢掉第二折 */
function buildFormFoldPrintStyleHandler(heightMm: number): () => string {
  const h = Math.max(heightMm, 1).toFixed(2);
  return () =>
    `<style>
.hiprint-printPaper{
  height:${h}mm !important;
  min-height:${h}mm !important;
  max-height:${h}mm !important;
  overflow:visible !important;
  page-break-after:always !important;
  break-after:page !important;
}
.hiprint-printPanel .hiprint-printPaper:last-child{
  page-break-after:avoid !important;
  break-after:auto !important;
}
.hiprint-paperNumber,.hiprint-paperNumber-disabled{display:none !important;}
</style>`;
}

/** 只要最外层纸张，排除套在里面的节点 */
function isTopLevelHiprintPaper(el: HTMLElement): boolean {
  return !el.parentElement?.closest('.hiprint-printPaper');
}

/** 从 getHtml 结果里拆出每一折最外层 .hiprint-printPaper */
function listHiprintPapers(htmlResult: unknown): HTMLElement[] {
  const $html = $(htmlResult as never);
  const nested = $html.find('.hiprint-printPaper').toArray() as HTMLElement[];
  const self = $html.filter('.hiprint-printPaper').toArray() as HTMLElement[];
  const papers = [...new Set([...self, ...nested])].filter(isTopLevelHiprintPaper);
  if (papers.length > 0) {
    return papers;
  }
  const root = $html.get?.(0);
  return root ? [root] : [];
}

/** 单折包一层 template/panel，给 printByHtml2 */
function wrapHiprintPaper(paper: HTMLElement): HTMLElement {
  const templateEl = document.createElement('div');
  templateEl.className = 'hiprint-printTemplate';
  const panelEl = document.createElement('div');
  panelEl.className = 'hiprint-printPanel';
  panelEl.append(paper.cloneNode(true));
  templateEl.append(panelEl);
  return templateEl;
}

function sleepMs(ms: number): Promise<void> {
  return new Promise((resolve) => {
    window.setTimeout(resolve, ms);
  });
}

let runtimeInited = false;
let lastHost = '';
let printLockCssPrefetched = false;

function ensureJquery(): void {
  installHiprintJquery();
}

/** 预取 print-lock.css，减轻 print2 内同步 XHR 等待 */
function prefetchPrintLockCss(): void {
  if (printLockCssPrefetched) {
    return;
  }
  printLockCssPrefetched = true;
  const link = document.querySelector(
    'link[media=print][href*="print-lock"]',
  ) as HTMLLinkElement | null;
  if (link?.href) {
    fetch(link.href).catch(() => undefined);
  }
}

/** 初始化 hiprint 运行时（electron-hiprint Socket.IO） */
function ensureHiprintPrintRuntime(host: string): void {
  ensureJquery();
  prefetchPrintLockCss();
  if (runtimeInited && lastHost === host) {
    return;
  }
  hiprint.init({
    // hiprint 导出的小写构造函数名，非标准 PascalCase
    providers: [new defaultElementTypeProvider()], // eslint-disable-line new-cap
    lang: 'cn',
    host,
  });
  runtimeInited = true;
  lastHost = host;
  markHiprintEngineInited();
}

/** 打印用 Socket 地址（同步兜底，优先 env / 缓存 / 默认 17521） */
export function resolveHiprintHost(): string {
  return getHiprintSocketHost();
}

/** 打印用 Socket 地址（经 5000 /api/health 解析 Hiprint 端口，env 优先；超时快速兜底） */
export async function resolveHiprintHostAsync(): Promise<string> {
  const fallback = getHiprintSocketHost();
  const resolved = await Promise.race([
    resolveHiprintSocketHostAsync(),
    new Promise<string>((resolve) => {
      window.setTimeout(() => resolve(fallback), HIPRINT_HOST_WAIT_MS);
    }),
  ]);
  return resolved;
}

export interface HiprintProbeResult {
  ready: boolean;
  host: string;
  reason?: string;
}

/** 探测 hiprint 是否可连接（经 5000 health 解析地址后直连 Socket） */
export async function probeHiprintClient(): Promise<HiprintProbeResult> {
  const host = await resolveHiprintHostAsync();
  const ready = await connectHiprintClient(host, resolveConnectTimeoutMs());
  return ready
    ? { ready: true, host }
    : {
        ready: false,
        host,
        reason: `无法连接 Hiprint 打印客户端（${host}），请确认 Zxplat.Win 已启动且 Hiprint 子进程在运行`,
      };
}

/** 探测 electron-hiprint 是否可连接 */
export async function isHiprintClientReady(): Promise<boolean> {
  const probe = await probeHiprintClient();
  return probe.ready;
}

/**
 * 后台预热打印客户端：先用默认地址尝试连接，health 解析在后台进行，不阻塞任何业务请求。
 */
export function schedulePrintClientWarmup(): void {
  if (isHiprintSocketOpened()) {
    return;
  }
  queueMicrotask(() => {
    const fallbackHost = getHiprintSocketHost();
    void connectHiprintClient(fallbackHost, WARMUP_CONNECT_TIMEOUT_MS).catch(() => undefined);
    prefetchHiprintSocketHost();
  });
}

/** 应用启动后可调用，提前建立 Socket 连接（不阻塞） */
export function warmupHiprintClient(): void {
  schedulePrintClientWarmup();
}

function resolveConnectTimeoutMs(): number {
  return isHiprintHealthRecentlyUnavailable()
    ? WARMUP_CONNECT_TIMEOUT_MS
    : DEFAULT_CONNECT_TIMEOUT_MS;
}

function isHiprintSocketOpened(): boolean {
  const w = window as Window & { hiwebSocket?: { opened?: boolean } };
  return w.hiwebSocket?.opened === true;
}

/** 建立 hiprint Socket 连接 */
export function connectHiprintClient(
  host: string,
  timeoutMs = DEFAULT_CONNECT_TIMEOUT_MS,
): Promise<boolean> {
  ensureHiprintPrintRuntime(host);
  if (isHiprintSocketOpened()) {
    return Promise.resolve(true);
  }
  return new Promise((resolve) => {
    let settled = false;
    const timer = window.setTimeout(() => {
      if (settled) {
        return;
      }
      settled = true;
      resolve(isHiprintSocketOpened());
    }, timeoutMs);
    autoConnect((status) => {
      if (!status) {
        return;
      }
      if (settled) {
        return;
      }
      settled = true;
      window.clearTimeout(timer);
      resolve(true);
    });
  });
}

/**
 * 交给 hiprint 的模板只保留版式结构。
 * 去掉元素 options.testData，以及误写入 PrintJson 根上的样例字段（如 cvlservFlagText:"是"），
 * 避免设计器测试值在真打时冒充业务数据。
 */
function toHiprintTemplateJson(raw: Record<string, unknown>): Record<string, unknown> {
  if (!Array.isArray(raw.panels)) {
    return { panels: [] };
  }
  const panels = (raw.panels as unknown[]).map((panel) => {
    if (!panel || typeof panel !== 'object') {
      return panel;
    }
    const panelObj = panel as Record<string, unknown>;
    const elements = panelObj.printElements;
    if (!Array.isArray(elements)) {
      return panelObj;
    }
    return {
      ...panelObj,
      printElements: elements.map((element) => {
        if (!element || typeof element !== 'object') {
          return element;
        }
        const el = element as Record<string, unknown>;
        const options = el.options;
        if (!options || typeof options !== 'object') {
          return el;
        }
        const nextOptions = { ...(options as Record<string, unknown>) };
        delete nextOptions.testData;
        return { ...el, options: nextOptions };
      }),
    };
  });
  const result: Record<string, unknown> = { panels };
  if (raw.engine !== undefined) {
    result.engine = raw.engine;
  }
  if (raw.schemaVersion !== undefined) {
    result.schemaVersion = raw.schemaVersion;
  }
  return result;
}

/** 屏幕预览加载 print-lock（index.html 仅 media=print，对话框内不生效） */
function ensureScreenPrintLockCss(): void {
  if (document.querySelector('#hiprint-print-lock-screen')) {
    return;
  }
  const link = document.createElement('link');
  link.id = 'hiprint-print-lock-screen';
  link.rel = 'stylesheet';
  link.href = '/print-lock.css';
  document.head.append(link);
}

/** 与 print2 同一套模板实例，用 getHtml 做屏幕预览（不走设计器 init / 浏览器 print） */
export function renderHiprintPreviewHtml(
  printJson: Record<string, unknown>,
  printData: Record<string, unknown>,
): string {
  ensureJquery();
  ensureScreenPrintLockCss();
  ensureHiprintPrintRuntime(getHiprintSocketHost());
  const templateJson = toHiprintTemplateJson(printJson);
  if (!Array.isArray(templateJson.panels) || templateJson.panels.length === 0) {
    throw new Error('打印模板缺少 panels，无法预览');
  }
  const template = new hiprint.PrintTemplate({
    template: templateJson,
  }) as unknown as HiprintPrintTemplate;
  if (typeof template.getHtml !== 'function') {
    throw new Error('hiprint 不支持 getHtml 预览');
  }
  const htmlResult = template.getHtml(printData);
  if (typeof htmlResult === 'string') {
    return htmlResult;
  }
  return (
    $('<div></div>')
      .append(htmlResult as never)
      .html() ?? ''
  );
}

/** 统计预览 HTML 中的联单折数（只计最外层纸张） */
export function countHiprintPreviewFolds(previewHtml: string): number {
  const doc = new DOMParser().parseFromString(previewHtml, 'text/html');
  const papers = [...doc.querySelectorAll('.hiprint-printPaper')].filter((el) =>
    isTopLevelHiprintPaper(el as HTMLElement),
  );
  return Math.max(1, papers.length);
}

function extractErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }
  if (error && typeof error === 'object' && 'msg' in error) {
    const msg = (error as { msg?: unknown }).msg;
    if (typeof msg === 'string' && msg.trim()) {
      return msg;
    }
  }
  return '打印失败';
}

function waitPrintJob(
  template: HiprintPrintTemplate,
  start: () => void,
  timeoutMs = DEFAULT_PRINT_TIMEOUT_MS,
): Promise<void> {
  return new Promise((resolve, reject) => {
    let settled = false;
    const onSuccess = () => {
      if (settled) {
        return;
      }
      settled = true;
      window.clearTimeout(timer);
      cleanup();
      resolve();
    };
    const onError = (error?: unknown) => {
      if (settled) {
        return;
      }
      settled = true;
      window.clearTimeout(timer);
      cleanup();
      reject(new Error(extractErrorMessage(error)));
    };
    const cleanup = () => {
      template.off?.('printSuccess', onSuccess);
      template.off?.('printError', onError);
    };
    const timer = window.setTimeout(() => {
      onError(new Error('打印超时'));
    }, timeoutMs);

    template.on('printSuccess', onSuccess);
    template.on('printError', onError);
    try {
      start();
    } catch (error) {
      onError(error);
    }
  });
}

function printOnce(
  template: HiprintPrintTemplate,
  printData: Record<string, unknown> | Record<string, unknown>[],
  options: HiprintPrint2Options,
  timeoutMs = DEFAULT_PRINT_TIMEOUT_MS,
): Promise<void> {
  return waitPrintJob(
    template,
    () => {
      template.print2(printData, {
        printer: options.printer ?? '',
        title: options.title ?? 'ZXPlat',
        pageSize: options.pageSize,
        landscape: options.landscape,
        duplexMode: options.duplexMode,
        styleHandler: options.styleHandler,
        copies: options.copies ?? 1,
        silent: options.silent ?? true,
      });
    },
    timeoutMs,
  );
}

/** 一折 HTML 送 printByHtml2 */
function printHtmlOnce(
  template: HiprintPrintTemplate,
  html: HTMLElement,
  options: HiprintPrint2Options,
  timeoutMs = DEFAULT_PRINT_TIMEOUT_MS,
): Promise<void> {
  if (typeof template.printByHtml2 !== 'function') {
    throw new Error('hiprint 不支持按折打印');
  }
  return waitPrintJob(
    template,
    () => {
      template.printByHtml2?.(html, {
        printer: options.printer ?? '',
        title: options.title ?? 'ZXPlat',
        pageSize: options.pageSize,
        landscape: options.landscape,
        duplexMode: options.duplexMode,
        styleHandler: options.styleHandler,
        copies: 1,
        silent: options.silent ?? true,
      });
    },
    timeoutMs,
  );
}

/** 联单：按预览的每一折依次送打，避免第二折被裁进第一折 */
async function printFormFolds(
  template: HiprintPrintTemplate,
  printData: Record<string, unknown>,
  copies: number,
  options: HiprintPrint2Options,
): Promise<void> {
  if (typeof template.getHtml !== 'function' || typeof template.printByHtml2 !== 'function') {
    await printOnce(
      template,
      copies > 1 ? Array.from({ length: copies }, () => printData) : printData,
      {
        ...options,
        copies: 1,
      },
    );
    return;
  }
  let printed = 0;
  // 每一份是完整单据；表格超出一折时 hiprint 会拆成多张 paper
  for (let copyIndex = 0; copyIndex < copies; copyIndex += 1) {
    const papers = listHiprintPapers(template.getHtml(printData));
    if (papers.length === 0) {
      throw new Error('联单预览未生成纸张，无法打印');
    }
    // 与预览一致：第 1 折、第 2 折依次送针式
    for (const paper of papers) {
      if (printed > 0) {
        await sleepMs(400);
      }
      await printHtmlOnce(template, wrapHiprintPaper(paper), options);
      printed += 1;
    }
  }
}

/** 经 electron-hiprint（17521）静默直打 */
export async function postHiprintPrint(
  request: HiprintPrintRequest,
): Promise<HiprintPrintResponse> {
  const host = await resolveHiprintHostAsync();
  const connected = await connectHiprintClient(host, resolveConnectTimeoutMs());
  if (!connected) {
    return {
      ok: false,
      code: PRINT_ERROR_CODE.CLIENT_OFFLINE,
      message: `无法连接 Hiprint 打印客户端（${host}），请确认 electron-hiprint 已启动`,
    };
  }

  const scenarioCode = request.scenarioCode?.trim();
  const isWristband = scenarioCode === PRINT_SCENARIO.ipWristbandPrint;
  // 腕带：不用库内横版 + 元素 rotate（易撑破纸宽一次出两页）；改用走纸竖版 + formatter 内旋转进位图
  const printJson = isWristband ? buildWristbandFeedPrintJson() : request.PrintJson;

  const template = new hiprint.PrintTemplate({
    template: toHiprintTemplateJson(printJson),
  }) as unknown as HiprintPrintTemplate;

  const copies = Math.max(1, request.copies ?? 1);
  const printer = request.printer?.trim() || undefined;
  const title = request.jobName?.trim() || 'ZXPlat';
  const panel = readFirstPanel(printJson);
  const pageOpts =
    request.pageSize !== undefined || request.landscape !== undefined
      ? { pageSize: request.pageSize, landscape: request.landscape }
      : resolveHiprintPageSize(printJson, scenarioCode);

  // 仅非联单且模板声明 panelPageRule=none 时按内容撑高（瓶签等）
  const useContentHeight =
    request.pageSize === undefined &&
    request.repeatPrintData !== true &&
    isContentHeightPanel(panel) &&
    panel &&
    typeof panel.width === 'number' &&
    panel.width > 0;

  let contentHeightStyleHandler: (() => string) | undefined;
  let resolvedPageOpts = pageOpts;
  if (useContentHeight && panel && typeof panel.width === 'number') {
    const contentHeightMm = measurePrintContentHeightMm(template, request.printData);
    if (contentHeightMm != null && contentHeightMm > 0) {
      const widthMm = panel.width;
      const heightMm = contentHeightMm + 8;
      resolvedPageOpts = {
        pageSize: mmToMicronPageSize(widthMm, heightMm),
        landscape: false,
      };
      contentHeightStyleHandler = buildContentHeightPrintStyleHandler(heightMm);
    } else {
      console.warn('[print] content-height measure failed, fallback panel size', {
        scenarioCode,
        contentHeightMm,
      });
    }
  } else if (
    request.repeatPrintData === true &&
    panel &&
    typeof panel.height === 'number' &&
    panel.height > 0
  ) {
    contentHeightStyleHandler = buildFormFoldPrintStyleHandler(panel.height);
  }

  const printOptions: HiprintPrint2Options = {
    printer,
    title,
    pageSize: resolvedPageOpts.pageSize,
    landscape: false,
    duplexMode: request.duplexMode,
    styleHandler: contentHeightStyleHandler,
    copies: request.repeatPrintData === true ? 1 : copies,
    silent: request.silent ?? true,
  };

  try {
    if (request.repeatPrintData === true) {
      await printFormFolds(template, request.printData, copies, printOptions);
    } else {
      await printOnce(template, request.printData, printOptions);
    }
    return {
      ok: true,
      printer,
      copies,
    };
  } catch (error) {
    const message = extractErrorMessage(error);
    const busy = /忙|busy/i.test(message);
    const printerMissing =
      /printer|打印机/i.test(message) && /not found|找不到|未找到|不存在|不可用/i.test(message);
    return {
      ok: false,
      code: busy
        ? PRINT_ERROR_CODE.PRINT_BUSY
        : printerMissing
          ? PRINT_ERROR_CODE.PRINTER_NOT_FOUND
          : PRINT_ERROR_CODE.PRINT_FAILED,
      message,
      retryAfterMs: busy ? 500 : undefined,
    };
  }
}
