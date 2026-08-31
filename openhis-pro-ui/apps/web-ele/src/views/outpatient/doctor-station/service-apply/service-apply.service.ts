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
  httpListDoctorStationAdvice,
  httpPageDoctorStationAdviceCatalog,
  httpSaveDoctorStationServiceApplyForm,
  httpSignDoctorStationAdviceBatch,
} from '#/api/doctor-station/advice.http';
import {
  httpListInpatientDoctorStationAdvice,
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
import { decimalAdd } from '#/utils/format/decimal-util';
import {
  resolveServiceBillingQuantity,
  resolveServicePerExecutionQuantity,
} from '../advice/services/service-quantity-calc';
import {
  convertAdviceCatalogSplitQtyToPackageDisplay,
  formatAdviceCatalogStockCellText,
  isAdviceCatalogZeroStock,
  resolveAdviceCatalogPackageDisplayQty,
  sumAdviceCatalogInventorySplitQty,
} from '#/utils/doctor-station/advice-catalog-stock-display';
import { unitCodeByBandKind } from '#/utils/doctor-station/medication-unit-band-query';
import {
  convertInboundQtyBetweenBandKinds,
  normalizeInboundProductUnitBands,
} from '#/utils/inventory/inbound-product-unit-bands';
import { loadAllDepartmentPerformOrgOptions } from '#/views/doctor-station/advice-shared/perform-org-options.service';

import type { PrepickedProductKeyDto } from '#/api/doctor-station/advice.types';
import { httpPageInpatientDoctorStationAdviceCatalog } from '#/api/inpatient/doctor-station/inpatient-doctor-station-advice.http';
import { fetchAdviceCatalogRowsByPrepickedKeys } from '../advice/services/advice-catalog-prepicked';
import { mapCatalogRowToOption } from '../advice/services/advice-list';
import type { AdviceProductOption } from '../advice/services/advice.types';
import {
  ADVICE_PRICING_SOURCE_CODE,
  DEVICE_DEFINITION_TABLE,
} from '../advice/services/advice-tab.constants';

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
  filterDeviceBindDialogRows,
  mapDraftBindItemsToDialogRows,
  persistAdviceDraftBindsWithConfirmedDialogRows,
  resolveAdviceDraftBindDialogRowsFromLines,
  saveAdviceDraftBindLinesAfterPrimaryPersisted,
} from '../advice/services/advice-draft-bind.service';
import type { AdviceDraftBindDialogRow } from '../advice/services/advice-draft-bind.types';
import {
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

/** 目录行展示类别 */
export function catalogRowCategoryLabel(row: DoctorStationAdviceCatalogRowDto): string {
  return String(row.categoryCodeDictText ?? row.categoryCode ?? '').trim() || '—';
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
    if (Number(item.quantity) <= 0) return `「${item.activityName}」单次执行数量须大于 0`;
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
  const executionCountRaw = Number(params.executionCount);
  // 整单执行次数：非法时回落 1，与行级单次量相乘得账单 quantity
  const executionCount =
    Number.isFinite(executionCountRaw) && executionCountRaw > 0 ? Math.trunc(executionCountRaw) : 1;
  const lines = params.items.map((item) => {
    // 检验/治疗可无明细：空明细不传 detailItems
    const detailItems = (item.detailItems ?? [])
      .map((detail) => ({
        targetTable: detail.targetTable,
        targetId: preserveApiIdForRequest(detail.targetId) ?? detail.targetId,
        remark: String(detail.remark ?? '').trim() || undefined,
      }))
      .filter((detail) => String(detail.targetId ?? '').trim());
    // 行 quantity 为单次执行数量；落库账单 quantity = 单次 × 整单执行次数
    const billingQty = resolveServiceBillingQuantity(item.quantity, executionCount);
    return {
      activityDefinitionId:
        preserveApiIdForRequest(item.activityDefinitionId) ?? item.activityDefinitionId,
      quantity: billingQty ?? item.quantity,
      unitCode: item.unitCode,
      remark: String(item.remark ?? '').trim() || undefined,
      ...(detailItems.length > 0 ? { detailItems } : {}),
    };
  });
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
  /** 整单执行次数；用于把落库总量拆回单次执行数量 */
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
      // 落库 listQuantity 为计费总量；卡内回填单次执行数量
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
        unitCode: String(line.listUnitCode ?? '').trim(),
        unitPrice,
        chrgitmLvText: String(line.chrgitmLvText ?? '').trim() || undefined,
        remark: String(line.remark ?? '').trim() || undefined,
        detailItems: mapServiceRequestDetailsToDetailItems(line.serviceRequestDetails, applyKind),
        boundConsumables,
        boundConsumablesLoaded: boundRows.length > 0,
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

/** 申请单诊疗 + 绑定耗材合并签发目标（去重） */
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

/** 申请单诊疗行关联绑定耗材 DELETE 行 */
export function buildServiceApplyBoundConsumableDeleteLines(
  serviceRows: readonly AdviceRowItem[],
  allRows: readonly AdviceRowItem[],
): DoctorStationAdviceDraftLineRequest[] {
  const serviceRequestIds = serviceRows
    .map((row) => normalizeServiceRequestIdForBind(row.requestId))
    .filter(Boolean);
  return resolveBoundConsumableSignTargetsForServiceRequestIds(serviceRequestIds, allRows).map(
    (target) => ({
      lineOp: 'DELETE',
      requestId: target.requestId,
      productTableName: target.productTableName,
    }),
  );
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

/** 弹窗行 → 申请单卡内耗材展示行 */
function mapDialogRowToBoundConsumable(row: AdviceDraftBindDialogRow): ServiceApplyBoundConsumable {
  const targetItemId = String(row.bindItem.targetItemId ?? '').trim();
  return {
    key: String(row.key ?? targetItemId).trim() || targetItemId,
    targetItemId,
    targetItemName: String(row.targetItemName ?? targetItemId).trim(),
    quantity: row.quantity,
    unitCode: String(row.unitCode ?? '').trim() || 'U',
    unitName: String(row.unitName ?? '').trim() || undefined,
  };
}

/** 选中诊疗后加载模板绑定的带出耗材（仅耗材，不含诊疗带出） */
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
  return filterDeviceBindDialogRows(dialogRows).map(mapDialogRowToBoundConsumable);
}

/** 批量为已选清单行补齐带出耗材 */
export async function enrichServiceApplySelectedItemsBoundConsumables(params: {
  encounterId: string;
  patientId: string;
  items: ServiceApplySelectedItem[];
  stationScope: 'inpatient' | 'outpatient';
}): Promise<ServiceApplySelectedItem[]> {
  const next = [...params.items];
  for (let i = 0; i < next.length; i += 1) {
    const item = next[i];
    if (!item || item.boundConsumablesLoaded) {
      continue;
    }
    try {
      const boundConsumables = await loadServiceApplyBoundConsumables({
        encounterId: params.encounterId,
        patientId: params.patientId,
        item,
        stationScope: params.stationScope,
      });
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

interface ServiceApplyBoundConsumableStockLine {
  targetItemId: string;
  targetItemName: string;
  quantity: number;
  unitCode: string;
  unitName?: string;
}

/** 单位 code → 计量带 kind（库存换算用） */
function findBandKindByUnitCode(
  unitCode: string,
  bands: AdviceProductOption['unitBands'],
): string | undefined {
  const code = String(unitCode ?? '').trim();
  if (!code) {
    return undefined;
  }
  const hit = (bands ?? []).find((band) => String(band.unitCode ?? '').trim() === code);
  const kind = String(hit?.bandKind ?? '').trim();
  return kind ? kind.toUpperCase() : undefined;
}

/** 开立数量换算为 PACKAGE 展示量，便于与目录库存对比 */
function convertRequiredQtyToPackageDisplay(
  requiredQty: number,
  requiredUnitCode: string,
  unitBands: AdviceProductOption['unitBands'],
): number | undefined {
  const qty = Number(requiredQty);
  if (!Number.isFinite(qty) || qty <= 0) {
    return undefined;
  }
  const bands = normalizeInboundProductUnitBands(unitBands);
  const unitCode = String(requiredUnitCode ?? '').trim();
  const kind = findBandKindByUnitCode(unitCode, unitBands);
  if (!kind) {
    const packageCode = unitCodeByBandKind(unitBands, 'PACKAGE');
    if (packageCode && packageCode === unitCode) {
      return qty;
    }
    return undefined;
  }
  const splitQty = convertInboundQtyBetweenBandKinds(qty, kind, 'SPLIT', bands);
  if (splitQty === undefined) {
    return undefined;
  }
  return convertAdviceCatalogSplitQtyToPackageDisplay(splitQty, unitBands);
}

/** 单条绑定耗材库存是否不足 */
function resolveBoundConsumableStockIssue(
  line: ServiceApplyBoundConsumableStockLine,
  catalog: AdviceProductOption,
): null | string {
  const productName = String(line.targetItemName ?? catalog.name ?? '').trim() || '未命名耗材';
  if (isAdviceCatalogZeroStock(catalog)) {
    return '暂无库存';
  }
  const requiredQty = Number(line.quantity ?? 1);
  const requiredUnitCode =
    String(line.unitCode ?? catalog.defaultUnitCode ?? '').trim() ||
    unitCodeByBandKind(catalog.unitBands, 'PACKAGE') ||
    '';
  const requiredPackageQty = convertRequiredQtyToPackageDisplay(
    requiredQty,
    requiredUnitCode,
    catalog.unitBands,
  );
  const availablePackageQty = resolveAdviceCatalogPackageDisplayQty(catalog);
  if (
    requiredPackageQty !== undefined &&
    availablePackageQty !== undefined &&
    requiredPackageQty > availablePackageQty + 1e-6
  ) {
    const requiredText =
      requiredUnitCode && Number.isFinite(requiredQty)
        ? `${requiredQty}${line.unitName ?? requiredUnitCode}`
        : String(requiredQty);
    const availableText = formatAdviceCatalogStockCellText(catalog);
    return `库存不足（需要 ${requiredText}，当前 ${availableText}）`;
  }
  if (
    requiredPackageQty !== undefined &&
    availablePackageQty === undefined &&
    sumAdviceCatalogInventorySplitQty(catalog.inventoryList) === undefined
  ) {
    return null;
  }
  return null;
}

/** 汇总各诊疗行绑定耗材（同耗材 id 数量累加） */
function aggregateBoundConsumablesForStock(
  items: readonly ServiceApplySelectedItem[],
): ServiceApplyBoundConsumableStockLine[] {
  const map = new Map<string, ServiceApplyBoundConsumableStockLine>();
  for (const item of items) {
    if (!item.boundConsumablesLoaded) {
      continue;
    }
    for (const consumable of item.boundConsumables ?? []) {
      const targetItemId = String(consumable.targetItemId ?? '').trim();
      if (!targetItemId) {
        continue;
      }
      const qtyRaw = Number(consumable.quantity ?? 1);
      const quantity = Number.isFinite(qtyRaw) && qtyRaw > 0 ? qtyRaw : 1;
      const existing = map.get(targetItemId);
      if (existing) {
        existing.quantity = decimalAdd(existing.quantity, quantity);
        continue;
      }
      map.set(targetItemId, {
        targetItemId,
        targetItemName: String(consumable.targetItemName ?? targetItemId).trim(),
        quantity,
        unitCode: String(consumable.unitCode ?? '').trim() || 'U',
        unitName: String(consumable.unitName ?? '').trim() || undefined,
      });
    }
  }
  return [...map.values()];
}

/** 列表签发：按 IF-DOR-23 解析待带出耗材及数量 */
async function resolveBoundConsumablesForStockFromDraftRows(params: {
  encounterId: string;
  patientId: string;
  stationScope: 'inpatient' | 'outpatient';
  draftRows: readonly AdviceRowItem[];
}): Promise<ServiceApplyBoundConsumableStockLine[]> {
  const bindLines = buildServiceApplyBindTriggerLinesFromAdviceRows(params.draftRows);
  if (bindLines.length === 0) {
    return [];
  }
  const apiAdapter =
    params.stationScope === 'inpatient'
      ? inpatientAdviceDraftBindHttpAdapter
      : outpatientAdviceDraftBindHttpAdapter;
  const resolveRes = await apiAdapter.resolveDraftBinds({
    encounterId: params.encounterId,
    patientId: params.patientId,
    lines: bindLines,
  });
  const dialogRows = filterDeviceBindDialogRows(
    mapDraftBindItemsToDialogRows(resolveRes.bindItems ?? [], bindLines),
  );
  const map = new Map<string, ServiceApplyBoundConsumableStockLine>();
  for (const row of dialogRows) {
    const targetItemId = String(row.bindItem.targetItemId ?? '').trim();
    if (!targetItemId) {
      continue;
    }
    const qtyRaw = Number(row.quantity ?? 1);
    const quantity = Number.isFinite(qtyRaw) && qtyRaw > 0 ? qtyRaw : 1;
    const existing = map.get(targetItemId);
    if (existing) {
      existing.quantity = decimalAdd(existing.quantity, quantity);
      continue;
    }
    map.set(targetItemId, {
      targetItemId,
      targetItemName: String(row.targetItemName ?? targetItemId).trim(),
      quantity,
      unitCode: String(row.unitCode ?? '').trim() || 'U',
      unitName: String(row.unitName ?? '').trim() || undefined,
    });
  }
  return [...map.values()];
}

/** 批量校验绑定耗材目录库存 */
async function assertBoundConsumablesCatalogStock(params: {
  encounterId: string;
  patientId: string;
  stationScope: 'inpatient' | 'outpatient';
  organizationUnitId?: string;
  consumables: readonly ServiceApplyBoundConsumableStockLine[];
}): Promise<void> {
  if (params.consumables.length === 0) {
    return;
  }
  const keys: PrepickedProductKeyDto[] = params.consumables.map((line) => ({
    productDefinitionId: preserveApiIdForRequest(line.targetItemId) ?? line.targetItemId,
    productTableName: DEVICE_DEFINITION_TABLE,
  }));
  const pageFn =
    params.stationScope === 'inpatient'
      ? httpPageInpatientDoctorStationAdviceCatalog
      : httpPageDoctorStationAdviceCatalog;
  const rawRows = await fetchAdviceCatalogRowsByPrepickedKeys(
    keys,
    {
      encounterId: params.encounterId,
      patientId: params.patientId,
      organizationUnitId: params.organizationUnitId,
      pricingSourceCode: ADVICE_PRICING_SOURCE_CODE,
    },
    pageFn,
  );
  const catalogById = new Map<string, AdviceProductOption>();
  for (const row of rawRows) {
    const id = String(row.productDefinitionId ?? '').trim();
    if (!id) {
      continue;
    }
    catalogById.set(id, mapCatalogRowToOption(row));
  }
  const issues: string[] = [];
  for (const line of params.consumables) {
    const catalog = catalogById.get(line.targetItemId);
    const name = String(line.targetItemName ?? '').trim() || '耗材';
    if (!catalog) {
      issues.push(`耗材「${name}」暂不可用`);
      continue;
    }
    const detail = resolveBoundConsumableStockIssue(line, catalog);
    if (detail) {
      issues.push(`耗材「${name}」${detail}`);
    }
  }
  if (issues.length > 0) {
    throw new Error(issues.join('；'));
  }
}

/** 申请单签发前：校验绑定耗材库存（弹窗已选 / 列表草稿） */
export async function validateServiceApplyBoundConsumablesBeforeSign(params: {
  encounterId: string;
  patientId: string;
  stationScope: 'inpatient' | 'outpatient';
  organizationUnitId?: string;
  items?: readonly ServiceApplySelectedItem[];
  draftRows?: readonly AdviceRowItem[];
}): Promise<void> {
  let consumables: ServiceApplyBoundConsumableStockLine[] = [];
  if (params.items && params.items.length > 0) {
    const enriched = await enrichServiceApplySelectedItemsBoundConsumables({
      encounterId: params.encounterId,
      patientId: params.patientId,
      items: [...params.items],
      stationScope: params.stationScope,
    });
    consumables = aggregateBoundConsumablesForStock(enriched);
  } else if (params.draftRows && params.draftRows.length > 0) {
    consumables = await resolveBoundConsumablesForStockFromDraftRows({
      encounterId: params.encounterId,
      patientId: params.patientId,
      stationScope: params.stationScope,
      draftRows: params.draftRows,
    });
  }
  await assertBoundConsumablesCatalogStock({
    encounterId: params.encounterId,
    patientId: params.patientId,
    stationScope: params.stationScope,
    organizationUnitId: params.organizationUnitId,
    consumables,
  });
}

/** 卡内已编辑耗材数量 → 签发落库用的确认绑定行 */
function buildServiceApplyConfirmedDeviceBindDialogRows(
  items: readonly ServiceApplySelectedItem[],
  bindLines: DoctorStationAdviceDraftLineRequest[],
  bindItems: DoctorStationAdviceDraftBindItemDto[],
): AdviceDraftBindDialogRow[] {
  const dialogRows = filterDeviceBindDialogRows(
    mapDraftBindItemsToDialogRows(bindItems, bindLines),
  );
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
  for (const row of dialogRows) {
    const triggerIndex = row.bindItem.triggerLineIndex;
    const triggerLine =
      triggerIndex === undefined || triggerIndex === null ? undefined : bindLines[triggerIndex];
    const activityId = String(
      triggerLine?.servicePayload?.activityDefinitionId ?? row.bindItem.triggerDefinitionId ?? '',
    ).trim();
    const targetId = String(row.bindItem.targetItemId ?? '').trim();
    if (!targetId) {
      continue;
    }
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
}): Promise<void> {
  const bindLines = buildServiceApplyBindTriggerLines(params.items, params.requestIds);
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

/** @deprecated 签发前弹窗确认绑定；申请单已改为卡内编辑，请用 persistServiceApplyDraftBindsAfterSign */
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
): DoctorStationAdviceDraftLineRequest[] {
  const lines: DoctorStationAdviceDraftLineRequest[] = [];
  for (let i = 0; i < items.length; i += 1) {
    const item = items[i];
    const requestId = preserveApiIdForRequest(requestIds[i]);
    if (!item || !requestId) {
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
      },
    });
  }
  return lines;
}

/** 列表/弹窗已落库草稿行 → IF-DOR-23 / IF-IP-DOR-23 触发行 */
export function buildServiceApplyBindTriggerLinesFromAdviceRows(
  adviceRows: readonly AdviceRowItem[],
): DoctorStationAdviceDraftLineRequest[] {
  const lines: DoctorStationAdviceDraftLineRequest[] = [];
  for (const row of adviceRows) {
    const requestId = preserveApiIdForRequest(row.requestId);
    const activityDefinitionId =
      preserveApiIdForRequest(row.productDefinitionId) ?? row.productDefinitionId;
    if (!requestId || !activityDefinitionId) {
      continue;
    }
    const qtyRaw = Number(row.listQuantity ?? 1);
    const quantity = Number.isFinite(qtyRaw) && qtyRaw > 0 ? qtyRaw : 1;
    const unitCode = String(row.listUnitCode ?? '').trim() || 'U';
    lines.push({
      lineOp: 'CREATE',
      productTableName: SERVICE_APPLY_ACTIVITY_TABLE,
      requestId,
      servicePayload: {
        activityDefinitionId,
        quantity,
        unitCode,
      },
    });
  }
  return lines;
}

/** 草稿申请单行签发前先解析绑定，再 sign */
export async function signServiceApplyDraftAdviceRowsWithBind(params: {
  encounterId: string;
  patientId: string;
  draftRows: readonly AdviceRowItem[];
  stationScope: 'inpatient' | 'outpatient';
  existingAdviceRows?: AdviceRowItem[] | DoctorStationAdviceRowDto[];
}): Promise<void> {
  await validateServiceApplyBoundConsumablesBeforeSign({
    encounterId: params.encounterId,
    patientId: params.patientId,
    stationScope: params.stationScope,
    draftRows: params.draftRows,
  });
  const bindLines = buildServiceApplyBindTriggerLinesFromAdviceRows(params.draftRows);
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
