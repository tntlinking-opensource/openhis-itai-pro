<script setup lang="ts">
import type { RecheckRowDto } from '#/api/peis/recheck';

import { onMounted, reactive, ref } from 'vue';

import {
  httpCompleteRecheck,
  httpConfirmRecheckItems,
  httpCreateRecheck,
  httpSearchRecheck,
} from '#/api/peis/recheck';
import SectionTitle from '#/components/layouts/section-title.vue';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import {
  ElButton,
  ElDialog,
  ElInput,
  ElMessage,
  ElPagination,
  ElTable,
  ElTableColumn,
} from 'element-plus';

defineOptions({ name: 'PeisRecheckManage' });

const pager = reactive({ pageNo: 1, pageSize: 20 });
const records = ref<RecheckRowDto[]>([]);
const total = ref(0);
const loading = ref(false);

const createVisible = ref(false);
const createEncounterId = ref('');
const createReason = ref('');
const creating = ref(false);

const confirmVisible = ref(false);
const confirmTaskId = ref('');
const confirmActivityIds = ref('');
const confirming = ref(false);

const completeVisible = ref(false);
const completeTaskId = ref('');
const completeDrId = ref('');
const completing = ref(false);

/** 加载复查列表 */
async function loadList() {
  loading.value = true;
  try {
    const res = await httpSearchRecheck({
      pageNo: pager.pageNo,
      pageSize: pager.pageSize,
    });
    const parsed = parsePagedRowsFromApiData(res);
    records.value = parsed.content as RecheckRowDto[];
    total.value = parsed.totalCount;
  } finally {
    loading.value = false;
  }
}

/** 提交复查申请 */
async function submitCreate() {
  if (!createEncounterId.value.trim() || !createReason.value.trim()) {
    ElMessage.warning('请填写就诊 ID 与复查原因');
    return;
  }
  creating.value = true;
  try {
    await httpCreateRecheck({
      encounterId: createEncounterId.value.trim(),
      reason: createReason.value.trim(),
    });
    ElMessage.success('复查已申请');
    createVisible.value = false;
    createEncounterId.value = '';
    createReason.value = '';
    await loadList();
  } finally {
    creating.value = false;
  }
}

/** 确认复查项目 */
async function submitConfirm() {
  if (!confirmTaskId.value.trim() || !confirmActivityIds.value.trim()) {
    ElMessage.warning('请填写任务 ID 与项目 ID 列表');
    return;
  }
  const activityIds = confirmActivityIds.value
    .split(/[,，\s]+/)
    .map((s) => s.trim())
    .filter(Boolean);
  confirming.value = true;
  try {
    await httpConfirmRecheckItems({
      taskId: confirmTaskId.value.trim(),
      activityIds,
    });
    ElMessage.success('复查项目已确认');
    confirmVisible.value = false;
    await loadList();
  } finally {
    confirming.value = false;
  }
}

/** 完成复查 */
async function submitComplete() {
  if (!completeTaskId.value.trim() || !completeDrId.value.trim()) {
    ElMessage.warning('请填写任务 ID 与复查报告 ID');
    return;
  }
  completing.value = true;
  try {
    await httpCompleteRecheck({
      taskId: completeTaskId.value.trim(),
      diagnosticReportId: completeDrId.value.trim(),
    });
    ElMessage.success('复查已完成');
    completeVisible.value = false;
    await loadList();
  } finally {
    completing.value = false;
  }
}

/** 从行打开项目确认 */
function openConfirmFromRow(row: RecheckRowDto) {
  confirmTaskId.value = String(row.taskId ?? '');
  confirmVisible.value = true;
}

/** 从行打开完成复查 */
function openCompleteFromRow(row: RecheckRowDto) {
  completeTaskId.value = String(row.taskId ?? '');
  completeVisible.value = true;
}

onMounted(() => {
  loadList();
});
</script>

<template>
  <div class="p-4">
    <SectionTitle title="复查管理" />
    <div class="mb-4 flex gap-2">
      <ElButton type="primary" @click="createVisible = true"> 复查申请 </ElButton>
      <ElButton @click="confirmVisible = true"> 确认复查项目 </ElButton>
      <ElButton @click="completeVisible = true"> 完成复查 </ElButton>
      <ElButton :loading="loading" @click="loadList"> 刷新 </ElButton>
    </div>
    <ElTable v-loading="loading" :data="records" stripe height="520">
      <ElTableColumn prop="busNo" label="流水号" min-width="120" />
      <ElTableColumn prop="statusCode" label="状态" width="110" />
      <ElTableColumn prop="progressLabel" label="进度" width="100" />
      <ElTableColumn prop="reason" label="原因" min-width="160" show-overflow-tooltip />
      <ElTableColumn label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <ElButton
            v-if="row.statusCode === 'requested'"
            link
            type="primary"
            @click="openConfirmFromRow(row)"
          >
            确认项目
          </ElButton>
          <ElButton
            v-if="row.statusCode === 'in-progress'"
            link
            type="primary"
            @click="openCompleteFromRow(row)"
          >
            完成
          </ElButton>
        </template>
      </ElTableColumn>
    </ElTable>
    <ElPagination
      v-model:current-page="pager.pageNo"
      v-model:page-size="pager.pageSize"
      class="mt-3 justify-end"
      layout="total, prev, pager, next"
      :total="total"
      @current-change="loadList"
      @size-change="loadList"
    />

    <ElDialog v-model="createVisible" title="复查申请" width="480px">
      <ElInput v-model="createEncounterId" class="mb-3" placeholder="原体检就诊 ID" />
      <ElInput v-model="createReason" type="textarea" :rows="3" placeholder="复查原因" />
      <template #footer>
        <ElButton @click="createVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="creating" @click="submitCreate">提交</ElButton>
      </template>
    </ElDialog>

    <ElDialog v-model="confirmVisible" title="确认复查项目" width="480px">
      <ElInput v-model="confirmTaskId" class="mb-3" placeholder="复查任务 ID" />
      <ElInput
        v-model="confirmActivityIds"
        type="textarea"
        :rows="2"
        placeholder="项目 AD ID，逗号分隔"
      />
      <template #footer>
        <ElButton @click="confirmVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="confirming" @click="submitConfirm">确认</ElButton>
      </template>
    </ElDialog>

    <ElDialog v-model="completeVisible" title="完成复查" width="480px">
      <ElInput v-model="completeTaskId" class="mb-3" placeholder="复查任务 ID" />
      <ElInput v-model="completeDrId" placeholder="复查总检报告 ID（须 final）" />
      <template #footer>
        <ElButton @click="completeVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="completing" @click="submitComplete">完成</ElButton>
      </template>
    </ElDialog>
  </div>
</template>
