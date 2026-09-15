import type {
  CancelWithRefundResult,
  ListBookableSlotHintsParams,
  ListBookableSlotHintsResult,
  ListPractitionersParams,
  ListPractitionersResult,
  LoadAppointmentForRegisterParams,
  LoadRegisterFormInitParams,
  LoadRegisterFormInitResult,
  OutpatientBookingSnapshotDto,
  PageClinicRegistersParams,
  PagePatientsForSelectResult,
  PatientItemDto,
  PayRegisterFeeParams,
  PayRegisterFeeResult,
  PreRegisterSettlementParams,
  PreRegisterSettlementResult,
  RegisterHistoryTableRow,
} from '#/api/outpatient-registration';

import {
  httpCancelPlannedRegistration,
  httpCancelWithRefund,
  httpListBookableSlotHints,
  httpListPractitioners,
  httpLoadAppointmentForRegister,
  httpLoadRegisterFormInit,
  httpPageClinicRegisters,
  httpPagePatientsForSelect,
  httpPayRegisterFee,
  httpPreRegisterFromAppointment,
  httpPreRegisterSettlement,
  httpRefreshRegisterFeeInsurancePreSettle,
  httpValidateCancelable,
} from '#/api/outpatient-registration';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

/** 挂号收银优先默认渠道：有银联则扫码，否则现金 */
export const DEFAULT_REGISTER_PAY_CHANNEL_CODE = 'SCAN_PAY';

/**
 * 患者分页查询（SelectTable 使用 pageNo 从 1 开始）。
 */
export async function pageRegisterPatientsForSelect(params: {
  keyword: string;
  pageNo: number;
  pageSize: number;
}): Promise<{ content: PatientItemDto[]; totalElements: number }> {
  const body = {
    keyword: params.keyword.trim(),
    pageNo: params.pageNo,
    pageSize: params.pageSize,
  };
  const data: PagePatientsForSelectResult = await httpPagePatientsForSelect(body);
  const { content, totalCount } = parsePagedRowsFromApiData<PatientItemDto>(data);
  return {
    content,
    totalElements: totalCount,
  };
}

/** 加载挂号页面初始化下拉数据（同参数并发去重） */
const initInflight = new Map<string, Promise<LoadRegisterFormInitResult>>();

function serializeRegisterInitParams(params: LoadRegisterFormInitParams): string {
  return [
    String(params.organizationId ?? ''),
    String(params.practitionerId ?? ''),
    String(params.registrationDate ?? ''),
  ].join('|');
}

export async function loadRegisterFormInit(
  params: LoadRegisterFormInitParams = {},
): Promise<LoadRegisterFormInitResult> {
  const key = serializeRegisterInitParams(params);
  const existing = initInflight.get(key);
  if (existing) {
    return existing;
  }
  const promise = httpLoadRegisterFormInit(params).finally(() => {
    initInflight.delete(key);
  });
  initInflight.set(key, promise);
  return promise;
}

/** 查询医生下拉数据（支持按科室过滤） */
export async function listPractitioners(
  params: ListPractitionersParams = {},
): Promise<ListPractitionersResult> {
  return httpListPractitioners(params);
}

/** 执行挂号预结算 */
export async function preRegisterSettlement(
  body: PreRegisterSettlementParams,
): Promise<PreRegisterSettlementResult> {
  return httpPreRegisterSettlement(body);
}

/** IF-REG-13：预约转挂号预结算 */
export async function preRegisterFromAppointment(
  body: PreRegisterSettlementParams,
): Promise<PreRegisterSettlementResult> {
  return httpPreRegisterFromAppointment(body);
}

/** IF-REG-12：加载有效预约 */
export async function loadAppointmentForRegister(
  body: LoadAppointmentForRegisterParams,
): Promise<OutpatientBookingSnapshotDto> {
  return httpLoadAppointmentForRegister(body);
}

/** IF-REG-14：可约时段摘要 */
export async function listBookableSlotHints(
  body: ListBookableSlotHintsParams,
): Promise<ListBookableSlotHintsResult> {
  return httpListBookableSlotHints(body);
}

/** 执行挂号费正式结算 */
export async function payRegisterFee(body: PayRegisterFeeParams): Promise<PayRegisterFeeResult> {
  return httpPayRegisterFee(body);
}

/** 重新读码后刷新挂号费医保预结算（2205→2206） */
export async function refreshRegisterFeeInsurancePreSettle(
  body: import('#/api/outpatient-registration').RefreshRegisterFeeInsurancePreSettleParams,
) {
  return httpRefreshRegisterFeeInsurancePreSettle(body);
}

/**
 * 就诊记录表格分页（pageNo 从 1 开始）。
 */
export async function pageRegisterHistoryForTable(opts: {
  dateFrom: string;
  dateTo: string;
  keyword: string;
  pageNo: number;
  pageSize: number;
  patientId?: null | number | string;
}): Promise<{ list: RegisterHistoryTableRow[]; total: number }> {
  const body: PageClinicRegistersParams = {
    dateFrom: opts.dateFrom,
    dateTo: opts.dateTo,
    keyword: opts.keyword.trim(),
    pageNo: opts.pageNo,
    pageSize: opts.pageSize,
  };
  if (opts.patientId !== undefined && opts.patientId !== null && String(opts.patientId).trim()) {
    body.patientId = opts.patientId;
  }
  const data = await httpPageClinicRegisters(body);
  const { content, totalCount } = parsePagedRowsFromApiData<RegisterHistoryTableRow>(data);
  return {
    list: content,
    total: totalCount,
  };
}

/**
 * 退号流程：先做可退校验，再提交退号退费。
 */
export async function cancelRegisterEncounter(params: {
  cancelReason?: string;
  clientRefundResult?: import('#/api/payment/payment.types').ClientRefundResultDto;
  encounterId: string;
}): Promise<CancelWithRefundResult> {
  const encounterId = params.encounterId;
  const cancelReason = params.cancelReason ?? '窗口退号';
  const validate = await httpValidateCancelable({ encounterId });
  if (!validate.cancelable) {
    throw new Error(validate.reasonText || '当前记录不可退号');
  }
  const refundRequestId =
    typeof crypto !== 'undefined' && crypto.randomUUID
      ? crypto.randomUUID()
      : `ref-${encounterId}-${Date.now()}`;
  return httpCancelWithRefund({
    cancelReason,
    encounterId,
    refundRequestId,
    ...(params.clientRefundResult ? { clientRefundResult: params.clientRefundResult } : {}),
  });
}

/** 待挂号（PLANNED）撤销：含医保平台退号（2202）；前端文案「取消挂号」 */
export async function cancelPlannedRegisterEncounter(params: {
  cancelReason?: string;
  encounterId: string;
}) {
  return httpCancelPlannedRegistration({
    encounterId: params.encounterId,
    cancelReason: params.cancelReason ?? '窗口取消挂号',
  });
}
