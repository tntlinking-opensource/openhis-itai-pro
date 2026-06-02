import type {
  PageOutpatientInfoForDoctorStationRequest,
  PageOutpatientInfoForDoctorStationResponse,
  StartOutpatientReceptionRequest,
  StartOutpatientReceptionResponse,
} from './encounter.types';

import { publicRequestClient } from '#/api/request';

/** 医生站：分页查询就诊患者列表（在诊 / 待诊队列） */
export async function httpPageOutpatientInfoForDoctorStation(
  body: PageOutpatientInfoForDoctorStationRequest,
): Promise<PageOutpatientInfoForDoctorStationResponse> {
  return publicRequestClient.post<PageOutpatientInfoForDoctorStationResponse>(
    '/doctorStationEncounterManage/pageOutpatientInfoForDoctorStation',
    body,
  );
}

/** 医生站：提交门诊接诊（计划态 → 就诊中） */
export async function httpStartOutpatientReception(
  body: StartOutpatientReceptionRequest,
): Promise<StartOutpatientReceptionResponse> {
  return publicRequestClient.post<StartOutpatientReceptionResponse>(
    '/doctorStationEncounterManage/startOutpatientReception',
    body,
  );
}
