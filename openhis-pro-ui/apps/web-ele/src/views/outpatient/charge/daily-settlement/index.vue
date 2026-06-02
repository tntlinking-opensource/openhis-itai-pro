<script setup lang="ts">
import { computed, reactive } from 'vue';

import { Page } from '@vben/common-ui';

import {
  ElButton,
  ElCard,
  ElDatePicker,
  ElForm,
  ElFormItem,
  ElInput,
  ElOption,
  ElSelect,
} from 'element-plus';

defineOptions({
  name: 'ChargeOpDailySettlement',
});

interface SummaryItem {
  label: string;
  value: string;
}

interface QueryFormState {
  dateRange: [string, string];
  expenseProperty: string;
  cashier: string;
}

interface ReconciliationFormState {
  settlementOrg: string;
  settlementDateRange: [string, string];
  settlementType: string;
  chargeTotal: string;
  fundTotal: string;
  personalPayTotal: string;
  cashPayTotal: string;
}

const queryForm = reactive<QueryFormState>({
  dateRange: ['2026-04-28', '2026-04-28'],
  expenseProperty: 'selfPay',
  cashier: '',
});

const reconciliationForm = reactive<ReconciliationFormState>({
  settlementOrg: 'jilin',
  settlementDateRange: ['2026-03-01', '2026-03-31'],
  settlementType: 'clinic',
  chargeTotal: '0',
  fundTotal: '0',
  personalPayTotal: '0',
  cashPayTotal: '0',
});

const feeSummaryColumns = computed<SummaryItem[][]>(() => [
  [
    { label: '西药', value: '33.55 元' },
    { label: '检查', value: '0.00 元' },
    { label: '中成药', value: '40.49 元' },
    { label: '医用耗材', value: '5.82 元' },
  ],
  [
    { label: '护理', value: '0.00 元' },
    { label: '治疗', value: '0.00 元' },
    { label: '化验', value: '22.00 元' },
    { label: '外科处置', value: '0.00 元' },
  ],
  [
    { label: '处置费', value: '20.00 元' },
    { label: '彩超费', value: '0.00 元' },
    { label: 'X 光费', value: '150.00 元' },
    { label: '心电费', value: '0.00 元' },
  ],
  [
    { label: '注射费', value: '8.00 元' },
    { label: '其他', value: '0.00 元' },
    { label: '挂号费', value: '7.00 元' },
    { label: '体检', value: '0.00 元' },
  ],
  [
    { label: '眼科处置', value: '0.00 元' },
    { label: '耳鼻喉科处置', value: '0.00 元' },
    { label: '中医治疗', value: '0.00 元' },
  ],
]);

const incomeSummaryColumns = computed<SummaryItem[][]>(() => [
  [
    { label: '总收入', value: '286.87 元' },
    { label: '应收金额', value: '286.87 元' },
    { label: '优惠金额', value: '0.00 元' },
    { label: '实收金额', value: '286.87 元' },
  ],
  [
    { label: '微信', value: '286.87 元' },
    { label: '支付宝', value: '0.00 元' },
    { label: '网银', value: '0.00 元' },
    { label: '现金', value: '0.00 元' },
  ],
  [
    { label: '自费人次', value: '7 人' },
    { label: '省医保人次', value: '1 人' },
    { label: '市医保人次', value: '2 人' },
  ],
  [
    { label: '省医保现金', value: '0.00 元' },
    { label: '省医保统筹', value: '0.00 元' },
    { label: '省医保账户', value: '0.00 元' },
    { label: '省医保基金', value: '0.00 元' },
  ],
  [
    { label: '市医保现金', value: '0.00 元' },
    { label: '市医保统筹', value: '0.00 元' },
    { label: '市医保账户', value: '0.00 元' },
    { label: '市医保基金', value: '0.00 元' },
  ],
  [
    { label: '省医保总额', value: '0.00 元' },
    { label: '市医保总额', value: '0.00 元' },
  ],
  [
    { label: '现金收入总额', value: '286.87 元' },
    { label: '体检人次', value: '0' },
  ],
]);

// 执行日结单查询，后续可接入后端接口。
function handleSearch() {
  // 仅绘制页面结构，当前保留占位行为。
}

// 触发日结单打印，后续可替换为打印服务。
function handlePrint() {
  // 仅绘制页面结构，当前保留占位行为。
}

// 清空医保对账输入区。
function handleClearReconciliation() {
  reconciliationForm.chargeTotal = '0';
  reconciliationForm.fundTotal = '0';
  reconciliationForm.personalPayTotal = '0';
  reconciliationForm.cashPayTotal = '0';
}

// 提交医保对账清算，后续可接入结算接口。
function handleSubmitReconciliation() {
  // 仅绘制页面结构，当前保留占位行为。
}
</script>

<template>
  <Page class="min-h-0 bg-[var(--el-fill-color-light)] p-2">
    <div class="grid h-full min-h-0 grid-cols-[2fr_1fr] gap-2">
      <div class="flex min-h-0 flex-col gap-2">
        <ElCard shadow="never">
          <ElForm
            :inline="true"
            :model="queryForm"
            class="daily-settlement-query-form flex flex-nowrap items-center overflow-x-auto"
          >
            <ElFormItem label="查询日期">
              <ElDatePicker
                v-model="queryForm.dateRange"
                type="daterange"
                value-format="YYYY-MM-DD"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                class="!w-[360px]"
              />
            </ElFormItem>
            <ElFormItem label="费用性质">
              <ElSelect v-model="queryForm.expenseProperty" class="!w-[120px]">
                <ElOption label="自费" value="selfPay" />
                <ElOption label="医保" value="insurance" />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="经办人">
              <ElSelect v-model="queryForm.cashier" placeholder="经办人" class="!w-[120px]">
                <ElOption label="经办人A" value="cashierA" />
                <ElOption label="经办人B" value="cashierB" />
              </ElSelect>
            </ElFormItem>
            <div class="ml-auto flex shrink-0 items-center gap-2">
              <ElButton type="primary" @click="handleSearch">查询</ElButton>
              <ElButton @click="handlePrint">打印</ElButton>
            </div>
          </ElForm>
        </ElCard>

        <ElCard shadow="never" class="daily-settlement-card min-h-0 flex-1">
          <div class="flex h-full min-h-0 flex-col gap-4">
            <h3 class="text-center text-xl font-semibold tracking-wide">门诊收费日结单</h3>

            <div
              class="grid grid-cols-5 gap-4 border-b border-[var(--el-border-color)] pb-3 text-sm"
            >
              <div><span class="text-[var(--el-text-color-regular)]">经办人姓名：</span> 全部</div>
              <div><span class="text-[var(--el-text-color-regular)]">科室：</span></div>
              <div>
                <span class="text-[var(--el-text-color-regular)]">机构：</span> 长春大学医院
              </div>
              <div class="col-span-2">
                <span class="text-[var(--el-text-color-regular)]">时间：</span>
                2026-04-28~2026-04-28
              </div>
            </div>

            <div class="flex flex-col gap-3 border-b border-[var(--el-border-color)] pb-4">
              <div
                v-for="(row, rowIndex) in feeSummaryColumns"
                :key="`fee-${rowIndex}`"
                class="grid grid-cols-4 gap-x-6 gap-y-2 text-sm"
              >
                <div v-for="item in row" :key="item.label" class="flex justify-between gap-2">
                  <span>{{ item.label }}：</span>
                  <span class="font-medium">{{ item.value }}</span>
                </div>
              </div>
            </div>

            <div class="flex flex-col gap-3">
              <div
                v-for="(row, rowIndex) in incomeSummaryColumns"
                :key="`income-${rowIndex}`"
                class="grid grid-cols-4 gap-x-6 gap-y-2 text-sm"
              >
                <div v-for="item in row" :key="item.label" class="flex justify-between gap-2">
                  <span>{{ item.label }}：</span>
                  <span class="font-medium">{{ item.value }}</span>
                </div>
              </div>
            </div>
          </div>
        </ElCard>
      </div>

      <ElCard shadow="never" class="daily-settlement-card h-full min-h-0">
        <div class="flex h-full min-h-0 flex-col">
          <div class="mb-3 border-b border-[var(--el-border-color)] pb-2 text-sm font-medium">
            医保对账
          </div>
          <ElForm :model="reconciliationForm" label-width="108px" class="grid grid-cols-1 gap-2">
            <ElFormItem label="结算统办机构">
              <ElSelect v-model="reconciliationForm.settlementOrg" class="w-full">
                <ElOption label="吉林省医保" value="jilin" />
                <ElOption label="长春市医保" value="changchun" />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="结算日期">
              <ElDatePicker
                v-model="reconciliationForm.settlementDateRange"
                type="daterange"
                value-format="YYYY-MM-DD"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                class="w-full"
              />
            </ElFormItem>
            <ElFormItem label="清算类型">
              <ElSelect v-model="reconciliationForm.settlementType" class="w-full">
                <ElOption label="门诊" value="clinic" />
                <ElOption label="住院" value="hospital" />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="医疗费总额">
              <ElInput v-model="reconciliationForm.chargeTotal" />
            </ElFormItem>
            <ElFormItem label="基金支付总额">
              <ElInput v-model="reconciliationForm.fundTotal" />
            </ElFormItem>
            <ElFormItem label="个人账户支付总额">
              <ElInput v-model="reconciliationForm.personalPayTotal" />
            </ElFormItem>
            <ElFormItem label="现金支付总额">
              <ElInput v-model="reconciliationForm.cashPayTotal" />
            </ElFormItem>
          </ElForm>
          <div
            class="mt-auto flex items-center justify-end gap-2 border-t border-[var(--el-border-color)] pt-3"
          >
            <ElButton @click="handleClearReconciliation">清算</ElButton>
            <ElButton type="primary" @click="handleSubmitReconciliation">手动清算</ElButton>
          </div>
        </div>
      </ElCard>
    </div>
  </Page>
</template>

<style scoped>
.daily-settlement-card :deep(.el-card__body) {
  padding: 12px;
}

.daily-settlement-query-form :deep(.el-form-item) {
  margin-right: 12px;
  margin-bottom: 0;
}
</style>
