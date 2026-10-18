import type { Component, InjectionKey, Ref } from 'vue';

import type {
  MedicalTechEncounterScope,
  MedicalTechPatientQueueItemDto,
} from '#/api/medical-tech/medical-tech-execution.types';

import { defineAsyncComponent } from 'vue';

const loadExecutionPanel = () => import('./execution/execution-panel.vue');
const loadFeeListPanel = () => import('./fee-list/fee-list-panel.vue');
const loadChargingPanel = () => import('./charging/charging-panel.vue');

const ExecutionPanel = defineAsyncComponent(loadExecutionPanel);
const FeeListPanel = defineAsyncComponent(loadFeeListPanel);
const ChargingPanel = defineAsyncComponent(loadChargingPanel);

/** 医技工作站页签 code */
export const MEDICAL_TECH_TAB = {
  EXECUTION: 'EXECUTION',
  FEE_LIST: 'FEE_LIST',
  CHARGING: 'CHARGING',
} as const;

export type MedicalTechTab = (typeof MEDICAL_TECH_TAB)[keyof typeof MEDICAL_TECH_TAB];

export const MEDICAL_TECH_TAB_LABEL: Record<MedicalTechTab, string> = {
  [MEDICAL_TECH_TAB.EXECUTION]: '医技执行',
  [MEDICAL_TECH_TAB.FEE_LIST]: '费用清单',
  [MEDICAL_TECH_TAB.CHARGING]: '医技计费',
};

/** 导航栏展示顺序（计费是否可见由壳层按 encounterScope 裁剪） */
export const medicalTechTabs: MedicalTechTab[] = [
  MEDICAL_TECH_TAB.EXECUTION,
  MEDICAL_TECH_TAB.FEE_LIST,
  MEDICAL_TECH_TAB.CHARGING,
];

export const medicalTechComponentByTab: Record<MedicalTechTab, Component> = {
  [MEDICAL_TECH_TAB.EXECUTION]: ExecutionPanel,
  [MEDICAL_TECH_TAB.FEE_LIST]: FeeListPanel,
  [MEDICAL_TECH_TAB.CHARGING]: ChargingPanel,
};

/** 壳层共享上下文：就诊类别 + 当前就诊 + 患者摘要 + 队列刷新 */
export interface MedicalTechWorkstationContext {
  encounterScope: Ref<MedicalTechEncounterScope>;
  selectedEncounterId: Ref<string>;
  /** 当前选中队列行摘要；费用清单 / 执行信息条共用 */
  selectedPatientSummary: Ref<MedicalTechPatientQueueItemDto | null>;
  /** 执行成功后刷新壳层左侧队列 */
  reloadPatientQueue: () => Promise<void>;
  /**
   * 右侧内容强制重载令牌：再次点选同一患者或点「刷新」时递增，
   * 避免 selectedEncounterId 不变导致 watch 不触发。
   */
  contentReloadToken: Ref<number>;
}

export const medicalTechWorkstationContextKey: InjectionKey<MedicalTechWorkstationContext> = Symbol(
  'medicalTechWorkstationContext',
);
