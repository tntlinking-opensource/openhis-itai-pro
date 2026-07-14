import type { PrintOptions, PrintResult } from './types';

import { ElMessage } from 'element-plus';

import { confirmPrintApi, preparePrintApi } from '#/api/print/print-job';

import { resolvePrintData } from '../mappers/registry';
import { resolvePrinterForPrint } from '../resolve-printer';
import { resolvePrintErrorMessage } from './errors';
import { notifyPrintError } from './print-user-error';
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

/** 业务打印统一入口：mapper → prepare → electron-hiprint 静默直打 → confirm（不抛异常） */
export async function print<TSource>(options: PrintOptions<TSource>): Promise<PrintResult> {
  try {
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

async function runPrint<TSource>(options: PrintOptions<TSource>): Promise<PrintResult> {
  const scenarioCode = options.scenarioCode?.trim();
  if (!scenarioCode) {
    const message = 'scenarioCode 不能为空';
    reportPrintUserMessage(options, message);
    return { ok: false, errorCode: PRINT_ERROR_CODE.INVALID_OPTIONS, errorMessage: message };
  }

  const hasSource = options.source !== undefined && options.source !== null;
  const hasPrintData = options.printData !== undefined && options.printData !== null;
  if (hasSource === hasPrintData) {
    const message = 'source 与 printData 须二选一';
    reportPrintUserMessage(options, message);
    return { ok: false, errorCode: PRINT_ERROR_CODE.INVALID_OPTIONS, errorMessage: message };
  }

  const resolvedPrinter = await resolvePrinterForPrint({
    scenarioCode,
    scenarioLabel: options.scenarioLabel,
    printer: options.printer,
    selectPrinter: options.selectPrinter,
  });
  if (resolvedPrinter.cancelled) {
    return {
      ok: false,
      errorCode: PRINT_ERROR_CODE.USER_CANCELLED,
      errorMessage: '已取消打印',
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
      errorCode: PRINT_ERROR_CODE.MAPPER_NOT_FOUND,
      errorMessage: message,
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
      errorCode: PRINT_ERROR_CODE.PREPARE_FAILED,
      errorMessage: message,
    };
  }

  const traceId = prepared.traceId?.trim();
  const resolvedBusNo = prepared.busNo?.trim();
  const printJson = prepared.PrintJson;
  if (!traceId || !resolvedBusNo || !printJson || Object.keys(printJson).length === 0) {
    const message = resolvePrintErrorMessage(PRINT_ERROR_CODE.MISSING_PRINT_JSON);
    reportPrintUserMessage(options, message);
    return {
      ok: false,
      traceId,
      busNo: resolvedBusNo,
      scenarioCode,
      errorCode: PRINT_ERROR_CODE.MISSING_PRINT_JSON,
      errorMessage: message,
    };
  }

  const hiprintResult = await postHiprintPrintWithRetry({
    PrintJson: printJson,
    printData,
    scenarioCode,
    printer: resolvedPrinter.printer ?? options.printer,
    copies: options.copies ?? 1,
    silent: options.silent ?? true,
    jobName: options.jobName ?? resolvedBusNo,
  });

  if (hiprintResult.ok) {
    await safeConfirmPrint({
      traceId,
      busNo: resolvedBusNo,
      scenarioCode,
      templateVersion: prepared.templateVersion,
      status: 'SUCCESS',
      printData,
      bizRef: options.bizRef,
      copies: hiprintResult.copies ?? options.copies,
      printer: hiprintResult.printer ?? resolvedPrinter.printer ?? options.printer,
    });
    return {
      ok: true,
      traceId,
      busNo: resolvedBusNo,
      scenarioCode,
      printer: hiprintResult.printer,
      copies: hiprintResult.copies,
    };
  }

  const errorCode = normalizeHiprintErrorCode(hiprintResult.code, hiprintResult.message);
  const errorMessage = hiprintResult.message || resolvePrintErrorMessage(errorCode);
  await safeConfirmPrint({
    traceId,
    busNo: resolvedBusNo,
    scenarioCode,
    templateVersion: prepared.templateVersion,
    status: 'FAILED',
    printData,
    bizRef: options.bizRef,
    copies: options.copies,
    printer: resolvedPrinter.printer ?? options.printer,
    errorCode,
    errorMessage: hiprintResult.message || errorMessage,
  });
  reportPrintUserError(options, errorCode, errorMessage);
  return {
    ok: false,
    traceId,
    busNo: resolvedBusNo,
    scenarioCode,
    errorCode,
    errorMessage,
  };
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

export type { PrintOptions, PrintResult } from './types';
