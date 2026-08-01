<script setup lang="ts">
import type {
  ObservationRowDto,
  PendingDeskRowDto,
  SaveObservationRequest,
} from '#/api/peis/dept-desk';
import type { PeisCommonResultDto } from '#/api/peis/diagnosis-config';

import { computed, onMounted, reactive, ref } from 'vue';

import { useUserStore } from '@vben/stores';

import { searchCommonResultApi } from '#/api/peis/diagnosis-config';
import {
  httpApplyCommonResult,
  httpLoadObservationsByServiceRequest,
  httpSaveDeptConclusion,
  httpSaveObservation,
  httpSearchPendingDeptDesk,
} from '#/api/peis/dept-desk';
import SectionTitle from '#/components/layouts/section-title.vue';
import StationTriPaneLayout from '#/components/layouts/station-tri-pane-layout.vue';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { getDateRangeQuick } from '#/utils/format/date-range-quick';
import {
  ElButton,
  ElDatePicker,
  ElInput,
  ElMessage,
  ElOption,
  ElPagination,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

defineOptions({ name: 'PeisDeptExamDesk' });

const userStore = useUserStore();

const keyword = ref('');
const dateRange = ref<[string, string]>(
  getDateRangeQuick({ strategy: 'calendar-day' }) as [string, string],
);
const deptOrgIdInput = ref('');
const queuePager = reactive({ pageNo: 1, pageSize: 20 });
const queueRecords = ref<PendingDeskRowDto[]>([]);
const queueTotal = ref(0);
const queueLoading = ref(false);

const selectedRow = ref<PendingDeskRowDto | null>(null);
const observationRows = ref<ObservationRowDto[]>([]);
const observationLoading = ref(false);

const editDefinitionId = ref('');
const editValueString = ref('');
const editInterpretation = ref('');
const saving = ref(false);

const commonResults = ref<PeisCommonResultDto[]>([]);
const selectedCommonResultId = ref('');

const conclusionDefinitionId = ref('');
const conclusionText = ref('');
const savingConclusion = ref(false);

const defaultDeptOrgId = computed(() => {
  const fromUser = userStore.userInfo?.organizationId;
  if (fromUser != null && String(fromUser).trim().length > 0) {
    return String(fromUser);
  }
  return deptOrgIdInput.value.trim();
});

const execStatusLabel: Record<string, string> = {
  pending: '待检',
  checked: '已检',
  abandoned: '弃检',
  deferred: '延检',
};

async function loadQueue() {
  queueLoading.value = true;
  try {
    const res = await httpSearchPendingDeptDesk({
      pageNo: queuePager.pageNo,
      pageSize: queuePager.pageSize,
      keyword: keyword.value.trim() || undefined,
      startDate: dateRange.value?.[0],
      endDate: dateRange.value?.[1],
      deptOrgId: defaultDeptOrgId.value || undefined,
    });
    const parsed = parsePagedRowsFromApiData(res);
    queueRecords.value = parsed.content as PendingDeskRowDto[];
    queueTotal.value = parsed.totalCount;
  } finally {
    queueLoading.value = false;
  }
}

async function handleSelectRow(row: PendingDeskRowDto) {
  selectedRow.value = row;
  editDefinitionId.value = '';
  editValueString.value = '';
  editInterpretation.value = '';
  selectedCommonResultId.value = '';
  commonResults.value = [];
  observationLoading.value = true;
  try {
    const res = await httpLoadObservationsByServiceRequest({
      serviceRequestId: row.serviceRequestId,
    });
    observationRows.value = res.observations ?? [];
  } finally {
    observationLoading.value = false;
  }
}

async function loadCommonResultsForDefinition(definitionId: string) {
  if (!definitionId) {
    commonResults.value = [];
    return;
  }
  const res = await searchCommonResultApi({
    observationDefinitionId: definitionId,
    pageNo: 1,
    pageSize: 100,
  });
  commonResults.value = res.records ?? [];
}

function buildSavePayload(finalizeFlag: boolean): SaveObservationRequest | null {
  if (!selectedRow.value || !editDefinitionId.value) {
    ElMessage.warning('请选择待检项目并填写小项定义');
    return null;
  }
  return {
    serviceRequestId: selectedRow.value.serviceRequestId,
    definitionId: editDefinitionId.value,
    deptOrgId: defaultDeptOrgId.value || undefined,
    valueString: editValueString.value,
    interpretationCode: editInterpretation.value || undefined,
    finalizeFlag,
  };
}

async function handleSave(finalizeFlag: boolean) {
  const payload = buildSavePayload(finalizeFlag);
  if (!payload) {
    return;
  }
  saving.value = true;
  try {
    await httpSaveObservation(payload);
    ElMessage.success(finalizeFlag ? '已确认结果' : '已保存');
    await loadQueue();
    if (selectedRow.value) {
      await handleSelectRow(selectedRow.value);
    }
  } finally {
    saving.value = false;
  }
}

async function handleApplyCommonResult() {
  if (!selectedRow.value || !selectedCommonResultId.value) {
    ElMessage.warning('请选择常见结果模板');
    return;
  }
  saving.value = true;
  try {
    await httpApplyCommonResult({
      serviceRequestId: selectedRow.value.serviceRequestId,
      commonResultId: selectedCommonResultId.value,
      deptOrgId: defaultDeptOrgId.value || undefined,
      finalizeFlag: false,
    });
    ElMessage.success('已套用常见结果');
    if (selectedRow.value) {
      await handleSelectRow(selectedRow.value);
    }
  } finally {
    saving.value = false;
  }
}

async function handleSaveConclusion() {
  if (!selectedRow.value || !conclusionDefinitionId.value || !conclusionText.value.trim()) {
    ElMessage.warning('请填写分科结论定义与文本');
    return;
  }
  savingConclusion.value = true;
  try {
    await httpSaveDeptConclusion({
      serviceRequestId: selectedRow.value.serviceRequestId,
      definitionId: conclusionDefinitionId.value,
      deptOrgId: defaultDeptOrgId.value || undefined,
      valueString: conclusionText.value.trim(),
    });
    ElMessage.success('分科结论已保存');
  } finally {
    savingConclusion.value = false;
  }
}

function handleDefinitionChange(definitionId: string) {
  editDefinitionId.value = definitionId;
  loadCommonResultsForDefinition(definitionId);
}

onMounted(() => {
  loadQueue();
});
</script>

<template>
  <StationTriPaneLayout>
    <template #left>
      <SectionTitle title="待检列表" />
      <div class="mb-3 flex flex-col gap-2">
        <ElDatePicker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          @change="loadQueue"
        />
        <ElInput
          v-model="keyword"
          placeholder="姓名/流水号/项目"
          clearable
          @keyup.enter="loadQueue"
        />
        <ElInput
          v-if="!userStore.userInfo?.organizationId"
          v-model="deptOrgIdInput"
          placeholder="科室机构 ID（未登录机构时必填）"
        />
        <ElButton type="primary" :loading="queueLoading" @click="loadQueue">查询</ElButton>
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
        <ElTableColumn prop="activityDisplay" label="项目" min-width="120" />
        <ElTableColumn label="状态" width="80">
          <template #default="{ row }">
            <ElTag size="small">{{
              execStatusLabel[row.execStatusEnum] ?? row.execStatusEnum
            }}</ElTag>
          </template>
        </ElTableColumn>
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
      <SectionTitle title="小项录入" />
      <div v-if="!selectedRow" class="text-gray-500">请从左侧选择待检项目</div>
      <div v-else class="flex flex-col gap-3">
        <div class="text-sm text-gray-600">
          {{ selectedRow.patientName }} · {{ selectedRow.activityDisplay }} · SR
          {{ selectedRow.serviceRequestId }}
        </div>
        <ElTable
          v-loading="observationLoading"
          :data="observationRows"
          size="small"
          max-height="200"
        >
          <ElTableColumn prop="definitionDisplay" label="小项" min-width="120" />
          <ElTableColumn prop="valueString" label="结果" min-width="100" />
          <ElTableColumn prop="statusEnum" label="状态" width="80" />
        </ElTable>
        <ElInput
          v-model="editDefinitionId"
          placeholder="观察定义 ID（小项 OD）"
          @change="handleDefinitionChange(editDefinitionId)"
        />
        <ElSelect
          v-model="selectedCommonResultId"
          placeholder="常见结果套用"
          clearable
          class="w-full"
        >
          <ElOption
            v-for="item in commonResults"
            :key="item.id"
            :label="item.display"
            :value="String(item.id)"
          />
        </ElSelect>
        <ElButton v-if="selectedCommonResultId" size="small" @click="handleApplyCommonResult">
          套用所选常见结果
        </ElButton>
        <ElInput v-model="editValueString" type="textarea" :rows="3" placeholder="结果值" />
        <ElInput v-model="editInterpretation" placeholder="解读码（H/L/N 等）" />
        <div class="flex gap-2">
          <ElButton :loading="saving" @click="handleSave(false)">保存</ElButton>
          <ElButton type="primary" :loading="saving" @click="handleSave(true)">保存并确认</ElButton>
        </div>
        <SectionTitle title="分科结论" />
        <ElInput
          v-model="conclusionDefinitionId"
          placeholder="分科结论 OD ID（category=dept_conclusion）"
        />
        <ElInput v-model="conclusionText" type="textarea" :rows="3" placeholder="分科结论文本" />
        <ElButton type="success" :loading="savingConclusion" @click="handleSaveConclusion">
          保存分科结论
        </ElButton>
      </div>
    </template>
  </StationTriPaneLayout>
</template>
