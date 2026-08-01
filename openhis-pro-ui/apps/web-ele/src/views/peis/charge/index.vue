<script setup lang="ts">
import type {
  LoadPhysicalExamChargeItemsResponse,
  PhysicalExamChargeItemRowDto,
  PhysicalExamChargeQueueRowDto,
  PreSettlePhysicalExamChargesResponse,
} from '#/api/peis/physical-exam-charge';
import type { OptionDto } from '#/api/outpatient-registration';

import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { RotateCw } from '@vben/icons';

import { useDebounceFn } from '@vueuse/core';
import dayjs from 'dayjs';
import {
  ElButton,
  ElCard,
  ElDatePicker,
  ElDialog,
  ElMessage,
  ElPagination,
  ElRadioButton,
  ElRadioGroup,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  httpConfirmPhysicalExamChargeSettlement,
  httpLoadPhysicalExamChargeItems,
  httpLoadPhysicalExamChargePageInit,
  httpPagePhysicalExamCompleteQueue,
  httpPagePhysicalExamWaitingQueue,
  httpPreSettlePhysicalExamCharges,
} from '#/api/peis/physical-exam-charge';
import SectionTitle from '#/components/layouts/section-title.vue';
import StationTriPaneLayout from '#/components/layouts/station-tri-pane-layout.vue';
import SelectFuzzyInput from '#/components/select-fuzzy-input.vue';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { formatMoneyDisplay } from '#/utils/format/money-scale';
import { optionDtosToPayChannelOptions } from '#/utils/payment/payment-channel-options';

defineOptions({
  name: 'PeisPhysicalExamCharge',
});

const QUEUE_TAB = {
  WAITING: 'WAITING',
  COMPLETE: 'COMPLETE',
} as const;

const SETTLE_OUTCOME_COMPLETED = '1';

const route = useRoute();
const queueTabCode = ref<string>(QUEUE_TAB.WAITING);
const keyword = ref('');
const dateRange = ref<[string, string] | null>(null);
const queuePager = reactive({ pageNo: 1, pageSize: 20 });
const queueRecords = ref<PhysicalExamChargeQueueRowDto[]>([]);
const queueTotal = ref(0);
const queueLoading = ref(false);

const selectedEncounterId = ref('');
const chargeDetail = ref<LoadPhysicalExamChargeItemsResponse | null>(null);
const detailLoading = ref(false);
const selectedChargeRows = ref<PhysicalExamChargeItemRowDto[]>([]);

const paymentChannelOptions = ref<{ label: string; value: string }[]>([]);
const settlementVisible = ref(false);
const settlementLoading = ref(false);
const preSettleSnapshot = ref<PreSettlePhysicalExamChargesResponse | null>(null);
const lockedChargeItemIds = ref<string[]>([]);
const payChannelCode = ref('WECHAT');

const plannedChargeItems = computed(() => {
  const items = chargeDetail.value?.items ?? [];
  return items.filter((row) => row.status === 'PLANNED');
});

const selectedPlannedRows = computed(() =>
  selectedChargeRows.value.filter((row) => row.status === 'PLANNED'),
);

const canSettle = computed(
  () => selectedPlannedRows.value.length > 0 && selectedEncounterId.value !== '',
);

function formatMoney(value: null | number | string | undefined) {
  return formatMoneyDisplay(value);
}

function resolveQueueApi() {
  if (queueTabCode.value === QUEUE_TAB.COMPLETE) {
    return httpPagePhysicalExamCompleteQueue;
  }
  return httpPagePhysicalExamWaitingQueue;
}

async function loadQueue() {
  queueLoading.value = true;
  try {
    const body = {
      pageNo: queuePager.pageNo,
      pageSize: queuePager.pageSize,
      keyword: keyword.value.trim(),
      startDate: dateRange.value?.[0] ?? undefined,
      endDate: dateRange.value?.[1] ?? undefined,
    };
    const data = await resolveQueueApi()(body);
    const parsed = parsePagedRowsFromApiData<PhysicalExamChargeQueueRowDto>(data);
    queueRecords.value = parsed.records;
    queueTotal.value = parsed.total;
  } catch (error) {
    ElMessage.error('查询体检收费队列失败');
    console.error(error);
  } finally {
    queueLoading.value = false;
  }
}

const debouncedLoadQueue = useDebounceFn(loadQueue, 300);

async function loadDetail(encounterId: string, patientId?: string) {
  if (!encounterId) {
    return;
  }
  selectedEncounterId.value = encounterId;
  detailLoading.value = true;
  selectedChargeRows.value = [];
  try {
    chargeDetail.value = await httpLoadPhysicalExamChargeItems({
      encounterId,
      patientId,
    });
  } catch (error) {
    ElMessage.error('加载收费明细失败');
    console.error(error);
  } finally {
    detailLoading.value = false;
  }
}

function onQueueRowClick(row: PhysicalExamChargeQueueRowDto) {
  loadDetail(row.encounterId, row.patientId);
}

function onSelectionChange(rows: PhysicalExamChargeItemRowDto[]) {
  selectedChargeRows.value = rows;
}

async function openSettlementDialog() {
  if (!canSettle.value) {
    ElMessage.warning('请选择待收费明细');
    return;
  }
  settlementLoading.value = true;
  try {
    const ids = selectedPlannedRows.value.map((row) => row.chargeItemId);
    lockedChargeItemIds.value = ids;
    preSettleSnapshot.value = await httpPreSettlePhysicalExamCharges({
      encounterId: selectedEncounterId.value,
      chargeItemIds: ids,
    });
    settlementVisible.value = true;
  } catch (error) {
    ElMessage.error('预结算失败');
    console.error(error);
  } finally {
    settlementLoading.value = false;
  }
}

async function confirmSettlement() {
  if (!preSettleSnapshot.value?.paymentDraftId) {
    return;
  }
  settlementLoading.value = true;
  try {
    const displayAmount = preSettleSnapshot.value.displayAmount ?? 0;
    await httpConfirmPhysicalExamChargeSettlement({
      paymentDraftId: preSettleSnapshot.value.paymentDraftId,
      encounterId: selectedEncounterId.value,
      chargeItemIds: lockedChargeItemIds.value,
      outcomeEnum: SETTLE_OUTCOME_COMPLETED,
      billDate: dayjs().format('YYYY-MM-DD HH:mm:ss'),
      paymentDetails: [
        {
          payChannelCode: payChannelCode.value,
          amount: displayAmount,
        },
      ],
    });
    ElMessage.success('收费成功');
    settlementVisible.value = false;
    preSettleSnapshot.value = null;
    await loadQueue();
    await loadDetail(selectedEncounterId.value, chargeDetail.value?.patientId);
  } catch (error) {
    ElMessage.error('正式收费失败，请勿重复提交');
    console.error(error);
  } finally {
    settlementLoading.value = false;
  }
}

async function initPage() {
  try {
    const init = await httpLoadPhysicalExamChargePageInit();
    paymentChannelOptions.value = optionDtosToPayChannelOptions(
      init.paymentChannelOptions as OptionDto[] | undefined,
    );
    if (paymentChannelOptions.value.length > 0) {
      payChannelCode.value = paymentChannelOptions.value[0]?.value ?? 'WECHAT';
    }
  } catch (error) {
    console.error(error);
  }
}

watch(queueTabCode, () => {
  queuePager.pageNo = 1;
  loadQueue();
});

watch([keyword, dateRange], () => {
  queuePager.pageNo = 1;
  debouncedLoadQueue();
});

onMounted(async () => {
  if (!dateRange.value) {
    const today = dayjs().format('YYYY-MM-DD');
    dateRange.value = [today, today];
  }
  await initPage();
  await loadQueue();
  const encounterId = String(route.query.encounterId ?? '').trim();
  const patientId = String(route.query.patientId ?? '').trim();
  if (encounterId) {
    await loadDetail(encounterId, patientId || undefined);
  }
});
</script>

<template>
  <StationTriPaneLayout>
    <template #toolbar>
      <div class="flex flex-wrap items-center gap-3">
        <ElRadioGroup v-model="queueTabCode">
          <ElRadioButton :label="QUEUE_TAB.WAITING">待收费</ElRadioButton>
          <ElRadioButton :label="QUEUE_TAB.COMPLETE">已收费</ElRadioButton>
        </ElRadioGroup>
        <ElDatePicker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
        />
        <SelectFuzzyInput v-model="keyword" placeholder="流水号/姓名" clearable />
        <ElButton :icon="RotateCw" @click="loadQueue">刷新</ElButton>
      </div>
    </template>

    <template #left>
      <ElCard shadow="never" class="h-full" v-loading="queueLoading">
        <SectionTitle title="体检单队列" />
        <ElTable
          :data="queueRecords"
          highlight-current-row
          height="100%"
          @row-click="onQueueRowClick"
        >
          <ElTableColumn prop="busNo" label="流水号" min-width="120" />
          <ElTableColumn prop="patientName" label="姓名" min-width="90" />
          <ElTableColumn prop="packageName" label="套餐" min-width="120" show-overflow-tooltip />
          <ElTableColumn label="待收" min-width="80">
            <template #default="{ row }">
              {{ formatMoney(row.plannedAmount) }}
            </template>
          </ElTableColumn>
          <ElTableColumn label="已收" min-width="80">
            <template #default="{ row }">
              {{ formatMoney(row.settledAmount) }}
            </template>
          </ElTableColumn>
        </ElTable>
        <div class="mt-3 flex justify-end">
          <ElPagination
            v-model:current-page="queuePager.pageNo"
            v-model:page-size="queuePager.pageSize"
            :total="queueTotal"
            layout="total, prev, pager, next"
            @current-change="loadQueue"
            @size-change="loadQueue"
          />
        </div>
      </ElCard>
    </template>

    <template #right>
      <ElCard shadow="never" class="flex h-full flex-col" v-loading="detailLoading">
        <SectionTitle title="收费明细" />
        <div v-if="chargeDetail" class="mb-3 text-sm text-gray-600">
          <span class="mr-4">流水号：{{ chargeDetail.busNo ?? '—' }}</span>
          <span class="mr-4">套餐：{{ chargeDetail.packageName ?? '—' }}</span>
          <span>待收合计：{{ formatMoney(chargeDetail.plannedTotalAmount) }}</span>
        </div>
        <ElTable
          :data="chargeDetail?.items ?? []"
          height="100%"
          @selection-change="onSelectionChange"
        >
          <ElTableColumn
            type="selection"
            width="48"
            :selectable="(row) => row.status === 'PLANNED'"
          />
          <ElTableColumn prop="itemLabel" label="项目" min-width="160" />
          <ElTableColumn label="单价" min-width="90">
            <template #default="{ row }">{{ formatMoney(row.unitPrice) }}</template>
          </ElTableColumn>
          <ElTableColumn label="数量" prop="quantity" min-width="70" />
          <ElTableColumn label="金额" min-width="90">
            <template #default="{ row }">{{ formatMoney(row.totalPrice) }}</template>
          </ElTableColumn>
          <ElTableColumn label="状态" min-width="90">
            <template #default="{ row }">
              <ElTag :type="row.status === 'SETTLED' ? 'success' : 'warning'">
                {{ row.status === 'SETTLED' ? '已结算' : '待收费' }}
              </ElTag>
            </template>
          </ElTableColumn>
        </ElTable>
        <div class="mt-4 flex justify-end gap-2">
          <ElButton
            type="primary"
            :disabled="!canSettle"
            :loading="settlementLoading"
            @click="openSettlementDialog"
          >
            预结算并收费
          </ElButton>
        </div>
      </ElCard>
    </template>
  </StationTriPaneLayout>

  <ElDialog v-model="settlementVisible" title="确认收费（自费）" width="520px">
    <div class="space-y-3">
      <div>
        应付金额：
        <strong>{{ formatMoney(preSettleSnapshot?.displayAmount) }}</strong>
      </div>
      <div class="flex items-center gap-2">
        <span>支付渠道</span>
        <ElRadioGroup v-model="payChannelCode">
          <ElRadioButton v-for="opt in paymentChannelOptions" :key="opt.value" :label="opt.value">
            {{ opt.label }}
          </ElRadioButton>
        </ElRadioGroup>
      </div>
      <ElTable :data="preSettleSnapshot?.previews ?? []" size="small">
        <ElTableColumn prop="itemLabel" label="项目" />
        <ElTableColumn label="金额">
          <template #default="{ row }">{{ formatMoney(row.totalPrice) }}</template>
        </ElTableColumn>
      </ElTable>
    </div>
    <template #footer>
      <ElButton @click="settlementVisible = false">取消</ElButton>
      <ElButton type="primary" :loading="settlementLoading" @click="confirmSettlement">
        确认收费
      </ElButton>
    </template>
  </ElDialog>
</template>
