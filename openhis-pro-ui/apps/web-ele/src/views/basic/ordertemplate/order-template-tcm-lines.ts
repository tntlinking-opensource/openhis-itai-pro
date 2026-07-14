import type { CatalogSelectRow, DraftLine } from './order-template-lines';

import type { TcmHerbCardItem } from '#/components/doctor-station/tcm-advice/components/tcm-herb-card.vue';
import type { AdviceProductOption } from '#/views/outpatient/doctor-station/advice';

import { ORDER_TEMPLATE_LINE_KIND_MEDICATION } from '#/api/basic/order-template';
import { resolveTcmDoseUnitFromProduct } from '#/components/doctor-station/tcm-advice/services/tcm-prescription.service';
import { calcMoneyLineTotal, sumMoneyThenCalc } from '#/utils/format/money-scale';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import {
  buildDosageText,
  buildUnitBandsFromMedicationCatalogItem,
  parseDosageTextParts,
} from './order-template-medication-units';

/** 组套草稿行扩展：卡片展示用零售价 */
export interface TcmTemplateDraftLine extends DraftLine {
  _retailPrice?: number;
}

/** 中药组套药材卡片列（对齐门诊中医 Tab） */
export const tcmTemplateProductColumns = [
  { prop: 'code', label: '项目编码', width: 120 },
  { prop: 'name', label: '项目名称', width: 200 },
  { prop: 'category', label: '类别', width: 72 },
  { prop: 'spec', label: '单位', width: 80 },
  { prop: 'availableQuantity', label: '库存数量', width: 88 },
  { prop: 'price', label: '单价', width: 72 },
];

/** 目录检索行 → AdviceOrderSelect 选项 */
export function mapCatalogSelectRowToAdviceProductOption(
  row: CatalogSelectRow,
): AdviceProductOption {
  const defId = String(row.value ?? '').trim();
  const unitBands = buildUnitBandsFromMedicationCatalogItem(row as Record<string, unknown>);
  const priceRaw = Number(row.retailPrice);
  const price = Number.isFinite(priceRaw) ? priceRaw : undefined;
  return {
    id: defId,
    code: String(row.busNo ?? '').trim(),
    name: String(row.label ?? '').trim(),
    category: String(row.categoryCode_dictText ?? '药品').trim() || '药品',
    spec: String(row.specification ?? row.unitCode_dictText ?? 'g').trim() || 'g',
    price,
    catalogUnitPrice: price,
    productDefinitionId: defId,
    defaultUnitCode: String(row.unitCode_dictText ?? 'g').trim() || 'g',
    unitBands,
  };
}

/** 解析草稿行单次剂量（优先剂量侧，回落 dosageText） */
export function resolveTcmTemplateLineDose(line: TcmTemplateDraftLine): number {
  const fromConvert = Number(line._doseConvertRatio);
  if (Number.isFinite(fromConvert) && fromConvert > 0) {
    return fromConvert;
  }
  const fromSingle = Number(line._singleDose);
  if (Number.isFinite(fromSingle) && fromSingle > 0) {
    return fromSingle;
  }
  const parsed = parseDosageTextParts(line.dosageText);
  return parsed.doseQty ?? 0;
}

/** 解析草稿行剂量单位 */
export function resolveTcmTemplateLineDoseUnit(line: TcmTemplateDraftLine): string {
  const fromField = String(line._doseSubUnit ?? line._doseMainUnit ?? '').trim();
  if (fromField) {
    return fromField;
  }
  const parsed = parseDosageTextParts(line.dosageText);
  return parsed.unitText?.trim() || 'g';
}

/** 草稿行 → 药材卡片展示模型 */
export function mapDraftLineToHerbCardItem(line: TcmTemplateDraftLine): TcmHerbCardItem {
  const doseUnitName = resolveTcmTemplateLineDoseUnit(line);
  return {
    clientLineKey: line._clientKey,
    medicationDefinitionId: String(line.medicationDefinitionId ?? ''),
    medicationName: String(line._productLabel || line.productName || ''),
    dose: resolveTcmTemplateLineDose(line),
    doseUnitCode: doseUnitName,
    doseUnitName,
    retailPrice: Number(line._retailPrice ?? 0),
  };
}

/** 选药后写回草稿行（组套保存仍走 dosageText） */
export function applyTcmHerbProductToDraftLine(
  line: TcmTemplateDraftLine,
  product: AdviceProductOption,
): void {
  const id =
    preserveApiIdForRequest(product.productDefinitionId) ??
    preserveApiIdForRequest(product.id) ??
    '';
  line.lineKindEnum = ORDER_TEMPLATE_LINE_KIND_MEDICATION;
  line.medicationDefinitionId = id || undefined;
  line.activityDefinitionId = undefined;
  line.deviceDefinitionId = undefined;
  line._productLabel = String(product.name ?? '').trim();
  line.productName = line._productLabel || undefined;
  const doseUnit = resolveTcmDoseUnitFromProduct(product);
  line._retailPrice = doseUnit.retailPrice;
  line._doseSubUnit = doseUnit.doseUnitName;
  line._doseMainUnit = doseUnit.doseUnitName;
  const currentDose = resolveTcmTemplateLineDose(line);
  line._doseConvertRatio = currentDose > 0 ? currentDose : 10;
  line._singleDose = undefined;
  line.dosageText = buildDosageText(line);
}

/** 卡片内修改单次剂量 */
export function updateTcmTemplateLineDose(line: TcmTemplateDraftLine, dose: number): void {
  const nextDose = Number(dose);
  if (!Number.isFinite(nextDose) || nextDose < 0) {
    return;
  }
  line._doseConvertRatio = nextDose;
  if (!String(line._doseSubUnit ?? '').trim()) {
    line._doseSubUnit = 'g';
  }
  line.dosageText = buildDosageText(line);
}

/** 单剂金额：各行 dose × retailPrice 累加（6 位） */
export function calcTcmTemplateSingleDoseAmount(lines: TcmTemplateDraftLine[]): number {
  const lineTotals: number[] = [];
  for (const line of lines) {
    if (!String(line.medicationDefinitionId ?? '').trim()) {
      continue;
    }
    const dose = resolveTcmTemplateLineDose(line);
    const price = Number(line._retailPrice ?? 0);
    lineTotals.push(calcMoneyLineTotal(price, dose));
  }
  return sumMoneyThenCalc(lineTotals);
}
