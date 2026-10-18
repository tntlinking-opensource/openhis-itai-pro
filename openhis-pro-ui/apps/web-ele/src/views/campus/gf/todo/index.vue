<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@zxplat-core/table-layout-ui';

import type { GfRatioRequestDto } from '#/api/campus/campus-gf-record';
import type { GfChargeMode } from '#/api/campus/types';

import { onMounted, reactive, ref, watch } from 'vue';

import { Page } from '@zxplat/common-ui';
import { TableLayout } from '@zxplat-core/table-layout-ui';

import {
  approveApi,
  pageFinishedApi,
  pageTodoApi,
  rejectApi,
} from '#/api/campus/campus-gf-approval';
import { gfChargeModeLabel, gfRequestStatusLabel, unwrapCampusPage } from '#/api/campus/types';
import { useRequest } from '#/hooks/use-request';
import ChargeModeSwitch from '#/views/campus/components/ChargeModeSwitch.vue';
import { ElButton, ElMessage, ElMessageBox, ElTabPane, ElTabs, ElTag } from 'element-plus';

defineOptions({ name: 'CampusGfApprovalTodo' });

type ApprovalTab = 'todo' | 'finished';

const activeTab = ref<ApprovalTab>('todo');
const chargeMode = ref<GfChargeMode | ''>('');
const list = ref<GfRatioRequestDto[]>([]);
const total = ref(0);

const query = reactive({
  pageNo: 1,
  pageSize: 20,
  keyword: '',
});

const formItems: FormItemConfig[] = [
  { prop: 'keyword', label: '关键词', type: 'input', placeholder: '单号/项目编码' },
];

const columns: TableColumn[] = [
  { prop: 'requestNo', label: '申请单号', minWidth: 140 },
  { prop: 'itemCode', label: '项目编码', minWidth: 120 },
  { prop: 'chargeModeEnum', label: '收费模式', width: 110, slot: 'chargeModeEnum' },
  { prop: 'oldRatio', label: '原比例', width: 90 },
  { prop: 'newRatio', label: '新比例', width: 90 },
  { prop: 'applicantName', label: '申请人', width: 100 },
  { prop: 'applyTime', label: '申请时间', minWidth: 150 },
  { prop: 'statusEnum', label: '状态', width: 90, slot: 'statusEnum' },
  { prop: 'actions', label: '操作', width: 140, slot: 'actions' },
];

async function loadList() {
  const params = {
    chargeModeEnum: chargeMode.value || undefined,
    keyword: String(query.keyword || '').trim() || undefined,
    pageNo: query.pageNo,
    pageSize: query.pageSize,
  };
  const res =
    activeTab.value === 'todo' ? await pageTodoApi(params) : await pageFinishedApi(params);
  const page = unwrapCampusPage(res);
  list.value = page.list;
  total.value = page.total;
}

const { run: runLoad, loading } = useRequest(loadList, { manual: true });

function handleSearch() {
  query.pageNo = 1;
  void runLoad();
}

function handlePagination(pagination: { limit: number; page: number }) {
  query.pageNo = pagination.page;
  query.pageSize = pagination.limit;
  void runLoad();
}

async function promptComment(title: string): Promise<string | undefined> {
  try {
    const { value } = await ElMessageBox.prompt('请输入审批意见（可选）', title, {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputType: 'textarea',
    });
    return value;
  } catch {
    return undefined;
  }
}

async function handleApprove(row: GfRatioRequestDto) {
  const comment = await promptComment('通过申请');
  if (comment === undefined) return;
  await approveApi({ requestId: row.id!, comment: comment || undefined });
  ElMessage.success('已通过');
  await runLoad();
}

async function handleReject(row: GfRatioRequestDto) {
  const comment = await promptComment('驳回申请');
  if (comment === undefined) return;
  await rejectApi({ requestId: row.id!, comment: comment || undefined });
  ElMessage.success('已驳回');
  await runLoad();
}

watch([activeTab, chargeMode], () => {
  query.pageNo = 1;
  void runLoad();
});

onMounted(() => {
  void runLoad();
});
</script>

<template>
  <Page title="比例审批" description="待办与已办审批；按收费模式筛选，通过/驳回需填写意见。">
    <ElTabs v-model="activeTab" class="mb-3">
      <ElTabPane label="待办" name="todo" />
      <ElTabPane label="已办" name="finished" />
    </ElTabs>

    <div class="mb-3">
      <ChargeModeSwitch v-model="chargeMode" allow-all />
    </div>

    <TableLayout
      v-model:query-params="query"
      :table-columns="columns"
      :table-data="list"
      :form-items="formItems"
      :loading="loading"
      :total="total"
      @pagination="handlePagination"
      @query="handleSearch"
      @reset="handleSearch"
    >
      <template #chargeModeEnum="{ row }">
        <ElTag size="small">{{ gfChargeModeLabel(row.chargeModeEnum) }}</ElTag>
      </template>
      <template #statusEnum="{ row }">
        <ElTag
          :type="
            row.statusEnum === 'APPROVED'
              ? 'success'
              : row.statusEnum === 'REJECTED'
                ? 'danger'
                : 'warning'
          "
        >
          {{ gfRequestStatusLabel(row.statusEnum) }}
        </ElTag>
      </template>
      <template #actions="{ row }">
        <template v-if="activeTab === 'todo'">
          <ElButton link type="primary" @click="handleApprove(row)">通过</ElButton>
          <ElButton link type="danger" @click="handleReject(row)">驳回</ElButton>
        </template>
        <span v-else class="text-secondary">—</span>
      </template>
    </TableLayout>
  </Page>
</template>

<style scoped>
.text-secondary {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
