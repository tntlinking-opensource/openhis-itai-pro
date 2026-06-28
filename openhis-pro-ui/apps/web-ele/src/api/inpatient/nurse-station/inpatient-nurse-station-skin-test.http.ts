import type {
  DeleteNurseStationSkinTestRecordRequest,
  DeleteNurseStationSkinTestRecordResponse,
  ListNurseStationSkinTestRowsRequest,
  ListNurseStationSkinTestRowsResponse,
  LoadNurseStationSkinTestInitResponse,
  SaveNurseStationSkinTestRecordRequest,
  SaveNurseStationSkinTestRecordResponse,
} from './inpatient-nurse-station-skin-test.types';

import { publicRequestClient } from '#/api/request';

/** 加载皮试登记表单枚举选项 */
export async function httpLoadNurseStationSkinTestInit(): Promise<LoadNurseStationSkinTestInitResponse> {
  return publicRequestClient.post<LoadNurseStationSkinTestInitResponse>(
    '/nurseStationSkinTest/loadNurseStationSkinTestInit',
    {},
  );
}

/** 按就诊查询皮试待登记/已登记列表 */
export async function httpListNurseStationSkinTestRows(
  body: ListNurseStationSkinTestRowsRequest,
): Promise<ListNurseStationSkinTestRowsResponse> {
  return publicRequestClient.post<ListNurseStationSkinTestRowsResponse>(
    '/nurseStationSkinTest/listNurseStationSkinTestRows',
    body,
  );
}

/** 保存皮试结果（双签；首次含补费划价） */
export async function httpSaveNurseStationSkinTestRecord(
  body: SaveNurseStationSkinTestRecordRequest,
): Promise<SaveNurseStationSkinTestRecordResponse> {
  return publicRequestClient.post<SaveNurseStationSkinTestRecordResponse>(
    '/nurseStationSkinTest/saveNurseStationSkinTestRecord',
    body,
  );
}

/** 删除误录皮试记录（未划价） */
export async function httpDeleteNurseStationSkinTestRecord(
  body: DeleteNurseStationSkinTestRecordRequest,
): Promise<DeleteNurseStationSkinTestRecordResponse> {
  return publicRequestClient.post<DeleteNurseStationSkinTestRecordResponse>(
    '/nurseStationSkinTest/deleteNurseStationSkinTestRecord',
    body,
  );
}
