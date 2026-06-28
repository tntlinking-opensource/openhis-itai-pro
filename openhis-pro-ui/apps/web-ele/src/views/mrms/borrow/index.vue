<script setup lang="ts">
import type { BorrowRequestItemDto, QueryBorrowPageParams } from '#/api/mrms/types';

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
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTag,
} from 'element-plus';

import {
  approveBorrowApi,
  markBorrowedApi,
  markReturnedApi,
  queryBorrowPageApi,
  rejectBorrowApi,
} from '#/api/mrms';

defineOptions({ name: 'MrmsBorrowWorkbench' });

const BORROW_STATUS_LABEL: Record<string, string> = {
  pending: '待审',
  approved: '已批准',
  rejected: '已拒绝',
  borrowed: '已借',
  returned: '已还',
};

const tableData = ref<BorrowRequestItemDto[]>([]);
const total = ref(0);
const loading = ref(false);
const queryParams = reactive<QueryBorrowPageParams>({
  pageNo: 1,
  pageSize: 20,
});

const actionDialogVisible = ref(false);
const actionType = ref<'approve' | 'reject'>('approve');
const actionRow = ref<BorrowRequestItemDto | null>(null);
const actionComment = ref('');

async function loadPage() {
  loading.value = true;
  try {
    const page = await queryBorrowPageApi(queryParams);
    tableData.value = page.rows;
    total.value = page.total;
  } finally {
    loading.value = false;
  }
}

function openAction(row: BorrowRequestItemDto, type: 'approve' | 'reject') {
  actionRow.value = row;
  actionType.value = type;
  actionComment.value = '';
  actionDialogVisible.value = true;
}

async function submitAction() {
  if (!actionRow.value?.id) return;
  if (actionType.value === 'approve') {
    await approveBorrowApi({
      borrowRequestId: actionRow.value.id,
      comment: actionComment.value,
    });
    ElMessage.success('已批准');
  } else {
    await rejectBorrowApi({
      borrowRequestId: actionRow.value.id,
      comment: actionComment.value,
    });
    ElMessage.success('已拒绝');
  }
  actionDialogVisible.value = false;
  await loadPage();
}

async function handleMarkBorrowed(row: BorrowRequestItemDto) {
  if (!row.id) return;
  await markBorrowedApi({ borrowRequestId: row.id });
  ElMessage.success('已标记借出');
  await loadPage();
}

async function handleMarkReturned(row: BorrowRequestItemDto) {
  if (!row.id) return;
  await ElMessageBox.confirm('确认归还该借阅申请？', '归还确认');
  await markReturnedApi({ borrowRequestId: row.id });
  ElMessage.success('已归还');
  await loadPage();
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
        <ElSelect
          v-model="queryParams.statusCode"
          clearable
          placeholder="状态"
          style="width: 140px"
          @change="loadPage"
        >
          <ElOption
            v-for="(label, code) in BORROW_STATUS_LABEL"
            :key="code"
            :label="label"
            :value="code"
          />
        </ElSelect>
        <ElButton type="primary" @click="loadPage">查询</ElButton>
      </template>
      <template #columns>
        <el-table-column label="病案号" prop="medcasno" width="120" />
        <el-table-column label="患者" prop="psnName" width="100" />
        <el-table-column label="申请人" prop="applicantName" width="100" />
        <el-table-column label="科室" prop="applicantDeptName" width="120" />
        <el-table-column label="用途" prop="purpose" min-width="160" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <ElTag>{{ BORROW_STATUS_LABEL[row.statusCode] ?? row.statusCode }}</ElTag>
          </template>
        </el-table-column>
        <el-table-column label="申请时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <ElButton
              v-if="row.statusCode === 'pending'"
              link
              type="primary"
              @click="openAction(row, 'approve')"
            >
              批准
            </ElButton>
            <ElButton
              v-if="row.statusCode === 'pending'"
              link
              type="danger"
              @click="openAction(row, 'reject')"
            >
              拒绝
            </ElButton>
            <ElButton
              v-if="row.statusCode === 'approved'"
              link
              type="primary"
              @click="handleMarkBorrowed(row)"
            >
              借出
            </ElButton>
            <ElButton
              v-if="row.statusCode === 'borrowed'"
              link
              type="success"
              @click="handleMarkReturned(row)"
            >
              归还
            </ElButton>
          </template>
        </el-table-column>
      </template>
    </TableLayout>

    <ElDialog
      v-model="actionDialogVisible"
      :title="actionType === 'approve' ? '批准借阅' : '拒绝借阅'"
      width="480px"
    >
      <ElForm label-width="80px">
        <ElFormItem label="审批意见">
          <ElInput v-model="actionComment" :rows="3" type="textarea" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="actionDialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="submitAction">确定</ElButton>
      </template>
    </ElDialog>
  </Page>
</template>
