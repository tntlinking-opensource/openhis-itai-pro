import type { HiprintPrinterItem } from './fetch-printer-list';
import type { InvFormPaperKind } from './form-print-preference';
import type { PrinterPreference } from './printer-preference';

import { isInvPrintScenario } from './constants/print-scenario';
import { resolvePrintScenarioLabel } from './constants/print-scenario-labels';
import { fetchHiprintPrinterList, isPrinterAvailable } from './fetch-printer-list';
import {
  getFormPrintPreference,
  INV_FORM_PAPER_HEIGHT_MM,
  INV_FORM_PRINT_PREF_KEY,
  normalizeInvFormPaperKind,
  saveFormPrintPreference,
} from './form-print-preference';
import { openPrinterSelectDialog } from './open-printer-select-dialog';
import { getPrinterPreference, savePrinterPreference } from './printer-preference';

export type PrinterSelectMode = 'always' | 'auto' | 'never';

export interface ResolvePrinterOptions {
  scenarioCode: string;
  /** 调用方显式指定时跳过偏好解析 */
  printer?: string;
  /** auto=按偏好自动；always=强制弹窗；never=不弹窗（默认 auto） */
  selectPrinter?: PrinterSelectMode;
  scenarioLabel?: string;
  /** settings 模式仅保存偏好，确认按钮文案为「保存」 */
  dialogMode?: 'print' | 'settings';
  /** 库房单据：同窗选择纸张 */
  showPaperKind?: boolean;
  initialPaperKind?: InvFormPaperKind;
}

export interface ResolvePrinterResult {
  printer?: string;
  cancelled: boolean;
  paperKind?: InvFormPaperKind;
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

/** 弹窗标题：库房打印带纸张 */
function resolveDialogTitle(
  dialogMode: 'print' | 'settings' | undefined,
  showPaperKind: boolean,
): string {
  if (dialogMode === 'settings') {
    return showPaperKind ? '设置纸张与打印机' : '设置默认打印机';
  }
  return showPaperKind ? '选择纸张与打印机' : '选择打印机';
}

/** 打开选择弹窗并落盘打印机（及库房纸张）偏好 */
async function promptAndSave(options: {
  scenarioCode: string;
  scenarioLabel: string;
  dialogMode?: 'print' | 'settings';
  showPaperKind: boolean;
  initialPaperKind?: InvFormPaperKind;
  initialPrinterName?: string;
  initialSkipPrompt: boolean;
  unavailableHint: string;
}): Promise<ResolvePrinterResult> {
  const selected = await openPrinterSelectDialog({
    bindingLabel: options.scenarioLabel,
    title: resolveDialogTitle(options.dialogMode, options.showPaperKind),
    confirmText: options.dialogMode === 'settings' ? '保存' : '打印',
    initialPrinterName: options.initialPrinterName,
    initialSkipPrompt: options.initialSkipPrompt,
    unavailableHint: options.unavailableHint,
    loadPrinters: fetchHiprintPrinterList,
    showPaperKind: options.showPaperKind,
    initialPaperKind: options.initialPaperKind,
  });
  if (!selected) {
    return { cancelled: true };
  }
  savePrinterPreference(options.scenarioCode, {
    printerName: selected.printerName,
    skipPrompt: selected.skipPrompt,
    updatedAt: new Date().toISOString(),
  });
  let paperKind: InvFormPaperKind | undefined;
  if (options.showPaperKind) {
    paperKind = normalizeInvFormPaperKind(selected.paperKind ?? options.initialPaperKind);
    // 设置页，或勾选「下次不再提示」时，把纸张一并写成默认
    if (options.dialogMode === 'settings' || selected.skipPrompt) {
      const formPref = getFormPrintPreference(INV_FORM_PRINT_PREF_KEY);
      saveFormPrintPreference(INV_FORM_PRINT_PREF_KEY, {
        paperHeightMm: formPref.paperHeightMm || INV_FORM_PAPER_HEIGHT_MM,
        copies: formPref.copies,
        paperKind,
        persistPaperKind: true,
      });
    }
  }
  return { printer: selected.printerName, cancelled: false, paperKind };
}

/** 解析本次打印应使用的打印机（必要时弹窗） */
export async function resolvePrinterForPrint(
  options: ResolvePrinterOptions,
): Promise<ResolvePrinterResult> {
  const scenarioCode = options.scenarioCode.trim();
  const showPaperKind = options.showPaperKind === true;
  const explicitPrinter = options.printer?.trim();
  if (explicitPrinter && !showPaperKind) {
    return { printer: explicitPrinter, cancelled: false };
  }

  const selectPrinter = options.selectPrinter ?? 'auto';
  const scenarioLabel = options.scenarioLabel?.trim() || resolvePrintScenarioLabel(scenarioCode);
  const pref = getPrinterPreference(scenarioCode);
  const paperPref = showPaperKind ? getFormPrintPreference(INV_FORM_PRINT_PREF_KEY) : undefined;
  const initialPaperKind = normalizeInvFormPaperKind(
    options.initialPaperKind ?? paperPref?.paperKind,
  );

  if (selectPrinter === 'never' && !showPaperKind) {
    return { printer: pref?.printerName, cancelled: false };
  }

  let printers: HiprintPrinterItem[] = [];
  try {
    printers = await fetchHiprintPrinterList();
  } catch (error) {
    if (
      selectPrinter === 'auto' &&
      !showPaperKind &&
      pref?.skipPrompt !== false &&
      pref?.printerName
    ) {
      return { printer: pref.printerName, cancelled: false };
    }
    const message =
      error instanceof Error ? error.message : '无法连接打印客户端，请确认 OpenHIS.Win 已启动';
    return promptAndSave({
      scenarioCode,
      scenarioLabel,
      dialogMode: options.dialogMode,
      showPaperKind,
      initialPaperKind,
      initialPrinterName: pref?.printerName,
      initialSkipPrompt: pref?.skipPrompt !== false,
      unavailableHint: message,
    });
  }

  const decision = decidePrompt(scenarioCode, pref, printers, selectPrinter);
  const paperLocked = paperPref?.paperKindFromSettings === true;
  // 纸张后加：旧「不再询问」仍弹一次确认纸张；确认并勾选后才跳过
  if (!decision.shouldPrompt && (!showPaperKind || paperLocked)) {
    return {
      printer: decision.initialPrinterName,
      cancelled: false,
      paperKind: showPaperKind ? initialPaperKind : undefined,
    };
  }

  return promptAndSave({
    scenarioCode,
    scenarioLabel,
    dialogMode: options.dialogMode,
    showPaperKind,
    initialPaperKind,
    initialPrinterName: decision.initialPrinterName || pref?.printerName,
    initialSkipPrompt: decision.initialSkipPrompt,
    unavailableHint: decision.unavailableHint,
  });
}

/** 设置页：仅保存默认打印机（库房场景同时保存纸张），不触发打印 */
export async function configurePrinterPreference(scenarioCode: string): Promise<boolean> {
  const result = await resolvePrinterForPrint({
    scenarioCode,
    selectPrinter: 'always',
    dialogMode: 'settings',
    showPaperKind: isInvPrintScenario(scenarioCode),
  });
  return !result.cancelled && Boolean(result.printer);
}
