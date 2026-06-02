import type { ComputedRef, InjectionKey, Ref } from 'vue';

import type { PatientItem, PatientStatusFilter, RegionItem, WardItem } from '../types';

export type { PatientStatusFilter };

export interface StatusOptionItem {
  label: string;
  value: PatientStatusFilter;
}

export interface NurseStationPatientListPanelContext {
  totalPatientCount: ComputedRef<number>;
  pendingAuditCount: ComputedRef<number>;
  pendingExecuteCount: ComputedRef<number>;
  keyword: Ref<string>;
  statusFilter: Ref<PatientStatusFilter>;
  selectedRegionIds: Ref<string[]>;
  selectedWardIds: Ref<string[]>;
  filterPanelExpanded: Ref<boolean>;
  statusOptions: StatusOptionItem[];
  regionOptions: Ref<RegionItem[]>;
  availableWards: ComputedRef<WardItem[]>;
  filteredPatients: ComputedRef<PatientItem[]>;
  regionNameByIdRecord: ComputedRef<Record<string, string>>;
  wardNameByIdRecord: ComputedRef<Record<string, string>>;
  changeKeyword: (value: string) => void;
  changeStatusFilter: (value: PatientStatusFilter) => void;
  changeRegionIds: (value: string[]) => void;
  changeWardIds: (value: string[]) => void;
  toggleFilterPanel: () => void;
  refreshPatientFilters: () => Promise<void>;
  togglePatientCardSelection: (id: string) => void;
  /** 双击患者卡片打开入科选床弹窗 */
  onPatientCardDblClick: (patient: PatientItem) => void;
  togglePatientSelection: (id: string, value: boolean) => void;
  onPatientDragStart: (event: DragEvent, patient: PatientItem) => void;
  onPatientDragEnd: () => void;
}

export const nurseStationPatientListPanelContextKey: InjectionKey<NurseStationPatientListPanelContext> =
  Symbol('nurse-station-patient-list-panel-context');
