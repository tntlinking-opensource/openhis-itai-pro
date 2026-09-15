import type { ComputedRef, InjectionKey, Ref } from 'vue';

import type {
  PatientAdmissionFilter,
  PatientItem,
  PatientStatusFilter,
  RegionItem,
  WardItem,
} from '../types';

export type { PatientAdmissionFilter, PatientStatusFilter };

export interface StatusOptionItem {
  label: string;
  value: PatientStatusFilter;
}

export interface AdmissionOptionItem {
  label: string;
  value: PatientAdmissionFilter;
}

export interface NurseStationPatientListPanelContext {
  totalPatientCount: ComputedRef<number>;
  pendingAuditCount: ComputedRef<number>;
  pendingExecuteCount: ComputedRef<number>;
  keyword: Ref<string>;
  statusFilter: Ref<PatientStatusFilter>;
  admissionFilter: Ref<PatientAdmissionFilter>;
  selectedRegionIds: Ref<string[]>;
  selectedWardIds: Ref<string[]>;
  filterPanelExpanded: Ref<boolean>;
  statusOptions: StatusOptionItem[];
  admissionOptions: AdmissionOptionItem[];
  regionOptions: Ref<RegionItem[]>;
  availableWards: ComputedRef<WardItem[]>;
  filteredPatients: ComputedRef<PatientItem[]>;
  /** 患者队列加载中（左侧列表小 loading） */
  queueLoading: Ref<boolean>;
  regionNameByIdRecord: ComputedRef<Record<string, string>>;
  wardNameByIdRecord: ComputedRef<Record<string, string>>;
  changeKeyword: (value: string) => void;
  changeStatusFilter: (value: PatientStatusFilter) => void;
  changeAdmissionFilter: (value: PatientAdmissionFilter) => void;
  changeRegionIds: (value: string[]) => void;
  changeWardIds: (value: string[]) => void;
  toggleFilterPanel: () => void;
  refreshPatientFilters: () => Promise<void>;
  /** 出院医嘱校对等变更就诊态后，同步刷新入出转床位图（含出 Tab） */
  refreshWorkbenchAfterEncounterFlowChange: () => Promise<void>;
  togglePatientCardSelection: (id: string) => void;
  /** 双击患者卡片打开患者体征卡 */
  onPatientCardDblClick: (patient: PatientItem) => void;
  togglePatientSelection: (id: string, value: boolean) => void;
  onPatientDragStart: (event: DragEvent, patient: PatientItem) => void;
  onPatientDragEnd: () => void;
  /** 展开患者一览大屏（铺满工作站） */
  openPatientLargeScreen: () => void;
}

export const nurseStationPatientListPanelContextKey: InjectionKey<NurseStationPatientListPanelContext> =
  Symbol('nurse-station-patient-list-panel-context');
