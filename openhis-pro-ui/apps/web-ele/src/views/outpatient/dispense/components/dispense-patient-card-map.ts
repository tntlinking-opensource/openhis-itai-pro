import type { DispenseQueuePatient } from './types';

import type { OutpatientPatientCardPayload } from '#/components/outpatient/patient-card.types';

function genderLabelToEnum(label: string): number | undefined {
  if (label.includes('男')) return 1;
  if (label.includes('女')) return 2;
  return undefined;
}

/** 将发药队列行映射为门诊就诊卡片入参（不发状态/类别标签由 variant=dispense 控制） */
export function toDispensePatientCardModel(p: DispenseQueuePatient): OutpatientPatientCardPayload {
  return {
    encounterId: p.id,
    encounterBusNo: p.visitNo,
    patientName: p.patientName,
    genderEnum: p.genderEnum ?? genderLabelToEnum(p.genderLabel),
    ageText: p.ageLabel,
    startTime: p.visitTimeLabel,
  };
}
