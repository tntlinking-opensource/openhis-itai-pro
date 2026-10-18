import type { Ref } from 'vue';

import type { DoctorStationAdviceRowDto } from '#/api/doctor-station/advice.types';

import { onUnmounted, ref, watch } from 'vue';

import { httpListDoctorStationAdvice } from '#/api/doctor-station/advice.http';
import { httpListTcmHerbalPrescriptions as httpListOutpatientTcm } from '#/api/doctor-station/tcm.http';
import { httpListInpatientDoctorStationAdvice } from '#/api/inpatient/doctor-station/inpatient-doctor-station-advice.http';
import { httpListTcmHerbalPrescriptions as httpListInpatientTcm } from '#/api/inpatient/doctor-station/inpatient-doctor-station-tcm.http';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

import {
  buildEncounterFeeSummary,
  onEncounterFeeRefresh,
  type EncounterFeeSummaryView,
} from './encounter-fee-summary';

interface UseEncounterFeeSummaryOptions {
  encounterId: Ref<string>;
  patientId: Ref<string>;
  scope: 'inpatient' | 'outpatient';
}

/** 按当前就诊拉取医嘱与中药方，汇总信息条费用 */
export function useEncounterFeeSummary(options: UseEncounterFeeSummaryOptions) {
  const feeSummary = ref<EncounterFeeSummaryView | null>(null);
  let requestSeq = 0;

  async function refreshEncounterFee() {
    const encounterId = options.encounterId.value.trim();
    const patientId = options.patientId.value.trim();
    const seq = ++requestSeq;
    if (!encounterId || !patientId) {
      feeSummary.value = null;
      return;
    }
    try {
      const advicePromise =
        options.scope === 'outpatient'
          ? httpListDoctorStationAdvice({ encounterId, patientId, onlyMine: false })
          : httpListInpatientDoctorStationAdvice({ encounterId, patientId });
      const tcmPromise =
        options.scope === 'outpatient'
          ? httpListOutpatientTcm({ encounterId, patientId })
          : httpListInpatientTcm({ encounterId, patientId });
      const [adviceRes, tcmRes] = await Promise.all([advicePromise, tcmPromise]);
      if (seq !== requestSeq) return;
      const { content } = parsePagedRowsFromApiData<DoctorStationAdviceRowDto>(adviceRes);
      feeSummary.value = buildEncounterFeeSummary(content, tcmRes.prescriptions ?? []);
    } catch {
      if (seq !== requestSeq) return;
    }
  }

  watch(
    [options.encounterId, options.patientId],
    () => {
      void refreshEncounterFee();
    },
    { immediate: true },
  );

  const stopRefreshListen = onEncounterFeeRefresh(() => {
    void refreshEncounterFee();
  });
  onUnmounted(() => {
    stopRefreshListen();
  });

  return {
    feeSummary,
    refreshEncounterFee,
  };
}
