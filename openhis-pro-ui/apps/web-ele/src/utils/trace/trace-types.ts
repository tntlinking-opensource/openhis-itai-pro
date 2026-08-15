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
   * 本行「建议应扫」个数（ceil(操作拆零÷每码拆零)）；弹窗「待扫」展示与半扫软确认用。
   * 消耗侧<strong>不得</strong>当作绑码硬上限（硬上限见 {@link maxTraceCount}）。
   */
  requiredTraceCount?: number;
  /**
   * 消耗侧单行绑码硬上限（ceil(操作拆零)）：开 2 支最多 2 码。
   * 缺省时回落 {@link operationSplitQty}；进货勿依赖本字段（仍走应扫硬 Gate）。
   */
  maxTraceCount?: number;
  /**
   * 本次操作拆零量（消耗/流转盖住判定用）；有值时扫码「够了」按剩余合计 ≥ 本值。
   */
  operationSplitQty?: number;
  /**
   * 主表该品批是否已有码（有码批=true）。
   * false=无码批：扫码仅软提示数量；true=有码批：消耗侧码身份仍软提示（仅进货硬拦），写流水盖不住跳过。
   */
  lotHasTraceMaster?: boolean;
  /** 追溯码所贴单位（发药取主档快照；进货/库房取目录；匹配仍按码→品+批） */
  traceUnitCode?: string;
  /** 追溯码单位展示文案（术语/计量带 unitName；弹窗药品明细列） */
  traceUnitCodeLabel?: string;
  /**
   * 1 个追溯码单位对应的拆零数量（发药取主档 `trace_unit_ratio`；库房取目录）。
   * 弹窗「追溯码/拆零」列直出。
   */
  traceUnitRatio?: number;
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
    /** 未建档码可软录入；主档耗尽由扫码弹窗/后端硬拦 */
    allowNotFound: true,
    /** 发药耗尽硬拦走剩余判定，不依赖本开关整段拒不在院 */
    rejectNotInHospital: false,
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
    /** 库房出库等同消耗侧：码身份软提示，硬 Gate 仅进货 */
    allowNotFound: true,
    rejectNotInHospital: false,
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
