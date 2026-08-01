import type { HiprintPrinterItem } from './fetch-printer-list';
import type { PrinterPreference } from './printer-preference';

import { resolvePrintScenarioLabel } from './constants/print-scenario-labels';
import { fetchHiprintPrinterList, isPrinterAvailable } from './fetch-printer-list';
import { openPrinterSelectDialog } from './open-printer-select-dialog';
import { getPrinterPreference, savePrinterPreference } from './printer-preference';

export type PrinterSelectMode = 'always' | 'auto' | 'never';

export interface ResolvePrinterOptions {
  scenarioCode: string;
  /** 调用方显式指定时跳过偏好解析 */
  printer?: string;
  /** auto=按偏好自动；always=强制弹窗；never=不弹窗 */
  selectPrinter?: PrinterSelectMode;
  scenarioLabel?: string;
  /** settings 模式仅保存偏好，确认按钮文案为「保存」 */
  dialogMode?: 'print' | 'settings';
}

export interface ResolvePrinterResult {
  printer?: string;
  cancelled: boolean;
}

interface PromptDecision {
  initialPrinterName: string;
  initialSkipPrompt: boolean;
  unavailableHint: string;
  shouldPrompt: boolean;
}

/** 判断是否需要弹出打印机选择框 */
function decidePrompt(
  scenarioCode: string,
  pref: null | PrinterPreference,
  printers: HiprintPrinterItem[],
  selectPrinter: PrinterSelectMode,
): PromptDecision {
  const initialPrinterName = pref?.printerName?.trim() ?? '';
  const initialSkipPrompt = pref?.skipPrompt !== false;
  const available = isPrinterAvailable(initialPrinterName, printers);

  if (selectPrinter === 'always') {
    return {
      initialPrinterName: available ? initialPrinterName : '',
      initialSkipPrompt,
      unavailableHint: available
        ? ''
        : initialPrinterName
          ? `原默认打印机「${initialPrinterName}」不可用，请重新选择`
          : '',
      shouldPrompt: true,
    };
  }

  if (selectPrinter === 'never') {
    return {
      initialPrinterName,
      initialSkipPrompt,
      unavailableHint: '',
      shouldPrompt: false,
    };
  }

  if (!initialPrinterName || !initialSkipPrompt || !available) {
    return {
      initialPrinterName: available ? initialPrinterName : '',
      initialSkipPrompt: pref ? initialSkipPrompt : true,
      unavailableHint:
        initialPrinterName && !available
          ? `原默认打印机「${initialPrinterName}」不可用，请重新选择`
          : '',
      shouldPrompt: true,
    };
  }

  return {
    initialPrinterName,
    initialSkipPrompt,
    unavailableHint: '',
    shouldPrompt: false,
  };
}

/** 解析本次打印应使用的打印机（必要时弹窗） */
export async function resolvePrinterForPrint(
  options: ResolvePrinterOptions,
): Promise<ResolvePrinterResult> {
  const scenarioCode = options.scenarioCode.trim();
  const explicitPrinter = options.printer?.trim();
  if (explicitPrinter) {
    return { printer: explicitPrinter, cancelled: false };
  }

  const selectPrinter = options.selectPrinter ?? 'auto';
  const scenarioLabel = options.scenarioLabel?.trim() || resolvePrintScenarioLabel(scenarioCode);
  const pref = getPrinterPreference(scenarioCode);

  if (selectPrinter === 'never') {
    return { printer: pref?.printerName, cancelled: false };
  }

  let printers: HiprintPrinterItem[] = [];
  try {
    printers = await fetchHiprintPrinterList();
  } catch (error) {
    if (selectPrinter === 'auto' && pref?.skipPrompt !== false && pref?.printerName) {
      return { printer: pref.printerName, cancelled: false };
    }
    const message =
      error instanceof Error ? error.message : '无法连接打印客户端，请确认 OpenHIS.Win 已启动';
    const selected = await openPrinterSelectDialog({
      bindingLabel: scenarioLabel,
      title: options.dialogMode === 'settings' ? '设置默认打印机' : '选择打印机',
      confirmText: options.dialogMode === 'settings' ? '保存' : '打印',
      initialPrinterName: pref?.printerName,
      initialSkipPrompt: pref?.skipPrompt !== false,
      unavailableHint: message,
      loadPrinters: fetchHiprintPrinterList,
    });
    if (!selected) {
      return { cancelled: true };
    }
    savePrinterPreference(scenarioCode, {
      printerName: selected.printerName,
      skipPrompt: selected.skipPrompt,
      updatedAt: new Date().toISOString(),
    });
    return { printer: selected.printerName, cancelled: false };
  }

  const decision = decidePrompt(scenarioCode, pref, printers, selectPrinter);
  if (!decision.shouldPrompt) {
    return { printer: decision.initialPrinterName, cancelled: false };
  }

  const selected = await openPrinterSelectDialog({
    bindingLabel: scenarioLabel,
    title: options.dialogMode === 'settings' ? '设置默认打印机' : '选择打印机',
    confirmText: options.dialogMode === 'settings' ? '保存' : '打印',
    initialPrinterName: decision.initialPrinterName,
    initialSkipPrompt: decision.initialSkipPrompt,
    unavailableHint: decision.unavailableHint,
    loadPrinters: fetchHiprintPrinterList,
  });
  if (!selected) {
    return { cancelled: true };
  }

  savePrinterPreference(scenarioCode, {
    printerName: selected.printerName,
    skipPrompt: selected.skipPrompt,
    updatedAt: new Date().toISOString(),
  });
  return { printer: selected.printerName, cancelled: false };
}

/** 设置页：仅保存默认打印机，不触发打印 */
export async function configurePrinterPreference(scenarioCode: string): Promise<boolean> {
  const result = await resolvePrinterForPrint({
    scenarioCode,
    selectPrinter: 'always',
    dialogMode: 'settings',
  });
  return !result.cancelled && Boolean(result.printer);
}
