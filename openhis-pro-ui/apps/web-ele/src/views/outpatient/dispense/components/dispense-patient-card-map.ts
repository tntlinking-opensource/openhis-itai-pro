import type { DispenseQueuePatient } from './types';

import type { OutpatientPatientCardPayload } from '#/components/outpatient/patient-card.types';

/** 将发药队列行映射为门诊就诊卡片入参（发药态展示费用性质与电话，不展示就诊状态标签） */
export function toDispensePatientCardModel(p: DispenseQueuePatient): OutpatientPatientCardPayload {
  return {
    encounterId: p.id,
    encounterBusNo: p.visitNo,
    patientName: p.patientName,
    genderEnumText: p.genderLabel || undefined,
    ageText: p.ageLabel,
    organizationName: p.organizationName,
    startTime: p.visitTimeLabel,
    phone: p.patientPhone,
    contractDisplay: p.feeNatureLabel,
  };
}
