<script setup lang="ts">
import type { GrantRowDto } from '#/api/peis/report-grant';

import { onMounted, reactive, ref } from 'vue';

import { httpRegisterGrant, httpSearchPendingGrant, httpSignGrant } from '#/api/peis/report-grant';
import SectionTitle from '#/components/layouts/section-title.vue';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { ElButton, ElInput, ElMessage, ElPagination, ElTable, ElTableColumn } from 'element-plus';

defineOptions({ name: 'PeisReportGrant' });

const grantStatusCode = ref('pending');
const pager = reactive({ pageNo: 1, pageSize: 20 });
const records = ref<GrantRowDto[]>([]);
const total = ref(0);
const loading = ref(false);
const recipientName = ref('');
const acting = ref(false);

/** 加载待发放列表 */
async function loadList() {
  loading.value = true;
  try {
    const res = await httpSearchPendingGrant({
      pageNo: pager.pageNo,
      pageSize: pager.pageSize,
      grantStatusCode: grantStatusCode.value || 'pending',
    });
    const parsed = parsePagedRowsFromApiData(res);
    records.value = parsed.content as GrantRowDto[];
    total.value = parsed.totalCount;
  } finally {
    loading.value = false;
  }
}

/** 登记发放 */
async function handleRegister(row: GrantRowDto) {
  if (!row.diagnosticReportId) {
    return;
  }
  acting.value = true;
  try {
    await httpRegisterGrant({ diagnosticReportId: row.diagnosticReportId });
    ElMessage.success('发放已登记');
    await loadList();
  } finally {
    acting.value = false;
  }
}

/** 签收 */
async function handleSign(row: GrantRowDto) {
  if (!row.diagnosticReportId) {
    return;
  }
  acting.value = true;
  try {
    await httpSignGrant({
      diagnosticReportId: row.diagnosticReportId,
      recipientName: recipientName.value.trim() || undefined,
    });
    ElMessage.success('已签收');
    await loadList();
  } finally {
    acting.value = false;
  }
}

onMounted(() => {
  loadList();
});
</script>

<template>
  <div class="p-4">
    <SectionTitle title="报告发放" />
    <div class="mb-4 flex flex-wrap items-center gap-2">
      <ElInput v-model="recipientName" class="w-40" placeholder="签收人（可选）" clearable />
      <ElButton type="primary" :loading="loading" @click="loadList"> 刷新 </ElButton>
    </div>
    <ElTable v-loading="loading" :data="records" stripe height="560">
      <ElTableColumn prop="patientName" label="姓名" min-width="90" />
      <ElTableColumn prop="busNo" label="流水号" min-width="120" />
      <ElTableColumn prop="grantStatusCode" label="发放状态" width="100" />
      <ElTableColumn prop="reportFinalTime" label="定稿时间" min-width="160" />
      <ElTableColumn label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <ElButton
            v-if="row.grantStatusCode === 'pending'"
            link
            type="primary"
            :loading="acting"
            @click="handleRegister(row)"
          >
            登记
          </ElButton>
          <ElButton
            v-if="row.grantStatusCode === 'pending' || row.grantStatusCode === 'printed'"
            link
            type="primary"
            :loading="acting"
            @click="handleSign(row)"
          >
            签收
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
  </div>
</template>
