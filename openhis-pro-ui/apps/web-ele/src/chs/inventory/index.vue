<script setup lang="ts">
/**
 * 医保进销存运维台 —— 本地补传 + 3508–3513 平台查询 + 3507 删除。
 * 业务页触发 3501–3506 异步上报；失败后一律在本页按交易号查流水、编辑 request_json 补传。
 */
import type { LocalInvUploadRecord } from '#/chs/api';

import { computed, onMounted, ref } from 'vue';

import { Page } from '@zxplat/common-ui';
import { formatDateTime } from '@zxplat/utils';

import {
  ElAlert,
  ElButton,
  ElCard,
  ElDatePicker,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElRadioButton,
  ElRadioGroup,
  ElSelect,
  ElTabPane,
  ElTable,
  ElTableColumn,
  ElTabs,
  ElTag,
} from 'element-plus';
import dayjs from 'dayjs';

import { getChargeTypeListApi } from '#/api/core/common';
import {
  deleteInvData3507Api,
  getLocalInvUploadRecordApi,
  listLocalInvUploadRecordsApi,
  retryPendingInvUploadsApi,
  retryUploadWithRequestApi,
} from '#/chs/api';
import PlatformInvQueryPanel from '#/chs/inventory/components/PlatformInvQueryPanel.vue';
import {
  filterInsuranceChargeTypes,
  INVENTORY_OPS_CONTRACT_ALL,
  isInventoryOpsContractAll,
  resolveDefaultInsuranceBusNo,
} from '#/chs/constants/insurance-contract';
import { ensureInsuranceSignIn } from '#/chs/utils/ensure-insurance-sign-in';

defineOptions({ name: 'ChsInventoryOpsPage' });

/** 主 Tab：本地补传 | 平台查询 | 3507 删除 */
type MainTab = 'local' | 'platformQuery' | 'platformDelete3507';

/** 3507 进销存数据类型 */
type InvDataType3507 = '1' | '2' | '3' | '4';

/** 交易号 Tab：ALL=全部 3501–3506（避免空字符串 Radio 失效） */
type InfnoTab = 'ALL' | '3501' | '3502' | '3503' | '3504' | '3505' | '3506';

/** 契约下拉项（含「全部」哨兵；不含自费） */
interface InventoryOpsContractOption {
  busNo: string;
  label: string;
}

const mainTab = ref<MainTab>('local');
const contractBusNo = ref<string>(INVENTORY_OPS_CONTRACT_ALL);
const chargeTypeOptions = ref<InventoryOpsContractOption[]>([]);
/** 平台签到/出站用的默认医保契约（选「全部」时回落） */
const defaultInsuranceBusNo = ref<string>();
const loadingChargeTypes = ref(false);

/**
 * 平台查询/3507/签到用契约：选「全部」时回落首个医保契约。
 */
const gatewayContractBusNo = computed(() => {
  const selected = contractBusNo.value;
  if (!selected || isInventoryOpsContractAll(selected)) {
    return defaultInsuranceBusNo.value;
  }
  return selected;
});

/** 当前展开的交易号（Tab） */
const activeInfno = ref<InfnoTab>('ALL');
const localSourceType = ref('');
/** 默认 FAILED：进页看待补传 */
const localUploadStatus = ref('FAILED');
const localFixmedinsBchno = ref('');
/** 创建时间范围（默认近 7 天） */
const localCreatedRange = ref<[string, string] | undefined>();
const localRecords = ref<LocalInvUploadRecord[]>([]);
const localLoading = ref(false);

const retryDialogVisible = ref(false);
const retrySubmitting = ref(false);
const retryLoadingDetail = ref(false);
const retryTarget = ref<LocalInvUploadRecord | null>(null);
const retryRequestJson = ref('');
const retryResponseJson = ref('');
const retryPlatformMsg = ref('');

/** 查看报文弹窗（只读 request/response，SUCCESS 也可用） */
const viewDialogVisible = ref(false);
const viewLoadingDetail = ref(false);
const viewTarget = ref<LocalInvUploadRecord | null>(null);
const viewRequestJson = ref('');
const viewResponseJson = ref('');
const viewPlatformMsg = ref('');

/** 3507 删除表单 */
const delete3507Bchno = ref('');
const delete3507InvDataType = ref<InvDataType3507>('3');
const delete3507Submitting = ref(false);
const delete3507LastMsg = ref('');

const invDataTypeOptions: Array<{ label: string; value: InvDataType3507 }> = [
  { label: '1 盘存（删 3501）', value: '1' },
  { label: '2 变更（删 3502）', value: '2' },
  { label: '3 采购（删 3503+3504）', value: '3' },
  { label: '4 销售（删 3505+3506）', value: '4' },
];

const infnoTabs: Array<{ label: string; value: InfnoTab }> = [
  { label: '全部', value: 'ALL' },
  { label: '3501 盘存', value: '3501' },
  { label: '3502 变更', value: '3502' },
  { label: '3503 采购', value: '3503' },
  { label: '3504 采购退货', value: '3504' },
  { label: '3505 销售', value: '3505' },
  { label: '3506 销售退货', value: '3506' },
];

const sourceTypeOptions = [
  { label: '全部', value: '' },
  { label: '发药/退药', value: 'DISPENSE' },
  { label: '进货/退供', value: 'INBOUND' },
  { label: '盘点', value: 'STOCKTAKE' },
  { label: '损益', value: 'GAIN_LOSS' },
  { label: '科室领用', value: 'DEPT_ISSUE' },
  { label: '科室退领', value: 'DEPT_ISSUE_RETURN' },
  { label: '清床汇总', value: 'CLEAR_BED_SUMMARY' },
  { label: '初始化', value: 'INIT' },
  { label: '运维手工', value: 'MANUAL' },
];

const uploadStatusOptions = [
  { label: '全部', value: '' },
  { label: 'FAILED 失败', value: 'FAILED' },
  { label: 'PENDING 待传', value: 'PENDING' },
  { label: 'SUCCESS 成功', value: 'SUCCESS' },
  { label: 'SKIPPED 跳过', value: 'SKIPPED' },
];

/**
 * 本地流水默认创建时间：近 7 天。
 */
function defaultCreatedRange(): [string, string] {
  return [
    dayjs().subtract(6, 'day').startOf('day').format('YYYY-MM-DD HH:mm:ss'),
    dayjs().endOf('day').format('YYYY-MM-DD HH:mm:ss'),
  ];
}

onMounted(async () => {
  localCreatedRange.value = defaultCreatedRange();
  loadingChargeTypes.value = true;
  try {
    const chargeTypes = await getChargeTypeListApi();
    // 仅医保契约（自费发药出站也挂省/市，不下拉自费项）
    const insuranceTypes = filterInsuranceChargeTypes(chargeTypes);
    defaultInsuranceBusNo.value = resolveDefaultInsuranceBusNo(insuranceTypes);
    // 下拉：全部 + 省/市/工伤等医保契约
    const opsTypes = insuranceTypes.map((item) => {
      const busNo = (item.busNo ?? '').trim();
      return {
        busNo,
        label: item.label || busNo,
      };
    });
    chargeTypeOptions.value = [{ busNo: INVENTORY_OPS_CONTRACT_ALL, label: '全部' }, ...opsTypes];
    contractBusNo.value = INVENTORY_OPS_CONTRACT_ALL;
    await loadLocalRecords();
  } finally {
    loadingChargeTypes.value = false;
  }
});

/**
 * 契约下拉展示文案：「全部」不加 busNo；其余「名称（busNo）」。
 */
function formatContractOptionLabel(item: InventoryOpsContractOption): string {
  if (isInventoryOpsContractAll(item.busNo)) {
    return item.label;
  }
  return item.busNo ? `${item.label}（${item.busNo}）` : item.label;
}

/**
 * 解析本地流水查询用的契约：全部→不传；其余传 busNo。
 */
function resolveLocalQueryContractBusNo(): string | undefined {
  if (isInventoryOpsContractAll(contractBusNo.value)) {
    return undefined;
  }
  return contractBusNo.value?.trim() || undefined;
}

/**
 * 出站前签到：选「全部」时回落默认医保契约；无可用医保契约则提示。
 */
async function ensureGatewaySignIn(): Promise<boolean> {
  const busNo = gatewayContractBusNo.value;
  if (!busNo) {
    ElMessage.warning('请先配置可用的医保契约（省/市医保）后再做出站操作');
    return false;
  }
  await ensureInsuranceSignIn(busNo);
  return true;
}

/**
 * 切换交易号（3501–3506）后立即查询对应流水。
 */
async function onInfnoTabChange() {
  await loadLocalRecords();
}

/**
 * 创建时间展示：yyyy-MM-dd HH:mm:ss。
 */
function formatCreatedAt(value?: string) {
  if (!value) {
    return '—';
  }
  const formatted = formatDateTime(value);
  if (formatted && formatted !== String(value)) {
    return formatted;
  }
  return String(value).replace('T', ' ').slice(0, 19);
}

/**
 * 状态 Tag 颜色。
 * @param status 上报状态 code
 */
function uploadStatusTagType(status?: string) {
  if (status === 'SUCCESS') return 'success';
  if (status === 'FAILED') return 'danger';
  if (status?.startsWith('SKIPPED')) return 'info';
  return 'warning';
}

/**
 * 仅 FAILED/PENDING 允许补传。
 * @param status 上报状态
 */
function canRetryUpload(status?: string) {
  return status === 'FAILED' || status === 'PENDING';
}

/**
 * 切换契约后刷新本地流水。
 */
async function onContractBusNoChange() {
  localRecords.value = [];
  await loadLocalRecords();
}

/**
 * 按契约 + 交易号 Tab + 其它条件 AND 查询本地流水。
 */
async function loadLocalRecords() {
  localLoading.value = true;
  try {
    const range = localCreatedRange.value;
    localRecords.value = await listLocalInvUploadRecordsApi({
      contractBusNo: resolveLocalQueryContractBusNo(),
      infno: activeInfno.value === 'ALL' ? undefined : activeInfno.value,
      sourceType: localSourceType.value || undefined,
      uploadStatus: localUploadStatus.value || undefined,
      fixmedinsBchno: localFixmedinsBchno.value.trim() || undefined,
      createdFrom: range?.[0],
      createdTo: range?.[1],
    });
  } finally {
    localLoading.value = false;
  }
}

/**
 * 重置筛选为默认（近 7 天 + FAILED）。
 */
async function resetFilters() {
  activeInfno.value = 'ALL';
  localSourceType.value = '';
  localUploadStatus.value = 'FAILED';
  localFixmedinsBchno.value = '';
  localCreatedRange.value = defaultCreatedRange();
  await loadLocalRecords();
}

/**
 * 批量重试 FAILED/PENDING（重放库内 request_json）。
 */
async function handleRetryPending() {
  if (!(await ensureGatewaySignIn())) {
    return;
  }
  const result = await retryPendingInvUploadsApi({ batchSize: 50 });
  ElMessage.success(
    `重试完成：尝试 ${result.attempted ?? 0}，成功 ${result.succeeded ?? 0}，失败 ${result.failed ?? 0}`,
  );
  await loadLocalRecords();
}

/**
 * 从列表/详情行取出报文（兼容 camel / snake；对象则 stringify）。
 */
function pickPayload(
  row: LocalInvUploadRecord | Record<string, unknown> | null | undefined,
  camelKey: 'requestJson' | 'responseJson',
): string {
  if (!row) {
    return '';
  }
  const anyRow = row as Record<string, unknown>;
  const snakeKey = camelKey === 'requestJson' ? 'request_json' : 'response_json';
  const raw = anyRow[camelKey] ?? anyRow[snakeKey];
  if (raw == null) {
    return '';
  }
  if (typeof raw === 'string') {
    return raw;
  }
  try {
    return JSON.stringify(raw, null, 2);
  } catch {
    return String(raw);
  }
}

/**
 * 可解析则美化缩进，便于编辑回显。
 */
function formatJsonForEdit(raw: string): string {
  const text = raw.trim();
  if (!text) {
    return '';
  }
  try {
    return JSON.stringify(JSON.parse(text), null, 2);
  } catch {
    return raw;
  }
}

/**
 * 打开查看报文弹窗：按 id 拉详情，只读展示 request/response。
 * @param row 本地流水行
 */
async function openViewDialog(row: LocalInvUploadRecord) {
  viewTarget.value = row;
  viewPlatformMsg.value = row.platformMsg ?? '';
  viewRequestJson.value = formatJsonForEdit(pickPayload(row, 'requestJson'));
  viewResponseJson.value = formatJsonForEdit(pickPayload(row, 'responseJson'));
  viewDialogVisible.value = true;
  if (row.id == null) {
    return;
  }
  viewLoadingDetail.value = true;
  try {
    const detail = await getLocalInvUploadRecordApi(row.id);
    if (!detail) {
      return;
    }
    viewTarget.value = detail;
    viewPlatformMsg.value = detail.platformMsg ?? viewPlatformMsg.value;
    viewRequestJson.value = formatJsonForEdit(pickPayload(detail, 'requestJson'));
    viewResponseJson.value = formatJsonForEdit(pickPayload(detail, 'responseJson'));
  } finally {
    viewLoadingDetail.value = false;
  }
}

/**
 * 打开补传对话框：按 id 再拉详情，回显 request/response/失败原因。
 * @param row 本地流水行
 */
async function openRetryDialog(row: LocalInvUploadRecord) {
  if (!canRetryUpload(row.uploadStatus)) {
    ElMessage.warning('仅失败/待传记录可补传');
    return;
  }
  retryTarget.value = row;
  retryPlatformMsg.value = row.platformMsg ?? '';
  retryRequestJson.value = formatJsonForEdit(pickPayload(row, 'requestJson'));
  retryResponseJson.value = formatJsonForEdit(pickPayload(row, 'responseJson'));
  retryDialogVisible.value = true;
  if (row.id == null) {
    return;
  }
  retryLoadingDetail.value = true;
  try {
    const detail = await getLocalInvUploadRecordApi(row.id);
    if (!detail) {
      return;
    }
    retryTarget.value = detail;
    retryPlatformMsg.value = detail.platformMsg ?? retryPlatformMsg.value;
    retryRequestJson.value = formatJsonForEdit(pickPayload(detail, 'requestJson'));
    retryResponseJson.value = formatJsonForEdit(pickPayload(detail, 'responseJson'));
    if (!retryRequestJson.value.trim()) {
      ElMessage.warning(
        '库内 request_json 为空（出站前校验失败旧数据可能未落草稿）。请手工补全或重新触发业务上报后再补传。',
      );
    }
  } finally {
    retryLoadingDetail.value = false;
  }
}

/**
 * 提交单条补传：用（可改）request_json 重放。
 */
async function submitRetryUpload() {
  if (!retryTarget.value?.id) {
    ElMessage.warning('未选择补传记录');
    return;
  }
  if (!retryRequestJson.value.trim()) {
    ElMessage.warning('request_json 不能为空（出站前校验失败时需手工填写完整报文后再补传）');
    return;
  }
  if (!(await ensureGatewaySignIn())) {
    return;
  }
  retrySubmitting.value = true;
  try {
    const result = await retryUploadWithRequestApi({
      uploadId: retryTarget.value.id,
      requestJson: retryRequestJson.value,
    });
    retryResponseJson.value = result.responseJson ?? '';
    if (result.succeeded) {
      ElMessage.success('补传成功');
      retryDialogVisible.value = false;
    } else {
      ElMessage.warning(result.platformMsg || '补传失败');
    }
    await loadLocalRecords();
  } finally {
    retrySubmitting.value = false;
  }
}

/**
 * 按本地流水交易号推断 3507 inv_data_type。
 * @param infno 本地上报交易号
 */
function inferInvDataTypeFromInfno(infno?: string): InvDataType3507 {
  if (infno === '3501') {
    return '1';
  }
  if (infno === '3502') {
    return '2';
  }
  if (infno === '3503' || infno === '3504') {
    return '3';
  }
  if (infno === '3505' || infno === '3506') {
    return '4';
  }
  return '3';
}

/**
 * 从本地流水带入删除条件并切到 3507 Tab。
 * @param row 本地上报行
 */
function fillDelete3507FromRow(row: LocalInvUploadRecord) {
  delete3507Bchno.value = (row.fixmedinsBchno ?? '').trim();
  delete3507InvDataType.value = inferInvDataTypeFromInfno(row.infno);
  delete3507LastMsg.value = '';
  mainTab.value = 'platformDelete3507';
  // 吉林 3504 与 3503 共用进货 CIR：从退货流水带入时务必提示
  if (row.infno === '3504' || row.infno === '3503') {
    ElMessage.warning(
      '注意：类型「3 采购」会按该批次号同时删除平台上的 3503 与 3504。进货与退货共用 CIR 时勿误删仍需保留的一侧。',
    );
  }
}

/**
 * 组装 3507 确认框文案（采购/销售大类强调同号一并删除）。
 */
function buildDelete3507ConfirmMessage(bchno: string, typeLabel: string): string {
  let msg = `确认删除平台数据？\n批次号：${bchno}\n类型：${typeLabel}\n此操作不可撤销。`;
  // 类型 3：吉林进货/退货 fixmedins_bchno 同为 CIR，一次删两边
  if (delete3507InvDataType.value === '3') {
    msg +=
      '\n\n【高风险】类型 3 会同时删除该批次下的【3503 采购】与【3504 采购退货】。' +
      '吉林口径下二者常共用进货单号（CIR），删除进货会连退货一起清掉，反之亦然。请确认两侧都不再需要。';
  }
  if (delete3507InvDataType.value === '4') {
    msg +=
      '\n\n【注意】类型 4 会同时删除该批次号下的【3505 销售】与【3506 销售退货】（仅当退货也使用同一 fixmedins_bchno 时才会命中退货）。';
  }
  return msg;
}

/**
 * 提交 3507：确认后按批次号 + 数据类型删平台数据，并同步本地成功流水为 DELETED。
 */
async function submitDelete3507() {
  const gatewayBusNo = gatewayContractBusNo.value;
  if (!gatewayBusNo) {
    ElMessage.warning('请先配置可用的医保契约（省/市医保）后再删除平台数据');
    return;
  }
  const bchno = delete3507Bchno.value.trim();
  if (!bchno) {
    ElMessage.warning('请填写批次流水号 fixmedins_bchno');
    return;
  }
  const typeOpt = invDataTypeOptions.find((o) => o.value === delete3507InvDataType.value);
  const typeLabel = typeOpt?.label ?? delete3507InvDataType.value;
  try {
    await ElMessageBox.confirm(
      buildDelete3507ConfirmMessage(bchno, typeLabel),
      '3507 商品信息删除',
      {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        // 采购大类默认焦点在取消，降低误点确认
        distinguishCancelAndClose: true,
      },
    );
  } catch {
    return;
  }
  delete3507Submitting.value = true;
  delete3507LastMsg.value = '';
  try {
    if (!(await ensureGatewaySignIn())) {
      return;
    }
    const result = await deleteInvData3507Api({
      contractBusNo: gatewayBusNo,
      fixmedinsBchno: bchno,
      invDataType: delete3507InvDataType.value,
    });
    if (result.uploadStatus === 'SUCCESS') {
      ElMessage.success('3507 删除成功');
      delete3507LastMsg.value = `成功：${bchno} / ${typeLabel}`;
      await loadLocalRecords();
    } else {
      const msg = result.platformMsg || '3507 删除失败';
      ElMessage.warning(msg);
      delete3507LastMsg.value = msg;
    }
  } finally {
    delete3507Submitting.value = false;
  }
}
</script>

<template>
  <Page
    auto-content-height
    title="医保进销存运维"
    description="本地补传 3501–3506；平台查询 3508–3513；3507 按批次删除。业务单据页不提供补传。"
  >
    <ElCard shadow="never" class="mb-4">
      <ElForm inline>
        <ElFormItem label="医保契约" required>
          <ElSelect
            v-model="contractBusNo"
            class="!w-72"
            placeholder="请选择医保契约"
            :loading="loadingChargeTypes"
            @change="onContractBusNoChange"
          >
            <ElOption
              v-for="item in chargeTypeOptions"
              :key="item.busNo"
              :label="formatContractOptionLabel(item)"
              :value="item.busNo"
            />
          </ElSelect>
        </ElFormItem>
      </ElForm>
    </ElCard>

    <ElTabs v-model="mainTab">
      <ElTabPane label="本地补传" name="local">
        <ElCard shadow="never">
          <ElAlert
            type="warning"
            :closable="false"
            class="mb-3"
            title="展开 3501–3506 查本地上报流水；FAILED/PENDING 点「补传」改报文重放。行内「删平台」可带入 3507。"
          />

          <div class="mb-3">
            <span class="mr-3 text-sm" style="color: var(--el-text-color-regular)">交易号</span>
            <ElRadioGroup v-model="activeInfno" @change="onInfnoTabChange">
              <ElRadioButton v-for="tab in infnoTabs" :key="tab.value" :label="tab.value">
                {{ tab.label }}
              </ElRadioButton>
            </ElRadioGroup>
          </div>

          <ElForm inline class="mb-3">
            <ElFormItem label="创建时间">
              <ElDatePicker
                v-model="localCreatedRange"
                type="datetimerange"
                range-separator="至"
                start-placeholder="开始"
                end-placeholder="结束"
                value-format="YYYY-MM-DD HH:mm:ss"
                :clearable="true"
                style="width: 360px"
                @change="loadLocalRecords"
              />
            </ElFormItem>
            <ElFormItem label="来源类型">
              <ElSelect
                v-model="localSourceType"
                clearable
                style="width: 140px"
                @change="loadLocalRecords"
              >
                <ElOption
                  v-for="opt in sourceTypeOptions"
                  :key="opt.value || 'all-source'"
                  :label="opt.label"
                  :value="opt.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="上报状态">
              <ElSelect
                v-model="localUploadStatus"
                clearable
                style="width: 140px"
                @change="loadLocalRecords"
              >
                <ElOption
                  v-for="opt in uploadStatusOptions"
                  :key="opt.value || 'all-status'"
                  :label="opt.label"
                  :value="opt.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="批次流水号">
              <ElInput
                v-model="localFixmedinsBchno"
                clearable
                placeholder="支持模糊"
                style="width: 180px"
                @keyup.enter="loadLocalRecords"
                @clear="loadLocalRecords"
              />
            </ElFormItem>
            <ElFormItem>
              <ElButton type="primary" :loading="localLoading" @click="loadLocalRecords">
                查询
              </ElButton>
              <ElButton @click="resetFilters">重置</ElButton>
              <ElButton @click="handleRetryPending">批量重试失败/待传</ElButton>
            </ElFormItem>
          </ElForm>

          <ElTable v-loading="localLoading" :data="localRecords" border stripe max-height="560">
            <ElTableColumn prop="infno" label="交易号" width="80" align="center" />
            <ElTableColumn
              prop="fixmedinsBchno"
              label="批次流水号(busNo)"
              min-width="160"
              show-overflow-tooltip
            />
            <ElTableColumn prop="medListCodg" label="医保编码" min-width="120" align="center" />
            <ElTableColumn prop="sourceType" label="来源" width="120" align="center" />
            <ElTableColumn prop="sourceLineId" label="来源行ID" min-width="120" align="center" />
            <ElTableColumn prop="retryCount" label="重试次数" width="90" align="center" />
            <ElTableColumn label="状态" width="130">
              <template #default="{ row }">
                <ElTag :type="uploadStatusTagType(row.uploadStatus)" size="small">
                  {{ row.uploadStatus }}
                </ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn
              prop="platformMsg"
              label="平台消息"
              min-width="180"
              show-overflow-tooltip
            />
            <ElTableColumn label="创建时间" width="170" align="center">
              <template #default="{ row }">
                {{ formatCreatedAt(row.createdAt) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="操作" width="200" fixed="right" align="center">
              <template #default="{ row }">
                <ElButton link type="primary" @click="openViewDialog(row)">查看</ElButton>
                <ElButton
                  v-if="canRetryUpload(row.uploadStatus)"
                  link
                  type="primary"
                  @click="openRetryDialog(row)"
                >
                  补传
                </ElButton>
                <ElButton
                  v-if="row.fixmedinsBchno"
                  link
                  type="danger"
                  @click="fillDelete3507FromRow(row)"
                >
                  删平台
                </ElButton>
              </template>
            </ElTableColumn>
          </ElTable>
        </ElCard>
      </ElTabPane>

      <ElTabPane label="平台查询 3508–3513" name="platformQuery">
        <ElCard shadow="never">
          <PlatformInvQueryPanel :contract-bus-no="gatewayContractBusNo" />
        </ElCard>
      </ElTabPane>

      <ElTabPane label="3507 删除" name="platformDelete3507">
        <ElCard shadow="never">
          <ElAlert
            type="error"
            :closable="false"
            class="mb-3"
            title="高风险：类型「3 采购」按 fixmedins_bchno 同时删 3503+3504。吉林进货与退货共用进货单号（CIR），删一侧等于两侧都清。类型「4 销售」同理删同号下的 3505+3506。删除前请用 3510/3511 核对。"
          />
          <ElForm label-width="120px" class="max-w-xl">
            <ElFormItem label="批次流水号" required>
              <ElInput
                v-model="delete3507Bchno"
                clearable
                placeholder="fixmedins_bchno；采购侧多为 CIR 进货号"
              />
            </ElFormItem>
            <ElFormItem label="数据类型" required>
              <ElSelect v-model="delete3507InvDataType" style="width: 320px">
                <ElOption
                  v-for="opt in invDataTypeOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem>
              <ElButton type="danger" :loading="delete3507Submitting" @click="submitDelete3507">
                删除平台数据
              </ElButton>
            </ElFormItem>
            <ElFormItem v-if="delete3507LastMsg" label="最近结果">
              <span class="whitespace-pre-wrap break-all">{{ delete3507LastMsg }}</span>
            </ElFormItem>
          </ElForm>
        </ElCard>
      </ElTabPane>
    </ElTabs>

    <ElDialog v-model="viewDialogVisible" title="查看上报报文" width="720px">
      <ElForm v-loading="viewLoadingDetail" label-width="110px">
        <ElFormItem label="交易号">
          <span>{{ viewTarget?.infno }}</span>
        </ElFormItem>
        <ElFormItem label="批次流水号">
          <span>{{ viewTarget?.fixmedinsBchno }}</span>
        </ElFormItem>
        <ElFormItem label="状态">
          <ElTag :type="uploadStatusTagType(viewTarget?.uploadStatus)" size="small">
            {{ viewTarget?.uploadStatus || '—' }}
          </ElTag>
        </ElFormItem>
        <ElFormItem label="平台消息">
          <span class="whitespace-pre-wrap break-all">{{ viewPlatformMsg || '—' }}</span>
        </ElFormItem>
        <ElFormItem label="request_json">
          <ElInput
            v-model="viewRequestJson"
            type="textarea"
            :rows="12"
            readonly
            placeholder="无请求报文"
          />
        </ElFormItem>
        <ElFormItem label="response_json">
          <ElInput
            v-model="viewResponseJson"
            type="textarea"
            :rows="8"
            readonly
            placeholder="无响应报文"
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton type="primary" @click="viewDialogVisible = false">关闭</ElButton>
      </template>
    </ElDialog>

    <ElDialog v-model="retryDialogVisible" title="补传（编辑 request_json 后重放）" width="720px">
      <ElAlert
        type="info"
        :closable="false"
        class="mb-3"
        title="只重放报文，不改业务表。有库内报文则下方回显；出站前校验失败的旧数据可能为空，须手工补全或重新触发业务上报。"
      />
      <ElForm v-loading="retryLoadingDetail" label-width="110px">
        <ElFormItem label="交易号">
          <span>{{ retryTarget?.infno }}</span>
        </ElFormItem>
        <ElFormItem label="批次流水号">
          <span>{{ retryTarget?.fixmedinsBchno }}</span>
        </ElFormItem>
        <ElFormItem label="失败原因">
          <span class="whitespace-pre-wrap break-all text-red-600">{{
            retryPlatformMsg || '—'
          }}</span>
        </ElFormItem>
        <ElFormItem label="request_json" required>
          <ElInput
            v-model="retryRequestJson"
            type="textarea"
            :rows="12"
            placeholder="库内报文回显；可编辑后重放"
          />
        </ElFormItem>
        <ElFormItem label="response_json">
          <ElInput
            v-model="retryResponseJson"
            type="textarea"
            :rows="6"
            readonly
            placeholder="平台响应（只读）"
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="retryDialogVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="retrySubmitting" @click="submitRetryUpload">
          提交补传
        </ElButton>
      </template>
    </ElDialog>
  </Page>
</template>
