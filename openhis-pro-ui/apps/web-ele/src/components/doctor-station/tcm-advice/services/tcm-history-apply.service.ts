import type { TcmHistoryTreeItem, TcmPrescriptionUi } from './tcm-prescription.service';

import type {
  DoctorStationAdviceCatalogRowDto,
  PrepickedProductKeyDto,
} from '#/api/doctor-station/advice.types';
import type {
  ListTcmRxRequest,
  ListTcmRxResponse,
  PageTcmMedCatalogRequest,
  PageTcmMedCatalogResponse,
  TcmRxHeaderDto,
  TcmRxLineDto,
} from '#/api/doctor-station/tcm.types';
import type { AdviceProductOption } from '#/views/outpatient/doctor-station/advice';

import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { mapCatalogRowToOption } from '#/views/outpatient/doctor-station/advice/services/advice-list';

import { applyCatalogProductToLine, createEmptyLine } from './tcm-prescription.service';

/** 中药历史处方引用业务异常 */
export class TcmHistoryApplyError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'TcmHistoryApplyError';
  }
}

const HERBAL_PRODUCT_TABLE = 'med_medication_definition';

/** 历史明细 → 目录 prepicked 键 */
function prepickedKeyFromHistoryLine(line: TcmRxLineDto): null | PrepickedProductKeyDto {
  const defId = preserveApiIdForRequest(line.medicationDefinitionId);
  if (!defId) {
    return null;
  }
  return { productDefinitionId: defId, productTableName: HERBAL_PRODUCT_TABLE };
}

/** 批量拉取饮片目录（当前就诊价/库存/库位） */
async function fetchTcmCatalogByPrepickedKeys(
  keys: PrepickedProductKeyDto[],
  encounterId: string,
  patientId: string,
  pageCatalog: (body: PageTcmMedCatalogRequest) => Promise<PageTcmMedCatalogResponse>,
): Promise<{
  catalogOptions: AdviceProductOption[];
  locationByDefId: Map<string, string>;
}> {
  if (keys.length === 0) {
    return { catalogOptions: [], locationByDefId: new Map() };
  }
  const res = await pageCatalog({
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

/** 按处方号在历史就诊处方列表中定位方头 */
function findHistoryPrescriptionHeader(
  prescriptions: null | TcmRxHeaderDto[] | undefined,
  prescriptionNo: string,
): TcmRxHeaderDto | undefined {
  const no = prescriptionNo.trim();
  if (!no) {
    return undefined;
  }
  return (prescriptions ?? []).find((item) => String(item.prescriptionNo ?? '').trim() === no);
}

/** 将历史方头可复用字段写入当前草稿方 */
function applyHistoryHeaderToPrescription(
  rx: TcmPrescriptionUi,
  historyHeader: TcmRxHeaderDto,
  currentSyndromeGroupValues: string[],
) {
  const doseQty = Number(historyHeader.chineseHerbsDoseQuantity);
  if (Number.isFinite(doseQty) && doseQty > 0) {
    rx.chineseHerbsDoseQuantity = doseQty;
  }
  if (historyHeader.sufferingFlag !== undefined && historyHeader.sufferingFlag !== null) {
    rx.sufferingFlag = Boolean(historyHeader.sufferingFlag);
  }
  const methodCode = String(historyHeader.methodCode ?? '').trim();
  if (methodCode) {
    rx.methodCode = methodCode;
    rx.methodName = String(historyHeader.methodName ?? '').trim() || undefined;
  }
  const rateCode = String(historyHeader.rateCode ?? '').trim();
  if (rateCode) {
    rx.rateCode = rateCode;
    rx.rateName = String(historyHeader.rateName ?? '').trim() || undefined;
  }
  const days = Number(historyHeader.days);
  if (Number.isFinite(days) && days > 0) {
    rx.days = days;
  }
  const remark = String(historyHeader.remark ?? '').trim();
  if (remark) {
    rx.remark = remark;
  }
  // 病证组仅在当前就诊仍存在时回填，避免引用失效组号
  const syndromeGroup = String(historyHeader.tcmSyndromeGroup ?? '').trim();
  if (syndromeGroup && currentSyndromeGroupValues.includes(syndromeGroup)) {
    rx.tcmSyndromeGroup = syndromeGroup;
  }
  rx.headerDirty = true;
}

/** 引用历史中药处方至当前草稿方（按当前就诊重拉目录价/库位） */
export async function applyTcmHistoryPrescriptionToDraft(params: {
  /** 是否写入方头；增量追加到已存处方时应为 false */
  applyHeader?: boolean;
  catalogDispenseLocationMap: Record<string, string>;
  currentSyndromeGroupValues?: string[];
  encounterId: string;
  historyItem: TcmHistoryTreeItem;
  listTcmHerbalPrescriptions: (body: ListTcmRxRequest) => Promise<ListTcmRxResponse>;
  pageTcmHerbalMedicationCatalog: (
    body: PageTcmMedCatalogRequest,
  ) => Promise<PageTcmMedCatalogResponse>;
  patientId: string;
  prescription: TcmPrescriptionUi;
}): Promise<{ appliedLineCount: number }> {
  const encounterId = params.encounterId.trim();
  const patientId = params.patientId.trim();
  if (!encounterId || !patientId) {
    throw new TcmHistoryApplyError('缺少就诊或患者信息，无法引用历史处方');
  }

  const historyEncounterId = preserveApiIdForRequest(params.historyItem.row.encounterId);
  const prescriptionNo = String(params.historyItem.prescriptionNo ?? '').trim();
  if (!historyEncounterId || !prescriptionNo) {
    throw new TcmHistoryApplyError('历史处方信息不完整，无法引用');
  }

  const historyListRes = await params.listTcmHerbalPrescriptions({
    encounterId: historyEncounterId,
    patientId,
  });
  const historyHeader = findHistoryPrescriptionHeader(historyListRes.prescriptions, prescriptionNo);
  if (!historyHeader) {
    throw new TcmHistoryApplyError('未找到历史处方明细，无法引用');
  }

  const historyLines = [...(historyHeader.lines ?? [])].toSorted(
    (left, right) => (Number(left.sortNumber) || 0) - (Number(right.sortNumber) || 0),
  );
  if (historyLines.length === 0) {
    throw new TcmHistoryApplyError('历史处方无药材明细，无法引用');
  }

  const prepickedKeys = historyLines
    .map((line) => prepickedKeyFromHistoryLine(line))
    .filter((key): key is PrepickedProductKeyDto => key !== null);
  if (prepickedKeys.length !== historyLines.length) {
    throw new TcmHistoryApplyError('历史处方存在无效药材行，无法引用');
  }

  const { catalogOptions, locationByDefId } = await fetchTcmCatalogByPrepickedKeys(
    prepickedKeys,
    encounterId,
    patientId,
    params.pageTcmHerbalMedicationCatalog,
  );
  const catalogByDefId = new Map<string, AdviceProductOption>();
  for (const option of catalogOptions) {
    const defId = preserveApiIdForRequest(option.productDefinitionId);
    if (defId) {
      catalogByDefId.set(defId, option);
    }
  }

  const rx = params.prescription;
  // 新开/未存方：同步历史方头；已存方增量追加时跳过
  if (params.applyHeader !== false) {
    applyHistoryHeaderToPrescription(rx, historyHeader, params.currentSyndromeGroupValues ?? []);
  }

  let sortBase = rx.lines.filter((line) => !line.pendingDelete).length;
  let appliedLineCount = 0;
  let skippedDuplicateCount = 0;
  // 当前方已有 + 本批已写入的定义主键，避免同方/同批重复
  const occupiedDefIds = new Set(
    rx.lines
      .filter((line) => !line.pendingDelete && String(line.medicationDefinitionId ?? '').trim())
      .map((line) => String(line.medicationDefinitionId).trim()),
  );

  for (const historyLine of historyLines) {
    const key = prepickedKeyFromHistoryLine(historyLine);
    if (!key) {
      continue;
    }
    const catalog = catalogByDefId.get(key.productDefinitionId);
    if (!catalog) {
      const name = String(historyLine.medicationName ?? '').trim() || '未命名药材';
      throw new TcmHistoryApplyError(`目录中未找到「${name}」，无法引用历史处方`);
    }

    if (occupiedDefIds.has(key.productDefinitionId)) {
      skippedDuplicateCount += 1;
      continue;
    }

    sortBase += 1;
    const line = createEmptyLine(sortBase);
    applyCatalogProductToLine(line, catalog);
    const dose = Number(historyLine.dose);
    if (Number.isFinite(dose) && dose > 0) {
      line.dose = dose;
    }
    const dosageInstructionCode = String(historyLine.dosageInstructionCode ?? '').trim();
    if (dosageInstructionCode) {
      line.dosageInstructionCode = dosageInstructionCode;
      line.dosageInstructionText =
        String(historyLine.dosageInstructionText ?? '').trim() || undefined;
    }
    line.isNew = true;
    line.dirty = true;

    const defId = preserveApiIdForRequest(catalog.productDefinitionId) ?? '';
    const locId = locationByDefId.get(defId);
    if (defId && locId) {
      params.catalogDispenseLocationMap[defId] = locId;
      line.dispenseLocationId = locId;
    }

    rx.lines.push(line);
    occupiedDefIds.add(key.productDefinitionId);
    appliedLineCount += 1;
  }

  if (appliedLineCount === 0) {
    if (skippedDuplicateCount > 0) {
      throw new TcmHistoryApplyError('引用药材均已在当前处方中，不允许重复药品');
    }
    throw new TcmHistoryApplyError('没有可引用的药材行');
  }

  return { appliedLineCount };
}
