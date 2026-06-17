import { ref } from 'vue';

import { ElMessage } from 'element-plus';

import { useOutpatientPrescriptionPrint } from '#/composables/use-outpatient-prescription-print';
import {
  OUTPATIENT_NURSE_PRESCRIPTION_TEMPLATE,
  resolveNursePrescriptionPrintTargets,
} from '#/utils/report/nurse-prescription-print';
import type { NurseAdviceExecuteRow } from '#/views/outpatient/nurse-station/components/types';

export interface NursePrescriptionPrintParams {
  encounterId: string;
  medicationRequestIds: string[];
  executeRows?: NurseAdviceExecuteRow[];
  templateCode?: string;
  jobName?: string;
}

export function useNursePrescriptionPrint() {
  const { isPrinting, previewLoading, printPrescription } = useOutpatientPrescriptionPrint();
  const resolving = ref(false);

  async function printNursePrescriptions(params: NursePrescriptionPrintParams): Promise<number> {
    const encounterId = params.encounterId?.trim();
    if (!encounterId) {
      ElMessage.warning('缺少就诊信息，无法打印处方单');
      return 0;
    }
    if (!params.medicationRequestIds?.length) {
      ElMessage.warning('没有可打印的药品医嘱');
      return 0;
    }

    resolving.value = true;
    let targets;
    try {
      targets = await resolveNursePrescriptionPrintTargets(
        encounterId,
        params.medicationRequestIds,
        params.executeRows ?? [],
      );
    } finally {
      resolving.value = false;
    }

    if (!targets.length) {
      ElMessage.warning('没有可打印的处方');
      return 0;
    }

    const templateCode = params.templateCode?.trim() || OUTPATIENT_NURSE_PRESCRIPTION_TEMPLATE;
    for (let i = 0; i < targets.length; i += 1) {
      const target = targets[i]!;
      const suffix = targets.length > 1 ? ` (${i + 1}/${targets.length})` : '';
      const label = target.prescriptionNo ?? target.medicationRequestIds[0] ?? '处方';
      await printPrescription({
        encounterId,
        prescriptionNo: target.prescriptionNo,
        prescriptionGroupId: target.prescriptionGroupId,
        medicationRequestIds: target.medicationRequestIds,
        westernOnly: target.westernOnly,
        tcmOnly: target.tcmOnly,
        templateCode,
        jobName: `${params.jobName ?? '护士处方执行联'}-${label}${suffix}`,
      });
    }
    return targets.length;
  }

  return {
    isPrinting,
    previewLoading,
    resolving,
    printNursePrescriptions,
  };
}
