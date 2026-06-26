import type { TcmPrescriptionLineUi, TcmPrescriptionUi } from './tcm-prescription.service';

import type { OrderTemplateHeaderItem, OrderTemplateLineRowItem } from '#/api/basic/order-template';
import type {
  DoctorStationAdviceCatalogRowDto,
  PrepickedProductKeyDto,
  TerminologyOptionDto,
} from '#/api/doctor-station/advice.types';
import type { AdviceProductOption } from '#/views/outpatient/doctor-station/advice';

import { pageFrequencyApi } from '#/api/basic/frequency';
import { ORDER_TEMPLATE_LINE_KIND_MEDICATION } from '#/api/basic/order-template';
import { httpPageTcmHerbalMedicationCatalog } from '#/api/doctor-station/tcm.http';
import type {
  PageTcmMedCatalogRequest,
  PageTcmMedCatalogResponse,
} from '#/api/doctor-station/tcm.types';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { parseDosageTextParts } from '#/views/basic/ordertemplate/order-template-medication-units';
import { mapCatalogRowToOption } from '#/views/outpatient/doctor-station/advice/services/advice-list';

import { applyCatalogProductToLine, createEmptyLine } from './tcm-prescription.service';

/** 中药组套套用业务异常 */
export class TcmOrderTemplateApplyError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'TcmOrderTemplateApplyError';
  }
}

/** 组套行 → 目录 prepicked 键 */
function prepickedKeyFromTemplateLine(
  line: OrderTemplateLineRowItem,
): null | PrepickedProductKeyDto {
  const defId = String(line.medicationDefinitionId ?? '').trim();
  if (!defId) {
    return null;
  }
  const tbl = String(line.productTableName ?? '').trim() || 'med_medication_definition';
  return { productDefinitionId: defId, productTableName: tbl };
}

/** 组套行是否为药材明细 */
function isMedicationTemplateLine(line: OrderTemplateLineRowItem): boolean {
  return (
    String(line.lineKindEnum ?? '')
      .trim()
      .toUpperCase() === ORDER_TEMPLATE_LINE_KIND_MEDICATION
  );
}

/** 解析组套行单剂剂量 */
function resolveTemplateLineDose(line: OrderTemplateLineRowItem): number {
  const parsed = parseDosageTextParts(line.dosageText);
  if (parsed.doseQty !== undefined && parsed.doseQty > 0) {
    return parsed.doseQty;
  }
  const qty = Number(line.defaultQuantity);
  if (Number.isFinite(qty) && qty > 0) {
    return qty;
  }
  return 10;
}

/** 批量拉取饮片目录（prepickedProductKeys） */
async function fetchTcmCatalogByPrepickedKeys(
  keys: PrepickedProductKeyDto[],
  encounterId: string,
  patientId: string,
  pageCatalog?: (body: PageTcmMedCatalogRequest) => Promise<PageTcmMedCatalogResponse>,
): Promise<{
  catalogOptions: AdviceProductOption[];
  locationByDefId: Map<string, string>;
}> {
  if (keys.length === 0) {
    return { catalogOptions: [], locationByDefId: new Map() };
  }
  const res = await (pageCatalog ?? httpPageTcmHerbalMedicationCatalog)({
    encounterId,
    patientId,
    pageNo: 1,
    pageSize: Math.max(keys.length, 20),
    prepickedProductKeys: keys,
  });
  const { content: rawRows } = parsePagedRowsFromApiData<DoctorStationAdviceCatalogRowDto>(res);
  const catalogOptions = rawRows.map((row) => mapCatalogRowToOption(row));
  const locationByDefId = new Map<string, string>();
  for (const row of rawRows) {
    const defId = preserveApiIdForRequest(row.productDefinitionId);
    const locId = preserveApiIdForRequest(row.defaultDispenseLocationId);
    if (defId && locId) {
      locationByDefId.set(defId, locId);
    }
  }
  return { catalogOptions, locationByDefId };
}

function catalogKeyOf(option: AdviceProductOption): string {
  const tbl = String(option.productTableName ?? '').trim();
  const defId =
    preserveApiIdForRequest(option.productDefinitionId) ?? preserveApiIdForRequest(option.id) ?? '';
  return `${tbl}:${defId}`;
}

/** 批量解析 frequencyId → rateCode */
async function buildRateCodeMapByFrequencyIds(
  frequencyIds: string[],
): Promise<Map<string, string>> {
  const map = new Map<string, string>();
  const unique = [
    ...new Set(frequencyIds.map((id) => preserveApiIdForRequest(id)).filter(Boolean)),
  ] as string[];
  if (unique.length === 0) {
    return map;
  }
  try {
    const res = await pageFrequencyApi({
      pageNo: 1,
      pageSize: Math.max(unique.length, 200),
    });
    const { content: list } = parsePagedRowsFromApiData<{
      id?: number | string;
      rateCode?: string;
    }>(res.data ?? res);
    for (const item of list) {
      const id = preserveApiIdForRequest(item.id);
      const rateCode = String(item.rateCode ?? '').trim();
      if (id && rateCode) {
        map.set(id, rateCode);
      }
    }
  } catch {
    /* 频次主数据不可用时回落 frequencyText 匹配 */
  }
  return map;
}

/** 组套行频次 → 处方 rateCode */
function resolveTemplateLineRateCode(
  line: OrderTemplateLineRowItem,
  rateCodeMap: Map<string, string>,
  medicationRateOptions: TerminologyOptionDto[],
): string {
  const frequencyId = preserveApiIdForRequest(line.frequencyId);
  if (frequencyId) {
    const fromMap = rateCodeMap.get(frequencyId);
    if (fromMap) {
      return fromMap;
    }
  }
  const text = String(line.frequencyText ?? '').trim();
  if (text) {
    const hit = medicationRateOptions.find((opt) => String(opt.label ?? '').trim() === text);
    if (hit?.value) {
      return String(hit.value).trim();
    }
  }
  return '';
}

/** 套用中药组套至当前处方草稿（仅追加药材行，方头默认值写入 rx） */
export async function applyTcmOrderTemplateToPrescription(params: {
  catalogDispenseLocationMap: Record<string, string>;
  encounterId: string;
  header: OrderTemplateHeaderItem;
  lines: OrderTemplateLineRowItem[];
  medicationRateOptions?: TerminologyOptionDto[];
  patientId: string;
  prescription: TcmPrescriptionUi;
  pageTcmHerbalMedicationCatalog?: (
    body: PageTcmMedCatalogRequest,
  ) => Promise<PageTcmMedCatalogResponse>;
}): Promise<{ appliedLineCount: number }> {
  const encounterId = params.encounterId.trim();
  const patientId = params.patientId.trim();
  if (!encounterId || !patientId) {
    throw new TcmOrderTemplateApplyError('缺少就诊或患者信息，无法套用组套');
  }

  const medicationLines = params.lines
    .filter((line) => isMedicationTemplateLine(line))
    .toSorted((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0));

  if (medicationLines.length === 0) {
    throw new TcmOrderTemplateApplyError('组套明细为空，无法套用');
  }

  const prepickedKeys = medicationLines
    .map((line) => prepickedKeyFromTemplateLine(line))
    .filter((key): key is PrepickedProductKeyDto => key !== null);

  if (prepickedKeys.length !== medicationLines.length) {
    throw new TcmOrderTemplateApplyError('组套明细存在无效药材行，无法套用');
  }

  const { catalogOptions, locationByDefId } = await fetchTcmCatalogByPrepickedKeys(
    prepickedKeys,
    encounterId,
    patientId,
    params.pageTcmHerbalMedicationCatalog,
  );
  const catalogByKey = new Map<string, AdviceProductOption>();
  for (const option of catalogOptions) {
    catalogByKey.set(catalogKeyOf(option), option);
  }

  const rx = params.prescription;
  const header = params.header;
  const medicationRateOptions = params.medicationRateOptions ?? [];
  const leaderLine = medicationLines[0];
  const rateCodeMap = await buildRateCodeMapByFrequencyIds(
    medicationLines
      .map((line) => preserveApiIdForRequest(line.frequencyId))
      .filter(Boolean) as string[],
  );

  const defaultDoseQty = Number(header.defaultChineseHerbsDoseQuantity);
  if (Number.isFinite(defaultDoseQty) && defaultDoseQty > 0) {
    rx.chineseHerbsDoseQuantity = defaultDoseQty;
  }
  if (header.defaultSufferingFlag !== undefined && header.defaultSufferingFlag !== null) {
    rx.sufferingFlag = Boolean(header.defaultSufferingFlag);
  }
  const methodCode = String(leaderLine?.routeCode ?? '').trim();
  if (methodCode) {
    rx.methodCode = methodCode;
  }
  const rateCode = leaderLine
    ? resolveTemplateLineRateCode(leaderLine, rateCodeMap, medicationRateOptions)
    : '';
  if (rateCode) {
    rx.rateCode = rateCode;
  }
  const days = Number(leaderLine?.defaultDays);
  if (Number.isFinite(days) && days > 0) {
    rx.days = days;
  }
  rx.headerDirty = true;

  let sortBase = rx.lines.filter((line) => !line.pendingDelete).length;
  let appliedLineCount = 0;

  for (const templateLine of medicationLines) {
    const key = prepickedKeyFromTemplateLine(templateLine);
    if (!key) {
      continue;
    }
    const catalog =
      catalogByKey.get(`${key.productTableName}:${key.productDefinitionId}`) ??
      catalogOptions.find(
        (item) => preserveApiIdForRequest(item.productDefinitionId) === key.productDefinitionId,
      );
    if (!catalog) {
      const name = String(templateLine.productName ?? '').trim() || '未命名药材';
      throw new TcmOrderTemplateApplyError(`目录中未找到「${name}」，无法套用组套`);
    }

    sortBase += 1;
    const line: TcmPrescriptionLineUi = createEmptyLine(sortBase);
    applyCatalogProductToLine(line, catalog);
    line.dose = resolveTemplateLineDose(templateLine);
    line.isNew = true;
    line.dirty = true;

    const defId = preserveApiIdForRequest(catalog.productDefinitionId) ?? '';
    const locId = locationByDefId.get(defId);
    if (defId && locId) {
      params.catalogDispenseLocationMap[defId] = locId;
      line.dispenseLocationId = locId;
    }

    rx.lines.push(line);
    appliedLineCount += 1;
  }

  if (appliedLineCount === 0) {
    throw new TcmOrderTemplateApplyError('没有可套用的药材行');
  }

  return { appliedLineCount };
}
