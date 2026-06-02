/**
 * 岗位管理 · Tab「按人」— 医务人员主档展示（与后端 PractitionerWorkbenchDetailDto 一致）
 */
export type PractitionerStatusCode = 'ACTIVE' | 'INACTIVE' | 'DELETED';

export interface PractitionerSummary {
  id: string;
  name: string;
  busNo?: string;
  genderEnum?: string;
  phone?: string;
  orgId?: string;
  currentOrgName?: string;
  statusEnum: PractitionerStatusCode | string;
  userName?: string;
  /** 需提示用户到「医务人员」Tab 补充主档 */
  profileIncomplete?: boolean;
}
