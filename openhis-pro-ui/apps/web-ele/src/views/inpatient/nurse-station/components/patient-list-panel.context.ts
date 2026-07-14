import type { ComputedRef, InjectionKey, Ref } from 'vue';

import type {
  PatientItem,
  PatientQueueScopeFilter,
  PatientStatusFilter,
  RegionItem,
  WardItem,
} from '../types';

export type { PatientQueueScopeFilter, PatientStatusFilter };

export interface StatusOptionItem {
  label: string;
  value: PatientStatusFilter;
}

export interface QueueScopeOptionItem {
  label: string;
  value: PatientQueueScopeFilter;
}

export interface NurseStationPatientListPanelContext {
  totalPatientCount: ComputedRef<number>;
  pendingAuditCount: ComputedRef<number>;
  pendingExecuteCount: ComputedRef<number>;
  keyword: Ref<string>;
  statusFilter: Ref<PatientStatusFilter>;
  queueScopeFilter: Ref<PatientQueueScopeFilter>;
  selectedRegionIds: Ref<string[]>;
  selectedWardIds: Ref<string[]>;
  filterPanelExpanded: Ref<boolean>;
  statusOptions: StatusOptionItem[];
  queueScopeOptions: QueueScopeOptionItem[];
  regionOptions: Ref<RegionItem[]>;
  availableWards: ComputedRef<WardItem[]>;
  filteredPatients: ComputedRef<PatientItem[]>;
  regionNameByIdRecord: ComputedRef<Record<string, string>>;
  wardNameByIdRecord: ComputedRef<Record<string, string>>;
  changeKeyword: (value: string) => void;
  changeStatusFilter: (value: PatientStatusFilter) => void;
  changeQueueScopeFilter: (value: PatientQueueScopeFilter) => void;
  changeRegionIds: (value: string[]) => void;
  changeWardIds: (value: string[]) => void;
  toggleFilterPanel: () => void;
  refreshPatientFilters: () => Promise<void>;
  /** 出院医嘱校对等变更就诊态后，同步刷新入出转床位图（含出 Tab） */
  refreshWorkbenchAfterEncounterFlowChange: () => Promise<void>;
  togglePatientCardSelection: (id: string) => void;
  /** 双击患者卡片打开入科选床弹窗 */
  onPatientCardDblClick: (patient: PatientItem) => void;
  togglePatientSelection: (id: string, value: boolean) => void;
  /** 出院召回（已出院结算列表行操作） */
  onRecallPatient: (patient: PatientItem) => void;
  onPatientDragStart: (event: DragEvent, patient: PatientItem) => void;
  onPatientDragEnd: () => void;
}

export const nurseStationPatientListPanelContextKey: InjectionKey<NurseStationPatientListPanelContext> =
  Symbol('nurse-station-patient-list-panel-context');
