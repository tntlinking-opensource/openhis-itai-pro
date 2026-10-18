import type {
  GetPatientDetailRequest,
  GetPatientDetailResponse,
  LoadAdmissionParticipantInitRequest,
  LoadAdmissionParticipantInitResponse,
  LoadWardTreeRequest,
  LoadWardTreeResponse,
  PagePatientQueueRequest,
  PagePatientQueueResponse,
  SavePatientInfoRequest,
  SavePatientInfoResponse,
} from './inpatient-nurse-station.types';

import { publicRequestClient } from '#/api/request';

/** 住院护士站 · 患者管理（AS-IP-NUR-001 encountermanage） */
const BASE = '/nurseStationEncounterManage';

/** 加载护士站管理范围内的病区病房树 */
export async function httpLoadWardTree(
  body: LoadWardTreeRequest = {},
): Promise<LoadWardTreeResponse> {
  return publicRequestClient.post<LoadWardTreeResponse>(`${BASE}/loadWardTree`, body);
}

/** 与后端 {@code SilentPollHttpHeaders#NAME} 对齐：带此头时跳过访问/入参日志 */
const SILENT_POLL_HEADER = 'X-Zxplat-Silent-Poll';

export interface PagePatientQueueHttpOptions {
  /** 静默轮询：不写后端访问日志；前端勿配合 loading */
  silentPoll?: boolean;
}

/** 分页查询在院患者队列（任务计数 enrich 偏重，单独放宽超时） */
export async function httpPagePatientQueue(
  body: PagePatientQueueRequest,
  options?: PagePatientQueueHttpOptions,
): Promise<PagePatientQueueResponse> {
  const headers =
    options?.silentPoll === true
      ? {
          [SILENT_POLL_HEADER]: '1',
        }
      : undefined;
  return publicRequestClient.post<PagePatientQueueResponse>(`${BASE}/pagePatientQueue`, body, {
    timeout: 60_000,
    headers,
  });
}

/** 患者详情查询 */
export async function httpGetPatientDetail(
  body: GetPatientDetailRequest,
): Promise<GetPatientDetailResponse> {
  return publicRequestClient.post<GetPatientDetailResponse>(`${BASE}/getPatientDetail`, body);
}

/** 患者信息维护 */
export async function httpSavePatientInfo(
  body: SavePatientInfoRequest,
): Promise<SavePatientInfoResponse> {
  return publicRequestClient.post<SavePatientInfoResponse>(`${BASE}/savePatientInfo`, body);
}

/** 分床/入科责任医护下拉初始化 */
export async function httpLoadAdmissionParticipantInit(
  body: LoadAdmissionParticipantInitRequest = {},
): Promise<LoadAdmissionParticipantInitResponse> {
  return publicRequestClient.post<LoadAdmissionParticipantInitResponse>(
    `${BASE}/loadAdmissionParticipantInit`,
    body,
  );
}
