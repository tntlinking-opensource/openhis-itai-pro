<script setup lang="ts">
/**
 * 挂号待遇区内联展示工伤备案（9102）摘要，替代原先仅一行文案。
 */
import type { WorkInjuryFilingProbeRow } from './probe-work-injury-filing';

import { computed } from 'vue';

import { ElEmpty, ElTag } from 'element-plus';

defineOptions({ name: 'WorkInjuryBenefitSummary' });

const props = defineProps<{
  errorMessage?: string;
  hasValidFiling?: boolean;
  injureAffirmId?: string;
  loadingHint?: string;
  psnName?: string;
  psnNo?: string;
  rows?: WorkInjuryFilingProbeRow[];
  statusLabel?: string;
}>();

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

const summaryLine = computed(() => {
  const bits: string[] = [];
  if (props.psnName) {
    bits.push(props.psnName);
  }
  if (props.psnNo) {
    bits.push(`人员 ${props.psnNo}`);
  }
  if (props.injureAffirmId) {
    bits.push(`认定 ${props.injureAffirmId}`);
  }
  return bits.join(' · ');
});

const displayRows = computed(() => {
  const rows = [...(props.rows ?? [])];
  rows.sort((a, b) => {
    const av = isCurrentlyValid(a) ? 1 : 0;
    const bv = isCurrentlyValid(b) ? 1 : 0;
    if (av !== bv) {
      return bv - av;
    }
    return dayNum(b.endDate) - dayNum(a.endDate) || dayNum(b.startDate) - dayNum(a.startDate);
  });
  const valid = rows.filter((r) => isCurrentlyValid(r));
  return (valid.length > 0 ? valid : rows).slice(0, 6);
});

const statusTagType = computed(() => {
  if (props.errorMessage) {
    return 'danger';
  }
  if (props.hasValidFiling) {
    return 'success';
  }
  if (props.psnNo || props.statusLabel) {
    return 'warning';
  }
  return 'info';
});

const resolvedStatus = computed(() => {
  if (props.errorMessage) {
    return '查询失败';
  }
  if (props.statusLabel) {
    return props.statusLabel;
  }
  if (props.hasValidFiling) {
    return `审批 ${props.rows?.length ?? 0} 条`;
  }
  if (props.psnNo) {
    return '暂无审批';
  }
  return props.loadingHint || '未查询';
});
</script>

<template>
  <div class="wi-benefit-summary space-y-2 text-sm">
    <div class="flex flex-wrap items-center gap-2">
      <ElTag :type="statusTagType" effect="light" size="small">{{ resolvedStatus }}</ElTag>
      <span v-if="summaryLine" class="text-[var(--el-text-color-regular)]">{{ summaryLine }}</span>
    </div>

    <p v-if="errorMessage" class="text-[var(--el-color-danger)]">
      {{ errorMessage }}
    </p>

    <div
      v-if="displayRows.length"
      class="wi-benefit-summary__rows overflow-hidden rounded border border-[var(--el-border-color-lighter)]"
    >
      <div
        v-for="(row, idx) in displayRows"
        :key="`${row.qualificationId}-${idx}`"
        class="flex flex-wrap items-center gap-x-3 gap-y-1 border-b border-[var(--el-border-color-extra-light)] px-2 py-1.5 last:border-b-0"
      >
        <span class="w-10 shrink-0 text-[var(--el-text-color-secondary)]">
          {{ approveTypeLabel(row.approveType) }}
        </span>
        <span class="min-w-0 flex-1 truncate">
          {{ formatDay(row.startDate) }} ~ {{ formatDay(row.endDate) }}
        </span>
        <ElTag :type="isCurrentlyValid(row) ? 'success' : 'info'" size="small" effect="plain">
          {{ isCurrentlyValid(row) ? '有效' : '过期' }}
        </ElTag>
      </div>
    </div>

    <ElEmpty
      v-else-if="!errorMessage && psnNo && !hasValidFiling"
      description="暂无审批备案"
      :image-size="48"
    />
    <ElEmpty
      v-else-if="!errorMessage && !psnNo && !loadingHint"
      description="选择工伤后将查询备案"
      :image-size="48"
    />
  </div>
</template>
