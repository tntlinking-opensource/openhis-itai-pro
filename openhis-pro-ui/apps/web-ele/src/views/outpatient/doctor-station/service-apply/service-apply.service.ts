import type {
  DoctorStationAdviceCatalogRowDto,
  SaveDoctorStationServiceApplyFormRequest,
  ServiceApplyFormDetailItemRequest,
  TerminologyOptionDto,
} from '#/api/doctor-station/advice.types';
import type { EncounterDiagnosisRowDto } from '#/api/doctor-station/diagnosis.types';
import { httpGetEncounterDiagnosis } from '#/api/doctor-station/diagnosis.http';
import { resolveEncounterDiagnosisDisplayRows } from '#/components/doctor-station/diagnosis/services/encounter-diagnosis-select';
import {
  httpInitDoctorStationAdvice,
  httpListActivityExecutionLocations,
  httpPageDoctorStationAdviceCatalog,
  httpSaveDoctorStationServiceApplyForm,
  httpSignDoctorStationAdviceBatch,
} from '#/api/doctor-station/advice.http';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { preserveApiIdForRequest } from '#/utils/preserve-api-id';

import { pickMainDiagnosisDisplayName } from '../advice/services/advice-edit';

import type {
  ServiceApplyDiagnosisOption,
  ServiceApplyKind,
  ServiceApplySelectedItem,
} from './service-apply.types';
import {
  SERVICE_APPLY_ACTIVITY_TABLE,
  SERVICE_APPLY_BODY_STRUCTURE_TABLE,
  SERVICE_APPLY_CATEGORY_BY_KIND,
  SERVICE_APPLY_CATALOG_PAGE_SIZE,
  SERVICE_APPLY_SIGN_PRODUCT_TABLE,
  SERVICE_APPLY_SPECIMEN_TABLE,
} from './service-apply.constants';

export interface ServiceApplyInitOptions {
  bodyStructureOptions: TerminologyOptionDto[];
  specimenDefinitionOptions: TerminologyOptionDto[];
}

export interface ServiceApplyOrgOption {
  value: string;
  label: string;
}

/** 申请单类型 → 后端 categoryEnum */
export function resolveServiceApplyCategoryEnum(kind: ServiceApplyKind): string {
  return SERVICE_APPLY_CATEGORY_BY_KIND[kind];
}

/** 拉取当前就诊已保存诊断行 */
export async function loadServiceApplyEncounterDiagnosisRows(
  encounterId: string,
  patientId: string,
): Promise<EncounterDiagnosisRowDto[]> {
  const res = await httpGetEncounterDiagnosis({ encounterId, patientId });
  const list = res?.diagnosisList ?? res?.content ?? [];
  const rows = Array.isArray(list) ? list : [];
  return resolveEncounterDiagnosisDisplayRows(rows);
}

/** 就诊诊断行 → 申请单诊断下拉项 */
export function buildServiceApplyDiagnosisOptions(
  rows: EncounterDiagnosisRowDto[],
): ServiceApplyDiagnosisOption[] {
  return rows
    .map((row) => {
      const label = String(row.conditionDefinitionName ?? '').trim();
      const value = String(row.encounterDiagnosisId ?? row.conditionId ?? '').trim();
      const conditionDefinitionId = String(row.conditionDefinitionId ?? '').trim() || undefined;
      if (!label || !value) return null;
      return { label, value, conditionDefinitionId };
    })
    .filter((item): item is ServiceApplyDiagnosisOption => item !== null);
}

/** 默认选中主诊断，无则取首条 */
export function pickDefaultServiceApplyEncounterDiagnosisId(
  rows: EncounterDiagnosisRowDto[],
  options: ServiceApplyDiagnosisOption[],
): string {
  if (options.length === 0) return '';
  const mainName = pickMainDiagnosisDisplayName(rows);
  if (mainName) {
    const hit = options.find((item) => item.label === mainName);
    if (hit) return hit.value;
  }
  return options[0]?.value ?? '';
}

/** 由所选就诊诊断主键解析保存用双字段 */
export function resolveServiceApplyDiagnosisIds(
  encounterDiagnosisId: string,
  rows: EncounterDiagnosisRowDto[],
): { conditionDefinitionId?: string; encounterDiagnosisId?: string } {
  const selectedId = String(encounterDiagnosisId ?? '').trim();
  if (!selectedId) return {};
  const hit = rows.find((row) => {
    const eid = String(row.encounterDiagnosisId ?? '').trim();
    const cid = String(row.conditionId ?? '').trim();
    return eid === selectedId || cid === selectedId;
  });
  const conditionDefinitionId = String(hit?.conditionDefinitionId ?? '').trim() || undefined;
  const resolvedEncounterDiagnosisId =
    String(hit?.encounterDiagnosisId ?? '').trim() || selectedId || undefined;
  return {
    ...(conditionDefinitionId ? { conditionDefinitionId } : {}),
    ...(resolvedEncounterDiagnosisId ? { encounterDiagnosisId: resolvedEncounterDiagnosisId } : {}),
  };
}

/** 目录行稳定键（productTableName + productDefinitionId） */
export function catalogRowKey(row: DoctorStationAdviceCatalogRowDto): string {
  const tbl = String(row.productTableName ?? SERVICE_APPLY_ACTIVITY_TABLE).trim();
  const id = String(row.productDefinitionId ?? '').trim();
  return `${tbl}:${id}`;
}

/** 目录行展示类别 */
export function catalogRowCategoryLabel(row: DoctorStationAdviceCatalogRowDto): string {
  return String(row.categoryCodeDictText ?? row.categoryCode ?? '').trim() || '—';
}

/** 目录行展示单价 */
export function catalogRowUnitPrice(row: DoctorStationAdviceCatalogRowDto): number {
  const raw = row.unitPrice;
  if (raw === null || raw === undefined || String(raw).trim() === '') return 0;
  const n = Number(raw);
  return Number.isFinite(n) ? n : 0;
}

/** 校验目录行是否可用于加入清单 */
export function isValidServiceApplyCatalogRow(row: DoctorStationAdviceCatalogRowDto): boolean {
  const activityDefinitionId = String(row.productDefinitionId ?? '').trim();
  const name = String(row.name ?? '').trim();
  const unitCode = String(row.unitCode ?? '').trim();
  return Boolean(activityDefinitionId && name && unitCode);
}

/** 申请单明细目标表名 */
export function resolveDetailTargetTable(applyKind: ServiceApplyKind): string {
  return applyKind === 'lab' ? SERVICE_APPLY_SPECIMEN_TABLE : SERVICE_APPLY_BODY_STRUCTURE_TABLE;
}

/** 由多选 targetId 构造 detailItems */
export function buildDetailItems(
  applyKind: ServiceApplyKind,
  targetIds: string[],
): ServiceApplyFormDetailItemRequest[] {
  const targetTable = resolveDetailTargetTable(applyKind);
  return targetIds
    .map((id) => String(id ?? '').trim())
    .filter(Boolean)
    .map((targetId) => ({ targetTable, targetId }));
}

/** 从 detailItems 提取 targetId 列表（多选绑定用） */
export function detailTargetIds(items: ServiceApplyFormDetailItemRequest[]): string[] {
  return items.map((item) => String(item.targetId ?? '').trim()).filter(Boolean);
}

/** 加载部位/样本下拉 */
export async function loadServiceApplyInitOptions(
  encounterId: string,
  patientId: string,
): Promise<ServiceApplyInitOptions> {
  const res = await httpInitDoctorStationAdvice({ encounterId, patientId });
  return {
    bodyStructureOptions: res.bodyStructureOptions ?? [],
    specimenDefinitionOptions: res.specimenDefinitionOptions ?? [],
  };
}

/** 分页检索申请单可选诊疗项目 */
export async function loadServiceApplyCatalog(params: {
  encounterId: string;
  patientId: string;
  applyKind: ServiceApplyKind;
  keyword?: string;
  pageNo?: number;
  pageSize?: number;
  organizationUnitId?: string;
}): Promise<{ rows: DoctorStationAdviceCatalogRowDto[]; totalCount: number }> {
  const res = await httpPageDoctorStationAdviceCatalog({
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

/** 按诊疗定义拉取可执行科室（去重） */
export async function loadServiceApplyOrgOptions(params: {
  encounterId: string;
  patientId: string;
  activityDefinitionId: string;
}): Promise<ServiceApplyOrgOption[]> {
  const activityDefinitionId = preserveApiIdForRequest(params.activityDefinitionId);
  if (!activityDefinitionId) return [];
  const res = await httpListActivityExecutionLocations({
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

/** 用目录 defaultPerformOrg* 补全/修正执行科室下拉 label */
export function mergeCatalogDefaultPerformOrgOptions(
  options: ServiceApplyOrgOption[],
  catalogRows: DoctorStationAdviceCatalogRowDto[],
  selectedCatalogRowKeys: string[],
): ServiceApplyOrgOption[] {
  const merged = [...options];
  for (const key of selectedCatalogRowKeys) {
    const hit = catalogRows.find((row) => catalogRowKey(row) === key);
    if (!hit) continue;
    const orgId = String(hit.defaultPerformOrgId ?? '').trim();
    const orgName = String(hit.defaultPerformOrgName ?? '').trim();
    if (!orgId) continue;
    const existing = merged.find((o) => o.value === orgId);
    if (existing) {
      if (orgName) existing.label = orgName;
    } else {
      merged.unshift({ value: orgId, label: orgName || orgId });
    }
  }
  return merged;
}

/** 合并多个诊疗项的执行科室下拉 */
export async function mergeServiceApplyOrgOptions(params: {
  encounterId: string;
  patientId: string;
  activityDefinitionIds: string[];
}): Promise<ServiceApplyOrgOption[]> {
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

/** 校验已选行是否满足保存前置条件 */
export function validateServiceApplySelectedItems(
  items: ServiceApplySelectedItem[],
  orgId: string,
  encounterDiagnosisId: string,
): string | null {
  if (items.length === 0) return '请先选择申请项目';
  if (!String(orgId ?? '').trim()) return '请选择执行科室';
  if (!String(encounterDiagnosisId ?? '').trim()) return '请选择诊断';
  for (const item of items) {
    if (!String(item.activityDefinitionId ?? '').trim()) return '存在无效项目，请移除后重选';
    if (!String(item.unitCode ?? '').trim())
      return `「${item.activityName}」缺少单位，请移除后重选`;
    if (Number(item.quantity) <= 0) return `「${item.activityName}」数量须大于 0`;
    if ((item.detailItems ?? []).length === 0) {
      return `「${item.activityName}」须选择部位/样本明细`;
    }
  }
  return null;
}

/** 组装整张申请单保存请求 */
export function buildSaveServiceApplyFormRequest(params: {
  encounterId: string;
  patientId: string;
  applyKind: ServiceApplyKind;
  orgId: string;
  encounterDiagnosisId: string;
  encounterDiagnosisRows: EncounterDiagnosisRowDto[];
  previousPrescriptionNo?: string;
  items: ServiceApplySelectedItem[];
}): SaveDoctorStationServiceApplyFormRequest {
  const diagnosisIds = resolveServiceApplyDiagnosisIds(
    params.encounterDiagnosisId,
    params.encounterDiagnosisRows,
  );
  const lines = params.items.map((item) => ({
    activityDefinitionId:
      preserveApiIdForRequest(item.activityDefinitionId) ?? item.activityDefinitionId,
    quantity: item.quantity,
    unitCode: item.unitCode,
    remark: String(item.remark ?? '').trim() || undefined,
    detailItems: (item.detailItems ?? []).map((detail) => ({
      targetTable: detail.targetTable,
      targetId: preserveApiIdForRequest(detail.targetId) ?? detail.targetId,
      remark: String(detail.remark ?? '').trim() || undefined,
    })),
  }));
  return {
    encounterId: params.encounterId,
    patientId: params.patientId,
    categoryEnum: resolveServiceApplyCategoryEnum(params.applyKind),
    orgId: preserveApiIdForRequest(params.orgId) ?? params.orgId,
    ...(diagnosisIds.conditionDefinitionId
      ? {
          conditionDefinitionId:
            preserveApiIdForRequest(diagnosisIds.conditionDefinitionId) ??
            diagnosisIds.conditionDefinitionId,
        }
      : {}),
    ...(diagnosisIds.encounterDiagnosisId
      ? {
          encounterDiagnosisId:
            preserveApiIdForRequest(diagnosisIds.encounterDiagnosisId) ??
            diagnosisIds.encounterDiagnosisId,
        }
      : {}),
    previousPrescriptionNo: params.previousPrescriptionNo?.trim() || undefined,
    lines,
  };
}

/** 保存申请单（整张），返回新 AR 号与 requestIds */
export async function saveServiceApplyForm(
  request: SaveDoctorStationServiceApplyFormRequest,
): Promise<{ prescriptionNo: string; requestIds: string[] }> {
  const res = await httpSaveDoctorStationServiceApplyForm(request);
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
  const res = await httpSignDoctorStationAdviceBatch({
    encounterId: params.encounterId,
    patientId: params.patientId,
    signTargets,
  });
  if (res.success === false) {
    throw new Error(String(res.message ?? '').trim() || '签发失败');
  }
}
