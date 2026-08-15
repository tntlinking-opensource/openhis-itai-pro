import type {
  DeleteNurseStationTemperatureSheetRequest,
  DeleteNurseStationTemperatureSheetResponse,
  GetNurseStationTemperatureSheetDataRequest,
  GetNurseStationTemperatureSheetDataResponse,
  LoadNurseStationTemperatureSheetInitResponse,
  SaveNurseStationTemperatureSheetAtRecordedTimeRequest,
  SaveNurseStationTemperatureSheetAtRecordedTimeResponse,
} from './inpatient-nurse-station-temperature-sheet.types';

import { publicRequestClient } from '#/api/request';

const BASE = '/nurseStationTemperatureSheet';

/** 加载体温单页初始化配置 */
export async function httpLoadNurseStationTemperatureSheetInit(): Promise<LoadNurseStationTemperatureSheetInitResponse> {
  return publicRequestClient.post<LoadNurseStationTemperatureSheetInitResponse>(
    `${BASE}/loadNurseStationTemperatureSheetInit`,
    {},
  );
}

/** 加载体温单曲线、网格与分页数据 */
export async function httpGetNurseStationTemperatureSheetData(
  body: GetNurseStationTemperatureSheetDataRequest,
): Promise<GetNurseStationTemperatureSheetDataResponse> {
  return publicRequestClient.post<GetNurseStationTemperatureSheetDataResponse>(
    `${BASE}/getNurseStationTemperatureSheetData`,
    body,
  );
}

/** 按记录时点保存三测单数据 */
export async function httpSaveNurseStationTemperatureSheetAtRecordedTime(
  body: SaveNurseStationTemperatureSheetAtRecordedTimeRequest,
): Promise<SaveNurseStationTemperatureSheetAtRecordedTimeResponse> {
  return publicRequestClient.post<SaveNurseStationTemperatureSheetAtRecordedTimeResponse>(
    `${BASE}/saveNurseStationTemperatureSheetAtRecordedTime`,
    body,
  );
}

/** 删除一条已保存的体温单记录 */
export async function httpDeleteNurseStationTemperatureSheet(
  body: DeleteNurseStationTemperatureSheetRequest,
): Promise<DeleteNurseStationTemperatureSheetResponse> {
  return publicRequestClient.post<DeleteNurseStationTemperatureSheetResponse>(
    `${BASE}/deleteNurseStationTemperatureSheet`,
    body,
  );
}
