<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type {
  PlatformDictItem,
  PlatformDictSyncLog,
  SyncPlatformDictFrom1901Params,
} from '#/chs/api/platform-dict';
import type { ChargeType } from '#/api/core/common';
import type { PageParams } from '#/types/api-response';

import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import {
  ElAlert,
  ElButton,
  ElCheckbox,
  ElDescriptions,
  ElDescriptionsItem,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTag,
} from 'element-plus';

import { getChargeTypeListApi } from '#/api/core/common';
import {
  getPlatformDictLastSyncLogApi,
  pagePlatformDictApi,
  syncPlatformDictFrom1901Api,
} from '#/chs/api/platform-dict';
import {
  filterInsuranceChargeTypes,
  resolveDefaultInsuranceBusNo,
} from '#/chs/constants/insurance-contract';
import {
  PLATFORM_DICT_SYNC_HINT,
  YB_PLATFORM_DICT_DEFAULT,
  YB_PLATFORM_DICT_TYPE_MED_TYPE,
  YB_PLATFORM_DICT_TYPE_OPTIONS,
} from '#/chs/constants/platform-dict';
import AppDialog from '#/components/app-dialog.vue';
import { ensureInsuranceSignIn } from '#/chs/utils/ensure-insurance-sign-in';
import { useRequest } from '#/hooks/use-request';

defineOptions({ name: 'ChsPlatformDictPage' });

const route = useRoute();

interface PlatformDictQuery extends PageParams {
  dictType?: string;
  keyword?: string;
}

const queryParams = ref<PlatformDictQuery>({
  pageNo: 1,
  pageSize: 50,
  dictType: YB_PLATFORM_DICT_DEFAULT,
  keyword: undefined,
});

const tableData = ref<PlatformDictItem[]>([]);
const total = ref(0);

const chargeTypeOptions = ref<ChargeType[]>([]);
const syncContractBusNo = ref<string>();
const lastSyncLog = ref<PlatformDictSyncLog | null>(null);

const syncDialogVisible = ref(false);
const syncForm = ref<SyncPlatformDictFrom1901Params>({
  dictType: YB_PLATFORM_DICT_DEFAULT,
  queryAdmdvs: '',
  parentValue: '',
  recursive: true,
  pageSize: 500,
});

const formItems = computed<FormItemConfig[]>(() => [
  {
    type: 'select',
    prop: 'dictType',
    label: '字典类型',
    clearable: false,
    multiple: false,
    options: YB_PLATFORM_DICT_TYPE_OPTIONS.map((item) => ({
      label: item.label,
      value: item.value,
    })),
  },
  {
    type: 'input',
    prop: 'keyword',
    label: '关键字',
    placeholder: '字典码或名称',
    clearable: true,
  },
]);

const tableColumns: TableColumn[] = [
  {
    prop: 'dictValue',
    label: '字典码',
    width: 110,
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'dictLabel',
    label: '展示名',
    minWidth: 160,
    showOverflowTooltip: true,
  },
  {
    prop: 'parentValue',
    label: '上级',
    width: 100,
    align: 'center',
    headerAlign: 'center',
    formatter: (_row, _column, cellValue) => String(cellValue ?? '').trim() || '—',
  },
  {
    prop: 'valiFlag',
    label: '有效',
    width: 72,
    align: 'center',
    headerAlign: 'center',
    slot: 'valiFlag',
  },
  {
    prop: 'queryAdmdvs',
    label: '同步区划',
    width: 100,
    align: 'center',
    headerAlign: 'center',
    formatter: (_row, _column, cellValue) => String(cellValue ?? '').trim() || '—',
  },
  {
    prop: 'syncedAt',
    label: '同步时间',
    width: 168,
    align: 'center',
    headerAlign: 'center',
  },
];

const lastSyncStatusTag = computed(() => {
  const log = lastSyncLog.value;
  if (!log) return { type: 'info' as const, text: '未同步' };
  const rows = log.rowsUpserted ?? 0;
  if (rows <= 0) {
    if (log.status === 'FAILED') return { type: 'danger' as const, text: '失败' };
    return { type: 'warning' as const, text: '未写入' };
  }
  const status = log.status;
  if (status === 'SUCCESS') return { type: 'success' as const, text: '成功' };
  if (status === 'FAILED') return { type: 'danger' as const, text: '失败' };
  return { type: 'info' as const, text: status || '未知' };
});

const { loading: pageLoading, run: runPageQuery } = useRequest(pagePlatformDictApi, {
  manual: true,
  onSuccess: (result) => {
    tableData.value = result.content;
    total.value = result.totalCount;
  },
});

const { loading: syncLogLoading, run: runLoadSyncLog } = useRequest(getPlatformDictLastSyncLogApi, {
  manual: true,
  showErrorMessage: false,
  onSuccess: (log) => {
    lastSyncLog.value = log;
  },
});

const { loading: syncLoading, run: runSync } = useRequest(syncPlatformDictFrom1901Api, {
  manual: true,
  errorMessage: (error) => {
    const err = error as {
      message?: string;
      msg?: string;
      response?: { data?: { message?: string } };
    };
    return err?.message || err?.msg || err?.response?.data?.message || '1901 同步失败，请稍后重试';
  },
  onSuccess: (result) => {
    const rows = result.rowsUpserted ?? 0;
    ElMessage.success(`1901 同步完成，写入 ${rows} 行`);
    if (result.dictType) {
      queryParams.value.dictType = result.dictType;
    }
    syncDialogVisible.value = false;
    void refreshAfterSync();
  },
});

const { loading: signInLoading, run: runSignIn } = useRequest(ensureInsuranceSignIn, {
  manual: true,
  onSuccess: () => ElMessage.success('医保签到成功'),
});

function requireDictType(): string | undefined {
  const dictType = String(queryParams.value.dictType ?? '').trim();
  if (!dictType) {
    ElMessage.warning('请选择字典类型');
    return undefined;
  }
  return dictType;
}

async function loadList() {
  const dictType = requireDictType();
  if (!dictType) return;
  await runPageQuery({
    pageNo: queryParams.value.pageNo ?? 1,
    pageSize: queryParams.value.pageSize ?? 50,
    dictType,
    keyword: queryParams.value.keyword?.trim() || undefined,
  });
}

function handleQuery(params: Record<string, unknown>) {
  queryParams.value.pageNo = 1;
  if (params.dictType !== undefined) {
    queryParams.value.dictType = String(params.dictType ?? YB_PLATFORM_DICT_DEFAULT);
  }
  if (params.keyword !== undefined) {
    const raw = params.keyword;
    queryParams.value.keyword =
      raw === null || raw === '' ? undefined : typeof raw === 'string' ? raw : String(raw);
  }
  void loadList();
  void loadLastSyncLog();
}

function handleReset() {
  queryParams.value = {
    pageNo: 1,
    pageSize: 50,
    dictType: YB_PLATFORM_DICT_DEFAULT,
    keyword: undefined,
  };
  void loadList();
  void loadLastSyncLog();
}

function handlePagination(pagination: { limit: number; page: number }) {
  queryParams.value.pageNo = pagination.page;
  queryParams.value.pageSize = pagination.limit;
  void loadList();
}

async function loadLastSyncLog() {
  const dictType = requireDictType();
  if (!dictType) return;
  await runLoadSyncLog(dictType);
}

async function refreshAfterSync() {
  await loadLastSyncLog();
  queryParams.value.pageNo = 1;
  await loadList();
}

function openSyncDialog() {
  const busNo = syncContractBusNo.value?.trim();
  if (!busNo) {
    ElMessage.warning('请先加载医保契约');
    return;
  }
  syncForm.value = {
    contractBusNo: busNo,
    dictType: queryParams.value.dictType || YB_PLATFORM_DICT_DEFAULT,
    queryAdmdvs: '',
    parentValue: '',
    recursive:
      (queryParams.value.dictType || YB_PLATFORM_DICT_DEFAULT) !== YB_PLATFORM_DICT_TYPE_MED_TYPE,
    pageSize: 500,
  };
  syncDialogVisible.value = true;
}

async function handleConfirmSync() {
  const busNo = syncContractBusNo.value?.trim();
  if (!busNo) {
    ElMessage.warning('请选择医保契约');
    return;
  }
  try {
    await ElMessageBox.confirm(
      '将从省平台 1901 拉取字典并 upsert 至本地镜像，耗时可能较长，是否继续？',
      '1901 手动同步',
      { type: 'warning', confirmButtonText: '开始同步', cancelButtonText: '取消' },
    );
  } catch {
    return;
  }
  try {
    await runSignIn(busNo);
  } catch {
    return;
  }
  await runSync({
    contractBusNo: busNo,
    dictType: syncForm.value.dictType || YB_PLATFORM_DICT_DEFAULT,
    queryAdmdvs: syncForm.value.queryAdmdvs?.trim() || undefined,
    parentValue: syncForm.value.parentValue ?? '',
    recursive: syncForm.value.recursive !== false,
    pageSize: syncForm.value.pageSize ?? 500,
  });
}

async function handleSignIn() {
  const busNo = syncContractBusNo.value?.trim();
  if (!busNo) {
    ElMessage.warning('暂无可用医保契约');
    return;
  }
  await runSignIn(busNo);
}

async function loadChargeTypes() {
  try {
    const all = await getChargeTypeListApi();
    const eligible = filterInsuranceChargeTypes(all);
    chargeTypeOptions.value = eligible;
    syncContractBusNo.value = resolveDefaultInsuranceBusNo(
      eligible,
      (route.query.contractBusNo as string | undefined) ?? syncContractBusNo.value,
    );
  } catch {
    chargeTypeOptions.value = [];
    syncContractBusNo.value = undefined;
  }
}

function valiFlagTagType(code?: string): 'danger' | 'info' | 'success' {
  if (code === '1') return 'success';
  if (code === '0') return 'danger';
  return 'info';
}

onMounted(async () => {
  await loadChargeTypes();
  await loadLastSyncLog();
  await loadList();
});
</script>

<template>
  <Page auto-content-height content-class="!overflow-hidden flex h-full min-h-0 flex-col">
    <TableLayout
      class="h-full min-h-0 flex-1"
      table-height="100%"
      :table-data="tableData"
      :loading="pageLoading"
      :total="total"
      :query-params="queryParams"
      :form-items="formItems"
      :table-columns="tableColumns"
      :show-top-query="true"
      :show-pagination="true"
      border
      stripe
      row-key="id"
      @query="handleQuery"
      @reset="handleReset"
      @pagination="handlePagination"
    >
      <template #toolbar-actions>
        <div class="mb-2 flex w-full flex-col gap-2">
          <ElAlert :title="PLATFORM_DICT_SYNC_HINT" type="info" :closable="false" show-icon />
          <div class="flex flex-wrap items-center gap-2">
            <ElSelect
              v-model="syncContractBusNo"
              placeholder="同步所用医保契约"
              style="width: 280px"
              filterable
            >
              <ElOption
                v-for="item in chargeTypeOptions"
                :key="item.busNo"
                :label="item.busNo ? `${item.label}（${item.busNo}）` : item.label"
                :value="item.busNo"
              />
            </ElSelect>
            <ElButton type="primary" :loading="syncLoading" @click="openSyncDialog">
              1901 手动同步
            </ElButton>
            <ElButton plain :loading="signInLoading" @click="handleSignIn">医保签到</ElButton>
            <ElButton plain :loading="syncLogLoading" @click="loadLastSyncLog"
              >刷新同步状态</ElButton
            >
          </div>
          <ElDescriptions
            v-if="lastSyncLog"
            :column="4"
            border
            size="small"
            class="max-w-full"
            title="最近一次同步"
          >
            <ElDescriptionsItem label="状态">
              <ElTag :type="lastSyncStatusTag.type" effect="light">
                {{ lastSyncStatusTag.text }}
              </ElTag>
            </ElDescriptionsItem>
            <ElDescriptionsItem label="写入行数">
              {{ lastSyncLog.rowsUpserted ?? '—' }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="查询区划">
              {{ lastSyncLog.queryAdmdvs || '—' }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="完成时间">
              {{ lastSyncLog.finishedAt || '—' }}
            </ElDescriptionsItem>
            <ElDescriptionsItem v-if="lastSyncLog.errorMessage" label="说明" :span="4">
              {{ lastSyncLog.errorMessage }}
            </ElDescriptionsItem>
            <ElDescriptionsItem
              v-else-if="(lastSyncLog.rowsUpserted ?? 0) <= 0"
              label="说明"
              :span="4"
            >
              本地镜像暂无数据。可先选「医疗类别 (med_type)」验证 1901 入库；国标字典表不含医保区划
              type。
            </ElDescriptionsItem>
          </ElDescriptions>
        </div>
      </template>

      <template #valiFlag="{ row }">
        <ElTag :type="valiFlagTagType(row.valiFlag)" effect="light">
          {{ row.valiFlag === '1' ? '有效' : row.valiFlag === '0' ? '无效' : row.valiFlag || '—' }}
        </ElTag>
      </template>
    </TableLayout>

    <AppDialog v-model="syncDialogVisible" title="1901 字典手动同步" width="520px">
      <ElForm label-width="108px">
        <ElFormItem label="字典类型">
          <ElSelect v-model="syncForm.dictType" style="width: 100%">
            <ElOption
              v-for="item in YB_PLATFORM_DICT_TYPE_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="查询区划">
          <ElInput
            v-model="syncForm.queryAdmdvs"
            placeholder="留空取契约 adm_vs（如 229900）"
            maxlength="6"
            clearable
          />
        </ElFormItem>
        <ElFormItem label="根 parentValue">
          <ElInput
            v-model="syncForm.parentValue"
            placeholder="默认空串；可按省根如 220000 分批"
            clearable
          />
        </ElFormItem>
        <ElFormItem label="递归子级">
          <ElCheckbox v-model="syncForm.recursive">BFS 拉取下级 parentValue</ElCheckbox>
        </ElFormItem>
        <ElFormItem label="分页大小">
          <ElInputNumber v-model="syncForm.pageSize" :min="50" :max="1000" :step="50" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="syncDialogVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="syncLoading" @click="handleConfirmSync"
          >开始同步</ElButton
        >
      </template>
    </AppDialog>
  </Page>
</template>
