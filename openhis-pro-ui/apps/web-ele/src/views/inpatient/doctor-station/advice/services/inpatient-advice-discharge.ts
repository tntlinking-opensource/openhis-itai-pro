import type { CreateDischargeInpatientDoctorStationAdviceRequest } from '#/api/inpatient/doctor-station/inpatient-doctor-station-advice.types';

import { httpCreateDischargeInpatientDoctorStationAdvice } from '#/api/inpatient/doctor-station/inpatient-doctor-station-advice.http';

/** 开立出院医嘱（IF-IP-DOR-21） */
export async function createInpatientDischargeAdvice(
  body: CreateDischargeInpatientDoctorStationAdviceRequest,
) {
  return httpCreateDischargeInpatientDoctorStationAdvice(body);
}
