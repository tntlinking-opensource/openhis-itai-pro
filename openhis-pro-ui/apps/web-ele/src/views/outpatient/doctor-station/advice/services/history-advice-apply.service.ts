import type { AdviceDrugItem, AdviceProductOption, AdviceRowItem } from './advice.types';
import type { HistoryAdviceRowItem } from './history-advice.types';

import type {
  PrepickedProductKeyDto,
  TerminologyOptionDto,
} from '#/api/doctor-station/advice.types';
import type { EncounterDiagnosisRowDto } from '#/api/doctor-station/diagnosis.types';

import { httpGetEncounterDiagnosis } from '#/api/doctor-station/diagnosis.http';
import { httpGetInpatientEncounterDiagnosis } from '#/api/inpatient/doctor-station/inpatient-doctor-station-diagnosis.http';
import {
  buildEncounterDiagnosisBindingPatch,
  parseEncounterDiagnosisIds,
} from '#/components/doctor-station/diagnosis/services/encounter-diagnosis-select';
import {
  filterWesternEncounterDiagnosisRowsForSave,
  saveEncounterDiagnosisRows,
} from '#/components/doctor-station/diagnosis/services/encounter-diagnosis.service';
import { isTcmDiagnosisRow } from '#/components/doctor-station/diagnosis/services/merge-encounter-diagnosis-display';
import {
  convertAdviceCatalogSplitQtyToPackageDisplay,
  formatAdviceCatalogStockCellText,
  isAdviceCatalogZeroStock,
  resolveAdviceCatalogPackageDisplayQty,
  sumAdviceCatalogInventorySplitQty,
} from '#/utils/doctor-station/advice-catalog-stock-display';
import { isChronicMedicalTypeCode } from '#/utils/chronic-medical-type';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import {
  convertInboundQtyBetweenBandKinds,
  normalizeInboundProductUnitBands,
} from '#/utils/inventory/inbound-product-unit-bands';

import { fetchAdviceCatalogRowsByPrepickedKeys } from './advice-catalog-prepicked';
import { saveAdviceDraftBatchDirect } from './advice-draft-save.service';
import { createAdviceDrugItem, inferDoseBandConvertK } from './advice-edit';
import {
  buildAdviceDrugItemSeedFromListRow,
  mergeCatalogIntoAdviceDrugItemForEdit,
  prepickedKeyFromListRow,
} from './advice-edit-hydrate';
import { mapCatalogRowToOption } from './advice-list';
import {
  loadPatientOpspFilingForHistoryApply,
  resolveHistoryDefMedicalTypeViaOpspFiling,
} from './history-advice-opsp-filing-gate';
import { buildAdviceDraftLines } from './advice-medication-draft';
import { inferAdviceLineEditorKind, validateWesternEditLineItem } from './advice-row';
import { ADVICE_PRICING_SOURCE_CODE } from './advice-tab.constants';
import { unitCodeByBandKind } from './medication-unit-bands';

/** 历史医嘱复用：药品库存不足明细 */
export interface HistoryAdviceMedicationStockIssue {
  productName: string;
  detail: string;
}

/** 历史医嘱复用入参 */
export interface ApplyHistoryAdviceParams {
  rows: HistoryAdviceRowItem[];
  encounterId: string;
  patientId: string;
  organizationUnitId?: string;
  medicationRateOptions: TerminologyOptionDto[];
  medicationRouteOptions: TerminologyOptionDto[];
  feeNatureOptions: TerminologyOptionDto[];
  /** 当前就诊主表行，用于药品 sortNumber 追加 */
  existingAdviceRows?: AdviceRowItem[];
}

/** 历史医嘱复用结果 */
export interface ApplyHistoryAdviceResult {
  appliedLineCount: number;
  /** 新写入或升级为慢病的诊断条目数 */
  importedDiagnosisCount: number;
  /** 历史慢病因备案无效/过期等未按慢病开立的提示（可空） */
  chronicSkippedHints?: string[];
}

/** 待落到本次就诊的西医诊断目录快照（跨就诊用 definitionId 重建） */
interface HistoryWesternDiagnosisDef {
  conditionCode?: string;
  conditionDefinitionId: string;
  conditionDefinitionName?: string;
  diagnosisDesc?: string;
  medicalTypeCode?: string;
  ybNo?: string;
}

/** 单行医嘱诊断绑定（本次就诊 ids） */
interface HistoryRowDiagnosisBinding {
  diagnosis: string;
  encounterDiagnosisIds: string[];
}

/** 历史医嘱复用业务异常（含库存不足明细） */
export class HistoryAdviceApplyError extends Error {
  stockIssues: HistoryAdviceMedicationStockIssue[];

  constructor(message: string, stockIssues: HistoryAdviceMedicationStockIssue[] = []) {
    super(message);
    this.name = 'HistoryAdviceApplyError';
    this.stockIssues = stockIssues;
  }
}

/** 成组展示行展开为单条医嘱行 */
function flattenHistoryRowsForApply(rows: HistoryAdviceRowItem[]): HistoryAdviceRowItem[] {
  const flat: HistoryAdviceRowItem[] = [];
  for (const row of rows) {
    const members = row.mergedAdviceRows;
    if (members && members.length > 1) {
      for (const member of members) {
        flat.push({
          ...member,
          encounterId: row.encounterId,
          groupId: String(row.groupId ?? member.groupId ?? '').trim() || undefined,
          reusable: row.reusable,
          reuseWarning: row.reuseWarning,
        });
      }
      continue;
    }
    flat.push(row);
  }
  return flat;
}

/** 复用时为历史组号分配新 groupId，避免与当前就诊冲突 */
function remapGroupIds(rows: HistoryAdviceRowItem[]): HistoryAdviceRowItem[] {
  const oldToNew = new Map<string, string>();
  let seq = 0;
  return rows.map((row) => {
    const oldGid = String(row.groupId ?? '').trim();
    if (!oldGid) {
      return row;
    }
    if (!oldToNew.has(oldGid)) {
      oldToNew.set(oldGid, String(Date.now() + seq));
      seq += 1;
    }
    return { ...row, groupId: oldToNew.get(oldGid) };
  });
}

/** 药品并组行：组首用法/频次/天数同步至组员 */
function syncGroupSharedFieldsFromLeader(rows: HistoryAdviceRowItem[]): HistoryAdviceRowItem[] {
  const leaderByGroup = new Map<string, HistoryAdviceRowItem>();
  for (const row of rows) {
    const groupId = String(row.groupId ?? '').trim();
    if (
      groupId &&
      String(row.resourceType ?? '').toUpperCase() === 'MEDICATION' &&
      !leaderByGroup.has(groupId)
    ) {
      leaderByGroup.set(groupId, row);
    }
  }
  return rows.map((row) => {
    const groupId = String(row.groupId ?? '').trim();
    if (!groupId) {
      return row;
    }
    const leader = leaderByGroup.get(groupId);
    if (!leader || leader === row) {
      return row;
    }
    return {
      ...row,
      methodCode: leader.methodCode ?? row.methodCode,
      rateCode: leader.rateCode ?? row.rateCode,
      dispensePerDuration: leader.dispensePerDuration ?? row.dispensePerDuration,
    };
  });
}

function normalizeHistoryRowsForApply(rows: HistoryAdviceRowItem[]): HistoryAdviceRowItem[] {
  return syncGroupSharedFieldsFromLeader(remapGroupIds(flattenHistoryRowsForApply(rows)));
}

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

function resolveHistoryMedicationStockIssue(
  row: HistoryAdviceRowItem,
  catalog: AdviceProductOption,
): HistoryAdviceMedicationStockIssue | null {
  const productName =
    String(row.adviceDisplayName ?? row.content ?? catalog.name ?? '').trim() || '未命名药品';
  if (isAdviceCatalogZeroStock(catalog)) {
    return {
      productName,
      detail: `库存不足（当前 ${formatAdviceCatalogStockCellText(catalog)}）`,
    };
  }

  const requiredQty = row.listQuantity ?? 1;
  const requiredUnitCode =
    String(row.listUnitCode ?? catalog.defaultUnitCode ?? '').trim() ||
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
        ? `${requiredQty} ${requiredUnitCode}`
        : String(requiredQty);
    return {
      productName,
      detail: `库存不足（需要 ${requiredText}，当前 ${formatAdviceCatalogStockCellText(catalog)}）`,
    };
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

async function fetchCatalogOptionsByPrepickedKeys(
  keys: PrepickedProductKeyDto[],
  params: Pick<ApplyHistoryAdviceParams, 'encounterId' | 'organizationUnitId' | 'patientId'>,
): Promise<AdviceProductOption[]> {
  if (keys.length === 0) {
    return [];
  }
  const encounterId = params.encounterId.trim();
  const patientId = params.patientId.trim();
  if (!encounterId || !patientId) {
    return [];
  }
  const rawRows = await fetchAdviceCatalogRowsByPrepickedKeys(keys, {
    encounterId,
    patientId,
    organizationUnitId: params.organizationUnitId,
    pricingSourceCode: ADVICE_PRICING_SOURCE_CODE,
  });
  return rawRows.map((item) => mapCatalogRowToOption(item));
}

function applyServicePerformOrgFallback(
  item: AdviceDrugItem,
  catalog: AdviceProductOption,
  organizationUnitId?: string,
): AdviceDrugItem {
  if (inferAdviceLineEditorKind(item) !== 'SERVICE') {
    return item;
  }
  const orgId = String(
    item.performOrgId ?? catalog.defaultPerformOrgId ?? organizationUnitId ?? '',
  ).trim();
  if (!orgId) {
    return item;
  }
  return {
    ...item,
    performOrgId: orgId,
    performOrgName:
      String(item.performOrgName ?? catalog.defaultPerformOrgName ?? '').trim() || undefined,
  };
}

/** 历史行 → 编辑模型：按已解析的本次就诊诊断 ids 绑定（无则空，不默认塞主诊） */
function buildAdviceDrugItemFromHistoryRow(
  row: HistoryAdviceRowItem,
  catalog: AdviceProductOption,
  params: ApplyHistoryAdviceParams,
  diagnosisBinding: HistoryRowDiagnosisBinding,
): AdviceDrugItem {
  const ctx = {
    feeNatureOptions: params.feeNatureOptions,
    medicationRateOptions: params.medicationRateOptions,
    medicationRouteOptions: params.medicationRouteOptions,
  };
  const seed = buildAdviceDrugItemSeedFromListRow(row, { reuseAsNew: true });
  const base = createAdviceDrugItem(seed, ctx);
  const merged = mergeCatalogIntoAdviceDrugItemForEdit({ ...base, ...seed }, catalog, {
    resourceType: row.resourceType,
  });
  const item = inferDoseBandConvertK({
    ...base,
    ...merged,
    diagnosis: diagnosisBinding.diagnosis,
    encounterDiagnosisIds: diagnosisBinding.encounterDiagnosisIds,
    groupId: seed.groupId,
    expanded: false,
    medicationRequestId: undefined,
  } as AdviceDrugItem);
  return applyServicePerformOrgFallback(item, catalog, params.organizationUnitId);
}

/** 解析就诊诊断列表响应 */
function parseEncounterDiagnosisResponse(
  res: Awaited<ReturnType<typeof httpGetEncounterDiagnosis>>,
): EncounterDiagnosisRowDto[] {
  const list = res?.diagnosisList ?? res?.content ?? [];
  return Array.isArray(list) ? list : [];
}

/** 拉取指定就诊诊断（门诊优先，失败回落住院，对齐中药历史跨场景） */
async function loadEncounterDiagnosisRows(
  encounterId: string,
  patientId: string,
): Promise<EncounterDiagnosisRowDto[]> {
  try {
    return parseEncounterDiagnosisResponse(
      await httpGetEncounterDiagnosis({ encounterId, patientId }),
    );
  } catch {
    try {
      return parseEncounterDiagnosisResponse(
        await httpGetInpatientEncounterDiagnosis({ encounterId, patientId }),
      );
    } catch {
      return [];
    }
  }
}

/** 历史就诊诊断行 → 可跨就诊重建的西医目录快照 */
function toHistoryWesternDiagnosisDef(
  row: EncounterDiagnosisRowDto,
): HistoryWesternDiagnosisDef | null {
  if (isTcmDiagnosisRow(row)) {
    return null;
  }
  const conditionDefinitionId = preserveApiIdForRequest(row.conditionDefinitionId) ?? '';
  if (!conditionDefinitionId) {
    return null;
  }
  return {
    conditionDefinitionId,
    conditionCode: String(row.conditionCode ?? '').trim() || undefined,
    conditionDefinitionName: String(row.conditionDefinitionName ?? '').trim() || undefined,
    diagnosisDesc: String(row.diagnosisDesc ?? '').trim() || undefined,
    medicalTypeCode: String(row.medicalTypeCode ?? '').trim() || undefined,
    ybNo: String(row.ybNo ?? '').trim() || undefined,
  };
}

/** 按有序历史就诊诊断 ids 解析西医目录快照（跳过中医/无效） */
function resolveWesternDefsFromHistoryDxIds(
  historyDxIds: string[],
  historyDxById: Map<string, EncounterDiagnosisRowDto>,
): HistoryWesternDiagnosisDef[] {
  const defs: HistoryWesternDiagnosisDef[] = [];
  const seen = new Set<string>();
  // 保序：历史行 encounterDiagnosisIds 首位为主诊语义
  for (const dxId of historyDxIds) {
    const historyRow = historyDxById.get(dxId);
    if (!historyRow) {
      continue;
    }
    const def = toHistoryWesternDiagnosisDef(historyRow);
    if (!def || seen.has(def.conditionDefinitionId)) {
      continue;
    }
    seen.add(def.conditionDefinitionId);
    defs.push(def);
  }
  return defs;
}

/** 批量拉历史就诊诊断，构建 encounterId → (dxId → 行) */
async function loadHistoryDiagnosisMaps(
  historyEncounterIds: string[],
  patientId: string,
): Promise<Map<string, Map<string, EncounterDiagnosisRowDto>>> {
  const result = new Map<string, Map<string, EncounterDiagnosisRowDto>>();
  // 去重后并行拉取各历史就诊诊断
  await Promise.all(
    historyEncounterIds.map(async (historyEncounterId) => {
      const rows = await loadEncounterDiagnosisRows(historyEncounterId, patientId);
      const byId = new Map<string, EncounterDiagnosisRowDto>();
      for (const row of rows) {
        const dxId = String(row.encounterDiagnosisId ?? '').trim();
        if (dxId) {
          byId.set(dxId, row);
        }
      }
      result.set(historyEncounterId, byId);
    }),
  );
  return result;
}

/**
 * 把历史行诊断落到本次就诊并按行解析绑定。
 * - 已有相同目录：不落库，直接绑本次 ids
 * - 历史慢病：须过慢病备案校验后才按慢病开立/升级；未过检则按普通门诊带入
 * - 没有：追加写入（不抢主诊；本次无主诊时首个新诊作主诊）
 * - 行上无诊断：绑空
 */
async function resolveHistoryAdviceRowDiagnosisBindings(params: {
  currentEncounterId: string;
  patientId: string;
  rows: HistoryAdviceRowItem[];
}): Promise<{
  bindings: HistoryRowDiagnosisBinding[];
  encounterDiagnosisRows: EncounterDiagnosisRowDto[];
  importedDiagnosisCount: number;
  chronicSkippedHints: string[];
}> {
  const currentEncounterId = params.currentEncounterId.trim();
  const patientId = params.patientId.trim();
  const emptyBinding: HistoryRowDiagnosisBinding = {
    diagnosis: '',
    encounterDiagnosisIds: [],
  };
  const chronicSkippedHints: string[] = [];

  // 跨就诊才需要拉历史诊断；同就诊可直接用历史行上的本次有效 ids
  const crossHistoryEncounterIds: string[] = [];
  const seenHistoryEid = new Set<string>();
  for (const row of params.rows) {
    const historyEid = String(row.encounterId ?? '').trim();
    if (!historyEid || historyEid === currentEncounterId) {
      continue;
    }
    if (parseEncounterDiagnosisIds(row.encounterDiagnosisIds).length === 0) {
      continue;
    }
    if (seenHistoryEid.has(historyEid)) {
      continue;
    }
    seenHistoryEid.add(historyEid);
    crossHistoryEncounterIds.push(historyEid);
  }

  const [currentRows, historyMaps] = await Promise.all([
    loadEncounterDiagnosisRows(currentEncounterId, patientId),
    loadHistoryDiagnosisMaps(crossHistoryEncounterIds, patientId),
  ]);

  // 同就诊行：历史 dx id 即本次 id，并入查找表
  const currentById = new Map<string, EncounterDiagnosisRowDto>();
  for (const row of currentRows) {
    const dxId = String(row.encounterDiagnosisId ?? '').trim();
    if (dxId) {
      currentById.set(dxId, row);
    }
  }
  historyMaps.set(currentEncounterId, currentById);

  // 按行解析目录快照；并集用于判断需否追加
  const defsPerRow: HistoryWesternDiagnosisDef[][] = [];
  const neededDefs: HistoryWesternDiagnosisDef[] = [];
  const neededSeen = new Set<string>();
  for (const row of params.rows) {
    const historyEid = String(row.encounterId ?? '').trim();
    const historyDxIds = parseEncounterDiagnosisIds(row.encounterDiagnosisIds);
    if (!historyEid || historyDxIds.length === 0) {
      defsPerRow.push([]);
      continue;
    }
    const historyDxById = historyMaps.get(historyEid) ?? new Map();
    const defs = resolveWesternDefsFromHistoryDxIds(historyDxIds, historyDxById);
    defsPerRow.push(defs);
    for (const def of defs) {
      if (neededSeen.has(def.conditionDefinitionId)) {
        continue;
      }
      neededSeen.add(def.conditionDefinitionId);
      neededDefs.push(def);
    }
  }

  // 历史含慢病时才拉备案；与诊断 Tab 导入同契约
  const needsOpspGate = neededDefs.some((def) => isChronicMedicalTypeCode(def.medicalTypeCode));
  const opspFiling = needsOpspGate
    ? await loadPatientOpspFilingForHistoryApply(currentEncounterId, patientId)
    : { opspFilingImportEnabled: false, filingList: [] };

  // 备案门控后写回各目录的有效 medicalTypeCode；补录可能 remap 目录 id
  const resolvedMedTypeByDefId = new Map<string, string | undefined>();
  const chronicAllowedDefIds = new Set<string>();
  const remappedDefIdByHistoryDefId = new Map<string, string>();
  for (const def of neededDefs) {
    const resolved = await resolveHistoryDefMedicalTypeViaOpspFiling(
      def,
      opspFiling,
      currentEncounterId,
      patientId,
    );
    const effectiveDefId = resolved.remappedConditionDefinitionId || def.conditionDefinitionId;
    if (resolved.remappedConditionDefinitionId) {
      remappedDefIdByHistoryDefId.set(
        def.conditionDefinitionId,
        resolved.remappedConditionDefinitionId,
      );
    }
    resolvedMedTypeByDefId.set(effectiveDefId, resolved.medicalTypeCode);
    // 历史 id 也指向同一医疗类别，便于已有行升级查找
    resolvedMedTypeByDefId.set(def.conditionDefinitionId, resolved.medicalTypeCode);
    if (resolved.asChronic) {
      chronicAllowedDefIds.add(effectiveDefId);
      chronicAllowedDefIds.add(def.conditionDefinitionId);
    }
    if (resolved.skipHint && !chronicSkippedHints.includes(resolved.skipHint)) {
      chronicSkippedHints.push(resolved.skipHint);
    }
  }

  // 行级 defs：补录后目录 id 替换为院内可用 id
  const effectiveDefsPerRow = defsPerRow.map((defs) =>
    defs.map((def) => {
      const remapped = remappedDefIdByHistoryDefId.get(def.conditionDefinitionId);
      if (!remapped) {
        return def;
      }
      return { ...def, conditionDefinitionId: remapped };
    }),
  );
  const effectiveNeededDefs = neededDefs.map((def) => {
    const remapped = remappedDefIdByHistoryDefId.get(def.conditionDefinitionId);
    if (!remapped) {
      return def;
    }
    return { ...def, conditionDefinitionId: remapped };
  });
  // 去重（多历史诊断 remap 到同一目录）
  const dedupedNeededDefs: HistoryWesternDiagnosisDef[] = [];
  const dedupeSeen = new Set<string>();
  for (const def of effectiveNeededDefs) {
    if (dedupeSeen.has(def.conditionDefinitionId)) {
      continue;
    }
    dedupeSeen.add(def.conditionDefinitionId);
    dedupedNeededDefs.push(def);
  }

  let westernCurrent = filterWesternEncounterDiagnosisRowsForSave(currentRows);
  const existingByDefId = new Map<string, EncounterDiagnosisRowDto>();
  for (const row of westernCurrent) {
    const defId = preserveApiIdForRequest(row.conditionDefinitionId) ?? '';
    if (defId && !existingByDefId.has(defId)) {
      existingByDefId.set(defId, row);
    }
  }
  const hasMain = westernCurrent.some((row) => Number(row.mainDiagnosisFlag) === 1);

  // 备案通过的慢病且本次同目录非慢病 → 升级 med_type
  let upgradedChronicCount = 0;
  westernCurrent = westernCurrent.map((row) => {
    const defId = preserveApiIdForRequest(row.conditionDefinitionId) ?? '';
    if (
      !defId ||
      !chronicAllowedDefIds.has(defId) ||
      isChronicMedicalTypeCode(row.medicalTypeCode)
    ) {
      return row;
    }
    const med = resolvedMedTypeByDefId.get(defId);
    if (!isChronicMedicalTypeCode(med)) {
      return row;
    }
    upgradedChronicCount += 1;
    return { ...row, medicalTypeCode: med };
  });

  // 追加本次尚无的目录诊断（慢病 medicalType 已过备案门控）
  const toAppend: EncounterDiagnosisRowDto[] = [];
  for (const def of dedupedNeededDefs) {
    if (existingByDefId.has(def.conditionDefinitionId)) {
      continue;
    }
    toAppend.push({
      conditionDefinitionId: def.conditionDefinitionId,
      conditionCode: def.conditionCode ?? '',
      conditionDefinitionName: def.conditionDefinitionName ?? '',
      medicineType: 'WESTERN',
      // 本次已有主诊则不抢；无主诊时首个带入作主诊
      mainDiagnosisFlag: !hasMain && toAppend.length === 0 ? 1 : 0,
      medicalTypeCode: resolvedMedTypeByDefId.get(def.conditionDefinitionId) ?? def.medicalTypeCode,
      diagnosisDesc: def.diagnosisDesc,
      ybNo: def.ybNo,
    });
    existingByDefId.set(def.conditionDefinitionId, toAppend[toAppend.length - 1]!);
  }

  let importedDiagnosisCount = 0;
  if (toAppend.length > 0 || upgradedChronicCount > 0) {
    await saveEncounterDiagnosisRows(currentEncounterId, patientId, [
      ...westernCurrent,
      ...toAppend,
    ]);
    importedDiagnosisCount = toAppend.length + upgradedChronicCount;
    const refreshed = await loadEncounterDiagnosisRows(currentEncounterId, patientId);
    westernCurrent = filterWesternEncounterDiagnosisRowsForSave(refreshed);
  }

  // 目录 id → 本次就诊诊断 id（西医）
  const currentDxIdByDefId = new Map<string, string>();
  for (const row of westernCurrent) {
    const defId = preserveApiIdForRequest(row.conditionDefinitionId) ?? '';
    const dxId = String(row.encounterDiagnosisId ?? '').trim();
    if (defId && dxId && !currentDxIdByDefId.has(defId)) {
      currentDxIdByDefId.set(defId, dxId);
    }
  }

  const encounterDiagnosisRows = westernCurrent.length > 0 ? westernCurrent : currentRows;
  const bindings: HistoryRowDiagnosisBinding[] = effectiveDefsPerRow.map((defs) => {
    if (defs.length === 0) {
      return emptyBinding;
    }
    // 按历史行诊断序映射到本次 ids
    const currentIds: string[] = [];
    for (const def of defs) {
      const dxId = currentDxIdByDefId.get(def.conditionDefinitionId);
      if (dxId && !currentIds.includes(dxId)) {
        currentIds.push(dxId);
      }
    }
    if (currentIds.length === 0) {
      return emptyBinding;
    }
    return buildEncounterDiagnosisBindingPatch(currentIds, encounterDiagnosisRows);
  });

  return { bindings, encounterDiagnosisRows, importedDiagnosisCount, chronicSkippedHints };
}

/** 历史医嘱复用至当前就诊草稿（DIRECT_ONLY：不解析用法绑定，避免二次带出） */
export async function applyHistoryAdviceToAdviceDraft(
  params: ApplyHistoryAdviceParams,
): Promise<ApplyHistoryAdviceResult> {
  const encounterId = params.encounterId.trim();
  const patientId = params.patientId.trim();
  if (!encounterId || !patientId) {
    throw new HistoryAdviceApplyError('缺少就诊或患者信息，无法复用历史医嘱');
  }

  const normalizedRows = normalizeHistoryRowsForApply(params.rows);
  if (normalizedRows.length === 0) {
    throw new HistoryAdviceApplyError('未选择可复用的医嘱');
  }

  const prepickedKeys = normalizedRows
    .map((row) => prepickedKeyFromListRow(row))
    .filter((key): key is PrepickedProductKeyDto => key !== null);
  if (prepickedKeys.length !== normalizedRows.length) {
    throw new HistoryAdviceApplyError('历史医嘱存在无效项目，无法复用');
  }

  // 诊断导入与目录 prepicked 并行（历史诊断拉数失败在内部降级为空，落库失败向上抛）
  const [catalogList, diagnosisResolve] = await Promise.all([
    fetchCatalogOptionsByPrepickedKeys(prepickedKeys, params),
    resolveHistoryAdviceRowDiagnosisBindings({
      currentEncounterId: encounterId,
      patientId,
      rows: normalizedRows,
    }),
  ]);

  const { bindings, encounterDiagnosisRows, importedDiagnosisCount, chronicSkippedHints } =
    diagnosisResolve;

  const catalogMap = new Map<string, AdviceProductOption>();
  for (const opt of catalogList) {
    const id = String(opt.productDefinitionId ?? '').trim();
    const tbl = String(opt.productTableName ?? '').trim();
    if (id && tbl) {
      catalogMap.set(`${tbl}:${id}`, opt);
    }
  }

  const stockIssues: HistoryAdviceMedicationStockIssue[] = [];
  const drugItems: AdviceDrugItem[] = [];

  for (let index = 0; index < normalizedRows.length; index += 1) {
    const row = normalizedRows[index]!;
    const key = prepickedKeyFromListRow(row);
    if (!key) {
      continue;
    }
    const catalog = catalogMap.get(`${key.productTableName}:${key.productDefinitionId}`);
    if (!catalog) {
      const name = String(row.adviceDisplayName ?? row.content ?? '').trim() || '未知项目';
      throw new HistoryAdviceApplyError(`目录中未找到「${name}」，无法复用历史医嘱`);
    }

    if (String(row.resourceType ?? '').toUpperCase() === 'MEDICATION') {
      const issue = resolveHistoryMedicationStockIssue(row, catalog);
      if (issue) {
        stockIssues.push(issue);
      }
    }

    const diagnosisBinding = bindings[index] ?? {
      diagnosis: '',
      encounterDiagnosisIds: [],
    };
    drugItems.push(buildAdviceDrugItemFromHistoryRow(row, catalog, params, diagnosisBinding));
  }

  if (stockIssues.length > 0) {
    const names = stockIssues.map((item) => `「${item.productName}」${item.detail}`).join('；');
    throw new HistoryAdviceApplyError(`以下药品库存不足：${names}`, stockIssues);
  }

  const invalidIndex = drugItems.findIndex((item) => !validateWesternEditLineItem(item));
  if (invalidIndex !== -1) {
    const badRow = normalizedRows[invalidIndex];
    const name =
      String(badRow?.adviceDisplayName ?? badRow?.content ?? '').trim() ||
      `第 ${invalidIndex + 1} 项`;
    throw new HistoryAdviceApplyError(`「${name}」参数不完整，无法复用历史医嘱`);
  }

  const draftLines = (() => {
    try {
      return buildAdviceDraftLines(drugItems, [], {
        encounterDiagnosisRows,
        existingAdviceRows: params.existingAdviceRows ?? [],
      });
    } catch (error) {
      throw new HistoryAdviceApplyError(error instanceof Error ? error.message : '请求组包失败');
    }
  })();
  const saveRes = await saveAdviceDraftBatchDirect({
    encounterId,
    patientId,
    pricingSourceCode: ADVICE_PRICING_SOURCE_CODE,
    lines: draftLines,
  });
  if (saveRes.success === false) {
    throw new HistoryAdviceApplyError(String(saveRes.message ?? '').trim() || '保存医嘱草稿失败');
  }

  return {
    appliedLineCount: draftLines.length,
    importedDiagnosisCount,
    chronicSkippedHints: chronicSkippedHints.length > 0 ? chronicSkippedHints : undefined,
  };
}
