import type { ApiResponse } from '#/types/api-response';

import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import { publicRequestClient } from '../request';

export interface LisPacsReportItem {
  serviceRequestId?: string;
  categoryEnum?: string;
  systemLabel?: string;
  activityBusNo?: string;
  activityName?: string;
  applyNo?: string;
  reportPdfUrl?: string;
  /** sent / failed / notSent */
  dispatchStatus?: string;
  authoredTime?: string;
}

const BASE = '/h22010402403LisPacsReport';

/** 按就诊列出 LIS/PACS 报告打开链接 */
export async function listLisPacsReportByEncounterApi(encounterId: number | string) {
  return publicRequestClient.post<ApiResponse<LisPacsReportItem[]>>(`${BASE}/listByEncounter`, {
    encounterId: preserveApiIdForRequest(encounterId),
  });
}
