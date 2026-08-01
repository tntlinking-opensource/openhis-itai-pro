<script setup lang="ts">
import { computed, ref, watch } from 'vue';

import {
  ElAlert,
  ElButton,
  ElEmpty,
  ElMessage,
  ElRadioButton,
  ElRadioGroup,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import AppDialog from '#/components/app-dialog.vue';

import WorkInjury2301ApprovalDialog from './work-injury-2301-approval-dialog.vue';
import {
  probeWorkInjuryFilingViaWin,
  type WorkInjuryFilingProbeResult,
  type WorkInjuryFilingProbeRow,
} from './probe-work-injury-filing';
import { httpWorkInjuryLoadEncounterContext } from './work-injury.http';
import type { WorkInjuryEncounterContextResult } from './work-injury.types';
import { bindWorkInjuryQualification, runWorkInjuryRegister9102 } from './use-work-injury-register';
import { isWorkInjuryPluginOnline } from './work-injury-plugin';

defineOptions({ name: 'WorkInjuryFilingDetailDialog' });

const props = defineProps<{
  encounterId?: string | number;
  contractId?: string | number;
}>();

const emit = defineEmits<{
  completed: [];
  probed: [result: WorkInjuryFilingProbeResult];
}>();

const visible = defineModel<boolean>({ default: false });

const loading = ref(false);
const queryLoading = ref(false);
const context = ref<WorkInjuryEncounterContextResult | null>(null);
const probe = ref<WorkInjuryFilingProbeResult | null>(null);
/** valid=当前有效；op=门诊类型12；all=全部 */
const rowFilter = ref<'valid' | 'op' | 'all'>('valid');
const approvalDialogRef = ref<InstanceType<typeof WorkInjury2301ApprovalDialog> | null>(null);

const anchorReady = computed(
  () =>
    props.encounterId != null &&
    props.encounterId !== '' &&
    props.contractId != null &&
    props.contractId !== '',
);

const todayNum = computed(() => {
  const d = new Date();
  return d.getFullYear() * 10_000 + (d.getMonth() + 1) * 100 + d.getDate();
});

function dayNum(value?: string): number {
  const digits = String(value ?? '').replaceAll(/\D/g, '');
  return digits.length === 8 ? Number(digits) : 0;
}

function formatDay(value?: string): string {
  const digits = String(value ?? '').replaceAll(/\D/g, '');
  if (digits.length !== 8) {
    return value?.trim() || '—';
  }
  return `${digits.slice(0, 4)}-${digits.slice(4, 6)}-${digits.slice(6, 8)}`;
}

function isCurrentlyValid(row: WorkInjuryFilingProbeRow): boolean {
  const end = dayNum(row.endDate);
  const start = dayNum(row.startDate);
  if (end && end < todayNum.value) {
    return false;
  }
  if (start && start > todayNum.value) {
    return false;
  }
  return true;
}

function approveTypeLabel(type?: string): string {
  const code = String(type ?? '').trim();
  if (code === '12') {
    return '门诊';
  }
  if (code === '10') {
    return '转诊';
  }
  if (code === '17') {
    return '辅助器具';
  }
  return code || '—';
}

const hasFiling = computed(
  () => Boolean(context.value?.qualificationValid) || Boolean(probe.value?.hasValidFiling),
);

const statusLabel = computed(() => {
  if (context.value?.approvalStatusLabel) {
    return context.value.approvalStatusLabel;
  }
  if (probe.value?.hasValidFiling) {
    const validCount = (probe.value.rows ?? []).filter((r) => isCurrentlyValid(r)).length;
    return validCount > 0 ? `有效审批 ${validCount} 条` : `历史审批 ${probe.value.rows.length} 条`;
  }
  if (probe.value) {
    return '暂无审批';
  }
  return '未查询';
});

const statusTagType = computed(() => {
  if (hasFiling.value) {
    return 'success';
  }
  if (probe.value || context.value?.needs2301Upload) {
    return 'warning';
  }
  return 'info';
});

const diagnosisSummary = computed(() => {
  const first = probe.value?.diagnoses?.[0];
  if (!first) {
    return '';
  }
  const parts = [first.injuredConclusion, first.injuredPart].filter(Boolean);
  return parts.join(' · ') || first.injureAffirmId;
});

const sortedRows = computed(() => {
  const rows = [...(probe.value?.rows ?? [])];
  rows.sort((a, b) => {
    const av = isCurrentlyValid(a) ? 1 : 0;
    const bv = isCurrentlyValid(b) ? 1 : 0;
    if (av !== bv) {
      return bv - av;
    }
    return dayNum(b.endDate) - dayNum(a.endDate) || dayNum(b.startDate) - dayNum(a.startDate);
  });
  return rows;
});

const validRows = computed(() => sortedRows.value.filter((r) => isCurrentlyValid(r)));
const opRows = computed(() =>
  sortedRows.value.filter((r) => String(r.approveType ?? '').trim() === '12'),
);

const displayRows = computed(() => {
  if (rowFilter.value === 'valid') {
    return validRows.value.length > 0 ? validRows.value : sortedRows.value.slice(0, 8);
  }
  if (rowFilter.value === 'op') {
    return opRows.value;
  }
  return sortedRows.value;
});

const filterHint = computed(() => {
  if (!probe.value?.rows.length) {
    return '';
  }
  if (rowFilter.value === 'valid' && validRows.value.length === 0) {
    return '当前无在有效期内的审批，已临时展示部分历史记录';
  }
  if (rowFilter.value === 'all') {
    return `共 ${probe.value.rows.length} 条，可上滑浏览`;
  }
  return '';
});

const summaryLine = computed(() => {
  const bits: string[] = [];
  if (probe.value?.psnName) {
    bits.push(probe.value.psnName);
  }
  if (displayPsnNo.value && displayPsnNo.value !== '—') {
    bits.push(`人员 ${displayPsnNo.value}`);
  }
  if (displayInjureAffirmId.value && displayInjureAffirmId.value !== '—') {
    bits.push(`认定 ${displayInjureAffirmId.value}`);
  }
  return bits.join(' · ');
});

const displayPsnNo = computed(() => context.value?.psnNo || probe.value?.psnNo || '—');
const displayInjureAffirmId = computed(
  () => context.value?.injureAffirmId || probe.value?.injureAffirmId || '—',
);

const pendingAutoGuide = ref(false);

async function loadAnchorContext() {
  if (!anchorReady.value) {
    context.value = null;
    return;
  }
  try {
    context.value = await httpWorkInjuryLoadEncounterContext(props.encounterId!);
  } catch {
    context.value = null;
  }
}

async function runProbe(options?: { silent?: boolean }) {
  loading.value = true;
  try {
    if (!(await isWorkInjuryPluginOnline())) {
      throw new Error('请启动 OpenHIS.Win 并启用工伤模块');
    }
    const result = await probeWorkInjuryFilingViaWin();
    probe.value = result;
    rowFilter.value = 'valid';
    emit('probed', result);
    if (!options?.silent) {
      const validCount = result.rows.filter((r) => isCurrentlyValid(r)).length;
      ElMessage.success(
        result.hasValidFiling
          ? `查到审批 ${result.rows.length} 条（当前有效 ${validCount}）`
          : '工伤人员已识别，暂无审批备案',
      );
    }
  } catch (error) {
    if (!options?.silent) {
      ElMessage.error(error instanceof Error ? error.message : '工伤备案查询失败');
    }
    throw error;
  } finally {
    loading.value = false;
  }
}

function openAddFiling() {
  if (!anchorReady.value) {
    ElMessage.warning('添加备案需先完成确认挂号（落就诊后再上传 2301）');
    return;
  }
  // nextTick：确保 2301 子组件已挂载
  window.setTimeout(() => {
    approvalDialogRef.value?.openDialog();
  }, 0);
}

async function onApprovalCompleted() {
  await loadAnchorContext();
  emit('completed');
}

async function maybeGuideApprovalAfterLoad() {
  if (!pendingAutoGuide.value) {
    return;
  }
  pendingAutoGuide.value = false;
  if (!anchorReady.value) {
    ElMessage.warning('暂无有效审批：请先确认挂号后再添加备案');
    return;
  }
  if (context.value?.qualificationValid) {
    return;
  }
  // 有当前有效审批但未绑定：自动绑定门诊(12)优先，否则取首条有效
  const preferred =
    validRows.value.find((r) => String(r.approveType ?? '').trim() === '12') ?? validRows.value[0];
  if (preferred?.qualificationId) {
    try {
      await bindWorkInjuryQualification({
        encounterId: props.encounterId!,
        contractId: props.contractId,
        qualificationId: preferred.qualificationId,
      });
      await loadAnchorContext();
      ElMessage.success(`已自动绑定有效审批 ${preferred.qualificationId}`);
      emit('completed');
      return;
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : '自动绑定审批失败');
    }
  }
  ElMessage.warning('暂无有效审批，请填写备案信息');
  openAddFiling();
}

async function loadAll() {
  loading.value = true;
  try {
    await loadAnchorContext();
    if (!context.value?.qualificationValid || !context.value?.psnNo) {
      await runProbe({ silent: true });
    }
    await maybeGuideApprovalAfterLoad();
  } catch {
    // runProbe 已提示
  } finally {
    loading.value = false;
  }
}

watch(visible, (open) => {
  if (open) {
    rowFilter.value = 'valid';
    void loadAll();
  } else {
    pendingAutoGuide.value = false;
  }
});

async function refreshQualification() {
  queryLoading.value = true;
  try {
    if (anchorReady.value) {
      if (!(await isWorkInjuryPluginOnline())) {
        throw new Error('请启动 OpenHIS.Win 并启用工伤模块');
      }
      await runWorkInjuryRegister9102(
        {
          encounterId: props.encounterId!,
          contractId: props.contractId!,
        },
        { silent: true },
      );
      await loadAnchorContext();
      emit('completed');
    }
    await runProbe({ silent: true });
    ElMessage.success('备案查询已刷新');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '工伤审批查询失败');
  } finally {
    queryLoading.value = false;
  }
}

function openDialog(options?: { autoGuideApproval?: boolean }) {
  pendingAutoGuide.value = Boolean(options?.autoGuideApproval);
  visible.value = true;
}

defineExpose({ openDialog, runProbe });
</script>

<template>
  <AppDialog
    v-model="visible"
    title="工伤备案详情"
    width="640px"
    destroy-on-close
    :close-on-click-modal="false"
  >
    <div v-loading="loading" class="wi-filing-dialog">
      <ElAlert
        type="info"
        :closable="false"
        show-icon
        class="!mb-2"
        title="挂号费须自费；添加备案需确认挂号后"
      />

      <template v-if="probe || context">
        <div class="mb-2 flex flex-wrap items-center gap-2">
          <ElTag :type="statusTagType" effect="light" size="small">{{ statusLabel }}</ElTag>
          <span class="text-sm text-[var(--el-text-color-regular)]">{{ summaryLine }}</span>
        </div>

        <div
          v-if="diagnosisSummary"
          class="mb-2 rounded bg-[var(--el-fill-color-light)] px-2 py-1.5 text-sm"
        >
          <span class="text-[var(--el-text-color-secondary)]">认定 · </span>
          {{ diagnosisSummary }}
        </div>

        <div v-if="probe" class="mb-1 flex flex-wrap items-center justify-between gap-2">
          <span class="text-sm text-[var(--el-text-color-secondary)]">
            审批（9102）
            <template v-if="probe.rows.length">
              · 有效 {{ validRows.length }} / 全部 {{ probe.rows.length }}
            </template>
          </span>
          <ElRadioGroup v-if="probe.rows.length" v-model="rowFilter" size="small">
            <ElRadioButton value="valid">有效</ElRadioButton>
            <ElRadioButton value="op">门诊</ElRadioButton>
            <ElRadioButton value="all">全部</ElRadioButton>
          </ElRadioGroup>
        </div>

        <p v-if="filterHint" class="mb-1 text-xs text-[var(--el-text-color-secondary)]">
          {{ filterHint }}
        </p>

        <ElTable
          v-if="probe?.rows.length"
          :data="displayRows"
          size="small"
          border
          max-height="220"
          class="wi-filing-table"
        >
          <ElTableColumn label="类型" width="72">
            <template #default="{ row }">
              {{ approveTypeLabel(row.approveType) }}
            </template>
          </ElTableColumn>
          <ElTableColumn label="有效期" min-width="168">
            <template #default="{ row }">
              {{ formatDay(row.startDate) }} ~ {{ formatDay(row.endDate) }}
            </template>
          </ElTableColumn>
          <ElTableColumn
            prop="qualificationId"
            label="资格 ID"
            min-width="120"
            show-overflow-tooltip
          />
          <ElTableColumn label="状态" width="64">
            <template #default="{ row }">
              <ElTag :type="isCurrentlyValid(row) ? 'success' : 'info'" size="small" effect="plain">
                {{ isCurrentlyValid(row) ? '有效' : '过期' }}
              </ElTag>
            </template>
          </ElTableColumn>
        </ElTable>
        <ElEmpty v-else-if="probe" description="暂无审批，确认挂号后可添加备案" :image-size="56" />
      </template>

      <ElEmpty v-else description="点击「查询备案」读取工伤卡信息" :image-size="56" />
    </div>

    <template #footer>
      <ElButton @click="visible = false">关闭</ElButton>
      <ElButton :loading="queryLoading || loading" type="primary" @click="refreshQualification">
        查询备案
      </ElButton>
      <ElButton
        v-if="!context?.qualificationValid"
        type="warning"
        :disabled="!anchorReady"
        @click="openAddFiling"
      >
        添加备案
      </ElButton>
    </template>
  </AppDialog>

  <WorkInjury2301ApprovalDialog
    v-if="anchorReady"
    ref="approvalDialogRef"
    :encounter-id="encounterId!"
    :contract-id="contractId"
    :injure-affirm-id-hint="displayInjureAffirmId === '—' ? undefined : displayInjureAffirmId"
    @completed="onApprovalCompleted"
  />
</template>
