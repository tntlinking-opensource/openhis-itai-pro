import type { PageParams } from '#/types/api-response';

/** 分页查询待追溯分配患者队列 */
export interface PagePatientsPendingTraceAssignmentRequest extends PageParams {
  keyword?: string;
}

/** 待追溯分配患者队列行 */
export interface PatientTraceAssignmentQueueRowDto {
  encounterId?: string;
  /** 住院号（就诊 busNo，非 encounterId）。 */
  encounterBusNo?: string;
  patientId?: string;
  patientName?: string;
  bedName?: string;
  pendingGroupCount?: number;
}

/** 分页查询待追溯分配患者队列响应 */
export interface PagePatientsPendingTraceAssignmentResponse {
  pageNo?: number;
  pageSize?: number;
  totalCount?: number;
  content?: PatientTraceAssignmentQueueRowDto[];
}

/** 加载患者药批汇总待分配行 */
export interface ListPatientTraceAssignmentGroupsRequest {
  encounterId: string;
}

/** 患者药批汇总待分配/已分配行 */
export interface PatientTraceAssignmentGroupDto {
  groupKey?: string;
  summaryNo?: string;
  medicationId?: string;
  medicationName?: string;
  specification?: string;
  lotNumber?: string;
  dispenseQuantitySplit?: number | string;
  splitUnitCode?: string;
  splitUnitCode_dictText?: string;
  dispenseIds?: Array<number | string>;
  assignedTraceNo?: string;
  assignmentStatus?: string;
  assignmentStatus_enumText?: string;
  tracePoolPreview?: string;
  /** 汇总单码池完整列表（同 summaryNo 相同） */
  tracePoolCodes?: string[];
  maxTraceCodeCount?: number;
}

/** 加载患者药批汇总待分配行响应 */
export interface ListPatientTraceAssignmentGroupsResponse {
  groups?: PatientTraceAssignmentGroupDto[];
}

/** 批量解析追溯码 */
export interface ResolveTraceCodesForPatientRequest {
  encounterId: string;
  traceCodes: string[];
  groupKeys: string[];
}

/** 追溯码匹配预览行 */
export interface TraceCodeMatchPreviewDto {
  groupKey?: string;
  matchedTraceCodes?: string[];
  medicationId?: string;
  lotNumber?: string;
}

/** 追溯码解析失败明细 */
export interface TraceCodeResolveFailItemDto {
  traceCode?: string;
  reasonCode?: string;
}

/** 批量解析追溯码响应 */
export interface ResolveTraceCodesForPatientResponse {
  matches?: TraceCodeMatchPreviewDto[];
  failItems?: TraceCodeResolveFailItemDto[];
}

/** 单汇总组追溯码保存载荷 */
export interface PatientTraceAssignmentGroupSubmitDto {
  groupKey: string;
  dispenseIds: string[];
  traceNo?: string;
}

/** 保存患者追溯码分配 */
export interface SavePatientTraceAssignmentRequest {
  encounterId: string;
  groups: PatientTraceAssignmentGroupSubmitDto[];
}

/** 保存患者追溯码分配响应 */
export interface SavePatientTraceAssignmentResponse {
  savedGroupCount?: number;
}
