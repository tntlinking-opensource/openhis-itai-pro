/** 静默轮询检测到的待校/待执增量（用于浮层提示） */
export interface NurseStationTaskIncreaseHint {
  encounterId: string;
  patientName: string;
  bedNo: string;
  /** 待校对数量相对上次是否上升 */
  auditIncreased: boolean;
  /** 待执行数量相对上次是否上升 */
  executeIncreased: boolean;
  pendingAudit: number;
  pendingExecute: number;
}

/** 浮层条目 */
export interface NurseStationTaskNotifyCard {
  id: string;
  encounterId: string;
  patientName: string;
  bedNo: string;
  /** 点击后跳转的目标页签 */
  targetTab: 'ORDER_AUDIT' | 'ORDER_EXECUTE';
  /** 展示文案 */
  message: string;
}
