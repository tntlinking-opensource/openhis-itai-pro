import type {
  OptionDto,
  ParticipantDto,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station.types';

import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import { NURSE_STATION_PARTICIPANT_ROLE } from '../components/patient-admission-bed-dialog.constants';

export interface NurseStationParticipantFormIds {
  chiefDoctorId: string;
  inpatientDoctorId: string;
  attendingDoctorId: string;
  primaryNurseId: string;
}

export interface MapParticipantsToFormIdsOptions {
  /**
   * 待入科首次分床：办理入院 / 门诊转住院带来的医生默认落到「住院医生」（必填）；
   * 登记落库的 ATTENDING_DOCTOR 亦归到住院医生；主治 / 主任不自动带入。
   */
  bringDoctorToInpatientDoctor?: boolean;
}

/** 从详情参与者列表解析各角色医务人员 id */
export function mapParticipantsToFormIds(
  participants: ParticipantDto[] | undefined,
  options?: MapParticipantsToFormIdsOptions,
): NurseStationParticipantFormIds {
  const result: NurseStationParticipantFormIds = {
    inpatientDoctorId: '',
    attendingDoctorId: '',
    chiefDoctorId: '',
    primaryNurseId: '',
  };
  if (!participants?.length) {
    return result;
  }
  let admitterId = '';
  let attendingId = '';
  for (const row of participants) {
    const role = (row.roleCode ?? '').trim();
    const id = preserveApiIdForRequest(row.practitionerId) ?? '';
    if (!id) {
      continue;
    }
    if (role === NURSE_STATION_PARTICIPANT_ROLE.INPATIENT_DOCTOR) {
      admitterId = id;
    } else if (role === NURSE_STATION_PARTICIPANT_ROLE.ATTENDING_DOCTOR) {
      attendingId = id;
    } else if (role === NURSE_STATION_PARTICIPANT_ROLE.CHIEF_DOCTOR) {
      result.chiefDoctorId = id;
    } else if (role === NURSE_STATION_PARTICIPANT_ROLE.PRIMARY_NURSE) {
      result.primaryNurseId = id;
    }
  }
  if (options?.bringDoctorToInpatientDoctor) {
    // 待入科：带到住院医生；申请 ADMITTER / 登记 ATTENDING 均可；主治、主任不自动带
    result.inpatientDoctorId = admitterId || attendingId;
    return result;
  }
  result.inpatientDoctorId = admitterId;
  result.attendingDoctorId = attendingId;
  return result;
}

/** 将表单中的医护选择组装为 savePatientInfo 参与者提交行 */
export function buildParticipantSubmitRows(form: NurseStationParticipantFormIds) {
  const rows: Array<{ practitionerId: string; roleCode: string }> = [];
  if (form.inpatientDoctorId) {
    rows.push({
      roleCode: NURSE_STATION_PARTICIPANT_ROLE.INPATIENT_DOCTOR,
      practitionerId: form.inpatientDoctorId,
    });
  }
  if (form.attendingDoctorId) {
    rows.push({
      roleCode: NURSE_STATION_PARTICIPANT_ROLE.ATTENDING_DOCTOR,
      practitionerId: form.attendingDoctorId,
    });
  }
  if (form.chiefDoctorId) {
    rows.push({
      roleCode: NURSE_STATION_PARTICIPANT_ROLE.CHIEF_DOCTOR,
      practitionerId: form.chiefDoctorId,
    });
  }
  if (form.primaryNurseId) {
    rows.push({
      roleCode: NURSE_STATION_PARTICIPANT_ROLE.PRIMARY_NURSE,
      practitionerId: form.primaryNurseId,
    });
  }
  return rows;
}

const DOCTOR_PARTICIPANT_ROLES = new Set<string>([
  NURSE_STATION_PARTICIPANT_ROLE.INPATIENT_DOCTOR,
  NURSE_STATION_PARTICIPANT_ROLE.ATTENDING_DOCTOR,
  NURSE_STATION_PARTICIPANT_ROLE.CHIEF_DOCTOR,
]);

/** 将详情已有参与者补入下拉，避免 ElSelect 因选项缺失而回显 id */
export function mergeParticipantsIntoOptionLists(
  doctorOptions: OptionDto[],
  primaryNurseOptions: OptionDto[],
  participants: ParticipantDto[] | undefined,
): { doctorOptions: OptionDto[]; primaryNurseOptions: OptionDto[] } {
  const mergedDoctors = [...doctorOptions];
  const mergedNurses = [...primaryNurseOptions];
  const doctorValueSet = new Set(mergedDoctors.map((item) => String(item.value)));
  const nurseValueSet = new Set(mergedNurses.map((item) => String(item.value)));

  for (const row of participants ?? []) {
    const id = preserveApiIdForRequest(row.practitionerId);
    if (!id) {
      continue;
    }
    const label = (row.practitionerName ?? '').trim() || id;
    const role = (row.roleCode ?? '').trim();
    if (DOCTOR_PARTICIPANT_ROLES.has(role)) {
      if (!doctorValueSet.has(id)) {
        doctorValueSet.add(id);
        mergedDoctors.push({ value: id, label });
      }
      continue;
    }
    if (role === NURSE_STATION_PARTICIPANT_ROLE.PRIMARY_NURSE && !nurseValueSet.has(id)) {
      nurseValueSet.add(id);
      mergedNurses.push({ value: id, label });
    }
  }

  return {
    doctorOptions: mergedDoctors,
    primaryNurseOptions: mergedNurses,
  };
}
