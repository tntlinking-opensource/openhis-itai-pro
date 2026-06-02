<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type { Catalog1312FilterParams } from '#/chs/constants/catalog-1312-filter';
import type { CatalogDownloadCursor, NationalCatalogItem } from '#/chs/api';
import type { ChargeType } from '#/api/core/common';
import type { PageParams } from '#/types/api-response';

import { computed, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import {
  ElAlert,
  ElButton,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getChargeTypeListApi } from '#/api/core/common';
import {
  insuranceSignInApi,
  listDownloadCursorsApi,
  pageNationalCatalogApi,
  refreshFromPlatform1312Api,
} from '#/chs/api';
import { resolveSignEndpointForSignIn } from '#/chs/utils/yb-plugin';
import {
  CATALOG_1312_SCOPE_HINT,
  formatCatalogListType,
  formatChrgitmLv,
  formatLmtUsedFlag,
  formatValiFlag,
  YB_CATALOG_LIST_TYPE_OPTIONS,
  YB_CHRGITM_LV_OPTIONS,
  YB_LMT_USED_FLAG_OPTIONS,
  YB_VALI_FLAG_OPTIONS,
} from '#/chs/constants/catalog-list-type';
import { pickCatalog1312Filter } from '#/chs/constants/catalog-1312-filter';
import AppDialog from '#/components/app-dialog.vue';
import { useRequest } from '#/hooks/use-request';

defineOptions({ name: 'ChsNationalCatalogPage' });

interface NationalCatalogQuery extends PageParams, Catalog1312FilterParams {
  contractBusNo?: string;
  queryPlatformIfMiss?: boolean;
}

const default1312Filter = (): Catalog1312FilterParams => ({
  listType: '101',
  keyword: undefined,
  hilistCode: undefined,
  hilistName: undefined,
  pinyin: undefined,
  updtTime: undefined,
  valiFlag: '1',
  chrgitmLv: undefined,
  lmtUsedFlag: undefined,
  queryDate: undefined,
  begndate: undefined,
});

const queryParams = ref<NationalCatalogQuery>({
  pageNo: 1,
  pageSize: 50,
  contractBusNo: undefined,
  queryPlatformIfMiss: true,
  ...default1312Filter(),
});

const tableData = ref<NationalCatalogItem[]>([]);
const total = ref(0);

const cursorDialogVisible = ref(false);
const downloadCursors = ref<CatalogDownloadCursor[]>([]);

const chargeTypeOptions = ref<ChargeType[]>([]);

const dateFieldProps = {
  valueFormat: 'YYYY-MM-DD',
  clearable: true,
};

const formItems = computed<FormItemConfig[]>(() => [
  {
    type: 'select',
    prop: 'contractBusNo',
    label: '医保契约',
    placeholder: '请选择医保契约',
    clearable: false,
    multiple: false,
    options: chargeTypeOptions.value
      .filter((item) => item.busNo)
      .map((item) => ({
        label: item.busNo ? `${item.label}（${item.busNo}）` : item.label,
        value: item.busNo,
      })),
  },
  {
    type: 'select',
    prop: 'listType',
    label: '目录类型',
    placeholder: '请选择目录类型',
    clearable: true,
    multiple: false,
    options: YB_CATALOG_LIST_TYPE_OPTIONS.map((item) => ({
      label: item.label,
      value: item.value,
    })),
  },
  {
    type: 'input',
    prop: 'keyword',
    label: '名称关键字',
    placeholder: '同时匹配名称/拼音',
    clearable: true,
  },
  {
    type: 'input',
    prop: 'hilistName',
    label: '目录名称',
    placeholder: '1312 hilist_name',
    clearable: true,
  },
  {
    type: 'input',
    prop: 'pinyin',
    label: '拼音码',
    placeholder: '1312 pinyin',
    clearable: true,
  },
  {
    type: 'input',
    prop: 'hilistCode',
    label: '医保编码',
    placeholder: '1312 hilist_code',
    clearable: true,
  },
  {
    type: 'date',
    prop: 'updtTime',
    label: '增量游标',
    placeholder: '留空自动',
    ...dateFieldProps,
  },
  {
    type: 'select',
    prop: 'valiFlag',
    label: '有效标志',
    clearable: true,
    multiple: false,
    options: YB_VALI_FLAG_OPTIONS.map((item) => ({
      label: item.label,
      value: item.value,
    })),
  },
  {
    type: 'select',
    prop: 'chrgitmLv',
    label: '收费等级',
    placeholder: '甲/乙/丙',
    clearable: true,
    multiple: false,
    options: YB_CHRGITM_LV_OPTIONS.map((item) => ({
      label: item.label,
      value: item.value,
    })),
  },
  {
    type: 'select',
    prop: 'lmtUsedFlag',
    label: '限制使用',
    clearable: true,
    multiple: false,
    options: YB_LMT_USED_FLAG_OPTIONS.map((item) => ({
      label: item.label,
      value: item.value,
    })),
  },
  {
    type: 'date',
    prop: 'queryDate',
    label: '查询时间点',
    placeholder: '1312 query_date',
    ...dateFieldProps,
  },
  {
    type: 'date',
    prop: 'begndate',
    label: '开始日期',
    placeholder: '1312 begndate',
    ...dateFieldProps,
  },
  {
    type: 'radio',
    prop: 'queryPlatformIfMiss',
    label: '无本地数据时查平台',
    options: [
      { label: '是', value: true },
      { label: '否', value: false },
    ],
  },
]);

const tableColumns: TableColumn[] = [
  { type: 'index', label: '序号', width: 60, align: 'center', headerAlign: 'center' },
  {
    prop: 'listType',
    label: '目录类型',
    width: 120,
    align: 'center',
    headerAlign: 'center',
    slot: 'listType',
  },
  {
    prop: 'hilistCode',
    label: '医保编码',
    minWidth: 140,
    showOverflowTooltip: true,
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'hilistName',
    label: '医保名称',
    minWidth: 200,
    showOverflowTooltip: true,
  },
  {
    prop: 'chrgitmLv',
    label: '收费等级',
    width: 90,
    align: 'center',
    headerAlign: 'center',
    slot: 'chrgitmLv',
  },
  {
    prop: 'lmtUsedFlag',
    label: '限制使用',
    width: 90,
    align: 'center',
    headerAlign: 'center',
    slot: 'lmtUsedFlag',
  },
  {
    prop: 'valiFlag',
    label: '有效标志',
    width: 90,
    align: 'center',
    headerAlign: 'center',
    slot: 'valiFlag',
  },
  { prop: 'begndate', label: '开始日期', width: 110, align: 'center', headerAlign: 'center' },
  { prop: 'enddate', label: '结束日期', width: 110, align: 'center', headerAlign: 'center' },
  { prop: 'pinyin', label: '拼音', width: 100, showOverflowTooltip: true },
  { prop: 'updtTime', label: '更新时间', width: 110, align: 'center', headerAlign: 'center' },
  {
    prop: 'sourceInfno',
    label: '来源接口',
    width: 90,
    align: 'center',
    headerAlign: 'center',
  },
];

const { run: runPageQuery, loading: pageLoading } = useRequest(pageNationalCatalogApi, {
  manual: true,
  onSuccess: (data) => {
    tableData.value = data.content ?? [];
    total.value = data.totalCount ?? 0;
  },
});

const { run: runRefresh1312, loading: refreshLoading } = useRequest(refreshFromPlatform1312Api, {
  manual: true,
  showErrorMessage: true,
  onSuccess: (count) => {
    ElMessage.success(`已从平台同步 ${count ?? 0} 条目录`);
    void loadList();
  },
});

const { run: runSignIn, loading: signInLoading } = useRequest(insuranceSignInApi, {
  manual: true,
  showErrorMessage: true,
  onSuccess: (signNo) => {
    ElMessage.success(`签到成功：${signNo ?? ''}`);
  },
});

const { run: runListCursors, loading: cursorLoading } = useRequest(listDownloadCursorsApi, {
  manual: true,
  onSuccess: (rows) => {
    downloadCursors.value = rows ?? [];
    cursorDialogVisible.value = true;
  },
});

/** 校验医保契约 bus_no 是否已选 */
function requireContractBusNo(): string | undefined {
  const busNo = queryParams.value.contractBusNo;
  if (busNo === undefined || busNo === null || String(busNo).trim() === '') {
    ElMessage.warning('请先选择医保契约');
    return undefined;
  }
  return String(busNo).trim();
}

/** 组装分页查询入参（本地库 + 可选 1312 补库条件） */
function buildPageParams(): Parameters<typeof pageNationalCatalogApi>[0] | undefined {
  const contractBusNo = requireContractBusNo();
  if (!contractBusNo) return undefined;
  return {
    contractBusNo,
    ...pickCatalog1312Filter(queryParams.value),
    queryPlatformIfMiss: queryParams.value.queryPlatformIfMiss ?? false,
    pageNo: queryParams.value.pageNo ?? 1,
    pageSize: queryParams.value.pageSize ?? 50,
  };
}

/** 从 TableLayout 回写 1312 筛选字段 */
function applyFilterParams(params: Record<string, unknown>) {
  const stringFields = [
    'contractBusNo',
    'listType',
    'keyword',
    'hilistCode',
    'hilistName',
    'pinyin',
    'updtTime',
    'valiFlag',
    'chrgitmLv',
    'lmtUsedFlag',
    'queryDate',
    'begndate',
  ] as const;
  for (const key of stringFields) {
    if (params[key] === undefined) continue;
    const raw = params[key];
    const value =
      raw === null || raw === '' ? undefined : typeof raw === 'string' ? raw : String(raw);
    (queryParams.value as Record<string, unknown>)[key] = value;
  }
  if (params.queryPlatformIfMiss !== undefined) {
    queryParams.value.queryPlatformIfMiss = Boolean(params.queryPlatformIfMiss);
  }
}

/** 拉取国家目录分页列表 */
async function loadList() {
  const params = buildPageParams();
  if (!params) return;
  await runPageQuery(params);
}

/** TableLayout 查询：同步筛选条件并回到第一页 */
function handleQuery(params: Record<string, unknown>) {
  queryParams.value.pageNo = 1;
  applyFilterParams(params);
  void loadList();
}

/** 重置筛选并重新查询 */
function handleReset() {
  const contractBusNo = queryParams.value.contractBusNo;
  queryParams.value = {
    pageNo: 1,
    pageSize: 50,
    contractBusNo,
    queryPlatformIfMiss: true,
    ...default1312Filter(),
  };
  void loadList();
}

/** 分页变更后重新查询 */
function handlePagination(pagination: { limit: number; page: number }) {
  queryParams.value.pageNo = pagination.page;
  queryParams.value.pageSize = pagination.limit;
  void loadList();
}

/** 从医保平台 1312 刷新当前筛选条件下的目录 */
async function handleRefreshFromPlatform() {
  const contractBusNo = requireContractBusNo();
  if (!contractBusNo) return;
  try {
    await ElMessageBox.confirm(
      '将按当前筛选条件调用医保 1312 接口并写入本地镜像，是否继续？',
      '平台刷新',
      { type: 'warning', confirmButtonText: '刷新', cancelButtonText: '取消' },
    );
  } catch {
    return;
  }
  await runRefresh1312({
    contractBusNo,
    ...pickCatalog1312Filter(queryParams.value),
    pageNum: queryParams.value.pageNo ?? 1,
    pageSize: queryParams.value.pageSize ?? 50,
  });
}

/** 9001 签到：本机 OpenHIS.Win 优先，否则由服务端 sign_mac / yb_plugin_url 回退 */
async function handleSignIn() {
  const contractBusNo = requireContractBusNo();
  if (!contractBusNo) return;

  const endpoint = await resolveSignEndpointForSignIn();
  if (!endpoint.fromPlugin) {
    const reason = endpoint.fallbackReason ?? '';
    const message = endpoint.pluginOnline
      ? `本机 HeaSec 获取签到 MAC 失败：${reason}。将改由服务端 sign_mac 完成签到。`
      : `本机读卡插件未启动：${reason}。将改由服务端 sign_mac 完成签到。`;
    ElMessage.warning({
      message,
      duration: 8000,
      showClose: true,
    });
    await runSignIn({ contractBusNo });
    return;
  }

  await runSignIn({
    contractBusNo,
    mac: endpoint.mac,
    ip: endpoint.ip,
  });
}

/** 查看目录下载游标 */
async function handleShowDownloadCursors() {
  const contractBusNo = requireContractBusNo();
  if (!contractBusNo) return;
  await runListCursors(contractBusNo);
}

/** 有效标志对应 Tag 类型 */
function valiFlagTagType(code?: string): 'danger' | 'info' | 'success' {
  if (code === '1') return 'success';
  if (code === '0') return 'danger';
  return 'info';
}

/** 加载医保契约下拉（费用性质主数据） */
async function loadChargeTypes() {
  try {
    chargeTypeOptions.value = await getChargeTypeListApi();
    const firstWithBusNo = chargeTypeOptions.value.find((item) => item.busNo);
    if (!queryParams.value.contractBusNo && firstWithBusNo?.busNo) {
      queryParams.value.contractBusNo = firstWithBusNo.busNo;
    }
  } catch {
    chargeTypeOptions.value = [];
  }
}

onMounted(async () => {
  await loadChargeTypes();
  if (queryParams.value.contractBusNo) {
    await loadList();
  }
});
</script>

<template>
  <Page auto-content-height>
    <TableLayout
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
          <ElAlert :title="CATALOG_1312_SCOPE_HINT" type="info" :closable="false" show-icon />
          <div class="flex flex-wrap items-center gap-1">
            <ElButton type="primary" :loading="refreshLoading" @click="handleRefreshFromPlatform">
              平台刷新(1312)
            </ElButton>
            <ElButton plain :loading="signInLoading" @click="handleSignIn"> 医保签到 </ElButton>
            <ElButton plain :loading="cursorLoading" @click="handleShowDownloadCursors">
              下载游标
            </ElButton>
          </div>
        </div>
      </template>

      <template #listType="{ row }">
        {{ formatCatalogListType(row.listType) }}
      </template>

      <template #chrgitmLv="{ row }">
        {{ formatChrgitmLv(row.chrgitmLv) }}
      </template>

      <template #lmtUsedFlag="{ row }">
        {{ formatLmtUsedFlag(row.lmtUsedFlag) }}
      </template>

      <template #valiFlag="{ row }">
        <ElTag :type="valiFlagTagType(row.valiFlag)" effect="light">
          {{ formatValiFlag(row.valiFlag) }}
        </ElTag>
      </template>
    </TableLayout>

    <AppDialog v-model="cursorDialogVisible" title="目录下载游标" width="720px">
      <ElTable
        v-loading="cursorLoading"
        :data="downloadCursors"
        border
        stripe
        empty-text="暂无下载游标记录"
        max-height="400"
      >
        <ElTableColumn prop="infno" label="接口号" width="90" align="center" />
        <ElTableColumn prop="localVer" label="本地版本" min-width="120" show-overflow-tooltip />
        <ElTableColumn
          prop="lastFileQuryNo"
          label="末次文件查询号"
          min-width="140"
          show-overflow-tooltip
        />
        <ElTableColumn
          prop="lastFilename"
          label="末次文件名"
          min-width="160"
          show-overflow-tooltip
        />
        <ElTableColumn prop="lastSuccessAt" label="末次成功时间" width="170" align="center" />
      </ElTable>
      <template #footer>
        <ElButton @click="cursorDialogVisible = false">关闭</ElButton>
      </template>
    </AppDialog>
  </Page>
</template>
