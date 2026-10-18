import type { HiprintPrinterItem } from './fetch-printer-list';
import type { InvFormPaperKind, InvFormTripleSpec } from './form-print-preference';
import type { PrinterPreference } from './printer-preference';
import type { ServiceApplyPaperSize } from './service-apply-paper-preference';

import { isInvPrintScenario } from './constants/print-scenario';
import { resolvePrintScenarioLabel } from './constants/print-scenario-labels';
import { fetchHiprintPrinterList, isPrinterAvailable } from './fetch-printer-list';
import {
  getFormPrintPreference,
  INV_FORM_PRINT_PREF_KEY,
  normalizeInvFormPaperKind,
  normalizeInvFormTripleSpec,
  saveFormPrintPreference,
} from './form-print-preference';
import { openPrinterSelectDialog } from './open-printer-select-dialog';
import { getPrinterPreference, savePrinterPreference } from './printer-preference';
import {
  getServiceApplyPaperSizePreference,
  normalizeServiceApplyPaperSize,
  remapServiceApplyScenarioByPaper,
  saveServiceApplyPaperSizePreference,
} from './service-apply-paper-preference';

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
  initialTripleSpec?: InvFormTripleSpec;
  /** 门诊申请单：同窗选择 A4 / A5 */
  showServiceApplyPaperSize?: boolean;
  initialServiceApplyPaperSize?: ServiceApplyPaperSize;
}

export interface ResolvePrinterResult {
  printer?: string;
  cancelled: boolean;
  paperKind?: InvFormPaperKind;
  tripleSpec?: InvFormTripleSpec;
  serviceApplyPaperSize?: ServiceApplyPaperSize;
  /** 申请单改纸张后的最终场景码（可能与入参不同） */
  resolvedScenarioCode?: string;
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

/** 弹窗标题：带纸张时用合一文案 */
function resolveDialogTitle(
  dialogMode: 'print' | 'settings' | undefined,
  showAnyPaper: boolean,
): string {
  if (dialogMode === 'settings') {
    return showAnyPaper ? '设置纸张与打印机' : '设置默认打印机';
  }
  return showAnyPaper ? '选择纸张与打印机' : '选择打印机';
}

/** 打开选择弹窗并落盘打印机（及纸张）偏好 */
async function promptAndSave(options: {
  scenarioCode: string;
  scenarioLabel: string;
  dialogMode?: 'print' | 'settings';
  showPaperKind: boolean;
  initialPaperKind?: InvFormPaperKind;
  initialTripleSpec?: InvFormTripleSpec;
  showServiceApplyPaperSize: boolean;
  initialServiceApplyPaperSize?: ServiceApplyPaperSize;
  initialPrinterName?: string;
  initialSkipPrompt: boolean;
  unavailableHint: string;
}): Promise<ResolvePrinterResult> {
  const showAnyPaper = options.showPaperKind || options.showServiceApplyPaperSize;
  const selected = await openPrinterSelectDialog({
    bindingLabel: options.scenarioLabel,
    title: resolveDialogTitle(options.dialogMode, showAnyPaper),
    confirmText: options.dialogMode === 'settings' ? '保存' : '打印',
    initialPrinterName: options.initialPrinterName,
    initialSkipPrompt: options.initialSkipPrompt,
    unavailableHint: options.unavailableHint,
    loadPrinters: fetchHiprintPrinterList,
    showPaperKind: options.showPaperKind,
    initialPaperKind: options.initialPaperKind,
    initialTripleSpec: options.initialTripleSpec,
    showServiceApplyPaperSize: options.showServiceApplyPaperSize,
    initialServiceApplyPaperSize: options.initialServiceApplyPaperSize,
  });
  if (!selected) {
    return { cancelled: true };
  }

  let paperKind: InvFormPaperKind | undefined;
  let tripleSpec: InvFormTripleSpec | undefined;
  if (options.showPaperKind) {
    paperKind = normalizeInvFormPaperKind(selected.paperKind ?? options.initialPaperKind);
    tripleSpec = normalizeInvFormTripleSpec(selected.tripleSpec ?? options.initialTripleSpec);
    // 设置页，或勾选「下次不再提示」时，把纸张与规格一并写成默认
    if (options.dialogMode === 'settings' || selected.skipPrompt) {
      const formPref = getFormPrintPreference(INV_FORM_PRINT_PREF_KEY);
      saveFormPrintPreference(INV_FORM_PRINT_PREF_KEY, {
        copies: formPref.copies,
        paperKind,
        tripleSpec,
        persistPaperKind: true,
      });
    }
  }

  let serviceApplyPaperSize: ServiceApplyPaperSize | undefined;
  let resolvedScenarioCode = options.scenarioCode;
  if (options.showServiceApplyPaperSize) {
    serviceApplyPaperSize = normalizeServiceApplyPaperSize(
      selected.serviceApplyPaperSize ?? options.initialServiceApplyPaperSize,
    );
    // 打印时按纸张切 A4/A5 场景；设置页只改偏好，不改绑定场景
    if (options.dialogMode !== 'settings') {
      resolvedScenarioCode = remapServiceApplyScenarioByPaper(
        options.scenarioCode,
        serviceApplyPaperSize,
      );
    }
    saveServiceApplyPaperSizePreference(serviceApplyPaperSize);
  }

  savePrinterPreference(resolvedScenarioCode, {
    printerName: selected.printerName,
    skipPrompt: selected.skipPrompt,
    updatedAt: new Date().toISOString(),
  });

  return {
    printer: selected.printerName,
    cancelled: false,
    paperKind,
    tripleSpec,
    serviceApplyPaperSize,
    resolvedScenarioCode:
      options.showServiceApplyPaperSize && options.dialogMode !== 'settings'
        ? resolvedScenarioCode
        : undefined,
  };
}

/** 解析本次打印应使用的打印机（必要时弹窗） */
export async function resolvePrinterForPrint(
  options: ResolvePrinterOptions,
): Promise<ResolvePrinterResult> {
  const scenarioCode = options.scenarioCode.trim();
  const showPaperKind = options.showPaperKind === true;
  const showServiceApplyPaperSize = options.showServiceApplyPaperSize === true;
  const showAnyPaper = showPaperKind || showServiceApplyPaperSize;
  const explicitPrinter = options.printer?.trim();
  if (explicitPrinter && !showAnyPaper) {
    return { printer: explicitPrinter, cancelled: false };
  }

  const selectPrinter = options.selectPrinter ?? 'auto';
  const scenarioLabel = options.scenarioLabel?.trim() || resolvePrintScenarioLabel(scenarioCode);
  const pref = getPrinterPreference(scenarioCode);
  const paperPref = showPaperKind ? getFormPrintPreference(INV_FORM_PRINT_PREF_KEY) : undefined;
  const initialPaperKind = normalizeInvFormPaperKind(
    options.initialPaperKind ?? paperPref?.paperKind,
  );
  const initialTripleSpec = normalizeInvFormTripleSpec(
    options.initialTripleSpec ?? paperPref?.tripleSpec,
  );
  const initialServiceApplyPaperSize = normalizeServiceApplyPaperSize(
    options.initialServiceApplyPaperSize ?? getServiceApplyPaperSizePreference(),
  );

  if (selectPrinter === 'never' && !showAnyPaper) {
    return { printer: pref?.printerName, cancelled: false };
  }

  let printers: HiprintPrinterItem[] = [];
  try {
    printers = await fetchHiprintPrinterList();
  } catch (error) {
    if (
      selectPrinter === 'auto' &&
      !showAnyPaper &&
      pref?.skipPrompt !== false &&
      pref?.printerName
    ) {
      return { printer: pref.printerName, cancelled: false };
    }
    const message =
      error instanceof Error ? error.message : '无法连接打印客户端，请确认 Zxplat.Win 已启动';
    return promptAndSave({
      scenarioCode,
      scenarioLabel,
      dialogMode: options.dialogMode,
      showPaperKind,
      initialPaperKind,
      initialTripleSpec,
      showServiceApplyPaperSize,
      initialServiceApplyPaperSize,
      initialPrinterName: pref?.printerName,
      initialSkipPrompt: pref?.skipPrompt !== false,
      unavailableHint: message,
    });
  }

  const decision = decidePrompt(scenarioCode, pref, printers, selectPrinter);
  const paperLocked = paperPref?.paperKindFromSettings === true;
  // 库房纸张后加：旧「不再询问」仍弹一次确认纸张；确认并勾选后才跳过
  // 申请单纸张随打印机弹窗：打印机可跳过时一并跳过，用本地纸张偏好
  if (!decision.shouldPrompt && (!showPaperKind || paperLocked)) {
    const resolvedScenarioCode = showServiceApplyPaperSize
      ? remapServiceApplyScenarioByPaper(scenarioCode, initialServiceApplyPaperSize)
      : undefined;
    return {
      printer: decision.initialPrinterName,
      cancelled: false,
      paperKind: showPaperKind ? initialPaperKind : undefined,
      tripleSpec: showPaperKind ? initialTripleSpec : undefined,
      serviceApplyPaperSize: showServiceApplyPaperSize ? initialServiceApplyPaperSize : undefined,
      resolvedScenarioCode,
    };
  }

  return promptAndSave({
    scenarioCode,
    scenarioLabel,
    dialogMode: options.dialogMode,
    showPaperKind,
    initialPaperKind,
    initialTripleSpec,
    showServiceApplyPaperSize,
    initialServiceApplyPaperSize,
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
