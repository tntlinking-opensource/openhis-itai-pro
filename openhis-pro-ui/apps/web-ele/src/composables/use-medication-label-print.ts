import {
  useIvInfusionLabelPrint,
  type IvInfusionLabelPrintParams,
} from '#/composables/use-iv-infusion-label-print';

export type MedicationLabelScope = 'injection' | 'nebulization' | 'oral' | 'skin_test';

export interface MedicationLabelPrintParams
  extends Omit<IvInfusionLabelPrintParams, 'templateCode'> {
  templateCode?: string;
}

const TEMPLATE_BY_SCOPE: Record<MedicationLabelScope, string> = {
  injection: 'injection_label',
  oral: 'oral_drug_label',
  skin_test: 'skin_test_label',
  nebulization: 'nebulization_label',
};

const JOB_NAME_BY_SCOPE: Record<MedicationLabelScope, string> = {
  injection: '注射用药标签',
  oral: '口服药标签',
  skin_test: '皮试标签',
  nebulization: '雾化标签',
};

export function useMedicationLabelPrint(scope: MedicationLabelScope) {
  const { isPrinting, previewLoading, printIvInfusionLabels } = useIvInfusionLabelPrint();

  async function printMedicationLabels(params: MedicationLabelPrintParams): Promise<void> {
    await printIvInfusionLabels({
      ...params,
      labelScope: scope,
      templateCode: params.templateCode ?? TEMPLATE_BY_SCOPE[scope],
      jobName: params.jobName ?? JOB_NAME_BY_SCOPE[scope],
    });
  }

  return {
    isPrinting,
    previewLoading,
    printMedicationLabels,
    labelScope: scope,
  };
}

export function useInjectionLabelPrint() {
  return useMedicationLabelPrint('injection');
}

export function useOralDrugLabelPrint() {
  return useMedicationLabelPrint('oral');
}

export function useSkinTestLabelPrint() {
  return useMedicationLabelPrint('skin_test');
}

export function useNebulizationLabelPrint() {
  return useMedicationLabelPrint('nebulization');
}
