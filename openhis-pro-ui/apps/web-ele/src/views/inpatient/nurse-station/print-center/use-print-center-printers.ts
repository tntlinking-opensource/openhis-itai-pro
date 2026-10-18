import type { HiprintPrinterItem } from '#/utils/print/fetch-printer-list';
import type { PrintDocType } from './print-center.types';
import type { WatchSource } from 'vue';

import { computed, reactive, ref, watch } from 'vue';

import { fetchHiprintPrinterList } from '#/utils/print/fetch-printer-list';
import {
  clearPrinterPreference,
  getPrinterPreference,
  resolvePrinterPreferenceScope,
  savePrinterPreference,
} from '#/utils/print/printer-preference';
import { schedulePrintClientWarmup } from '#/utils/print/runtime/hiprint-client';

import { resolvePrintCenterScenarioCode } from './doc-registry';

/** 住院护士站打印中心：各单据打印机下拉（缓存有值则回填，没有则空着） */
export function usePrintCenterPrinters(docTypes: WatchSource<readonly PrintDocType[]>) {
  const printers = ref<HiprintPrinterItem[]>([]);
  const printersLoading = ref(false);
  const printersError = ref('');
  const printerByDoc = reactive<Partial<Record<PrintDocType, string>>>({});

  /** 从本机偏好回填各单据打印机；没有缓存则保持空 */
  function hydratePrintersFromCache(docTypes: readonly PrintDocType[]): void {
    for (const docType of docTypes) {
      const scenarioCode = resolvePrintCenterScenarioCode(docType);
      if (!scenarioCode) {
        printerByDoc[docType] = '';
        continue;
      }
      const pref = getPrinterPreference(scenarioCode);
      printerByDoc[docType] = pref?.printerName?.trim() ?? '';
    }
  }

  /** 拉本机打印机列表；失败时保留已回填的缓存名 */
  async function loadPrinters(): Promise<void> {
    printersLoading.value = true;
    printersError.value = '';
    schedulePrintClientWarmup();
    try {
      printers.value = await fetchHiprintPrinterList();
      if (printers.value.length === 0) {
        printersError.value = '未发现本机打印机，请确认打印客户端已启动';
      }
    } catch (error) {
      printers.value = [];
      printersError.value =
        error instanceof Error ? error.message : '无法连接打印客户端，请确认 Zxplat.Win 已启动';
    } finally {
      printersLoading.value = false;
    }
  }

  /** 某单据下拉选项：缓存里的机不在列表时仍展示，避免看起来像被清空 */
  function printerOptionsFor(docType: PrintDocType): HiprintPrinterItem[] {
    const cached = String(printerByDoc[docType] ?? '').trim();
    const list = printers.value.slice();
    if (cached && !list.some((item) => item.name === cached)) {
      list.unshift({ name: cached, displayName: cached, isDefault: false });
    }
    return list;
  }

  /** 下拉变更：写入/清除该场景偏好 */
  function handlePrinterChange(docType: PrintDocType, printerName: string): void {
    const next = String(printerName ?? '').trim();
    printerByDoc[docType] = next;
    const scenarioCode = resolvePrintCenterScenarioCode(docType);
    if (!scenarioCode) {
      return;
    }
    if (!next) {
      clearPrinterPreference(scenarioCode);
      return;
    }
    savePrinterPreference(scenarioCode, {
      printerName: next,
      skipPrompt: true,
      updatedAt: new Date().toISOString(),
    });
  }

  /** 当前单据已选打印机（未选为空串） */
  function resolveSelectedPrinter(docType: PrintDocType): string {
    return String(printerByDoc[docType] ?? '').trim();
  }

  const printerHint = computed(() => {
    if (printersLoading.value) {
      return '正在加载本机打印机…';
    }
    return printersError.value;
  });

  watch(
    [
      docTypes,
      () => resolvePrinterPreferenceScope().tenantId,
    ],
    ([types]) => {
      hydratePrintersFromCache(types);
    },
    { immediate: true },
  );

  return {
    printers,
    printersLoading,
    printersError,
    printerByDoc,
    printerHint,
    loadPrinters,
    printerOptionsFor,
    handlePrinterChange,
    resolveSelectedPrinter,
  };
}
