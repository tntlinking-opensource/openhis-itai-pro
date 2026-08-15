import type { TcmHistoryTreeItem, TcmPrescriptionUi } from './tcm-prescription.service';

import type {
  DoctorStationAdviceCatalogRowDto,
  PrepickedProductKeyDto,
} from '#/api/doctor-station/advice.types';
import type { EncounterDiagnosisRowDto } from '#/api/doctor-station/diagnosis.types';
import type {
  ListTcmRxRequest,
  ListTcmRxResponse,
  PageTcmMedCatalogRequest,
  PageTcmMedCatalogResponse,
  TcmDiagnosisGroupItemRequest,
  TcmDiagnosisGroupRowDto,
  TcmRxHeaderDto,
  TcmRxLineDto,
} from '#/api/doctor-station/tcm.types';
import type { AdviceProductOption } from '#/views/outpatient/doctor-station/advice';

import { parseEncounterDiagnosisIds } from '#/components/doctor-station/diagnosis/services/encounter-diagnosis-select';
import {
  fetchEncounterDiagnosisDisplayList,
  type DoctorStationDiagnosisScope,
} from '#/components/doctor-station/diagnosis/services/encounter-diagnosis.service';
import { isTcmDiagnosisRow } from '#/components/doctor-station/diagnosis/services/merge-encounter-diagnosis-display';
import { formatTcmDiagnosisDisplayName } from '#/components/doctor-station/diagnosis/services/tcm-diagnosis-display';
import {
  isSameTcmDiagnosisGroupPair,
  mapTcmGroupRowToSaveItem,
  resolveTcmDiagnosisHttp,
} from '#/components/doctor-station/diagnosis/services/tcm-diagnosis-groups.service';
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

/** 历史方诊断绑定到本次就诊后的方头回填 */
export interface TcmHistoryDiagnosisBinding {
  encounterDiagnosisIds: string[];
  tcmSyndromeGroup: string;
}

/** 待落到本次就诊的病证目录对（院点允许仅病或仅证） */
interface TcmHistoryDiagnosisPair {
  diagnosisDesc?: string;
  diseaseDefinitionId: string;
  diseaseName?: string;
  medicalTypeCode?: string;
  primaryFlag: number;
  syndromeDefinitionId: string;
  syndromeName?: string;
}

/** 目录对去重键：主线病+证；院点单侧时另一侧为空串 */
function historyDiagnosisPairKey(pair: TcmHistoryDiagnosisPair): string {
  return `${pair.diseaseDefinitionId}|${pair.syndromeDefinitionId}`;
}

/** 当前方是否已挂诊断（ids 或病证组号） */
function rxHasDiagnosisBinding(
  rx: Pick<TcmPrescriptionUi, 'encounterDiagnosisIds' | 'tcmSyndromeGroup'>,
): boolean {
  if (parseEncounterDiagnosisIds(rx.encounterDiagnosisIds).length > 0) {
    return true;
  }
  return String(rx.tcmSyndromeGroup ?? '').trim().length > 0;
}

/** 方头诊断是否与待绑定结果一致（相同则无需覆盖提示） */
function isSameRxDiagnosisBinding(
  rx: Pick<TcmPrescriptionUi, 'encounterDiagnosisIds' | 'tcmSyndromeGroup'>,
  binding: TcmHistoryDiagnosisBinding,
): boolean {
  const prevIds = parseEncounterDiagnosisIds(rx.encounterDiagnosisIds);
  const nextIds = binding.encounterDiagnosisIds;
  if (prevIds.length !== nextIds.length) {
    return false;
  }
  const prevSet = new Set(prevIds);
  for (const id of nextIds) {
    if (!prevSet.has(id)) {
      return false;
    }
  }
  return String(rx.tcmSyndromeGroup ?? '').trim() === String(binding.tcmSyndromeGroup ?? '').trim();
}

/** 病证组行 → 可保存目录对（至少一侧有目录 id） */
function toHistoryDiagnosisPair(group: TcmDiagnosisGroupRowDto): null | TcmHistoryDiagnosisPair {
  const diseaseDefinitionId = preserveApiIdForRequest(group.diseaseDefinitionId) ?? '';
  const syndromeDefinitionId = preserveApiIdForRequest(group.syndromeDefinitionId) ?? '';
  // 主线历史一般为病+证；院点允许仅病或仅证
  if (!diseaseDefinitionId && !syndromeDefinitionId) {
    return null;
  }
  return {
    diseaseDefinitionId,
    syndromeDefinitionId,
    diseaseName: String(group.diseaseName ?? '').trim() || undefined,
    syndromeName: String(group.syndromeName ?? '').trim() || undefined,
    diagnosisDesc: String(group.diagnosisDesc ?? '').trim() || undefined,
    medicalTypeCode: group.medicalTypeCode ?? undefined,
    primaryFlag: group.primaryFlag === 1 ? 1 : 0,
  };
}

/** 展示行 → 可保存目录对（至少一侧有目录 id） */
function toHistoryDiagnosisPairFromDisplay(
  row: EncounterDiagnosisRowDto,
): null | TcmHistoryDiagnosisPair {
  if (!isTcmDiagnosisRow(row)) {
    return null;
  }
  const diseaseDefinitionId = preserveApiIdForRequest(row.conditionDefinitionId) ?? '';
  const syndromeDefinitionId = preserveApiIdForRequest(row.syndromeDefinitionId) ?? '';
  if (!diseaseDefinitionId && !syndromeDefinitionId) {
    return null;
  }
  return {
    diseaseDefinitionId,
    syndromeDefinitionId,
    diagnosisDesc:
      String(row.conditionDefinitionName ?? row.diagnosisDesc ?? '').trim() || undefined,
    medicalTypeCode: row.medicalTypeCode ?? undefined,
    primaryFlag: Number(row.mainDiagnosisFlag) === 1 ? 1 : 0,
  };
}

/** 尝试按门诊/住院作用域拉取历史就诊病证组（可见性失败时换另一作用域） */
async function listHistoryTcmDiagnosisGroups(
  historyEncounterId: string,
  patientId: string,
  preferredScope: DoctorStationDiagnosisScope,
): Promise<TcmDiagnosisGroupRowDto[]> {
  const scopes: DoctorStationDiagnosisScope[] =
    preferredScope === 'inpatient' ? ['inpatient', 'outpatient'] : ['outpatient', 'inpatient'];
  for (const scope of scopes) {
    try {
      const res = await resolveTcmDiagnosisHttp(scope).httpListTcmDiagnosisGroups({
        encounterId: historyEncounterId,
        patientId,
      });
      return res.groups ?? [];
    } catch {
      // 历史就诊可能跨门诊/住院队列，换作用域再试
    }
  }
  return [];
}

/** 尝试拉取历史就诊诊断合并展示行 */
async function fetchHistoryDiagnosisDisplayRows(
  historyEncounterId: string,
  patientId: string,
  preferredScope: DoctorStationDiagnosisScope,
): Promise<EncounterDiagnosisRowDto[]> {
  const scopes: DoctorStationDiagnosisScope[] =
    preferredScope === 'inpatient' ? ['inpatient', 'outpatient'] : ['outpatient', 'inpatient'];
  for (const scope of scopes) {
    try {
      return await fetchEncounterDiagnosisDisplayList(
        { encounterId: historyEncounterId, patientId },
        scope,
      );
    } catch {
      // 同上：跨场景历史就诊换作用域
    }
  }
  return [];
}

/**
 * 解析历史方要带到本次的病证目录对。
 * 优先用 IF-DOR-39 detail.reuseDiagnosisGroups（无可见性门控）；再回落拉历史就诊诊断。
 */
async function resolveHistoryDiagnosisPairs(params: {
  historyEncounterId: string;
  historyHeader: TcmRxHeaderDto;
  patientId: string;
  preferredScope: DoctorStationDiagnosisScope;
}): Promise<TcmHistoryDiagnosisPair[]> {
  const pairs: TcmHistoryDiagnosisPair[] = [];
  const seen = new Set<string>();

  /** 去重追加目录对 */
  function pushPair(pair: null | TcmHistoryDiagnosisPair) {
    if (!pair) {
      return;
    }
    const key = historyDiagnosisPairKey(pair);
    if (seen.has(key)) {
      return;
    }
    seen.add(key);
    pairs.push(pair);
  }

  // 1) 历史 detail 已附带（后端无门控组装，主路径）
  for (const group of params.historyHeader.reuseDiagnosisGroups ?? []) {
    pushPair(toHistoryDiagnosisPair(group));
  }
  if (pairs.length > 0) {
    return pairs;
  }

  // 2) 回落：拉历史就诊病证组 / 展示行（可能被可见性挡住）
  const historyGroups = await listHistoryTcmDiagnosisGroups(
    params.historyEncounterId,
    params.patientId,
    params.preferredScope,
  );
  const syndromeGroup = String(params.historyHeader.tcmSyndromeGroup ?? '').trim();
  if (syndromeGroup) {
    for (const group of historyGroups) {
      if (String(group.tcmSyndromeGroup ?? '').trim() === syndromeGroup) {
        pushPair(toHistoryDiagnosisPair(group));
      }
    }
  }

  const historyDxIds = parseEncounterDiagnosisIds(params.historyHeader.encounterDiagnosisIds);
  if (pairs.length === 0 && historyDxIds.length > 0) {
    const displayRows = await fetchHistoryDiagnosisDisplayRows(
      params.historyEncounterId,
      params.patientId,
      params.preferredScope,
    );
    const idSet = new Set(historyDxIds);
    for (const row of displayRows) {
      const dxId = String(row.encounterDiagnosisId ?? '').trim();
      if (!dxId || !idSet.has(dxId)) {
        continue;
      }
      pushPair(toHistoryDiagnosisPairFromDisplay(row));
    }
  }

  if (pairs.length === 0 && historyGroups.length === 1) {
    pushPair(toHistoryDiagnosisPair(historyGroups[0]!));
  }

  return pairs;
}

/** 历史病证对 → 本次就诊新建保存子项（院点可单侧） */
function buildNewGroupSaveItemFromPair(
  pair: TcmHistoryDiagnosisPair,
  options: { primaryFlag: number; rank: number },
): TcmDiagnosisGroupItemRequest {
  const diagnosisDesc =
    pair.diagnosisDesc ||
    formatTcmDiagnosisDisplayName(pair.diseaseName, pair.syndromeName) ||
    undefined;
  return {
    diseaseDefinitionId: pair.diseaseDefinitionId || undefined,
    syndromeDefinitionId: pair.syndromeDefinitionId || undefined,
    primaryFlag: options.primaryFlag,
    rank: options.rank,
    medicalTypeCode: pair.medicalTypeCode,
    diagnosisDesc,
  };
}

/** 本次就诊列表是否已有相同病证（主线病+证；院点按已选侧） */
function encounterHasSameDiagnosisPair(
  groups: TcmDiagnosisGroupRowDto[],
  saveItems: TcmDiagnosisGroupItemRequest[],
  source: TcmHistoryDiagnosisPair,
): boolean {
  if (
    groups.some((row) =>
      isSameTcmDiagnosisGroupPair(row, source.diseaseDefinitionId, source.syndromeDefinitionId),
    )
  ) {
    return true;
  }
  return saveItems.some(
    (item) =>
      String(item.diseaseDefinitionId ?? '').trim() === source.diseaseDefinitionId &&
      String(item.syndromeDefinitionId ?? '').trim() === source.syndromeDefinitionId,
  );
}

/** 从本次就诊病证组/展示行解析方头可绑定 ids 与组号 */
function bindIdsFromCurrentEncounter(
  sourcePairs: TcmHistoryDiagnosisPair[],
  currentGroups: TcmDiagnosisGroupRowDto[],
  displayRows: EncounterDiagnosisRowDto[],
): TcmHistoryDiagnosisBinding | null {
  const encounterDiagnosisIds: string[] = [];
  let tcmSyndromeGroup = '';
  for (const source of sourcePairs) {
    const matchedGroup = currentGroups.find((row) =>
      isSameTcmDiagnosisGroupPair(row, source.diseaseDefinitionId, source.syndromeDefinitionId),
    );
    if (!matchedGroup) {
      continue;
    }
    const groupKey = String(matchedGroup.tcmSyndromeGroup ?? '').trim();
    if (!tcmSyndromeGroup && groupKey) {
      tcmSyndromeGroup = groupKey;
    }
    // 展示行优先按组号匹配，回落目录对（含院点单侧空串）
    const display =
      displayRows.find((row) => {
        if (!isTcmDiagnosisRow(row)) {
          return false;
        }
        const rowGroup = String(row.tcmSyndromeGroup ?? '').trim();
        if (groupKey && rowGroup && groupKey === rowGroup) {
          return true;
        }
        return (
          String(row.conditionDefinitionId ?? '').trim() === source.diseaseDefinitionId &&
          String(row.syndromeDefinitionId ?? '').trim() === source.syndromeDefinitionId
        );
      }) ?? null;
    const dxId = String(display?.encounterDiagnosisId ?? '').trim();
    if (dxId && !encounterDiagnosisIds.includes(dxId)) {
      encounterDiagnosisIds.push(dxId);
    }
  }

  if (encounterDiagnosisIds.length === 0 && !tcmSyndromeGroup) {
    return null;
  }
  return { encounterDiagnosisIds, tcmSyndromeGroup };
}

/**
 * 把历史方诊断落到本次就诊并解析方头绑定。
 * - 就诊列表已有相同：不落库，直接解析本次 ids 覆盖到方头
 * - 就诊列表没有：先保存就诊诊断，再绑定方头
 */
export async function resolveTcmHistoryDiagnosisBinding(params: {
  currentEncounterId: string;
  historyEncounterId: string;
  historyHeader: TcmRxHeaderDto;
  patientId: string;
  stationScope?: DoctorStationDiagnosisScope;
}): Promise<TcmHistoryDiagnosisBinding | null> {
  const currentEncounterId = params.currentEncounterId.trim();
  const historyEncounterId = params.historyEncounterId.trim();
  const patientId = params.patientId.trim();
  if (!currentEncounterId || !historyEncounterId || !patientId) {
    return null;
  }
  const scope = params.stationScope ?? 'outpatient';

  // 同就诊：历史方诊断 id / 组号本身即有效，直接覆盖到当前方
  if (currentEncounterId === historyEncounterId) {
    const ids = parseEncounterDiagnosisIds(params.historyHeader.encounterDiagnosisIds);
    const syndromeGroup = String(params.historyHeader.tcmSyndromeGroup ?? '').trim();
    if (ids.length === 0 && !syndromeGroup) {
      return null;
    }
    return {
      encounterDiagnosisIds: ids,
      tcmSyndromeGroup: syndromeGroup,
    };
  }

  const sourcePairs = await resolveHistoryDiagnosisPairs({
    historyEncounterId,
    historyHeader: params.historyHeader,
    patientId,
    preferredScope: scope,
  });
  if (sourcePairs.length === 0) {
    return null;
  }

  const tcmHttp = resolveTcmDiagnosisHttp(scope);
  const currentListRes = await tcmHttp.httpListTcmDiagnosisGroups({
    encounterId: currentEncounterId,
    patientId,
  });
  let currentGroups = currentListRes.groups ?? [];

  // 仅对「就诊列表尚无」的历史诊断追加落库；已有相同的不调用保存
  const saveItems: TcmDiagnosisGroupItemRequest[] = currentGroups
    .map((row) => mapTcmGroupRowToSaveItem(row))
    .filter((item): item is TcmDiagnosisGroupItemRequest => item !== null);
  const hasMain = saveItems.some((item) => item.primaryFlag === 1);
  let maxRank = saveItems.reduce((max, item) => Math.max(max, item.rank), 0);
  let appended = 0;
  for (const source of sourcePairs) {
    if (encounterHasSameDiagnosisPair(currentGroups, saveItems, source)) {
      continue;
    }
    maxRank += 1;
    saveItems.push(
      buildNewGroupSaveItemFromPair(source, {
        rank: maxRank,
        // 本次尚无主诊时，首个带入组作主诊
        primaryFlag: !hasMain && appended === 0 ? 1 : 0,
      }),
    );
    appended += 1;
  }

  if (appended > 0) {
    await tcmHttp.httpSaveTcmDiagnosisGroupsBatch({
      encounterId: currentEncounterId,
      patientId,
      groups: saveItems,
    });
    const refreshed = await tcmHttp.httpListTcmDiagnosisGroups({
      encounterId: currentEncounterId,
      patientId,
    });
    currentGroups = refreshed.groups ?? [];
  }

  const displayRows = await fetchEncounterDiagnosisDisplayList(
    { encounterId: currentEncounterId, patientId },
    scope,
  );
  return bindIdsFromCurrentEncounter(sourcePairs, currentGroups, displayRows);
}

/** 将历史方头可复用字段写入当前草稿方 */
function applyHistoryHeaderToPrescription(
  rx: TcmPrescriptionUi,
  historyHeader: TcmRxHeaderDto,
  currentSyndromeGroupValues: string[],
  diagnosisBinding?: null | TcmHistoryDiagnosisBinding,
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
  // 历史诊断绑定整方覆盖当前方（不追加）；无绑定时仅在同就诊组号仍有效时回填
  if (diagnosisBinding) {
    rx.encounterDiagnosisIds = [...diagnosisBinding.encounterDiagnosisIds];
    rx.tcmSyndromeGroup = diagnosisBinding.tcmSyndromeGroup;
  } else {
    const syndromeGroup = String(historyHeader.tcmSyndromeGroup ?? '').trim();
    if (syndromeGroup && currentSyndromeGroupValues.includes(syndromeGroup)) {
      rx.tcmSyndromeGroup = syndromeGroup;
    }
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
  /**
   * 兼容回落：历史行无 detail 时再按历史就诊拉 list。
   * 住院站对历史就诊会 assertVisible 失败，优先依赖 historyItem.row.detail。
   */
  listTcmHerbalPrescriptions?: (body: ListTcmRxRequest) => Promise<ListTcmRxResponse>;
  pageTcmHerbalMedicationCatalog: (
    body: PageTcmMedCatalogRequest,
  ) => Promise<PageTcmMedCatalogResponse>;
  patientId: string;
  prescription: TcmPrescriptionUi;
  /** 门诊/住院作用域：写方头时用于历史诊断落到本次就诊 */
  stationScope?: DoctorStationDiagnosisScope;
}): Promise<{
  appliedLineCount: number;
  /** 历史诊断已覆盖当前方原有诊断（需轻提示） */
  diagnosisCoveredExisting: boolean;
  diagnosisBinding: null | TcmHistoryDiagnosisBinding;
}> {
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

  // 优先用历史列表已附带的完整明细，避免再调 list（住院可见性会挡历史就诊）
  let historyHeader: TcmRxHeaderDto | undefined = params.historyItem.row.detail ?? undefined;
  if (historyHeader) {
    const detailNo = String(historyHeader.prescriptionNo ?? '').trim();
    // 带处方号时须与侧栏节点一致；无处方号仍可用（明细已挂在该历史行上）
    if (detailNo && detailNo !== prescriptionNo) {
      historyHeader = undefined;
    }
  }
  if (!historyHeader && params.listTcmHerbalPrescriptions) {
    const historyListRes = await params.listTcmHerbalPrescriptions({
      encounterId: historyEncounterId,
      patientId,
    });
    historyHeader = findHistoryPrescriptionHeader(historyListRes.prescriptions, prescriptionNo);
  }
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
  let diagnosisBinding: null | TcmHistoryDiagnosisBinding = null;
  let diagnosisCoveredExisting = false;
  // 新开/未存方：同步历史方头；已存方增量追加时跳过
  if (params.applyHeader !== false) {
    // 覆盖前提：当前方是否已有诊断（与历史绑定不同则轻提示）
    const hadRxDiagnosis = rxHasDiagnosisBinding(rx);
    // 写方头时把历史诊断落到本次就诊（失败不阻断药材引用）
    try {
      diagnosisBinding = await resolveTcmHistoryDiagnosisBinding({
        currentEncounterId: encounterId,
        historyEncounterId,
        historyHeader,
        patientId,
        stationScope: params.stationScope,
      });
    } catch {
      diagnosisBinding = null;
    }
    if (diagnosisBinding && hadRxDiagnosis && !isSameRxDiagnosisBinding(rx, diagnosisBinding)) {
      diagnosisCoveredExisting = true;
    }
    applyHistoryHeaderToPrescription(
      rx,
      historyHeader,
      params.currentSyndromeGroupValues ?? [],
      diagnosisBinding,
    );
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

  return { appliedLineCount, diagnosisBinding, diagnosisCoveredExisting };
}
