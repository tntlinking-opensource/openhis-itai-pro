import type {
  DoctorStationAdviceCatalogRowDto,
  DoctorStationAdviceRowDto,
} from '#/api/doctor-station/advice.types';
import {
  mergeConsecutiveRowsByDrugGroup,
  type DrugGroupDisplayItem,
  type DrugGroupKey,
} from '#/utils/doctor-station/drug-group-table';
import { resolveAdviceQuantityUnitLabel } from '#/utils/doctor-station/advice-quantity-unit-display';
import { convertProductUnitPriceForDisplay } from '#/utils/doctor-station/product-unit-band-price';

import { formatDateTime } from '@vben/utils';

import { resolveAdviceStatusTagTypeFromCode } from '#/utils/advice/advice-status-display';

import {
  formatAdviceCatalogStockCellText,
  isAdviceCatalogZeroStock,
} from '#/utils/doctor-station/advice-catalog-stock-display';
import { buildServiceApplyFormExtensionText } from '#/utils/service-apply-display';
import {
  resolveServiceApplyFormNo,
  resolveServiceApplyFormSortKey,
} from '#/utils/service-apply-form-group-display';

import type { AdviceDrugItem, AdviceProductOption, AdviceRowItem } from './advice.types';
import { formatUnitPriceYuan } from './advice-format';
import { isInpatientLongTermMedicationTherapy } from './advice-row';
import { INPATIENT_NURSING_LEVEL_CATEGORY_CODE } from './advice-tab.constants';
import { normalizeCatalogUnitBands } from './medication-unit-bands';

/** 医嘱列表映射、成组合并、目录选品、库存展示、旧行回填 */

export { formatAdviceCatalogStockCellText, isAdviceCatalogZeroStock };

// —— 列表 DTO → 表格行 ——

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
  const tbl = String(dto.productTableName ?? '').toLowerCase();
  if (tbl.includes('medication')) return 'MEDICATION';
  return '';
}

function buildAdviceContentTitle(dto: DoctorStationAdviceRowDto): string {
  const name = String(dto.adviceDisplayName ?? dto.contentSummary ?? '').trim();
  const spec = String(dto.specificationText ?? '').trim();
  if (name && spec) return `${name}【${spec}】`;
  return name || spec;
}

function buildAdviceDosageSummaryLine(dto: DoctorStationAdviceRowDto): string {
  const longTerm = isInpatientLongTermMedicationTherapy(dto.therapyTypeCode);
  const qtyRaw = dto.quantity;
  const unit = resolveAdviceQuantityUnitLabel(dto.unitName, dto.unitCode);
  const right = longTerm
    ? ''
    : qtyRaw !== null && qtyRaw !== undefined && String(qtyRaw).trim() !== '' && unit
      ? `共${qtyRaw}${unit}`
      : qtyRaw !== null && qtyRaw !== undefined && String(qtyRaw).trim() !== ''
        ? `共${qtyRaw}`
        : '';
  const doseRaw = dto.dose;
  const doseU = String(dto.doseUnitName ?? dto.doseUnitCode ?? '').trim();
  const left =
    doseRaw !== null && doseRaw !== undefined && String(doseRaw).trim() !== '' && doseU
      ? `${doseRaw}${doseU}/次`
      : doseRaw !== null && doseRaw !== undefined && String(doseRaw).trim() !== ''
        ? `${doseRaw}/次`
        : '';
  if (left && right) return `${left}，${right}。`;
  if (left) return `${left}。`;
  if (right) return `${right}。`;
  return '';
}

function therapyTypeLabel(code: string): string {
  const c = code.trim().toUpperCase();
  if (c === 'LONG_TERM') return '长期';
  if (c === 'TEMPORARY') return '临时';
  if (c === 'DISCHARGE_MEDICATION') return '出院带药';
  return '';
}

/** 列表「类型」列长临文案：优先后端 therapyTypeText，否则按 therapyTypeCode 推导 */
export function resolveAdviceTherapyTypeText(
  row: Pick<AdviceRowItem, 'therapyTypeCode' | 'therapyTypeText'>,
): string {
  const text = String(row.therapyTypeText ?? '').trim();
  if (text) return text;
  return therapyTypeLabel(String(row.therapyTypeCode ?? ''));
}

/** 长临颜色 class（由 therapyTypeCode 判断） */
export function adviceTherapyTypeClass(code?: string): string {
  const c = String(code ?? '')
    .trim()
    .toUpperCase();
  if (c === 'LONG_TERM') return 'ip-advice-therapy-text--long';
  if (c === 'TEMPORARY') return 'ip-advice-therapy-text--temp';
  if (c === 'DISCHARGE_MEDICATION') return 'ip-advice-therapy-text--discharge';
  return '';
}

function buildAdviceMethodRateDurationLine(dto: DoctorStationAdviceRowDto): string {
  const m = String(dto.methodName ?? '').trim();
  const r = String(dto.rateName ?? '').trim();
  const longTerm = isInpatientLongTermMedicationTherapy(dto.therapyTypeCode);
  let dayPart = '';
  if (!longTerm) {
    const durRaw = dto.dispensePerDuration;
    if (durRaw !== null && durRaw !== undefined && String(durRaw).trim() !== '') {
      const d = Number(durRaw);
      if (Number.isFinite(d) && d > 0) dayPart = ` (${Math.trunc(d)}天)`;
    }
  }
  if (m && r) return `${m}, ${r}${dayPart}`;
  if (m) return `${m}${dayPart}`;
  if (r) return `${r}${dayPart}`;
  return dayPart.trim();
}

/** 列表 DTO 是否为护理级别（category 26） */
function isNursingLevelAdviceDto(dto: DoctorStationAdviceRowDto): boolean {
  const serviceCat = String(dto.serviceCategoryEnum ?? '').trim();
  if (serviceCat === INPATIENT_NURSING_LEVEL_CATEGORY_CODE) return true;
  const catalogCat = String(dto.catalogCategoryCode ?? '').trim();
  return catalogCat === INPATIENT_NURSING_LEVEL_CATEGORY_CODE;
}

/** 医嘱内容 extension：申请单样本/部位/备注 + 用法频次天数 */
function buildAdviceListExtensionLine(dto: DoctorStationAdviceRowDto): string {
  // 护理级别无频次展示（BR-IP-ORD-026；列表仅展示级别名称）
  const usageLine = isNursingLevelAdviceDto(dto) ? '' : buildAdviceMethodRateDurationLine(dto);
  return buildServiceApplyFormExtensionText(
    {
      remark: dto.remark,
      serviceRequestDetails: dto.serviceRequestDetails,
    },
    usageLine ? [usageLine] : [],
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

/** 护理级别诊疗行识别（列表置顶与弹窗门控共用） */
export function isNursingLevelAdviceRow(
  row: Pick<AdviceRowItem, 'serviceCategoryEnum' | 'catalogCategoryCode'>,
): boolean {
  const serviceCat = String(row.serviceCategoryEnum ?? '').trim();
  if (serviceCat === INPATIENT_NURSING_LEVEL_CATEGORY_CODE) return true;
  const catalogCat = String(row.catalogCategoryCode ?? '').trim();
  return catalogCat === INPATIENT_NURSING_LEVEL_CATEGORY_CODE;
}

/** 护理级别须固定展示在医嘱列表第一行（AS-IP-DOR-003 展示习惯） */
export function pinNursingLevelAdviceRowsToFirst(rows: readonly AdviceRowItem[]): AdviceRowItem[] {
  if (rows.length <= 1) return [...rows];
  const nursingRows: AdviceRowItem[] = [];
  const otherRows: AdviceRowItem[] = [];
  for (const row of rows) {
    if (isNursingLevelAdviceRow(row)) {
      nursingRows.push(row);
    } else {
      otherRows.push(row);
    }
  }
  if (nursingRows.length === 0) return [...rows];
  return [...nursingRows, ...otherRows];
}

export function mapAdviceListRowDto(dto: DoctorStationAdviceRowDto): AdviceRowItem {
  const resourceType = inferAdviceRowResourceType(dto);
  const requestId = String(dto.requestId ?? '').trim();
  const listKey = String(dto.listRowKey ?? '').trim();
  const key =
    listKey ||
    (requestId && resourceType ? `api-${resourceType}-${requestId}` : `api-row-${Date.now()}`);
  const status =
    String(dto.displayStatusEnumText ?? '').trim() ||
    String(dto.statusEnumText ?? '').trim() ||
    String(dto.displayStatusEnum ?? '').trim() ||
    String(dto.statusEnum ?? '').trim() ||
    '—';
  const authoredRaw = String(dto.authoredAt ?? '').trim();
  const startTime = authoredRaw ? formatDateTime(authoredRaw) : '';
  const content = buildAdviceContentTitle(dto);
  const dosageLine = buildAdviceDosageSummaryLine(dto);
  const extension = buildAdviceListExtensionLine(dto);
  const typeLabel =
    String(dto.catalogCategoryName ?? '').trim() || resourceTypeToTypeLabel(resourceType);

  return {
    key,
    status,
    type: typeLabel,
    content: content || String(dto.contentSummary ?? '').trim(),
    startTime,
    extension,
    lineTotalAmount: formatAdviceRowTotalPrice(dto),
    diagnosisSummary:
      (
        dto.conditionDefinitionName ||
        dto.boundConditionName ||
        dto.diagnosisSummary ||
        ''
      ).trim() || '—',
    orderingDoctor: (dto.authoredByName || '').trim() || '—',
    skinTestText: skinTestDisplayText(dto),
    dosageText: dosageLine || undefined,
    productTableName: String(dto.productTableName ?? '').trim() || undefined,
    sortNumber: normalizedListSortNumber(dto.sortNumber),
    skinTestFlag: dto.skinTestFlag,
    resourceType,
    requestId,
    groupId: String(dto.groupId ?? '').trim() || undefined,
    statusEnum: dto.displayStatusEnum ?? dto.statusEnum,
    therapyTypeCode: String(dto.therapyTypeCode ?? '').trim() || undefined,
    therapyTypeText:
      String(dto.therapyTypeText ?? '').trim() ||
      therapyTypeLabel(String(dto.therapyTypeCode ?? '')) ||
      undefined,
    rateCode: String(dto.rateCode ?? '').trim() || undefined,
    productDefinitionId: String(dto.productDefinitionId ?? '').trim() || undefined,
    adviceDisplayName: String(dto.adviceDisplayName ?? '').trim() || undefined,
    specificationText: String(dto.specificationText ?? '').trim() || undefined,
    conditionDefinitionName: String(dto.conditionDefinitionName ?? '').trim() || undefined,
    methodCode: String(dto.methodCode ?? '').trim() || undefined,
    listDose: positiveListNumber(dto.dose),
    listDoseUnitCode: String(dto.doseUnitCode ?? '').trim() || undefined,
    listUnitCode: String(dto.unitCode ?? '').trim() || undefined,
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
    applyFormNo: resolveServiceApplyFormNo(dto.prescriptionNo),
    serviceCategoryEnum: String(dto.serviceCategoryEnum ?? '').trim() || undefined,
    catalogCategoryCode: String(dto.catalogCategoryCode ?? '').trim() || undefined,
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
    category: categoryLabel,
    spec: specText,
    price,
    catalogUnitPrice,
    availableQuantity: row.availableQuantity,
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
  };
}

export function getAdviceStatusTagType(
  row: AdviceRowItem,
): 'danger' | 'info' | 'primary' | 'success' | 'warning' {
  return resolveAdviceStatusTagTypeFromCode(row.statusEnum);
}

// —— 成组合并 ——

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
 * 列表排序：药品先按 groupId 聚邻（成组合并要求连续行），再 sortNumber、requestId。
 * 若仅按 sortNumber，同组药品可能与别组交叉，导致无法合并展示。
 */
function sortDoctorStationAdviceRowsStable(
  rows: readonly DoctorStationAdviceRowDto[],
): DoctorStationAdviceRowDto[] {
  return [...rows].sort((a, b) => {
    const leftFormKey = resolveServiceApplyFormSortKey(a.prescriptionNo);
    const rightFormKey = resolveServiceApplyFormSortKey(b.prescriptionNo);
    const formCompare = leftFormKey.localeCompare(rightFormKey);
    if (formCompare !== 0) {
      return formCompare;
    }

    const ag = medicationGroupMergeKey(a);
    const bg = medicationGroupMergeKey(b);
    const aGroupSort = ag === '' || ag === null || ag === undefined ? '\uffff' : String(ag);
    const bGroupSort = bg === '' || bg === null || bg === undefined ? '\uffff' : String(bg);
    const gcmp = aGroupSort.localeCompare(bGroupSort);
    if (gcmp !== 0) return gcmp;
    const sa = dtoSortSeq(a.sortNumber);
    const sb = dtoSortSeq(b.sortNumber);
    if (sa !== sb) return sa - sb;
    return String(a.requestId ?? '').localeCompare(String(b.requestId ?? ''));
  });
}

function dtoSortSeq(raw: DoctorStationAdviceRowDto['sortNumber']): number {
  if (raw === null || raw === undefined || raw === '') return Number.MAX_SAFE_INTEGER;
  const n = typeof raw === 'number' ? raw : Number(raw);
  return Number.isFinite(n) ? n : Number.MAX_SAFE_INTEGER;
}

function medicationGroupMergeKey(dto: DoctorStationAdviceRowDto): DrugGroupKey {
  if (inferAdviceRowResourceType(dto) !== 'MEDICATION') return '';
  const gid = String(dto.groupId ?? '').trim();
  return gid === '' ? '' : gid;
}

function doctorStationAdviceRowDtoToDrugGroupItem(
  dto: DoctorStationAdviceRowDto,
): DrugGroupDisplayItem {
  const longTerm = isInpatientLongTermMedicationTherapy(dto.therapyTypeCode);
  return {
    selectedDrugName: String(dto.adviceDisplayName ?? dto.contentSummary ?? '').trim(),
    selectedDrugSpec: String(dto.specificationText ?? '').trim(),
    pharmacy: '',
    singleDose: dto.dose ?? '',
    doseSubUnit: String(dto.doseUnitName ?? dto.doseUnitCode ?? '').trim(),
    totalAmount: longTerm ? '' : (dto.quantity ?? ''),
    totalAmountUnit: longTerm ? '' : resolveAdviceQuantityUnitLabel(dto.unitName, dto.unitCode),
    usage: String(dto.methodName ?? dto.methodCode ?? '').trim(),
    frequency: String(dto.rateName ?? dto.rateCode ?? '').trim(),
    medicationDays: longTerm ? '' : (dto.dispensePerDuration ?? ''),
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

export function mergeMedicationAdviceRowsForListDisplay(
  rows: readonly DoctorStationAdviceRowDto[],
): AdviceRowItem[] {
  const sorted = sortDoctorStationAdviceRowsStable(rows);
  const merged = mergeConsecutiveRowsByDrugGroup(sorted, {
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
  return pinNursingLevelAdviceRowsToFirst(merged);
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
  const singleRaw = dosageText.split('/次')[0] || '';
  const totalRaw = (dosageText.split('，').find((part) => part.includes('共')) || '').replace(
    '共',
    '',
  );
  const singleParsed = parseDoseText(singleRaw);
  const totalParsed = parseDoseText(totalRaw.replace('。', ''));
  const doseQty =
    Number.isFinite(singleParsed.value) && singleParsed.value > 0 ? singleParsed.value : undefined;

  const rateFromRow = String(row.rateCode ?? '').trim();
  const tbl = String(row.productTableName ?? '').trim();
  const defId = String(row.productDefinitionId ?? '').trim();
  const selectedId = tbl && defId ? `${tbl}:${defId}` : `fallback-${row.key}`;
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
    selectedId,
    diagnosis: '',
    feeNature: '',
    skinTest: row.skinTestFlag === true,
    pharmacy: (pharmacyMatch?.[1] || '').trim(),
    singleDose: undefined,
    frequency: rateFromRow || parsedUsage.frequency,
    usage: parsedUsage.usage,
    medicationDays: isInpatientLongTermMedicationTherapy(row.therapyTypeCode)
      ? undefined
      : parsedUsage.medicationDays,
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
    therapyEnum: String(row.therapyTypeCode ?? '').trim() || undefined,
  };
}
