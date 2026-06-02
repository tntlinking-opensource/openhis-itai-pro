import type { OutpatientPatientCardPayload } from '#/components/outpatient/patient-card.types';

import type { ReturnMedicineQueuePatient } from './types';

/** 退药队列行 → 门诊患者卡片（展示退药状态标签） */
export function toReturnPatientCardModel(
  p: ReturnMedicineQueuePatient,
): OutpatientPatientCardPayload {
  return {
    encounterId: p.id,
    encounterBusNo: p.visitNo,
    patientName: p.patientName,
    genderEnumText: p.genderEnumText,
    ageText: p.ageLabel,
    startTime: p.visitTimeLabel,
    organizationName: p.departmentName,
    statusEnum: p.refundEnum,
    statusEnumText: p.refundEnumText,
  };
}
