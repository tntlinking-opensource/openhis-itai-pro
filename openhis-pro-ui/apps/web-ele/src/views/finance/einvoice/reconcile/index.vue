<script setup lang="ts">
import type { CheckDailyTotalResponse, ReconcileDateMode } from '#/api/einvoice-boss/reconcile';

import { computed, reactive, ref } from 'vue';

import { Page } from '@zxplat/common-ui';

import {
  ElAlert,
  ElButton,
  ElDatePicker,
  ElDescriptions,
  ElDescriptionsItem,
  ElForm,
  ElFormItem,
  ElMessage,
  ElOption,
  ElSelect,
  ElTag,
} from 'element-plus';

import { checkDailyTotalApi } from '#/api/einvoice-boss/reconcile';
import { useRequest } from '#/hooks/use-request';
import { formatMoneyDisplay } from '#/utils/format/money-scale';

defineOptions({
  name: 'EinvoiceReconcile',
});

const result = ref<CheckDailyTotalResponse | null>(null);

const formModel = reactive<{
  checkDate?: string;
  dateMode: ReconcileDateMode;
  issueKind?: string;
}>({
  dateMode: 'BUS_DATE',
});

const { run: runCheck, loading } = useRequest(checkDailyTotalApi, { manual: true });

const matchTagType = computed(() => {
  if (!result.value?.bossSuccess) {
    return 'warning';
  }
  return result.value.matched ? 'success' : 'danger';
});

const matchLabel = computed(() => {
  if (!result.value?.bossSuccess) {
    return '博思查询失败';
  }
  return result.value.matched ? '笔数一致' : '笔数不一致';
});

function formatAmount(value?: number) {
  if (value == null) {
    return '-';
  }
  return formatMoneyDisplay(value);
}

async function handleCheck() {
  if (!formModel.checkDate) {
    ElMessage.warning('请选择核对日期');
    return;
  }
  result.value = await runCheck({
    checkDate: formModel.checkDate,
    dateMode: formModel.dateMode,
    issueKind: formModel.issueKind || undefined,
  });
}

function handleReset() {
  formModel.checkDate = '';
  formModel.dateMode = 'BUS_DATE';
  formModel.issueKind = '';
  result.value = null;
}
</script>

<template>
  <Page auto-content-height>
    <ElForm :model="formModel" label-width="100px" class="max-w-3xl">
      <ElFormItem label="核对日期" required>
        <ElDatePicker
          v-model="formModel.checkDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="选择日期"
          class="w-full"
        />
      </ElFormItem>
      <ElFormItem label="日期维度">
        <ElSelect v-model="formModel.dateMode" class="w-full">
          <ElOption label="业务日期" value="BUS_DATE" />
          <ElOption label="开票日期" value="IVC_DATE" />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="业务线">
        <ElSelect v-model="formModel.issueKind" clearable placeholder="全部" class="w-full">
          <ElOption label="挂号" value="REG" />
          <ElOption label="门诊" value="AMB" />
          <ElOption label="住院" value="IMP" />
        </ElSelect>
      </ElFormItem>
      <ElFormItem>
        <ElButton type="primary" :loading="loading" @click="handleCheck">开始核对</ElButton>
        <ElButton @click="handleReset">重置</ElButton>
      </ElFormItem>
    </ElForm>

    <div v-if="result" class="mt-6 space-y-4">
      <ElAlert
        :title="`核对结果：${matchLabel}`"
        :type="
          matchTagType === 'success' ? 'success' : matchTagType === 'danger' ? 'error' : 'warning'
        "
        show-icon
        :closable="false"
      >
        <template v-if="result.bossSuccess">
          本地 {{ result.localIssuedCount ?? 0 }} 笔，博思 {{ result.bossTotalCount ?? 0 }} 笔，差异
          {{ result.countDiff ?? 0 }} 笔。
        </template>
        <template v-else>
          博思返回 {{ result.bossResultCode || '未知' }}，请检查网络与 placeCode 配置。
        </template>
      </ElAlert>

      <div class="grid gap-4 md:grid-cols-2">
        <ElDescriptions title="本地（ZXPlat）" :column="1" border size="small">
          <ElDescriptionsItem label="已开具笔数">
            {{ result.localIssuedCount ?? 0 }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="已开具金额">
            {{ formatAmount(result.localIssuedAmount) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="日期维度">
            {{ result.dateMode === 'IVC_DATE' ? '开票日期' : '业务日期' }}
          </ElDescriptionsItem>
        </ElDescriptions>

        <ElDescriptions title="博思平台" :column="1" border size="small">
          <ElDescriptionsItem label="总笔数">
            {{ result.bossTotalCount ?? 0 }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="总金额">
            {{ formatAmount(result.bossTotalAmount) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="查询状态">
            <ElTag :type="result.bossSuccess ? 'success' : 'danger'" size="small">
              {{ result.bossSuccess ? '成功' : result.bossResultCode || '失败' }}
            </ElTag>
          </ElDescriptionsItem>
        </ElDescriptions>
      </div>
    </div>
  </Page>
</template>
