import type { InpatientAdviceClipboardLine } from './inpatient-advice-clipboard';
import type {
  AdviceDrugItem,
  AdviceProductOption,
  AdviceRowItem,
  InpatientAdviceTherapyType,
} from './advice.types';

import type { AdviceRowItem as OutpatientAdviceRowItem } from '#/views/outpatient/doctor-station/advice/services/advice.types';

import { copyServiceApplyFormsAsNewDraft } from '#/views/outpatient/doctor-station/service-apply/service-apply.service';

import type {
  DoctorStationAdviceCatalogRowDto,
  PrepickedProductKeyDto,
  TerminologyOptionDto,
} from '#/api/doctor-station/advice.types';

import { httpPageInpatientDoctorStationAdviceCatalog } from '#/api/inpatient/doctor-station/inpatient-doctor-station-advice.http';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import {
  convertAdviceCatalogSplitQtyToPackageDisplay,
  formatAdviceCatalogStockCellText,
  isAdviceCatalogZeroStock,
  resolveAdviceCatalogPackageDisplayQty,
} from '#/utils/doctor-station/advice-catalog-stock-display';
import {
  convertInboundQtyBetweenBandKinds,
  normalizeInboundProductUnitBands,
} from '#/utils/inventory/inbound-product-unit-bands';

import { createAdviceDrugItem } from './advice-edit';
import {
  buildAdviceDrugItemSeedFromListRow,
  mergeCatalogIntoAdviceDrugItemForEdit,
} from './advice-edit-hydrate';
import { mapCatalogRowToOption } from './advice-list';
import { buildAdviceDraftLines } from './advice-medication-draft';
import { validateWesternEditLineItem } from './advice-row';
import { ADVICE_CATALOG_EXCLUDE_SERVICE_APPLY_CATEGORY_CODES } from '#/views/outpatient/doctor-station/advice/constants/advice-catalog-exclude-service-apply-categories.config';

import {
  INPATIENT_ADVICE_CATALOG_TABLE_NAMES,
  WESTERN_ADVICE_CATALOG_EXCLUDE_MEDICATION_CATEGORY_CODES,
} from './advice-tab.constants';
import { saveInpatientAdviceDraftBatchDirect } from './inpatient-advice-draft-save.service';
import { unitCodeByBandKind } from './medication-unit-bands';

export interface PasteInpatientAdviceClipboardParams {
  lines: InpatientAdviceClipboardLine[];
  encounterId: string;
  patientId: string;
  organizationUnitId?: string;
  feeNatureOptions: TerminologyOptionDto[];
  medicationRateOptions: TerminologyOptionDto[];
  medicationRouteOptions: TerminologyOptionDto[];
  /** 当前列表筛选；与粘贴行治疗类型不一致时仅提示，仍保存 */
  listTherapyFilter: '' | InpatientAdviceTherapyType;
}

export interface PasteInpatientAdviceClipboardResult {
  appliedLineCount: number;
  /** 筛选挡住了部分新草稿时的提示；空表示当前筛选能看到全部 */
  filterHint: string;
}

/** 粘贴失败（库存、目录、校验）；剪贴板由调用方保留 */
export class InpatientAdviceClipboardPasteError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'InpatientAdviceClipboardPasteError';
  }
}

/** 把剪贴板写成当前就诊的新草稿：换组号、费别用当前默认、不带诊断与源请求 */
export async function pasteInpatientAdviceClipboard(
  params: PasteInpatientAdviceClipboardParams,
): Promise<PasteInpatientAdviceClipboardResult> {
  const encounterId = params.encounterId.trim();
  const patientId = params.patientId.trim();
  if (!encounterId || !patientId) {
    throw new InpatientAdviceClipboardPasteError('请先选择患者');
  }
  if (params.lines.length === 0) {
    throw new InpatientAdviceClipboardPasteError('没有可粘贴的医嘱');
  }

  const applyLines = params.lines.filter((line) => String(line.sourceApplyFormNo ?? '').trim());
  const adviceLines = params.lines.filter((line) => !String(line.sourceApplyFormNo ?? '').trim());
  let appliedLineCount = 0;
  if (applyLines.length > 0) {
    appliedLineCount += await pasteServiceApplyClipboardLines({
      lines: applyLines,
      encounterId,
      patientId,
    });
  }
  if (adviceLines.length === 0) {
    return {
      appliedLineCount,
      filterHint: resolveFilterHint(params.lines, params.listTherapyFilter),
    };
  }

  const groupIdBySource = assignPasteGroupIds(adviceLines);
  const catalogList = await fetchCatalogForClipboard(adviceLines, {
    encounterId,
    patientId,
    organizationUnitId: params.organizationUnitId,
  });
  const catalogMap = new Map<string, AdviceProductOption>();
  for (const opt of catalogList) {
    const id = String(opt.productDefinitionId ?? '').trim();
    const tbl = String(opt.productTableName ?? '').trim();
    if (id && tbl) {
      catalogMap.set(`${tbl}:${id}`, opt);
    }
  }

  const ctx = {
    feeNatureOptions: params.feeNatureOptions,
    medicationRateOptions: params.medicationRateOptions,
    medicationRouteOptions: params.medicationRouteOptions,
  };
  const drugItems: AdviceDrugItem[] = [];
  const stockMessages: string[] = [];

  for (const line of adviceLines) {
    const catalog = catalogMap.get(`${line.productTableName}:${line.productDefinitionId}`);
    if (!catalog) {
      throw new InpatientAdviceClipboardPasteError(`「${line.displayName}」当前不可开立，无法粘贴`);
    }
    const stockMessage = resolveMedicationStockMessage(line, catalog);
    if (stockMessage) {
      stockMessages.push(stockMessage);
    }
    drugItems.push(buildPasteDrugItem(line, catalog, groupIdBySource, ctx));
  }

  if (stockMessages.length > 0) {
    throw new InpatientAdviceClipboardPasteError(`${stockMessages.join('；')}，无法粘贴`);
  }

  const invalid = drugItems.find((item) => !validateWesternEditLineItem(item, item.therapyEnum));
  if (invalid) {
    const name = String(invalid.selectedDrugName ?? '').trim() || '医嘱';
    throw new InpatientAdviceClipboardPasteError(`「${name}」参数不完整，无法粘贴`);
  }

  const draftLines = buildAdviceDraftLines(drugItems);
  const saveRes = await saveInpatientAdviceDraftBatchDirect({
    encounterId,
    patientId,
    lines: draftLines,
  });
  if (saveRes.success === false) {
    throw new InpatientAdviceClipboardPasteError(
      String(saveRes.message ?? '').trim() || '保存医嘱草稿失败',
    );
  }

  return {
    appliedLineCount: appliedLineCount + draftLines.length,
    filterHint: resolveFilterHint(params.lines, params.listTherapyFilter),
  };
}

/** 剪贴板中的申请单按源单号聚组，整张存成新草稿 */
async function pasteServiceApplyClipboardLines(params: {
  lines: InpatientAdviceClipboardLine[];
  encounterId: string;
  patientId: string;
}): Promise<number> {
  const grouped = new Map<string, InpatientAdviceClipboardLine[]>();
  for (const line of params.lines) {
    const formNo = String(line.sourceApplyFormNo ?? '').trim();
    if (!formNo) {
      continue;
    }
    const bucket = grouped.get(formNo);
    if (bucket) {
      bucket.push(line);
    } else {
      grouped.set(formNo, [line]);
    }
  }
  const forms = [...grouped.values()].map((lines) => ({
    lines: lines.map((line, index) => clipboardApplyLineToAdviceRow(line, index)),
  }));
  try {
    await copyServiceApplyFormsAsNewDraft({
      encounterId: params.encounterId,
      patientId: params.patientId,
      stationScope: 'inpatient',
      forms,
    });
    return params.lines.length;
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : '复制申请单失败';
    throw new InpatientAdviceClipboardPasteError(message);
  }
}

/** 申请单剪贴板行还原成保存所需的列表行形状 */
function clipboardApplyLineToAdviceRow(
  line: InpatientAdviceClipboardLine,
  index: number,
): OutpatientAdviceRowItem {
  return {
    key: `clip-apply-${line.sourceApplyFormNo}-${index}`,
    status: '',
    type: '',
    content: line.displayName,
    startTime: '',
    extension: '',
    lineTotalAmount: '',
    diagnosisSummary: '',
    orderingDoctor: '',
    skinTestText: '',
    productDefinitionId: line.productDefinitionId,
    catalogCategoryCode: line.catalogCategoryCode,
    listQuantity: line.listQuantity,
    listUnitCode: line.listUnitCode,
    listExecutionCount: line.listExecutionCount,
    remark: line.remark,
    performOrgId: line.performOrgId,
    serviceRequestDetails: line.serviceRequestDetails,
    encounterDiagnosisIds: line.encounterDiagnosisIds,
  };
}

/** 同一源组换成新的数字组号，避免并进源患者的组 */
function assignPasteGroupIds(lines: InpatientAdviceClipboardLine[]): Map<string, string> {
  const map = new Map<string, string>();
  let seq = 0;
  const stamp = Date.now();
  for (const line of lines) {
    const source = line.sourceGroupKey;
    if (!source || map.has(source)) {
      continue;
    }
    seq += 1;
    map.set(source, `${stamp}${String(seq).padStart(2, '0')}`);
  }
  return map;
}

/** 剪贴板行 + 目标目录 → 新建草稿行（诊断空、费别取当前默认） */
function buildPasteDrugItem(
  line: InpatientAdviceClipboardLine,
  catalog: AdviceProductOption,
  groupIdBySource: Map<string, string>,
  ctx: {
    feeNatureOptions: TerminologyOptionDto[];
    medicationRateOptions: TerminologyOptionDto[];
    medicationRouteOptions: TerminologyOptionDto[];
  },
): AdviceDrugItem {
  const therapy = line.therapyTypeCode || undefined;
  const defaults = createAdviceDrugItem({ therapyEnum: therapy, diagnosis: '' }, ctx);
  const seedRow = clipboardLineToSeedRow(line, groupIdBySource.get(line.sourceGroupKey));
  const seed = buildAdviceDrugItemSeedFromListRow(seedRow);
  const merged = mergeCatalogIntoAdviceDrugItemForEdit(
    {
      ...seed,
      diagnosis: '',
      medicationRequestId: undefined,
      sortNumber: undefined,
      contractId: defaults.contractId,
      feeNature: defaults.feeNature,
      groupId: groupIdBySource.get(line.sourceGroupKey),
    },
    catalog,
    { therapyTypeCode: therapy },
  );
  return {
    ...defaults,
    ...merged,
    diagnosis: '',
    medicationRequestId: undefined,
    contractId: defaults.contractId,
    feeNature: defaults.feeNature,
    expanded: false,
  };
}

/** 剪贴板行还原成列表种子所需的行形状 */
function clipboardLineToSeedRow(
  line: InpatientAdviceClipboardLine,
  groupId: string | undefined,
): AdviceRowItem {
  return {
    key: `clip-${line.productTableName}-${line.productDefinitionId}`,
    status: '',
    type: '',
    content: line.displayName,
    startTime: '',
    extension: '',
    lineTotalAmount: '',
    diagnosisSummary: '',
    orderingDoctor: '',
    skinTestText: '',
    adviceDisplayName: line.displayName,
    productDefinitionId: line.productDefinitionId,
    productTableName: line.productTableName,
    resourceType: line.resourceType,
    therapyTypeCode: line.therapyTypeCode,
    rateCode: line.rateCode,
    methodCode: line.methodCode,
    speedCode: line.speedCode,
    skinTestFlag: line.skinTestFlag,
    injectionFlag: line.injectionFlag,
    remark: line.remark,
    entrustContent: line.entrustContent,
    catalogCategoryCode: line.catalogCategoryCode,
    listDose: line.listDose,
    listDoseUnitCode: line.listDoseUnitCode,
    listFirstDose: line.listFirstDose,
    listFirstDoseUnitCode: line.listFirstDoseUnitCode,
    listDoseSchedule: line.listDoseSchedule,
    listQuantity: line.listQuantity,
    listUnitCode: line.listUnitCode,
    listUnitName: line.listUnitName,
    listExecutionCount: line.listExecutionCount,
    dispensePerDuration: line.dispensePerDuration,
    performOrgId: line.performOrgId,
    performOrgName: line.performOrgName,
    groupId,
  };
}

/** 药品零库存或需求量超过可开库存时返回文案 */
function resolveMedicationStockMessage(
  line: InpatientAdviceClipboardLine,
  catalog: AdviceProductOption,
): string | null {
  const tbl = String(line.productTableName ?? '')
    .trim()
    .toLowerCase();
  if (tbl !== 'med_medication_definition') {
    return null;
  }
  const productName = line.displayName || catalog.name || '未命名药品';
  if (isAdviceCatalogZeroStock(catalog)) {
    return `药品「${productName}」暂无库存`;
  }
  const requiredQty = Number(line.listQuantity ?? 1);
  const requiredUnitCode = String(line.listUnitCode ?? '').trim();
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
    const availableText = formatAdviceCatalogStockCellText(catalog);
    return `药品「${productName}」库存不足（需要 ${requiredQty}${requiredUnitCode}，当前 ${availableText}）`;
  }
  return null;
}

/** 需求量换到包装口径，便于和目录库存比较 */
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
  const hit = (unitBands ?? []).find((band) => String(band.unitCode ?? '').trim() === unitCode);
  const kind = String(hit?.bandKind ?? '')
    .trim()
    .toUpperCase();
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

/** 按剪贴板项目回查当前患者可开目录 */
async function fetchCatalogForClipboard(
  lines: InpatientAdviceClipboardLine[],
  params: { encounterId: string; organizationUnitId?: string; patientId: string },
): Promise<AdviceProductOption[]> {
  const keys: PrepickedProductKeyDto[] = lines.map((line) => ({
    productDefinitionId: line.productDefinitionId,
    productTableName: line.productTableName,
  }));
  const tableNames = [
    ...new Set([
      ...INPATIENT_ADVICE_CATALOG_TABLE_NAMES,
      ...lines.map((line) => line.productTableName),
    ]),
  ];
  const res = await httpPageInpatientDoctorStationAdviceCatalog({
    encounterId: params.encounterId,
    patientId: params.patientId,
    organizationUnitId: params.organizationUnitId,
    pageNo: 1,
    pageSize: Math.max(keys.length, 20),
    prepickedProductKeys: keys,
    productTableNames: tableNames,
    excludeMedicationCategoryCodes: [...WESTERN_ADVICE_CATALOG_EXCLUDE_MEDICATION_CATEGORY_CODES],
    excludeCategoryCodes: [...ADVICE_CATALOG_EXCLUDE_SERVICE_APPLY_CATEGORY_CODES],
  });
  const { content: rawRows } = parsePagedRowsFromApiData<DoctorStationAdviceCatalogRowDto>(res);
  return rawRows.map((item) => mapCatalogRowToOption(item));
}

/** 当前筛选看不到的治疗类型，提示切到全部 */
function resolveFilterHint(
  lines: InpatientAdviceClipboardLine[],
  filter: '' | InpatientAdviceTherapyType,
): string {
  if (!filter) {
    return '';
  }
  const hidden = lines.some((line) => line.therapyTypeCode && line.therapyTypeCode !== filter);
  if (!hidden) {
    return '';
  }
  return '部分医嘱的长期/临时与当前筛选不一致，请切换到「全部」查看';
}
