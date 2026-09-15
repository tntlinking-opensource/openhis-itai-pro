/**
 * 卫统 N041 CSV 导出 API（MrmsWeistatExportAppService）
 */

import type { BinaryExportResult } from '#/utils/export/download-binary-export';

import { publicRequestClient } from '#/api/request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

const BASE = '/mrmsWeistatExport';

/** 卫统 N041 批量导出请求 */
export interface ExportN041CsvParams {
  /** 病案主档主键列表 */
  caseRecordIds: Array<number | string>;
}

/**
 * 多选病案导出卫统 N041 CSV。
 *
 * @param params 病案主键列表
 */
export async function exportWeistatN041CsvApi(
  params: ExportN041CsvParams,
): Promise<BinaryExportResult> {
  const caseRecordIds = (params.caseRecordIds ?? [])
    .map((id) => preserveApiIdForRequest(id))
    .filter((id) => id != null && String(id).length > 0);
  return publicRequestClient.post(`${BASE}/exportN041Csv`, {
    caseRecordIds,
  });
}
