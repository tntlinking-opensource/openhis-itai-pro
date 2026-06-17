import type { InpatientPatientCardPayload } from '#/components/outpatient/patient-card.types';

import type { InpatientReturnMedicineQueuePatient } from './types';

/** 住院退药队列行 → PatientCard 住院载荷 */
export function toInpatientReturnPatientCardModel(
  p: InpatientReturnMedicineQueuePatient,
): InpatientPatientCardPayload {
  const bedRaw = p.bedName.trim();
  const bedLabel = bedRaw ? (bedRaw.includes('床') ? bedRaw : `${bedRaw}床`) : '未分床';
  const ageRaw = p.ageLabel.trim().replace(/岁\s*$/u, '');

  return {
    patientType: 'inpatient',
    id: p.id,
    name: p.patientName,
    gender: p.genderEnumText.trim() || '-',
    age: ageRaw || '-',
    hospitalizationNo: p.hospitalizationNo || undefined,
    visitNo: p.hospitalizationNo || undefined,
    priority: bedLabel,
    isRegistered: true,
  };
}
