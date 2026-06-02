<script lang="ts" setup>
import type {
  PositionUserWorkbenchListParams,
  PositionUserWorkbenchRow,
} from '#/api/basic/position';

import { computed, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElPagination,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { pageUsersForWorkbenchApi } from '#/api/basic/position';

import PositionAssignmentsPanel from './position-assignments-panel.vue';
import PractitionerSignaturePanel from './practitioner-signature-panel.vue';

defineOptions({
  name: 'PositionWorkbench',
});

const queryParams = ref<PositionUserWorkbenchListParams>({
  pageNo: 1,
  pageSize: 50,
  keyword: '',
});

const tableData = ref<PositionUserWorkbenchRow[]>([]);
const loading = ref(false);
const total = ref(0);

const selectedRow = ref<PositionUserWorkbenchRow | null>(null);

const loadData = async () => {
  loading.value = true;
  try {
    const response = await pageUsersForWorkbenchApi({
      pageNo: queryParams.value.pageNo,
      pageSize: queryParams.value.pageSize,
      keyword: queryParams.value.keyword || undefined,
    });
    const page = response.data;
    if (page) {
      tableData.value = page.content ?? [];
      total.value = page.totalCount ?? 0;
    } else {
      tableData.value = [];
      total.value = 0;
    }
  } catch {
    ElMessage.error('加载用户列表失败，请稍后重试');
    tableData.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  queryParams.value.pageNo = 1;
  loadData();
};

const handleReset = () => {
  queryParams.value = {
    pageNo: 1,
    pageSize: 50,
    keyword: '',
  };
  selectedRow.value = null;
  loadData();
};

const handlePageChange = (page: number) => {
  queryParams.value.pageNo = page;
  loadData();
};

const handleSizeChange = (size: number) => {
  queryParams.value.pageSize = size;
  queryParams.value.pageNo = 1;
  loadData();
};

const handleRowClick = (row: PositionUserWorkbenchRow) => {
  selectedRow.value = row;
};

const assignmentUserSummary = computed(() => {
  const r = selectedRow.value;
  if (!r) {
    return '';
  }
  const name = r.name ? `（${r.name}）` : '';
  const pr = r.practitionerName ? ` · 医务 ${r.practitionerName}` : '';
  return `${r.userName ?? ''}${name}${pr}`;
});

onMounted(() => {
  loadData();
});
</script>

<template>
  <Page>
    <div class="position-workbench flex h-full min-h-[480px] flex-col gap-0 md:flex-row">
      <aside
        class="position-workbench__side border-border flex w-full flex-shrink-0 flex-col border-b md:w-[480px] md:border-b-0 md:border-r"
      >
        <div class="p-4">
          <ElForm :inline="true" class="position-workbench__form" @submit.prevent="handleSearch">
            <ElFormItem label="关键词">
              <ElInput
                v-model="queryParams.keyword"
                clearable
                placeholder="用户名、姓名、手机、邮箱"
                style="width: 200px"
                @keyup.enter="handleSearch"
              />
            </ElFormItem>
            <ElFormItem>
              <ElButton type="primary" @click="handleSearch">查询</ElButton>
              <ElButton @click="handleReset">重置</ElButton>
            </ElFormItem>
          </ElForm>

          <ElTable
            :data="tableData"
            :loading="loading"
            class="w-full"
            height="420"
            highlight-current-row
            row-key="userId"
            stripe
            @row-click="handleRowClick"
          >
            <ElTableColumn label="用户名" min-width="88" prop="userName" show-overflow-tooltip />
            <ElTableColumn label="姓名" min-width="72" prop="name" show-overflow-tooltip />
            <ElTableColumn
              label="系统角色"
              min-width="120"
              prop="roleNames"
              show-overflow-tooltip
            />
            <ElTableColumn label="医务人员" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.practitionerName">
                  {{ row.practitionerName }}
                  <span v-if="row.busNo" class="text-muted-foreground text-xs"
                    >（{{ row.busNo }}）</span
                  >
                </span>
                <span v-else class="text-muted-foreground">未建档</span>
              </template>
            </ElTableColumn>
          </ElTable>

          <div class="mt-3 flex justify-end">
            <ElPagination
              v-model:current-page="queryParams.pageNo"
              v-model:page-size="queryParams.pageSize"
              :disabled="loading"
              :page-sizes="[20, 50, 100]"
              :total="total"
              background
              layout="total, sizes, prev, pager, next"
              @current-change="handlePageChange"
              @size-change="handleSizeChange"
            />
          </div>
        </div>
      </aside>

      <div
        class="position-workbench__center md:border-border flex min-h-[420px] min-w-0 flex-1 flex-col overflow-hidden p-3 md:border-l"
      >
        <template v-if="selectedRow">
          <PractitionerSignaturePanel
            :practitioner-id="
              selectedRow.practitionerId != null ? String(selectedRow.practitionerId) : undefined
            "
            :practitioner-name="selectedRow.practitionerName"
            :bus-no="selectedRow.busNo"
          />
          <PositionAssignmentsPanel
            :practitioner-id="selectedRow.practitionerId"
            :user-summary="assignmentUserSummary"
          />
        </template>
        <template v-else>
          <p class="text-muted-foreground p-4 text-sm">
            请在左侧列表中选择用户以维护岗位（adm_pr_assign）。
          </p>
        </template>
      </div>
    </div>
  </Page>
</template>
