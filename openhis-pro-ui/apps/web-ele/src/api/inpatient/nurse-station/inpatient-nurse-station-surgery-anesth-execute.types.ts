import type { NurseStationOrderExecuteBindItemDto } from './inpatient-nurse-station-order-execute.types';

/** 装载手术/麻醉执行弹窗 */
export interface LoadSurgeryAnesthExecuteDialogRequest {
  encounterId: string;
  serviceRequestId: string;
  planTime: string;
}

/** 手术/麻醉执行弹窗回填 */
export interface SurgeryAnesthExecuteDialogDto {
  serviceRequestId?: string;
  /** 类目：24=手术 / 25=麻醉 */
  categoryEnum?: string;
  activityName?: string;
  procedureTime?: string;
  specimen?: string;
  note?: string;
  nurseId?: string;
  nurseName?: string;
  anesthesiaStartTime?: string;
  anesthesiaEndTime?: string;
  mainProcedureFlag?: boolean;
  procedureLevelCode?: string;
  anesthesiaMethodCode?: string;
  incisionTypeCode?: string;
  surgeonId?: string;
  surgeonName?: string;
  firstAssistantId?: string;
  firstAssistantName?: string;
  secondAssistantId?: string;
  secondAssistantName?: string;
  anesthetistId?: string;
  anesthetistName?: string;
  planTime?: string;
}

/** 提交手术/麻醉执行 */
export interface SubmitSurgeryAnesthExecuteRequest {
  encounterId: string;
  serviceRequestId: string;
  planTime: string;
  actualTime?: string;
  accountId: string;
  procedureTime?: string;
  specimen?: string;
  note?: string;
  nurseId?: string;
  anesthesiaStartTime?: string;
  anesthesiaEndTime?: string;
  bindItems?: NurseStationOrderExecuteBindItemDto[];
}

export interface SubmitSurgeryAnesthExecuteResponse {
  processedCount?: number;
  warnings?: string[];
}
