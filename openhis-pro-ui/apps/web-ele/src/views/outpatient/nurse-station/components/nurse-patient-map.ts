import type { NursePatient } from './types';

import type { OutpatientPatientCardPayload } from '#/components/outpatient/patient-card.types';

/** 映射为 `PatientCard` 所需门诊就诊行形状 */
export function toPatientCardPatient(p: NursePatient): OutpatientPatientCardPayload {
  return {
    encounterId: p.id,
    encounterBusNo: (p.encounterBusNo ?? '').trim() || null,
    patientName: p.name,
    genderEnumText: p.genderEnumText ?? null,
    genderEnum: p.gender === '男' ? 1 : p.gender === '女' ? 2 : undefined,
    ageText: p.age,
    phone: p.phone ?? null,
    startTime: p.registrationTime ?? p.admissionTime,
    receptionTime: null,
    statusEnumText: p.status,
    serviceTypeName: p.paymentType ?? null,
    pendingExecuteCount: p.pendingExecuteCount,
    riskHint: p.riskHint,
  };
}
