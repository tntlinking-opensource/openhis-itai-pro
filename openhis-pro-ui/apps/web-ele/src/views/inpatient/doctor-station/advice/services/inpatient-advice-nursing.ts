import type {
  InpatientDoctorStationAdviceCatalogRowDto,
  InpatientDoctorStationAdviceRowDto,
} from '#/api/inpatient/doctor-station/inpatient-doctor-station-advice.types';

import {
  httpListInpatientDoctorStationAdvice,
  httpPageInpatientDoctorStationAdviceCatalog,
  httpSaveInpatientDoctorStationAdviceDraftBatch,
  httpSignInpatientDoctorStationAdviceBatch,
} from '#/api/inpatient/doctor-station/inpatient-doctor-station-advice.http';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

import { mapCatalogRowToOption } from './advice-list';
import type { AdviceProductOption } from './advice.types';
import {
  INPATIENT_NURSING_LEVEL_CATALOG_TABLE_NAMES,
  INPATIENT_NURSING_LEVEL_CATEGORY_CODE,
} from './advice-tab.constants';

/** 护理级别开立参数（IF-IP-DOR-14/15 · BR-IP-ORD-026） */
export interface SaveAndSignNursingLevelAdviceParams {
  encounterId: string;
  patientId: string;
  activityDefinitionId: string;
  rateCode: string;
  unitCode: string;
  performOrgId?: string;
}

const ACTIVITY_PRODUCT_TABLE = 'wkf_activity_definition';

/** 目录行是否为护理级别（category_code=26） */
export function isNursingLevelCatalogRow(
  row: Pick<InpatientDoctorStationAdviceCatalogRowDto, 'categoryCode'>,
): boolean {
  return String(row.categoryCode ?? '').trim() === INPATIENT_NURSING_LEVEL_CATEGORY_CODE;
}

/** 加载护理级别目录选项（IF-IP-DOR-11；客户端按 categoryCode=26 过滤） */
export async function loadNursingLevelCatalogOptions(params: {
  encounterId: string;
  patientId: string;
  organizationUnitId?: string;
}): Promise<AdviceProductOption[]> {
  const encounterId = params.encounterId.trim();
  const patientId = params.patientId.trim();
  const res = await httpPageInpatientDoctorStationAdviceCatalog({
    encounterId,
    patientId,
    organizationUnitId: params.organizationUnitId,
    productTableNames: [...INPATIENT_NURSING_LEVEL_CATALOG_TABLE_NAMES],
    // 下推 ActivityDef.category_code=26，避免全量诊疗前 200 条截断漏掉护理级别
    categoryEnum: INPATIENT_NURSING_LEVEL_CATEGORY_CODE,
    pageNo: 1,
    pageSize: 200,
  });
  const { content } = parsePagedRowsFromApiData<InpatientDoctorStationAdviceCatalogRowDto>(res);
  return content.filter(isNursingLevelCatalogRow).map((row) => mapCatalogRowToOption(row));
}

/** 从统一列表中定位刚暂存的护理级别草稿行 */
function findDraftNursingLevelRow(
  rows: InpatientDoctorStationAdviceRowDto[],
  activityDefinitionId: string,
): InpatientDoctorStationAdviceRowDto | undefined {
  const targetActId = activityDefinitionId.trim();
  return rows.find((row) => {
    const category = String(row.serviceCategoryEnum ?? row.catalogCategoryCode ?? '').trim();
    if (category !== INPATIENT_NURSING_LEVEL_CATEGORY_CODE) return false;
    const status = String(row.statusEnum ?? '')
      .trim()
      .toUpperCase();
    if (status !== 'DRAFT') return false;
    const actId = String(row.productDefinitionId ?? '').trim();
    return actId === targetActId;
  });
}

/** 暂存并签发护理级别长期医嘱（仅 category 26） */
export async function saveAndSignNursingLevelAdvice(params: SaveAndSignNursingLevelAdviceParams) {
  const encounterId = params.encounterId.trim();
  const patientId = params.patientId.trim();
  const activityDefinitionId = params.activityDefinitionId.trim();
  const rateCode = params.rateCode.trim();
  const unitCode = params.unitCode.trim() || '次';
  const performOrgId = String(params.performOrgId ?? '').trim();

  const saveRes = await httpSaveInpatientDoctorStationAdviceDraftBatch({
    encounterId,
    patientId,
    lines: [
      {
        lineOp: 'CREATE',
        productTableName: ACTIVITY_PRODUCT_TABLE,
        therapyTypeCode: 'LONG_TERM',
        therapyEnum: 'LONG_TERM',
        sortNumber: 1,
        servicePayload: {
          activityDefinitionId,
          quantity: 1,
          unitCode,
          rateCode,
          ...(performOrgId && /^\d+$/.test(performOrgId) ? { performOrgId } : {}),
        },
      },
    ],
  });
  if (saveRes.success === false) {
    throw new Error(String(saveRes.message ?? '').trim() || '暂存护理级别失败');
  }

  const listRes = await httpListInpatientDoctorStationAdvice({ encounterId, patientId });
  const { content: listRows } =
    parsePagedRowsFromApiData<InpatientDoctorStationAdviceRowDto>(listRes);
  const draftRow = findDraftNursingLevelRow(listRows, activityDefinitionId);
  const requestId = String(draftRow?.requestId ?? '').trim();
  if (!requestId) {
    throw new Error('未找到新建的护理级别草稿，请刷新后重试');
  }

  const signRes = await httpSignInpatientDoctorStationAdviceBatch({
    encounterId,
    patientId,
    signTargets: [{ productTableName: ACTIVITY_PRODUCT_TABLE, requestId }],
  });
  if (signRes.success === false) {
    throw new Error(String(signRes.message ?? '').trim() || '签发护理级别失败');
  }
  return signRes;
}

/** 患者队列护理级别是否仍为有效态（换级须先停嘱） */
export function hasActiveNursingLevelOnPatient(statusEnum: string | undefined | null): boolean {
  const status = String(statusEnum ?? '')
    .trim()
    .toUpperCase();
  return status === 'DRAFT' || status === 'ACTIVE' || status === 'VERIFIED';
}
