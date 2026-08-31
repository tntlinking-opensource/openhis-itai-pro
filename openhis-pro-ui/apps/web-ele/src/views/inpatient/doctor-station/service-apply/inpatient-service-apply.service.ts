import type {
  DoctorStationAdviceCatalogRowDto,
  SaveDoctorStationServiceApplyFormRequest,
} from '#/api/doctor-station/advice.types';
import type { EncounterDiagnosisRowDto } from '#/api/doctor-station/diagnosis.types';
import type { ServiceApplyKind } from '#/views/outpatient/doctor-station/service-apply/service-apply.types';

import {
  httpInitInpatientDoctorStationAdvice,
  httpPageInpatientDoctorStationAdviceCatalog,
  httpSaveInpatientDoctorStationServiceApplyForm,
} from '#/api/inpatient/doctor-station/inpatient-doctor-station-advice.http';
import { fetchEncounterDiagnosisDisplayList } from '#/components/doctor-station/diagnosis/services/encounter-diagnosis.service';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { loadAllDepartmentPerformOrgOptions } from '#/views/doctor-station/advice-shared/perform-org-options.service';
import {
  SERVICE_APPLY_ACTIVITY_TABLE,
  SERVICE_APPLY_CATALOG_PAGE_SIZE,
} from '#/views/outpatient/doctor-station/service-apply/service-apply.constants';
import {
  isValidServiceApplyCatalogRow,
  resolveServiceApplyCategoryEnum,
  signServiceApplyRequests as signServiceApplyRequestsShared,
} from '#/views/outpatient/doctor-station/service-apply/service-apply.service';

export {
  buildDetailItems,
  buildSaveServiceApplyFormRequest,
  buildServiceApplyDiagnosisOptions,
  catalogRowCategoryLabel,
  catalogRowKey,
  catalogRowUnitPrice,
  detailTargetIds,
  isValidServiceApplyCatalogRow,
  mergeCatalogDefaultPerformOrgOptions,
  pickDefaultServiceApplyEncounterDiagnosisId,
  resolveDetailTargetTable,
  resolveServiceApplyCategoryEnum,
  resolveServiceApplyDiagnosisIds,
  validateServiceApplySelectedItems,
} from '#/views/outpatient/doctor-station/service-apply/service-apply.service';

export type {
  ServiceApplyInitOptions,
  ServiceApplyOrgOption,
} from '#/views/outpatient/doctor-station/service-apply/service-apply.service';

/** 拉取当前住院就诊已保存诊断行（中医病证组合并展示） */
export async function loadServiceApplyEncounterDiagnosisRows(
  encounterId: string,
  patientId: string,
): Promise<EncounterDiagnosisRowDto[]> {
  return fetchEncounterDiagnosisDisplayList({ encounterId, patientId }, 'inpatient');
}

/** 加载部位/样本下拉（住院 init） */
export async function loadServiceApplyInitOptions(
  encounterId: string,
  patientId: string,
): Promise<
  import('#/views/outpatient/doctor-station/service-apply/service-apply.service').ServiceApplyInitOptions
> {
  const res = await httpInitInpatientDoctorStationAdvice({ encounterId, patientId });
  return {
    bodyStructureOptions: res.bodyStructureOptions ?? [],
    specimenDefinitionOptions: res.specimenDefinitionOptions ?? [],
  };
}

/** 分页检索住院申请单可选诊疗项目 */
export async function loadServiceApplyCatalog(params: {
  applyKind: ServiceApplyKind;
  encounterId: string;
  keyword?: string;
  organizationUnitId?: string;
  pageNo?: number;
  pageSize?: number;
  patientId: string;
}): Promise<{ rows: DoctorStationAdviceCatalogRowDto[]; totalCount: number }> {
  const res = await httpPageInpatientDoctorStationAdviceCatalog({
    encounterId: params.encounterId,
    patientId: params.patientId,
    organizationUnitId: params.organizationUnitId,
    keyword: params.keyword?.trim() || undefined,
    pageNo: params.pageNo ?? 1,
    pageSize: params.pageSize ?? SERVICE_APPLY_CATALOG_PAGE_SIZE,
    productTableNames: [SERVICE_APPLY_ACTIVITY_TABLE],
    categoryEnum: resolveServiceApplyCategoryEnum(params.applyKind),
  });
  const { content, totalCount } = parsePagedRowsFromApiData<DoctorStationAdviceCatalogRowDto>(res);
  const rows = content.filter((row) => isValidServiceApplyCatalogRow(row));
  return { rows, totalCount };
}

/** 申请单执行科室为整单全局字段，下拉始终全院科室（与门诊一致，不按单项目规则收窄） */
export async function loadServiceApplyOrgOptions(_params?: {
  activityDefinitionId: string;
  encounterId: string;
  patientId: string;
}): Promise<
  import('#/views/outpatient/doctor-station/service-apply/service-apply.service').ServiceApplyOrgOption[]
> {
  return loadAllDepartmentPerformOrgOptions();
}

/** 整单执行科室下拉：全局选一个，清单内全部项目共用该科室（与门诊一致） */
export async function mergeServiceApplyOrgOptions(_params?: {
  activityDefinitionIds: string[];
  encounterId: string;
  patientId: string;
}): Promise<
  import('#/views/outpatient/doctor-station/service-apply/service-apply.service').ServiceApplyOrgOption[]
> {
  return loadAllDepartmentPerformOrgOptions();
}

/** 保存住院申请单（整张），返回新 AR 号与 requestIds */
export async function saveServiceApplyForm(
  request: SaveDoctorStationServiceApplyFormRequest,
): Promise<{ prescriptionNo: string; requestIds: string[] }> {
  const res = await httpSaveInpatientDoctorStationServiceApplyForm(request);
  const prescriptionNo = String(res.prescriptionNo ?? '').trim();
  const requestIds = (res.requestIds ?? []).map((id) => String(id ?? '').trim()).filter(Boolean);
  if (!prescriptionNo || requestIds.length === 0) {
    throw new Error('保存成功但未返回申请单号或医嘱标识');
  }
  return { prescriptionNo, requestIds };
}

/** 签发申请单下全部 ServiceRequest 草稿 */
export async function signServiceApplyRequests(params: {
  encounterId: string;
  patientId: string;
  requestIds: string[];
  stationScope?: 'inpatient' | 'outpatient';
}): Promise<void> {
  await signServiceApplyRequestsShared({
    ...params,
    stationScope: params.stationScope ?? 'inpatient',
  });
}
