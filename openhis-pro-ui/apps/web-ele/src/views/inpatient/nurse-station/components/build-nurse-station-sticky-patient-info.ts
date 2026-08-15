import type { NurseStationModulePatient } from './nurse-station-module-patient.types';

import dayjs from 'dayjs';

/** 护士站模块顶部患者信息条入参（对齐 InpatientPatientInfoHeader）。 */
export interface NurseStationStickyPatientInfo {
  age?: number | string;
  admitAt?: string;
  bedNo?: string;
  diagnosis?: string;
  gender?: string;
  inpatientDays?: number | string;
  inpatientNo?: string;
  inWardAt?: string;
  insuranceType?: string;
  name?: string;
}

/** 将读模型时间格式化为 YYYY-MM-DD HH:mm:ss。 */
function formatNurseStationPatientDateTime(value?: string): string | undefined {
  const text = (value ?? '').trim();
  if (!text) {
    return undefined;
  }
  const parsed = dayjs(text);
  if (parsed.isValid()) {
    return parsed.format('YYYY-MM-DD HH:mm:ss');
  }
  return text;
}

/**
 * 住院天数：即进不计出（入院日计、出院日不计）；在院未出院含当日。
 *
 * @param admissionAt 入院时间
 * @param dischargeAt 出院时间；空则按在院含当日
 */
function calcNurseStationInpatientDays(
  admissionAt?: string,
  dischargeAt?: string,
): number | undefined {
  const admissionRaw = (admissionAt ?? '').trim();
  if (!admissionRaw) {
    return undefined;
  }
  const admission = dayjs(admissionRaw);
  if (!admission.isValid()) {
    return undefined;
  }
  const dischargeRaw = (dischargeAt ?? '').trim();
  let hasDischarge = false;
  let end = dayjs();
  if (dischargeRaw && dayjs(dischargeRaw).isValid()) {
    hasDischarge = true;
    end = dayjs(dischargeRaw);
  }
  const diff = end.startOf('day').diff(admission.startOf('day'), 'day');
  // 已出院：即进不计出（11→13=2）
  if (hasDischarge) {
    return Math.max(1, diff);
  }
  // 在院：含当日
  return Math.max(1, diff + 1);
}

/** 由护士站模块患者入参构造顶部信息条数据。 */
export function buildNurseStationStickyPatientInfoFromModulePatient(
  patient: NurseStationModulePatient,
): NurseStationStickyPatientInfo {
  const admitAt = formatNurseStationPatientDateTime(patient.admissionAt);
  const inWardAt = formatNurseStationPatientDateTime(patient.inWardAt ?? patient.admissionAt);
  return {
    age: patient.age,
    admitAt,
    bedNo: patient.bedNo,
    diagnosis: patient.diagnosis ?? '—',
    gender: patient.gender,
    inpatientDays: calcNurseStationInpatientDays(patient.admissionAt, patient.dischargeAt),
    inpatientNo: patient.encounterBusNo,
    inWardAt,
    insuranceType: patient.feeType ?? '—',
    name: patient.name,
  };
}

/** 多选或未选时的顶部信息条占位文案。 */
export function buildNurseStationStickyPatientInfoPlaceholder(options: {
  diagnosis: string;
  name: string;
}): NurseStationStickyPatientInfo {
  return {
    diagnosis: options.diagnosis,
    name: options.name,
  };
}
