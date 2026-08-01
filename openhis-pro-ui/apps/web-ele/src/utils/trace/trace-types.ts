/** 与后端 {@code TraceIdentityResolveStatus} 对齐 */
export type TraceIdentityResolveStatusEnum =
  | 'DUPLICATE_IN_REQUEST'
  | 'NOT_FOUND'
  | 'NOT_IN_HOSPITAL'
  | 'OK';

/** 批量扫码弹窗单行候选（各业务页映射为统一结构） */
export interface TraceBatchScanLine {
  id: string;
  /** 库存行库房（查在库追溯码用；可由弹窗 defaultLocationId 兜底） */
  locationId?: string;
  itemId?: string;
  lotNumber?: string;
  itemName?: string;
  /** 当前行已绑定的追溯码（单码或逗号串） */
  traceNo?: string;
  /** 扩展展示列（如处方号、规格） */
  spec?: string;
  rxNo?: string;
  /**
   * 本行应扫追溯码数量（由业务页/桥接按「操作量 ÷ 每码追溯单位量」算好后传入；
   * dialog 不再自行按原始数量推断）。
   *
   * <p>进货硬 Gate 仍用个数；消耗/流转优先 {@link operationSplitQty} 剩余覆盖。</p>
   */
  requiredTraceCount?: number;
  /**
   * 本次操作拆零量（消耗/流转盖住判定用）；有值时扫码「够了」按剩余合计 ≥ 本值。
   */
  operationSplitQty?: number;
  /**
   * 主表该品批是否已有码（有码批=true）。
   * false=无码批：扫码仅软提示数量，允许未建档码绑定；true=硬校验反查/在院。
   */
  lotHasTraceMaster?: boolean;
  /** 追溯码所贴单位（进货行可选；匹配仍按码→品+批） */
  traceUnitCode?: string;
  /** 操作（录入）单位；与 traceUnitCode 相同时应扫数 = ceil(操作量) */
  operationUnitCode?: string;
}

export interface TraceIdentityResolveItemDto {
  traceNo?: string;
  resolveStatusEnum?: TraceIdentityResolveStatusEnum | string;
  itemTable?: string;
  itemId?: string;
  lotNumber?: string;
  traceUnitCode?: string;
  /** 主档贴码比例快照（1 码对应拆零量） */
  traceUnitRatio?: number | string;
  remainingSplitQuantity?: number | string;
  inHospitalStatusEnum?: string;
}

export interface TraceIdentityResolveFailItemDto {
  traceNo?: string;
  resolveStatusEnum?: TraceIdentityResolveStatusEnum | string;
}

export interface BatchResolveTraceIdentityResponse {
  items?: TraceIdentityResolveItemDto[];
  failItems?: TraceIdentityResolveFailItemDto[];
}

/** 发药侧反查请求 */
export interface BatchResolveTraceIdentityDispenseRequest {
  traceCodes: string[];
  sceneContext?: 'DISPENSE' | 'INBOUND' | 'INVENTORY' | 'RETURN' | 'STOCKTAKE';
}

/** 进货侧反查请求（字段名与后端一致） */
export interface BatchResolveTraceIdentityInboundRequest {
  traceNos: string[];
}

export type TraceScanBindMode = 'comma' | 'single';

/** 批量扫码场景预设 */
export interface TraceScanScenePreset {
  bindMode: TraceScanBindMode;
  /** NOT_FOUND 时是否仍尝试绑定到候选行 */
  allowNotFound: boolean;
  /** 发药/出库类：NOT_IN_HOSPITAL 拒绝绑定 */
  rejectNotInHospital: boolean;
  /** 反查 HTTP 走进货还是通用读接口 */
  resolveProvider: 'dispense' | 'inbound';
  sceneContext?: BatchResolveTraceIdentityDispenseRequest['sceneContext'];
}

export const TRACE_SCAN_SCENE_PRESETS = {
  dispense: {
    bindMode: 'comma',
    allowNotFound: false,
    rejectNotInHospital: true,
    resolveProvider: 'dispense',
    sceneContext: 'DISPENSE',
  },
  inbound: {
    bindMode: 'comma',
    allowNotFound: true,
    rejectNotInHospital: false,
    resolveProvider: 'inbound',
  },
  inventoryOut: {
    bindMode: 'comma',
    allowNotFound: false,
    rejectNotInHospital: true,
    resolveProvider: 'dispense',
    sceneContext: 'INVENTORY',
  },
  stocktake: {
    bindMode: 'comma',
    allowNotFound: true,
    rejectNotInHospital: false,
    resolveProvider: 'dispense',
    sceneContext: 'STOCKTAKE',
  },
  /** 损益：扫盈亏码本身（报溢可新码、报损须在院；同意时与正负同向直接加减） */
  gainLoss: {
    bindMode: 'comma',
    allowNotFound: true,
    rejectNotInHospital: false,
    resolveProvider: 'dispense',
    sceneContext: 'STOCKTAKE',
  },
} as const satisfies Record<string, TraceScanScenePreset>;

export type TraceScanSceneKey = keyof typeof TRACE_SCAN_SCENE_PRESETS;

export type TraceScanLogStatus = 'duplicate' | 'invalid' | 'not_found' | 'not_in_hospital' | 'ok';

export interface TraceScanLogItem {
  id: string;
  code: string;
  message: string;
  status: TraceScanLogStatus;
  timeLabel: string;
}
