/**
 * HQMS 住院病案首页 CSV 导出 API（MrmsHqmsExportAppService）
 */

import type { BinaryExportResult } from '#/utils/export/download-binary-export';

import { publicRequestClient } from '#/api/request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

const BASE = '/mrmsHqmsExport';

/** HQMS 批量导出请求 */
export interface ExportHqmsCsvParams {
  /** 病案主档主键列表 */
  caseRecordIds: Array<number | string>;
}

/**
 * 多选病案导出 HQMS CSV。
 *
 * @param params 病案主键列表
 */
export async function exportHqmsCsvApi(params: ExportHqmsCsvParams): Promise<BinaryExportResult> {
  const caseRecordIds = (params.caseRecordIds ?? [])
    .map((id) => preserveApiIdForRequest(id))
    .filter((id) => id != null && String(id).length > 0);
  return publicRequestClient.post(`${BASE}/exportHqmsCsv`, {
    caseRecordIds,
  });
}
