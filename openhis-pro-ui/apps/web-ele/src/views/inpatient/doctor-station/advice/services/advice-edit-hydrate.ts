import type { PrepickedProductKeyDto } from '#/api/doctor-station/advice.types';

import type { AdviceDrugItem, AdviceProductOption, AdviceRowItem } from './advice.types';
import type { WesternProductSelectPatchOptions } from './advice-edit';
import { buildWesternProductSelectPatch, pickCatalogChrgitmLvPatch } from './advice-edit';
import { isInpatientLongTermMedicationTherapy } from './advice-row';
import {
  hydrateAdviceRowDoseFromBackend,
  hydrateDoseScheduleSlots,
  hydrateFirstDoseDisplayQty,
  resolveAdviceLineUnitPrice,
} from './medication-unit-bands';

type AdviceDrugItemSeed = Partial<AdviceDrugItem> & {
  listDose?: number;
  listDoseUnitCode?: string;
  listFirstDose?: number;
  listFirstDoseUnitCode?: string;
  listDoseSchedule?: string;
  firstDose?: number;
  firstDoseUnitCode?: string;
};

/** 列表行 → 目录 prepicked 键 */
export function prepickedKeyFromListRow(row: AdviceRowItem): PrepickedProductKeyDto | null {
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
export function buildAdviceDrugItemSeedFromListRow(row: AdviceRowItem): AdviceDrugItemSeed {
  const defId = String(row.productDefinitionId ?? '').trim();
  const tbl = String(row.productTableName ?? '').trim();
  const selectedId = defId && tbl ? `${tbl}:${defId}` : `fallback-${row.key}`;
  const rt = String(row.resourceType ?? '')
    .trim()
    .toUpperCase();
  const isService = rt === 'SERVICE' || tbl.toLowerCase() === 'wkf_activity_definition';
  const qty = row.listQuantity;
  const unitCode = String(row.listUnitCode ?? '').trim();
  const unitName = String(row.listUnitName ?? '').trim();
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
    speedCode: String(row.speedCode ?? '').trim() || undefined,
    // 列表 infusion_flag 可能未落库；有滴速码时仍展示滴速编辑
    injectFlag: row.injectionFlag === true || Boolean(String(row.speedCode ?? '').trim()),
    medicationDays: isInpatientLongTermMedicationTherapy(row.therapyTypeCode)
      ? undefined
      : positiveInt(row.dispensePerDuration),
    sortNumber: row.sortNumber,
    groupId: String(row.groupId ?? '').trim() || undefined,
    therapyEnum: String(row.therapyTypeCode ?? '').trim() || undefined,
    medicationRequestId: String(row.requestId ?? '').trim() || undefined,
    catalogCategoryCode:
      String(row.catalogCategoryCode ?? row.serviceCategoryEnum ?? '').trim() || undefined,
    priorityEnum: String(row.priorityEnum ?? '').trim() || undefined,
    entrustContent: String(row.entrustContent ?? '').trim() || undefined,
    remark: String(row.remark ?? '').trim() || undefined,
    listDose: row.listDose,
    listDoseUnitCode: row.listDoseUnitCode,
    // 分次用量不一致：列表 doseSchedule → merge 后展开多框（BR-IP-ORD-037）
    listDoseSchedule: String(row.listDoseSchedule ?? '').trim() || undefined,
    // 种子暂存库内最小单位量；merge 目录后再 hydrate 成计量展示
    firstDose: row.listFirstDose,
    firstDoseUnitCode: row.listFirstDoseUnitCode,
    listFirstDose: row.listFirstDose,
    listFirstDoseUnitCode: row.listFirstDoseUnitCode,
    ...(isService
      ? {
          serviceQuantity: qty ?? 1,
          // 临时：执行次数与数量分立；长期无执行次数时回落数量
          totalAmount: positiveInt(row.listExecutionCount) ?? qty ?? 1,
          totalAmountUnit: unitCode || '次',
          ...(unitCode
            ? {
                totalUnitSalesCode: unitCode,
                totalUnitSalesName: unitName || unitCode,
              }
            : {}),
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
    speedCode: seed.speedCode,
    injectFlag: seed.injectFlag === true,
    medicationDays: isInpatientLongTermMedicationTherapy(opts?.therapyTypeCode ?? seed.therapyEnum)
      ? undefined
      : seed.medicationDays,
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
    catalogCategoryCode: seed.catalogCategoryCode,
    priorityEnum: seed.priorityEnum,
    entrustContent: seed.entrustContent,
    remark: seed.remark,
  };

  if (tbl === 'wkf_activity_definition') {
    const performOrgId =
      String(seed.performOrgId ?? catalog.defaultPerformOrgId ?? '').trim() || undefined;
    const performOrgName =
      String(seed.performOrgName ?? catalog.defaultPerformOrgName ?? '').trim() || undefined;
    const uCode = String(seed.totalAmountUnit ?? catalog.defaultUnitCode ?? '').trim();
    const uName = String(seed.totalUnitSalesName ?? catalog.defaultUnitName ?? '').trim();
    return {
      ...base,
      ...preserved,
      ...pickCatalogChrgitmLvPatch(catalog),
      performOrgId,
      performOrgName,
      selectedDrugPrice: catalog.price ?? seed.selectedDrugPrice ?? '',
      catalogCategoryCode:
        String(catalog.categoryCode ?? seed.catalogCategoryCode ?? '').trim() || undefined,
      priorityEnum: String(seed.priorityEnum ?? '').trim() || undefined,
      ...(uCode
        ? {
            totalAmountUnit: uCode,
            totalUnitSalesCode: uCode,
            totalUnitSalesName: uName || uCode,
          }
        : {}),
    };
  }

  if (tbl === 'adm_device_definition') {
    const uCode = String(catalog.defaultUnitCode ?? seed.totalAmountUnit ?? '').trim();
    const uName = String(catalog.defaultUnitName ?? '').trim();
    return {
      ...base,
      ...preserved,
      ...pickCatalogChrgitmLvPatch(catalog),
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

  const unitBands = catalog.unitBands ?? catalogPatch.unitBands;
  const doseHydrated = hydrateAdviceRowDoseFromBackend({
    dose: seed.listDose,
    doseUnitCode: seed.listDoseUnitCode,
    doseUnitName: seed.doseSubUnit,
    unitBands,
  });

  const totalUnit = String(seed.totalAmountUnit ?? '').trim() || catalogPatch.totalAmountUnit;
  let selectedDrugPrice = catalogPatch.selectedDrugPrice ?? catalog.price ?? seed.selectedDrugPrice;
  if (totalUnit) {
    const linePrice = resolveAdviceLineUnitPrice({
      bands: unitBands,
      catalogUnitPrice: catalogPatch.catalogUnitPrice ?? catalog.catalogUnitPrice,
      totalAmountUnit: totalUnit,
    });
    if (linePrice !== undefined) selectedDrugPrice = linePrice;
  }

  // 库内首日=最小单位 → 编辑卡按计量单位展示（同写剂量）
  const persistedFirstDose = seed.firstDose ?? seed.listFirstDose;
  const persistedFirstDoseUnit = seed.firstDoseUnitCode ?? seed.listFirstDoseUnitCode;
  const firstDoseDisplay = hydrateFirstDoseDisplayQty({
    firstDose: persistedFirstDose,
    firstDoseUnitCode: persistedFirstDoseUnit,
    doseBandConvertK: doseHydrated.doseBandConvertK,
    unitBands,
  });
  // 分次 schedule → 多剂量框（计量串原样还原；BR-IP-ORD-037）
  const scheduleHydrated = hydrateDoseScheduleSlots({
    doseSchedule: seed.listDoseSchedule ?? (seed as { doseSchedule?: string }).doseSchedule,
  });

  return {
    ...catalogPatch,
    ...doseHydrated,
    ...base,
    ...preserved,
    firstDose: firstDoseDisplay,
    // 编辑卡内存不持落库单位；保存时再换算最小单位
    firstDoseUnitCode: undefined,
    unevenDoseEnabled: scheduleHydrated.unevenDoseEnabled,
    doseScheduleSlots: scheduleHydrated.doseScheduleSlots,
    totalAmountUnit: totalUnit,
    selectedDrugPrice,
  };
}
