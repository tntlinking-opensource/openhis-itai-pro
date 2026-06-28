import type { SaveDoctorStationServiceApplyFormRequest } from '#/api/doctor-station/advice.types';
import type { EncounterDiagnosisRowDto } from '#/api/doctor-station/diagnosis.types';

import {
  httpInitInpatientDoctorStationAdvice,
  httpListInpatientActivityExecutionLocations,
  httpPageInpatientDoctorStationAdviceCatalog,
  httpSaveInpatientDoctorStationServiceApplyForm,
  httpSignInpatientDoctorStationAdviceBatch,
} from '#/api/inpatient/doctor-station/inpatient-doctor-station-advice.http';
import { fetchEncounterDiagnosisDisplayList } from '#/components/doctor-station/diagnosis/services/encounter-diagnosis.service';

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

import type { ServiceApplyKind } from '#/views/outpatient/doctor-station/service-apply/service-apply.types';
import {
  SERVICE_APPLY_ACTIVITY_TABLE,
  SERVICE_APPLY_CATALOG_PAGE_SIZE,
  SERVICE_APPLY_SIGN_PRODUCT_TABLE,
} from '#/views/outpatient/doctor-station/service-apply/service-apply.constants';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import type { DoctorStationAdviceCatalogRowDto } from '#/api/doctor-station/advice.types';

import {
  isValidServiceApplyCatalogRow,
  resolveServiceApplyCategoryEnum,
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
  encounterId: string;
  patientId: string;
  applyKind: ServiceApplyKind;
  keyword?: string;
  pageNo?: number;
  pageSize?: number;
  organizationUnitId?: string;
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
  const rows = content.filter(isValidServiceApplyCatalogRow);
  return { rows, totalCount };
}

/** 按诊疗定义拉取可执行科室（住院 execution locations） */
export async function loadServiceApplyOrgOptions(params: {
  encounterId: string;
  patientId: string;
  activityDefinitionId: string;
}): Promise<
  import('#/views/outpatient/doctor-station/service-apply/service-apply.service').ServiceApplyOrgOption[]
> {
  const activityDefinitionId = preserveApiIdForRequest(params.activityDefinitionId);
  if (!activityDefinitionId) return [];
  const res = await httpListInpatientActivityExecutionLocations({
    encounterId: params.encounterId,
    patientId: params.patientId,
    activityDefinitionId,
  });
  const map = new Map<string, string>();
  for (const rule of res.rules ?? []) {
    const orgId = String(rule.executionOrgId ?? '').trim();
    if (!orgId) continue;
    const orgName = String(rule.executionOrgName ?? '').trim();
    if (orgName) {
      map.set(orgId, orgName);
    } else if (!map.has(orgId)) {
      map.set(orgId, orgId);
    }
  }
  return [...map.entries()].map(([value, label]) => ({ value, label }));
}

/** 合并多个诊疗项的执行科室下拉 */
export async function mergeServiceApplyOrgOptions(params: {
  encounterId: string;
  patientId: string;
  activityDefinitionIds: string[];
}): Promise<
  import('#/views/outpatient/doctor-station/service-apply/service-apply.service').ServiceApplyOrgOption[]
> {
  const map = new Map<string, string>();
  const ids = [
    ...new Set(params.activityDefinitionIds.map((id) => String(id).trim()).filter(Boolean)),
  ];
  for (const activityDefinitionId of ids) {
    const options = await loadServiceApplyOrgOptions({
      encounterId: params.encounterId,
      patientId: params.patientId,
      activityDefinitionId,
    });
    for (const opt of options) {
      const prev = map.get(opt.value);
      if (!prev || (prev === opt.value && opt.label !== opt.value)) {
        map.set(opt.value, opt.label);
      }
    }
  }
  return [...map.entries()].map(([value, label]) => ({ value, label }));
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
}): Promise<void> {
  const signTargets = params.requestIds.map((requestId) => ({
    requestId: preserveApiIdForRequest(requestId) ?? requestId,
    productTableName: SERVICE_APPLY_SIGN_PRODUCT_TABLE,
  }));
  const res = await httpSignInpatientDoctorStationAdviceBatch({
    encounterId: params.encounterId,
    patientId: params.patientId,
    signTargets,
  });
  if (res.success === false) {
    throw new Error(String(res.message ?? '').trim() || '签发失败');
  }
}
