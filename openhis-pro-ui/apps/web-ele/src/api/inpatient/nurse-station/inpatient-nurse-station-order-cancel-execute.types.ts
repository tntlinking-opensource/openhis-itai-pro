/** 批量取消已执行医嘱计划点 */

export interface CancelNurseStationOrderExecutionsRequest {
  /** 左侧已选就诊主键 */
  encounterIds: string[];
  /** 已执行 Tab 勾选槽位对应的净剩 COMPLETED procedureId，1～50 */
  procedureIds: string[];
  /** 取消原因（必填） */
  statusReasonText: string;
}

export interface CancelNurseStationOrderExecutionsResponse {
  success?: boolean;
  cancelledProcedureIds?: string[];
  rebuiltSummaryNos?: string[];
  message?: string;
}
