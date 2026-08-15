import type {
  SaveTcmRxDraftBatchRequest,
  SignTcmRxBatchRequest,
  TcmDiagnosisGroupRowDto,
  TcmRxDraftHeaderRequest,
  TcmRxDraftLineRequest,
  TcmRxHeaderDto,
  TcmRxHistoryRowDto,
  TcmRxLineDto,
  WithdrawOrDeleteTcmRxRequest,
} from '#/api/doctor-station/tcm.types';
import type { DiagnosisItem } from '#/components/doctor-station/diagnosis';
import type { AdviceProductOption } from '#/views/outpatient/doctor-station/advice';

import { formatTcmDiagnosisDisplayName } from '#/components/doctor-station/diagnosis/services/tcm-diagnosis-display';
import { parseEncounterDiagnosisIds } from '#/components/doctor-station/diagnosis/services/encounter-diagnosis-select';
import {
  convertProductUnitPriceBetweenBandKinds,
  normalizeProductUnitBandsForPrice,
  parseProductUnitBandPrice,
  PRODUCT_CATALOG_PRICE_SOURCE_BAND_KIND,
} from '#/utils/doctor-station/product-unit-band-price';
import { decimalMul } from '#/utils/format/decimal-util';
import { calcMoneyLineTotal, sumMoneyThenCalc } from '#/utils/format/money-scale';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { convertInboundQtyBetweenBandKinds } from '#/utils/inventory/inbound-product-unit-bands';
import {
  unitCodeByBandKind,
  unitNameByBandKind,
} from '#/views/outpatient/doctor-station/advice/services/medication-unit-bands';

/** 方状态：草稿可编辑 */
export const TCM_RX_STATUS_DRAFT = 'DRAFT';

/** 方状态：已停嘱 */
export const TCM_RX_STATUS_STOPPED = 'STOPPED';

/**
 * 代煎 companion 诊疗项目名称（与后端 DoctorStationTcmConstants.DECOCTION_ACTIVITY_NAME 一致）。
 * 签发后代煎费挂在开立医嘱列表；中药处方页同步展示费用。
 */
export const TCM_DECOCTION_ACTIVITY_NAME = '代煎中药';

/** 住院长临：长期 */
export const TCM_THERAPY_LONG_TERM = 'LONG_TERM';

/** 住院长临：临时 */
export const TCM_THERAPY_TEMPORARY = 'TEMPORARY';

/** 住院临嘱立即频次码 */
export const INPATIENT_IMMEDIATE_RATE_CODE = 'STAT';

/** 签退动作 */
export const TCM_RX_ACTION_WITHDRAW = 'WITHDRAW';

/** 删除动作 */
export const TCM_RX_ACTION_DELETE = 'DELETE';

/** 整方范围 */
export const TCM_RX_SCOPE_PRESCRIPTION = 'PRESCRIPTION';

/** 处方明细行 UI 态（字段与后端读模型对齐，另含仅前端使用的编辑态） */
export interface TcmPrescriptionLineUi {
  clientLineKey: string;
  medicationRequestId?: string;
  medicationDefinitionId: string;
  medicationName: string;
  dose: number;
  doseUnitCode: string;
  /** 单剂用量单位展示名（如 g）；落库仍用 doseUnitCode */
  doseUnitName?: string;
  quantity?: number;
  unitCode?: string;
  sortNumber: number;
  retailPrice: number;
  dispenseLocationId?: string;
  /** 行级费用态码（ChargeItemStatus） */
  chargeStatusEnum?: string;
  chargeStatusText?: string;
  lineStatusEnum?: string;
  /** 待提交删除的行 */
  pendingDelete?: boolean;
  /** 行内容相对上次暂存有改动 */
  dirty?: boolean;
  /** 新建尚未落库 */
  isNew?: boolean;
  /** 特殊煎法术语 code（dosage_instruction） */
  dosageInstructionCode?: string;
  /** 特殊煎法展示名 */
  dosageInstructionText?: string;
  /** 医保等级码（CHRGITM_LV，目录带出仅展示） */
  chrgitmLv?: number | string;
  /** 医保等级展示名（甲类/乙类/自费等） */
  chrgitmLvText?: string;
  /** 行级费用性质/合同主键（以行为主，自费等级仅本行切自费） */
  contractId?: string;
  /** 行级费用性质展示名 */
  feeNatureText?: string;
  /**
   * 选品时当前 FIFO 批剩余（包装单位数值）；改剂量时软校验请求量是否超批。
   * 仅前端缓存，不落库。
   */
  catalogCurrentLotPackageQty?: number;
  /** 选品时单位槽快照，供剂量→包装换算软校验；不落库 */
  catalogUnitBands?: AdviceProductOption['unitBands'];
  /** 选品时包装单位编码快照；不落库 */
  catalogPackageUnitCode?: string;
}

/** 处方方头 UI 态 */
export interface TcmPrescriptionUi {
  prescriptionGroupKey: string;
  prescriptionNo?: string;
  /** 住院方头长临 */
  therapyEnum?: string;
  tcmSyndromeGroup: string;
  /** 方头绑定就诊诊断 id（多选，与西医开嘱同口径） */
  encounterDiagnosisIds: string[];
  chineseHerbsDoseQuantity?: number;
  sufferingFlag: boolean;
  methodCode: string;
  methodName?: string;
  rateCode: string;
  rateName?: string;
  days?: number;
  remark: string;
  statusEnum: string;
  /** 方状态展示码（退费态优先于请求态；签发态与费用态分列后仅兼容保留） */
  displayStatusEnum?: string;
  /** 方状态展示文案 */
  displayStatusEnumText?: string;
  /** 方级费用态码（ChargeItemStatus 归并） */
  chargeStatusEnum?: string;
  /** 方级费用态展示文案 */
  chargeStatusText?: string;
  /** 方头默认费用性质/合同主键（新开行回落；行可各自覆盖） */
  contractId?: string;
  /** 方头默认费用性质展示名 */
  feeNatureText?: string;
  lines: TcmPrescriptionLineUi[];
  /** 方头相对上次暂存有改动 */
  headerDirty?: boolean;
}

/** 历史/组套侧栏 Tooltip 单味药 */
export interface TcmHerbTooltipItem {
  /** 药名 */
  name: string;
  /** 单剂克数文案，如 15g；无剂量时为空 */
  doseText: string;
}

/** 历史处方侧栏节点 */
export interface TcmHistoryTreeItem {
  id: string;
  prescriptionNo: string;
  summaryText: string;
  herbSummary: string;
  /** hover 结构化药材（含克数，优先来自 detail.lines） */
  herbItems: TcmHerbTooltipItem[];
  /** 树节点展示：味数 · 开立人 · 日期 */
  treeLabel: string;
  authoredByName: string;
  openedDateText: string;
  /** 是否本次就诊（侧栏「本次」标签） */
  currentVisit: boolean;
  row: TcmRxHistoryRowDto;
}

function toNum(value: unknown, fallback = 0): number {
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  if (value === null || value === undefined || value === '') return fallback;
  const n = Number(value);
  return Number.isFinite(n) ? n : fallback;
}

/** 读模型数量字段：无值时不落默认 1 */
function toOptionalPositiveNum(value: unknown): number | undefined {
  if (value === null || value === undefined || value === '') return undefined;
  const n = Number(value);
  if (!Number.isFinite(n) || n <= 0) return undefined;
  return n;
}

function toStr(value: unknown): string {
  if (value === null || value === undefined) return '';
  return String(value).trim();
}

/** 中药明细行 sortNumber 排序序（缺失时置后） */
function tcmLineSortSeq(sortNumber: number | undefined): number {
  if (typeof sortNumber === 'number' && Number.isFinite(sortNumber) && sortNumber > 0) {
    return sortNumber;
  }
  return Number.MAX_SAFE_INTEGER;
}

/** 按 sortNumber、medicationRequestId 稳定排序方内药材行 */
function sortTcmPrescriptionLines<T extends { medicationRequestId?: string; sortNumber: number }>(
  lines: readonly T[],
): T[] {
  return [...lines].toSorted((left, right) => {
    const sortCompare = tcmLineSortSeq(left.sortNumber) - tcmLineSortSeq(right.sortNumber);
    if (sortCompare !== 0) {
      return sortCompare;
    }
    return String(left.medicationRequestId ?? '').localeCompare(
      String(right.medicationRequestId ?? ''),
    );
  });
}

/** 生成前端方标识 */
export function createPrescriptionGroupKey(): string {
  return `tcm-rx-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

/** 生成明细行前端 key */
export function createClientLineKey(): string {
  return `tcm-line-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

/** 病证组下拉展示文案（仅病名（证候），不展示组号） */
export function formatTcmSyndromeGroupOptionLabel(group: TcmDiagnosisGroupRowDto): string {
  const label = formatTcmDiagnosisDisplayName(group.diseaseName, group.syndromeName);
  return label || '未命名病证组';
}

/** 处方页签展示名 */
export function resolvePrescriptionTabLabel(rx: TcmPrescriptionUi, index: number): string {
  const no = toStr(rx.prescriptionNo);
  if (no) return no;
  return `中药处方${index + 1}`;
}

/** 方是否只读（非草稿不可编辑） */
export function isTcmPrescriptionReadonly(rx: null | TcmPrescriptionUi | undefined): boolean {
  if (!rx) return true;
  const status = toStr(rx.statusEnum).toUpperCase();
  return status !== '' && status !== TCM_RX_STATUS_DRAFT;
}

/** 住院长期方是否可停嘱 */
export function canStopLongTermTcmPrescription(rx: null | TcmPrescriptionUi | undefined): boolean {
  if (!rx) return false;
  const therapy = toStr(rx.therapyEnum).toUpperCase();
  if (therapy !== TCM_THERAPY_LONG_TERM) return false;
  const prescriptionNo = toStr(rx.prescriptionNo);
  if (!prescriptionNo) return false;
  const status = toStr(rx.statusEnum).toUpperCase();
  return status === 'ACTIVE' || status === 'VERIFIED';
}

/** 住院长期方是否可取消停嘱 */
export function canCancelStopLongTermTcmPrescription(
  rx: null | TcmPrescriptionUi | undefined,
): boolean {
  if (!rx) return false;
  const therapy = toStr(rx.therapyEnum).toUpperCase();
  if (therapy !== TCM_THERAPY_LONG_TERM) return false;
  const prescriptionNo = toStr(rx.prescriptionNo);
  if (!prescriptionNo) return false;
  return toStr(rx.statusEnum).toUpperCase() === TCM_RX_STATUS_STOPPED;
}

/** 方签发态 Tag 类型（仅认 statusEnum，退费走费用状态列） */
export function resolveTcmRxStatusTagType(
  statusEnum?: null | string,
): '' | 'danger' | 'info' | 'success' | 'warning' {
  const status = toStr(statusEnum).toUpperCase();
  if (status === 'ACTIVE' || status === 'COMPLETED') return 'success';
  if (status === 'DRAFT') return 'info';
  if (status === 'STOPPED') return 'danger';
  if (status === 'CANCELLED' || status === 'REVOKED') return 'warning';
  if (status === 'ENTERED_IN_ERROR') return 'danger';
  return '';
}

/** 方签发态展示文案（与费用态分列，不再用退费 displayStatus 覆盖） */
export function resolveTcmRxStatusLabel(statusEnum?: null | string): string {
  const status = toStr(statusEnum).toUpperCase();
  const map: Record<string, string> = {
    DRAFT: '草稿',
    ACTIVE: '已签发',
    VERIFIED: '已校对',
    STOPPED: '已停嘱',
    COMPLETED: '已完成',
    CANCELLED: '已作废',
    REVOKED: '已撤回',
    ENTERED_IN_ERROR: '录入错误',
  };
  return map[status] ?? (status || '草稿');
}

/**
 * 方级费用状态展示文案：草稿/空统一「—」，与开立医嘱列表口径一致。
 */
export function resolveTcmRxChargeStatusLabel(
  chargeStatusEnum?: null | string,
  chargeStatusText?: null | string,
): string {
  const code = toStr(chargeStatusEnum).toUpperCase();
  if (!code || code === 'DRAFT') {
    return '—';
  }
  return toStr(chargeStatusText) || code || '—';
}

/** 读模型方头 → UI */
export function mapApiHeaderToUi(dto: TcmRxHeaderDto): TcmPrescriptionUi {
  const groupKey = toStr(dto.prescriptionGroupKey) || createPrescriptionGroupKey();
  return {
    prescriptionGroupKey: groupKey,
    prescriptionNo: toStr(dto.prescriptionNo) || undefined,
    therapyEnum: toStr(dto.therapyEnum) || undefined,
    tcmSyndromeGroup: toStr(dto.tcmSyndromeGroup),
    encounterDiagnosisIds: parseEncounterDiagnosisIds(dto.encounterDiagnosisIds),
    chineseHerbsDoseQuantity: toOptionalPositiveNum(dto.chineseHerbsDoseQuantity),
    sufferingFlag: Boolean(dto.sufferingFlag),
    methodCode: toStr(dto.methodCode),
    methodName: toStr(dto.methodName) || undefined,
    rateCode: toStr(dto.rateCode),
    rateName: toStr(dto.rateName) || undefined,
    days: toOptionalPositiveNum(dto.days),
    remark: toStr(dto.remark),
    statusEnum: toStr(dto.statusEnum) || TCM_RX_STATUS_DRAFT,
    displayStatusEnum: toStr(dto.displayStatusEnum) || undefined,
    displayStatusEnumText: toStr(dto.displayStatusEnumText) || undefined,
    chargeStatusEnum: toStr(dto.chargeStatusEnum) || undefined,
    chargeStatusText: toStr(dto.chargeStatusText) || undefined,
    contractId: toStr(dto.contractId) || undefined,
    feeNatureText: toStr(dto.feeNatureText) || undefined,
    lines: sortTcmPrescriptionLines(dto.lines ?? []).map((line, index) =>
      mapApiLineToUi(line, index),
    ),
    headerDirty: false,
  };
}

/** 读模型明细行 → UI */
export function mapApiLineToUi(dto: TcmRxLineDto, index: number): TcmPrescriptionLineUi {
  const dose = toNum(dto.dose, 0);
  const quantity = toNum(dto.quantity, 0);
  const doseUnitCode = toStr(dto.doseUnitCode) || 'g';
  const doseUnitName = toStr(dto.doseUnitName) || undefined;
  return {
    clientLineKey: createClientLineKey(),
    medicationRequestId: preserveApiIdForRequest(dto.medicationRequestId) || undefined,
    medicationDefinitionId: preserveApiIdForRequest(dto.medicationDefinitionId) ?? '',
    medicationName: toStr(dto.medicationName),
    dose,
    doseUnitCode,
    doseUnitName,
    quantity: quantity || undefined,
    unitCode: toStr(dto.unitCode) || doseUnitCode || undefined,
    sortNumber: dto.sortNumber ?? index + 1,
    retailPrice: toNum(dto.retailUnitPrice, 0),
    chargeStatusEnum: toStr(dto.chargeStatusEnum) || undefined,
    chargeStatusText: toStr(dto.chargeStatusText) || undefined,
    lineStatusEnum: toStr(dto.lineStatusEnum) || undefined,
    pendingDelete: false,
    dirty: false,
    isNew: false,
    dosageInstructionCode: toStr(dto.dosageInstructionCode) || undefined,
    dosageInstructionText: toStr(dto.dosageInstructionText) || undefined,
    contractId: toStr(dto.contractId) || undefined,
    feeNatureText: toStr(dto.feeNatureText) || undefined,
  };
}

/** 历史开立时间格式化为天（YYYY-MM-DD） */
function formatHistoryOpenedDateText(reqOpenedTime: unknown): string {
  const raw = toStr(reqOpenedTime);
  if (!raw) {
    return '';
  }
  // LocalDateTime / ISO 字符串取日期段
  if (raw.length >= 10 && /^\d{4}-\d{2}-\d{2}/.test(raw)) {
    return raw.slice(0, 10);
  }
  return raw;
}

/** 历史处方树节点展示文案：味数 · 开立人 · 日期 */
export function formatTcmHistoryTreeLabel(params: {
  authoredByName?: string;
  openedDateText?: string;
  summaryText: string;
}): string {
  const parts = [params.summaryText];
  const author = toStr(params.authoredByName);
  if (author) {
    parts.push(author);
  }
  const dateText = toStr(params.openedDateText);
  if (dateText) {
    parts.push(dateText);
  }
  return parts.join(' · ');
}

/** 历史明细行 → 单剂克数展示（与药卡克数整数口径一致） */
function formatHistoryLineDoseText(line: TcmRxLineDto): string {
  const dose = toOptionalPositiveNum(line.dose);
  if (dose == null) {
    return '';
  }
  const unit = toStr(line.doseUnitName) || toStr(line.doseUnitCode) || 'g';
  return `${Math.round(dose)}${unit}`;
}

/** 历史方 → Tooltip 药材列表（优先 detail 带克数，否则仅药名） */
export function buildHistoryHerbTooltipItems(row: TcmRxHistoryRowDto): TcmHerbTooltipItem[] {
  const detailLines = row.detail?.lines ?? [];
  if (detailLines.length > 0) {
    // 按 detail 行组装：药名 + 单剂克数
    const fromDetail: TcmHerbTooltipItem[] = [];
    for (const line of detailLines) {
      const name = toStr(line.medicationName);
      if (!name) {
        continue;
      }
      fromDetail.push({
        name,
        doseText: formatHistoryLineDoseText(line),
      });
    }
    if (fromDetail.length > 0) {
      return fromDetail;
    }
  }
  // 无 detail 时回落药名列表（无克数）
  return (row.medicationNames ?? [])
    .map((name) => toStr(name))
    .filter((name) => Boolean(name))
    .map((name) => ({ name, doseText: '' }));
}

/** Tooltip 药材列表 → 搜索用摘要文案 */
function formatHerbSummaryFromItems(items: readonly TcmHerbTooltipItem[]): string {
  return items
    .map((item) => (item.doseText ? `${item.name} ${item.doseText}` : item.name))
    .join('，');
}

/** 历史处方 → 侧栏节点 */
export function mapHistoryRowToTreeItem(
  row: TcmRxHistoryRowDto,
  index: number,
  options?: { currentEncounterId?: string },
): TcmHistoryTreeItem {
  const prescriptionNo = toStr(row.prescriptionNo) || `history-${index}`;
  const herbItems = buildHistoryHerbTooltipItems(row);
  const summaryText = toStr(row.summaryText) || prescriptionNo;
  const authoredByName = toStr(row.authoredByName);
  const openedDateText = formatHistoryOpenedDateText(row.reqOpenedTime);
  const rowEncounterId = preserveApiIdForRequest(row.encounterId);
  const currentEncounterId = preserveApiIdForRequest(options?.currentEncounterId);
  // 优先后端 currentEncounterFlag；缺省时按就诊 id 对照
  let currentVisit = row.currentEncounterFlag === true;
  if (!currentVisit && currentEncounterId && rowEncounterId) {
    currentVisit = rowEncounterId === currentEncounterId;
  }
  return {
    id: `history-${prescriptionNo}-${index}`,
    prescriptionNo,
    summaryText,
    herbItems,
    herbSummary: formatHerbSummaryFromItems(herbItems),
    authoredByName,
    openedDateText,
    currentVisit,
    treeLabel: formatTcmHistoryTreeLabel({
      summaryText,
      authoredByName,
      openedDateText,
    }),
    row,
  };
}

/** 空白处方 */
export function createEmptyPrescription(
  tcmSyndromeGroup = '',
  chineseHerbsDoseQuantity?: number,
  defaultMethodCode = '',
  defaultRateCode = '',
  therapyEnum?: string,
  defaultContractId = '',
  defaultFeeNatureText = '',
): TcmPrescriptionUi {
  return {
    prescriptionGroupKey: createPrescriptionGroupKey(),
    therapyEnum: therapyEnum || undefined,
    tcmSyndromeGroup,
    encounterDiagnosisIds: [],
    chineseHerbsDoseQuantity,
    sufferingFlag: false,
    methodCode: defaultMethodCode,
    rateCode: defaultRateCode,
    days: undefined,
    remark: '',
    statusEnum: TCM_RX_STATUS_DRAFT,
    contractId: toStr(defaultContractId) || undefined,
    feeNatureText: toStr(defaultFeeNatureText) || undefined,
    lines: [],
    headerDirty: false,
  };
}

/**
 * 由已选就诊诊断推导方头病证组号（取首个带组号的中医项，供后端校验/content_json）。
 */
export function resolveTcmSyndromeGroupFromDiagnosisIds(
  selectedIds: string[],
  options: DiagnosisItem[],
): string {
  const idSet = new Set(parseEncounterDiagnosisIds(selectedIds));
  for (const item of options) {
    const id = String(item.encounterDiagnosisId ?? item.id ?? '').trim();
    if (!id || !idSet.has(id)) {
      continue;
    }
    const group = String(item.tcmSyndromeGroup ?? '').trim();
    if (group) {
      return group;
    }
  }
  return '';
}

/**
 * 方头诊断多选回显：优先已落库 ids；否则按病证组号匹配候选项。
 */
export function resolveTcmPrescriptionDiagnosisIds(
  rx: Pick<TcmPrescriptionUi, 'encounterDiagnosisIds' | 'tcmSyndromeGroup'>,
  options: DiagnosisItem[],
): string[] {
  const fromRx = parseEncounterDiagnosisIds(rx.encounterDiagnosisIds);
  if (fromRx.length > 0) {
    const valid = new Set(
      options
        .map((item) => String(item.encounterDiagnosisId ?? item.id ?? '').trim())
        .filter(Boolean),
    );
    const kept = fromRx.filter((id) => valid.has(id));
    if (kept.length > 0) {
      return kept;
    }
  }
  const group = toStr(rx.tcmSyndromeGroup);
  if (!group) {
    return [];
  }
  const hit = options.find((item) => String(item.tcmSyndromeGroup ?? '').trim() === group);
  const id = String(hit?.encounterDiagnosisId ?? hit?.id ?? '').trim();
  return id ? [id] : [];
}

/** 空白明细行 */
export function createEmptyLine(sortNumber: number): TcmPrescriptionLineUi {
  return {
    clientLineKey: createClientLineKey(),
    medicationDefinitionId: '',
    medicationName: '',
    dose: 0,
    doseUnitCode: 'g',
    doseUnitName: 'g',
    sortNumber,
    retailPrice: 0,
    isNew: true,
    dirty: true,
    dosageInstructionCode: '',
  };
}

/** 饮片目录 → 单剂用量单位与单价（优先 unitBands.DOSE） */
export function resolveTcmDoseUnitFromProduct(product: AdviceProductOption): {
  doseUnitCode: string;
  doseUnitName: string;
  retailPrice: number;
} {
  const bands = product.unitBands ?? [];
  const doseUnitCode =
    unitCodeByBandKind(bands, 'DOSE') ||
    toStr(product.defaultUnitCode) ||
    toStr(product.spec) ||
    'g';
  const doseUnitName =
    unitNameByBandKind(bands, 'DOSE') ||
    toStr(product.unitName) ||
    toStr(product.spec) ||
    doseUnitCode;
  const priceBands = normalizeProductUnitBandsForPrice(bands);
  const doseBand = priceBands.find(
    (band) =>
      String(band.bandKind ?? '')
        .trim()
        .toUpperCase() === 'DOSE',
  );
  let retailPrice = parseProductUnitBandPrice(doseBand);
  if (retailPrice === undefined) {
    const catalogPrice = toNum(product.catalogUnitPrice ?? product.price, 0);
    retailPrice =
      catalogPrice > 0 && priceBands.length > 0
        ? (convertProductUnitPriceBetweenBandKinds(
            catalogPrice,
            PRODUCT_CATALOG_PRICE_SOURCE_BAND_KIND,
            'DOSE',
            priceBands,
          ) ?? catalogPrice)
        : catalogPrice;
  }
  return { doseUnitCode, doseUnitName, retailPrice };
}

/** 单剂用量 × 付数 → 请求总量（与后端 quantity 口径一致） */
export function calcLineTotalQuantity(
  line: Pick<TcmPrescriptionLineUi, 'dose'>,
  chineseHerbsDoseQuantity: number,
): number {
  const doseQty = toNum(chineseHerbsDoseQuantity, 1);
  return decimalMul(toNum(line.dose, 0), doseQty > 0 ? doseQty : 1);
}

/**
 * 将剂量单位下的请求总量换算为包装单位数量，供与目录 availableQuantity 对比。
 * 无法换算时返回 undefined（跳过软校验）。
 */
export function convertTcmRequestDoseQtyToPackage(
  requestDoseQty: number,
  product: Pick<AdviceProductOption, 'defaultUnitCode' | 'unitBands'>,
  doseUnitCode: string,
): number | undefined {
  const qty = Number(requestDoseQty);
  if (!Number.isFinite(qty) || qty < 0) {
    return undefined;
  }
  const bands = product.unitBands ?? [];
  if (bands.length > 0) {
    const converted = convertInboundQtyBetweenBandKinds(qty, 'DOSE', 'PACKAGE', bands);
    if (converted !== undefined && Number.isFinite(converted)) {
      return converted;
    }
  }
  const packageCode = unitCodeByBandKind(bands, 'PACKAGE') || toStr(product.defaultUnitCode);
  const doseCode = toStr(doseUnitCode);
  // 剂量单位与包装单位编码一致时直接比
  if (packageCode && doseCode && packageCode === doseCode) {
    return qty;
  }
  return undefined;
}

/**
 * 已写入行后，按选品时缓存的目录可开量判断请求量是否超限。
 */
export function isTcmLineRequestExceedCurrentLot(
  line: Pick<
    TcmPrescriptionLineUi,
    | 'catalogCurrentLotPackageQty'
    | 'catalogPackageUnitCode'
    | 'catalogUnitBands'
    | 'dose'
    | 'doseUnitCode'
  >,
  chineseHerbsDoseQuantity: number,
): boolean {
  const cap = line.catalogCurrentLotPackageQty;
  if (cap === undefined || !Number.isFinite(cap)) {
    return false;
  }
  const requestDoseQty = calcLineTotalQuantity(line, chineseHerbsDoseQuantity);
  const requestPackage = convertTcmRequestDoseQtyToPackage(
    requestDoseQty,
    {
      unitBands: line.catalogUnitBands,
      defaultUnitCode: line.catalogPackageUnitCode,
    },
    line.doseUnitCode,
  );
  if (requestPackage === undefined) {
    return false;
  }
  return requestPackage > cap;
}

/**
 * 列出请求量超过目录可用库存的药材行（依赖选品缓存的 catalogCurrentLotPackageQty）。
 */
export function listTcmLinesExceedingCurrentLot(
  lines: TcmPrescriptionLineUi[],
  chineseHerbsDoseQuantity: number,
): TcmPrescriptionLineUi[] {
  return lines.filter((row) => {
    if (row.pendingDelete) {
      return false;
    }
    if (!String(row.medicationDefinitionId ?? '').trim()) {
      return false;
    }
    return isTcmLineRequestExceedCurrentLot(row, chineseHerbsDoseQuantity);
  });
}

/**
 * 暂存/签发前：校验是否有药材用量超过可用库存；无超限返回 null。
 */
export function validatePrescriptionStockExceed(
  lines: TcmPrescriptionLineUi[],
  chineseHerbsDoseQuantity: number,
): null | string {
  const over = listTcmLinesExceedingCurrentLot(lines, chineseHerbsDoseQuantity);
  if (over.length === 0) {
    return null;
  }
  const names = over
    .map((row) => {
      const name = String(row.medicationName ?? '').trim();
      return name || '未命名药材';
    })
    .join('、');
  return `以下药材用量超过可用库存：${names}。请减少剂量/付数`;
}

/** 解析选品时目录可开量（各批合计，包装单位）。 */
export function parseCatalogCurrentLotPackageQty(
  product: Pick<AdviceProductOption, 'availableQuantity'>,
): number | undefined {
  const raw = product.availableQuantity;
  if (raw === null || raw === undefined || String(raw).trim() === '') {
    return undefined;
  }
  const n = Number(raw);
  return Number.isFinite(n) ? n : undefined;
}

/** 目录选品写回明细行 */
export function applyCatalogProductToLine(
  line: TcmPrescriptionLineUi,
  product: AdviceProductOption,
): void {
  const defId =
    preserveApiIdForRequest(product.productDefinitionId) ??
    preserveApiIdForRequest(product.id) ??
    '';
  const doseUnit = resolveTcmDoseUnitFromProduct(product);
  line.medicationDefinitionId = defId;
  line.medicationName = toStr(product.name);
  line.doseUnitCode = doseUnit.doseUnitCode;
  line.doseUnitName = doseUnit.doseUnitName;
  line.unitCode = doseUnit.doseUnitCode;
  line.retailPrice = doseUnit.retailPrice;
  // 选药不预填默认剂量，由医生在剂量框输入；空行仍保持 dose=0
  // 缓存选品时目录可开量与单位槽，供改剂量时软校验（不落库）
  line.catalogCurrentLotPackageQty = parseCatalogCurrentLotPackageQty(product);
  line.catalogUnitBands = product.unitBands;
  line.catalogPackageUnitCode = toStr(product.defaultUnitCode) || undefined;
  // 医保等级：目录带出仅展示；无值时清空避免沿用上一药材
  const lvRaw = product.chrgitmLv;
  line.chrgitmLv =
    lvRaw !== null && lvRaw !== undefined && String(lvRaw).trim() !== '' ? lvRaw : undefined;
  const lvText = String(product.chrgitmLvText ?? '').trim();
  line.chrgitmLvText = lvText || undefined;
  // 特殊煎法：目录维护默认带出；无值时清空避免沿用上一药材
  const dosageCode = String(product.dosageInstruction ?? '').trim();
  if (dosageCode) {
    line.dosageInstructionCode = dosageCode;
    line.dosageInstructionText =
      String(product.dosageInstructionDictText ?? '').trim() || undefined;
  } else {
    line.dosageInstructionCode = '';
    line.dosageInstructionText = undefined;
  }
  line.dirty = true;
}

/** 单剂金额：各行 dose × retailPrice 累加（6 位） */
export function calcSingleDoseAmount(lines: TcmPrescriptionLineUi[]): number {
  return sumMoneyThenCalc(
    lines
      .filter((line) => !line.pendingDelete && line.medicationDefinitionId)
      .map((line) => calcMoneyLineTotal(line.retailPrice, line.dose)),
  );
}

/** 校验方头必填项（暂存/签发前共用；天数非必填） */
export function validatePrescriptionHeaderFields(
  rx: Pick<
    TcmPrescriptionUi,
    | 'chineseHerbsDoseQuantity'
    | 'contractId'
    | 'methodCode'
    | 'rateCode'
    | 'tcmSyndromeGroup'
    | 'therapyEnum'
  >,
  options?: { requireTherapy?: boolean },
): null | string {
  if (!rx.tcmSyndromeGroup.trim()) {
    return '请先选择绑定的病证组';
  }
  if (options?.requireTherapy && !String(rx.therapyEnum ?? '').trim()) {
    return '请选择医嘱类型（长期/临时）';
  }
  if (!String(rx.contractId ?? '').trim()) {
    return '请选择费用性质';
  }
  if (!String(rx.methodCode ?? '').trim()) {
    return '请选择用法';
  }
  if (!String(rx.rateCode ?? '').trim()) {
    return '请选择用药频次';
  }
  const doseQty = rx.chineseHerbsDoseQuantity;
  if (doseQty === null || doseQty === undefined || doseQty <= 0) {
    return '请填写付数';
  }
  return null;
}

/**
 * 同方是否已有相同药品定义（排除待删行；可选排除当前编辑行，便于换药校验）。
 */
export function findDuplicateHerbInPrescription(
  lines: TcmPrescriptionLineUi[],
  medicationDefinitionId: string,
  excludeClientLineKey?: string,
): TcmPrescriptionLineUi | undefined {
  const defId = String(medicationDefinitionId ?? '').trim();
  if (!defId) {
    return undefined;
  }
  // 只在有效药材行中按定义主键比对
  return lines.find((line) => {
    if (line.pendingDelete) {
      return false;
    }
    if (excludeClientLineKey && line.clientLineKey === excludeClientLineKey) {
      return false;
    }
    return String(line.medicationDefinitionId ?? '').trim() === defId;
  });
}

/** 校验同方无重复药品；有重复则返回提示文案 */
export function validatePrescriptionNoDuplicateHerbs(
  lines: TcmPrescriptionLineUi[],
): null | string {
  // defId → 首次出现的药名，用于拼提示
  const seen = new Map<string, string>();
  for (const line of lines) {
    if (line.pendingDelete) {
      continue;
    }
    const defId = String(line.medicationDefinitionId ?? '').trim();
    if (!defId) {
      continue;
    }
    const prevName = seen.get(defId);
    if (prevName !== undefined) {
      const name = String(line.medicationName ?? '').trim() || prevName || '该药品';
      return `同一处方中不允许重复药品：${name}`;
    }
    seen.set(defId, String(line.medicationName ?? '').trim());
  }
  return null;
}

/** 是否存在待提交的方头/明细变更 */
export function hasPersistableDraftChanges(rx: TcmPrescriptionUi): boolean {
  if (rx.headerDirty) {
    return true;
  }
  return rx.lines.some((line) => line.dirty || line.pendingDelete);
}

/** 组装暂存请求（含待删行 DELETE） */
export function buildDraftSaveRequest(
  encounterId: string,
  patientId: string,
  prescriptions: TcmPrescriptionUi[],
): SaveTcmRxDraftBatchRequest {
  const draftHeaders: TcmRxDraftHeaderRequest[] = prescriptions
    .filter((rx) => !isTcmPrescriptionReadonly(rx))
    .map((rx) => {
      const draftLines: TcmRxDraftLineRequest[] = [];
      let visibleSortSeq = 0;
      // 当前可见行：新建 CREATE、已存在 UPDATE（sortNumber 与界面顺序一致）
      rx.lines.forEach((line) => {
        if (line.pendingDelete) return;
        if (!line.medicationDefinitionId) return;
        visibleSortSeq += 1;
        line.sortNumber = visibleSortSeq;
        const base: TcmRxDraftLineRequest = {
          lineOp: line.medicationRequestId && !line.isNew ? 'UPDATE' : 'CREATE',
          medicationDefinitionId: line.medicationDefinitionId,
          dose: line.dose,
          doseUnitCode: line.doseUnitCode || 'g',
          sortNumber: visibleSortSeq,
          dosageInstructionCode: String(line.dosageInstructionCode ?? '').trim(),
        };
        if (line.medicationRequestId && !line.isNew) {
          base.medicationRequestId = line.medicationRequestId;
        }
        if (line.dispenseLocationId) {
          base.dispenseLocationId = line.dispenseLocationId;
        }
        // 行级合同优先；空则后端回落方头默认
        const lineContractId = toStr(line.contractId) || toStr(rx.contractId);
        if (lineContractId) {
          base.contractId = lineContractId;
        }
        draftLines.push(base);
      });
      // 标记删除的已落库行（带上 dose，兼容未升级后端对 DELETE 仍算付数的路径）
      rx.lines.forEach((line) => {
        if (!line.pendingDelete || !line.medicationRequestId) return;
        draftLines.push({
          lineOp: 'DELETE',
          medicationRequestId: line.medicationRequestId,
          dose: line.dose ?? 0,
          doseUnitCode: line.doseUnitCode || 'g',
        });
      });
      const encounterDiagnosisIds = parseEncounterDiagnosisIds(rx.encounterDiagnosisIds).join(',');
      return {
        prescriptionGroupKey: rx.prescriptionGroupKey,
        therapyEnum: rx.therapyEnum || undefined,
        tcmSyndromeGroup: rx.tcmSyndromeGroup,
        ...(encounterDiagnosisIds ? { encounterDiagnosisIds } : {}),
        chineseHerbsDoseQuantity: rx.chineseHerbsDoseQuantity ?? 0,
        sufferingFlag: rx.sufferingFlag,
        methodCode: rx.methodCode || undefined,
        rateCode: rx.rateCode || undefined,
        days: rx.days || undefined,
        remark: rx.remark || undefined,
        ...(toStr(rx.contractId) ? { contractId: toStr(rx.contractId) } : {}),
        lines: draftLines,
      };
    })
    .filter((header) => header.lines.length > 0);

  return {
    encounterId,
    patientId,
    prescriptions: draftHeaders,
  };
}

/** 组装签发请求 */
export function buildSignRequest(
  encounterId: string,
  patientId: string,
  rx: TcmPrescriptionUi,
): SignTcmRxBatchRequest {
  const signTargets = rx.lines.flatMap((line) => {
    if (line.pendingDelete || !line.medicationRequestId) {
      return [];
    }
    return [
      {
        prescriptionGroupKey: rx.prescriptionGroupKey,
        medicationRequestId: line.medicationRequestId,
      },
    ];
  });
  return { encounterId, patientId, signTargets };
}

/** 组装签退请求 */
export function buildWithdrawRequest(
  encounterId: string,
  patientId: string,
  rx: TcmPrescriptionUi,
): WithdrawOrDeleteTcmRxRequest {
  const medicationRequestIds = rx.lines.flatMap((line) =>
    line.medicationRequestId ? [line.medicationRequestId] : [],
  );
  return {
    encounterId,
    patientId,
    action: TCM_RX_ACTION_WITHDRAW,
    scope: TCM_RX_SCOPE_PRESCRIPTION,
    medicationRequestIds,
    prescriptionGroupKey: rx.prescriptionGroupKey,
    prescriptionNo: rx.prescriptionNo || undefined,
  };
}

/** 组装删除草稿方请求 */
export function buildDeleteDraftRequest(
  encounterId: string,
  patientId: string,
  rx: TcmPrescriptionUi,
): WithdrawOrDeleteTcmRxRequest {
  const medicationRequestIds = rx.lines.flatMap((line) =>
    line.medicationRequestId ? [line.medicationRequestId] : [],
  );
  return {
    encounterId,
    patientId,
    action: TCM_RX_ACTION_DELETE,
    scope: TCM_RX_SCOPE_PRESCRIPTION,
    medicationRequestIds,
    prescriptionGroupKey: rx.prescriptionGroupKey,
    prescriptionNo: rx.prescriptionNo || undefined,
  };
}

/** 组装停嘱请求（住院长期方按处方号整方） */
export function buildStopLongTermTcmRequest(
  encounterId: string,
  patientId: string,
  rx: TcmPrescriptionUi,
): import('#/api/doctor-station/tcm.types').StopLongTermTcmHerbalPrescriptionRequest | null {
  const prescriptionNo = toStr(rx.prescriptionNo);
  if (!prescriptionNo) return null;
  return { encounterId, patientId, prescriptionNos: [prescriptionNo] };
}

/** 组装取消停嘱请求（住院长期方按处方号整方） */
export function buildCancelStopLongTermTcmRequest(
  encounterId: string,
  patientId: string,
  rx: TcmPrescriptionUi,
): import('#/api/doctor-station/tcm.types').CancelStopLongTermTcmHerbalPrescriptionRequest | null {
  const prescriptionNo = toStr(rx.prescriptionNo);
  if (!prescriptionNo) return null;
  return { encounterId, patientId, prescriptionNos: [prescriptionNo] };
}

/** 方头表单字段同步回处方对象 */
export function syncHeaderFormToPrescription(
  rx: TcmPrescriptionUi,
  form: {
    chineseHerbsDoseQuantity?: number;
    contractId?: string;
    days?: number;
    encounterDiagnosisIds?: string[];
    feeNatureText?: string;
    methodCode: string;
    rateCode: string;
    sufferingFlag: boolean;
    tcmSyndromeGroup: string;
    therapyEnum?: string;
  },
): void {
  rx.tcmSyndromeGroup = form.tcmSyndromeGroup;
  rx.encounterDiagnosisIds = parseEncounterDiagnosisIds(form.encounterDiagnosisIds);
  if (form.therapyEnum !== undefined) {
    rx.therapyEnum = form.therapyEnum;
  }
  rx.chineseHerbsDoseQuantity = form.chineseHerbsDoseQuantity;
  rx.sufferingFlag = form.sufferingFlag;
  rx.methodCode = form.methodCode;
  rx.rateCode = form.rateCode;
  rx.days = form.days;
  rx.contractId = toStr(form.contractId) || undefined;
  rx.feeNatureText = toStr(form.feeNatureText) || undefined;
  rx.headerDirty = true;
}

/** 将方头字段载入表单中间态 */
export function loadHeaderFormFromPrescription(rx: null | TcmPrescriptionUi) {
  if (!rx) {
    return {
      tcmSyndromeGroup: '',
      encounterDiagnosisIds: [] as string[],
      therapyEnum: '',
      chineseHerbsDoseQuantity: undefined,
      sufferingFlag: false,
      methodCode: '',
      rateCode: '',
      days: undefined,
      contractId: '',
      feeNatureText: '',
    };
  }
  return {
    tcmSyndromeGroup: rx.tcmSyndromeGroup,
    encounterDiagnosisIds: [...(rx.encounterDiagnosisIds ?? [])],
    therapyEnum: rx.therapyEnum ?? '',
    chineseHerbsDoseQuantity: rx.chineseHerbsDoseQuantity,
    sufferingFlag: rx.sufferingFlag,
    methodCode: rx.methodCode,
    rateCode: rx.rateCode,
    days: rx.days,
    contractId: rx.contractId ?? '',
    feeNatureText: rx.feeNatureText ?? '',
  };
}

export {
  resolveDefaultTcmMethodCode,
  resolveTcmMethodLabel,
} from '#/components/doctor-station/tcm-advice/services/tcm-method-code';

export {
  DEFAULT_TCM_RATE_CODE,
  resolveDefaultTcmRateCode,
  resolveTcmRateLabel,
} from './tcm-method-code';
