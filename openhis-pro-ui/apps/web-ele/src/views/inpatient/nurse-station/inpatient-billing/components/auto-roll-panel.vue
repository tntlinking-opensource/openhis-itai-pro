<script setup lang="ts">
import type { NurseStationModulePatient } from '../../components/nurse-station-module-patient.types';
import type {
  AutoRollConfigFormModel,
  AutoRollConfigTableRow,
  AutoRollNursingTableRow,
  AutoRollPostedChargeTableRow,
  AutoRollSelectedCatalogItem,
} from '../services/map-nurse-station-auto-roll';

import { computed, ref, watch } from 'vue';

import { ElMessage, ElMessageBox } from 'element-plus';

import {
  httpActivateAutoGenRule,
  httpCreateAutoGenRule,
  httpDeleteAutoGenRule,
  httpListAutoRollConfigs,
  httpListAutoRollPostedCharges,
  httpSuspendAutoGenRule,
  httpUpdateAutoGenRule,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-inpatient-billing.http';
import { httpCancelNurseStationOrderExecutions } from '#/api/inpatient/nurse-station/inpatient-nurse-station-order-cancel-execute.http';
import { useRequest } from '#/hooks/use-request';
import { formatMoneyDisplay } from '#/utils/format/money-scale';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import AutoRollConfigDialog from './auto-roll-config-dialog.vue';
import AutoRollNursingReadonly from './auto-roll-nursing-readonly.vue';
import {
  mapAutoRollConfigRows,
  mapAutoRollNursingRows,
  mapAutoRollPostedChargeRows,
  buildDefaultAutoRollRuleName,
} from '../services/map-nurse-station-auto-roll';

defineOptions({
  name: 'InpatientBillingAutoRollPanel',
});

const props = withDefaults(defineProps<Props>(), {
  canViewBilling: false,
  canOperateBilling: false,
  patient: null,
});

interface Props {
  canViewBilling?: boolean;
  canOperateBilling?: boolean;
  patient?: NurseStationModulePatient | null;
}

interface ConfigSavedPayload {
  form: AutoRollConfigFormModel;
  selectedCatalog: AutoRollSelectedCatalogItem;
  mode: 'create' | 'edit';
  ruleId?: string;
}

/** 默认取消原因（复用取消执行写路径，不弹窗收集） */
const AUTO_ROLL_CANCEL_EXECUTE_REASON = '取消自动计费';

/** 业务日：默认空=全住院期间；选日后展开只展示当天费用 */
const billDate = ref('');
const configRows = ref<AutoRollConfigTableRow[]>([]);
const nursingRows = ref<AutoRollNursingTableRow[]>([]);
/** 计费人护士下拉 */
const nurseOptions = ref<{ label: string; value: string }[]>([]);
const configDialogVisible = ref(false);
const editingRow = ref<AutoRollConfigTableRow | null>(null);
const mutationLoading = ref(false);
/** ruleId → 已滚费用子表行 */
const postedChargesByRuleId = ref<Record<string, AutoRollPostedChargeTableRow[]>>({});
/** 护理 serviceRequestId → 已滚费用子表行 */
const postedChargesByServiceRequestId = ref<Record<string, AutoRollPostedChargeTableRow[]>>({});
/** 正在加载展开记录的 ruleId 集合 */
const postedLoadingRuleIds = ref<Record<string, boolean>>({});
/** 正在加载展开记录的护理 serviceRequestId 集合 */
const postedLoadingServiceRequestIds = ref<Record<string, boolean>>({});
/** 受控展开行（切患者时清空，避免旧 ruleId 错绑新就诊） */
const expandedRuleIds = ref<string[]>([]);
/** 护理受控展开行 */
const expandedNursingServiceRequestIds = ref<string[]>([]);
/** 正在取消的 chargeItemId */
const cancellingChargeItemId = ref<string | null>(null);
/** 配置列表请求世代：切患者 / 重复 reload 时丢弃过期响应 */
let configLoadGeneration = 0;
/** 已滚费用展开请求世代（同理） */
let postedLoadGeneration = 0;

const listConfigsRequest = useRequest(httpListAutoRollConfigs, { manual: true });
/** 过期请求错误由世代守卫吞掉，避免切患者误报「配置不存在」 */
const listPostedChargesRequest = useRequest(httpListAutoRollPostedCharges, {
  manual: true,
  showErrorMessage: false,
});
const cancelExecuteRequest = useRequest(httpCancelNurseStationOrderExecutions, { manual: true });
const createRuleRequest = useRequest(httpCreateAutoGenRule, { manual: true });
const updateRuleRequest = useRequest(httpUpdateAutoGenRule, { manual: true });
const deleteRuleRequest = useRequest(httpDeleteAutoGenRule, { manual: true });
const activateRuleRequest = useRequest(httpActivateAutoGenRule, { manual: true });
const suspendRuleRequest = useRequest(httpSuspendAutoGenRule, { manual: true });

/** 配置表格 loading 态。 */
const tableLoading = computed(() => listConfigsRequest.loading.value);

/** 弹窗保存 loading 态。 */
const dialogSaving = computed(
  () => createRuleRequest.loading.value || updateRuleRequest.loading.value,
);

/** 解析当前患者 encounterId 与 patientId。 */
function resolvePatientIds() {
  const encounterId = preserveApiIdForRequest(props.patient?.id);
  const patientId = preserveApiIdForRequest(props.patient?.patientId);
  return { encounterId, patientId };
}

/** 解析业务日筛选区间（空=不限，查看全住院期间）。 */
function resolvePostedChargeDateRange(): { dateFrom?: string; dateTo?: string } {
  const day = String(billDate.value ?? '').trim();
  if (!day) {
    return {};
  }
  return { dateFrom: day, dateTo: day };
}

/** 清空展开缓存与展开态（患者切换 / 配置刷新后重新拉取）。 */
function clearPostedChargeCache() {
  postedChargesByRuleId.value = {};
  postedChargesByServiceRequestId.value = {};
  postedLoadingRuleIds.value = {};
  postedLoadingServiceRequestIds.value = {};
  expandedRuleIds.value = [];
  expandedNursingServiceRequestIds.value = [];
}

/** 丢弃在途配置/展开请求，并立刻清掉旧患者行，避免展开时 ruleId 与就诊错位。 */
function invalidateAutoRollPanelState() {
  configLoadGeneration += 1;
  postedLoadGeneration += 1;
  configRows.value = [];
  nursingRows.value = [];
  nurseOptions.value = [];
  nurseOptionsReady = false;
  clearPostedChargeCache();
}

/** 当前表格是否仍包含该配置（防止旧行展开打到新就诊）。 */
function isRuleInCurrentConfigRows(ruleId: string): boolean {
  return configRows.value.some((item) => item.ruleId === ruleId);
}

/** 当前护理表是否仍包含该医嘱。 */
function isNursingInCurrentRows(serviceRequestId: string): boolean {
  return nursingRows.value.some((item) => item.serviceRequestId === serviceRequestId);
}

/** 是否已缓存计费人护士下拉（避免每次查询全量拉护士）。 */
let nurseOptionsReady = false;

/** 将后端护士选项映射为下拉（ID 保持 string）。 */
function mapNurseOptions(
  options: { value?: string; label?: string }[] | undefined,
): { label: string; value: string }[] {
  return (options ?? [])
    .map((opt) => {
      const value = preserveApiIdForRequest(opt.value) ?? '';
      const label = String(opt.label ?? '').trim() || value;
      return { value, label };
    })
    .filter((opt) => opt.value.length > 0);
}

/** 加载自动计费配置与护理只读区。 */
async function loadAutoRollConfigs() {
  const generation = ++configLoadGeneration;
  postedLoadGeneration += 1;
  // 先清空旧行与展开态，避免 reload / 切患者竞态下用旧 ruleId 打新就诊
  clearPostedChargeCache();
  configRows.value = [];
  nursingRows.value = [];
  // 护士下拉跨查询复用，不在此清空

  if (!props.canViewBilling || !props.patient) {
    return;
  }
  const { encounterId, patientId } = resolvePatientIds();
  if (!encounterId || !patientId) {
    return;
  }
  const requestedEncounterId = encounterId;
  // 首屏装载护士下拉；后续查询/刷新跳过以减负
  const needNurseOptions = !nurseOptionsReady;
  try {
    const res = await listConfigsRequest.run({
      encounterId,
      patientId,
      billDate: billDate.value || undefined,
      includeNurseOptions: needNurseOptions,
    });
    if (generation !== configLoadGeneration) {
      return;
    }
    if (preserveApiIdForRequest(props.patient?.id) !== requestedEncounterId) {
      return;
    }
    configRows.value = mapAutoRollConfigRows(res.configs);
    // 优先 nursingLevels；兼容仅返回 nursingLevelReadonly 的旧包
    let nursingSource = res.nursingLevels ?? [];
    if (nursingSource.length === 0 && res.nursingLevelReadonly) {
      nursingSource = [res.nursingLevelReadonly];
    }
    nursingRows.value = mapAutoRollNursingRows(nursingSource);
    // 仅首屏或后端显式返回时更新护士下拉
    if (needNurseOptions) {
      nurseOptions.value = mapNurseOptions(res.nurseOptions);
      nurseOptionsReady = nurseOptions.value.length > 0;
    }
  } catch {
    if (generation === configLoadGeneration) {
      configRows.value = [];
      nursingRows.value = [];
    }
  }
}

/** 加载指定配置的已滚费用记录。 */
async function loadPostedChargesForRule(ruleId: string) {
  const { encounterId, patientId } = resolvePatientIds();
  if (!encounterId || !patientId || !ruleId) {
    return;
  }
  // 表格仍是旧患者配置时禁止请求，避免 EX-IP-ARG-401
  if (!isRuleInCurrentConfigRows(ruleId)) {
    return;
  }
  // 仅与「切患者 / 刷新配置」共享世代；同屏多行展开互不抢世代
  const generation = postedLoadGeneration;
  const requestedEncounterId = encounterId;
  const requestedPatientId = patientId;
  const dateRange = resolvePostedChargeDateRange();
  postedLoadingRuleIds.value = {
    ...postedLoadingRuleIds.value,
    [ruleId]: true,
  };
  try {
    const res = await listPostedChargesRequest.run({
      encounterId: requestedEncounterId,
      patientId: requestedPatientId,
      ruleId,
      ...dateRange,
    });
    if (generation !== postedLoadGeneration) {
      return;
    }
    if (preserveApiIdForRequest(props.patient?.id) !== requestedEncounterId) {
      return;
    }
    if (!isRuleInCurrentConfigRows(ruleId)) {
      return;
    }
    postedChargesByRuleId.value = {
      ...postedChargesByRuleId.value,
      [ruleId]: mapAutoRollPostedChargeRows(res.charges),
    };
  } catch (error) {
    if (generation !== postedLoadGeneration) {
      return;
    }
    if (preserveApiIdForRequest(props.patient?.id) !== requestedEncounterId) {
      return;
    }
    const msg =
      (error as { message?: string; msg?: string })?.message ||
      (error as { message?: string; msg?: string })?.msg ||
      '计费记录加载失败，请稍后重试';
    ElMessage.error(msg);
  } finally {
    if (generation === postedLoadGeneration) {
      postedLoadingRuleIds.value = {
        ...postedLoadingRuleIds.value,
        [ruleId]: false,
      };
    }
  }
}

/** 加载指定护理医嘱的已滚费用记录。 */
async function loadPostedChargesForNursing(serviceRequestId: string) {
  const { encounterId, patientId } = resolvePatientIds();
  if (!encounterId || !patientId || !serviceRequestId) {
    return;
  }
  if (!isNursingInCurrentRows(serviceRequestId)) {
    return;
  }
  const generation = postedLoadGeneration;
  const requestedEncounterId = encounterId;
  const dateRange = resolvePostedChargeDateRange();
  postedLoadingServiceRequestIds.value = {
    ...postedLoadingServiceRequestIds.value,
    [serviceRequestId]: true,
  };
  try {
    const res = await listPostedChargesRequest.run({
      encounterId: requestedEncounterId,
      patientId,
      serviceRequestId,
      ...dateRange,
    });
    if (generation !== postedLoadGeneration) {
      return;
    }
    if (preserveApiIdForRequest(props.patient?.id) !== requestedEncounterId) {
      return;
    }
    if (!isNursingInCurrentRows(serviceRequestId)) {
      return;
    }
    postedChargesByServiceRequestId.value = {
      ...postedChargesByServiceRequestId.value,
      [serviceRequestId]: mapAutoRollPostedChargeRows(res.charges),
    };
  } catch (error) {
    if (generation !== postedLoadGeneration) {
      return;
    }
    if (preserveApiIdForRequest(props.patient?.id) !== requestedEncounterId) {
      return;
    }
    const msg =
      (error as { message?: string; msg?: string })?.message ||
      (error as { message?: string; msg?: string })?.msg ||
      '计费记录加载失败，请稍后重试';
    ElMessage.error(msg);
  } finally {
    if (generation === postedLoadGeneration) {
      postedLoadingServiceRequestIds.value = {
        ...postedLoadingServiceRequestIds.value,
        [serviceRequestId]: false,
      };
    }
  }
}

/** 展开行变化时懒加载已滚费用。 */
function handleConfigExpandChange(
  row: AutoRollConfigTableRow,
  expandedRows: AutoRollConfigTableRow[],
) {
  expandedRuleIds.value = expandedRows.map((item) => item.id);
  const expanded = expandedRows.some((item) => item.ruleId === row.ruleId);
  if (!expanded) {
    return;
  }
  if (!isRuleInCurrentConfigRows(row.ruleId)) {
    return;
  }
  // 已缓存则不重复请求
  if (postedChargesByRuleId.value[row.ruleId] !== undefined) {
    return;
  }
  void loadPostedChargesForRule(row.ruleId);
}

/** 护理行展开时懒加载已滚费用。 */
function handleNursingExpandChange(
  row: AutoRollNursingTableRow,
  expandedRows: AutoRollNursingTableRow[],
) {
  expandedNursingServiceRequestIds.value = expandedRows.map((item) => item.id);
  const expanded = expandedRows.some(
    (item) => item.serviceRequestId === row.serviceRequestId,
  );
  if (!expanded) {
    return;
  }
  if (!isNursingInCurrentRows(row.serviceRequestId)) {
    return;
  }
  if (postedChargesByServiceRequestId.value[row.serviceRequestId] !== undefined) {
    return;
  }
  void loadPostedChargesForNursing(row.serviceRequestId);
}

/** 对外暴露刷新入口，供父页切换 Tab / 患者时调用。 */
async function reload() {
  await loadAutoRollConfigs();
}

/** 弹窗打开前确保计费人护士下拉已装载。 */
async function ensureNurseOptionsLoaded() {
  if (nurseOptionsReady && nurseOptions.value.length > 0) {
    return;
  }
  nurseOptionsReady = false;
  await loadAutoRollConfigs();
}

/** 打开新增配置弹窗。 */
async function openCreateDialog() {
  if (!props.canOperateBilling) {
    ElMessage.warning('住院记账仅支持单选患者，请在左侧只勾选 1 位患者');
    return;
  }
  await ensureNurseOptionsLoaded();
  editingRow.value = null;
  configDialogVisible.value = true;
}

/** 打开编辑配置弹窗。 */
async function openEditDialog(row: AutoRollConfigTableRow) {
  if (!props.canOperateBilling) {
    ElMessage.warning('住院记账仅支持单选患者，请在左侧只勾选 1 位患者');
    return;
  }
  await ensureNurseOptionsLoaded();
  editingRow.value = row;
  configDialogVisible.value = true;
}

/** 关闭配置弹窗并清理编辑上下文。 */
function closeConfigDialog() {
  configDialogVisible.value = false;
  editingRow.value = null;
}

/** 处理弹窗保存（新增或更新）。 */
async function handleConfigSaved(payload: ConfigSavedPayload) {
  const { encounterId, patientId } = resolvePatientIds();
  if (!encounterId || !patientId) {
    return;
  }
  const activityId = preserveApiIdForRequest(
    payload.selectedCatalog.catalog.activityId ?? payload.selectedCatalog.catalog.productId,
  );
  const chargeDefinitionId = preserveApiIdForRequest(
    payload.selectedCatalog.catalog.chargeItemDefinitionId,
  );
  if (!activityId || !chargeDefinitionId) {
    return;
  }

  if (payload.mode === 'create') {
    await createRuleRequest.run({
      encounterId,
      patientId,
      ruleName: buildDefaultAutoRollRuleName(payload.selectedCatalog.catalog),
      activityId,
      chargeDefinitionId,
      quantityValue: payload.form.quantityValue,
      remark: payload.form.remark || undefined,
      businessTime: payload.form.businessTime,
      billingPractitionerId: payload.form.billingPractitionerId,
    });
    ElMessage.success('新增自动计费配置成功');
  } else {
    const ruleId = preserveApiIdForRequest(payload.ruleId);
    if (!ruleId) {
      return;
    }
    await updateRuleRequest.run({
      ruleId,
      encounterId,
      patientId,
      ruleName: buildDefaultAutoRollRuleName(payload.selectedCatalog.catalog),
      activityId,
      chargeDefinitionId,
      quantityValue: payload.form.quantityValue,
      remark: payload.form.remark,
      businessTime: payload.form.businessTime,
      billingPractitionerId: payload.form.billingPractitionerId,
    });
    ElMessage.success('更新自动计费配置成功');
  }

  closeConfigDialog();
  await loadAutoRollConfigs();
}

/** 构造启停/删除所需的 mutation 请求体。 */
function buildMutationBody(row: AutoRollConfigTableRow) {
  const { encounterId, patientId } = resolvePatientIds();
  return {
    ruleId: row.ruleId,
    encounterId: encounterId ?? '',
    patientId: patientId ?? '',
  };
}

/** 启用配置。 */
async function handleActivate(row: AutoRollConfigTableRow) {
  if (!props.canOperateBilling) {
    return;
  }
  mutationLoading.value = true;
  try {
    await activateRuleRequest.run(buildMutationBody(row));
    ElMessage.success('已启用配置');
    await loadAutoRollConfigs();
  } finally {
    mutationLoading.value = false;
  }
}

/** 停用配置。 */
async function handleSuspend(row: AutoRollConfigTableRow) {
  if (!props.canOperateBilling) {
    return;
  }
  try {
    await ElMessageBox.confirm(
      `确认停用「${row.activityName}」？停用后不再参与每日滚费，历史已滚费用不会回滚。`,
      '停用配置',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' },
    );
  } catch {
    return;
  }
  mutationLoading.value = true;
  try {
    await suspendRuleRequest.run(buildMutationBody(row));
    ElMessage.success('已停用配置');
    await loadAutoRollConfigs();
  } finally {
    mutationLoading.value = false;
  }
}

/** 删除配置（软删）。 */
async function handleDelete(row: AutoRollConfigTableRow) {
  if (!props.canOperateBilling) {
    return;
  }
  try {
    await ElMessageBox.confirm(
      `确认删除「${row.activityName}」？删除后不再参与滚费，历史已滚费用不会回滚。`,
      '删除配置',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' },
    );
  } catch {
    return;
  }
  mutationLoading.value = true;
  try {
    await deleteRuleRequest.run(buildMutationBody(row));
    ElMessage.success('已删除配置');
    await loadAutoRollConfigs();
  } finally {
    mutationLoading.value = false;
  }
}

/** 取消单笔已滚费用（复用取消执行 IF-IP-CXL-01）。 */
async function handleCancelPostedCharge(
  activityName: string,
  chargeRow: AutoRollPostedChargeTableRow,
  reload: () => Promise<void>,
) {
  if (!props.canOperateBilling || !chargeRow.cancelable || !chargeRow.procedureId) {
    return;
  }
  const { encounterId } = resolvePatientIds();
  if (!encounterId) {
    return;
  }
  try {
    await ElMessageBox.confirm(
      `确认取消「${activityName}」于 ${chargeRow.occurrenceTime} 的自动计费？取消后该日费用将删除，不影响配置与次日滚费。`,
      '取消自动计费',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' },
    );
  } catch {
    return;
  }
  cancellingChargeItemId.value = chargeRow.chargeItemId;
  try {
    await cancelExecuteRequest.run({
      encounterIds: [encounterId],
      procedureIds: [chargeRow.procedureId],
      statusReasonText: AUTO_ROLL_CANCEL_EXECUTE_REASON,
    });
    ElMessage.success('已取消自动计费');
    await reload();
  } finally {
    cancellingChargeItemId.value = null;
  }
}

/** 取消床位配置已滚费用。 */
async function handleCancelConfigPostedCharge(
  configRow: AutoRollConfigTableRow,
  chargeRow: AutoRollPostedChargeTableRow,
) {
  await handleCancelPostedCharge(configRow.activityName, chargeRow, async () => {
    // 清缓存后强制重拉，避免取消后仍展示旧行
    const next = { ...postedChargesByRuleId.value };
    delete next[configRow.ruleId];
    postedChargesByRuleId.value = next;
    await loadPostedChargesForRule(configRow.ruleId);
  });
}

/** 取消护理已滚费用。 */
async function handleCancelNursingPostedCharge(
  nursingRow: AutoRollNursingTableRow,
  chargeRow: AutoRollPostedChargeTableRow,
) {
  await handleCancelPostedCharge(nursingRow.activityName, chargeRow, async () => {
    const next = { ...postedChargesByServiceRequestId.value };
    delete next[nursingRow.serviceRequestId];
    postedChargesByServiceRequestId.value = next;
    await loadPostedChargesForNursing(nursingRow.serviceRequestId);
  });
}

/** 查询按钮：刷新配置与护理只读区。 */
async function handleQuery() {
  if (!props.canOperateBilling) {
    ElMessage.warning('住院记账仅支持单选患者，请在左侧只勾选 1 位患者');
    return;
  }
  await loadAutoRollConfigs();
}

/** 停用行表格样式 class。 */
function resolveRowClassName({ row }: { row: AutoRollConfigTableRow }) {
  return row.isActive ? '' : 'auto-roll-row--inactive';
}

/** 格式化自动计费单价/金额展示。 */
function formatAutoRollUnitPrice(value: number | string | undefined): string {
  if (value === undefined || value === null || value === '') {
    return '—';
  }
  const num = Number(value);
  if (!Number.isFinite(num)) {
    return '—';
  }
  return `￥${formatMoneyDisplay(num)}`;
}

/** 解析配置行对应的已滚费用列表。 */
function resolvePostedCharges(ruleId: string): AutoRollPostedChargeTableRow[] {
  return postedChargesByRuleId.value[ruleId] ?? [];
}

/** 解析配置行展开 loading。 */
function isPostedChargesLoading(ruleId: string): boolean {
  return postedLoadingRuleIds.value[ruleId] === true;
}

watch(
  () => props.patient?.id,
  () => {
    // 先作废在途请求并清空旧行，再拉新患者配置（防止展开明细带着旧 ruleId）
    invalidateAutoRollPanelState();
    void loadAutoRollConfigs();
  },
  { immediate: true },
);

defineExpose({ reload });
</script>

<template>
  <div class="auto-roll-panel flex min-h-0 flex-1 flex-col">
    <ElAlert
      title="下方配置的诊疗项目参与每日自动滚费；护理费取自医嘱校对（不可在此配置）。展开护理/配置行可查看已滚费用，待结算记录可在此取消（不影响配置与次日滚费）。业务日为空查看全住院期间，选择日期后展开仅展示当天费用。"
      type="info"
      :closable="false"
      show-icon
    />

    <AutoRollNursingReadonly
      v-model:bill-date="billDate"
      v-model:expanded-service-request-ids="expandedNursingServiceRequestIds"
      :loading="tableLoading"
      :can-operate-billing="canOperateBilling"
      :nursing-rows="nursingRows"
      :posted-charges-by-service-request-id="postedChargesByServiceRequestId"
      :posted-loading-service-request-ids="postedLoadingServiceRequestIds"
      :cancelling-charge-item-id="cancellingChargeItemId"
      @on-query="handleQuery"
      @expand-change="handleNursingExpandChange"
      @cancel-posted-charge="handleCancelNursingPostedCharge"
    />

    <div class="billing-list-card flex min-h-0 flex-1 flex-col">
      <div class="billing-list-card__toolbar">
        <span class="auto-roll-panel__section-title">自动计费配置</span>
        <div class="billing-list-card__actions">
          <ElButton :loading="tableLoading" @click="handleQuery">刷新</ElButton>
          <ElButton type="primary" :disabled="!canOperateBilling" @click="openCreateDialog">
            新增
          </ElButton>
        </div>
      </div>

      <ElAlert
        v-if="!canViewBilling"
        title="住院记账仅支持单选患者，请在左侧只勾选 1 位患者后查看自动计费"
        type="warning"
        :closable="false"
      />
      <ElAlert
        v-else-if="!canOperateBilling"
        title="患者已办理出院或已结算，仅可查看配置，不可新增或修改"
        type="warning"
        :closable="false"
      />

      <ElTable
        v-loading="tableLoading || mutationLoading"
        :data="configRows"
        border
        stripe
        height="100%"
        row-key="id"
        :expand-row-keys="expandedRuleIds"
        :row-class-name="resolveRowClassName"
        @expand-change="handleConfigExpandChange"
      >
        <ElTableColumn type="expand" width="48" align="center">
          <template #default="{ row }">
            <div class="auto-roll-panel__posted">
              <ElTable
                v-loading="isPostedChargesLoading(row.ruleId)"
                :data="resolvePostedCharges(row.ruleId)"
                border
                size="small"
                empty-text="暂无已滚费用记录"
              >
                <ElTableColumn
                  prop="occurrenceTime"
                  label="业务时间"
                  min-width="160"
                  align="center"
                  show-overflow-tooltip
                />
                <ElTableColumn prop="quantityText" label="数量" width="88" align="center" />
                <ElTableColumn label="单价" width="100" align="right">
                  <template #default="{ row: chargeRow }">
                    {{ formatAutoRollUnitPrice(chargeRow.unitPrice) }}
                  </template>
                </ElTableColumn>
                <ElTableColumn label="金额" width="100" align="right">
                  <template #default="{ row: chargeRow }">
                    {{ formatAutoRollUnitPrice(chargeRow.totalPrice) }}
                  </template>
                </ElTableColumn>
                <ElTableColumn
                  prop="statusText"
                  label="状态"
                  width="96"
                  align="center"
                  show-overflow-tooltip
                />
                <ElTableColumn label="操作" width="100" align="center" fixed="right">
                  <template #default="{ row: chargeRow }">
                    <ElButton
                      link
                      type="danger"
                      :disabled="!canOperateBilling || !chargeRow.cancelable"
                      :loading="cancellingChargeItemId === chargeRow.chargeItemId"
                      @click="handleCancelConfigPostedCharge(row, chargeRow)"
                    >
                      取消
                    </ElButton>
                  </template>
                </ElTableColumn>
              </ElTable>
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="96" align="center">
          <template #default="{ row }">
            <ElTag :type="row.isActive ? 'success' : 'info'" effect="light">
              {{ row.statusText }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="activityName" label="项目名称" min-width="160" show-overflow-tooltip />
        <ElTableColumn prop="quantityValue" label="数量" width="72" align="center" />
        <ElTableColumn
          prop="businessTime"
          label="业务时间"
          width="100"
          align="center"
          show-overflow-tooltip
        />
        <ElTableColumn
          prop="billingPractitionerName"
          label="计费人"
          min-width="96"
          align="center"
          show-overflow-tooltip
        />
        <ElTableColumn
          prop="unitPrice"
          label="价格"
          min-width="96"
          align="right"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ formatAutoRollUnitPrice(row.unitPrice) }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        <ElTableColumn label="操作" width="196" align="center" fixed="right">
          <template #default="{ row }">
            <ElButton
              link
              type="primary"
              :disabled="!canOperateBilling"
              @click="openEditDialog(row)"
            >
              编辑
            </ElButton>
            <ElButton
              v-if="row.isActive"
              link
              type="warning"
              :disabled="!canOperateBilling"
              @click="handleSuspend(row)"
            >
              停用
            </ElButton>
            <ElButton
              v-else
              link
              type="success"
              :disabled="!canOperateBilling"
              @click="handleActivate(row)"
            >
              启用
            </ElButton>
            <ElButton link type="danger" :disabled="!canOperateBilling" @click="handleDelete(row)">
              删除
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
    </div>

    <AutoRollConfigDialog
      v-model:visible="configDialogVisible"
      :can-operate="canOperateBilling"
      :edit-row="editingRow"
      :nurse-options="nurseOptions"
      :patient="patient"
      :saving="dialogSaving"
      @closed="closeConfigDialog"
      @saved="handleConfigSaved"
    />
  </div>
</template>

<style scoped>
.auto-roll-panel {
  gap: 10px;
}

.auto-roll-panel__section-title {
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
}

.auto-roll-panel__posted {
  padding: 8px 12px 12px 36px;
}

.billing-list-card {
  gap: 10px;
}

.billing-list-card__toolbar {
  align-items: center;
  display: flex;
  gap: 8px;
  justify-content: space-between;
  min-height: 32px;
}

.billing-list-card__actions {
  align-items: center;
  display: flex;
  gap: 8px;
}

.billing-list-card :deep(.el-table__header th.el-table__cell) {
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.billing-list-card :deep(.el-table__header-wrapper) {
  border-bottom: 1px solid var(--el-border-color-light);
}

.billing-list-card :deep(.auto-roll-row--inactive) {
  color: var(--el-text-color-secondary);
}
</style>
