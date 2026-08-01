<script setup lang="ts">
import type { TraceAssignmentGroupDraftWithPatient } from '../services/map-inpatient-trace-assignment';

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
} from '../services/map-inpatient-trace-assignment';

defineOptions({
  name: 'InpatientTraceAssignmentScanDialog',
});

const props = defineProps<{
  /** 已选全部患者的待分配草稿（含患者上下文） */
  drafts: TraceAssignmentGroupDraftWithPatient[];
  /** 已选患者人数（展示用） */
  selectedPatientCount?: number;
}>();

const emit = defineEmits<{
  applied: [drafts: TraceAssignmentGroupDraftWithPatient[]];
}>();

const visible = defineModel<boolean>({ default: false });

const scanInput = ref('');
const scanInputRef = ref<InstanceType<typeof ElInput> | null>(null);
const resolving = ref(false);
const failItems = ref<Array<{ traceCode: string; reasonLabel: string }>>([]);
const previewDrafts = ref<TraceAssignmentGroupDraftWithPatient[]>([]);

const dialogSubtitle = computed(() => {
  const count = props.selectedPatientCount ?? new Set(props.drafts.map((d) => d.encounterId)).size;
  if (count <= 0) {
    return '';
  }
  return `已选 ${count} 人 · 共 ${props.drafts.length} 组`;
});

/** 弹窗打开时重置输入与预览（含全部已选患者） */
function resetDialogState() {
  scanInput.value = '';
  failItems.value = [];
  previewDrafts.value = props.drafts.map((draft) => ({
    ...draft,
    group: { ...draft.group },
    traceNo: draft.traceNo,
  }));
}

/** 聚焦扫码输入框 */
async function focusScanInput() {
  await nextTick();
  scanInputRef.value?.focus();
}

/** 按就诊拆分草稿，分别解析后合并结果 */
async function handleResolveTraceCodes() {
  if (previewDrafts.value.length === 0) {
    ElMessage.warning('请先勾选患者');
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

  // 按就诊分组：解析接口按 encounter 维度
  const draftsByEncounter = new Map<string, TraceAssignmentGroupDraftWithPatient[]>();
  for (const draft of previewDrafts.value) {
    const list = draftsByEncounter.get(draft.encounterId) ?? [];
    list.push(draft);
    draftsByEncounter.set(draft.encounterId, list);
  }
  if (draftsByEncounter.size === 0) {
    ElMessage.warning('当前无待分配汇总组');
    return;
  }

  resolving.value = true;
  failItems.value = [];
  try {
    let matchedGroupCount = 0;
    const nextPreview = [...previewDrafts.value];
    const allFails: Array<{ traceCode: string; reasonLabel: string }> = [];

    for (const [encounterId, encounterDrafts] of draftsByEncounter) {
      const keys = collectGroupKeys(encounterDrafts);
      if (keys.length === 0) {
        continue;
      }
      const res = await httpResolveTraceCodesForPatient({
        encounterId,
        traceCodes,
        groupKeys: keys,
      });
      const matches = res.matches ?? [];
      const fails = res.failItems ?? [];
      for (let i = 0; i < nextPreview.length; i += 1) {
        const draft = nextPreview[i];
        if (!draft || draft.encounterId !== encounterId) {
          continue;
        }
        const key = String(draft.group.groupKey ?? '').trim();
        const match = matches.find((item) => String(item.groupKey ?? '').trim() === key);
        if (!match?.matchedTraceCodes?.length) {
          continue;
        }
        matchedGroupCount += 1;
        nextPreview[i] = {
          ...draft,
          traceNo: match.matchedTraceCodes.join(','),
        };
      }
      for (const item of fails) {
        allFails.push({
          traceCode: String(item.traceCode ?? '').trim(),
          reasonLabel: resolveTraceFailReasonLabel(item.reasonCode),
        });
      }
    }

    previewDrafts.value = nextPreview;
    // 失败码去重展示（多患者重复失败同一码只留一条）
    const seenFail = new Set<string>();
    failItems.value = [];
    for (const item of allFails) {
      const key = `${item.traceCode}|${item.reasonLabel}`;
      if (seenFail.has(key)) {
        continue;
      }
      seenFail.add(key);
      failItems.value.push(item);
    }

    if (matchedGroupCount === 0 && failItems.value.length > 0) {
      ElMessage.warning('追溯码均未能匹配，请核对码池与药品批号');
      return;
    }
    if (failItems.value.length > 0) {
      ElMessage.warning(`部分追溯码未能匹配（${failItems.value.length} 个）`);
      return;
    }
    ElMessage.success(`追溯码匹配完成（${matchedGroupCount} 组），请确认后应用`);
  } catch {
    ElMessage.error('追溯码解析失败，请稍后重试');
  } finally {
    resolving.value = false;
  }
}

/** 确认将预览结果写回主表（全部已选患者） */
function handleConfirmApply() {
  emit(
    'applied',
    previewDrafts.value.map((draft) => ({
      ...draft,
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
  <AppDialog v-model="visible" width="980px" destroy-on-close>
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
        <ElTableColumn prop="bedName" label="床号" width="72" align="center" />
        <ElTableColumn prop="patientName" label="患者" min-width="88" show-overflow-tooltip />
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
@use '../styles/order-table.scss';

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
