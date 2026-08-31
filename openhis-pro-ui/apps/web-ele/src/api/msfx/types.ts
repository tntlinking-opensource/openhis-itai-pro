/**
 * 码上放心 API 共享类型（对接 msfxUploadWorkbench / msfxInboundUpstream）。
 */

/** HIS 场景码（与后端 MsfxHisScene 对齐）。 */
export type MsfxHisSceneEnum =
  | 'WAREHOUSE_PURCHASE_IN'
  | 'SUPPLIER_RETURN'
  | 'OP_DISPENSE'
  | 'IP_DISPENSE'
  | 'OP_RETURN'
  | 'IP_RETURN';

/** 上传状态码。 */
export type MsfxUploadStatusEnum =
  | 'PENDING'
  | 'QUEUED'
  | 'PROCESSING'
  | 'SUCCESS'
  | 'PARTIAL'
  | 'FAILED'
  | 'SKIPPED';

export interface MsfxUploadStatusDto {
  id?: number | string;
  tenantId?: number | string;
  busNo?: string;
  hisSceneEnum?: string;
  billType?: string;
  billCode?: string;
  triggerSourceEnum?: string;
  uploadStatusEnum?: MsfxUploadStatusEnum | string;
  topModel?: string;
  dealStatus?: string;
  subProcessFlag?: string;
  lastMsgCode?: string;
  lastMsgInfo?: string;
  pollCount?: number;
  traceCount?: number;
  supplierEntId?: string;
  refEntId?: string;
  uploadAt?: string;
  lastPollAt?: string;
  finishedAt?: string;
  createdAt?: string;
  updatedAt?: string;
  version?: number;
}

export type MsfxUploadRecordDto = MsfxUploadStatusDto;

export interface MsfxUpstreamTraceCodeItem {
  code?: string;
  /** TOP code_level 原样保留；两套口径并存，不能单独判大/小 */
  codeLevel?: string;
  /** TOP code_pack_level：1最小 / 2中 / 3大 */
  codePackLevel?: string;
  /** TOP is_smallest：Y 最小包装 / N 否 */
  isSmallest?: string;
  pkgAmount?: string;
}

export interface MsfxUpstreamBillRowDto {
  billCode?: string;
  fromEntId?: string;
  fromEntName?: string;
  billTime?: string;
  billType?: string;
  fromRefUserId?: string;
  /** 药品展示名（明细行） */
  drugName?: string;
  physicName?: string;
  prodName?: string;
  packageSpec?: string;
  produceBatchNo?: string;
  /** 最小包装数量 */
  leastPkgAmount?: string;
  leastPrepnAmount?: string;
  pkgUnitDesc?: string;
  prepnUnit?: string;
  approvalNo?: string;
  /** 平台药品基础信息 id（本地记忆键） */
  drugEntBaseInfoId?: string;
  /** 生产企业 */
  productEntName?: string;
  produceDate?: string;
  validEndDate?: string;
  /** HIS 套用：小码（含中大码展开后的子码） */
  traceCodes?: string[];
  /** 平台原始码（含层级） */
  traceCodeItems?: MsfxUpstreamTraceCodeItem[];
  /** 未能展开的中/大包装码 */
  packTraceCodes?: string[];
  /** 确认窗：同时有中大/小码时，入库用哪一档 */
  inboundCodeRank?: 'PACK' | 'SMALL';
}

export interface MsfxUpstreamBillListDto {
  responseSuccess?: boolean;
  msgCode?: string;
  msgInfo?: string;
  totalNum?: number | string;
  rows?: MsfxUpstreamBillRowDto[];
}

export interface MsfxUpstreamBillDetailDto {
  importId?: number | string;
  importStatusEnum?: string;
  responseSuccess?: boolean;
  msgCode?: string;
  msgInfo?: string;
  /** 展开过程提示 */
  expandHint?: string;
  rows?: MsfxUpstreamBillRowDto[];
}

export interface MsfxUpstreamImportDto {
  id?: number | string;
  tenantId?: number | string;
  upstreamBillCode?: string;
  upstreamBillType?: string;
  fromEntId?: string;
  fromRefEntId?: string;
  assRefEntId?: string;
  toRefEntId?: string;
  upstreamBillTime?: string;
  fromEntName?: string;
  importStatusEnum?: string;
  hisBusNo?: string;
  traceCount?: number;
  detailSummary?: string;
  lastPullAt?: string;
  appliedAt?: string;
  createdAt?: string;
  version?: number;
}

/** 进货正式 102 上报并展开支码结果。 */
export interface MsfxInboundExpandSmallCodesDto {
  uploadStatusEnum?: MsfxUploadStatusEnum | string;
  beforeCodeCount?: number;
  afterCodeCount?: number;
  expanded?: boolean;
  wroteToLines?: boolean;
  alreadyAgreed?: boolean;
  hint?: string;
  smallCodes?: string[];
  /** 展开后的码项（含大/中/小层级） */
  codeItems?: MsfxUpstreamTraceCodeItem[];
}
