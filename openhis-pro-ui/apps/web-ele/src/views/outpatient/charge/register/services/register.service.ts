import type {
  CancelWithRefundResult,
  ListPractitionersParams,
  ListPractitionersResult,
  LoadAppointmentForRegisterParams,
  ListBookableSlotHintsParams,
  ListBookableSlotHintsResult,
  LoadRegisterFormInitParams,
  LoadRegisterFormInitResult,
  OutpatientBookingSnapshotDto,
  PagePatientsForSelectResult,
  PatientItemDto,
  PayRegisterFeeParams,
  PayRegisterFeeResult,
  PreRegisterSettlementParams,
  PreRegisterSettlementResult,
  RegisterHistoryTableRow,
} from '#/api/outpatient-registration';

import {
  httpCancelWithRefund,
  httpListPractitioners,
  httpListBookableSlotHints,
  httpLoadAppointmentForRegister,
  httpLoadRegisterFormInit,
  httpPageClinicRegisters,
  httpPagePatientsForSelect,
  httpPayRegisterFee,
  httpPreRegisterFromAppointment,
  httpPreRegisterSettlement,
  httpValidateCancelable,
} from '#/api/outpatient-registration';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

/** 窗口默认现金渠道编码（与收费域字典对齐后替换） */
export const DEFAULT_REGISTER_PAY_CHANNEL_CODE = 'CASH';

/**
 * 患者分页查询（SelectTable 使用 pageNo 从 1 开始）。
 */
export async function pageRegisterPatientsForSelect(params: {
  pageNo: number;
  pageSize: number;
  keyword: string;
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

/** 加载挂号页面初始化下拉数据 */
export async function loadRegisterFormInit(
  params: LoadRegisterFormInitParams = {},
): Promise<LoadRegisterFormInitResult> {
  return httpLoadRegisterFormInit(params);
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

/**
 * 就诊记录表格分页（pageNo 从 1 开始）。
 */
export async function pageRegisterHistoryForTable(opts: {
  dateFrom: string;
  dateTo: string;
  keyword: string;
  pageNo: number;
  pageSize: number;
}): Promise<{ list: RegisterHistoryTableRow[]; total: number }> {
  const body = {
    dateFrom: opts.dateFrom,
    dateTo: opts.dateTo,
    keyword: opts.keyword.trim(),
    pageNo: opts.pageNo,
    pageSize: opts.pageSize,
  };
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
  return httpCancelWithRefund({ cancelReason, encounterId, refundRequestId });
}
