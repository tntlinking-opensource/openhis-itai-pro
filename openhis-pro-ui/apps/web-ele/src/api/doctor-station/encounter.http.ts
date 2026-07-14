import type {
  CompleteOutpatientEncounterRequest,
  CompleteOutpatientEncounterResponse,
  PageOutpatientInfoForDoctorStationRequest,
  PageOutpatientInfoForDoctorStationResponse,
  StartOutpatientReceptionRequest,
  StartOutpatientReceptionResponse,
} from './encounter.types';

import { publicRequestClient } from '#/api/request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

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

/** 医生站：门诊完诊（在诊 / 暂离 → 诊毕） */
export async function httpCompleteOutpatientEncounter(
  body: CompleteOutpatientEncounterRequest,
): Promise<CompleteOutpatientEncounterResponse> {
  return publicRequestClient.post<CompleteOutpatientEncounterResponse>(
    '/doctorStationEncounterManage/completeOutpatientEncounter',
    {
      encounterId: preserveApiIdForRequest(body.encounterId),
      operatorId: preserveApiIdForRequest(body.operatorId),
      completedAt: body.completedAt,
      summary: body.summary,
    },
  );
}
