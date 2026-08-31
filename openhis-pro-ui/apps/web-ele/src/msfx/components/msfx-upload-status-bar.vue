<!--
  码上放心上传状态条：挂在进货/退货等单据单头状态旁。
  IF-MSFX-UPL-01～04：查状态 / 手工上传 / 刷新 / 重传。
-->
<script setup lang="ts">
import type {
  MsfxHisSceneEnum,
  MsfxInboundExpandSmallCodesDto,
  MsfxUploadStatusDto,
} from '#/api/msfx/types';

import { computed, ref, watch } from 'vue';

import { ElButton, ElInput, ElMessage, ElMessageBox, ElTag, ElTooltip } from 'element-plus';

import {
  getMsfxUploadStatusApi,
  manualMsfxUploadApi,
  refreshMsfxBillStatusApi,
  retryMsfxUploadApi,
  uploadAndExpandInboundSmallCodesApi,
} from '#/api/msfx/upload';
import {
  canManualMsfxUpload,
  canRefreshMsfxStatus,
  canRetryMsfxUpload,
  MSFX_HIS_SCENE,
  msfxUploadStatusLabel,
  msfxUploadStatusTagType,
} from '#/msfx/constants';

defineOptions({ name: 'MsfxUploadStatusBar' });

const props = withDefaults(
  defineProps<{
    busNo?: string | null;
    hisSceneEnum: MsfxHisSceneEnum | string;
    /** 102/202 必填：供应商企业 ent_id（可先空，上传时再填） */
    supplierEntId?: string | null;
    /** 可选：上游企业名称（对照/拉单带出，仅展示） */
    supplierEntName?: string | null;
    billTime?: string | null;
    /** 无单号时隐藏操作按钮 */
    compact?: boolean;
    /** 是否需要供应商 ent_id（102/202 为 true；发药/退药 false） */
    needSupplierEntId?: boolean;
  }>(),
  {
    busNo: '',
    supplierEntId: '',
    supplierEntName: '',
    billTime: '',
    compact: false,
    needSupplierEntId: true,
  },
);

const emit = defineEmits<{
  /** 用户补填供应商 ent_id 后回写父组件 */
  'update:supplierEntId': [value: string];
  /** 进货 102 成功并写回支码后，父组件应重拉明细 */
  inboundCodesExpanded: [payload: MsfxInboundExpandSmallCodesDto];
}>();

const loading = ref(false);
const acting = ref(false);
const status = ref<MsfxUploadStatusDto | null>(null);
const moduleUnavailable = ref(false);
const localSupplierEntId = ref('');
/** 默认收起企业 ID 编辑；有值时展开，或用户点「设置」展开 */
const entEditorOpen = ref(false);

const resolvedBusNo = computed(() => String(props.busNo ?? '').trim());
const statusCode = computed(() => status.value?.uploadStatusEnum ?? null);
const statusText = computed(() => msfxUploadStatusLabel(statusCode.value));
const tagType = computed(() => msfxUploadStatusTagType(statusCode.value));
const showBar = computed(() => !moduleUnavailable.value);
const canUpload = computed(
  () => Boolean(resolvedBusNo.value) && canManualMsfxUpload(statusCode.value),
);
const isWarehousePurchaseIn = computed(
  () => props.hisSceneEnum === MSFX_HIS_SCENE.WAREHOUSE_PURCHASE_IN,
);
const canExpandInbound = computed(
  () =>
    isWarehousePurchaseIn.value && Boolean(resolvedBusNo.value) && statusCode.value !== 'SKIPPED',
);
const expandButtonText = computed(() => {
  if (statusCode.value === 'SUCCESS' || statusCode.value === 'PARTIAL') return '展开支码';
  if (statusCode.value === 'QUEUED' || statusCode.value === 'PROCESSING') return '等待并展开支码';
  return '上报并展开支码';
});
const canRefresh = computed(
  () => Boolean(status.value?.id) && canRefreshMsfxStatus(statusCode.value),
);
const canRetry = computed(() => Boolean(status.value?.id) && canRetryMsfxUpload(statusCode.value));

const entIdTrimmed = computed(() => String(localSupplierEntId.value ?? '').trim());
const entNameTrimmed = computed(() => String(props.supplierEntName ?? '').trim());
const hasEntId = computed(() => Boolean(entIdTrimmed.value));
const showEntBlock = computed(
  () => !props.compact && Boolean(resolvedBusNo.value) && props.needSupplierEntId,
);

const entIdDisplayShort = computed(() => {
  const id = entIdTrimmed.value;
  if (!id) return '';
  if (id.length <= 12) return id;
  return `${id.slice(0, 6)}…${id.slice(-4)}`;
});

const entSummaryText = computed(() => {
  if (!hasEntId.value) return '未设置上游企业';
  if (entNameTrimmed.value) return entNameTrimmed.value;
  return entIdDisplayShort.value;
});

watch(
  () => props.supplierEntId,
  (v) => {
    localSupplierEntId.value = String(v ?? '').trim();
    if (localSupplierEntId.value) {
      entEditorOpen.value = false;
    }
  },
  { immediate: true },
);

watch(
  () => [resolvedBusNo.value, props.hisSceneEnum] as const,
  () => {
    void loadStatus();
  },
  { immediate: true },
);

function pickErrMsg(error: unknown): string {
  if (error && typeof error === 'object') {
    const e = error as { message?: string; response?: { data?: { message?: string } } };
    const m = e.response?.data?.message ?? e.message;
    if (m) return String(m);
  }
  return '请求失败';
}

async function loadStatus() {
  status.value = null;
  moduleUnavailable.value = false;
  const busNo = resolvedBusNo.value;
  if (!busNo) return;
  loading.value = true;
  try {
    const res = await getMsfxUploadStatusApi({
      busNo,
      hisSceneEnum: props.hisSceneEnum,
    });
    status.value = (res.data as MsfxUploadStatusDto | null | undefined) ?? null;
    const ent = String(status.value?.supplierEntId ?? '').trim();
    if (ent && !localSupplierEntId.value) {
      localSupplierEntId.value = ent;
      emit('update:supplierEntId', ent);
    }
  } catch (error) {
    const msg = pickErrMsg(error);
    // 模块未装配 / 404：静默隐藏，不打断进货主流程
    if (/404|Not Found|未启用|MSFX_NOT_ENABLED/i.test(msg)) {
      moduleUnavailable.value = true;
      return;
    }
    // 其它错误仅控制台，状态显示未上传
    console.warn('[msfx] getUploadStatus failed', error);
  } finally {
    loading.value = false;
  }
}

async function ensureSupplierEntId(): Promise<string | undefined> {
  // 发药/退药等场景不需要供应商 ent_id
  if (!props.needSupplierEntId) {
    return undefined;
  }
  let ent = String(localSupplierEntId.value || props.supplierEntId || '').trim();
  if (ent) return ent;
  entEditorOpen.value = true;
  try {
    const { value } = await ElMessageBox.prompt(
      '采购入库/退供应商上传须填写码上放心「上游企业 ID」（ent_id），可在供应商对照中维护后自动带出。',
      '填写上游企业 ID',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputPlaceholder: '例如 66c97b8f…',
        inputValidator: (v) => {
          if (!String(v ?? '').trim()) return '不能为空';
          return true;
        },
      },
    );
    ent = String(value ?? '').trim();
    if (!ent) return undefined;
    localSupplierEntId.value = ent;
    emit('update:supplierEntId', ent);
    entEditorOpen.value = false;
    return ent;
  } catch {
    return undefined;
  }
}

async function handleUpload() {
  const busNo = resolvedBusNo.value;
  if (!busNo) {
    ElMessage.warning('请先保存单据生成单号');
    return;
  }
  const supplierEntId = await ensureSupplierEntId();
  if (props.needSupplierEntId && !supplierEntId) return;
  acting.value = true;
  try {
    const res = await manualMsfxUploadApi({
      busNo,
      hisSceneEnum: props.hisSceneEnum,
      supplierEntId: supplierEntId || undefined,
      billTime: String(props.billTime ?? '').trim() || undefined,
    });
    status.value = (res.data as MsfxUploadStatusDto) ?? status.value;
    ElMessage.success(
      status.value?.uploadStatusEnum === 'SKIPPED' ? '已豁免跳过' : '已提交上传（入队）',
    );
  } catch {
    // 业务错误已由 request 拦截器提示，避免重复弹窗
  } finally {
    acting.value = false;
  }
}

async function handleRefresh() {
  const recordId = status.value?.id;
  if (recordId === undefined || recordId === null || recordId === '') {
    ElMessage.warning('无上传记录可刷新');
    return;
  }
  acting.value = true;
  try {
    const res = await refreshMsfxBillStatusApi({ recordId });
    status.value = (res.data as MsfxUploadStatusDto) ?? status.value;
    ElMessage.success('已刷新平台状态');
  } catch {
    // 业务错误已由 request 拦截器提示
  } finally {
    acting.value = false;
  }
}

async function handleRetry() {
  const recordId = status.value?.id;
  if (recordId === undefined || recordId === null || recordId === '') {
    ElMessage.warning('无上传记录可重传');
    return;
  }
  acting.value = true;
  try {
    const res = await retryMsfxUploadApi({ recordId });
    status.value = (res.data as MsfxUploadStatusDto) ?? status.value;
    ElMessage.success('已触发重传');
  } catch {
    // 业务错误已由 request 拦截器提示
  } finally {
    acting.value = false;
  }
}

async function handleUploadAndExpand() {
  const busNo = resolvedBusNo.value;
  if (!busNo) {
    ElMessage.warning('请先保存单据生成单号');
    return;
  }
  const st = statusCode.value;
  const needEnt = st !== 'SUCCESS' && st !== 'PARTIAL' && st !== 'QUEUED' && st !== 'PROCESSING';
  let supplierEntId: string | undefined;
  if (needEnt) {
    supplierEntId = await ensureSupplierEntId();
    if (!supplierEntId) return;
  }
  try {
    await ElMessageBox.confirm(
      '将用本进货单号向码上放心上报采购入库（102），成功后展开为支码并写回明细。可能需要约 1 分钟，请勿反复点击。',
      '上报并展开支码',
      { confirmButtonText: '开始', cancelButtonText: '取消', type: 'info' },
    );
  } catch {
    return;
  }
  acting.value = true;
  try {
    const res = await uploadAndExpandInboundSmallCodesApi({
      busNo,
      hisSceneEnum: props.hisSceneEnum,
      supplierEntId: supplierEntId || localSupplierEntId.value || undefined,
      billTime: String(props.billTime ?? '').trim() || undefined,
    });
    const data = (res.data ?? {}) as MsfxInboundExpandSmallCodesDto;
    await loadStatus();
    const hint = String(data.hint ?? '').trim();
    if (data.wroteToLines) {
      ElMessage.success(hint || `已展开为 ${data.afterCodeCount ?? 0} 个支码`);
      emit('inboundCodesExpanded', data);
    } else if (hint) {
      ElMessage.warning(hint);
    } else {
      ElMessage.success('已处理');
    }
  } catch {
    // 业务错误已由 request 拦截器提示，避免重复弹窗
  } finally {
    acting.value = false;
  }
}

function onSupplierEntBlur() {
  emit('update:supplierEntId', String(localSupplierEntId.value ?? '').trim());
}

function openEntEditor() {
  entEditorOpen.value = true;
}

function collapseEntEditor() {
  onSupplierEntBlur();
  entEditorOpen.value = false;
}

async function copyEntId() {
  const id = entIdTrimmed.value;
  if (!id) return;
  try {
    await navigator.clipboard.writeText(id);
    ElMessage.success('已复制企业 ID');
  } catch {
    ElMessage.info(id);
  }
}

defineExpose({ reload: loadStatus });
</script>

<template>
  <div v-if="showBar" class="msfx-bar">
    <div class="msfx-bar-main">
      <span class="msfx-bar-brand">码上放心</span>
      <ElTag size="small" :type="tagType" effect="light">
        {{ loading ? '查询中…' : statusText }}
      </ElTag>
      <ElTooltip
        v-if="status?.lastMsgInfo"
        :content="status.lastMsgInfo"
        placement="top"
        :show-after="300"
      >
        <span class="msfx-bar-msg">{{ status.lastMsgInfo }}</span>
      </ElTooltip>

      <div v-if="resolvedBusNo" class="msfx-bar-actions">
        <ElButton
          v-if="canUpload && !isWarehousePurchaseIn"
          size="small"
          type="primary"
          :loading="acting"
          @click="handleUpload"
        >
          上传
        </ElButton>
        <ElButton
          v-if="canExpandInbound"
          size="small"
          type="primary"
          :loading="acting"
          @click="handleUploadAndExpand"
        >
          {{ expandButtonText }}
        </ElButton>
        <ElButton v-if="canRefresh" size="small" plain :loading="acting" @click="handleRefresh">
          刷新
        </ElButton>
        <ElButton
          v-if="canRetry"
          size="small"
          type="warning"
          plain
          :loading="acting"
          @click="handleRetry"
        >
          重传
        </ElButton>
      </div>
    </div>

    <div v-if="showEntBlock" class="msfx-bar-ent">
      <template v-if="!entEditorOpen">
        <span class="msfx-bar-ent-label">上游企业</span>
        <ElTooltip
          :content="
            hasEntId
              ? `${entNameTrimmed ? entNameTrimmed + ' · ' : ''}企业 ID：${entIdTrimmed}`
              : '上传进货单前需填写码上放心上游企业 ID（ent_id）'
          "
          placement="top"
          :show-after="200"
        >
          <span
            class="msfx-bar-ent-summary"
            :class="{ 'is-empty': !hasEntId }"
            @click="openEntEditor"
          >
            {{ entSummaryText }}
          </span>
        </ElTooltip>
        <ElButton link type="primary" size="small" @click="openEntEditor">
          {{ hasEntId ? '修改' : '设置' }}
        </ElButton>
        <ElButton v-if="hasEntId" link size="small" @click="copyEntId"> 复制 ID </ElButton>
      </template>
      <template v-else>
        <span class="msfx-bar-ent-label">企业 ID</span>
        <ElInput
          v-model="localSupplierEntId"
          class="msfx-bar-ent-input"
          size="small"
          clearable
          placeholder="码上放心 ent_id，可从供应商对照带出"
          @blur="onSupplierEntBlur"
          @keyup.enter="collapseEntEditor"
        />
        <ElButton size="small" type="primary" plain @click="collapseEntEditor">完成</ElButton>
      </template>
    </div>
  </div>
</template>

<style scoped>
.msfx-bar {
  display: inline-flex;
  flex-direction: column;
  gap: 6px;
  max-width: 100%;
  padding: 6px 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-blank);
  vertical-align: middle;
}

.msfx-bar-main {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.msfx-bar-brand {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  letter-spacing: 0.02em;
}

.msfx-bar-msg {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  cursor: default;
}

.msfx-bar-actions {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-left: auto;
}

.msfx-bar-ent {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 8px;
  min-width: 0;
  padding-top: 2px;
  border-top: 1px dashed var(--el-border-color-extra-light);
}

.msfx-bar-ent-label {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.msfx-bar-ent-summary {
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: var(--el-text-color-primary);
  cursor: pointer;
}

.msfx-bar-ent-summary.is-empty {
  color: var(--el-color-warning);
}

.msfx-bar-ent-input {
  width: min(280px, 56vw);
}
</style>
