import type {
  DeleteNurseStationNursingRecordRequest,
  DeleteNurseStationNursingRecordResponse,
  ListNurseStationNursingRecordsRequest,
  ListNurseStationNursingRecordsResponse,
  LoadNurseStationNursingRecordInitResponse,
  SaveNurseStationNursingRecordsBatchRequest,
  SaveNurseStationNursingRecordsBatchResponse,
} from './inpatient-nurse-station-nursing-record.types';

import { publicRequestClient } from '#/api/request';

const BASE = '/nurseStationNursingRecord';

/** 加载护理记录页初始化配置 */
export async function httpLoadNurseStationNursingRecordInit(): Promise<LoadNurseStationNursingRecordInitResponse> {
  return publicRequestClient.post<LoadNurseStationNursingRecordInitResponse>(
    `${BASE}/loadNurseStationNursingRecordInit`,
    {},
  );
}

/** 按就诊与日期范围查询护理记录列表 */
export async function httpListNurseStationNursingRecords(
  body: ListNurseStationNursingRecordsRequest,
): Promise<ListNurseStationNursingRecordsResponse> {
  return publicRequestClient.post<ListNurseStationNursingRecordsResponse>(
    `${BASE}/listNurseStationNursingRecords`,
    body,
  );
}

/** 纸质单批量保存护理记录 */
export async function httpSaveNurseStationNursingRecordsBatch(
  body: SaveNurseStationNursingRecordsBatchRequest,
): Promise<SaveNurseStationNursingRecordsBatchResponse> {
  return publicRequestClient.post<SaveNurseStationNursingRecordsBatchResponse>(
    `${BASE}/saveNurseStationNursingRecordsBatch`,
    body,
  );
}

/** 删除一条已保存的护理记录 */
export async function httpDeleteNurseStationNursingRecord(
  body: DeleteNurseStationNursingRecordRequest,
): Promise<DeleteNurseStationNursingRecordResponse> {
  return publicRequestClient.post<DeleteNurseStationNursingRecordResponse>(
    `${BASE}/deleteNurseStationNursingRecord`,
    body,
  );
}
