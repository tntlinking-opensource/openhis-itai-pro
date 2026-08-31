/**
 * 体检日结清单 API。
 */
import { publicRequestClient } from '#/api/request';

export interface PhysicalExamDailySettlementLineDto {
  amount?: null | number | string;
  chargeItemId?: string;
  departmentName?: null | string;
  encounterId?: string;
  itemName?: null | string;
  ordererName?: null | string;
  patientId?: string;
  patientName?: null | string;
}

export interface ListPhysicalExamDailySettlementRequest {
  dateFrom: string;
  dateTo: string;
}

export interface ListPhysicalExamDailySettlementResponse {
  lines?: PhysicalExamDailySettlementLineDto[];
  queryTimeText?: null | string;
  totalAmount?: null | number | string;
  visitCount?: number;
  visitCountText?: null | string;
}

/** 按收费日查询体检已结清单明细与汇总 */
export function listPhysicalExamDailySettlementApi(body: ListPhysicalExamDailySettlementRequest) {
  return publicRequestClient.post<ListPhysicalExamDailySettlementResponse>(
    '/physicalExamDailySettlement/listDailySettlement',
    body,
  );
}
