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
import type { AdviceProductOption } from '#/views/outpatient/doctor-station/advice';

import { formatTcmDiagnosisDisplayName } from '#/components/doctor-station/diagnosis/services/tcm-diagnosis-display';
import {
  DEFAULT_TCM_METHOD_CODE,
  DEFAULT_TCM_RATE_CODE,
  resolveDefaultTcmMethodCode,
  resolveTcmMethodLabel,
} from '#/components/doctor-station/tcm-advice/services/tcm-method-code';
import { decimalMul } from '#/utils/format/decimal-util';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import {
  convertProductUnitPriceBetweenBandKinds,
  normalizeProductUnitBandsForPrice,
  parseProductUnitBandPrice,
  PRODUCT_CATALOG_PRICE_SOURCE_BAND_KIND,
} from '#/utils/doctor-station/product-unit-band-price';
import {
  unitCodeByBandKind,
  unitNameByBandKind,
} from '#/views/outpatient/doctor-station/advice/services/medication-unit-bands';

/** 方状态：草稿可编辑 */
export const TCM_RX_STATUS_DRAFT = 'DRAFT';

/** 方状态：已停嘱 */
export const TCM_RX_STATUS_STOPPED = 'STOPPED';

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
  chargeStatusText?: string;
  lineStatusEnum?: string;
  /** 待提交删除的行 */
  pendingDelete?: boolean;
  /** 行内容相对上次暂存有改动 */
  dirty?: boolean;
  /** 新建尚未落库 */
  isNew?: boolean;
}

/** 处方方头 UI 态 */
export interface TcmPrescriptionUi {
  prescriptionGroupKey: string;
  prescriptionNo?: string;
  /** 住院方头长临 */
  therapyEnum?: string;
  tcmSyndromeGroup: string;
  chineseHerbsDoseQuantity: number;
  sufferingFlag: boolean;
  methodCode: string;
  methodName?: string;
  rateCode: string;
  rateName?: string;
  days: number;
  remark: string;
  statusEnum: string;
  /** 方状态展示码（退费态优先于请求态） */
  displayStatusEnum?: string;
  /** 方状态展示文案 */
  displayStatusEnumText?: string;
  lines: TcmPrescriptionLineUi[];
  /** 方头相对上次暂存有改动 */
  headerDirty?: boolean;
}

/** 历史处方侧栏节点 */
export interface TcmHistoryTreeItem {
  id: string;
  prescriptionNo: string;
  summaryText: string;
  herbSummary: string;
  row: TcmRxHistoryRowDto;
}

function toNum(value: unknown, fallback = 0): number {
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  if (value === null || value === undefined || value === '') return fallback;
  const n = Number(value);
  return Number.isFinite(n) ? n : fallback;
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
  return [...lines].sort((left, right) => {
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

/** 方状态 Tag 类型 */
export function resolveTcmRxStatusTagType(
  statusEnum?: null | string,
  displayStatusEnum?: null | string,
): '' | 'danger' | 'info' | 'success' | 'warning' {
  const status = toStr(displayStatusEnum || statusEnum).toUpperCase();
  if (status === 'REFUNDED') return 'danger';
  if (status === 'REFUNDING') return 'warning';
  if (status === 'ACTIVE' || status === 'COMPLETED') return 'success';
  if (status === 'DRAFT') return 'info';
  if (status === 'STOPPED') return 'danger';
  if (status === 'CANCELLED' || status === 'REVOKED') return 'warning';
  if (status === 'ENTERED_IN_ERROR') return 'danger';
  return '';
}

/** 方状态展示文案 */
export function resolveTcmRxStatusLabel(
  statusEnum?: null | string,
  displayStatusEnumText?: null | string,
  displayStatusEnum?: null | string,
): string {
  const displayText = toStr(displayStatusEnumText);
  if (displayText) return displayText;
  const displayCode = toStr(displayStatusEnum).toUpperCase();
  if (displayCode === 'REFUNDING') return '待退费';
  if (displayCode === 'REFUNDED') return '已退费';
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

/** 读模型方头 → UI */
export function mapApiHeaderToUi(dto: TcmRxHeaderDto): TcmPrescriptionUi {
  const groupKey = toStr(dto.prescriptionGroupKey) || createPrescriptionGroupKey();
  return {
    prescriptionGroupKey: groupKey,
    prescriptionNo: toStr(dto.prescriptionNo) || undefined,
    therapyEnum: toStr(dto.therapyEnum) || undefined,
    tcmSyndromeGroup: toStr(dto.tcmSyndromeGroup),
    chineseHerbsDoseQuantity: toNum(dto.chineseHerbsDoseQuantity, 1),
    sufferingFlag: Boolean(dto.sufferingFlag),
    methodCode: toStr(dto.methodCode),
    methodName: toStr(dto.methodName) || undefined,
    rateCode: toStr(dto.rateCode) || DEFAULT_TCM_RATE_CODE,
    rateName: toStr(dto.rateName) || undefined,
    days: toNum(dto.days, 1),
    remark: toStr(dto.remark),
    statusEnum: toStr(dto.statusEnum) || TCM_RX_STATUS_DRAFT,
    displayStatusEnum: toStr(dto.displayStatusEnum) || undefined,
    displayStatusEnumText: toStr(dto.displayStatusEnumText) || undefined,
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
    chargeStatusText: toStr(dto.chargeStatusText) || undefined,
    lineStatusEnum: toStr(dto.lineStatusEnum) || undefined,
    pendingDelete: false,
    dirty: false,
    isNew: false,
  };
}

/** 历史处方 → 侧栏节点 */
export function mapHistoryRowToTreeItem(
  row: TcmRxHistoryRowDto,
  index: number,
): TcmHistoryTreeItem {
  const prescriptionNo = toStr(row.prescriptionNo) || `history-${index}`;
  const names = (row.medicationNames ?? []).filter((n) => toStr(n));
  return {
    id: `history-${prescriptionNo}-${index}`,
    prescriptionNo,
    summaryText: toStr(row.summaryText) || prescriptionNo,
    herbSummary: names.join('，'),
    row,
  };
}

/** 空白处方 */
export function createEmptyPrescription(
  tcmSyndromeGroup = '',
  chineseHerbsDoseQuantity = 1,
  defaultMethodCode = DEFAULT_TCM_METHOD_CODE,
  defaultRateCode = DEFAULT_TCM_RATE_CODE,
  therapyEnum?: string,
): TcmPrescriptionUi {
  return {
    prescriptionGroupKey: createPrescriptionGroupKey(),
    therapyEnum: therapyEnum || undefined,
    tcmSyndromeGroup,
    chineseHerbsDoseQuantity,
    sufferingFlag: false,
    methodCode: defaultMethodCode,
    rateCode: defaultRateCode,
    days: 1,
    remark: '',
    statusEnum: TCM_RX_STATUS_DRAFT,
    lines: [],
    headerDirty: true,
  };
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
  if (line.dose <= 0) {
    line.dose = 10;
  }
  line.dirty = true;
}

/** 单剂金额：各行 dose × retailPrice 累加 */
export function calcSingleDoseAmount(lines: TcmPrescriptionLineUi[]): number {
  return lines
    .filter((line) => !line.pendingDelete && line.medicationDefinitionId)
    .reduce((sum, line) => sum + decimalMul(line.dose, line.retailPrice), 0);
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
        };
        if (line.medicationRequestId && !line.isNew) {
          base.medicationRequestId = line.medicationRequestId;
        }
        if (line.dispenseLocationId) {
          base.dispenseLocationId = line.dispenseLocationId;
        }
        draftLines.push(base);
      });
      // 标记删除的已落库行
      rx.lines.forEach((line) => {
        if (!line.pendingDelete || !line.medicationRequestId) return;
        draftLines.push({
          lineOp: 'DELETE',
          medicationRequestId: line.medicationRequestId,
        });
      });
      return {
        prescriptionGroupKey: rx.prescriptionGroupKey,
        therapyEnum: rx.therapyEnum || undefined,
        tcmSyndromeGroup: rx.tcmSyndromeGroup,
        chineseHerbsDoseQuantity: rx.chineseHerbsDoseQuantity,
        sufferingFlag: rx.sufferingFlag,
        methodCode: rx.methodCode || undefined,
        rateCode: rx.rateCode || undefined,
        days: rx.days || undefined,
        remark: rx.remark || undefined,
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
    chineseHerbsDoseQuantity: number;
    days: number;
    methodCode: string;
    rateCode: string;
    remark: string;
    sufferingFlag: boolean;
    tcmSyndromeGroup: string;
    therapyEnum?: string;
  },
): void {
  rx.tcmSyndromeGroup = form.tcmSyndromeGroup;
  if (form.therapyEnum !== undefined) {
    rx.therapyEnum = form.therapyEnum;
  }
  rx.chineseHerbsDoseQuantity = form.chineseHerbsDoseQuantity;
  rx.sufferingFlag = form.sufferingFlag;
  rx.methodCode = form.methodCode;
  rx.rateCode = form.rateCode;
  rx.days = form.days;
  rx.remark = form.remark;
  rx.headerDirty = true;
}

/** 将方头字段载入表单中间态 */
export function loadHeaderFormFromPrescription(rx: null | TcmPrescriptionUi) {
  if (!rx) {
    return {
      tcmSyndromeGroup: '',
      therapyEnum: '',
      chineseHerbsDoseQuantity: 1,
      sufferingFlag: false,
      methodCode: '',
      rateCode: DEFAULT_TCM_RATE_CODE,
      days: 1,
      remark: '',
    };
  }
  return {
    tcmSyndromeGroup: rx.tcmSyndromeGroup,
    therapyEnum: rx.therapyEnum ?? '',
    chineseHerbsDoseQuantity: rx.chineseHerbsDoseQuantity,
    sufferingFlag: rx.sufferingFlag,
    methodCode: rx.methodCode,
    rateCode: rx.rateCode || DEFAULT_TCM_RATE_CODE,
    days: rx.days,
    remark: rx.remark,
  };
}

export { resolveDefaultTcmMethodCode, resolveTcmMethodLabel };
export {
  DEFAULT_TCM_RATE_CODE,
  resolveDefaultTcmRateCode,
  resolveTcmRateLabel,
} from './tcm-method-code';
