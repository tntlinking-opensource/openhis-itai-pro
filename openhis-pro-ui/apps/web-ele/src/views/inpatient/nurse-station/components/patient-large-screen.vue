<script setup lang="ts">
import type { PatientItem } from '../types';
import type { OperationTab } from '../workbench-tab.config';

import { computed, onMounted, onUnmounted, ref, watch } from 'vue';

import { Close, FullScreen, Refresh } from '@element-plus/icons-vue';
import dayjs from 'dayjs';

import { formatMoneyYuan } from '#/utils/format/money-scale';

import { encounterStatusToNurseStationLabel } from '../services/map-inpatient-nurse-station-api';
import { doesNurseStationTabRequireBed, OPERATION_TAB } from '../workbench-tab.config';
import {
  formatNurseStationAccountBalance,
  isNurseStationBedUnassigned,
  resolveInpatientAccountBalanceLabel,
  resolveNurseStationPatientBalanceTone,
  resolveNurseStationPatientStatusTone,
} from './nurse-station-module-patient.types';

defineOptions({
  name: 'InpatientNurseStationPatientLargeScreen',
});

/** 大屏卡片底部快捷入口（短标签，避免按钮截断） */
interface LargeScreenShortcut {
  label: string;
  tab: OperationTab;
  title: string;
}

const LARGE_SCREEN_SHORTCUTS: LargeScreenShortcut[] = [
  { label: '校对', title: '医嘱校对', tab: OPERATION_TAB.ORDER_AUDIT },
  { label: '执行', title: '医嘱执行', tab: OPERATION_TAB.ORDER_EXECUTE },
  { label: '护理', title: '护理记录', tab: OPERATION_TAB.NURSING_RECORD },
  { label: '体温', title: '体温单', tab: OPERATION_TAB.TEMPERATURE_SHEET },
  { label: '入出转', title: '入出转管理', tab: OPERATION_TAB.IN_OUT_TRANSFER },
  { label: '记账', title: '住院记账', tab: OPERATION_TAB.INPATIENT_BILLING },
  { label: '费用', title: '费用明细', tab: OPERATION_TAB.COST_DETAILS },
  { label: '皮试', title: '皮试管理', tab: OPERATION_TAB.SKIN_TEST },
  { label: '领药', title: '汇总领药', tab: OPERATION_TAB.DISPENSE_SUMMARY },
  { label: '打印', title: '打印中心', tab: OPERATION_TAB.PRINT_CENTER },
];

interface Props {
  /** 是否展开大屏 */
  modelValue?: boolean;
  /** 与左侧队列同源的患者列表 */
  patients?: PatientItem[];
  /** 总人数（与队列顶栏统计一致） */
  totalPatientCount?: number;
  /** 待校对数 */
  pendingAuditCount?: number;
  /** 待执行数 */
  pendingExecuteCount?: number;
  /** 病区 id → 名称 */
  regionNameById?: Record<string, string>;
  /** 病房 id → 名称 */
  wardNameById?: Record<string, string>;
  /** 已出院结算只读模式：不可选中 */
  recallListMode?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: false,
  patients: () => [],
  totalPatientCount: 0,
  pendingAuditCount: 0,
  pendingExecuteCount: 0,
  regionNameById: () => ({}),
  wardNameById: () => ({}),
  recallListMode: false,
});

const emit = defineEmits<{
  'update:modelValue': [value: boolean];
  /** 刷新队列 */
  refresh: [];
  /** 快捷入口：选中患者并打开对应业务页签 */
  shortcut: [payload: { patientId: string; tab: OperationTab }];
}>();

const clockText = ref('');
let clockTimer: null | ReturnType<typeof setInterval> = null;

/** 大屏可见时同步时钟与键盘收起 */
const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
});

/** 按床号优先、姓名次之排序，便于大屏扫视 */
const displayPatients = computed(() => {
  const list = [...props.patients];
  list.sort((a, b) => {
    const bedA = (a.bedNo ?? '').trim();
    const bedB = (b.bedNo ?? '').trim();
    if (!bedA && bedB) return 1;
    if (bedA && !bedB) return -1;
    if (bedA !== bedB) {
      return bedA.localeCompare(bedB, 'zh-CN', { numeric: true });
    }
    return (a.name ?? '').localeCompare(b.name ?? '', 'zh-CN');
  });
  return list;
});

/** 刷新当前时间文案 */
function tickClock() {
  clockText.value = dayjs().format('YYYY-MM-DD HH:mm:ss');
}

/** 启动时钟定时器 */
function startClock() {
  tickClock();
  if (clockTimer) {
    clearInterval(clockTimer);
  }
  clockTimer = setInterval(tickClock, 1000);
}

/** 停止时钟定时器 */
function stopClock() {
  if (clockTimer) {
    clearInterval(clockTimer);
    clockTimer = null;
  }
}

/** 收起大屏 */
function handleClose() {
  visible.value = false;
}

/** 触发队列刷新（同源接口） */
function handleRefresh() {
  emit('refresh');
}

/** 点击快捷入口：选中患者并切业务页签（暂不收起大屏，流程待定） */
function handleShortcutClick(patient: PatientItem, tab: OperationTab, event: MouseEvent) {
  event.stopPropagation();
  if (props.recallListMode) {
    return;
  }
  emit('shortcut', { patientId: patient.id, tab });
}

/** 未分床时除入出转外快捷入口禁用 */
function isShortcutDisabled(patient: PatientItem, tab: OperationTab): boolean {
  if (props.recallListMode) {
    return true;
  }
  return isNurseStationBedUnassigned(patient.bedNo) && doesNurseStationTabRequireBed(tab);
}

/** 快捷入口 title：未分床提示先分床 */
function resolveShortcutTitle(patient: PatientItem, item: LargeScreenShortcut): string {
  if (isShortcutDisabled(patient, item.tab)) {
    return '请先完成分床后再使用该功能';
  }
  return item.title;
}

/** Esc 收起大屏 */
function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && visible.value) {
    event.preventDefault();
    handleClose();
  }
}

/** 护理级别展示 */
function formatNursingLevel(row: PatientItem): string {
  return (row.level ?? '').trim();
}

/** 年龄展示：优先读模型原文案 */
function formatAgeLabel(row: PatientItem): string {
  const ageText = (row.ageText ?? '').trim();
  if (ageText) {
    return ageText;
  }
  if (row.age > 0) {
    return `${row.age}岁`;
  }
  return '—';
}

/** 住院号展示 */
function formatEncounterBusNo(row: PatientItem): string {
  return (row.encounterBusNo ?? '').trim() || '—';
}

/** 入院时间展示 */
function formatAdmissionAt(row: PatientItem): string {
  const raw = (row.admissionTime ?? row.startTime ?? '').trim();
  if (!raw) {
    return '—';
  }
  const parsed = dayjs(raw);
  if (parsed.isValid()) {
    return parsed.format('YYYY-MM-DD HH:mm');
  }
  return raw;
}

/** 入科时间展示 */
function formatInWardAt(row: PatientItem): string {
  const raw = (row.startTime ?? '').trim();
  if (!raw) {
    return '—';
  }
  const parsed = dayjs(raw);
  if (parsed.isValid()) {
    return parsed.format('YYYY-MM-DD HH:mm');
  }
  return raw;
}

/** 住院天数（入院日计第 1 天） */
function formatInpatientDays(row: PatientItem): string {
  const admissionRaw = (row.admissionTime ?? row.startTime ?? '').trim();
  if (!admissionRaw) {
    return '—';
  }
  const admission = dayjs(admissionRaw);
  if (!admission.isValid()) {
    return '—';
  }
  const endRaw = (row.endTime ?? '').trim();
  const end = endRaw && dayjs(endRaw).isValid() ? dayjs(endRaw) : dayjs();
  const days = end.startOf('day').diff(admission.startOf('day'), 'day') + 1;
  return `${Math.max(1, days)}天`;
}

/** 预缴额展示 */
function formatPaidAmount(row: PatientItem): string {
  if (row.paidAmount == null || row.paidAmount === '') {
    return '—';
  }
  const num = Number(row.paidAmount);
  if (Number.isFinite(num)) {
    return formatMoneyYuan(num);
  }
  return String(row.paidAmount);
}

/** 责任医护空值占位 */
function formatStaffName(value: string | undefined): string {
  return (value ?? '').trim() || '—';
}

/** 就诊状态文案 */
function formatStatusLabel(row: PatientItem): string {
  return encounterStatusToNurseStationLabel(row.statusEnum, row.statusEnumText);
}

/** 余额主文案 */
function formatBalanceLabel(row: PatientItem): string {
  return formatNurseStationAccountBalance(row);
}

/** 余额字段标签 */
function formatBalanceFieldLabel(row: PatientItem): string {
  return resolveInpatientAccountBalanceLabel(row);
}

/** 就诊状态色调 */
function resolveStatusTone(row: PatientItem): string {
  return resolveNurseStationPatientStatusTone(row.statusEnum);
}

/** 余额色调 */
function resolveBalanceTone(row: PatientItem): string {
  const balance = row.availableBalance ?? row.accountBalance;
  return resolveNurseStationPatientBalanceTone(balance, row.lowBalanceWarning);
}

/** 病区名称：优先读模型，再回退 id 反查 */
function resolveRegionName(row: PatientItem): string {
  const fromDto = (row.wardName ?? '').trim();
  if (fromDto) {
    return fromDto;
  }
  return props.regionNameById[row.regionId] || '—';
}

/** 病房名称：优先读模型，再回退 id 反查 */
function resolveHouseName(row: PatientItem): string {
  const fromDto = (row.houseName ?? '').trim();
  if (fromDto) {
    return fromDto;
  }
  return props.wardNameById[row.wardId] || '—';
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      startClock();
      return;
    }
    stopClock();
  },
);

onMounted(() => {
  window.addEventListener('keydown', handleKeydown);
  if (props.modelValue) {
    startClock();
  }
});

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown);
  stopClock();
});
</script>

<template>
  <Transition name="nurse-ls-fade">
    <div v-if="visible" class="nurse-ls" role="dialog" aria-modal="true" aria-label="患者大屏">
      <header class="nurse-ls__header">
        <div class="nurse-ls__brand">
          <ElIcon class="nurse-ls__brand-icon" :size="16"><FullScreen /></ElIcon>
          <div class="nurse-ls__brand-text">
            <h1 class="nurse-ls__title">患者一览</h1>
            <p class="nurse-ls__subtitle">患者一览 · 快捷入口直达业务（操作流程待定）</p>
          </div>
        </div>
        <div class="nurse-ls__stats">
          <div class="nurse-ls-stat nurse-ls-stat--total">
            <span class="nurse-ls-stat__label">在屏</span>
            <span class="nurse-ls-stat__value">{{ displayPatients.length }}</span>
          </div>
          <div class="nurse-ls-stat nurse-ls-stat--all">
            <span class="nurse-ls-stat__label">总人数</span>
            <span class="nurse-ls-stat__value">{{ totalPatientCount }}</span>
          </div>
          <div class="nurse-ls-stat nurse-ls-stat--audit">
            <span class="nurse-ls-stat__label">待校对</span>
            <span class="nurse-ls-stat__value">{{ pendingAuditCount }}</span>
          </div>
          <div class="nurse-ls-stat nurse-ls-stat--execute">
            <span class="nurse-ls-stat__label">待执行</span>
            <span class="nurse-ls-stat__value">{{ pendingExecuteCount }}</span>
          </div>
        </div>
        <div class="nurse-ls__actions">
          <time class="nurse-ls__clock">{{ clockText }}</time>
          <ElButton :icon="Refresh" circle title="刷新队列" @click="handleRefresh" />
          <ElButton :icon="Close" circle title="收起（Esc）" @click="handleClose" />
        </div>
      </header>

      <div class="nurse-ls__body">
        <div v-if="displayPatients.length === 0" class="nurse-ls__empty">
          <ElEmpty description="暂无符合条件的患者" />
        </div>
        <div v-else class="nurse-ls__grid">
          <article
            v-for="row in displayPatients"
            :key="row.id"
            class="nurse-ls-card"
            :class="{
              'is-selected': row.selected,
              'is-unassigned': isNurseStationBedUnassigned(row.bedNo),
              'is-low-balance': row.lowBalanceWarning,
              'is-pending-execute': row.pendingExecute > 0,
              'is-recall': recallListMode,
              [`is-tone-${resolveStatusTone(row)}`]: true,
            }"
          >
            <div class="nurse-ls-card__rail" aria-hidden="true"></div>

            <div class="nurse-ls-card__head">
              <div class="nurse-ls-card__head-row">
                <span
                  class="nurse-ls-card__bed"
                  :class="{ 'is-unassigned': isNurseStationBedUnassigned(row.bedNo) }"
                >
                  {{ row.bedNo?.trim() || '未分床' }}
                </span>
                <span class="nurse-ls-card__status" :class="`is-${resolveStatusTone(row)}`">
                  {{ formatStatusLabel(row) }}
                </span>
              </div>
              <div class="nurse-ls-card__identity">
                <span class="nurse-ls-card__name">{{ row.name }}</span>
                <span class="nurse-ls-card__meta">
                  {{ row.gender || '—' }} · {{ formatAgeLabel(row) }}
                </span>
              </div>
              <div class="nurse-ls-card__tags">
                <span class="nurse-ls-card__tag">{{ resolveRegionName(row) }}</span>
                <span class="nurse-ls-card__tag">{{ resolveHouseName(row) }}</span>
                <span v-if="formatNursingLevel(row)" class="nurse-ls-card__tag is-level">
                  {{ formatNursingLevel(row) }}
                </span>
                <span class="nurse-ls-card__tag">{{ row.feeType || '—' }}</span>
                <span class="nurse-ls-card__tag">住院{{ formatInpatientDays(row) }}</span>
                <span v-if="row.riskText" class="nurse-ls-card__tag is-risk">
                  {{ row.riskText }}
                </span>
              </div>
            </div>

            <div class="nurse-ls-card__money">
              <div class="nurse-ls-card__money-item">
                <span class="nurse-ls-card__k">{{ formatBalanceFieldLabel(row) }}</span>
                <span class="nurse-ls-card__money-val" :class="`is-${resolveBalanceTone(row)}`">
                  {{ formatBalanceLabel(row) }}
                </span>
              </div>
              <div class="nurse-ls-card__money-item">
                <span class="nurse-ls-card__k">预缴</span>
                <span class="nurse-ls-card__money-val">{{ formatPaidAmount(row) }}</span>
              </div>
            </div>

            <div class="nurse-ls-card__info">
              <div class="nurse-ls-card__info-cell">
                <span class="nurse-ls-card__k">医生</span>
                <span class="nurse-ls-card__v">{{ formatStaffName(row.attendingDoctorName) }}</span>
              </div>
              <div class="nurse-ls-card__info-cell">
                <span class="nurse-ls-card__k">护士</span>
                <span class="nurse-ls-card__v">{{ formatStaffName(row.primaryNurseName) }}</span>
              </div>
              <div class="nurse-ls-card__info-cell">
                <span class="nurse-ls-card__k">住院号</span>
                <span class="nurse-ls-card__v">{{ formatEncounterBusNo(row) }}</span>
              </div>
              <div class="nurse-ls-card__info-cell">
                <span class="nurse-ls-card__k">入院</span>
                <span class="nurse-ls-card__v">{{ formatAdmissionAt(row) }}</span>
              </div>
              <div class="nurse-ls-card__info-cell nurse-ls-card__info-cell--full">
                <span class="nurse-ls-card__k">入科</span>
                <span class="nurse-ls-card__v">{{ formatInWardAt(row) }}</span>
              </div>
              <div class="nurse-ls-card__info-cell nurse-ls-card__info-cell--full">
                <span class="nurse-ls-card__k">诊断</span>
                <span class="nurse-ls-card__v is-diag" :title="row.diagnosis">
                  {{ row.diagnosis || '—' }}
                </span>
              </div>
            </div>

            <div class="nurse-ls-card__tasks">
              <span class="nurse-ls-card__task" :class="{ 'is-on': row.pendingAudit > 0 }">
                校对 <b>{{ row.pendingAudit }}</b>
              </span>
              <span
                class="nurse-ls-card__task is-execute"
                :class="{ 'is-on': row.pendingExecute > 0 }"
              >
                执行 <b>{{ row.pendingExecute }}</b>
              </span>
            </div>

            <div class="nurse-ls-card__shortcuts" @click.stop>
              <button
                v-for="item in LARGE_SCREEN_SHORTCUTS"
                :key="item.tab"
                type="button"
                class="nurse-ls-card__shortcut"
                :title="resolveShortcutTitle(row, item)"
                :disabled="isShortcutDisabled(row, item.tab)"
                @click="handleShortcutClick(row, item.tab, $event)"
              >
                {{ item.label }}
              </button>
            </div>
          </article>
        </div>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
.nurse-ls {
  background: #e4e8ee;
  bottom: 0;
  color: var(--el-text-color-primary);
  display: flex;
  flex-direction: column;
  left: 0;
  overflow: hidden;
  position: absolute;
  right: 0;
  top: 0;
  z-index: 30;
}

.nurse-ls__header {
  align-items: center;
  background: #fff;
  border-bottom: 1px solid #c8ced8;
  display: flex;
  flex-wrap: wrap;
  gap: 12px 20px;
  justify-content: space-between;
  padding: 8px 14px;
}

.nurse-ls__brand {
  align-items: center;
  display: flex;
  gap: 8px;
  min-width: 200px;
}

.nurse-ls__brand-icon {
  color: var(--el-color-primary);
  flex-shrink: 0;
}

.nurse-ls__title {
  color: var(--el-text-color-primary);
  font-size: 15px;
  font-weight: 700;
  line-height: 1.25;
  margin: 0;
}

.nurse-ls__subtitle {
  color: var(--el-text-color-regular);
  font-size: 12px;
  margin: 1px 0 0;
}

.nurse-ls__stats {
  display: flex;
  flex: 1;
  flex-wrap: wrap;
  gap: 0;
  justify-content: center;
  min-width: 0;
}

.nurse-ls-stat {
  align-items: center;
  display: flex;
  gap: 8px;
  min-width: 84px;
  padding: 2px 14px;
}

.nurse-ls-stat + .nurse-ls-stat {
  border-left: 1px solid #c8ced8;
}

.nurse-ls-stat__label {
  color: var(--el-text-color-regular);
  font-size: 12px;
}

.nurse-ls-stat__value {
  font-size: 18px;
  font-variant-numeric: tabular-nums;
  font-weight: 700;
  line-height: 1;
}

.nurse-ls-stat--total .nurse-ls-stat__value,
.nurse-ls-stat--all .nurse-ls-stat__value {
  color: var(--el-color-primary);
}

.nurse-ls-stat--audit .nurse-ls-stat__value {
  color: var(--el-color-warning);
}

.nurse-ls-stat--execute .nurse-ls-stat__value {
  color: var(--el-color-danger);
}

.nurse-ls__actions {
  align-items: center;
  display: flex;
  gap: 8px;
}

.nurse-ls__clock {
  color: var(--el-text-color-primary);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  font-weight: 500;
  margin-right: 4px;
}

.nurse-ls__body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 12px;
}

.nurse-ls__empty {
  align-items: center;
  display: flex;
  height: 100%;
  justify-content: center;
  min-height: 280px;
}

.nurse-ls__grid {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(auto-fill, minmax(308px, 1fr));
}

.nurse-ls-card {
  background: #fff;
  border: 1px solid #b8bfcb;
  border-radius: 10px;
  box-shadow: 0 2px 8px rgb(15 23 42 / 6%);
  color: inherit;
  cursor: default;
  display: flex;
  flex-direction: column;
  gap: 8px;
  outline: none;
  overflow: hidden;
  padding: 0 0 8px;
  position: relative;
  text-align: left;
}

.nurse-ls-card.is-selected {
  background: rgb(59 130 246 / 4%);
  border-color: var(--el-color-primary);
  box-shadow:
    0 0 0 1px var(--el-color-primary),
    0 4px 14px rgb(37 99 235 / 12%);
}

.nurse-ls-card.is-unassigned {
  border-color: var(--el-color-warning);
}

.nurse-ls-card.is-low-balance {
  border-color: var(--el-color-danger);
}

.nurse-ls-card.is-pending-execute {
  background: #fff7f7;
  border-color: #f3a3a3;
}

.nurse-ls-card.is-pending-execute .nurse-ls-card__rail {
  background: var(--el-color-danger);
}

.nurse-ls-card.is-pending-execute .nurse-ls-card__head {
  background: linear-gradient(180deg, #fff1f1 0%, #fff7f7 100%);
  border-bottom-color: #f5c2c2;
}

.nurse-ls-card.is-pending-execute.is-selected {
  background: #fff1f1;
  border-color: var(--el-color-danger);
  box-shadow:
    0 0 0 1px var(--el-color-danger),
    0 4px 14px rgb(245 108 108 / 16%);
}

.nurse-ls-card.is-recall {
  cursor: default;
}

.nurse-ls-card__rail {
  background: var(--el-color-primary);
  bottom: 0;
  left: 0;
  position: absolute;
  top: 0;
  width: 3px;
}

.nurse-ls-card.is-tone-registered .nurse-ls-card__rail {
  background: var(--el-color-info);
}

.nurse-ls-card.is-tone-in-progress .nurse-ls-card__rail {
  background: var(--el-color-success);
}

.nurse-ls-card.is-tone-on-hold .nurse-ls-card__rail,
.nurse-ls-card.is-tone-pending .nurse-ls-card__rail {
  background: var(--el-color-warning);
}

.nurse-ls-card.is-tone-discharged .nurse-ls-card__rail,
.nurse-ls-card.is-tone-default .nurse-ls-card__rail {
  background: #94a3b8;
}

.nurse-ls-card__head {
  background: linear-gradient(180deg, #f7f9fc 0%, #fff 100%);
  border-bottom: 1px solid #e2e6ed;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 12px 8px 14px;
}

.nurse-ls-card__head-row {
  align-items: center;
  display: flex;
  gap: 8px;
  justify-content: space-between;
}

.nurse-ls-card__bed {
  color: var(--el-color-primary);
  font-size: 18px;
  font-weight: 800;
  letter-spacing: 0.01em;
  line-height: 1.15;
}

.nurse-ls-card__bed.is-unassigned {
  color: var(--el-color-warning);
}

.nurse-ls-card__status {
  border-radius: 999px;
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
  padding: 4px 8px;
}

.nurse-ls-card__status.is-registered {
  background: var(--el-color-info-light-9);
  color: var(--el-color-info);
}

.nurse-ls-card__status.is-in-progress {
  background: var(--el-color-success-light-9);
  color: var(--el-color-success);
}

.nurse-ls-card__status.is-on-hold,
.nurse-ls-card__status.is-pending {
  background: var(--el-color-warning-light-9);
  color: var(--el-color-warning);
}

.nurse-ls-card__status.is-completed {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.nurse-ls-card__status.is-discharged,
.nurse-ls-card__status.is-default {
  background: #eef1f5;
  color: #64748b;
}

.nurse-ls-card__identity {
  align-items: baseline;
  display: flex;
  flex-wrap: wrap;
  gap: 6px 8px;
  min-width: 0;
}

.nurse-ls-card__name {
  color: var(--el-text-color-primary);
  font-size: 15px;
  font-weight: 700;
  line-height: 1.2;
}

.nurse-ls-card__meta {
  color: #4b5565;
  font-size: 12px;
}

.nurse-ls-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.nurse-ls-card__tag {
  background: #eef2f7;
  border-radius: 4px;
  color: #334155;
  font-size: 11px;
  font-weight: 500;
  line-height: 1;
  padding: 4px 6px;
}

.nurse-ls-card__tag.is-level {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.nurse-ls-card__tag.is-risk {
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}

.nurse-ls-card__money {
  display: grid;
  gap: 1px;
  grid-template-columns: 1fr 1fr;
  margin: 0 12px 0 14px;
  overflow: hidden;
  border: 1px solid #d7dde7;
  border-radius: 6px;
  background: #d7dde7;
}

.nurse-ls-card__money-item {
  align-items: baseline;
  background: #f5f7fb;
  display: flex;
  gap: 6px;
  min-width: 0;
  padding: 7px 10px;
}

.nurse-ls-card__money-val {
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-variant-numeric: tabular-nums;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nurse-ls-card__money-val.is-danger {
  color: var(--el-color-danger);
}

.nurse-ls-card__money-val.is-warning {
  color: var(--el-color-warning);
}

.nurse-ls-card__money-val.is-success,
.nurse-ls-card__money-val.is-primary {
  color: var(--el-color-primary);
}

.nurse-ls-card__k {
  color: #64748b;
  flex-shrink: 0;
  font-size: 12px;
}

.nurse-ls-card__info {
  display: grid;
  gap: 4px 10px;
  grid-template-columns: 1fr 1fr;
  padding: 0 12px 0 14px;
}

.nurse-ls-card__info-cell {
  align-items: baseline;
  display: flex;
  gap: 4px;
  min-width: 0;
}

.nurse-ls-card__info-cell--full {
  grid-column: 1 / -1;
}

.nurse-ls-card__v {
  color: var(--el-text-color-primary);
  font-size: 12px;
  font-weight: 500;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nurse-ls-card__v.is-diag {
  color: #b42318;
  font-weight: 600;
}

.nurse-ls-card__tasks {
  display: flex;
  gap: 6px;
  padding: 0 12px 0 14px;
}

.nurse-ls-card__task {
  background: #f1f4f8;
  border-radius: 999px;
  color: #64748b;
  font-size: 12px;
  line-height: 1;
  padding: 4px 8px;
}

.nurse-ls-card__task b {
  color: #b45309;
  font-variant-numeric: tabular-nums;
  font-weight: 700;
  margin-left: 2px;
}

.nurse-ls-card__task.is-execute b {
  color: var(--el-color-danger);
}

.nurse-ls-card__task.is-on {
  background: #fff7ed;
  color: #9a3412;
}

.nurse-ls-card__task.is-execute.is-on {
  background: #fef2f2;
  color: #991b1b;
}

.nurse-ls-card__shortcuts {
  display: grid;
  gap: 5px;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  margin-top: 2px;
  padding: 0 12px 2px 14px;
}

.nurse-ls-card__shortcut {
  background: #fff;
  border: 1px solid #c5ccd8;
  border-radius: 5px;
  color: #1f2937;
  cursor: pointer;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.2;
  min-width: 0;
  padding: 6px 2px;
  transition:
    border-color 0.15s ease,
    color 0.15s ease,
    background-color 0.15s ease;
}

.nurse-ls-card__shortcut:hover:not(:disabled) {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}

.nurse-ls-card__shortcut:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.nurse-ls-fade-enter-active,
.nurse-ls-fade-leave-active {
  transition: opacity 0.2s ease;
}

.nurse-ls-fade-enter-from,
.nurse-ls-fade-leave-to {
  opacity: 0;
}

@media (max-width: 960px) {
  .nurse-ls__header {
    padding: 8px 10px;
  }

  .nurse-ls__grid {
    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  }
}
</style>
