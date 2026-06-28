/**
 * 病案卷宗 API（MrmsCaseRecordVolumeAppService）
 */

import type {
  GetVolumeCatalogParams,
  PreviewVolumeParams,
  VolumeCatalogDto,
  VolumePdfResultDto,
} from './types';

import { publicRequestClient } from '#/api/request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

const BASE = '/mrmsCaseRecordVolume';

export async function getVolumeCatalogApi(params: GetVolumeCatalogParams) {
  return publicRequestClient.post<VolumeCatalogDto>(`${BASE}/getVolumeCatalog`, {
    caseRecordId: preserveApiIdForRequest(params.caseRecordId),
  });
}

export async function previewVolumePdfApi(params: PreviewVolumeParams) {
  const documentIds = params.documentIds?.map((id) => preserveApiIdForRequest(id)).filter(Boolean);
  return publicRequestClient.post<VolumePdfResultDto>(`${BASE}/previewVolumePdf`, {
    caseRecordId: preserveApiIdForRequest(params.caseRecordId),
    documentIds: documentIds && documentIds.length > 0 ? documentIds : undefined,
    includeHomepage: params.includeHomepage ?? true,
  });
}
