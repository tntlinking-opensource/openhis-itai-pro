import type { ApiOptionItem, PageParams } from '#/types/api-response';

/** 加载退药页初始化元数据 */
export interface LoadInpatientReturnMedicineInitRequest extends PageParams {
  pageNo?: number;
  pageSize?: number;
}

export interface LoadInpatientReturnMedicineInitResponse {
  pharmacyOrganizationOptions?: ApiOptionItem[];
  wardOptions?: ApiOptionItem[];
  refundStatusOptions?: ApiOptionItem[];
}

/** 分页查询退药患者队列 */
export interface PageInpatientReturnPatientRequest extends PageParams {
  wardId?: string;
  /** PENDING=有待退；COMPLETED=仅已退且无待退 */
  queueTab?: 'COMPLETED' | 'PENDING';
  keyword?: string;
}

export interface InpatientReturnPatientRowDto {
  encounterId: string;
  patientName?: string;
  genderEnum?: string;
  genderEnumText?: string;
  age?: string;
  idCard?: string;
  wardName?: string;
  bedName?: string;
  encounterNo?: string;
  refundEnum?: string;
  refundEnumText?: string;
  /** 费用性质展示名（就诊优先合同名；空则前端回落自费） */
  contractDisplay?: string;
}

export interface PageInpatientReturnPatientResponse {
  pageNo?: number;
  pageSize?: number;
  totalCount?: number | string;
  rows?: InpatientReturnPatientRowDto[];
  content?: InpatientReturnPatientRowDto[];
}

/** 查询某就诊下退药明细 */
export interface GetInpatientReturnMedicineDetailRequest {
  encounterId: string;
  /** PENDING_REFUND | REFUNDED */
  refundStatus?: string;
}

export interface InpatientReturnMedicineDetailRowDto {
  dispenseId: string;
  requestId: string;
  itemId?: string;
  itemName?: string;
  lotNumber?: string;
  quantity?: number | string;
  unitCode?: string;
  unitCodeText?: string;
  doctorName?: string;
  refundEnum?: string;
  refundEnumText?: string;
  serviceTable?: string;
  traceNo?: string;
  selectable?: boolean;
}

export interface GetInpatientReturnMedicineDetailResponse {
  items?: InpatientReturnMedicineDetailRowDto[];
}

export interface InpatientReturnMedicineItemRequest {
  dispenseId: string;
}

export interface ConfirmInpatientMedicineReturnRequest {
  items: InpatientReturnMedicineItemRequest[];
  reason?: string;
  /** @deprecated CHS 启用即上传，请使用 contractBusNo */
  medicareUploadEnabled?: boolean;
  contractBusNo?: string;
}

export interface ConfirmInpatientMedicineReturnResponse {
  success?: boolean;
  message?: string;
}
