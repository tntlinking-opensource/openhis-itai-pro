<script setup lang="ts">
import type { TraceAssignmentGroupDraft } from '../services/map-inpatient-trace-assignment';

import { computed, ref, watch } from 'vue';

import { ElButton, ElInput, ElMessage } from 'element-plus';

import AppDialog from '#/components/app-dialog.vue';
import TraceCodeTooltip from '#/components/trace/trace-code-tooltip.vue';

import { formatTraceAssignmentQuantity } from '../services/map-inpatient-trace-assignment';

defineOptions({
  name: 'TraceAssignmentReviseDialog',
});

const props = defineProps<{
  encounterId: string;
  patientName?: string;
  draft: TraceAssignmentGroupDraft | null;
}>();

const emit = defineEmits<{
  /** 确认更正：新追溯码 */
  confirm: [payload: { groupKey: string; previousTraceNo: string; traceNo: string }];
}>();

const visible = defineModel<boolean>({ default: false });

const nextTraceNo = ref('');
const submitting = ref(false);

const dialogSubtitle = computed(() => {
  const name = String(props.patientName ?? '').trim();
  return name ? `患者：${name}` : '';
});

const previousTraceNo = computed(() => String(props.draft?.traceNo ?? '').trim());

const medicationLabel = computed(() => {
  const g = props.draft?.group;
  if (!g) {
    return '—';
  }
  const parts = [g.medicationName, g.specification, g.lotNumber ? `批号${g.lotNumber}` : '']
    .map((s) => String(s ?? '').trim())
    .filter(Boolean);
  return parts.join(' · ') || '—';
});

/** 弹窗打开时回填原码 */
function resetForm() {
  nextTraceNo.value = previousTraceNo.value;
  submitting.value = false;
}

/** 校验并提交更正 */
function handleConfirm() {
  const groupKey = String(props.draft?.group.groupKey ?? '').trim();
  if (!groupKey) {
    ElMessage.warning('缺少汇总组标识，无法更正');
    return;
  }
  const traceNo = nextTraceNo.value.trim();
  if (!traceNo) {
    ElMessage.warning('请录入更正后的追溯码');
    return;
  }
  if (traceNo === previousTraceNo.value) {
    ElMessage.warning('更正后追溯码与原码相同，请修改后再提交');
    return;
  }
  submitting.value = true;
  emit('confirm', {
    groupKey,
    previousTraceNo: previousTraceNo.value,
    traceNo,
  });
  submitting.value = false;
  visible.value = false;
}

watch(visible, (open) => {
  if (open) {
    resetForm();
  }
});
</script>

<template>
  <AppDialog v-model="visible" width="640px" destroy-on-close>
    <template #header>
      <div class="flex flex-wrap items-center gap-2">
        <span class="text-base font-medium text-[var(--el-text-color-primary)]">更正追溯码</span>
        <span v-if="dialogSubtitle" class="text-sm text-[var(--el-text-color-regular)]">
          {{ dialogSubtitle }}
        </span>
      </div>
    </template>

    <div class="flex flex-col gap-3 text-sm text-[var(--el-text-color-primary)]">
      <div class="rounded-md bg-[var(--el-fill-color-light)] px-3 py-2">
        <div class="text-[var(--el-text-color-regular)]">药品信息</div>
        <div class="mt-1 font-medium">{{ medicationLabel }}</div>
        <div class="mt-1 text-[var(--el-text-color-secondary)]">
          汇总单号 {{ draft?.group.summaryNo ?? '—' }} · 发药量
          {{ draft ? formatTraceAssignmentQuantity(draft.group) : '—' }}
        </div>
      </div>

      <div>
        <div class="mb-1 text-[var(--el-text-color-regular)]">原追溯码</div>
        <div
          class="min-h-8 rounded-md border border-solid border-[var(--el-border-color-lighter)] px-2 py-1.5"
        >
          <TraceCodeTooltip v-if="previousTraceNo" :value="previousTraceNo" />
          <span v-else class="text-[var(--el-text-color-secondary)]">（无）</span>
        </div>
      </div>

      <div>
        <div class="mb-1 text-[var(--el-text-color-regular)]">
          更正后追溯码 <span class="text-[var(--el-color-danger)]">*</span>
        </div>
        <ElInput
          v-model="nextTraceNo"
          type="textarea"
          :rows="2"
          clearable
          placeholder="扫码或手工录入（逗号分隔）"
        />
      </div>
    </div>

    <template #footer>
      <ElButton @click="visible = false">取消</ElButton>
      <ElButton type="warning" :loading="submitting" @click="handleConfirm">确认更正</ElButton>
    </template>
  </AppDialog>
</template>
