import type { PrintOptions, PrintResult } from './types';

import { ElMessage } from 'element-plus';

import { confirmPrintApi, preparePrintApi } from '#/api/print/print-job';

import {
  clampFormCopies,
  isInvFormTriplePaper,
  normalizeInvFormPaperKind,
  normalizeInvFormTripleSpec,
} from '../form-print-preference';
import { resolvePrintData } from '../mappers/registry';
import { openPrintPreviewDialog } from '../open-print-preview-dialog';
import { getPrinterPreference, savePrinterPreference } from '../printer-preference';
import { resolvePrinterForPrint } from '../resolve-printer';
import { resolvePrintErrorMessage } from './errors';
import { countHiprintPreviewFolds, renderHiprintPreviewHtml } from './hiprint-client';
import { notifyPrintError } from './print-user-error';
import {
  filterPrintJsonByPages,
  normalizePageIndexes,
  resolveDuplexModeForPrint,
} from './print-page-selection';
import { postHiprintPrintWithRetry } from './retry';
import { PRINT_CLIENT_TYPE, PRINT_ERROR_CODE } from './types';

import '#/utils/print/mappers';

export { PRINT_SCENARIO, type PrintScenario } from '../constants/print-scenario';

export { groupSlipRowsByPrescriptionNo } from '../mappers/outpatient-prescription';
export { resolveOutpatientPrescriptionPrintScenario } from '../resolve-outpatient-prescription-scenario';
export { PrintRuntimeError, resolvePrintErrorMessage } from './errors';
export {
  copyPrintChromeFlagUrl,
  notifyPrintError,
  PRINT_CHROME_FLAG_INTRO,
  PRINT_CHROME_FLAG_MESSAGE,
  PRINT_CHROME_FLAG_URL,
  PRINT_PLUGIN_INSTALL_BULLETS,
  PRINT_PLUGIN_INSTALL_MESSAGE,
  PRINT_TROUBLESHOOTING_MESSAGE,
  shouldShowPrintTroubleshooting,
} from './print-user-error';

/**
 * 主流程伴随打印：微任务异步执行，吞掉未预期异常，不阻塞业务操作。
 */
export function scheduleBackgroundPrint(task: () => Promise<unknown>): void {
  queueMicrotask(() => {
    void Promise.resolve()
      .then(task)
      .catch((error) => {
        console.warn('[print] 伴随打印任务异常', error);
      });
  });
}

/** 业务打印统一入口：mapper → prepare → 预览或 electron-hiprint 静默直打 → confirm（不抛异常） */
export async function print<TSource>(options: PrintOptions<TSource>): Promise<PrintResult> {
  try {
    if (options.preview) {
      return await runPreview(options);
    }
    return await runPrint(options);
  } catch (error) {
    const message =
      error instanceof Error
        ? error.message
        : resolvePrintErrorMessage(PRINT_ERROR_CODE.PRINT_FAILED);
    console.warn('[print] 未预期异常', error);
    reportPrintUserError(options, PRINT_ERROR_CODE.PRINT_FAILED, message);
    return {
      ok: false,
      errorCode: PRINT_ERROR_CODE.PRINT_FAILED,
      errorMessage: message,
    };
  }
}

function shouldNotifyPrintUser(options: PrintOptions): boolean {
  return options.background !== true;
}

function reportPrintUserError(options: PrintOptions, code: string, message: string): void {
  if (!shouldNotifyPrintUser(options)) {
    console.warn('[print] 静默打印失败', code, message);
    return;
  }
  notifyPrintError({ code, message });
}

function reportPrintUserMessage(options: PrintOptions, message: string): void {
  if (!shouldNotifyPrintUser(options)) {
    console.warn('[print] 静默打印失败', message);
    return;
  }
  ElMessage.error(message);
}

interface PreparedPrintContext {
  printJson: Record<string, unknown>;
  sourcePrintJson: Record<string, unknown>;
  printData: Record<string, unknown>;
  traceId: string;
  busNo: string;
  scenarioCode: string;
  templateVersion?: number;
}

type PreparePrintContextResult =
  | { ok: true; ctx: PreparedPrintContext }
  | { ok: false; result: PrintResult };

/** mapper + preparePrint，预览与静默打印共用 */
async function resolvePreparedPrintContext<TSource>(
  options: PrintOptions<TSource>,
): Promise<PreparePrintContextResult> {
  const scenarioCode = options.scenarioCode?.trim();
  if (!scenarioCode) {
    const message = 'scenarioCode 不能为空';
    reportPrintUserMessage(options, message);
    return {
      ok: false,
      result: { ok: false, errorCode: PRINT_ERROR_CODE.INVALID_OPTIONS, errorMessage: message },
    };
  }

  const hasSource = options.source !== undefined && options.source !== null;
  const hasPrintData = options.printData !== undefined && options.printData !== null;
  if (hasSource === hasPrintData) {
    const message = 'source 与 printData 须二选一';
    reportPrintUserMessage(options, message);
    return {
      ok: false,
      result: { ok: false, errorCode: PRINT_ERROR_CODE.INVALID_OPTIONS, errorMessage: message },
    };
  }

  let printData: Record<string, unknown>;
  try {
    printData = hasPrintData
      ? (options.printData as Record<string, unknown>)
      : resolvePrintData(scenarioCode, options.source);
  } catch (error) {
    const message =
      error instanceof Error
        ? error.message
        : resolvePrintErrorMessage(PRINT_ERROR_CODE.MAPPER_NOT_FOUND);
    reportPrintUserMessage(options, message);
    return {
      ok: false,
      result: { ok: false, errorCode: PRINT_ERROR_CODE.MAPPER_NOT_FOUND, errorMessage: message },
    };
  }

  let prepared: Awaited<ReturnType<typeof preparePrintApi>>;
  try {
    prepared = await preparePrintApi({ scenarioCode });
  } catch (error) {
    const message =
      error instanceof Error
        ? error.message
        : resolvePrintErrorMessage(PRINT_ERROR_CODE.PREPARE_FAILED);
    reportPrintUserMessage(options, message);
    return {
      ok: false,
      result: { ok: false, errorCode: PRINT_ERROR_CODE.PREPARE_FAILED, errorMessage: message },
    };
  }

  const traceId = prepared.traceId?.trim();
  const resolvedBusNo = prepared.busNo?.trim();
  const preparedJson = prepared.PrintJson;
  if (!traceId || !resolvedBusNo || !preparedJson || Object.keys(preparedJson).length === 0) {
    const message = resolvePrintErrorMessage(PRINT_ERROR_CODE.MISSING_PRINT_JSON);
    reportPrintUserMessage(options, message);
    return {
      ok: false,
      result: {
        ok: false,
        traceId,
        busNo: resolvedBusNo,
        scenarioCode,
        errorCode: PRINT_ERROR_CODE.MISSING_PRINT_JSON,
        errorMessage: message,
      },
    };
  }

  // 报表/库房纸张等场景可在送打前改列；默认深拷贝后原样使用
  const sourcePrintJson: Record<string, unknown> = structuredClone(preparedJson);
  let printJson: Record<string, unknown> = sourcePrintJson;
  if (options.transformPrintJson) {
    try {
      printJson = options.transformPrintJson(structuredClone(sourcePrintJson), {
        formPaperKind: options.formPaperKind,
        formTripleSpec: options.formTripleSpec,
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : '打印模板列转换失败';
      reportPrintUserMessage(options, message);
      return {
        ok: false,
        result: {
          ok: false,
          traceId,
          busNo: resolvedBusNo,
          scenarioCode,
          errorCode: PRINT_ERROR_CODE.PREPARE_FAILED,
          errorMessage: message,
        },
      };
    }
  }

  return {
    ok: true,
    ctx: {
      printJson,
      sourcePrintJson,
      printData,
      traceId,
      busNo: resolvedBusNo,
      scenarioCode,
      templateVersion: prepared.templateVersion,
    },
  };
}

/** 转义预览失败文案，避免塞进 v-html */
function escapePreviewErrorText(text: string): string {
  return text
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
}

/** 屏幕预览：hiprint getHtml；预览内打印走 print2，不写预览审计 */
async function runPreview<TSource>(options: PrintOptions<TSource>): Promise<PrintResult> {
  const prepared = await resolvePreparedPrintContext(options);
  if (!prepared.ok) {
    return prepared.result;
  }
  const { ctx } = prepared;
  const showFormPaperKind = options.showFormPaperKind === true;
  let currentPaperKind = options.formPaperKind
    ? normalizeInvFormPaperKind(options.formPaperKind)
    : undefined;
  let currentTripleSpec = options.formTripleSpec
    ? normalizeInvFormTripleSpec(options.formTripleSpec)
    : undefined;

  /** 按纸张与规格从源模板重建 PrintJson */
  function rebuildPrintJson(
    kind: NonNullable<typeof currentPaperKind>,
    spec?: typeof currentTripleSpec,
  ): Record<string, unknown> {
    if (!options.transformPrintJson) {
      return structuredClone(ctx.sourcePrintJson);
    }
    return options.transformPrintJson(structuredClone(ctx.sourcePrintJson), {
      formPaperKind: kind,
      formTripleSpec: spec,
    });
  }

  /** 渲染当前模板预览 HTML */
  function renderPreviewHtml(printJson: Record<string, unknown>): string {
    try {
      return renderHiprintPreviewHtml(printJson, ctx.printData);
    } catch (error) {
      const message = error instanceof Error ? error.message : '未知错误';
      console.warn('[print] hiprint 预览渲染失败', error);
      return `<p style="padding:16px;color:#c0392b;">预览渲染失败：${escapePreviewErrorText(message)}</p>`;
    }
  }

  const previewHtml = renderPreviewHtml(ctx.printJson);
  const isTriple = currentPaperKind ? isInvFormTriplePaper(currentPaperKind) : false;
  const printerPref = getPrinterPreference(ctx.scenarioCode);
  try {
    await openPrintPreviewDialog({
      previewHtml,
      foldCount: countHiprintPreviewFolds(previewHtml),
      copies: options.copies ?? 1,
      continuousForm: options.repeatPrintData === true || (showFormPaperKind && isTriple),
      formPaperKind: currentPaperKind,
      formTripleSpec: currentTripleSpec,
      showFormPaperKind,
      initialPrinterName: printerPref?.printerName,
      onFormPaperChange: showFormPaperKind
        ? (payload) => {
            currentPaperKind = payload.paperKind;
            currentTripleSpec = payload.tripleSpec;
            ctx.printJson = rebuildPrintJson(payload.paperKind, payload.tripleSpec);
            const html = renderPreviewHtml(ctx.printJson);
            return {
              previewHtml: html,
              foldCount: countHiprintPreviewFolds(html),
            };
          }
        : undefined,
      onPrint: (copies, formPaperKind, tripleSpec, printerName, pageSelection) => {
        const nextCopies = clampFormCopies(copies);
        const nextKind = formPaperKind
          ? normalizeInvFormPaperKind(formPaperKind)
          : currentPaperKind;
        const nextSpec = tripleSpec ? normalizeInvFormTripleSpec(tripleSpec) : currentTripleSpec;
        const nextIsTriple = nextKind ? isInvFormTriplePaper(nextKind) : false;
        if (showFormPaperKind && nextKind) {
          ctx.printJson = rebuildPrintJson(nextKind, nextSpec);
        }
        const printer = printerName?.trim();
        // 预览里已选打印机：落盘偏好并跳过第二次选机弹窗
        if (showFormPaperKind && printer) {
          savePrinterPreference(ctx.scenarioCode, {
            printerName: printer,
            skipPrompt: printerPref?.skipPrompt !== false,
            updatedAt: new Date().toISOString(),
          });
        }
        return executePreparedPrint(
          {
            ...options,
            copies: nextCopies,
            repeatPrintData: showFormPaperKind ? nextIsTriple : options.repeatPrintData,
            formPaperKind: nextKind,
            formTripleSpec: nextSpec,
            printer: showFormPaperKind ? printer || options.printer : options.printer,
            selectPrinter: showFormPaperKind ? 'never' : options.selectPrinter,
            showPaperKind: showFormPaperKind ? false : options.showPaperKind,
            pageSelection,
          },
          ctx,
        );
      },
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : '打印预览失败';
    reportPrintUserMessage(options, message);
    return {
      ok: false,
      traceId: ctx.traceId,
      busNo: ctx.busNo,
      scenarioCode: ctx.scenarioCode,
      errorCode: PRINT_ERROR_CODE.PRINT_FAILED,
      errorMessage: message,
    };
  }
  return {
    ok: true,
    traceId: ctx.traceId,
    busNo: ctx.busNo,
    scenarioCode: ctx.scenarioCode,
  };
}

/** 已准备模板：选打印机后 hiprint print2 + 审计 */
async function executePreparedPrint<TSource>(
  options: PrintOptions<TSource>,
  ctx: PreparedPrintContext,
): Promise<PrintResult> {
  const resolvedPrinter = await resolvePrinterForPrint({
    scenarioCode: ctx.scenarioCode,
    scenarioLabel: options.scenarioLabel,
    printer: options.printer,
    selectPrinter: options.selectPrinter,
    showPaperKind: options.showPaperKind,
    initialPaperKind: options.formPaperKind,
    initialTripleSpec: options.formTripleSpec,
    showServiceApplyPaperSize: options.showServiceApplyPaperSize,
    initialServiceApplyPaperSize: options.serviceApplyPaperSize,
  });
  if (resolvedPrinter.cancelled) {
    return {
      ok: false,
      errorCode: PRINT_ERROR_CODE.USER_CANCELLED,
      errorMessage: '已取消打印',
    };
  }
  return sendPreparedToHiprint(options, ctx, resolvedPrinter.printer ?? options.printer);
}

/** hiprint print2 直打并 confirm（不走浏览器 window.print） */
async function sendPreparedToHiprint<TSource>(
  options: PrintOptions<TSource>,
  ctx: PreparedPrintContext,
  printer?: string,
): Promise<PrintResult> {
  const panelCount = Array.isArray(ctx.printJson.panels)
    ? (ctx.printJson.panels as unknown[]).length
    : 0;
  const pageIndexes =
    options.pageSelection?.pageIndexes && panelCount > 0
      ? normalizePageIndexes(options.pageSelection.pageIndexes, panelCount)
      : undefined;
  const printJson =
    pageIndexes && pageIndexes.length > 0 && pageIndexes.length < panelCount
      ? filterPrintJsonByPages(ctx.printJson, pageIndexes)
      : ctx.printJson;
  const duplexMode = resolveDuplexModeForPrint(
    pageIndexes
      ? { pageIndexes, duplexMode: options.pageSelection?.duplexMode }
      : options.pageSelection,
  );

  const hiprintResult = await postHiprintPrintWithRetry({
    PrintJson: printJson,
    printData: ctx.printData,
    scenarioCode: ctx.scenarioCode,
    printer,
    copies: options.copies ?? 1,
    silent: options.silent ?? true,
    jobName: options.jobName ?? ctx.busNo,
    repeatPrintData: options.repeatPrintData,
    duplexMode,
  });

  if (hiprintResult.ok) {
    await safeConfirmPrint({
      traceId: ctx.traceId,
      busNo: ctx.busNo,
      scenarioCode: ctx.scenarioCode,
      templateVersion: ctx.templateVersion,
      status: 'SUCCESS',
      printData: ctx.printData,
      bizRef: options.bizRef,
      copies: hiprintResult.copies ?? options.copies,
      printer: hiprintResult.printer ?? printer,
    });
    return {
      ok: true,
      traceId: ctx.traceId,
      busNo: ctx.busNo,
      scenarioCode: ctx.scenarioCode,
      printer: hiprintResult.printer,
      copies: hiprintResult.copies,
    };
  }

  const errorCode = normalizeHiprintErrorCode(hiprintResult.code, hiprintResult.message);
  const errorMessage = hiprintResult.message || resolvePrintErrorMessage(errorCode);
  await safeConfirmPrint({
    traceId: ctx.traceId,
    busNo: ctx.busNo,
    scenarioCode: ctx.scenarioCode,
    templateVersion: ctx.templateVersion,
    status: 'FAILED',
    printData: ctx.printData,
    bizRef: options.bizRef,
    copies: options.copies,
    printer,
    errorCode,
    errorMessage: hiprintResult.message || errorMessage,
  });
  reportPrintUserError(options, errorCode, errorMessage);
  return {
    ok: false,
    traceId: ctx.traceId,
    busNo: ctx.busNo,
    scenarioCode: ctx.scenarioCode,
    errorCode,
    errorMessage,
  };
}

async function runPrint<TSource>(options: PrintOptions<TSource>): Promise<PrintResult> {
  const scenarioCode = options.scenarioCode?.trim();
  if (!scenarioCode) {
    const message = 'scenarioCode 不能为空';
    reportPrintUserMessage(options, message);
    return { ok: false, errorCode: PRINT_ERROR_CODE.INVALID_OPTIONS, errorMessage: message };
  }

  const resolvedPrinter = await resolvePrinterForPrint({
    scenarioCode,
    scenarioLabel: options.scenarioLabel,
    printer: options.printer,
    selectPrinter: options.selectPrinter,
    showPaperKind: options.showPaperKind,
    initialPaperKind: options.formPaperKind,
    initialTripleSpec: options.formTripleSpec,
    showServiceApplyPaperSize: options.showServiceApplyPaperSize,
    initialServiceApplyPaperSize: options.serviceApplyPaperSize,
  });
  if (resolvedPrinter.cancelled) {
    return {
      ok: false,
      errorCode: PRINT_ERROR_CODE.USER_CANCELLED,
      errorMessage: '已取消打印',
    };
  }

  let printOptions = options;
  if (options.showPaperKind && resolvedPrinter.paperKind) {
    printOptions = {
      ...printOptions,
      formPaperKind: resolvedPrinter.paperKind,
      formTripleSpec: resolvedPrinter.tripleSpec,
      repeatPrintData: isInvFormTriplePaper(resolvedPrinter.paperKind),
    };
  }
  // 申请单弹窗改纸张：按最终纸张切换 A4/A5 场景后再 prepare
  if (
    options.showServiceApplyPaperSize &&
    resolvedPrinter.resolvedScenarioCode &&
    resolvedPrinter.resolvedScenarioCode !== scenarioCode
  ) {
    printOptions = {
      ...printOptions,
      scenarioCode: resolvedPrinter.resolvedScenarioCode,
    };
  }
  if (options.showServiceApplyPaperSize && resolvedPrinter.serviceApplyPaperSize) {
    printOptions = {
      ...printOptions,
      serviceApplyPaperSize: resolvedPrinter.serviceApplyPaperSize,
      bizRef: {
        ...(printOptions.bizRef ?? {}),
        paperSize: resolvedPrinter.serviceApplyPaperSize,
        scenarioCode: printOptions.scenarioCode,
      },
    };
  }

  const prepared = await resolvePreparedPrintContext(printOptions);
  if (!prepared.ok) {
    return prepared.result;
  }
  return sendPreparedToHiprint(
    printOptions,
    prepared.ctx,
    resolvedPrinter.printer ?? printOptions.printer,
  );
}

interface ConfirmPayload {
  traceId: string;
  busNo: string;
  scenarioCode: string;
  templateVersion?: number;
  status: 'FAILED' | 'SUCCESS';
  printData: Record<string, unknown>;
  bizRef?: Record<string, unknown>;
  copies?: number;
  printer?: string;
  errorCode?: string;
  errorMessage?: string;
}

/** 审计落库失败不阻断用户感知，仅打 warn */
async function safeConfirmPrint(payload: ConfirmPayload): Promise<void> {
  try {
    await confirmPrintApi({
      traceId: payload.traceId,
      busNo: payload.busNo,
      scenarioCode: payload.scenarioCode,
      templateVersion: payload.templateVersion,
      status: payload.status,
      printData: payload.printData,
      bizRef: payload.bizRef,
      copies: payload.copies,
      printer: payload.printer,
      clientType: PRINT_CLIENT_TYPE,
      errorCode: payload.errorCode,
      errorMessage: payload.errorMessage,
    });
  } catch (error) {
    console.warn('打印审计确认失败', error);
  }
}

function normalizeHiprintErrorCode(code: string, message?: string): string {
  if (code === 'PRINT_BUSY') {
    return PRINT_ERROR_CODE.PRINT_BUSY;
  }
  if (code === 'PRINTER_NOT_FOUND') {
    return PRINT_ERROR_CODE.PRINTER_NOT_FOUND;
  }
  const text = message?.trim() ?? '';
  if (/printer|打印机/i.test(text) && /not found|找不到|未找到|不存在|不可用/i.test(text)) {
    return PRINT_ERROR_CODE.PRINTER_NOT_FOUND;
  }
  return code || PRINT_ERROR_CODE.PRINT_FAILED;
}

export type { PrintOptions, PrintResult, PrintTransformContext } from './types';
