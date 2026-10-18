<script setup lang="ts">
import type { CommonLineRequest, IssueCommonRequest } from '#/api/einvoice-boss/common';

import { computed, reactive, ref } from 'vue';

import { Page } from '@zxplat/common-ui';

import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElSelect,
} from 'element-plus';

import { issueCommonApi } from '#/api/einvoice-boss/common';
import { useRequest } from '#/hooks/use-request';
import { formatMoneyDisplay, sumMoneyThenSettle } from '#/utils/format/money-scale';

defineOptions({
  name: 'EinvoiceCommon',
});

const formModel = reactive<IssueCommonRequest>({
  commonBusType: '11',
  payerType: '1',
  payerSex: '男',
  payerAge: '0',
  lines: [{ chargeCode: '', chargeName: '', amount: undefined, quantity: 1, unit: '次' }],
});

const result = ref<Awaited<ReturnType<typeof issueCommonApi>> | null>(null);

const { run: runIssue, loading } = useRequest(issueCommonApi, { manual: true });

const lineTotal = computed(() =>
  sumMoneyThenSettle((formModel.lines ?? []).map((line) => line.amount ?? 0)),
);

function addLine() {
  formModel.lines = [
    ...(formModel.lines ?? []),
    { chargeCode: '', chargeName: '', amount: undefined, quantity: 1, unit: '次' },
  ];
}

function removeLine(index: number) {
  const lines = [...(formModel.lines ?? [])];
  if (lines.length <= 1) {
    return;
  }
  lines.splice(index, 1);
  formModel.lines = lines;
}

async function handleSubmit() {
  const payer = formModel.payer?.trim();
  if (!payer) {
    ElMessage.warning('请填写交款人');
    return;
  }
  const lines = (formModel.lines ?? [])
    .map((line) => ({
      chargeCode: line.chargeCode?.trim(),
      chargeName: line.chargeName?.trim(),
      amount: line.amount,
      quantity: line.quantity ?? 1,
      unit: line.unit?.trim() || '次',
    }))
    .filter(
      (line) => line.chargeCode && line.chargeName && line.amount && line.amount > 0,
    ) as CommonLineRequest[];
  if (lines.length === 0) {
    ElMessage.warning('请至少填写一条有效明细');
    return;
  }
  const totalAmt = lines.reduce((sum, line) => sum + Number(line.amount ?? 0), 0);
  result.value = await runIssue({
    commonBusType: formModel.commonBusType,
    payer,
    payerPhone: formModel.payerPhone?.trim() || undefined,
    payerIdCard: formModel.payerIdCard?.trim() || undefined,
    payerType: formModel.payerType,
    payerSex: formModel.payerSex,
    payerAge: formModel.payerAge,
    remark: formModel.remark?.trim() || undefined,
    operatorName: formModel.operatorName?.trim() || undefined,
    totalAmt,
    lines,
  });
  if (result.value?.success) {
    ElMessage.success(result.value.billNo ? `开具成功，票号 ${result.value.billNo}` : '开具成功');
  } else {
    ElMessage.error(result.value?.message || '开具失败');
  }
}
</script>

<template>
  <Page auto-content-height>
    <ElForm :model="formModel" label-width="110px" class="max-w-4xl">
      <ElFormItem label="票据类型" required>
        <ElSelect v-model="formModel.commonBusType" class="w-full">
          <ElOption label="往来票 (09)" value="09" />
          <ElOption label="捐赠票 (10)" value="10" />
          <ElOption label="非税通用票 (11)" value="11" />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="交款人" required>
        <ElInput v-model="formModel.payer" placeholder="个人或单位名称" />
      </ElFormItem>
      <ElFormItem label="手机号">
        <ElInput v-model="formModel.payerPhone" />
      </ElFormItem>
      <ElFormItem label="证件号">
        <ElInput v-model="formModel.payerIdCard" />
      </ElFormItem>
      <ElFormItem label="交款人类型">
        <ElSelect v-model="formModel.payerType" class="w-full">
          <ElOption label="个人" value="1" />
          <ElOption label="单位" value="2" />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="性别">
        <ElInput v-model="formModel.payerSex" />
      </ElFormItem>
      <ElFormItem label="年龄">
        <ElInput v-model="formModel.payerAge" />
      </ElFormItem>
      <ElFormItem label="经办人">
        <ElInput v-model="formModel.operatorName" placeholder="可选" />
      </ElFormItem>
      <ElFormItem label="备注">
        <ElInput v-model="formModel.remark" type="textarea" :rows="2" />
      </ElFormItem>

      <div class="mb-2 text-sm font-medium text-[var(--el-text-color-primary)]">收费明细</div>
      <div
        v-for="(line, index) in formModel.lines"
        :key="index"
        class="mb-3 grid gap-3 rounded border border-[var(--el-border-color-light)] p-3 md:grid-cols-4"
      >
        <ElInput v-model="line.chargeCode" placeholder="收费编码" />
        <ElInput v-model="line.chargeName" placeholder="收费名称" />
        <ElInputNumber
          v-model="line.amount"
          :min="0.01"
          :precision="2"
          class="w-full"
          placeholder="金额"
        />
        <div class="flex gap-2">
          <ElInputNumber v-model="line.quantity" :min="1" :precision="2" class="flex-1" />
          <ElButton
            v-if="(formModel.lines?.length ?? 0) > 1"
            link
            type="danger"
            @click="removeLine(index)"
          >
            删除
          </ElButton>
        </div>
      </div>
      <ElButton class="mb-4" @click="addLine">添加明细</ElButton>

      <div class="mb-4 text-sm">
        明细合计：<strong>{{ formatMoneyDisplay(lineTotal) }}</strong> 元
      </div>

      <ElFormItem>
        <ElButton type="primary" :loading="loading" @click="handleSubmit">立即开具</ElButton>
      </ElFormItem>
    </ElForm>

    <div
      v-if="result"
      class="mt-4 rounded border border-[var(--el-border-color-light)] p-4 text-sm"
    >
      <div>busNo：{{ result.busNo || '-' }}</div>
      <div>票号：{{ result.billNo || '-' }}</div>
      <div>批次号：{{ result.billBatchCode || '-' }}</div>
      <div v-if="result.pictureUrl">内网链接：{{ result.pictureUrl }}</div>
      <div v-if="result.pictureNetUrl">外网链接：{{ result.pictureNetUrl }}</div>
    </div>
  </Page>
</template>
