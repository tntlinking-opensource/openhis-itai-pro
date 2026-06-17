import type { Component } from 'vue';

import type { SelectionPolicy } from './types';

import { defineAsyncComponent } from 'vue';

const loadDailySettlementModule = () => import('./daily-settlement/index.vue');
const loadDispenseSummaryModule = () => import('./dispense-summary/index.vue');
const loadCostDetailsModule = () => import('./cost-details/index.vue');
const loadInOutTransferModule = () => import('./in-out-transfer/index.vue');
const loadInpatientBillingModule = () => import('./inpatient-billing/index.vue');
const loadNursingRecordModule = () => import('./nursing-record/index.vue');
const loadOrderAuditModule = () => import('./order-audit/index.vue');
const loadOrderExecuteModule = () => import('./order-execute/index.vue');
const loadOrderViewModule = () => import('./order-view/index.vue');
const loadMedicalRecordReviewModule = () => import('./medical-record-review/index.vue');
const loadTemperatureSheetModule = () => import('./temperature-sheet/index.vue');

const DailySettlementModule = defineAsyncComponent(loadDailySettlementModule);
const DispenseSummaryModule = defineAsyncComponent(loadDispenseSummaryModule);
const CostDetailsModule = defineAsyncComponent(loadCostDetailsModule);
const InOutTransferModule = defineAsyncComponent(loadInOutTransferModule);
const InpatientBillingModule = defineAsyncComponent(loadInpatientBillingModule);
const NursingRecordModule = defineAsyncComponent(loadNursingRecordModule);
const OrderAuditModule = defineAsyncComponent(loadOrderAuditModule);
const OrderExecuteModule = defineAsyncComponent(loadOrderExecuteModule);
const OrderViewModule = defineAsyncComponent(loadOrderViewModule);
const MedicalRecordReviewModule = defineAsyncComponent(loadMedicalRecordReviewModule);
const TemperatureSheetModule = defineAsyncComponent(loadTemperatureSheetModule);

export const operationTabs = [
  '入出转管理',
  '医嘱校对',
  '医嘱执行',
  '汇总领药',
  '护理记录',
  '体温单',
  '住院记账',
  '住院日结',
  '费用明细',
  '病历调阅',
  '医嘱查看',
] as const;

export type OperationTab = (typeof operationTabs)[number];

export const operationSelectionPolicyByTab: Record<OperationTab, SelectionPolicy> = {
  入出转管理: 'single',
  费用明细: 'single',
  医嘱校对: 'multiple',
  医嘱执行: 'multiple',
  汇总领药: 'multiple',
  护理记录: 'single',
  住院日结: 'single',
  体温单: 'single',
  住院记账: 'single',
  病历调阅: 'single',
  医嘱查看: 'single',
};

export const operationComponentByTab: Partial<Record<OperationTab, Component>> = {
  医嘱执行: OrderExecuteModule,
  医嘱校对: OrderAuditModule,
  医嘱查看: OrderViewModule,
  汇总领药: DispenseSummaryModule,
  护理记录: NursingRecordModule,
  住院日结: DailySettlementModule,
  入出转管理: InOutTransferModule,
  体温单: TemperatureSheetModule,
  住院记账: InpatientBillingModule,
  费用明细: CostDetailsModule,
  病历调阅: MedicalRecordReviewModule,
};

export const operationLoaderByTab: Partial<Record<OperationTab, () => Promise<unknown>>> = {
  医嘱执行: loadOrderExecuteModule,
  医嘱校对: loadOrderAuditModule,
  医嘱查看: loadOrderViewModule,
  汇总领药: loadDispenseSummaryModule,
  护理记录: loadNursingRecordModule,
  住院日结: loadDailySettlementModule,
  入出转管理: loadInOutTransferModule,
  体温单: loadTemperatureSheetModule,
  住院记账: loadInpatientBillingModule,
  费用明细: loadCostDetailsModule,
  病历调阅: loadMedicalRecordReviewModule,
};
