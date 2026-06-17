/**
 * 门诊退药应用服务请求/响应类型（与后端 DTO 字段名 camelCase 对齐；Long 主键用 string 提交）。
 */

import type { PageParams } from '#/types/api-response';

/** 与 TableNameConstants.MED_MEDICATION_REQUEST 一致，确认退药时 tableName 固定传此值 */
export const MED_MEDICATION_REQUEST_TABLE = 'med_medication_request';

export interface DepartmentOptionDto {
  departmentId: string;
  departmentName: string;
}

export interface RefundStatusOptionDto {
  statusCode: string;
  statusName: string;
}

/** 初始化请求（可空壳，继承分页基类字段一般不用传） */
export interface LoadReturnMedicineInitRequest extends PageParams {
  pageNo?: number;
  pageSize?: number;
}

export interface LoadReturnMedicineInitResponse {
  departmentOptions?: DepartmentOptionDto[];
  refundStatusOptions?: RefundStatusOptionDto[];
}

/** IF-PHA-02-02：与发药队列一致的时间窗 + searchKey + 分页（继承 AbstractHealthPageRequest） */
export interface PageReturnPatientRequest extends PageParams {
  /** 姓名/就诊号/拼音/五笔/证件号片段 */
  searchKey?: null | string;
  /** 状态变更时间下限（含），如 2026-05-16 00:00:00 */
  startDate?: null | string;
  /** 状态变更时间上限（含），如 2026-05-16 23:59:59 */
  endDate?: null | string;
}

export interface ReturnPatientRowDto {
  encounterId: string;
  patientName?: null | string;
  genderEnum?: null | string;
  genderEnumText?: null | string;
  age?: null | string;
  idCard?: null | string;
  departmentName?: null | string;
  refundEnum?: null | string;
  refundEnumText?: null | string;
  encounterNo?: null | string;
  receptionTime?: null | string;
}

export interface PageReturnPatientResponse {
  pageNo?: number;
  pageSize?: number;
  totalCount?: number | string;
  rows?: ReturnPatientRowDto[];
  content?: ReturnPatientRowDto[];
}

/** IF-PHA-02-03：仅 encounterId、refundStatus（PENDING_REFUND/REFUNDED） */
export interface GetReturnMedicineDetailRequest {
  encounterId: string;
  /** PENDING_REFUND | REFUNDED；与左侧 Tab 一致 */
  refundStatus?: null | string;
}

export interface ReturnMedicineDetailRowDto {
  dispenseId: string;
  requestId: string;
  refundMedicineId?: null | string;
  itemId?: null | string;
  itemName?: null | string;
  lotNumber?: null | string;
  quantity?: null | number | string;
  unitCode?: null | string;
  unitCodeText?: null | string;
  totalPrice?: null | number | string;
  doctorName?: null | string;
  refundEnum?: null | string;
  refundEnumText?: null | string;
  reqStatus?: null | string;
  reqStatusText?: null | string;
  serviceTable?: null | string;
  traceNo?: null | string;
}

export interface GetReturnMedicineDetailResponse {
  items?: ReturnMedicineDetailRowDto[];
}

export interface ReturnMedicineItemRequest {
  dispenseId: string;
  requestId: string;
  /** 固定 med_medication_request（本页仅药品退药） */
  tableName: string;
}

export interface ConfirmMedicineReturnRequest {
  items: ReturnMedicineItemRequest[];
  confirmType: 'CONFIRM' | 'DECLINE';
  /** @deprecated CHS 启用即上传，请使用 contractBusNo */
  medicareUploadEnabled?: boolean;
  contractBusNo?: string;
  reason?: null | string;
}

export interface FailedReturnItemDto {
  dispenseId: string;
  errorCode?: null | string;
  errorMessage?: null | string;
}

export interface ConfirmMedicineReturnResponse {
  success?: boolean;
  message?: null | string;
  failedItems?: FailedReturnItemDto[];
}
