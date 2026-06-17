import {
  useOutpatientPrescriptionPrint,
  type OutpatientPrescriptionPrintParams,
} from '#/composables/use-outpatient-prescription-print';
import { DISPENSE_SLIP_TEMPLATE } from '#/utils/report/outpatient-prescription-print-config';

export type DispenseSlipPrintParams = OutpatientPrescriptionPrintParams;

export function useDispenseSlipPrint() {
  const { isPrinting, previewLoading, printPrescription } = useOutpatientPrescriptionPrint();

  async function printDispenseSlip(params: DispenseSlipPrintParams): Promise<void> {
    await printPrescription({
      ...params,
      templateCode: DISPENSE_SLIP_TEMPLATE,
      jobName: params.jobName ?? '发药单',
    });
  }

  return {
    isPrinting,
    previewLoading,
    printDispenseSlip,
  };
}
