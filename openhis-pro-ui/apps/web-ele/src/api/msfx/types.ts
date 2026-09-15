/** Opensource stub — no mashangfangxin integration. */
export type MsfxHisSceneEnum =
  | 'WAREHOUSE_PURCHASE_IN'
  | 'SUPPLIER_RETURN'
  | 'OP_DISPENSE'
  | 'IP_DISPENSE'
  | 'OP_RETURN'
  | 'IP_RETURN';

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
  busNo?: string;
  hisSceneEnum?: string;
  uploadStatusEnum?: MsfxUploadStatusEnum | string;
  [key: string]: unknown;
}

export type MsfxUploadRecordDto = MsfxUploadStatusDto;

export interface MsfxUpstreamTraceCodeItem {
  code?: string;
  codeLevel?: string;
  codePackLevel?: string;
  isSmallest?: string;
  pkgAmount?: string;
}

export interface MsfxUpstreamBillRowDto {
  billCode?: string;
  drugName?: string;
  physicName?: string;
  prodName?: string;
  packageSpec?: string;
  produceBatchNo?: string;
  leastPkgAmount?: string;
  traceCodes?: MsfxUpstreamTraceCodeItem[];
  packTraceCodes?: MsfxUpstreamTraceCodeItem[];
  inboundCodeRank?: string;
  [key: string]: unknown;
}

export interface MsfxUpstreamBillListDto {
  rows?: MsfxUpstreamBillRowDto[];
  total?: number;
}

export interface MsfxUpstreamBillDetailDto {
  billCode?: string;
  drugs?: MsfxUpstreamBillRowDto[];
  [key: string]: unknown;
}

export interface MsfxUpstreamImportDto {
  importId?: number | string;
  hisBusNo?: string;
  [key: string]: unknown;
}

export interface MsfxInboundExpandSmallCodesDto {
  busNo?: string;
  expandedCodes?: string[];
  [key: string]: unknown;
}
