import type { AdviceRowItem } from './advice.types';

/** 历史就诊摘要（左栏卡片） */
export interface HistoryAdviceEncounterItem {
  encounterId: string;
  visitTime: string;
  departmentName: string;
  visitTypeText: string;
  primaryDiagnosis: string;
  orderingDoctor: string;
  adviceCountSummary: string;
  isCurrentEncounter?: boolean;
}

/** 历史医嘱行（表格 + 复用） */
export interface HistoryAdviceRowItem extends AdviceRowItem {
  encounterId: string;
  reusable?: boolean;
  reuseWarning?: string;
}

export interface HistoryAdviceEncounterBundle {
  encounter: HistoryAdviceEncounterItem;
  rows: HistoryAdviceRowItem[];
}
