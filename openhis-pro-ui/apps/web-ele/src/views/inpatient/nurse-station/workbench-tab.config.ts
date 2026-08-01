import type { Component } from 'vue';

import type { SelectionPolicy } from './types';

import { defineAsyncComponent } from 'vue';

const loadDispenseSummaryModule = () => import('./dispense-summary/index.vue');
const loadCostDetailsModule = () => import('./cost-details/index.vue');
const loadInOutTransferModule = () => import('./in-out-transfer/index.vue');
const loadInpatientBillingModule = () => import('./inpatient-billing/index.vue');
const loadNursingRecordModule = () => import('./nursing-record/index.vue');
const loadOrderAuditModule = () => import('./order-audit/index.vue');
const loadOrderExecuteModule = () => import('./order-execute/index.vue');
const loadOrderViewModule = () => import('./order-view/index.vue');
const loadMedicalRecordReviewModule = () => import('./medical-record-review/index.vue');
const loadPrintCenterModule = () => import('./print-center/index.vue');
const loadSkinTestModule = () => import('./skin-test/index.vue');
const loadTemperatureSheetModule = () => import('./temperature-sheet/index.vue');

const DispenseSummaryModule = defineAsyncComponent(loadDispenseSummaryModule);
const CostDetailsModule = defineAsyncComponent(loadCostDetailsModule);
const InOutTransferModule = defineAsyncComponent(loadInOutTransferModule);
const InpatientBillingModule = defineAsyncComponent(loadInpatientBillingModule);
const NursingRecordModule = defineAsyncComponent(loadNursingRecordModule);
const OrderAuditModule = defineAsyncComponent(loadOrderAuditModule);
const OrderExecuteModule = defineAsyncComponent(loadOrderExecuteModule);
const OrderViewModule = defineAsyncComponent(loadOrderViewModule);
const MedicalRecordReviewModule = defineAsyncComponent(loadMedicalRecordReviewModule);
const PrintCenterModule = defineAsyncComponent(loadPrintCenterModule);
const SkinTestModule = defineAsyncComponent(loadSkinTestModule);
const TemperatureSheetModule = defineAsyncComponent(loadTemperatureSheetModule);

/** 住院护士站工作台页签 code（逻辑判断用） */
export const OPERATION_TAB = {
  IN_OUT_TRANSFER: 'IN_OUT_TRANSFER',
  ORDER_AUDIT: 'ORDER_AUDIT',
  ORDER_EXECUTE: 'ORDER_EXECUTE',
  DISPENSE_SUMMARY: 'DISPENSE_SUMMARY',
  SKIN_TEST: 'SKIN_TEST',
  NURSING_RECORD: 'NURSING_RECORD',
  TEMPERATURE_SHEET: 'TEMPERATURE_SHEET',
  INPATIENT_BILLING: 'INPATIENT_BILLING',
  COST_DETAILS: 'COST_DETAILS',
  MEDICAL_RECORD_REVIEW: 'MEDICAL_RECORD_REVIEW',
  ORDER_VIEW: 'ORDER_VIEW',
  PRINT_CENTER: 'PRINT_CENTER',
} as const;

export type OperationTab = (typeof OPERATION_TAB)[keyof typeof OPERATION_TAB];

export const OPERATION_TAB_LABEL: Record<OperationTab, string> = {
  [OPERATION_TAB.IN_OUT_TRANSFER]: '入出转管理',
  [OPERATION_TAB.ORDER_AUDIT]: '医嘱校对',
  [OPERATION_TAB.ORDER_EXECUTE]: '医嘱执行',
  [OPERATION_TAB.DISPENSE_SUMMARY]: '汇总领药',
  [OPERATION_TAB.SKIN_TEST]: '皮试管理',
  [OPERATION_TAB.NURSING_RECORD]: '护理记录',
  [OPERATION_TAB.TEMPERATURE_SHEET]: '体温单',
  [OPERATION_TAB.INPATIENT_BILLING]: '住院记账',
  [OPERATION_TAB.COST_DETAILS]: '费用明细',
  [OPERATION_TAB.MEDICAL_RECORD_REVIEW]: '病历调阅',
  [OPERATION_TAB.ORDER_VIEW]: '医嘱查看',
  [OPERATION_TAB.PRINT_CENTER]: '打印中心',
};

/** 未分床时业务页签统一提示（入出转除外） */
export const NURSE_STATION_BED_REQUIRED_MESSAGE = '请先完成分床后再使用该功能';

/** 入出转以外页签均要求已分床 */
export function doesNurseStationTabRequireBed(tab: OperationTab): boolean {
  return tab !== OPERATION_TAB.IN_OUT_TRANSFER;
}

/** 导航栏展示顺序 */
export const operationTabs: OperationTab[] = [
  OPERATION_TAB.IN_OUT_TRANSFER,
  OPERATION_TAB.ORDER_AUDIT,
  OPERATION_TAB.ORDER_EXECUTE,
  OPERATION_TAB.DISPENSE_SUMMARY,
  OPERATION_TAB.SKIN_TEST,
  OPERATION_TAB.NURSING_RECORD,
  OPERATION_TAB.TEMPERATURE_SHEET,
  OPERATION_TAB.INPATIENT_BILLING,
  OPERATION_TAB.COST_DETAILS,
  OPERATION_TAB.PRINT_CENTER,
  OPERATION_TAB.MEDICAL_RECORD_REVIEW,
  OPERATION_TAB.ORDER_VIEW,
];

/** 暂不上线的页签，不在导航栏展示 */
const hiddenOperationTabs = new Set<OperationTab>([
  OPERATION_TAB.MEDICAL_RECORD_REVIEW,
  OPERATION_TAB.ORDER_VIEW,
]);

export const visibleOperationTabs = operationTabs.filter((tab) => !hiddenOperationTabs.has(tab));

export const operationSelectionPolicyByTab: Record<OperationTab, SelectionPolicy> = {
  [OPERATION_TAB.IN_OUT_TRANSFER]: 'single',
  [OPERATION_TAB.COST_DETAILS]: 'single',
  [OPERATION_TAB.ORDER_AUDIT]: 'multiple',
  [OPERATION_TAB.ORDER_EXECUTE]: 'multiple',
  [OPERATION_TAB.DISPENSE_SUMMARY]: 'multiple',
  [OPERATION_TAB.SKIN_TEST]: 'single',
  [OPERATION_TAB.NURSING_RECORD]: 'single',
  [OPERATION_TAB.TEMPERATURE_SHEET]: 'single',
  [OPERATION_TAB.INPATIENT_BILLING]: 'single',
  [OPERATION_TAB.MEDICAL_RECORD_REVIEW]: 'single',
  [OPERATION_TAB.ORDER_VIEW]: 'single',
  /** 打印中心按单据动态约束单/多选，默认多选以便批量贴签 */
  [OPERATION_TAB.PRINT_CENTER]: 'multiple',
};

export const operationComponentByTab: Partial<Record<OperationTab, Component>> = {
  [OPERATION_TAB.ORDER_EXECUTE]: OrderExecuteModule,
  [OPERATION_TAB.ORDER_AUDIT]: OrderAuditModule,
  [OPERATION_TAB.ORDER_VIEW]: OrderViewModule,
  [OPERATION_TAB.DISPENSE_SUMMARY]: DispenseSummaryModule,
  [OPERATION_TAB.SKIN_TEST]: SkinTestModule,
  [OPERATION_TAB.NURSING_RECORD]: NursingRecordModule,
  [OPERATION_TAB.IN_OUT_TRANSFER]: InOutTransferModule,
  [OPERATION_TAB.TEMPERATURE_SHEET]: TemperatureSheetModule,
  [OPERATION_TAB.INPATIENT_BILLING]: InpatientBillingModule,
  [OPERATION_TAB.COST_DETAILS]: CostDetailsModule,
  [OPERATION_TAB.MEDICAL_RECORD_REVIEW]: MedicalRecordReviewModule,
  [OPERATION_TAB.PRINT_CENTER]: PrintCenterModule,
};

export const operationLoaderByTab: Partial<Record<OperationTab, () => Promise<unknown>>> = {
  [OPERATION_TAB.ORDER_EXECUTE]: loadOrderExecuteModule,
  [OPERATION_TAB.ORDER_AUDIT]: loadOrderAuditModule,
  [OPERATION_TAB.ORDER_VIEW]: loadOrderViewModule,
  [OPERATION_TAB.DISPENSE_SUMMARY]: loadDispenseSummaryModule,
  [OPERATION_TAB.SKIN_TEST]: loadSkinTestModule,
  [OPERATION_TAB.NURSING_RECORD]: loadNursingRecordModule,
  [OPERATION_TAB.IN_OUT_TRANSFER]: loadInOutTransferModule,
  [OPERATION_TAB.TEMPERATURE_SHEET]: loadTemperatureSheetModule,
  [OPERATION_TAB.INPATIENT_BILLING]: loadInpatientBillingModule,
  [OPERATION_TAB.COST_DETAILS]: loadCostDetailsModule,
  [OPERATION_TAB.MEDICAL_RECORD_REVIEW]: loadMedicalRecordReviewModule,
  [OPERATION_TAB.PRINT_CENTER]: loadPrintCenterModule,
};

/** 多选患者入参的页签 */
export const MULTI_PATIENT_OPERATION_TABS = new Set<OperationTab>([
  OPERATION_TAB.ORDER_EXECUTE,
  OPERATION_TAB.ORDER_AUDIT,
  OPERATION_TAB.DISPENSE_SUMMARY,
  OPERATION_TAB.PRINT_CENTER,
]);

/** 单选患者入参的页签 */
export const SINGLE_PATIENT_OPERATION_TABS = new Set<OperationTab>([
  OPERATION_TAB.ORDER_VIEW,
  OPERATION_TAB.SKIN_TEST,
  OPERATION_TAB.NURSING_RECORD,
  OPERATION_TAB.TEMPERATURE_SHEET,
  OPERATION_TAB.INPATIENT_BILLING,
  OPERATION_TAB.COST_DETAILS,
  OPERATION_TAB.MEDICAL_RECORD_REVIEW,
]);
