import type { InpatientPatientCardPayload } from '#/components/outpatient/patient-card.types';

import { formatDateTime } from '@vben/utils';

import {
  formatInpatientAccountBalancePrimaryText,
  formatInpatientAccountBalanceSecondaryText,
} from '#/utils/inpatient/account-balance-display';

import type { InpatientDoctorStationPatient } from '../doctorPatientStore';

/** 住院医生站队列行 → 门诊 PatientCard（variant=doctor）住院载荷 */
export function mapInpatientDoctorStationPatientToCardPayload(
  row: InpatientDoctorStationPatient,
): InpatientPatientCardPayload {
  const genderCode = String(row.genderEnum ?? '').trim();
  const gender =
    (row.genderEnumText ?? '').trim() ||
    (genderCode === '1' ? '男' : genderCode === '2' ? '女' : '') ||
    '—';

  const ageRaw = (row.ageText ?? '').trim();
  const age = ageRaw ? ageRaw.replace(/岁\s*$/u, '') : '—';

  const bedRaw = (row.bedName ?? '').trim();
  const bedLabel = bedRaw ? (bedRaw.includes('床') ? bedRaw : `${bedRaw}床`) : '未分床';

  const rawTime = row.startTime ?? row.admissionTime;
  let admissionTime = '—';
  if (rawTime) {
    const formatted = formatDateTime(rawTime);
    admissionTime = formatted && formatted !== '-' ? formatted : String(rawTime);
  }

  const paymentType = (row.contractDisplay ?? '').trim() || undefined;

  return {
    patientType: 'inpatient',
    id: row.encounterId,
    name: row.patientName ?? '',
    gender,
    age,
    hospitalizationNo: (row.encounterBusNo ?? '').trim() || undefined,
    admissionTime,
    visitNo: (row.encounterBusNo ?? '').trim() || undefined,
    status: (row.statusEnumText ?? '').trim() || '在院',
    paymentType,
    priority: bedLabel,
    isRegistered: true,
    accountBalanceSummary: formatInpatientAccountBalancePrimaryText(row),
    accountBalanceSub: formatInpatientAccountBalanceSecondaryText(row),
    lowBalanceWarning: row.lowBalanceWarning === true,
  };
}
