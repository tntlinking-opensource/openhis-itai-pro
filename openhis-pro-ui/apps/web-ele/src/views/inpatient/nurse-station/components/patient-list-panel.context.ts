import type { ComputedRef, InjectionKey, Ref } from 'vue';

import type {
  PatientAdmissionFilter,
  PatientItem,
  PatientTaskFilter,
  RegionItem,
  SelectionPolicy,
  WardItem,
} from '../types';

export type { PatientAdmissionFilter, PatientTaskFilter };

export interface StatusOptionItem {
  label: string;
  value: PatientTaskFilter;
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
  /** 任务多选；空数组=全部任务 */
  statusFilters: Ref<PatientTaskFilter[]>;
  admissionFilter: Ref<PatientAdmissionFilter>;
  /** 仅看今日入院（与在科/任务筛选 AND；本地过滤不重拉队列） */
  todayAdmissionOnly: Ref<boolean>;
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
  changeStatusFilters: (value: PatientTaskFilter[]) => void;
  changeAdmissionFilter: (value: PatientAdmissionFilter) => void;
  /** 切换「今日入院」本地筛选 */
  changeTodayAdmissionOnly: (value: boolean) => void;
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
  /** 当前页签选择策略（多选时展示队列全选） */
  selectionPolicy: Ref<SelectionPolicy>;
  /** 当前筛选队列是否已全部勾选 */
  allFilteredPatientsSelected: ComputedRef<boolean>;
  /** 当前筛选队列是否部分勾选 */
  filteredPatientsIndeterminate: ComputedRef<boolean>;
  /** 全选/取消全选当前筛选队列 */
  toggleSelectAllFilteredPatients: (value: boolean) => void;
  onPatientDragStart: (event: DragEvent, patient: PatientItem) => void;
  onPatientDragEnd: () => void;
  /** 展开患者一览大屏（铺满工作站） */
  openPatientLargeScreen: () => void;
}

export const nurseStationPatientListPanelContextKey: InjectionKey<NurseStationPatientListPanelContext> =
  Symbol('nurse-station-patient-list-panel-context');
