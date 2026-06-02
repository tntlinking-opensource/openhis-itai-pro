import type { InpatientPatientCardPayload } from '#/components/outpatient/patient-card.types';

import type { InpatientDispenseQueuePatient } from './types';

/** 住院发药队列行 → PatientCard 住院载荷（布局对齐门诊发药 variant=dispense） */
export function toInpatientDispensePatientCardModel(
  p: InpatientDispenseQueuePatient,
): InpatientPatientCardPayload {
  const bedRaw = p.bedName.trim();
  const bedLabel = bedRaw ? (bedRaw.includes('床') ? bedRaw : `${bedRaw}床`) : '未分床';
  const ageRaw = p.ageLabel.trim().replace(/岁\s*$/u, '');

  return {
    patientType: 'inpatient',
    id: p.id,
    name: p.patientName,
    gender: p.genderText.trim() || '-',
    age: ageRaw || '-',
    hospitalizationNo: p.hospitalizationNo || undefined,
    admissionTime: p.admissionTimeLabel,
    visitNo: p.hospitalizationNo || undefined,
    priority: bedLabel,
    isRegistered: true,
  };
}
