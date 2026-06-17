import {
  useOrderExecuteSheetPrint,
  type OrderExecuteSheetPrintParams,
} from '#/composables/use-order-execute-sheet-print';

const DEFAULT_TEMPLATE_CODE = 'iv_infusion_execute_sheet';

export type IvInfusionExecuteSheetPrintParams = OrderExecuteSheetPrintParams;

export function useIvInfusionExecuteSheetPrint() {
  const { isPrinting, previewLoading, printOrderExecuteSheet } = useOrderExecuteSheetPrint();

  async function printIvInfusionExecuteSheet(
    params: IvInfusionExecuteSheetPrintParams,
  ): Promise<void> {
    await printOrderExecuteSheet({
      ...params,
      templateCode: params.templateCode ?? DEFAULT_TEMPLATE_CODE,
      jobName: params.jobName ?? '输液执行单',
    });
  }

  return {
    isPrinting,
    previewLoading,
    printIvInfusionExecuteSheet,
  };
}
