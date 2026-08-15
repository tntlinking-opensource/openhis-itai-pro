import type { AdviceDrugItem, AdviceProductOption, AdviceRowItem } from './advice.types';

import type {
  DoctorStationAdviceCatalogRowDto,
  DoctorStationAdviceRowDto,
  TerminologyOptionDto,
} from '#/api/doctor-station/advice.types';
import type { DrugGroupDisplayItem, DrugGroupKey } from '#/utils/doctor-station/drug-group-table';

import { formatDateTime } from '@vben/utils';

import {
  resolveAdviceChargeStatusTagTypeFromCode,
  resolveAdviceStatusTagTypeFromCode,
  resolveSkinTestTagTypeFromCode,
} from '#/utils/advice/advice-status-display';
import { resolveAdviceQuantityUnitLabel } from '#/utils/doctor-station/advice-quantity-unit-display';
import { mergeConsecutiveRowsByDrugGroup } from '#/utils/doctor-station/drug-group-table';
import { formatMedicationDoseSummaryLine } from '#/utils/doctor-station/medication-dose-display';
import { convertProductUnitPriceForDisplay } from '#/utils/doctor-station/product-unit-band-price';
import { decimalAdd } from '#/utils/format/decimal-util';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { buildServiceApplyFormExtensionText } from '#/utils/service-apply-display';
import {
  resolveServiceApplyFormNo,
  resolveServiceApplyFormSortKey,
} from '#/utils/service-apply-form-group-display';

import { formatUnitPriceYuan, terminologyLabel } from './advice-format';
import { normalizeCatalogUnitBands } from './medication-unit-bands';

/** 医嘱列表映射、成组合并、目录选品、库存展示、旧行回填 */

/** 与主数据「皮试检查」诊疗项目名称一致 */
const SKIN_TEST_INSPECTION_ACTIVITY_NAME = '皮试检查';

function resourceTypeToTypeLabel(resourceType: string): string {
  const rt = resourceType.toUpperCase();
  if (rt === 'MEDICATION') return '药品';
  if (rt === 'SERVICE') return '诊疗';
  if (rt === 'DEVICE') return '耗材';
  return resourceType || '医嘱';
}

export function inferAdviceRowResourceType(dto: DoctorStationAdviceRowDto): string {
  const raw = String(dto.resourceType ?? '').trim();
  if (raw) return raw.toUpperCase();
  const tbl = String(dto.productTableName ?? '')
    .trim()
    .toLowerCase();
  if (tbl.includes('medication') || tbl.includes('med_')) return 'MEDICATION';
  if (tbl.includes('activity') || tbl.includes('service') || tbl.includes('wkf_')) {
    return 'SERVICE';
  }
  if (tbl.includes('device') || tbl.includes('adm_')) return 'DEVICE';
  const cat = String(dto.catalogCategoryCode ?? '').trim();
  if (cat === '11') return 'DEVICE';
  if (cat === '21' || cat.startsWith('21')) return 'SERVICE';
  if (cat === '2' || cat === '3' || cat === '4') return 'MEDICATION';
  return '';
}

function buildAdviceContentTitle(dto: DoctorStationAdviceRowDto): string {
  const name = String(dto.adviceDisplayName ?? dto.contentSummary ?? '').trim();
  const spec = String(dto.specificationText ?? '').trim();
  if (name && spec) return `${name}【${spec}】`;
  return name || spec;
}

/** 判断是否为后端联动的「皮试检查」诊疗行 */
function isSkinTestInspectionServiceRow(dto: DoctorStationAdviceRowDto): boolean {
  if (inferAdviceRowResourceType(dto) !== 'SERVICE') return false;
  const name = String(dto.adviceDisplayName ?? dto.contentSummary ?? '').trim();
  if (!name) return false;
  if (name === SKIN_TEST_INSPECTION_ACTIVITY_NAME) return true;
  return name.startsWith(`${SKIN_TEST_INSPECTION_ACTIVITY_NAME}(`);
}

/** 解析皮试检查行锚定的皮试药 requestId（优先 API 字段，缺省启发式配对） */
function resolveSkinTestCompanionMedicationRequestId(
  dto: DoctorStationAdviceRowDto,
  heuristicByServiceRequestId: ReadonlyMap<string, string>,
): string {
  const fromApi = preserveApiIdForRequest(dto.basedOnMedicationRequestId);
  if (fromApi) return fromApi;
  const serviceRequestId = preserveApiIdForRequest(dto.requestId);
  if (!serviceRequestId) return '';
  return heuristicByServiceRequestId.get(serviceRequestId) ?? '';
}

/** 按开立时间与 requestId 稳定排序，供皮试药与 companion 启发式配对 */
function compareAdviceRowOpenedOrder(
  a: { reqOpenedTime: string; requestId: string },
  b: { reqOpenedTime: string; requestId: string },
): number {
  const openedCmp = a.reqOpenedTime.localeCompare(b.reqOpenedTime);
  if (openedCmp !== 0) return openedCmp;
  return a.requestId.localeCompare(b.requestId);
}

/**
 * 无 basedOnMedicationRequestId 时：皮试药与「皮试检查」按开立时间一一配对（同时间优先）。
 */
function buildSkinTestCompanionHeuristicMap(
  rows: readonly DoctorStationAdviceRowDto[],
): Map<string, string> {
  const result = new Map<string, string>();
  const usedMedicationRequestIds = new Set<string>();

  const skinTestMedications = rows
    .filter((d) => inferAdviceRowResourceType(d) === 'MEDICATION' && d.skinTestFlag === true)
    .map((d) => ({
      requestId: preserveApiIdForRequest(d.requestId) ?? '',
      reqOpenedTime: String(d.reqOpenedTime ?? '').trim(),
    }))
    .filter((item) => item.requestId !== '');

  const companions = rows
    .filter(
      (d) =>
        isSkinTestInspectionServiceRow(d) && !preserveApiIdForRequest(d.basedOnMedicationRequestId),
    )
    .map((d) => ({
      requestId: preserveApiIdForRequest(d.requestId) ?? '',
      reqOpenedTime: String(d.reqOpenedTime ?? '').trim(),
    }))
    .filter((item) => item.requestId !== '');

  // 第一遍：开立时间完全一致时配对
  for (const companion of companions) {
    const hit = skinTestMedications.find(
      (med) =>
        !usedMedicationRequestIds.has(med.requestId) &&
        med.reqOpenedTime !== '' &&
        med.reqOpenedTime === companion.reqOpenedTime,
    );
    if (!hit) continue;
    result.set(companion.requestId, hit.requestId);
    usedMedicationRequestIds.add(hit.requestId);
  }

  // 第二遍：剩余按时间序一一配对
  const remainingMedications = skinTestMedications
    .filter((med) => !usedMedicationRequestIds.has(med.requestId))
    .toSorted(compareAdviceRowOpenedOrder);
  const remainingCompanions = companions
    .filter((comp) => !result.has(comp.requestId))
    .toSorted(compareAdviceRowOpenedOrder);
  const pairCount = Math.min(remainingMedications.length, remainingCompanions.length);
  for (let i = 0; i < pairCount; i += 1) {
    const med = remainingMedications[i];
    const comp = remainingCompanions[i];
    if (!med || !comp) continue;
    result.set(comp.requestId, med.requestId);
  }

  return result;
}

/** 皮试检查行医嘱内容追加来源药品名：皮试检查(药品名) */
function enrichSkinTestCompanionDisplayNames(
  rows: readonly DoctorStationAdviceRowDto[],
): DoctorStationAdviceRowDto[] {
  const medicationNameByRequestId = new Map<string, string>();
  for (const dto of rows) {
    if (inferAdviceRowResourceType(dto) !== 'MEDICATION') continue;
    const requestId = preserveApiIdForRequest(dto.requestId);
    if (!requestId) continue;
    const name = String(dto.adviceDisplayName ?? dto.contentSummary ?? '').trim();
    if (name) medicationNameByRequestId.set(requestId, name);
  }

  const heuristicMap = buildSkinTestCompanionHeuristicMap(rows);

  return rows.map((dto) => {
    if (!isSkinTestInspectionServiceRow(dto)) return dto;

    const medicationRequestId = resolveSkinTestCompanionMedicationRequestId(dto, heuristicMap);
    if (!medicationRequestId) return dto;

    const medicationName = medicationNameByRequestId.get(medicationRequestId);
    if (!medicationName) return dto;

    const baseName = String(
      dto.adviceDisplayName ?? dto.contentSummary ?? SKIN_TEST_INSPECTION_ACTIVITY_NAME,
    ).trim();
    const suffix = `${SKIN_TEST_INSPECTION_ACTIVITY_NAME}(${medicationName})`;
    if (baseName === suffix || baseName.includes(`(${medicationName})`)) return dto;

    return {
      ...dto,
      adviceDisplayName: suffix,
      contentSummary: suffix,
    };
  });
}

function buildAdviceDosageSummaryLine(dto: DoctorStationAdviceRowDto): string {
  if (inferAdviceRowResourceType(dto) === 'SERVICE') {
    return buildServiceAdviceDosageSummaryLine(dto);
  }
  return formatMedicationDoseSummaryLine({
    dosageDisplay: dto.dosageDisplay,
    dose: dto.dose,
    doseUnitCode: dto.doseUnitCode,
    doseUnitName: dto.doseUnitName,
    splitDose: dto.splitDose,
    splitDoseUnitCode: dto.splitDoseUnitCode,
    splitDoseUnitName: dto.splitDoseUnitName,
    quantity: dto.quantity,
    unitCode: dto.unitCode,
    unitName: dto.unitName,
    resolveTotalUnitLabel: resolveAdviceQuantityUnitLabel,
  });
}

/** 诊疗行剂量摘要：数量 + 单位 + 执行次数（与编辑卡收起态口径一致） */
function buildServiceAdviceDosageSummaryLine(dto: DoctorStationAdviceRowDto): string {
  const qtyRaw = dto.quantity;
  const execRaw = dto.executionCount;
  const unit = resolveAdviceQuantityUnitLabel(dto.unitName, dto.unitCode);
  const qtyPart =
    qtyRaw !== null && qtyRaw !== undefined && String(qtyRaw).trim() !== ''
      ? `数量 ${qtyRaw}${unit}`
      : '';
  const execPart =
    execRaw !== null && execRaw !== undefined && String(execRaw).trim() !== ''
      ? `执行 ${execRaw} 次`
      : '';
  const parts = [qtyPart, execPart].filter(Boolean);
  return parts.length > 0 ? `${parts.join('，')}。` : '';
}

function buildAdviceMethodRateDurationLine(dto: DoctorStationAdviceRowDto): string {
  const m = String(dto.methodName ?? '').trim();
  const r = String(dto.rateName ?? '').trim();
  const durRaw = dto.dispensePerDuration;
  let dayPart = '';
  if (durRaw !== null && durRaw !== undefined && String(durRaw).trim() !== '') {
    const d = Number(durRaw);
    if (Number.isFinite(d) && d > 0) dayPart = ` (${Math.trunc(d)}天)`;
  }
  if (m && r) return `${m}, ${r}${dayPart}`;
  if (m) return `${m}${dayPart}`;
  if (r) return `${r}${dayPart}`;
  return dayPart.trim();
}

/** 医嘱内容 extension：申请单样本/部位/备注 + 用法频次天数 + 诊疗执行科室 */
function buildAdviceListExtensionLine(dto: DoctorStationAdviceRowDto): string {
  const usageLine = buildAdviceMethodRateDurationLine(dto);
  const extra: string[] = usageLine ? [usageLine] : [];
  if (inferAdviceRowResourceType(dto) === 'SERVICE') {
    const dept = String(dto.performLocationName ?? '').trim();
    if (dept) {
      extra.push(dept);
    }
  }
  return buildServiceApplyFormExtensionText(
    {
      remark: dto.remark,
      serviceRequestDetails: dto.serviceRequestDetails,
    },
    extra,
  );
}

function formatAdviceRowTotalPrice(dto: DoctorStationAdviceRowDto): string {
  const v = dto.lineDisplayTotalPrice;
  if (v !== null && v !== undefined && String(v).trim() !== '') {
    return formatUnitPriceYuan(v);
  }
  return formatUnitPriceYuan(dto.lineTotalAmount);
}

function normalizedListSortNumber(
  raw: DoctorStationAdviceRowDto['sortNumber'],
): number | undefined {
  if (raw === null || raw === undefined || raw === '') return undefined;
  const n = typeof raw === 'number' ? raw : Number(raw);
  return Number.isFinite(n) ? Math.trunc(n) : undefined;
}

function skinTestDisplayText(dto: DoctorStationAdviceRowDto): string {
  const outcome = String(dto.skinTestOutcomeText ?? '').trim();
  if (outcome) return outcome;
  const t = String(dto.skinTestFlagText ?? '').trim();
  if (t) return t;
  if (dto.skinTestFlag === true) return '是';
  if (dto.skinTestFlag === false) return '否';
  return '—';
}

export function mapAdviceListRowDto(dto: DoctorStationAdviceRowDto): AdviceRowItem {
  const resourceType = inferAdviceRowResourceType(dto);
  const requestId = String(dto.requestId ?? '').trim();
  const listKey = String(dto.listRowKey ?? '').trim();
  const key =
    listKey ||
    (requestId && resourceType ? `api-${resourceType}-${requestId}` : `api-row-${Date.now()}`);
  // 签发态与费用态分列展示，不再使用后端合并的 displayStatus*
  const status =
    String(dto.statusEnumText ?? '').trim() || String(dto.statusEnum ?? '').trim() || '—';
  const chargeStatusCode = String(dto.chargeStatusEnum ?? '')
    .trim()
    .toUpperCase();
  // 草稿未进入收费窗口：列表不展示「草稿」，统一显示 —
  const chargeStatus =
    chargeStatusCode === 'DRAFT'
      ? '—'
      : String(dto.chargeStatusEnumText ?? '').trim() ||
        String(dto.chargeStatusEnum ?? '').trim() ||
        '—';
  const openedRaw = String(dto.reqOpenedTime ?? '').trim();
  const startTime = openedRaw ? formatDateTime(openedRaw) : '';
  const content = buildAdviceContentTitle(dto);
  const dosageLine = buildAdviceDosageSummaryLine(dto);
  const extension = buildAdviceListExtensionLine(dto);
  const typeLabel =
    String(dto.catalogCategoryName ?? '').trim() || resourceTypeToTypeLabel(resourceType);

  return {
    key,
    status,
    chargeStatus,
    type: typeLabel,
    content: content || String(dto.contentSummary ?? '').trim(),
    startTime,
    extension,
    lineTotalAmount: formatAdviceRowTotalPrice(dto),
    contractId: String(dto.contractId ?? '').trim() || undefined,
    feeNatureText: String(dto.feeNatureText ?? '').trim() || undefined,
    diagnosisSummary:
      (
        dto.conditionDefinitionName ||
        dto.boundConditionName ||
        dto.diagnosisSummary ||
        ''
      ).trim() || '—',
    orderingDoctor: (dto.authoredByName || '').trim() || '—',
    skinTestText: skinTestDisplayText(dto),
    skinTestOutcomeCode: String(dto.skinTestOutcomeCode ?? '').trim() || undefined,
    dosageText: dosageLine || undefined,
    productTableName: String(dto.productTableName ?? '').trim() || undefined,
    sortNumber: normalizedListSortNumber(dto.sortNumber),
    skinTestFlag: dto.skinTestFlag,
    injectionFlag: dto.injectionFlag === true,
    resourceType,
    requestId,
    groupId: String(dto.groupId ?? '').trim() || undefined,
    packageId: String(dto.packageId ?? '').trim() || undefined,
    statusEnum: dto.statusEnum,
    chargeStatusEnum: dto.chargeStatusEnum,
    productDefinitionId: String(dto.productDefinitionId ?? '').trim() || undefined,
    adviceDisplayName: String(dto.adviceDisplayName ?? '').trim() || undefined,
    specificationText: String(dto.specificationText ?? '').trim() || undefined,
    conditionDefinitionName: String(dto.conditionDefinitionName ?? '').trim() || undefined,
    encounterDiagnosisIds: String(dto.encounterDiagnosisIds ?? '').trim() || undefined,
    methodCode: String(dto.methodCode ?? '').trim() || undefined,
    rateCode: String(dto.rateCode ?? '').trim() || undefined,
    listDose: (() => {
      const n = positiveListNumber(dto.dose);
      return n;
    })(),
    listDoseUnitCode: String(dto.doseUnitCode ?? '').trim() || undefined,
    listUnitCode: String(dto.unitCode ?? '').trim() || undefined,
    listUnitName: String(dto.unitName ?? '').trim() || undefined,
    dispensePerDuration: (() => {
      const raw = dto.dispensePerDuration;
      if (raw === null || raw === undefined || String(raw).trim() === '') return undefined;
      const n = Number(raw);
      return Number.isFinite(n) && n > 0 ? Math.trunc(n) : undefined;
    })(),
    listQuantity: (() => {
      const q = dto.quantity;
      if (q === null || q === undefined || String(q).trim() === '') return undefined;
      const n = typeof q === 'number' ? q : Number(q);
      return Number.isFinite(n) && n > 0 ? n : undefined;
    })(),
    listExecutionCount: (() => {
      const raw = dto.executionCount;
      if (raw === null || raw === undefined || String(raw).trim() === '') return undefined;
      const n = typeof raw === 'number' ? raw : Number(raw);
      return Number.isFinite(n) && n > 0 ? Math.trunc(n) : undefined;
    })(),
    performOrgId: String(dto.performLocationId ?? '').trim() || undefined,
    performOrgName: String(dto.performLocationName ?? '').trim() || undefined,
    basedOnMedicationRequestId:
      preserveApiIdForRequest(dto.basedOnMedicationRequestId) || undefined,
    basedOnServiceRequestId: preserveApiIdForRequest(dto.basedOnServiceRequestId) || undefined,
    applyFormNo: resolveServiceApplyFormNo(dto.prescriptionNo),
    remark: String(dto.remark ?? '').trim() || undefined,
    serviceRequestDetails: dto.serviceRequestDetails ?? undefined,
    catalogCategoryCode:
      String(dto.catalogCategoryCode ?? dto.categoryCode ?? '').trim() || undefined,
    ...(String(dto.chrgitmLvText ?? '').trim()
      ? { chrgitmLvText: String(dto.chrgitmLvText).trim() }
      : {}),
  };
}

function positiveListNumber(raw: unknown): number | undefined {
  if (raw === null || raw === undefined || String(raw).trim() === '') return undefined;
  const n = typeof raw === 'number' ? raw : Number(raw);
  return Number.isFinite(n) && n > 0 ? n : undefined;
}

export function mapCatalogRowToOption(row: DoctorStationAdviceCatalogRowDto): AdviceProductOption {
  const defId = String(row.productDefinitionId ?? '').trim();
  const tbl = String(row.productTableName ?? '').trim();
  const advice = String(row.adviceType ?? '')
    .trim()
    .toUpperCase();
  const categoryFallback =
    advice === 'MED' ? '药品' : advice === 'SRV' ? '诊疗' : advice === 'DEV' ? '耗材' : advice;
  const categoryLabel = String(row.categoryCodeDictText ?? '').trim() || categoryFallback;
  const id = tbl && defId ? `${tbl}:${defId}` : defId || tbl || `row-${Date.now()}`;
  const specText =
    String(row.totalVolume ?? '').trim() ||
    String(row.unitName ?? '').trim() ||
    String(row.unitCode ?? '').trim();
  const unitBands = normalizeCatalogUnitBands(row.unitBands, row.unitBandsJson);
  const catalogUnitPriceRaw = row.unitPrice;
  let catalogUnitPrice: number | undefined;
  if (
    catalogUnitPriceRaw !== null &&
    catalogUnitPriceRaw !== undefined &&
    String(catalogUnitPriceRaw).trim() !== ''
  ) {
    const cp = Number(catalogUnitPriceRaw);
    if (Number.isFinite(cp)) catalogUnitPrice = cp;
  }
  const price = convertProductUnitPriceForDisplay({
    unitPrice: row.unitPrice,
    unitBands,
    unitBandsJson: row.unitBandsJson,
  });
  const orgIdRaw = row.defaultPerformOrgId;
  const orgId =
    orgIdRaw !== null && orgIdRaw !== undefined && String(orgIdRaw).trim() !== ''
      ? String(orgIdRaw).trim()
      : undefined;
  const orgName = String(row.defaultPerformOrgName ?? '').trim() || undefined;
  const uCode = String(row.unitCode ?? '').trim() || undefined;
  const uName = String(row.unitName ?? '').trim() || undefined;

  return {
    id,
    code: row.busNo || '',
    name: row.name || '',
    // 商品名始终回填，空值由选品表展示为「—」
    merchandiseName: String(row.merchandiseName ?? '').trim(),
    category: categoryLabel,
    spec: specText,
    price,
    catalogUnitPrice,
    availableQuantity: row.availableQuantity,
    ...(String(row.availableQuantityText ?? '').trim()
      ? { availableQuantityText: String(row.availableQuantityText).trim() }
      : {}),
    // 厂家列始终回填，空值由选品表展示为「—」
    manufacturer: String(row.manufacturer ?? '').trim(),
    ...(Array.isArray(row.inventoryList) && row.inventoryList.length > 0
      ? { inventoryList: row.inventoryList }
      : {}),
    unitName: uName,
    rateCode: row.rateCode ? String(row.rateCode).trim() : undefined,
    methodCode: row.methodCode ? String(row.methodCode).trim() : undefined,
    productDefinitionId: defId,
    productTableName: tbl,
    ...(unitBands.length > 0 ? { unitBands } : {}),
    ...(orgId ? { defaultPerformOrgId: orgId } : {}),
    ...(orgName ? { defaultPerformOrgName: orgName } : {}),
    ...(uCode ? { defaultUnitCode: uCode } : {}),
    ...(uName ? { defaultUnitName: uName } : {}),
    ...(String(row.partAttributeEnum ?? '').trim()
      ? { partAttributeEnum: String(row.partAttributeEnum).trim() }
      : {}),
    ...pickPositiveCatalogDoseField(row.dose, 'dose'),
    ...pickPositiveCatalogDoseField(row.minUnit, 'minUnit'),
    ...pickPositiveCatalogDoseField(row.maxUnit, 'maxUnit'),
    ...(row.chrgitmLv !== null && row.chrgitmLv !== undefined && String(row.chrgitmLv).trim() !== ''
      ? { chrgitmLv: row.chrgitmLv }
      : {}),
    ...(String(row.chrgitmLvText ?? '').trim()
      ? { chrgitmLvText: String(row.chrgitmLvText).trim() }
      : {}),
    ...(row.injectFlag === true ? { injectFlag: true } : {}),
    ...(String(row.dosageInstruction ?? '').trim()
      ? { dosageInstruction: String(row.dosageInstruction).trim() }
      : {}),
    ...(String(row.dosageInstructionDictText ?? '').trim()
      ? { dosageInstructionDictText: String(row.dosageInstructionDictText).trim() }
      : {}),
  };
}

/** 目录剂量字段：仅透出有限正数，避免 0/空串污染选品默认值。 */
function pickPositiveCatalogDoseField(
  raw: null | number | string | undefined,
  key: 'dose' | 'maxUnit' | 'minUnit',
): Partial<Pick<AdviceProductOption, 'dose' | 'maxUnit' | 'minUnit'>> {
  if (raw === null || raw === undefined || String(raw).trim() === '') {
    return {};
  }
  const n = Number(raw);
  if (!Number.isFinite(n) || n <= 0) {
    return {};
  }
  return { [key]: n };
}

export function getAdviceStatusTagType(
  row: AdviceRowItem,
): 'danger' | 'info' | 'primary' | 'success' | 'warning' {
  return resolveAdviceStatusTagTypeFromCode(row.statusEnum);
}

/** 费用状态列 Tag 类型：仅认 chargeStatusEnum */
export function getAdviceChargeStatusTagType(
  row: Pick<AdviceRowItem, 'chargeStatusEnum'>,
): 'danger' | 'info' | 'primary' | 'success' | 'warning' {
  return resolveAdviceChargeStatusTagTypeFromCode(row.chargeStatusEnum);
}

/** 皮试列 Tag 类型：仅认 skinTestOutcomeCode / skinTestFlag */
export function getSkinTestDisplayTagType(
  row: Pick<AdviceRowItem, 'skinTestFlag' | 'skinTestOutcomeCode' | 'skinTestText'>,
): 'danger' | 'info' | 'primary' | 'success' | 'warning' | null {
  return resolveSkinTestTagTypeFromCode(row.skinTestOutcomeCode, row.skinTestFlag);
}

// —— 成组合并 ——

/** 仅保留检验/检查申请单（AR 前缀 prescriptionNo）行 */
export function filterServiceApplyFormAdviceRows(rows: readonly AdviceRowItem[]): AdviceRowItem[] {
  return rows.filter((row) => Boolean(row.applyFormNo?.trim()));
}

/** 排除检验/检查申请单行（供「开立医嘱」列表展示） */
export function excludeServiceApplyFormAdviceRows(rows: readonly AdviceRowItem[]): AdviceRowItem[] {
  return rows.filter((row) => !row.applyFormNo?.trim());
}

export function flattenAdviceRowsForResourceOps(rows: AdviceRowItem[]): AdviceRowItem[] {
  const out: AdviceRowItem[] = [];
  for (const r of rows) {
    const members = r.mergedAdviceRows;
    if (members !== undefined && members.length > 0) {
      out.push(...members);
    } else {
      out.push(r);
    }
  }
  return out;
}

/**
 * 列表排序：待签发置顶 → 申请单号聚邻 → 有效 sortNumber 升序。
 * 后端已排序；前端二次排序用于成组合并前的稳定聚邻，并补全派生行继承锚定药品 sortNumber。
 */
function sortDoctorStationAdviceRowsStable(
  rows: readonly DoctorStationAdviceRowDto[],
): DoctorStationAdviceRowDto[] {
  const medicationSortByRequestId = buildMedicationSortNumberByRequestId(rows);
  return [...rows].toSorted((a, b) => {
    const draftCmp = Number(isAdviceRowPendingSign(b)) - Number(isAdviceRowPendingSign(a));
    if (draftCmp !== 0) {
      return draftCmp;
    }

    const leftFormKey = resolveServiceApplyFormSortKey(a.prescriptionNo);
    const rightFormKey = resolveServiceApplyFormSortKey(b.prescriptionNo);
    const formCompare = leftFormKey.localeCompare(rightFormKey);
    if (formCompare !== 0) {
      return formCompare;
    }

    const sa = resolveEffectiveSortSeq(a, medicationSortByRequestId);
    const sb = resolveEffectiveSortSeq(b, medicationSortByRequestId);
    return sa - sb;
  });
}

/** 待签发：statusEnum = DRAFT（仅用 code，不用展示文案） */
function isAdviceRowPendingSign(dto: DoctorStationAdviceRowDto): boolean {
  return (
    String(dto.statusEnum ?? '')
      .trim()
      .toUpperCase() === 'DRAFT'
  );
}

function dtoSortSeq(raw: DoctorStationAdviceRowDto['sortNumber']): number {
  if (raw === null || raw === undefined || raw === '') return Number.MAX_SAFE_INTEGER;
  const n = typeof raw === 'number' ? raw : Number(raw);
  return Number.isFinite(n) ? n : Number.MAX_SAFE_INTEGER;
}

/** 药品 requestId → sortNumber，供锚定派生行继承排序 */
function buildMedicationSortNumberByRequestId(
  rows: readonly DoctorStationAdviceRowDto[],
): Map<string, number> {
  const map = new Map<string, number>();
  for (const dto of rows) {
    if (inferAdviceRowResourceType(dto) !== 'MEDICATION') {
      continue;
    }
    const requestId = preserveApiIdForRequest(dto.requestId);
    if (!requestId) {
      continue;
    }
    const sn = dtoSortSeq(dto.sortNumber);
    if (sn !== Number.MAX_SAFE_INTEGER) {
      map.set(requestId, sn);
    }
  }
  return map;
}

/**
 * 列表有效排序号：自身 sortNumber 优先；缺省时继承 basedOnMedicationRequestId 锚定药品的 sortNumber。
 */
function resolveEffectiveSortSeq(
  dto: DoctorStationAdviceRowDto,
  medicationSortByRequestId: ReadonlyMap<string, number>,
): number {
  const direct = dtoSortSeq(dto.sortNumber);
  if (direct !== Number.MAX_SAFE_INTEGER) {
    return direct;
  }
  const anchorId = preserveApiIdForRequest(dto.basedOnMedicationRequestId);
  if (!anchorId) {
    return Number.MAX_SAFE_INTEGER;
  }
  return medicationSortByRequestId.get(anchorId) ?? Number.MAX_SAFE_INTEGER;
}

function medicationGroupMergeKey(dto: DoctorStationAdviceRowDto): DrugGroupKey {
  if (inferAdviceRowResourceType(dto) !== 'MEDICATION') return '';
  const gid = String(dto.groupId ?? '').trim();
  return gid === '' ? '' : gid;
}

function doctorStationAdviceRowDtoToDrugGroupItem(
  dto: DoctorStationAdviceRowDto,
): DrugGroupDisplayItem {
  return {
    selectedDrugName: String(dto.adviceDisplayName ?? dto.contentSummary ?? '').trim(),
    selectedDrugSpec: String(dto.specificationText ?? '').trim(),
    pharmacy: '',
    singleDose: dto.dose ?? '',
    doseSubUnit: String(dto.doseUnitName ?? dto.doseUnitCode ?? '').trim(),
    totalAmount: dto.quantity ?? '',
    totalAmountUnit: resolveAdviceQuantityUnitLabel(dto.unitName, dto.unitCode),
    usage: String(dto.methodName ?? dto.methodCode ?? '').trim(),
    frequency: String(dto.rateName ?? dto.rateCode ?? '').trim(),
    medicationDays: dto.dispensePerDuration ?? '',
    speedDisplay:
      String(dto.speedName ?? '').trim() || String(dto.speedCode ?? '').trim() || undefined,
    dosageDisplay: String(dto.dosageDisplay ?? '').trim() || undefined,
    splitDose: dto.splitDose ?? undefined,
    splitDoseUnitCode: String(dto.splitDoseUnitCode ?? '').trim() || undefined,
    splitDoseUnitName: String(dto.splitDoseUnitName ?? '').trim() || undefined,
    doseUnitCode: String(dto.doseUnitCode ?? '').trim() || undefined,
  };
}

function sumSliceLinePriceY(slice: readonly DoctorStationAdviceRowDto[]): string {
  let sum = 0;
  for (const d of slice) {
    const v = d.lineDisplayTotalPrice ?? d.lineTotalAmount;
    const n = typeof v === 'number' ? v : Number(String(v ?? '').trim());
    if (Number.isFinite(n)) sum += n;
  }
  return formatUnitPriceYuan(sum);
}

/** 解析列表行已格式化的金额文案为数字 */
function parseAdviceLineAmountYuan(text: string): number {
  const normalized = String(text ?? '')
    .trim()
    .replaceAll(/[^\d.-]/g, '');
  const n = Number(normalized);
  return Number.isFinite(n) ? n : 0;
}

/**
 * 列表「费用性质」回填：优先接口 feeNatureText；缺省时用 init 下拉按 contractId 取合同名。
 * 后端未识别 category_enum 数字码时仍可先展示合同名，避免列恒为 —。
 */
export function enrichAdviceListFeeNatureText(
  rows: readonly AdviceRowItem[],
  feeNatureOptions: readonly TerminologyOptionDto[] | undefined,
): AdviceRowItem[] {
  if (!rows.length) return [...rows];
  const options = feeNatureOptions ?? [];
  return rows.map((row) => {
    if (row.isEditor) return row;
    const fromApi = String(row.feeNatureText ?? '').trim();
    if (fromApi) return row;
    const contractId = String(row.contractId ?? '').trim();
    if (!contractId) return row;
    const label = terminologyLabel(options, contractId);
    if (!label || label === '—') return row;
    return { ...row, feeNatureText: label };
  });
}

/** 汇总医嘱列表各明细行总金额（排除嵌入编辑卡虚拟行；成组行按合并后行金额计一次） */
export function sumAdviceListTotalAmount(rows: readonly AdviceRowItem[]): string {
  let sum = 0;
  for (const row of rows) {
    if (row.isEditor) continue;
    sum = decimalAdd(sum, parseAdviceLineAmountYuan(row.lineTotalAmount));
  }
  return formatUnitPriceYuan(sum);
}

export function mergeMedicationAdviceRowsForListDisplay(
  rows: readonly DoctorStationAdviceRowDto[],
): AdviceRowItem[] {
  // 代煎中药为中药方 companion，费用在中药处方页展示，不进开立医嘱列表
  const withoutDecoction = rows.filter((dto) => {
    const name = String(dto.adviceDisplayName ?? dto.contentSummary ?? '').trim();
    return name !== '代煎中药';
  });
  const enrichedRows = enrichSkinTestCompanionDisplayNames(withoutDecoction);
  const sorted = sortDoctorStationAdviceRowsStable(enrichedRows);
  return mergeConsecutiveRowsByDrugGroup(sorted, {
    getGroupKey: medicationGroupMergeKey,
    toGroupItem: doctorStationAdviceRowDtoToDrugGroupItem,
    buildMergedRow: ({ slice, groupItems }) => {
      const members = slice.map((d) => mapAdviceListRowDto(d));
      const firstMember = members[0];
      if (firstMember === undefined) {
        throw new Error('mergeMedicationAdviceRowsForListDisplay: empty slice');
      }
      if (slice.length === 1) {
        return { ...firstMember };
      }
      const gid = String(slice[0]?.groupId ?? '').trim();
      const idPart = slice.map((d) => String(d.requestId ?? '').trim()).join('&');
      return {
        ...firstMember,
        key: gid ? `merged-med-${gid}-${idPart}` : `merged-med-${idPart}`,
        lineTotalAmount: sumSliceLinePriceY(slice),
        groupItems,
        mergedAdviceRows: members,
      };
    },
  });
}

// —— 旧行双击回填 ——

export interface UsageExtensionFallbacks {
  usage: string;
  frequency: string;
}

function parseUsageExtensionFromRow(
  extension: string,
  fallbacks: UsageExtensionFallbacks,
): {
  frequency: string;
  medicationDays: number;
  usage: string;
} {
  const s = String(extension || '').trim();
  const routeFallback = fallbacks.usage;
  const freqFallback = fallbacks.frequency;
  if (!s) {
    return {
      usage: routeFallback,
      frequency: freqFallback,
      medicationDays: 0,
    };
  }
  const dayMatchFull = s.match(/（(\d+)天）\s*$/);
  const dayMatchAsc = s.match(/\((\d+)天\)\s*$/);
  const dayMatch = dayMatchFull || dayMatchAsc;
  const medicationDays = dayMatch ? Math.max(1, Number(dayMatch[1]) || 1) : 0;
  const rest = dayMatch && dayMatch.index !== undefined ? s.slice(0, dayMatch.index).trim() : s;
  const idxCn = rest.indexOf('，');
  if (idxCn !== -1) {
    return {
      usage: rest.slice(0, idxCn).trim() || routeFallback,
      frequency: rest.slice(idxCn + 1).trim() || freqFallback,
      medicationDays,
    };
  }
  const commaIdx = rest.indexOf(', ');
  if (commaIdx !== -1) {
    return {
      usage: rest.slice(0, commaIdx).trim() || routeFallback,
      frequency: rest.slice(commaIdx + 2).trim() || freqFallback,
      medicationDays,
    };
  }
  return {
    usage: rest.trim() || routeFallback,
    frequency: freqFallback,
    medicationDays,
  };
}

function parseDoseText(text: string) {
  const chars = [...text.trim()];
  let numberPart = '';
  let unitPart = '';
  for (const char of chars) {
    if (!unitPart && ((char >= '0' && char <= '9') || char === '.')) {
      numberPart += char;
    } else {
      unitPart += char;
    }
  }
  return {
    value: numberPart ? Number(numberPart) : 0,
    unit: unitPart.trim(),
  };
}

export function buildFallbackAdviceDrugItemFromRow(
  row: AdviceRowItem,
  extensionFallbacks: UsageExtensionFallbacks,
): AdviceDrugItem {
  const content = row.content || '';
  const extension = row.extension || '';
  const dosageText = row.dosageText || '';
  const nameMatch = content.match(/^(.*?)【/);
  const specMatch = content.match(/【(.*?)】/);
  const pharmacyMatch = content.match(/（(.*?)）/);
  const parsedUsage = parseUsageExtensionFromRow(extension, extensionFallbacks);
  // 剂量段：`1支(2ml)/次` 时括号内为计量；无括号则整段为计量/同单位
  const doseSegment = (dosageText.split('，')[0] || '').replaceAll('/次', '').replaceAll('。', '');
  const doseInParen =
    doseSegment.match(/\(([^)]+)\)/)?.[1] || doseSegment.match(/（([^）]+)）/)?.[1];
  const singleRaw = String(doseInParen ?? doseSegment).trim();
  const totalRaw = (dosageText.split('，').find((part) => part.includes('共')) || '').replace(
    '共',
    '',
  );
  const singleParsed = parseDoseText(singleRaw);
  const totalParsed = parseDoseText(totalRaw.replace('。', ''));
  const doseQty =
    Number.isFinite(singleParsed.value) && singleParsed.value > 0 ? singleParsed.value : undefined;
  const isService =
    String(row.resourceType ?? '')
      .trim()
      .toUpperCase() === 'SERVICE';
  const serviceQty = isService
    ? row.listQuantity !== undefined && row.listQuantity > 0
      ? row.listQuantity
      : totalParsed.value || undefined
    : undefined;
  const execQty = isService ? totalParsed.value : totalParsed.value;

  return {
    orderType: (row.type.split('/')[0] || '').trim(),
    selectedId: `fallback-${row.key}`,
    diagnosis: '',
    feeNature: '',
    skinTest: row.skinTestFlag === true,
    pharmacy: (pharmacyMatch?.[1] || '').trim(),
    singleDose: undefined,
    frequency: parsedUsage.frequency,
    usage: parsedUsage.usage,
    medicationDays: parsedUsage.medicationDays,
    serviceQuantity: serviceQty,
    totalAmount: execQty,
    selectedDrugName: nameMatch?.[1]?.trim() || content,
    selectedDrugSpec: specMatch?.[1] || '',
    selectedDrugPrice: '',
    doseMainUnit: '',
    doseSubUnit: singleParsed.unit,
    doseConvertRatio: doseQty,
    doseBandConvertK: undefined,
    totalAmountUnit: totalParsed.unit,
    totalUnitSplitCode: undefined,
    totalUnitSplitName: undefined,
    totalUnitSalesCode: undefined,
    totalUnitSalesName: undefined,
    totalAmountSplitToSalesK: undefined,
    expanded: true,
    tcmSpecialDecoction: '',
    productTableName: row.productTableName,
    sortNumber: row.sortNumber,
    groupId: String(row.groupId ?? '').trim() || undefined,
  };
}

export {
  formatAdviceCatalogStockCellText,
  isAdviceCatalogZeroStock,
} from '#/utils/doctor-station/advice-catalog-stock-display';
