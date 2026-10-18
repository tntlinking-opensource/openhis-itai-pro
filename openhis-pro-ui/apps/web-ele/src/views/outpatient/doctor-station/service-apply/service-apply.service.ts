import type { AdviceRowItem } from '../advice/services/advice.types';
import type {
  ServiceApplyDiagnosisOption,
  ServiceApplyKind,
  ServiceApplyBoundConsumable,
  ServiceApplySelectedItem,
} from './service-apply.types';

import type {
  DoctorStationAdviceCatalogRowDto,
  DoctorStationAdviceDraftBindItemDto,
  DoctorStationAdviceDraftLineRequest,
  SaveDoctorStationServiceApplyFormRequest,
  ServiceApplyFormDetailItemRequest,
  ServiceRequestDetailSummaryDto,
  TerminologyOptionDto,
} from '#/api/doctor-station/advice.types';
import type { EncounterDiagnosisRowDto } from '#/api/doctor-station/diagnosis.types';

import {
  httpInitDoctorStationAdvice,
  httpListActivityExecutionLocations,
  httpListDoctorStationAdvice,
  httpPageDoctorStationAdviceCatalog,
  httpSaveDoctorStationServiceApplyForm,
  httpSignDoctorStationAdviceBatch,
} from '#/api/doctor-station/advice.http';
import {
  httpListInpatientDoctorStationAdvice,
  httpSaveInpatientDoctorStationServiceApplyForm,
  httpSignInpatientDoctorStationAdviceBatch,
} from '#/api/inpatient/doctor-station/inpatient-doctor-station-advice.http';
import type {
  DoctorStationAdviceResourceKeyDto,
  DoctorStationAdviceRowDto,
} from '#/api/doctor-station/advice.types';
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
import {
  resolveServiceBillingQuantity,
  resolveServicePerExecutionQuantity,
} from '../advice/services/service-quantity-calc';
import { isOrderTemplateOriginRequestId } from '../advice/services/order-template-origin-cache.service';
import { unionPerformOrgOptions } from '#/views/doctor-station/advice-shared/perform-org-options.service';

import {
  ACTIVITY_DEFINITION_TABLE,
  DEVICE_DEFINITION_TABLE,
} from '../advice/services/advice-tab.constants';

import { inferServiceApplyKindFromAdviceRow } from './service-apply-doc-groups';
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

import {
  inpatientAdviceDraftBindHttpAdapter,
  outpatientAdviceDraftBindHttpAdapter,
} from '../advice/services/advice-draft-bind-http-adapter';
import {
  isBloodCollectionFeeAdviceRow,
  isBloodCollectionFeeBindItem,
  mapDraftBindItemsToDialogRows,
  persistAdviceDraftBindsWithConfirmedDialogRows,
  resolveAdviceDraftBindDialogRowsFromLines,
  saveAdviceDraftBindLinesAfterPrimaryPersisted,
} from '../advice/services/advice-draft-bind.service';
import type { AdviceDraftBindDialogRow } from '../advice/services/advice-draft-bind.types';
import {
  isAdviceRowBoundActivityFromService,
  isAdviceRowBoundConsumableFromService,
  resourceKeyFromRow,
} from '../advice/services/advice-row';
import { mergeMedicationAdviceRowsForListDisplay } from '../advice/services/advice-list';

export interface ServiceApplyInitOptions {
  bodyStructureOptions: TerminologyOptionDto[];
  specimenDefinitionOptions: TerminologyOptionDto[];
}

/**
 * 下拉选项是否命中关键字（名称/业务编码/拼音/五笔/值，不区分大小写）。
 *
 * @param option 术语或主数据 Option
 * @param keyword 用户输入；空则视为全部命中
 */
export function matchTerminologyOptionKeyword(
  option: TerminologyOptionDto,
  keyword: string,
): boolean {
  const q = String(keyword ?? '')
    .trim()
    .toLowerCase();
  if (!q) {
    return true;
  }
  // 与后端 B.2.1 检索列口径对齐：名称、编码、拼音、五笔
  const fields = [option.label, option.busNo, option.pyStr, option.wbStr, option.value];
  for (const field of fields) {
    if (field && String(field).toLowerCase().includes(q)) {
      return true;
    }
  }
  return false;
}

/**
 * ElTreeSelect filter-node-method：当前节点命中关键字则保留（子命中时由组件保留祖先）。
 *
 * @param keyword 过滤框输入
 * @param data 树节点 Option
 */
export function matchBodyStructureTreeFilterNode(
  keyword: string,
  data: TerminologyOptionDto,
): boolean {
  return matchTerminologyOptionKeyword(data, keyword);
}

/**
 * 按关键字过滤样本平面下拉（名称/编码/拼音/五笔）。
 *
 * @param options 全量样本选项
 * @param keyword 用户输入
 */
export function filterSpecimenDefinitionOptions(
  options: readonly TerminologyOptionDto[],
  keyword: string,
): TerminologyOptionDto[] {
  const q = String(keyword ?? '').trim();
  if (!q) {
    return [...options];
  }
  const matched: TerminologyOptionDto[] = [];
  for (const option of options) {
    if (matchTerminologyOptionKeyword(option, q)) {
      matched.push(option);
    }
  }
  return matched;
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

/** 目录行展示类别（目录分类，选品副标题） */
export function catalogRowCategoryLabel(row: DoctorStationAdviceCatalogRowDto): string {
  return String(row.categoryCodeDictText ?? row.categoryCode ?? '').trim() || '—';
}

/**
 * 目录行业务类型展示（申请单分类 chips）。
 *
 * @param row 开立目录行
 */
export function catalogRowActivityTypeLabel(row: DoctorStationAdviceCatalogRowDto): string {
  return String(row.activityTypeDictText ?? row.activityTypeCode ?? '').trim() || '—';
}

/**
 * 目录行业务类型码（申请单分类筛选）。
 *
 * @param row 开立目录行
 */
export function catalogRowActivityTypeCode(row: DoctorStationAdviceCatalogRowDto): string {
  return String(row.activityTypeCode ?? '').trim();
}

/** 目录行医保等级展示文案（甲类/乙类/自费等；仅展示，不参与分支） */
export function catalogRowChrgitmLvText(row: DoctorStationAdviceCatalogRowDto): string {
  return String(row.chrgitmLvText ?? '').trim();
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
  /** 业务类型码；空则不按业务类型收窄 */
  typeEnum?: string;
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
    typeEnum: params.typeEnum?.trim() || undefined,
  });
  const { content, totalCount } = parsePagedRowsFromApiData<DoctorStationAdviceCatalogRowDto>(res);
  const rows = content.filter((item) => isValidServiceApplyCatalogRow(item));
  return { rows, totalCount };
}

/** 申请单执行科室：有规则用规则，没规则由接口回落开立科室。 */
export async function loadServiceApplyOrgOptions(params?: {
  activityDefinitionId: string;
  encounterId: string;
  patientId: string;
}): Promise<ServiceApplyOrgOption[]> {
  const activityDefinitionId = String(params?.activityDefinitionId ?? '').trim();
  const encounterId = String(params?.encounterId ?? '').trim();
  const patientId = String(params?.patientId ?? '').trim();
  if (!activityDefinitionId || !encounterId || !patientId) {
    return [];
  }
  return mergeServiceApplyOrgOptions({
    activityDefinitionIds: [activityDefinitionId],
    encounterId,
    patientId,
  });
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

/** 整单执行科室：各项目规则并行取并集；都没配时接口回落开立科室。 */
export async function mergeServiceApplyOrgOptions(params?: {
  activityDefinitionIds: string[];
  encounterId: string;
  patientId: string;
}): Promise<ServiceApplyOrgOption[]> {
  const encounterId = String(params?.encounterId ?? '').trim();
  const patientId = String(params?.patientId ?? '').trim();
  const activityDefinitionIds = params?.activityDefinitionIds ?? [];
  if (!encounterId || !patientId || activityDefinitionIds.length === 0) {
    return [];
  }
  const uniqueIds = [
    ...new Set(
      activityDefinitionIds
        .map((rawId) => String(rawId ?? '').trim())
        .filter((id) => id.length > 0),
    ),
  ];
  // 并行拉各项目规则，避免整单多项目时串行 N 次
  const responses = await Promise.all(
    uniqueIds.map((activityDefinitionId) =>
      httpListActivityExecutionLocations({
        encounterId,
        patientId,
        activityDefinitionId,
      }),
    ),
  );
  return unionPerformOrgOptions(responses.map((res) => res.rules));
}

/** 解析行级执行次数：非法时回落 1（与保存口径一致） */
export function resolveServiceApplyLineExecutionCount(
  executionCount: number | undefined | null,
): number {
  const raw = Number(executionCount);
  if (Number.isFinite(raw) && raw > 0) {
    return Math.trunc(raw);
  }
  return 1;
}

/** 校验已选行是否满足保存前置条件（部位/样本均选填） */
export function validateServiceApplySelectedItems(
  items: ServiceApplySelectedItem[],
  orgId: string,
  encounterDiagnosisIds: string[],
  applyKind: ServiceApplyKind,
  options?: { requireDiagnosis?: boolean },
): null | string {
  if (items.length === 0) return '请先选择申请项目';
  if (!String(orgId ?? '').trim()) return '请选择执行科室';
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
    if (Number(item.quantity) <= 0) return `「${item.activityName}」单次执行数量须大于 0`;
    if (resolveServiceApplyLineExecutionCount(item.executionCount) <= 0) {
      return `「${item.activityName}」执行次数须大于 0`;
    }
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
  items: ServiceApplySelectedItem[];
  orgId: string;
  patientId: string;
  previousPrescriptionNo?: string;
  /** 拆多张后再保存：须传齐全部旧 AR，避免孤儿草稿 */
  previousPrescriptionNos?: string[];
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
    // 行级：账单 quantity = 单次执行数量 × 本行执行次数
    const lineExecutionCount = resolveServiceApplyLineExecutionCount(item.executionCount);
    const billingQty = resolveServiceBillingQuantity(item.quantity, lineExecutionCount);
    return {
      activityDefinitionId:
        preserveApiIdForRequest(item.activityDefinitionId) ?? item.activityDefinitionId,
      quantity: billingQty ?? item.quantity,
      unitCode: item.unitCode,
      executionCount: lineExecutionCount,
      remark: String(item.remark ?? '').trim() || undefined,
      priorityEnum: String(item.priorityEnum ?? '').trim() || '',
      ...(detailItems.length > 0 ? { detailItems } : {}),
    };
  });
  const previousPrescriptionNos = [
    ...new Set(
      (params.previousPrescriptionNos ?? [])
        .map((no) => String(no ?? '').trim())
        .filter(Boolean)
        .concat(
          params.previousPrescriptionNo?.trim()
            ? [params.previousPrescriptionNo.trim()]
            : [],
        ),
    ),
  ];
  return {
    encounterId: params.encounterId,
    patientId: params.patientId,
    categoryEnum: resolveServiceApplyCategoryEnum(params.applyKind),
    orgId: preserveApiIdForRequest(params.orgId) ?? params.orgId,
    ...(encounterDiagnosisIds ? { encounterDiagnosisIds } : {}),
    ...(previousPrescriptionNos.length > 0
      ? {
          previousPrescriptionNos,
          previousPrescriptionNo: previousPrescriptionNos[0],
        }
      : {}),
    lines,
  };
}

/** 保存申请单结果（可按执行科室拆多张 AR） */
export interface SaveServiceApplyFormResult {
  prescriptionNo: string;
  prescriptionNos: string[];
  requestIds: string[];
  /** 与 requestIds 对齐的所属单号 */
  requestPrescriptionNos: string[];
}

/** 规范化保存响应：兼容仅返回首张号的旧后端 */
export function normalizeSaveServiceApplyFormResult(res: {
  prescriptionNo?: string | null;
  prescriptionNos?: string[] | null;
  requestIds?: string[] | null;
  requestPrescriptionNos?: string[] | null;
}): SaveServiceApplyFormResult {
  const requestIds = (res.requestIds ?? []).map((id) => String(id ?? '').trim()).filter(Boolean);
  const prescriptionNosRaw = (res.prescriptionNos ?? [])
    .map((no) => String(no ?? '').trim())
    .filter(Boolean);
  const prescriptionNo =
    String(res.prescriptionNo ?? '').trim() || prescriptionNosRaw[0] || '';
  const prescriptionNos =
    prescriptionNosRaw.length > 0 ? prescriptionNosRaw : prescriptionNo ? [prescriptionNo] : [];
  let requestPrescriptionNos = (res.requestPrescriptionNos ?? [])
    .map((no) => String(no ?? '').trim())
    .filter(Boolean);
  // 旧后端无逐行单号时：全部行回落首张号（不拆单）
  if (requestPrescriptionNos.length !== requestIds.length) {
    requestPrescriptionNos = requestIds.map(() => prescriptionNo);
  }
  if (!prescriptionNo || requestIds.length === 0) {
    throw new Error('保存成功但未返回申请单号或医嘱标识');
  }
  return { prescriptionNo, prescriptionNos, requestIds, requestPrescriptionNos };
}

/** 保存申请单（可拆多张），返回 AR 号列表与对齐 requestIds */
export async function saveServiceApplyForm(
  request: SaveDoctorStationServiceApplyFormRequest,
): Promise<SaveServiceApplyFormResult> {
  const res = await httpSaveDoctorStationServiceApplyForm(request);
  return normalizeSaveServiceApplyFormResult(res);
}

/** 复制源：整张申请单的诊疗行（不含绑定耗材子行） */
export interface CopyServiceApplyFormSource {
  lines: readonly AdviceRowItem[];
  boundConsumablesByRequestId?: ReadonlyMap<string, AdviceRowItem[]>;
}

/** 按门诊/住院保存申请单，不替换旧单号（复制专用） */
async function saveCopiedServiceApplyForm(
  stationScope: 'inpatient' | 'outpatient',
  request: SaveDoctorStationServiceApplyFormRequest,
): Promise<SaveServiceApplyFormResult> {
  if (stationScope === 'inpatient') {
    const res = await httpSaveInpatientDoctorStationServiceApplyForm(request);
    return normalizeSaveServiceApplyFormResult(res);
  }
  return saveServiceApplyForm(request);
}

/**
 * 将勾选申请单按新草稿整张落库（不带旧单号）。
 * 门诊须沿用源单诊断；绑定耗材按源单数量重落，签发时不再二次解析。
 */
export async function copyServiceApplyFormsAsNewDraft(params: {
  encounterId: string;
  patientId: string;
  stationScope: 'inpatient' | 'outpatient';
  forms: readonly CopyServiceApplyFormSource[];
}): Promise<number> {
  const encounterId = params.encounterId.trim();
  const patientId = params.patientId.trim();
  if (!encounterId || !patientId) {
    throw new Error('缺少就诊或患者信息，无法复制申请单');
  }
  if (params.forms.length === 0) {
    throw new Error('未选择可复制的申请单');
  }
  const requireDiagnosis = params.stationScope !== 'inpatient';
  let copiedCount = 0;
  for (const form of params.forms) {
    const lines = form.lines.filter((line) => String(line.productDefinitionId ?? '').trim());
    if (lines.length === 0) {
      throw new Error('申请单缺少项目，无法复制');
    }
    let applyKind: ServiceApplyKind | null = null;
    for (const line of lines) {
      applyKind = inferServiceApplyKindFromAdviceRow(line);
      if (applyKind) {
        break;
      }
    }
    if (!applyKind) {
      throw new Error('无法识别申请单类型，无法复制');
    }
    const orgId = String(lines[0]?.performOrgId ?? '').trim();
    const diagnosisIds = requireDiagnosis
      ? parseEncounterDiagnosisIds(lines[0]?.encounterDiagnosisIds)
      : [];
    const items = buildServiceApplySelectedItemsFromAdviceRows(
      lines,
      applyKind,
      form.boundConsumablesByRequestId,
    );
    const invalid = validateServiceApplySelectedItems(items, orgId, diagnosisIds, applyKind, {
      requireDiagnosis,
    });
    if (invalid) {
      throw new Error(invalid);
    }
    const request = buildSaveServiceApplyFormRequest({
      encounterId,
      patientId,
      applyKind,
      orgId,
      encounterDiagnosisIds: diagnosisIds,
      encounterDiagnosisRows: [],
      items,
    });
    const saved = await saveCopiedServiceApplyForm(params.stationScope, request);
    await persistServiceApplyDraftBindsAfterSign({
      encounterId,
      patientId,
      items,
      requestIds: saved.requestIds,
      stationScope: params.stationScope,
      encounterDiagnosisIds: diagnosisIds.join(','),
    });
    copiedCount += 1;
  }
  return copiedCount;
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

/** 列表耗材行 → 申请单卡内绑定耗材 */
export function mapAdviceRowToServiceApplyBoundConsumable(
  row: AdviceRowItem,
): ServiceApplyBoundConsumable {
  const targetItemId = String(row.productDefinitionId ?? '').trim();
  const qtyRaw = Number(row.listQuantity ?? 1);
  const quantity = Number.isFinite(qtyRaw) && qtyRaw > 0 ? qtyRaw : 1;
  return {
    key: targetItemId || row.key,
    targetItemId,
    targetItemName: String(row.content ?? '').trim() || targetItemId,
    quantity,
    unitCode: String(row.listUnitCode ?? '').trim() || 'U',
    unitName: String(row.listUnitName ?? '').trim() || undefined,
  };
}

/** 列表行 → 编辑抽屉已选项目（回显用） */
export function buildServiceApplySelectedItemsFromAdviceRows(
  lines: readonly AdviceRowItem[],
  applyKind: ServiceApplyKind,
  boundConsumablesByRequestId?: ReadonlyMap<string, AdviceRowItem[]>,
  /** @deprecated 行级已用 listExecutionCount；保留参数仅兼容旧调用 */
  formExecutionCount?: number,
): ServiceApplySelectedItem[] {
  const execFallback =
    typeof formExecutionCount === 'number' &&
    Number.isFinite(formExecutionCount) &&
    formExecutionCount > 0
      ? Math.trunc(formExecutionCount)
      : undefined;
  return lines
    .map((line) => {
      const activityDefinitionId = String(line.productDefinitionId ?? '').trim();
      const activityName = String(line.content ?? '').trim();
      // 落库 listQuantity 为计费总量；卡内回填单次执行数量与本行执行次数
      const totalQtyRaw = line.listQuantity ?? 1;
      const totalQty = totalQtyRaw > 0 ? totalQtyRaw : 1;
      const execCount =
        (typeof line.listExecutionCount === 'number' &&
        Number.isFinite(line.listExecutionCount) &&
        line.listExecutionCount > 0
          ? Math.trunc(line.listExecutionCount)
          : undefined) ??
        execFallback ??
        1;
      const perQty = resolveServicePerExecutionQuantity(totalQty, execCount) ?? totalQty;
      // 单价仍按落库总量反推，避免展示金额漂移
      const lineAmount = parseAdviceLineAmountYuan(line.lineTotalAmount);
      const unitPrice = totalQty > 0 ? lineAmount / totalQty : 0;
      const catalogRowKey = `${SERVICE_APPLY_ACTIVITY_TABLE}:${activityDefinitionId}`;
      const requestIdRaw = String(line.requestId ?? '').trim();
      const requestId = preserveApiIdForRequest(requestIdRaw) ?? requestIdRaw;
      const boundRows = requestId ? (boundConsumablesByRequestId?.get(requestId) ?? []) : [];
      const boundConsumables = boundRows.map(mapAdviceRowToServiceApplyBoundConsumable);
      return {
        rowKey: `${catalogRowKey}-${line.key}`,
        catalogRowKey,
        activityDefinitionId,
        activityName,
        quantity: perQty,
        executionCount: execCount,
        unitCode: String(line.listUnitCode ?? '').trim(),
        unitPrice,
        chrgitmLvText: String(line.chrgitmLvText ?? '').trim() || undefined,
        remark: String(line.remark ?? '').trim() || undefined,
        priorityEnum: String(line.priorityEnum ?? '').trim() || undefined,
        detailItems: mapServiceRequestDetailsToDetailItems(line.serviceRequestDetails, applyKind),
        boundConsumables,
        boundConsumablesLoaded: boundRows.length > 0,
        packageChildren: line.packageChildren,
      };
    })
    .filter((item) => item.activityDefinitionId && item.activityName);
}

/** 从申请单行解析执行次数回落值（取首个有效行；兼容旧「整单共用」口径） */
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

/** 拉取就诊下已有医嘱行（绑定去重用） */
async function loadAdviceRowsForBindDedupe(params: {
  encounterId: string;
  patientId: string;
  stationScope: 'inpatient' | 'outpatient';
}): Promise<DoctorStationAdviceRowDto[]> {
  const listRes =
    params.stationScope === 'inpatient'
      ? await httpListInpatientDoctorStationAdvice({
          encounterId: params.encounterId,
          patientId: params.patientId,
        })
      : await httpListDoctorStationAdvice({
          encounterId: params.encounterId,
          patientId: params.patientId,
          onlyMine: false,
        });
  const { content: list } = parsePagedRowsFromApiData<DoctorStationAdviceRowDto>(listRes);
  return list;
}

/** 拉取就诊全量医嘱行（含绑定耗材），供签发/签退聚目标 */
export async function loadServiceApplyAdviceRowItems(params: {
  encounterId: string;
  patientId: string;
  stationScope: 'inpatient' | 'outpatient';
}): Promise<AdviceRowItem[]> {
  const list = await loadAdviceRowsForBindDedupe(params);
  return mergeMedicationAdviceRowsForListDisplay(list);
}

function normalizeServiceRequestIdForBind(value: unknown): string {
  const preserved = preserveApiIdForRequest(value);
  if (preserved) {
    return preserved;
  }
  return String(value ?? '').trim();
}

/** 诊疗 requestId → 绑定耗材签发目标 */
export function resolveBoundConsumableSignTargetsForServiceRequestIds(
  serviceRequestIds: readonly string[],
  allRows: readonly AdviceRowItem[],
): DoctorStationAdviceResourceKeyDto[] {
  const serviceIdSet = new Set(
    serviceRequestIds.map((id) => normalizeServiceRequestIdForBind(id)).filter(Boolean),
  );
  if (serviceIdSet.size === 0) {
    return [];
  }
  const targets: DoctorStationAdviceResourceKeyDto[] = [];
  for (const row of allRows) {
    if (!isAdviceRowBoundConsumableFromService(row)) {
      continue;
    }
    const anchorId = normalizeServiceRequestIdForBind(row.basedOnServiceRequestId);
    if (!anchorId || !serviceIdSet.has(anchorId)) {
      continue;
    }
    const key = resourceKeyFromRow(row);
    if (key) {
      targets.push(key);
    }
  }
  return targets;
}

/** 诊疗 requestId → 合管采血费等绑定诊疗签发目标（含不挂 based_on 的独立采血费） */
export function resolveBoundActivitySignTargetsForServiceRequestIds(
  serviceRequestIds: readonly string[],
  allRows: readonly AdviceRowItem[],
): DoctorStationAdviceResourceKeyDto[] {
  const serviceIdSet = new Set(
    serviceRequestIds.map((id) => normalizeServiceRequestIdForBind(id)).filter(Boolean),
  );
  if (serviceIdSet.size === 0) {
    return [];
  }
  const targets: DoctorStationAdviceResourceKeyDto[] = [];
  const seen = new Set<string>();
  const pushKey = (key: DoctorStationAdviceResourceKeyDto | undefined) => {
    if (!key) {
      return;
    }
    const rid = normalizeServiceRequestIdForBind(key.requestId);
    const pt = String(key.productTableName ?? '').trim();
    if (!rid || !pt) {
      return;
    }
    const dedupeKey = `${pt}:${rid}`;
    if (seen.has(dedupeKey)) {
      return;
    }
    seen.add(dedupeKey);
    targets.push(key);
  };
  // 旧数据：仍挂 based_on 的派生诊疗
  for (const row of allRows) {
    if (!isAdviceRowBoundActivityFromService(row)) {
      continue;
    }
    const anchorId = normalizeServiceRequestIdForBind(row.basedOnServiceRequestId);
    if (!anchorId || !serviceIdSet.has(anchorId)) {
      continue;
    }
    pushKey(resourceKeyFromRow(row));
  }
  // 新口径：独立采血费（不挂 based_on）随本批检验申请一并签发
  for (const row of allRows) {
    if (!isBloodCollectionFeeAdviceRow(row)) {
      continue;
    }
    if (!rowLooksDraftForServiceApplySign(row)) {
      continue;
    }
    pushKey(resourceKeyFromRow(row));
  }
  return targets;
}

/** 申请单签发用：行是否仍为待签草稿 */
function rowLooksDraftForServiceApplySign(row: AdviceRowItem): boolean {
  const code = String(row.statusEnum ?? '')
    .trim()
    .toUpperCase();
  return code === 'DRAFT' || code === '';
}

/** 申请单诊疗 + 绑定耗材/采血费合并签发目标（去重） */
export function buildServiceApplySignTargetsWithBinds(
  serviceRequestIds: readonly string[],
  allRows: readonly AdviceRowItem[],
): DoctorStationAdviceResourceKeyDto[] {
  const dedupe = new Map<string, DoctorStationAdviceResourceKeyDto>();
  const addTarget = (target: DoctorStationAdviceResourceKeyDto) => {
    const requestId = normalizeServiceRequestIdForBind(target.requestId);
    const productTableName = String(target.productTableName ?? '').trim();
    if (!requestId || !productTableName) {
      return;
    }
    dedupe.set(`${productTableName}:${requestId}`, {
      requestId,
      productTableName,
    });
  };
  for (const requestId of serviceRequestIds) {
    const normalized = normalizeServiceRequestIdForBind(requestId);
    if (!normalized) {
      continue;
    }
    addTarget({
      requestId: normalized,
      productTableName: SERVICE_APPLY_SIGN_PRODUCT_TABLE,
    });
  }
  for (const target of resolveBoundConsumableSignTargetsForServiceRequestIds(
    serviceRequestIds,
    allRows,
  )) {
    addTarget(target);
  }
  // 合管采血费等诊疗派生须与检验一并签发，否则草稿滞留且不进收费
  for (const target of resolveBoundActivitySignTargetsForServiceRequestIds(
    serviceRequestIds,
    allRows,
  )) {
    addTarget(target);
  }
  return [...dedupe.values()];
}

/** 申请单诊疗行 + 绑定耗材合并资源键（签退等批量操作） */
export function expandServiceApplyResourceTargetsWithBinds(
  serviceRows: readonly AdviceRowItem[],
  allRows: readonly AdviceRowItem[],
): DoctorStationAdviceResourceKeyDto[] {
  const serviceRequestIds = serviceRows
    .map((row) => normalizeServiceRequestIdForBind(row.requestId))
    .filter(Boolean);
  return buildServiceApplySignTargetsWithBinds(serviceRequestIds, allRows);
}

/** 申请单诊疗行关联绑定耗材/采血费 DELETE 行 */
export function buildServiceApplyBoundConsumableDeleteLines(
  serviceRows: readonly AdviceRowItem[],
  allRows: readonly AdviceRowItem[],
): DoctorStationAdviceDraftLineRequest[] {
  const serviceRequestIds = serviceRows
    .map((row) => normalizeServiceRequestIdForBind(row.requestId))
    .filter(Boolean);
  const deviceTargets = resolveBoundConsumableSignTargetsForServiceRequestIds(
    serviceRequestIds,
    allRows,
  );
  const activityTargets = resolveBoundActivitySignTargetsForServiceRequestIds(
    serviceRequestIds,
    allRows,
  );
  return [...deviceTargets, ...activityTargets].map((target) => ({
    lineOp: 'DELETE',
    requestId: target.requestId,
    productTableName: target.productTableName,
  }));
}

async function signDoctorStationAdviceBatchTargets(params: {
  encounterId: string;
  patientId: string;
  stationScope: 'inpatient' | 'outpatient';
  signTargets: DoctorStationAdviceResourceKeyDto[];
}): Promise<void> {
  if (params.signTargets.length === 0) {
    throw new Error('没有可签发的申请单');
  }
  const signHttp =
    params.stationScope === 'inpatient'
      ? httpSignInpatientDoctorStationAdviceBatch
      : httpSignDoctorStationAdviceBatch;
  const res = await signHttp({
    encounterId: params.encounterId,
    patientId: params.patientId,
    signTargets: params.signTargets,
  });
  if (res.success === false) {
    throw new Error(String(res.message ?? '').trim() || '签发失败');
  }
}

/** 组装单条诊疗 CREATE 触发行（IF-DOR-23 解析绑定用） */
function buildServiceApplyBindPreviewLine(
  item: Pick<ServiceApplySelectedItem, 'activityDefinitionId' | 'quantity' | 'unitCode'>,
): DoctorStationAdviceDraftLineRequest {
  const activityDefinitionId =
    preserveApiIdForRequest(item.activityDefinitionId) ?? item.activityDefinitionId;
  const unitCode = String(item.unitCode ?? '').trim() || 'U';
  const qtyRaw = Number(item.quantity ?? 1);
  const quantity = Number.isFinite(qtyRaw) && qtyRaw > 0 ? qtyRaw : 1;
  return {
    lineOp: 'CREATE',
    productTableName: SERVICE_APPLY_ACTIVITY_TABLE,
    servicePayload: {
      activityDefinitionId,
      quantity,
      unitCode,
    },
  };
}

/** 弹窗行 → 申请单卡内带出行（耗材可改量；合管采血费只读） */
function mapDialogRowToBoundConsumable(row: AdviceDraftBindDialogRow): ServiceApplyBoundConsumable {
  const targetItemId = String(row.bindItem.targetItemId ?? '').trim();
  const targetProductTableName = String(row.bindItem.targetProductTableName ?? '').trim();
  const isActivity = targetProductTableName === ACTIVITY_DEFINITION_TABLE;
  return {
    key: String(row.key ?? targetItemId).trim() || targetItemId,
    targetItemId,
    targetItemName: String(row.targetItemName ?? targetItemId).trim(),
    quantity: row.quantity,
    unitCode: String(row.unitCode ?? '').trim() || 'U',
    unitName: String(row.unitName ?? '').trim() || undefined,
    targetProductTableName: targetProductTableName || undefined,
    // 合管采血费等诊疗派生：卡内只展示，不可删改
    readOnly: isActivity,
  };
}

/** 选中诊疗后加载模板/合管派生绑定（耗材 + 采血费） */
export async function loadServiceApplyBoundConsumables(params: {
  encounterId: string;
  patientId: string;
  item: Pick<ServiceApplySelectedItem, 'activityDefinitionId' | 'quantity' | 'unitCode'>;
  stationScope: 'inpatient' | 'outpatient';
}): Promise<ServiceApplyBoundConsumable[]> {
  const apiAdapter =
    params.stationScope === 'inpatient'
      ? inpatientAdviceDraftBindHttpAdapter
      : outpatientAdviceDraftBindHttpAdapter;
  const lines = [buildServiceApplyBindPreviewLine(params.item)];
  const dialogRows = await resolveAdviceDraftBindDialogRowsFromLines({
    encounterId: params.encounterId,
    patientId: params.patientId,
    lines,
    apiAdapter,
  });
  // 卡内展示耗材与合管采血费；无库存耗材在签发绑定确认弹窗中删除
  return dialogRows
    .filter((row) => {
      const table = String(row.bindItem.targetProductTableName ?? '').trim();
      return table === DEVICE_DEFINITION_TABLE || table === ACTIVITY_DEFINITION_TABLE;
    })
    .map(mapDialogRowToBoundConsumable);
}

/** 批量为已选清单行补齐带出耗材（采血费跨行只展示一份） */
export async function enrichServiceApplySelectedItemsBoundConsumables(params: {
  encounterId: string;
  patientId: string;
  items: ServiceApplySelectedItem[];
  stationScope: 'inpatient' | 'outpatient';
}): Promise<ServiceApplySelectedItem[]> {
  const next = [...params.items];
  // 卡内采血费只挂在首次命中的行上，避免跨组/跨设备多份展示
  let bloodFeeAttached = next.some((item) =>
    (item.boundConsumables ?? []).some((c) =>
      isBloodCollectionFeeBindItem({
        targetItemName: c.targetItemName,
        targetProductTableName: c.targetProductTableName,
      }),
    ),
  );
  for (let i = 0; i < next.length; i += 1) {
    const item = next[i];
    if (!item || item.boundConsumablesLoaded) {
      continue;
    }
    try {
      let boundConsumables = await loadServiceApplyBoundConsumables({
        encounterId: params.encounterId,
        patientId: params.patientId,
        item,
        stationScope: params.stationScope,
      });
      if (bloodFeeAttached) {
        boundConsumables = boundConsumables.filter(
          (c) =>
            !isBloodCollectionFeeBindItem({
              targetItemName: c.targetItemName,
              targetProductTableName: c.targetProductTableName,
            }),
        );
      } else {
        const hasBloodFee = boundConsumables.some((c) =>
          isBloodCollectionFeeBindItem({
            targetItemName: c.targetItemName,
            targetProductTableName: c.targetProductTableName,
          }),
        );
        if (hasBloodFee) {
          bloodFeeAttached = true;
          let kept = false;
          boundConsumables = boundConsumables.filter((c) => {
            if (
              !isBloodCollectionFeeBindItem({
                targetItemName: c.targetItemName,
                targetProductTableName: c.targetProductTableName,
              })
            ) {
              return true;
            }
            if (kept) {
              return false;
            }
            kept = true;
            return true;
          });
        }
      }
      next[i] = {
        ...item,
        boundConsumables,
        boundConsumablesLoaded: true,
      };
    } catch {
      next[i] = {
        ...item,
        boundConsumables: [],
        boundConsumablesLoaded: true,
      };
    }
  }
  return next;
}

/**
 * 卡内已编辑耗材数量 + 合管派生采血费 → 签发落库用确认绑定行。
 * 卡内只编辑耗材；采血费（wkf_activity_definition）须一并落库，不可被 filterDevice 滤掉。
 */
function buildServiceApplyConfirmedDeviceBindDialogRows(
  items: readonly ServiceApplySelectedItem[],
  bindLines: DoctorStationAdviceDraftLineRequest[],
  bindItems: DoctorStationAdviceDraftBindItemDto[],
): AdviceDraftBindDialogRow[] {
  const allDialogRows = mapDraftBindItemsToDialogRows(bindItems, bindLines);
  // 活动定义 id → 用户保留的耗材 targetId 集合
  const includedTargetsByActivity = new Map<string, Set<string>>();
  const quantityByActivityTarget = new Map<string, number>();
  for (const item of items) {
    const activityId = String(item.activityDefinitionId ?? '').trim();
    if (!activityId || !item.boundConsumablesLoaded) {
      continue;
    }
    const targetSet = new Set<string>();
    for (const consumable of item.boundConsumables ?? []) {
      // 卡内勾选只约束耗材；采血费只读不参与过滤
      if (consumable.readOnly) {
        continue;
      }
      const targetTable = String(consumable.targetProductTableName ?? '').trim();
      if (targetTable === ACTIVITY_DEFINITION_TABLE) {
        continue;
      }
      const targetId = String(consumable.targetItemId ?? '').trim();
      if (!targetId) {
        continue;
      }
      targetSet.add(targetId);
      const qtyRaw = Number(consumable.quantity ?? 1);
      const quantity = Number.isFinite(qtyRaw) && qtyRaw > 0 ? qtyRaw : 1;
      quantityByActivityTarget.set(`${activityId}|${targetId}`, quantity);
    }
    includedTargetsByActivity.set(activityId, targetSet);
  }

  const confirmed: AdviceDraftBindDialogRow[] = [];
  for (const row of allDialogRows) {
    const targetTable = String(row.bindItem.targetProductTableName ?? '').trim();
    const targetId = String(row.bindItem.targetItemId ?? '').trim();
    if (!targetId) {
      continue;
    }
    // 合管采血费等诊疗派生：不经卡内编辑，原样落库
    if (targetTable === ACTIVITY_DEFINITION_TABLE) {
      confirmed.push(row);
      continue;
    }
    // 耗材：仅 adm_device_definition，并按卡内勾选/数量覆盖
    if (targetTable !== DEVICE_DEFINITION_TABLE) {
      continue;
    }
    const triggerIndex = row.bindItem.triggerLineIndex;
    const triggerLine =
      triggerIndex === undefined || triggerIndex === null ? undefined : bindLines[triggerIndex];
    const activityId = String(
      triggerLine?.servicePayload?.activityDefinitionId ?? row.bindItem.triggerDefinitionId ?? '',
    ).trim();
    const includedSet = includedTargetsByActivity.get(activityId);
    if (includedSet && !includedSet.has(targetId)) {
      continue;
    }
    const overrideQty = quantityByActivityTarget.get(`${activityId}|${targetId}`);
    confirmed.push({
      ...row,
      quantity: overrideQty ?? row.quantity,
    });
  }
  return confirmed;
}

/** 申请单保存后：按卡内耗材数量落库绑定行（不弹绑定确认框） */
export async function persistServiceApplyDraftBindsAfterSign(params: {
  encounterId: string;
  patientId: string;
  items: readonly ServiceApplySelectedItem[];
  requestIds: readonly string[];
  stationScope: 'inpatient' | 'outpatient';
  existingAdviceRows?: AdviceRowItem[] | DoctorStationAdviceRowDto[];
  /** 整张申请单诊断 ids 串；采血费派生行沿用 */
  encounterDiagnosisIds?: string;
}): Promise<void> {
  const bindLines = buildServiceApplyBindTriggerLines(params.items, params.requestIds, {
    encounterDiagnosisIds: params.encounterDiagnosisIds,
  });
  if (bindLines.length === 0) {
    return;
  }
  const apiAdapter =
    params.stationScope === 'inpatient'
      ? inpatientAdviceDraftBindHttpAdapter
      : outpatientAdviceDraftBindHttpAdapter;
  let existingAdviceRows = params.existingAdviceRows;
  if (!existingAdviceRows) {
    existingAdviceRows = await loadAdviceRowsForBindDedupe({
      encounterId: params.encounterId,
      patientId: params.patientId,
      stationScope: params.stationScope,
    });
  }
  const resolveRes = await apiAdapter.resolveDraftBinds({
    encounterId: params.encounterId,
    patientId: params.patientId,
    lines: bindLines,
  });
  const confirmedRows = buildServiceApplyConfirmedDeviceBindDialogRows(
    params.items,
    bindLines,
    resolveRes.bindItems ?? [],
  );
  if (confirmedRows.length === 0) {
    return;
  }
  const res = await persistAdviceDraftBindsWithConfirmedDialogRows(
    {
      encounterId: params.encounterId,
      patientId: params.patientId,
      lines: bindLines,
      existingAdviceRows,
      apiAdapter,
      primaryLinesAlreadyPersisted: true,
    },
    confirmedRows,
  );
  if (res.success === false) {
    throw new Error(String(res.message ?? '').trim() || '绑定带出失败');
  }
}

/** 签发前弹窗确认绑定并落库（门诊）；住院适配器恒空 */
export async function resolveServiceApplyDraftBindsBeforeSign(params: {
  encounterId: string;
  patientId: string;
  lines: DoctorStationAdviceDraftLineRequest[];
  stationScope: 'inpatient' | 'outpatient';
  existingAdviceRows?: AdviceRowItem[] | DoctorStationAdviceRowDto[];
}): Promise<void> {
  const apiAdapter =
    params.stationScope === 'inpatient'
      ? inpatientAdviceDraftBindHttpAdapter
      : outpatientAdviceDraftBindHttpAdapter;
  let existingAdviceRows = params.existingAdviceRows;
  if (!existingAdviceRows) {
    existingAdviceRows = await loadAdviceRowsForBindDedupe({
      encounterId: params.encounterId,
      patientId: params.patientId,
      stationScope: params.stationScope,
    });
  }
  const res = await saveAdviceDraftBindLinesAfterPrimaryPersisted({
    encounterId: params.encounterId,
    patientId: params.patientId,
    lines: params.lines,
    existingAdviceRows,
    apiAdapter,
  });
  if (res.success === false) {
    throw new Error(String(res.message ?? '').trim() || '绑定带出失败');
  }
}

/** 申请单已选项目 + 保存返回 requestId → IF-DOR-23 触发行 */
export function buildServiceApplyBindTriggerLines(
  items: readonly ServiceApplySelectedItem[],
  requestIds: readonly string[],
  options?: { encounterDiagnosisIds?: string; encounterId?: string },
): DoctorStationAdviceDraftLineRequest[] {
  const lines: DoctorStationAdviceDraftLineRequest[] = [];
  const encounterId = String(options?.encounterId ?? '').trim();
  const encounterDiagnosisIds = String(options?.encounterDiagnosisIds ?? '').trim();
  for (let i = 0; i < items.length; i += 1) {
    const item = items[i];
    const requestId = preserveApiIdForRequest(requestIds[i]);
    if (!item || !requestId) {
      continue;
    }
    // 申请单组套套用行：签发不再解析绑定/合管配置
    if (item.fromOrderTemplate) {
      continue;
    }
    if (encounterId && isOrderTemplateOriginRequestId(encounterId, requestId)) {
      continue;
    }
    const activityDefinitionId =
      preserveApiIdForRequest(item.activityDefinitionId) ?? item.activityDefinitionId;
    if (!activityDefinitionId) {
      continue;
    }
    lines.push({
      lineOp: 'CREATE',
      productTableName: SERVICE_APPLY_ACTIVITY_TABLE,
      requestId,
      servicePayload: {
        activityDefinitionId,
        quantity: item.quantity,
        unitCode: item.unitCode,
        ...(encounterDiagnosisIds ? { encounterDiagnosisIds } : {}),
      },
    });
  }
  return lines;
}

/** 列表/弹窗已落库草稿行 → IF-DOR-23 / IF-IP-DOR-23 触发行 */
export function buildServiceApplyBindTriggerLinesFromAdviceRows(
  adviceRows: readonly AdviceRowItem[],
  options?: { encounterId?: string },
): DoctorStationAdviceDraftLineRequest[] {
  const lines: DoctorStationAdviceDraftLineRequest[] = [];
  const encounterId = String(options?.encounterId ?? '').trim();
  for (const row of adviceRows) {
    const requestId = preserveApiIdForRequest(row.requestId);
    const activityDefinitionId =
      preserveApiIdForRequest(row.productDefinitionId) ?? row.productDefinitionId;
    if (!requestId || !activityDefinitionId) {
      continue;
    }
    // 组套套用落库行：签发跳过绑定/合管自动带出
    if (encounterId && isOrderTemplateOriginRequestId(encounterId, requestId)) {
      continue;
    }
    const qtyRaw = Number(row.listQuantity ?? 1);
    const quantity = Number.isFinite(qtyRaw) && qtyRaw > 0 ? qtyRaw : 1;
    const unitCode = String(row.listUnitCode ?? '').trim() || 'U';
    const encounterDiagnosisIds = String(row.encounterDiagnosisIds ?? '').trim();
    lines.push({
      lineOp: 'CREATE',
      productTableName: SERVICE_APPLY_ACTIVITY_TABLE,
      requestId,
      servicePayload: {
        activityDefinitionId,
        quantity,
        unitCode,
        ...(encounterDiagnosisIds ? { encounterDiagnosisIds } : {}),
      },
    });
  }
  return lines;
}

/** 草稿申请单行签发前先弹窗确认绑定（可删无库存耗材），再 sign */
export async function signServiceApplyDraftAdviceRowsWithBind(params: {
  encounterId: string;
  patientId: string;
  draftRows: readonly AdviceRowItem[];
  stationScope: 'inpatient' | 'outpatient';
  existingAdviceRows?: AdviceRowItem[] | DoctorStationAdviceRowDto[];
}): Promise<void> {
  const bindLines = buildServiceApplyBindTriggerLinesFromAdviceRows(params.draftRows, {
    encounterId: params.encounterId,
  });
  if (bindLines.length > 0) {
    await resolveServiceApplyDraftBindsBeforeSign({
      encounterId: params.encounterId,
      patientId: params.patientId,
      lines: bindLines,
      stationScope: params.stationScope,
      existingAdviceRows: params.existingAdviceRows,
    });
  }
  const allRowItems = await loadServiceApplyAdviceRowItems({
    encounterId: params.encounterId,
    patientId: params.patientId,
    stationScope: params.stationScope,
  });
  const serviceRequestIds = params.draftRows
    .map((row) => normalizeServiceRequestIdForBind(row.requestId))
    .filter(Boolean);
  const signTargets = buildServiceApplySignTargetsWithBinds(serviceRequestIds, allRowItems);
  await signDoctorStationAdviceBatchTargets({
    encounterId: params.encounterId,
    patientId: params.patientId,
    stationScope: params.stationScope,
    signTargets,
  });
}

/** 签发申请单下全部 ServiceRequest 草稿（弹窗保存后 requestIds） */
export async function signServiceApplyRequests(params: {
  encounterId: string;
  patientId: string;
  requestIds: string[];
  stationScope?: 'inpatient' | 'outpatient';
}): Promise<void> {
  const stationScope = params.stationScope ?? 'outpatient';
  const allRowItems = await loadServiceApplyAdviceRowItems({
    encounterId: params.encounterId,
    patientId: params.patientId,
    stationScope,
  });
  const signTargets = buildServiceApplySignTargetsWithBinds(params.requestIds, allRowItems);
  await signDoctorStationAdviceBatchTargets({
    encounterId: params.encounterId,
    patientId: params.patientId,
    stationScope,
    signTargets,
  });
}
