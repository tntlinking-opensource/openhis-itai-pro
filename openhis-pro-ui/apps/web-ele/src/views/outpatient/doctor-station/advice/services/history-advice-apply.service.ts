import type { AdviceDrugItem, AdviceProductOption, AdviceRowItem } from './advice.types';
import type { HistoryAdviceRowItem } from './history-advice.types';

import type {
  DoctorStationAdviceCatalogRowDto,
  PrepickedProductKeyDto,
  TerminologyOptionDto,
} from '#/api/doctor-station/advice.types';
import type { EncounterDiagnosisRowDto } from '#/api/doctor-station/diagnosis.types';

import {
  httpPageDoctorStationAdviceCatalog,
  httpSaveDoctorStationAdviceDraftBatch,
} from '#/api/doctor-station/advice.http';
import { httpGetEncounterDiagnosis } from '#/api/doctor-station/diagnosis.http';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { resolveRequestErrorMessage } from '#/utils/resolve-request-error-message';
import {
  convertAdviceCatalogSplitQtyToPackageDisplay,
  formatAdviceCatalogStockCellText,
  isAdviceCatalogZeroStock,
  resolveAdviceCatalogPackageDisplayQty,
  sumAdviceCatalogInventorySplitQty,
} from '#/utils/doctor-station/advice-catalog-stock-display';
import {
  convertInboundQtyBetweenBandKinds,
  normalizeInboundProductUnitBands,
} from '#/utils/inventory/inbound-product-unit-bands';

import {
  createAdviceDrugItem,
  inferDoseBandConvertK,
  pickMainDiagnosisDisplayName,
} from './advice-edit';
import {
  buildAdviceDrugItemSeedFromListRow,
  mergeCatalogIntoAdviceDrugItemForEdit,
  prepickedKeyFromListRow,
} from './advice-edit-hydrate';
import { mapCatalogRowToOption } from './advice-list';
import { buildAdviceDraftLines } from './advice-medication-draft';
import { validateWesternEditLineItem, inferAdviceLineEditorKind } from './advice-row';
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
  const res = await httpPageDoctorStationAdviceCatalog({
    encounterId,
    patientId,
    organizationUnitId: params.organizationUnitId,
    pageNo: 1,
    pageSize: Math.max(keys.length, 20),
    prepickedProductKeys: keys,
    pricingSourceCode: ADVICE_PRICING_SOURCE_CODE,
  });
  const { content: rawRows } = parsePagedRowsFromApiData<DoctorStationAdviceCatalogRowDto>(res);
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

function buildAdviceDrugItemFromHistoryRow(
  row: HistoryAdviceRowItem,
  catalog: AdviceProductOption,
  params: ApplyHistoryAdviceParams,
  mainDiagnosisName: string,
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
    diagnosis: mainDiagnosisName || seed.diagnosis || base.diagnosis,
    groupId: seed.groupId,
    expanded: false,
    medicationRequestId: undefined,
  } as AdviceDrugItem);
  return applyServicePerformOrgFallback(item, catalog, params.organizationUnitId);
}

async function loadEncounterDiagnosisRows(
  encounterId: string,
  patientId: string,
): Promise<EncounterDiagnosisRowDto[]> {
  const res = await httpGetEncounterDiagnosis({ encounterId, patientId });
  const list = res?.diagnosisList ?? res?.content ?? [];
  return Array.isArray(list) ? list : [];
}

/** 历史医嘱复用至当前就诊草稿（静默保存） */
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

  const [catalogList, encounterDiagnosisRows] = await Promise.all([
    fetchCatalogOptionsByPrepickedKeys(prepickedKeys, params),
    loadEncounterDiagnosisRows(encounterId, patientId),
  ]);

  const catalogMap = new Map<string, AdviceProductOption>();
  for (const opt of catalogList) {
    const id = String(opt.productDefinitionId ?? '').trim();
    const tbl = String(opt.productTableName ?? '').trim();
    if (id && tbl) {
      catalogMap.set(`${tbl}:${id}`, opt);
    }
  }

  const mainDiagnosisName = pickMainDiagnosisDisplayName(encounterDiagnosisRows);
  const stockIssues: HistoryAdviceMedicationStockIssue[] = [];
  const drugItems: AdviceDrugItem[] = [];

  for (const row of normalizedRows) {
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

    drugItems.push(buildAdviceDrugItemFromHistoryRow(row, catalog, params, mainDiagnosisName));
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

  const draftLines = buildAdviceDraftLines(drugItems, [], {
    encounterDiagnosisRows,
    existingAdviceRows: params.existingAdviceRows ?? [],
  });
  let saveRes: Awaited<ReturnType<typeof httpSaveDoctorStationAdviceDraftBatch>>;
  try {
    saveRes = await httpSaveDoctorStationAdviceDraftBatch({
      encounterId,
      patientId,
      pricingSourceCode: ADVICE_PRICING_SOURCE_CODE,
      lines: draftLines,
    });
  } catch (error) {
    throw new HistoryAdviceApplyError(resolveRequestErrorMessage(error, '保存医嘱草稿失败'));
  }
  if (saveRes.success === false) {
    throw new HistoryAdviceApplyError(String(saveRes.message ?? '').trim() || '保存医嘱草稿失败');
  }

  return { appliedLineCount: draftLines.length };
}
