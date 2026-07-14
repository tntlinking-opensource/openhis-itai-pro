<script setup lang="ts">
import type { TraceAssignmentGroupDraft } from '../services/map-nurse-station-trace-assignment';

import { computed, nextTick, ref, watch } from 'vue';

import { ElButton, ElInput, ElMessage, ElTable, ElTableColumn } from 'element-plus';

import { httpResolveTraceCodesForPatient } from '#/api/inpatient/nurse-station/inpatient-nurse-station-trace-assignment.http';
import AppDialog from '#/components/app-dialog.vue';
import TraceCodeTooltip from '#/components/trace/trace-code-tooltip.vue';
import { TRACE_BATCH_RESOLVE_MAX, dedupeTraceCodes, extractTraceCodes } from '#/utils/trace';

import {
  collectGroupKeys,
  formatTraceAssignmentQuantity,
  resolveTraceFailReasonLabel,
} from '../services/map-nurse-station-trace-assignment';

defineOptions({
  name: 'InpatientNurseStationTraceAssignmentScanDialog',
});

const props = defineProps<{
  encounterId: string;
  drafts: TraceAssignmentGroupDraft[];
  patientName?: string;
}>();

const emit = defineEmits<{
  applied: [drafts: TraceAssignmentGroupDraft[]];
}>();

const visible = defineModel<boolean>({ default: false });

const scanInput = ref('');
const scanInputRef = ref<InstanceType<typeof ElInput> | null>(null);
const resolving = ref(false);
const failItems = ref<Array<{ traceCode: string; reasonLabel: string }>>([]);
const previewDrafts = ref<TraceAssignmentGroupDraft[]>([]);

const groupKeys = computed(() => collectGroupKeys(props.drafts));

const dialogSubtitle = computed(() => {
  const name = String(props.patientName ?? '').trim();
  return name ? `患者：${name}` : '';
});

/** 弹窗打开时重置输入与预览 */
function resetDialogState() {
  scanInput.value = '';
  failItems.value = [];
  previewDrafts.value = props.drafts.map((draft) => ({
    group: { ...draft.group },
    traceNo: draft.traceNo,
  }));
}

/** 聚焦扫码输入框 */
async function focusScanInput() {
  await nextTick();
  scanInputRef.value?.focus();
}

/** 提交批量追溯码解析 */
async function handleResolveTraceCodes() {
  const encounterId = String(props.encounterId ?? '').trim();
  if (!encounterId) {
    ElMessage.warning('请先选择患者');
    return;
  }
  const { ok, bad } = extractTraceCodes(scanInput.value);
  if (bad.length > 0) {
    ElMessage.warning(`存在非法追溯码：${bad[0]}`);
    return;
  }
  const traceCodes = dedupeTraceCodes(ok);
  if (traceCodes.length === 0) {
    ElMessage.warning('请输入或扫描追溯码');
    return;
  }
  if (traceCodes.length > TRACE_BATCH_RESOLVE_MAX) {
    ElMessage.warning(`单次最多解析 ${TRACE_BATCH_RESOLVE_MAX} 个追溯码`);
    return;
  }
  const keys = groupKeys.value;
  if (keys.length === 0) {
    ElMessage.warning('当前患者无待分配汇总组');
    return;
  }
  resolving.value = true;
  failItems.value = [];
  try {
    const res = await httpResolveTraceCodesForPatient({
      encounterId,
      traceCodes,
      groupKeys: keys,
    });
    const matches = res.matches ?? [];
    const fails = res.failItems ?? [];
    previewDrafts.value = previewDrafts.value.map((draft) => {
      const key = String(draft.group.groupKey ?? '').trim();
      const match = matches.find((item) => String(item.groupKey ?? '').trim() === key);
      if (!match?.matchedTraceCodes?.length) {
        return draft;
      }
      return {
        ...draft,
        traceNo: match.matchedTraceCodes.join(','),
      };
    });
    failItems.value = fails.map((item) => ({
      traceCode: String(item.traceCode ?? '').trim(),
      reasonLabel: resolveTraceFailReasonLabel(item.reasonCode),
    }));
    if (matches.length === 0 && fails.length > 0) {
      ElMessage.warning('追溯码均未能匹配，请核对码池与药品批号');
      return;
    }
    if (fails.length > 0) {
      ElMessage.warning(`部分追溯码未能匹配（${fails.length} 个）`);
      return;
    }
    ElMessage.success('追溯码匹配完成，请确认后应用');
  } catch {
    ElMessage.error('追溯码解析失败，请稍后重试');
  } finally {
    resolving.value = false;
  }
}

/** 确认将预览结果写回主表 */
function handleConfirmApply() {
  emit(
    'applied',
    previewDrafts.value.map((draft) => ({
      group: { ...draft.group },
      traceNo: draft.traceNo,
    })),
  );
  visible.value = false;
}

watch(visible, (open) => {
  if (open) {
    resetDialogState();
    void focusScanInput();
  }
});
</script>

<template>
  <AppDialog v-model="visible" width="920px" destroy-on-close>
    <template #header>
      <div class="trace-scan-dialog__head flex flex-wrap items-center gap-2">
        <span class="text-base font-medium">批量扫描追溯码</span>
        <span v-if="dialogSubtitle" class="text-sm text-[var(--el-text-color-regular)]">
          {{ dialogSubtitle }}
        </span>
      </div>
    </template>
    <div class="trace-scan-dialog flex flex-col gap-3">
      <div class="trace-scan-dialog__toolbar flex items-center gap-2">
        <ElInput
          ref="scanInputRef"
          v-model="scanInput"
          class="trace-scan-dialog__input flex-1"
          clearable
          placeholder="扫描或粘贴追溯码（支持换行或连续扫码）"
          @keyup.enter="handleResolveTraceCodes"
        />
        <ElButton type="primary" :loading="resolving" @click="handleResolveTraceCodes">
          解析匹配
        </ElButton>
      </div>

      <ElTable
        :data="previewDrafts"
        class="order-table order-table--horizontal-only"
        stripe
        max-height="360"
      >
        <ElTableColumn
          prop="group.summaryNo"
          label="汇总单号"
          min-width="120"
          show-overflow-tooltip
        />
        <ElTableColumn
          prop="group.medicationName"
          label="药品"
          min-width="140"
          show-overflow-tooltip
        />
        <ElTableColumn prop="group.lotNumber" label="批号" width="110" show-overflow-tooltip />
        <ElTableColumn label="发药量" width="100" align="center">
          <template #default="{ row }">
            {{ formatTraceAssignmentQuantity(row.group) }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="traceNo" label="匹配追溯码" min-width="200">
          <template #default="{ row }">
            <TraceCodeTooltip :value="row.traceNo" />
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="group.tracePoolPreview"
          label="码池摘要"
          min-width="140"
          show-overflow-tooltip
        />
      </ElTable>

      <ElTable
        v-if="failItems.length > 0"
        :data="failItems"
        class="order-table order-table--horizontal-only"
        stripe
        max-height="160"
      >
        <ElTableColumn prop="traceCode" label="失败追溯码" min-width="180">
          <template #default="{ row }">
            <TraceCodeTooltip :value="row.traceCode" />
          </template>
        </ElTableColumn>
        <ElTableColumn prop="reasonLabel" label="原因" min-width="140" />
      </ElTable>
    </div>

    <template #footer>
      <ElButton @click="visible = false">取消</ElButton>
      <ElButton type="primary" @click="handleConfirmApply">确认应用</ElButton>
    </template>
  </AppDialog>
</template>

<style scoped lang="scss">
@use '../../components/nurse-station-patient-order-list.scss';

.trace-scan-dialog {
  color: var(--el-text-color-primary);
}

.trace-scan-dialog__head .text-base {
  color: var(--el-text-color-primary);
}

.trace-scan-dialog__input :deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--el-border-color) inset;
}
</style>
