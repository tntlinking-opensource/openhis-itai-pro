<script setup lang="ts">
import type { CaseRecordUploadSummaryDto, QueryUploadPageParams } from '#/api/mrms/types';

import { onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { formatDateTime } from '@vben/utils';

import { TableLayout } from '@vben-core/table-layout-ui';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
  ElTag,
} from 'element-plus';

import {
  previewAssembled4401Api,
  queryUploadLogsApi,
  queryUploadPageApi,
  upload4401Api,
} from '#/api/mrms';
import type { UploadLogDto } from '#/api/mrms/types';

defineOptions({ name: 'MrmsUploadWorkbench' });

const UPLOAD_STATUS_LABEL: Record<string, string> = {
  pending: '待上传',
  success: '成功',
  failed: '失败',
  skipped: '无需上传',
};

const tableData = ref<CaseRecordUploadSummaryDto[]>([]);
const total = ref(0);
const loading = ref(false);
const queryParams = reactive<QueryUploadPageParams>({
  pageNo: 1,
  pageSize: 20,
  statusCode: 'pending',
});

const uploadDialogVisible = ref(false);
const uploadRow = ref<CaseRecordUploadSummaryDto | null>(null);
const contractBusNo = ref('');

const logsDialogVisible = ref(false);
const uploadLogs = ref<UploadLogDto[]>([]);
const previewDialogVisible = ref(false);
const previewJson = ref('');

async function loadPage() {
  loading.value = true;
  try {
    const page = await queryUploadPageApi(queryParams);
    tableData.value = page.rows;
    total.value = page.total;
  } finally {
    loading.value = false;
  }
}

function openUpload(row: CaseRecordUploadSummaryDto) {
  uploadRow.value = row;
  contractBusNo.value = '';
  uploadDialogVisible.value = true;
}

async function submitUpload() {
  if (!uploadRow.value?.caseRecordId || !contractBusNo.value.trim()) {
    ElMessage.warning('请填写费用性质 contractBusNo');
    return;
  }
  const result = await upload4401Api({
    caseRecordId: uploadRow.value.caseRecordId,
    contractBusNo: contractBusNo.value.trim(),
  });
  ElMessage.success(
    `上传结果：${UPLOAD_STATUS_LABEL[result.statusCode ?? ''] ?? result.statusCode}`,
  );
  uploadDialogVisible.value = false;
  await loadPage();
}

async function showLogs(row: CaseRecordUploadSummaryDto) {
  if (!row.caseRecordId) return;
  uploadLogs.value = await queryUploadLogsApi({ caseRecordId: row.caseRecordId });
  logsDialogVisible.value = true;
}

async function handlePreview(row: CaseRecordUploadSummaryDto) {
  if (!row.caseRecordId || !contractBusNo.value.trim()) {
    ElMessage.warning('请先在上方输入 contractBusNo 用于预检');
    return;
  }
  const data = await previewAssembled4401Api({
    caseRecordId: row.caseRecordId,
    contractBusNo: contractBusNo.value.trim(),
  });
  previewJson.value = JSON.stringify(data, null, 2);
  previewDialogVisible.value = true;
}

onMounted(loadPage);
</script>

<template>
  <Page auto-content-height>
    <TableLayout
      v-model:page-no="queryParams.pageNo"
      v-model:page-size="queryParams.pageSize"
      :data="tableData"
      :loading="loading"
      :total="total"
      @page-change="loadPage"
    >
      <template #toolbar>
        <ElInput
          v-model="contractBusNo"
          placeholder="contractBusNo（上传/预检）"
          style="width: 220px"
        />
        <ElSelect
          v-model="queryParams.statusCode"
          clearable
          placeholder="上传状态"
          style="width: 140px"
          @change="loadPage"
        >
          <ElOption
            v-for="(label, code) in UPLOAD_STATUS_LABEL"
            :key="code"
            :label="label"
            :value="code"
          />
        </ElSelect>
        <ElInput
          v-model="queryParams.keyword"
          clearable
          placeholder="病案号/姓名"
          style="width: 180px"
          @keyup.enter="loadPage"
        />
        <ElButton type="primary" @click="loadPage">查询</ElButton>
      </template>
      <template #columns>
        <el-table-column label="病案号" prop="medcasno" width="120" />
        <el-table-column label="患者" prop="psnName" width="100" />
        <el-table-column label="出院" width="170">
          <template #default="{ row }">{{ formatDateTime(row.dscgTime) }}</template>
        </el-table-column>
        <el-table-column label="归档" width="170">
          <template #default="{ row }">{{ formatDateTime(row.archivedAt) }}</template>
        </el-table-column>
        <el-table-column label="上传状态" width="110">
          <template #default="{ row }">
            <ElTag>{{
              UPLOAD_STATUS_LABEL[row.chsUploadStatusCode ?? ''] ??
              (row.chsUploadStatusCode ? row.chsUploadStatusCode : '待上传')
            }}</ElTag>
          </template>
        </el-table-column>
        <el-table-column
          label="最近错误"
          prop="chsUploadError"
          min-width="180"
          show-overflow-tooltip
        />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <ElButton link type="primary" @click="openUpload(row)">上传</ElButton>
            <ElButton link @click="showLogs(row)">日志</ElButton>
            <ElButton link @click="handlePreview(row)">预检</ElButton>
          </template>
        </el-table-column>
      </template>
    </TableLayout>

    <ElDialog v-model="uploadDialogVisible" title="4401 上传" width="480px">
      <ElForm label-width="120px">
        <ElFormItem label="病案号">
          <span>{{ uploadRow?.medcasno }}</span>
        </ElFormItem>
        <ElFormItem label="contractBusNo">
          <ElInput v-model="contractBusNo" placeholder="fin_contract.bus_no" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="uploadDialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="submitUpload">上传</ElButton>
      </template>
    </ElDialog>

    <ElDialog v-model="logsDialogVisible" title="上传日志" width="720px">
      <el-table :data="uploadLogs" size="small">
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="状态" prop="statusCode" width="90" />
        <el-table-column label="infcode" prop="infcode" width="80" />
        <el-table-column label="错误" prop="errMsg" min-width="200" show-overflow-tooltip />
      </el-table>
    </ElDialog>

    <ElDialog v-model="previewDialogVisible" title="4401 预检 JSON" width="800px">
      <pre style="max-height: 480px; overflow: auto; font-size: 12px">{{ previewJson }}</pre>
    </ElDialog>
  </Page>
</template>
