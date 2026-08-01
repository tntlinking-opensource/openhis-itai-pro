import type { InpatientDoctorStationAdviceCatalogRowDto } from '#/api/inpatient/doctor-station/inpatient-doctor-station-advice.types';

import { httpPageInpatientDoctorStationAdviceCatalog } from '#/api/inpatient/doctor-station/inpatient-doctor-station-advice.http';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

import { mapCatalogRowToOption } from './advice-list';
import type { AdviceProductOption } from './advice.types';
import {
  INPATIENT_ENTRUST_ACTIVITY_NAME,
  INPATIENT_ENTRUST_CATALOG_TABLE_NAMES,
} from './advice-tab.constants';

/** 快捷开立嘱托：选品结果（必须恰好一条） */
export type LoadEntrustCatalogResult =
  | { ok: true; option: AdviceProductOption }
  | { ok: false; reason: 'EMPTY' | 'MULTIPLE' };

/**
 * 按关键字「嘱托」检索目录（IF-IP-DOR-11）。
 * 必须恰好 1 条才可开立；0 条或多条均拒绝。
 */
export async function loadEntrustCatalogOption(params: {
  encounterId: string;
  patientId: string;
  organizationUnitId?: string;
}): Promise<LoadEntrustCatalogResult> {
  const encounterId = params.encounterId.trim();
  const patientId = params.patientId.trim();
  if (!encounterId || !patientId) {
    return { ok: false, reason: 'EMPTY' };
  }

  const res = await httpPageInpatientDoctorStationAdviceCatalog({
    encounterId,
    patientId,
    organizationUnitId: params.organizationUnitId,
    productTableNames: [...INPATIENT_ENTRUST_CATALOG_TABLE_NAMES],
    keyword: INPATIENT_ENTRUST_ACTIVITY_NAME,
    pageNo: 1,
    pageSize: 20,
  });
  const { content, totalCount } =
    parsePagedRowsFromApiData<InpatientDoctorStationAdviceCatalogRowDto>(res);
  // 以本次返回行数与 totalCount 取较大值判断是否「多条」
  const hitCount = Math.max(content.length, totalCount);
  if (hitCount <= 0 || content.length === 0) {
    return { ok: false, reason: 'EMPTY' };
  }
  if (hitCount > 1 || content.length > 1) {
    return { ok: false, reason: 'MULTIPLE' };
  }
  const only = content[0]!;
  return { ok: true, option: mapCatalogRowToOption(only) };
}
