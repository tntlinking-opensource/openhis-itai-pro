import type { InpatientDoctorStationPatient } from '../doctorPatientStore';

import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

/** 就诊主键保持 string，禁止 Number() */
export function resolveDoctorSessionEncounterId(
  patient: InpatientDoctorStationPatient | string | null | undefined,
): string {
  if (!patient) return '';
  if (typeof patient === 'string') {
    return preserveApiIdForRequest(patient) ?? '';
  }
  return preserveApiIdForRequest(patient.encounterId) ?? '';
}

/** 页签床号：无床回落「未分床」 */
export function buildDoctorSessionBedLabel(patient: InpatientDoctorStationPatient): string {
  const bedRaw = (patient.bedName ?? '').trim();
  if (!bedRaw) return '未分床';
  return bedRaw.includes('床') ? bedRaw : `${bedRaw}床`;
}

/** 页签姓名 */
export function buildDoctorSessionPatientName(patient: InpatientDoctorStationPatient): string {
  return (patient.patientName ?? '').trim() || '未命名';
}

/** 页签辅文：性别 · 年龄，便于扫一眼核对 */
export function buildDoctorSessionTabMeta(patient: InpatientDoctorStationPatient): string {
  const genderCode = String(patient.genderEnum ?? '').trim();
  const genderText = (patient.genderEnumText ?? '').trim();
  const gender =
    genderText || (genderCode === '1' ? '男' : genderCode === '2' ? '女' : '');
  const ageRaw = (patient.ageText ?? '').trim();
  const age = ageRaw && !ageRaw.includes('岁') ? `${ageRaw}岁` : ageRaw;
  if (gender && age) return `${gender}·${age}`;
  return gender || age;
}

/** 页签文案：床号 + 姓名 */
export function buildDoctorSessionTabLabel(patient: InpatientDoctorStationPatient): string {
  const name = buildDoctorSessionPatientName(patient);
  const bedRaw = (patient.bedName ?? '').trim();
  if (!bedRaw) return name;
  return `${buildDoctorSessionBedLabel(patient)} ${name}`;
}

/** 页签悬停：性别年龄住院号 */
export function buildDoctorSessionTabTooltip(patient: InpatientDoctorStationPatient): string {
  const parts = [
    buildDoctorSessionTabLabel(patient),
    (patient.genderEnumText ?? '').trim(),
    (patient.ageText ?? '').trim(),
    (patient.encounterBusNo ?? '').trim(),
  ].filter(Boolean);
  return parts.join(' · ');
}
