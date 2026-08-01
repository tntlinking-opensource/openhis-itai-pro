<script setup lang="ts">
import type { DeptSummarySectionDto, MasterPendingRowDto } from '#/api/peis/master-review';

import { onMounted, reactive, ref } from 'vue';

import {
  httpCompleteMasterReview,
  httpLoadMasterSummary,
  httpSaveMasterDraft,
  httpSearchPendingMaster,
} from '#/api/peis/master-review';
import SectionTitle from '#/components/layouts/section-title.vue';
import StationTriPaneLayout from '#/components/layouts/station-tri-pane-layout.vue';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { getDateRangeQuick } from '#/utils/format/date-range-quick';
import {
  ElButton,
  ElDatePicker,
  ElInput,
  ElMessage,
  ElPagination,
  ElTable,
  ElTableColumn,
} from 'element-plus';

defineOptions({ name: 'PeisMasterReviewDesk' });

const keyword = ref('');
const dateRange = ref<[string, string]>(
  getDateRangeQuick({ strategy: 'calendar-day' }) as [string, string],
);
const queuePager = reactive({ pageNo: 1, pageSize: 20 });
const queueRecords = ref<MasterPendingRowDto[]>([]);
const queueTotal = ref(0);
const queueLoading = ref(false);

const selectedRow = ref<MasterPendingRowDto | null>(null);
const summaryLoading = ref(false);
const deptSections = ref<DeptSummarySectionDto[]>([]);
const reportStatusEnum = ref('');
const summaryText = ref('');
const conclusionText = ref('');
const savingDraft = ref(false);
const completing = ref(false);

async function loadQueue() {
  queueLoading.value = true;
  try {
    const res = await httpSearchPendingMaster({
      pageNo: queuePager.pageNo,
      pageSize: queuePager.pageSize,
      keyword: keyword.value.trim() || undefined,
      startDate: dateRange.value?.[0],
      endDate: dateRange.value?.[1],
    });
    const parsed = parsePagedRowsFromApiData(res);
    queueRecords.value = parsed.content as MasterPendingRowDto[];
    queueTotal.value = parsed.totalCount;
  } finally {
    queueLoading.value = false;
  }
}

async function handleSelectRow(row: MasterPendingRowDto) {
  selectedRow.value = row;
  summaryLoading.value = true;
  try {
    const res = await httpLoadMasterSummary({ encounterId: row.encounterId });
    deptSections.value = res.deptSections ?? [];
    reportStatusEnum.value = res.reportStatusEnum ?? '';
    summaryText.value = res.summaryText ?? '';
    conclusionText.value = res.conclusionText ?? '';
  } finally {
    summaryLoading.value = false;
  }
}

async function handleSaveDraft() {
  if (!selectedRow.value) {
    return;
  }
  if (!conclusionText.value.trim()) {
    ElMessage.warning('请填写总检结论');
    return;
  }
  savingDraft.value = true;
  try {
    const res = await httpSaveMasterDraft({
      encounterId: selectedRow.value.encounterId,
      summaryText: summaryText.value.trim() || undefined,
      conclusionText: conclusionText.value.trim(),
    });
    reportStatusEnum.value = res.statusEnum ?? reportStatusEnum.value;
    ElMessage.success('总检综述已保存');
  } finally {
    savingDraft.value = false;
  }
}

async function handleComplete() {
  if (!selectedRow.value) {
    return;
  }
  if (!conclusionText.value.trim()) {
    ElMessage.warning('请先填写并保存总检结论');
    return;
  }
  completing.value = true;
  try {
    await httpSaveMasterDraft({
      encounterId: selectedRow.value.encounterId,
      summaryText: summaryText.value.trim() || undefined,
      conclusionText: conclusionText.value.trim(),
    });
    await httpCompleteMasterReview({
      encounterId: selectedRow.value.encounterId,
    });
    ElMessage.success('总检已完成');
    selectedRow.value = null;
    deptSections.value = [];
    summaryText.value = '';
    conclusionText.value = '';
    reportStatusEnum.value = '';
    await loadQueue();
  } finally {
    completing.value = false;
  }
}

onMounted(() => {
  loadQueue();
});
</script>

<template>
  <StationTriPaneLayout>
    <template #left>
      <SectionTitle title="待总检列表" />
      <div class="mb-3 flex flex-col gap-2">
        <ElDatePicker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          @change="loadQueue"
        />
        <ElInput v-model="keyword" placeholder="姓名/流水号" clearable @keyup.enter="loadQueue" />
        <ElButton type="primary" :loading="queueLoading" @click="loadQueue"> 查询 </ElButton>
      </div>
      <ElTable
        v-loading="queueLoading"
        :data="queueRecords"
        highlight-current-row
        height="520"
        @row-click="handleSelectRow"
      >
        <ElTableColumn prop="patientName" label="姓名" min-width="90" />
        <ElTableColumn prop="busNo" label="流水号" min-width="110" />
        <ElTableColumn prop="deptCompleteRatio" label="分科完成" width="90" />
        <ElTableColumn prop="checkInTime" label="到检时间" min-width="150" />
      </ElTable>
      <ElPagination
        v-model:current-page="queuePager.pageNo"
        v-model:page-size="queuePager.pageSize"
        class="mt-3 justify-end"
        layout="total, prev, pager, next"
        :total="queueTotal"
        @current-change="loadQueue"
        @size-change="loadQueue"
      />
    </template>

    <template #center>
      <SectionTitle title="分科结果汇总" />
      <div v-if="!selectedRow" class="text-gray-500">请从左侧选择待总检就诊</div>
      <div v-else v-loading="summaryLoading" class="flex flex-col gap-3">
        <div class="text-sm text-gray-600">
          {{ selectedRow.patientName }} · {{ selectedRow.busNo }} · 完成
          {{ selectedRow.deptCompleteRatio }}
          <span v-if="reportStatusEnum"> · 报告态 {{ reportStatusEnum }}</span>
        </div>
        <div
          v-for="section in deptSections"
          :key="String(section.serviceRequestId ?? 'orphan')"
          class="border-b border-gray-100 pb-3"
        >
          <div class="mb-1 text-sm font-medium">
            SR {{ section.serviceRequestId ?? '未挂申请' }}
          </div>
          <ElTable :data="section.observations" size="small" max-height="180">
            <ElTableColumn prop="categoryCode" label="分类" width="120" />
            <ElTableColumn prop="valueString" label="结果" min-width="140" />
            <ElTableColumn prop="interpretationCode" label="解读" width="80" />
            <ElTableColumn prop="statusEnum" label="状态" width="90" />
          </ElTable>
        </div>
      </div>
    </template>

    <template #right>
      <SectionTitle title="总检综述" />
      <div v-if="!selectedRow" class="text-gray-500">请选择就诊后录入</div>
      <div v-else class="flex flex-col gap-3">
        <ElInput v-model="summaryText" type="textarea" :rows="4" placeholder="综述（可选）" />
        <ElInput
          v-model="conclusionText"
          type="textarea"
          :rows="6"
          placeholder="总检结论（必填其一）"
        />
        <div class="flex gap-2">
          <ElButton :loading="savingDraft" @click="handleSaveDraft"> 保存草稿 </ElButton>
          <ElButton type="primary" :loading="completing" @click="handleComplete">
            总检完成
          </ElButton>
        </div>
      </div>
    </template>
  </StationTriPaneLayout>
</template>
