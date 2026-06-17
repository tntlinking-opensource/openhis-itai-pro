import type { WesternProductSelectPatchOptions } from './advice-edit';
import type { AdviceDrugItem, AdviceProductOption, AdviceRowItem } from './advice.types';

import type { PrepickedProductKeyDto } from '#/api/doctor-station/advice.types';

import { buildWesternProductSelectPatch } from './advice-edit';
import {
  hydrateAdviceRowDoseFromBackend,
  resolveAdviceLineUnitPrice,
} from './medication-unit-bands';

type AdviceDrugItemSeed = Partial<AdviceDrugItem> & {
  listDose?: number;
  listDoseUnitCode?: string;
};

/** 列表行 → 目录 prepicked 键 */
export function prepickedKeyFromListRow(row: AdviceRowItem): null | PrepickedProductKeyDto {
  const productDefinitionId = String(row.productDefinitionId ?? '').trim();
  const productTableName = String(row.productTableName ?? '').trim();
  if (!productDefinitionId || !productTableName) return null;
  return { productDefinitionId, productTableName };
}

function positiveNumber(raw: unknown): number | undefined {
  if (raw === null || raw === undefined || raw === '') return undefined;
  const n = typeof raw === 'number' ? raw : Number(raw);
  return Number.isFinite(n) && n > 0 ? n : undefined;
}

function positiveInt(raw: unknown): number | undefined {
  const n = positiveNumber(raw);
  return n === undefined ? undefined : Math.trunc(n);
}

/** 列表行结构化字段 → 编辑卡种子（不含目录 unitBands） */
export function buildAdviceDrugItemSeedFromListRow(
  row: AdviceRowItem,
  options?: { reuseAsNew?: boolean },
): AdviceDrugItemSeed {
  const defId = String(row.productDefinitionId ?? '').trim();
  const tbl = String(row.productTableName ?? '').trim();
  const selectedId = defId && tbl ? `${tbl}:${defId}` : `fallback-${row.key}`;
  const rt = String(row.resourceType ?? '')
    .trim()
    .toUpperCase();
  const isService = rt === 'SERVICE' || tbl.toLowerCase() === 'wkf_activity_definition';
  const qty = row.listQuantity;
  const unitCode = String(row.listUnitCode ?? '').trim();
  const diagnosisRaw = String(row.conditionDefinitionName ?? row.diagnosisSummary ?? '').trim();
  const diagnosis = diagnosisRaw === '—' ? '' : diagnosisRaw;
  const displayName = String(row.adviceDisplayName ?? '').trim();
  const spec = String(row.specificationText ?? '').trim();
  const content = String(row.content ?? '').trim();
  const nameMatch = content.match(/^(.*?)【/);
  const specMatch = content.match(/【(.*?)】/);

  return {
    orderType: (row.type.split('/')[0] || '').trim(),
    selectedId,
    productTableName: tbl || undefined,
    selectedDrugName: displayName || nameMatch?.[1]?.trim() || content,
    selectedDrugSpec: spec || specMatch?.[1] || '',
    diagnosis,
    skinTest: row.skinTestFlag === true,
    frequency: String(row.rateCode ?? '').trim(),
    usage: String(row.methodCode ?? '').trim(),
    medicationDays: positiveInt(row.dispensePerDuration),
    sortNumber: row.sortNumber,
    groupId: String(row.groupId ?? '').trim() || undefined,
    therapyEnum: String(row.therapyTypeCode ?? '').trim() || undefined,
    medicationRequestId: options?.reuseAsNew
      ? undefined
      : String(row.requestId ?? '').trim() || undefined,
    listDose: row.listDose,
    listDoseUnitCode: row.listDoseUnitCode,
    ...(isService
      ? {
          serviceQuantity: qty ?? 1,
          totalAmount: positiveInt(row.listExecutionCount) ?? 1,
          totalAmountUnit: unitCode || '次',
          performOrgId: String(row.performOrgId ?? '').trim() || undefined,
          performOrgName: String(row.performOrgName ?? '').trim() || undefined,
        }
      : {
          totalAmount: qty,
          totalAmountUnit: unitCode,
        }),
  };
}

export interface MergeCatalogIntoAdviceDrugItemOptions extends WesternProductSelectPatchOptions {
  resourceType?: string;
  therapyTypeCode?: string;
}

/** 目录选品 + 列表行已填医嘱合并：保留开立数据，补齐 unitBands/单价/单次剂量换算。 */
export function mergeCatalogIntoAdviceDrugItemForEdit(
  seed: AdviceDrugItemSeed,
  catalog: AdviceProductOption,
  opts?: MergeCatalogIntoAdviceDrugItemOptions,
): Partial<AdviceDrugItem> {
  const tbl = String(catalog.productTableName ?? '')
    .trim()
    .toLowerCase();
  const base: Partial<AdviceDrugItem> = {
    selectedId: String(catalog.id ?? seed.selectedId ?? '').trim(),
    selectedDrugName: catalog.name || seed.selectedDrugName || '',
    selectedDrugSpec: catalog.spec || seed.selectedDrugSpec || '',
    productTableName: catalog.productTableName || seed.productTableName,
  };

  const preserved: Partial<AdviceDrugItem> = {
    diagnosis: seed.diagnosis,
    feeNature: seed.feeNature,
    frequency: seed.frequency,
    usage: seed.usage,
    medicationDays: seed.medicationDays,
    skinTest: seed.skinTest,
    pharmacy: seed.pharmacy,
    sortNumber: seed.sortNumber,
    groupId: seed.groupId,
    therapyEnum: seed.therapyEnum ?? opts?.therapyTypeCode,
    medicationRequestId: seed.medicationRequestId,
    serviceQuantity: seed.serviceQuantity,
    totalAmount: seed.totalAmount,
    totalAmountUnit: seed.totalAmountUnit,
    performOrgId: seed.performOrgId,
    performOrgName: seed.performOrgName,
  };

  if (tbl === 'wkf_activity_definition') {
    const performOrgId =
      String(seed.performOrgId ?? catalog.defaultPerformOrgId ?? '').trim() || undefined;
    const performOrgName =
      String(seed.performOrgName ?? catalog.defaultPerformOrgName ?? '').trim() || undefined;
    return {
      ...base,
      ...preserved,
      performOrgId,
      performOrgName,
      selectedDrugPrice: catalog.price ?? seed.selectedDrugPrice ?? '',
    };
  }

  if (tbl === 'adm_device_definition') {
    const uCode = String(catalog.defaultUnitCode ?? seed.totalAmountUnit ?? '').trim();
    const uName = String(catalog.defaultUnitName ?? '').trim();
    return {
      ...base,
      ...preserved,
      selectedDrugPrice: catalog.price ?? seed.selectedDrugPrice ?? '',
      unitBands: catalog.unitBands,
      totalUnitSalesCode: uCode || undefined,
      totalUnitSalesName: uName || uCode || undefined,
      totalAmountUnit: String(seed.totalAmountUnit ?? uCode).trim() || uCode,
    };
  }

  const therapy =
    String(opts?.therapyTypeCode ?? seed.therapyEnum ?? '')
      .trim()
      .toUpperCase() || undefined;
  const totalUnitDefaultKind =
    opts?.totalUnitDefaultKind ?? (therapy === 'TEMPORARY' ? 'PACKAGE' : 'SALES');
  const catalogPatch = buildWesternProductSelectPatch(
    { ...(seed as AdviceDrugItem), ...preserved } as AdviceDrugItem,
    catalog,
    { totalUnitDefaultKind },
  );

  const doseHydrated = hydrateAdviceRowDoseFromBackend({
    dose: seed.listDose,
    doseUnitCode: seed.listDoseUnitCode,
    doseUnitName: seed.doseSubUnit,
    unitBands: catalog.unitBands ?? catalogPatch.unitBands,
  });

  const totalUnit = String(seed.totalAmountUnit ?? '').trim() || catalogPatch.totalAmountUnit;
  let selectedDrugPrice = catalogPatch.selectedDrugPrice ?? catalog.price ?? seed.selectedDrugPrice;
  if (totalUnit) {
    const linePrice = resolveAdviceLineUnitPrice({
      bands: catalog.unitBands ?? catalogPatch.unitBands,
      catalogUnitPrice: catalogPatch.catalogUnitPrice ?? catalog.catalogUnitPrice,
      totalAmountUnit: totalUnit,
    });
    if (linePrice !== undefined) selectedDrugPrice = linePrice;
  }

  return {
    ...catalogPatch,
    ...doseHydrated,
    ...base,
    ...preserved,
    totalAmountUnit: totalUnit,
    selectedDrugPrice,
  };
}
