<script lang="ts" setup>
import type { FormItemConfig, TableColumn } from '@zxplat-core/table-layout-ui';

import type { EmqConfigListInitResponse, EmqConfigListItem } from '#/api';

import { nextTick, onMounted, ref } from 'vue';

import { Page } from '@zxplat/common-ui';

import { TableLayout } from '@zxplat-core/table-layout-ui';

import { ElButton, ElMessage, ElMessageBox } from 'element-plus';

import {
  collectDeviceDataApi,
  deleteEmqConfigListApi,
  getImecLatestDataApi,
  initEmqConfigListApi,
  pageEmqConfigListApi,
  startEmqConfigListApi,
  startImecCollectApi,
  stopEmqConfigListApi,
} from '#/api';
import StatusEnumTag from '#/components/status-enum-tag.vue';

import EditDialog from './edit-dialog.vue';
import HistoryDialog from './history-dialog.vue';
import ImecHistoryDialog from './imec-history-dialog.vue';
import ImecMonitorDialog from './imec-monitor-dialog.vue';
import MonitorDialog from './monitor-dialog.vue';

const queryParams = ref({
  pageNo: 1,
  pageSize: 50,
  keyword: '',
  name: '',
  deviceTypeEnum: undefined as number | undefined,
  protocolEnum: undefined as number | undefined,
  statusEnum: undefined as number | undefined,
});

const tableData = ref<EmqConfigListItem[]>([]);
const loading = ref(false);
const total = ref(0);
const editDialogVisible = ref(false);
const editFormData = ref<Partial<EmqConfigListItem>>({});
const selectedRows = ref<EmqConfigListItem[]>([]);
const collectingId = ref<null | string>(null);
const tableLayoutRef = ref<InstanceType<typeof TableLayout> | null>(null);
const monitorVisible = ref(false);
const monitorData = ref<any>(null);
const historyVisible = ref(false);
const historyDeviceId = ref('');
const historyDeviceName = ref('');

// iMEC（迈瑞）专用状态
const isMindray = (row: EmqConfigListItem) => row.brand === '迈瑞';
const imecMonitorVisible = ref(false);
const imecMonitorData = ref<any>(null);
const imecMonitorDeviceId = ref('');
const imecHistoryVisible = ref(false);
const imecHistoryDeviceId = ref('');
const imecHistoryDeviceName = ref('');

const tableColumns: TableColumn[] = [
  {
    type: 'selection',
    fixed: 'left',
    width: 50,
    align: 'center',
  },
  {
    type: 'index',
    label: '序号',
    width: 60,
    align: 'center',
  },
  {
    prop: 'busNo',
    label: '设备编号',
    width: 130,
    align: 'center',
    showOverflowTooltip: true,
  },
  {
    prop: 'name',
    label: '设备名称',
    width: 120,
    align: 'center',
    showOverflowTooltip: true,
    slot: 'name',
  },
  {
    prop: 'deviceTypeEnum',
    label: '设备类型',
    width: 100,
    align: 'center',
    showOverflowTooltip: true,
    slot: 'deviceTypeEnum',
  },
  {
    prop: 'brand',
    label: '品牌',
    width: 100,
    align: 'center',
    showOverflowTooltip: true,
  },
  {
    prop: 'modelNumber',
    label: '型号',
    width: 120,
    align: 'center',
    showOverflowTooltip: true,
  },
  {
    prop: 'protocolEnum',
    label: '连接协议',
    width: 100,
    align: 'center',
    slot: 'protocolEnum',
  },
  {
    prop: 'ipAddress',
    label: 'IP地址',
    width: 130,
    align: 'center',
    showOverflowTooltip: true,
  },
  {
    prop: 'port',
    label: '端口',
    width: 80,
    align: 'center',
  },
  {
    prop: 'orgId_dictText',
    label: '所属科室',
    width: 120,
    align: 'center',
    showOverflowTooltip: true,
  },
  {
    prop: 'bedNo',
    label: '床位号',
    width: 90,
    align: 'center',
  },
  {
    prop: 'statusEnum',
    label: '状态',
    width: 90,
    align: 'center',
    slot: 'statusEnum',
  },
  {
    prop: 'connectStatusEnum',
    label: '连接状态',
    width: 100,
    align: 'center',
    slot: 'connectStatusEnum',
  },
  {
    prop: 'logCount',
    label: '采集日志',
    width: 120,
    align: 'center',
    slot: 'logCount',
  },
  {
    prop: 'action',
    label: '操作',
    fixed: 'right',
    slot: 'action',
    width: 280,
    align: 'center',
  },
];

const formItems: FormItemConfig[] = [
  {
    type: 'input',
    prop: 'keyword',
    label: '关键词',
    placeholder: '请输入设备名称或编号',
    clearable: true,
  },
  {
    type: 'select',
    prop: 'deviceTypeEnum',
    label: '设备类型',
    placeholder: '请选择设备类型',
    clearable: true,
    multiple: false,
    options: [
      { label: '监护仪', value: 1 },
      { label: '呼吸机', value: 2 },
      { label: '输液泵', value: 3 },
      { label: '其他', value: 4 },
    ],
  },
  {
    type: 'select',
    prop: 'protocolEnum',
    label: '连接协议',
    placeholder: '请选择连接协议',
    clearable: true,
    multiple: false,
    options: [
      { label: 'HL7', value: 1 },
      { label: 'Socket', value: 2 },
      { label: 'HTTP', value: 3 },
    ],
  },
];

const handleQuery = async (params: Record<string, unknown>) => {
  loading.value = true;
  try {
    const response = await pageEmqConfigListApi({
      pageNo: typeof params.pageNo === 'number' ? params.pageNo : queryParams.value.pageNo,
      pageSize: typeof params.pageSize === 'number' ? params.pageSize : queryParams.value.pageSize,
      keyword:
        typeof params.keyword === 'string' && params.keyword
          ? params.keyword
          : queryParams.value.keyword || undefined,
      name:
        typeof params.name === 'string' && params.name
          ? params.name
          : queryParams.value.name || undefined,
      deviceTypeEnum:
        typeof params.deviceTypeEnum === 'number'
          ? params.deviceTypeEnum
          : queryParams.value.deviceTypeEnum || undefined,
      protocolEnum:
        typeof params.protocolEnum === 'number'
          ? params.protocolEnum
          : queryParams.value.protocolEnum || undefined,
      statusEnum:
        typeof params.statusEnum === 'number'
          ? params.statusEnum
          : queryParams.value.statusEnum || undefined,
    });

    const responseData = response.data;
    if (responseData) {
      tableData.value = responseData.content || [];
      total.value = responseData.totalElements || 0;
    } else {
      tableData.value = [];
      total.value = 0;
    }
  } catch (error) {
    console.error('查询失败:', error);
    ElMessage.error('查询失败，请稍后重试');
    tableData.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
};

const handleReset = () => {
  queryParams.value = {
    pageNo: 1,
    pageSize: 50,
    keyword: '',
    name: '',
    deviceTypeEnum: undefined,
    protocolEnum: undefined,
    statusEnum: undefined,
  };
  handleQuery(queryParams.value);
};

const handlePagination = (pagination: { limit: number; page: number }) => {
  queryParams.value.pageNo = pagination.page;
  queryParams.value.pageSize = pagination.limit;
  handleQuery(queryParams.value);
};

const handleSelectionChange = (selection: EmqConfigListItem[]) => {
  selectedRows.value = selection;
};

const handleSortChange = (_sortInfo: { column: unknown; order: string; prop: string }) => {};

const handleAdd = () => {
  editFormData.value = {};
  editDialogVisible.value = true;
};

const handleEdit = (row: EmqConfigListItem) => {
  editFormData.value = { ...row };
  editDialogVisible.value = true;
};

const handleEnable = async () => {
  const ids = selectedRows.value.map((item) => item.id).filter(Boolean);
  if (ids.length === 0) {
    ElMessage.warning('请先选择要启用的数据');
    return;
  }
  try {
    await startEmqConfigListApi(ids.map(String));
    ElMessage.success('启用成功');
    selectedRows.value = [];
    await handleQuery(queryParams.value);
    await nextTick();
    const gridApi = tableLayoutRef.value?.gridApi;
    if (gridApi) {
      const grid = gridApi.grid || gridApi;
      if (typeof grid.clearCheckboxRow === 'function') {
        grid.clearCheckboxRow();
      }
    }
  } catch (error) {
    console.error('启用失败:', error);
    ElMessage.error('启用失败，请稍后重试');
  }
};

const handleDisable = async () => {
  const ids = selectedRows.value.map((item) => item.id).filter(Boolean);
  if (ids.length === 0) {
    ElMessage.warning('请先选择要停用的数据');
    return;
  }
  try {
    await ElMessageBox.confirm('停用后将清除该设备的所有采集数据，确定要停用吗？', '停用确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    });
    // 迈瑞设备：先停止 iMEC 长连接采集
    const mindrayRows = selectedRows.value.filter((r) => isMindray(r));
    for (const row of mindrayRows) {
      if (row.id) {
        try {
          await stopImecCollectApi(String(row.id));
        } catch {
          // 忽略停止采集失败（可能未在采集）
        }
      }
    }
    await stopEmqConfigListApi(ids.map(String));
    ElMessage.success('停用成功');
    selectedRows.value = [];
    await handleQuery(queryParams.value);
    await nextTick();
    const gridApi = tableLayoutRef.value?.gridApi;
    if (gridApi) {
      const grid = gridApi.grid || gridApi;
      if (typeof grid.clearCheckboxRow === 'function') {
        grid.clearCheckboxRow();
      }
    }
  } catch (error) {
    console.error('停用失败:', error);
    ElMessage.error('停用失败，请稍后重试');
  }
};

const handleStart = async (row: EmqConfigListItem) => {
  if (!row.id) {
    ElMessage.warning('缺少ID');
    return;
  }
  try {
    await startEmqConfigListApi([String(row.id)]);
    ElMessage.success('启用成功');
    await handleQuery(queryParams.value);
  } catch (error) {
    console.error('启用失败:', error);
    ElMessage.error('启用失败，请稍后重试');
  }
};

const handleStop = async (row: EmqConfigListItem) => {
  if (!row.id) {
    ElMessage.warning('缺少ID');
    return;
  }
  try {
    await ElMessageBox.confirm('停用后将清除该设备的所有采集数据，确定要停用吗？', '停用确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    });
    // 迈瑞设备：先停止 iMEC 长连接采集
    if (isMindray(row)) {
      try {
        await stopImecCollectApi(String(row.id));
      } catch {
        // 忽略停止采集失败（可能未在采集）
      }
    }
    await stopEmqConfigListApi([String(row.id)]);
    ElMessage.success('停用成功');
    await handleQuery(queryParams.value);
  } catch (error) {
    if (error !== 'cancel') {
      console.error('停用失败:', error);
      ElMessage.error('停用失败，请稍后重试');
    }
  }
};

const handleDelete = async (row: EmqConfigListItem) => {
  if (!row.id) {
    ElMessage.warning('缺少ID');
    return;
  }
  try {
    await ElMessageBox.confirm(`确定要删除设备【${row.name || row.busNo}】吗？`, '删除确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    });
    await deleteEmqConfigListApi(String(row.id));
    ElMessage.success('删除成功');
    await handleQuery(queryParams.value);
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error);
      ElMessage.error('删除失败，请稍后重试');
    }
  }
};

const handleEditSuccess = () => {
  handleQuery(queryParams.value);
};

/** 采集设备数据（理邦：短连接） */
const handleCollect = async (row: EmqConfigListItem) => {
  if (!row.id) {
    ElMessage.warning('缺少ID');
    return;
  }
  if (row.statusEnum !== 2) {
    ElMessage.warning('请先启用设备后再采集');
    return;
  }
  if (!row.ipAddress || !row.port) {
    ElMessage.warning('该设备未配置IP地址或端口');
    return;
  }
  collectingId.value = String(row.id);
  try {
    const res = await collectDeviceDataApi(String(row.id));
    const data = res.data;
    ElMessage.success(`【${row.name || row.busNo}】采集成功`);
    // 弹出理邦监护仪风格展示弹窗
    monitorData.value = data;
    monitorVisible.value = true;
    await handleQuery(queryParams.value);
  } catch (error: any) {
    // 判断是否为超时错误
    const isTimeout =
      error?.code === 'ECONNABORTED' ||
      error?.message?.includes('timeout') ||
      error?.message?.includes('exceeded');
    if (isTimeout) {
      ElMessage.warning(`【${row.name || row.busNo}】设备暂时无响应，请稍后再试`);
    } else {
      const msg = error?.response?.data?.message || error?.message || '采集失败，请稍后重试';
      ElMessage.error(msg);
    }
  } finally {
    collectingId.value = null;
  }
};

/** 采集 iMEC 设备数据（迈瑞：长连接 WebSocket） */
const handleImecCollect = async (row: EmqConfigListItem) => {
  if (!row.id) {
    ElMessage.warning('缺少ID');
    return;
  }
  if (row.statusEnum !== 2) {
    ElMessage.warning('请先启用设备后再采集');
    return;
  }
  if (!row.ipAddress || !row.port) {
    // 未配置IP/端口，直接打开监护弹窗（可用测试数据调试）
    imecMonitorDeviceId.value = String(row.id);
    imecMonitorData.value = null;
    imecMonitorVisible.value = true;
    return;
  }
  collectingId.value = String(row.id);
  try {
    // 启动长连接采集
    await startImecCollectApi(String(row.id));
    // 获取最新数据
    const res = await getImecLatestDataApi(String(row.id));
    imecMonitorData.value = res.data;
    imecMonitorDeviceId.value = String(row.id);
    imecMonitorVisible.value = true;
    ElMessage.success(`【${row.name || row.busNo}】已启动持续采集`);
    await handleQuery(queryParams.value);
  } catch (error: any) {
    // 采集启动失败（如设备不在线），仍打开监护弹窗，用户可点"注入测试数据"按钮
    imecMonitorDeviceId.value = String(row.id);
    imecMonitorData.value = null;
    imecMonitorVisible.value = true;
    const msg =
      error?.response?.data?.message || error?.message || '设备连接失败，可使用测试数据调试';
    ElMessage.warning(msg);
  } finally {
    collectingId.value = null;
  }
};

/** 查看采集历史（按品牌分流） */
const handleViewHistory = (row: EmqConfigListItem) => {
  if (!row.id) return;
  if (isMindray(row)) {
    imecHistoryDeviceId.value = String(row.id);
    imecHistoryDeviceName.value = row.name || row.busNo || '';
    imecHistoryVisible.value = true;
  } else {
    historyDeviceId.value = String(row.id);
    historyDeviceName.value = row.name || row.busNo || '';
    historyVisible.value = true;
  }
};

/** 连接状态映射 */
const connectStatusMap: Record<number, { text: string; type: string }> = {
  0: { text: '未连接', type: 'info' },
  1: { text: '已连接', type: 'success' },
  2: { text: '连接异常', type: 'danger' },
};

/** 设备类型映射 */
const deviceTypeMap: Record<number, string> = {
  1: '监护仪',
  2: '呼吸机',
  3: '输液泵',
  4: '其他',
};

/** 连接协议映射 */
const protocolMap: Record<number, string> = {
  1: 'HL7',
  2: 'Socket',
  3: 'HTTP',
};

let initPromise: null | Promise<void> = null;
const initOptions = ref<EmqConfigListInitResponse>({} as EmqConfigListInitResponse);

const initPage = async () => {
  if (initPromise) {
    return initPromise;
  }

  initPromise = (async () => {
    try {
      const res = await initEmqConfigListApi();
      const data: EmqConfigListInitResponse = res.data || ({} as EmqConfigListInitResponse);
      initOptions.value = data;
      await handleQuery(queryParams.value);
    } catch (error) {
      console.error('加载初始化数据失败:', error);
      initPromise = null;
      await handleQuery(queryParams.value);
    }
  })();

  return initPromise;
};

onMounted(() => {
  initPage();
});
</script>

<template>
  <Page>
    <TableLayout
      ref="tableLayoutRef"
      :table-data="tableData"
      :loading="loading"
      :total="total"
      :query-params="queryParams"
      :form-items="formItems"
      :table-columns="tableColumns"
      :show-top-query="true"
      :show-pagination="true"
      row-key="id"
      border
      stripe
      size="default"
      @query="handleQuery"
      @reset="handleReset"
      @pagination="handlePagination"
      @selection-change="handleSelectionChange"
      @sort-change="handleSortChange"
    >
      <template #toolbar-actions>
        <ElButton type="primary" @click="handleAdd">新增</ElButton>
        <ElButton type="success" @click="handleEnable">批量启用</ElButton>
        <ElButton type="warning" @click="handleDisable">批量停用</ElButton>
      </template>

      <template #name="{ row }">
        <a
          href="javascript:void(0)"
          class="text-primary cursor-pointer hover:underline"
          @click="handleEdit(row)"
        >
          {{ row.name }}
        </a>
      </template>

      <template #statusEnum="{ row }">
        <StatusEnumTag :status-enum="row.statusEnum" :status-text="row.statusEnum_enumText" />
      </template>

      <template #deviceTypeEnum="{ row }">
        {{ deviceTypeMap[row.deviceTypeEnum] || row.deviceTypeEnum || '-' }}
      </template>

      <template #protocolEnum="{ row }">
        {{ protocolMap[row.protocolEnum] || row.protocolEnum || '-' }}
      </template>

      <template #connectStatusEnum="{ row }">
        <el-tag
          :type="(connectStatusMap[row.connectStatusEnum ?? 0]?.type as any) || 'info'"
          size="small"
        >
          {{ connectStatusMap[row.connectStatusEnum ?? 0]?.text || '未知' }}
        </el-tag>
      </template>

      <template #logCount="{ row }">
        <template v-if="row.logCount > 0">
          <el-tag type="success" size="small" style="margin-right: 6px">
            {{ row.logCount }}条
          </el-tag>
          <a
            href="javascript:void(0)"
            class="text-primary cursor-pointer hover:underline"
            style="font-size: 12px"
            @click="handleViewHistory(row)"
            >查看</a
          >
        </template>
        <template v-else>
          <el-tag type="info" size="small">无记录</el-tag>
        </template>
      </template>

      <template #action="{ row }">
        <ElButton type="primary" link size="small" @click="handleEdit(row)"> 编辑 </ElButton>
        <ElButton
          v-if="row.statusEnum !== 2"
          type="success"
          link
          size="small"
          @click="handleStart(row)"
        >
          启用
        </ElButton>
        <ElButton v-else type="warning" link size="small" @click="handleStop(row)"> 停用 </ElButton>
        <ElButton
          type="primary"
          link
          size="small"
          :loading="collectingId === String(row.id)"
          :disabled="!!collectingId"
          @click="isMindray(row) ? handleImecCollect(row) : handleCollect(row)"
        >
          {{ collectingId === String(row.id) ? '采集中' : '采集' }}
        </ElButton>
        <ElButton type="danger" link size="small" @click="handleDelete(row)"> 删除 </ElButton>
        <ElButton type="info" link size="small" @click="handleViewHistory(row)"> 历史 </ElButton>
      </template>
    </TableLayout>

    <EditDialog
      v-model:visible="editDialogVisible"
      :form-data="editFormData"
      :init-options="initOptions"
      @success="handleEditSuccess"
    />

    <MonitorDialog v-model:visible="monitorVisible" :data="monitorData" />

    <ImecMonitorDialog
      v-model:visible="imecMonitorVisible"
      :device-id="imecMonitorDeviceId"
      :data="imecMonitorData"
    />

    <HistoryDialog
      v-model:visible="historyVisible"
      :device-id="historyDeviceId"
      :device-name="historyDeviceName"
    />

    <ImecHistoryDialog
      v-model:visible="imecHistoryVisible"
      :device-id="imecHistoryDeviceId"
      :device-name="imecHistoryDeviceName"
    />
  </Page>
</template>
