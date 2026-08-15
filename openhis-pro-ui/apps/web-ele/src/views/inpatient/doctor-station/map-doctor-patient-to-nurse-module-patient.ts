import type { InpatientDoctorStationPatient } from './doctorPatientStore';

import type { NurseStationModulePatient } from '#/views/inpatient/nurse-station/components/nurse-station-module-patient.types';

/** 从年龄文案解析周岁数字（如「45岁」「45」→ 45） */
function parseAgeYears(ageText?: string): number {
  const match = (ageText ?? '').trim().match(/(\d+)/);
  if (!match?.[1]) {
    return 0;
  }
  const years = Number(match[1]);
  return Number.isFinite(years) ? years : 0;
}

/** 将医生站选中患者映射为护士站右侧模块统一入参 */
export function mapDoctorPatientToNurseModulePatient(
  patient: InpatientDoctorStationPatient,
): NurseStationModulePatient {
  const diagnosisList = patient.diagnosisList ?? [];
  return {
    id: patient.encounterId,
    patientId: patient.patientId,
    encounterBusNo: patient.encounterBusNo,
    admissionAt: patient.admissionTime ?? patient.startTime,
    inWardAt: patient.startTime,
    dischargeAt: patient.endTime,
    bedNo: (patient.bedName ?? '').trim(),
    name: patient.patientName,
    gender: (patient.genderEnumText ?? '').trim(),
    age: parseAgeYears(patient.ageText),
    nurseLevel: (patient.nursingLevelName ?? '').trim(),
    feeType: patient.contractDisplay,
    diagnosis: (patient.diagnosisSummary ?? '').trim() || diagnosisList.join('、'),
    regionName: (patient.houseName ?? '').trim() || '-',
    wardName: (patient.wardName ?? '').trim() || '-',
    pendingAudit: 0,
    pendingExecute: 0,
    accountBalance: patient.accountBalance,
    accountBalanceDisplayMode: patient.accountBalanceDisplayMode,
    accountBalanceSummaryText: patient.accountBalanceSummaryText,
    accountBalanceSubText: patient.accountBalanceSubText,
    availableBalance: patient.availableBalance,
    pendingSettlementTotal: patient.pendingSettlementTotal,
    lowBalanceWarning: patient.lowBalanceWarning,
    riskTags: [],
    statusEnum: patient.statusEnum,
  };
}
