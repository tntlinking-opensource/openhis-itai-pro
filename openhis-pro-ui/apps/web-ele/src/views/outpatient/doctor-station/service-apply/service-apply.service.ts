import type { AdviceRowItem } from '../advice/services/advice.types';
import type {
  ServiceApplyDiagnosisOption,
  ServiceApplyKind,
  ServiceApplySelectedItem,
} from './service-apply.types';

import type {
  DoctorStationAdviceCatalogRowDto,
  SaveDoctorStationServiceApplyFormRequest,
  ServiceApplyFormDetailItemRequest,
  ServiceRequestDetailSummaryDto,
  TerminologyOptionDto,
} from '#/api/doctor-station/advice.types';
import type { EncounterDiagnosisRowDto } from '#/api/doctor-station/diagnosis.types';

import {
  httpInitDoctorStationAdvice,
  httpPageDoctorStationAdviceCatalog,
  httpSaveDoctorStationServiceApplyForm,
  httpSignDoctorStationAdviceBatch,
} from '#/api/doctor-station/advice.http';
import { httpGetEncounterDiagnosis } from '#/api/doctor-station/diagnosis.http';
import {
  joinEncounterDiagnosisIds,
  parseEncounterDiagnosisIds,
  pickSoleEncounterDiagnosisIds,
  resolveEncounterDiagnosisDisplayRows,
  resolveSessionDefaultEncounterDiagnosisIds,
} from '#/components/doctor-station/diagnosis/services/encounter-diagnosis-select';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { loadAllDepartmentPerformOrgOptions } from '#/views/doctor-station/advice-shared/perform-org-options.service';

import {
  isServiceApplyDetailRequired,
  SERVICE_APPLY_ACTIVITY_TABLE,
  SERVICE_APPLY_BODY_STRUCTURE_TABLE,
  SERVICE_APPLY_CATALOG_PAGE_SIZE,
  SERVICE_APPLY_CATEGORY_BY_KIND,
  SERVICE_APPLY_KIND_LABEL,
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
  const options: ServiceApplyDiagnosisOption[] = [];
  for (const row of rows) {
    const label = String(row.conditionDefinitionName ?? '').trim();
    const value = String(row.encounterDiagnosisId ?? '').trim();
    if (!label || !value) continue;
    const conditionDefinitionId = String(row.conditionDefinitionId ?? '').trim() || undefined;
    options.push({
      label,
      value,
      ...(conditionDefinitionId ? { conditionDefinitionId } : {}),
    });
  }
  return options;
}

/** 默认选中：仅当就诊诊断恰好 1 条时默认该条（多诊断不默认）。 */
export function pickDefaultServiceApplyEncounterDiagnosisId(
  rows: EncounterDiagnosisRowDto[],
  options: ServiceApplyDiagnosisOption[],
): string {
  const ids = pickDefaultServiceApplyEncounterDiagnosisIds(rows, options);
  return ids[0] ?? '';
}

/** 默认勾选：唯一诊断 / 会话上次（由调用方传入 lastSelectedIds）。 */
export function pickDefaultServiceApplyEncounterDiagnosisIds(
  rows: EncounterDiagnosisRowDto[],
  options: ServiceApplyDiagnosisOption[],
  lastSelectedIds?: string[],
): string[] {
  if (options.length === 0) return [];
  const optionIdSet = new Set(options.map((item) => item.value));
  const resolved = resolveSessionDefaultEncounterDiagnosisIds([], rows, lastSelectedIds).filter(
    (id) => optionIdSet.has(id),
  );
  if (resolved.length > 0) return resolved;
  // options 与 rows 偶发不同步时：options 恰好 1 条也可默认
  if (options.length === 1) {
    const only = options[0]?.value;
    return only ? [only] : [];
  }
  return [];
}

/** 与医嘱一致的整单诊断默认（保留已选 → 上次 → 唯一）。 */
export function resolveServiceApplyDefaultEncounterDiagnosisIds(
  currentIds: string[],
  rows: EncounterDiagnosisRowDto[],
  lastSelectedIds?: string[],
): string[] {
  return resolveSessionDefaultEncounterDiagnosisIds(currentIds, rows, lastSelectedIds);
}

/** @deprecated 保留导出；请用 pickSoleEncounterDiagnosisIds */
export function pickSoleServiceApplyEncounterDiagnosisIds(
  rows: EncounterDiagnosisRowDto[],
): string[] {
  return pickSoleEncounterDiagnosisIds(rows);
}

/** 由多选就诊诊断主键拼保存用 ids 串（主诊第一） */
export function resolveServiceApplyEncounterDiagnosisIds(
  encounterDiagnosisIds: string[],
  rows: EncounterDiagnosisRowDto[],
): string | undefined {
  const ids = parseEncounterDiagnosisIds(encounterDiagnosisIds);
  if (ids.length === 0) return undefined;
  const idsText = joinEncounterDiagnosisIds(ids, rows);
  if (!idsText) return undefined;
  return parseEncounterDiagnosisIds(idsText)
    .map((id) => preserveApiIdForRequest(id) ?? id)
    .join(',');
}

/** @deprecated 改用 resolveServiceApplyEncounterDiagnosisIds */
export function resolveServiceApplyDiagnosisIds(
  encounterDiagnosisId: string,
  rows: EncounterDiagnosisRowDto[],
): { conditionDefinitionId?: string; encounterDiagnosisId?: string } {
  const selectedId = String(encounterDiagnosisId ?? '').trim();
  if (!selectedId) return {};
  const hit = rows.find((row) => {
    const eid = String(row.encounterDiagnosisId ?? '').trim();
    return eid === selectedId;
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

/** 申请单明细目标表名（检验→样本；检查/治疗→部位） */
export function resolveDetailTargetTable(applyKind: ServiceApplyKind): string {
  return applyKind === 'lab' ? SERVICE_APPLY_SPECIMEN_TABLE : SERVICE_APPLY_BODY_STRUCTURE_TABLE;
}

/** 申请单类型中文短名 */
export function resolveServiceApplyKindLabel(kind: ServiceApplyKind): string {
  return SERVICE_APPLY_KIND_LABEL[kind];
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

/** 深度优先取树形下拉首个可选 value（申请单默认部位） */
export function firstTreeOptionValue(options: TerminologyOptionDto[]): string {
  for (const opt of options) {
    const value = String(opt.value ?? '').trim();
    if (value) {
      return value;
    }
    if (opt.children?.length) {
      const nested = firstTreeOptionValue(opt.children);
      if (nested) {
        return nested;
      }
    }
  }
  return '';
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
  applyKind: ServiceApplyKind;
  encounterId: string;
  keyword?: string;
  organizationUnitId?: string;
  pageNo?: number;
  pageSize?: number;
  patientId: string;
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
  const rows = content.filter((item) => isValidServiceApplyCatalogRow(item));
  return { rows, totalCount };
}

/** 申请单执行科室为整单全局字段，下拉始终全院科室（不按单项目规则收窄） */
export async function loadServiceApplyOrgOptions(_params?: {
  activityDefinitionId: string;
  encounterId: string;
  patientId: string;
}): Promise<ServiceApplyOrgOption[]> {
  return loadAllDepartmentPerformOrgOptions();
}

/**
 * 用目录 defaultPerformOrg* 修正执行科室下拉 label。
 * 仅更新已在科室候选中的项；禁止把医院根等非科室 id 塞进下拉。
 */
export function mergeCatalogDefaultPerformOrgOptions(
  options: ServiceApplyOrgOption[],
  catalogRows: DoctorStationAdviceCatalogRowDto[],
  selectedCatalogRowKeys: string[],
): ServiceApplyOrgOption[] {
  const merged = [...options];
  const allowedIds = new Set(merged.map((item) => item.value));
  for (const key of selectedCatalogRowKeys) {
    const hit = catalogRows.find((row) => catalogRowKey(row) === key);
    if (!hit) continue;
    const orgId = String(hit.defaultPerformOrgId ?? '').trim();
    const orgName = String(hit.defaultPerformOrgName ?? '').trim();
    if (!orgId || !allowedIds.has(orgId)) continue;
    const existing = merged.find((o) => o.value === orgId);
    if (existing && orgName) {
      existing.label = orgName;
    }
  }
  return merged;
}

/** 从已选目录行中取首个仍落在科室候选内的默认执行科室 */
export function pickCatalogDefaultPerformOrgId(
  options: ServiceApplyOrgOption[],
  catalogRows: DoctorStationAdviceCatalogRowDto[],
  selectedCatalogRowKeys: string[],
): string {
  const allowedIds = new Set(options.map((item) => item.value));
  for (const key of selectedCatalogRowKeys) {
    const hit = catalogRows.find((row) => catalogRowKey(row) === key);
    if (!hit) continue;
    const orgId = String(hit.defaultPerformOrgId ?? '').trim();
    if (orgId && allowedIds.has(orgId)) {
      return orgId;
    }
  }
  return '';
}

/** 整单执行科室下拉：全局选一个，清单内全部项目共用该科室 */
export async function mergeServiceApplyOrgOptions(_params?: {
  activityDefinitionIds: string[];
  encounterId: string;
  patientId: string;
}): Promise<ServiceApplyOrgOption[]> {
  return loadAllDepartmentPerformOrgOptions();
}

/** 校验已选行是否满足保存前置条件（检验样本/治疗部位可选，BR-REQ-005） */
export function validateServiceApplySelectedItems(
  items: ServiceApplySelectedItem[],
  orgId: string,
  encounterDiagnosisIds: string[],
  applyKind: ServiceApplyKind,
  options?: { executionCount?: number; requireDiagnosis?: boolean },
): null | string {
  if (items.length === 0) return '请先选择申请项目';
  if (!String(orgId ?? '').trim()) return '请选择执行科室';
  const executionCount = Number(options?.executionCount ?? 0);
  if (!Number.isFinite(executionCount) || executionCount <= 0) {
    return '执行次数须大于 0';
  }
  // 住院申请单不展示诊断，跳过必填；门诊仍须选诊断
  const requireDiagnosis = options?.requireDiagnosis !== false;
  if (requireDiagnosis && parseEncounterDiagnosisIds(encounterDiagnosisIds).length === 0) {
    return '请选择诊断';
  }
  const detailRequired = isServiceApplyDetailRequired(applyKind);
  const detailLabel = applyKind === 'lab' ? '样本' : '部位';
  for (const item of items) {
    if (!String(item.activityDefinitionId ?? '').trim()) return '存在无效项目，请移除后重选';
    if (!String(item.unitCode ?? '').trim())
      return `「${item.activityName}」缺少单位，请移除后重选`;
    if (Number(item.quantity) <= 0) return `「${item.activityName}」数量须大于 0`;
    if (detailRequired && (item.detailItems ?? []).length === 0) {
      return `「${item.activityName}」须选择${detailLabel}明细`;
    }
  }
  return null;
}

/** 组装整张申请单保存请求 */
export function buildSaveServiceApplyFormRequest(params: {
  applyKind: ServiceApplyKind;
  encounterDiagnosisIds: string[];
  encounterDiagnosisRows: EncounterDiagnosisRowDto[];
  encounterId: string;
  /** 整单执行次数（各明细行共用） */
  executionCount: number;
  items: ServiceApplySelectedItem[];
  orgId: string;
  patientId: string;
  previousPrescriptionNo?: string;
}): SaveDoctorStationServiceApplyFormRequest {
  const encounterDiagnosisIds = resolveServiceApplyEncounterDiagnosisIds(
    params.encounterDiagnosisIds,
    params.encounterDiagnosisRows,
  );
  const lines = params.items.map((item) => {
    // 检验/治疗可无明细：空明细不传 detailItems
    const detailItems = (item.detailItems ?? [])
      .map((detail) => ({
        targetTable: detail.targetTable,
        targetId: preserveApiIdForRequest(detail.targetId) ?? detail.targetId,
        remark: String(detail.remark ?? '').trim() || undefined,
      }))
      .filter((detail) => String(detail.targetId ?? '').trim());
    return {
      activityDefinitionId:
        preserveApiIdForRequest(item.activityDefinitionId) ?? item.activityDefinitionId,
      quantity: item.quantity,
      unitCode: item.unitCode,
      remark: String(item.remark ?? '').trim() || undefined,
      ...(detailItems.length > 0 ? { detailItems } : {}),
    };
  });
  const executionCountRaw = Number(params.executionCount);
  const executionCount =
    Number.isFinite(executionCountRaw) && executionCountRaw > 0 ? Math.trunc(executionCountRaw) : 1;
  return {
    encounterId: params.encounterId,
    patientId: params.patientId,
    categoryEnum: resolveServiceApplyCategoryEnum(params.applyKind),
    orgId: preserveApiIdForRequest(params.orgId) ?? params.orgId,
    executionCount,
    ...(encounterDiagnosisIds ? { encounterDiagnosisIds } : {}),
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

/** 解析列表行金额文案为元数字 */
function parseAdviceLineAmountYuan(text: string): number {
  const normalized = String(text ?? '')
    .trim()
    .replaceAll(/[^\d.-]/g, '');
  const n = Number(normalized);
  return Number.isFinite(n) ? n : 0;
}

/** 列表 serviceRequestDetails → 保存用 detailItems */
export function mapServiceRequestDetailsToDetailItems(
  details: readonly ServiceRequestDetailSummaryDto[] | undefined,
  applyKind: ServiceApplyKind,
): ServiceApplyFormDetailItemRequest[] {
  const fallbackTable = resolveDetailTargetTable(applyKind);
  return (details ?? [])
    .map((detail) => ({
      targetTable: String(detail.targetTable ?? fallbackTable).trim() || fallbackTable,
      targetId: String(detail.targetId ?? '').trim(),
      remark: String(detail.remark ?? '').trim() || undefined,
    }))
    .filter((item) => item.targetId);
}

/** 列表行 → 编辑抽屉已选项目（回显用） */
export function buildServiceApplySelectedItemsFromAdviceRows(
  lines: readonly AdviceRowItem[],
  applyKind: ServiceApplyKind,
): ServiceApplySelectedItem[] {
  return lines
    .map((line) => {
      const activityDefinitionId = String(line.productDefinitionId ?? '').trim();
      const activityName = String(line.content ?? '').trim();
      // 数量取计价 quantity；执行次数为整单字段，不在此回填行级 quantity
      const quantityRaw = line.listQuantity ?? 1;
      const quantity = quantityRaw > 0 ? quantityRaw : 1;
      const lineAmount = parseAdviceLineAmountYuan(line.lineTotalAmount);
      const unitPrice = quantity > 0 ? lineAmount / quantity : 0;
      const catalogRowKey = `${SERVICE_APPLY_ACTIVITY_TABLE}:${activityDefinitionId}`;
      return {
        rowKey: `${catalogRowKey}-${line.key}`,
        catalogRowKey,
        activityDefinitionId,
        activityName,
        quantity,
        unitCode: String(line.listUnitCode ?? '').trim(),
        unitPrice,
        remark: String(line.remark ?? '').trim() || undefined,
        detailItems: mapServiceRequestDetailsToDetailItems(line.serviceRequestDetails, applyKind),
      };
    })
    .filter((item) => item.activityDefinitionId && item.activityName);
}

/** 从申请单行解析整单执行次数（各行应一致，取首个有效值） */
export function resolveServiceApplyFormExecutionCount(lines: readonly AdviceRowItem[]): number {
  for (const line of lines) {
    const count = line.listExecutionCount;
    if (typeof count === 'number' && Number.isFinite(count) && count > 0) {
      return Math.trunc(count);
    }
  }
  return 1;
}

/** 由列表诊断摘要匹配就诊诊断下拉主键 */
/** 由列表行诊断摘要/ids 串 回填多选就诊诊断 id */
export function resolveServiceApplyDiagnosisIdsFromAdviceRow(
  diagnosisSummary: string,
  encounterDiagnosisIdsText: string | undefined,
  options: ServiceApplyDiagnosisOption[],
): string[] {
  const fromIds = parseEncounterDiagnosisIds(encounterDiagnosisIdsText).filter((id) =>
    options.some((item) => item.value === id),
  );
  if (fromIds.length > 0) return fromIds;
  const label = String(diagnosisSummary ?? '').trim();
  if (!label || label === '—') {
    return [];
  }
  // 多选展示名可能为顿号拼接，逐段匹配
  const parts = label
    .split(/[、,，]/)
    .map((p) => p.trim())
    .filter(Boolean);
  const matched: string[] = [];
  for (const part of parts) {
    const hit = options.find((item) => item.label === part);
    if (hit && !matched.includes(hit.value)) {
      matched.push(hit.value);
    }
  }
  if (matched.length > 0) return matched;
  const hit = options.find((item) => item.label === label);
  return hit?.value ? [hit.value] : [];
}

/** @deprecated 改用 resolveServiceApplyDiagnosisIdsFromAdviceRow */
export function resolveServiceApplyDiagnosisIdFromAdviceRow(
  diagnosisSummary: string,
  options: ServiceApplyDiagnosisOption[],
): string {
  return (
    resolveServiceApplyDiagnosisIdsFromAdviceRow(diagnosisSummary, undefined, options)[0] ?? ''
  );
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
