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
  /** 点击患者卡：选中患者并回到当前业务页 */
  select: [payload: { patientId: string }];
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

/** 点击患者卡：选中该患者并回到工作台业务区 */
function handleCardClick(patient: PatientItem) {
  if (props.recallListMode) {
    return;
  }
  emit('select', { patientId: patient.id });
}

/** 点击快捷入口：选中患者并切业务页签 */
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

/** 住院天数：即进不计出（入院日计、出院日不计）；在院含当日 */
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
  let hasDischarge = false;
  let end = dayjs();
  if (endRaw && dayjs(endRaw).isValid()) {
    hasDischarge = true;
    end = dayjs(endRaw);
  }
  const diff = end.startOf('day').diff(admission.startOf('day'), 'day');
  // 已出院：即进不计出
  if (hasDischarge) {
    return `${Math.max(1, diff)}天`;
  }
  // 在院：含当日
  return `${Math.max(1, diff + 1)}天`;
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

/** 身份证号展示 */
function formatIdCardNo(row: PatientItem): string {
  return (row.idCardNo ?? '').trim() || '—';
}

/** 医保号（人员编号）展示 */
function formatInsuranceNo(row: PatientItem): string {
  return (row.insuranceNo ?? '').trim() || '—';
}

/** 住址展示 */
function formatAddress(row: PatientItem): string {
  return (row.address ?? '').trim() || '—';
}

/** 医保类型展示（合同/费用性质） */
function formatInsuranceType(row: PatientItem): string {
  return (row.feeType ?? '').trim() || '—';
}

/** 电话展示 */
function formatPhone(row: PatientItem): string {
  return (row.patientPhone ?? '').trim() || '—';
}

/** 病人 ID 展示 */
function formatPatientBusNo(row: PatientItem): string {
  return (row.patientBusNo ?? '').trim() || '—';
}

/** 入院途径展示 */
function formatInWay(row: PatientItem): string {
  return (row.inWayCodeText ?? '').trim() || '—';
}

/** 入院状态（病情）展示 */
function formatAdmissionStatus(row: PatientItem): string {
  return (row.priorityEnumText ?? '').trim() || '—';
}

/** 入院科室展示 */
function formatOrganizationName(row: PatientItem): string {
  return (row.organizationName ?? '').trim() || '—';
}

/** 医疗类别（居民医疗类型）展示 */
function formatMedType(row: PatientItem): string {
  return (row.medTypeDisplay ?? '').trim() || '—';
}

/** 险种类别展示 */
function formatInsutype(row: PatientItem): string {
  const code = (row.insutype ?? '').trim();
  if (!code) {
    return '—';
  }
  if (code === '1') {
    return '1-医疗保险';
  }
  if (code.includes('-')) {
    return code;
  }
  return code;
}

/** 欠费限额：当前无独立限额字段，占位展示 */
function formatArrearsLimit(_row: PatientItem): string {
  return '—';
}

/** 门诊号：队列暂无门诊来源号，占位展示 */
function formatOutpatientBusNo(_row: PatientItem): string {
  return '—';
}

/** 办理人：队列暂无登记操作人，占位展示 */
function formatHandlerName(_row: PatientItem): string {
  return '—';
}

/** 诊断行列表（优先 diagnosisList） */
function formatDiagnosisLines(row: PatientItem): string[] {
  if (row.diagnosisList?.length) {
    return row.diagnosisList;
  }
  const summary = (row.diagnosis ?? '').trim();
  if (!summary || summary === '—') {
    return [];
  }
  return summary
    .split(/[；;]/)
    .map((s) => s.trim())
    .filter(Boolean);
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

/** 头部床号展示（未分床时文案固定） */
function formatBedNoLabel(row: PatientItem): string {
  return (row.bedNo ?? '').trim() || '未分床';
}

/** 头部病区/病房展示（不含床号，床号单独加重） */
function formatWardHouseLabel(row: PatientItem): string {
  const parts: string[] = [];
  const ward = resolveRegionName(row);
  const house = resolveHouseName(row);
  if (ward && ward !== '—') {
    parts.push(ward);
  }
  if (house && house !== '—') {
    parts.push(house);
  }
  return parts.join(' · ');
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
            <p class="nurse-ls__subtitle">点击患者卡进入业务页 · 底部按钮直达对应模块</p>
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
            role="button"
            :tabindex="recallListMode ? -1 : 0"
            :title="recallListMode ? undefined : '点击进入该患者业务页'"
            @click="handleCardClick(row)"
            @keydown.enter.prevent="handleCardClick(row)"
          >
            <div class="nurse-ls-card__rail" aria-hidden="true"></div>

            <div class="nurse-ls-card__head">
              <div class="nurse-ls-card__head-row">
                <div class="nurse-ls-card__head-main">
                  <span
                    class="nurse-ls-card__bed"
                    :class="{ 'is-unassigned': isNurseStationBedUnassigned(row.bedNo) }"
                  >
                    {{ formatBedNoLabel(row) }}
                  </span>
                  <span class="nurse-ls-card__name">{{ row.name || '—' }}</span>
                  <span
                    v-if="formatWardHouseLabel(row)"
                    class="nurse-ls-card__ward"
                    :title="formatWardHouseLabel(row)"
                  >
                    {{ formatWardHouseLabel(row) }}
                  </span>
                </div>
                <span class="nurse-ls-card__status" :class="`is-${resolveStatusTone(row)}`">
                  {{ formatStatusLabel(row) }}
                </span>
              </div>
            </div>

            <div class="nurse-ls-card__form">
              <div class="nurse-ls-card__field">
                <span class="nurse-ls-card__label">住院号</span>
                <span class="nurse-ls-card__value">{{ formatEncounterBusNo(row) }}</span>
              </div>
              <div class="nurse-ls-card__field">
                <span class="nurse-ls-card__label">入院途径</span>
                <span class="nurse-ls-card__value">{{ formatInWay(row) }}</span>
              </div>
              <div class="nurse-ls-card__field">
                <span class="nurse-ls-card__label">门诊号</span>
                <span class="nurse-ls-card__value">{{ formatOutpatientBusNo(row) }}</span>
              </div>
              <div class="nurse-ls-card__field">
                <span class="nurse-ls-card__label">护理级别</span>
                <span class="nurse-ls-card__value">{{ formatNursingLevel(row) || '—' }}</span>
              </div>
              <div class="nurse-ls-card__field">
                <span class="nurse-ls-card__label">病人ID</span>
                <span class="nurse-ls-card__value">{{ formatPatientBusNo(row) }}</span>
              </div>
              <div class="nurse-ls-card__field">
                <span class="nurse-ls-card__label">电话</span>
                <span class="nurse-ls-card__value">{{ formatPhone(row) }}</span>
              </div>
              <div class="nurse-ls-card__field">
                <span class="nurse-ls-card__label">姓名</span>
                <span class="nurse-ls-card__value is-strong">{{ row.name || '—' }}</span>
              </div>
              <div class="nurse-ls-card__field">
                <span class="nurse-ls-card__label">性别</span>
                <span class="nurse-ls-card__value">{{ row.gender || '—' }}</span>
              </div>
              <div class="nurse-ls-card__field">
                <span class="nurse-ls-card__label">身份证号</span>
                <span class="nurse-ls-card__value is-mono">{{ formatIdCardNo(row) }}</span>
              </div>
              <div class="nurse-ls-card__field">
                <span class="nurse-ls-card__label">年龄</span>
                <span class="nurse-ls-card__value">{{ formatAgeLabel(row) }}</span>
              </div>
              <div class="nurse-ls-card__field nurse-ls-card__field--full">
                <span class="nurse-ls-card__label">地址</span>
                <span class="nurse-ls-card__value is-wrap" :title="formatAddress(row)">
                  {{ formatAddress(row) }}
                </span>
              </div>
              <div class="nurse-ls-card__field">
                <span class="nurse-ls-card__label">欠费限额</span>
                <span class="nurse-ls-card__value">{{ formatArrearsLimit(row) }}</span>
              </div>
              <div class="nurse-ls-card__field">
                <span class="nurse-ls-card__label">入院状态</span>
                <span class="nurse-ls-card__value">{{ formatAdmissionStatus(row) }}</span>
              </div>
              <div class="nurse-ls-card__field">
                <span class="nurse-ls-card__label">入院科室</span>
                <span class="nurse-ls-card__value">{{ formatOrganizationName(row) }}</span>
              </div>
              <div class="nurse-ls-card__field">
                <span class="nurse-ls-card__label">入院病区</span>
                <span class="nurse-ls-card__value">{{ resolveRegionName(row) }}</span>
              </div>
              <div class="nurse-ls-card__field">
                <span class="nurse-ls-card__label">主治医生</span>
                <span class="nurse-ls-card__value">{{
                  formatStaffName(row.attendingDoctorName)
                }}</span>
              </div>
              <div class="nurse-ls-card__field">
                <span class="nurse-ls-card__label">医保号</span>
                <span class="nurse-ls-card__value is-mono">{{ formatInsuranceNo(row) }}</span>
              </div>
              <div class="nurse-ls-card__field">
                <span class="nurse-ls-card__label">医保类型</span>
                <span class="nurse-ls-card__value is-insure">{{ formatInsuranceType(row) }}</span>
              </div>
              <div class="nurse-ls-card__field">
                <span class="nurse-ls-card__label">居民医疗类型</span>
                <span class="nurse-ls-card__value">{{ formatMedType(row) }}</span>
              </div>
              <div class="nurse-ls-card__field nurse-ls-card__field--full">
                <span class="nurse-ls-card__label">险种类别</span>
                <span class="nurse-ls-card__value">{{ formatInsutype(row) }}</span>
              </div>
            </div>

            <div class="nurse-ls-card__diag">
              <div class="nurse-ls-card__diag-title">入院诊断</div>
              <div class="nurse-ls-card__diag-table">
                <div class="nurse-ls-card__diag-head">
                  <span>名称</span>
                  <span>编码/病情</span>
                </div>
                <template v-if="formatDiagnosisLines(row).length">
                  <div
                    v-for="(line, idx) in formatDiagnosisLines(row)"
                    :key="`${row.id}-diag-${idx}`"
                    class="nurse-ls-card__diag-row"
                  >
                    <span class="is-diag" :title="line">{{ line }}</span>
                    <span>—</span>
                  </div>
                </template>
                <div v-else class="nurse-ls-card__diag-row">
                  <span>—</span>
                  <span>—</span>
                </div>
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

            <div class="nurse-ls-card__footer">
              <span>办理人：{{ formatHandlerName(row) }}</span>
              <span>入院时间：{{ formatAdmissionAt(row) }}</span>
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
              <span class="nurse-ls-card__task"> 住院 {{ formatInpatientDays(row) }} </span>
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
  padding: 8px;
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
  gap: 8px;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
}

.nurse-ls-card {
  background: #fff;
  border: 1px solid #b8bfcb;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgb(15 23 42 / 6%);
  color: inherit;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 5px;
  outline: none;
  overflow: hidden;
  padding: 0 0 5px;
  position: relative;
  text-align: left;
}

.nurse-ls-card:hover:not(.is-recall) {
  border-color: var(--el-color-primary-light-5);
  box-shadow: 0 4px 14px rgb(37 99 235 / 12%);
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
  padding: 4px 8px 4px 10px;
}

.nurse-ls-card__head-row {
  align-items: center;
  display: flex;
  gap: 6px;
  justify-content: space-between;
  min-width: 0;
}

.nurse-ls-card__head-main {
  align-items: baseline;
  display: flex;
  flex: 1;
  flex-wrap: wrap;
  gap: 2px 8px;
  min-width: 0;
}

.nurse-ls-card__bed {
  color: var(--el-color-primary);
  flex-shrink: 0;
  font-size: 18px;
  font-variant-numeric: tabular-nums;
  font-weight: 800;
  letter-spacing: 0.02em;
  line-height: 1.15;
}

.nurse-ls-card__bed.is-unassigned {
  color: var(--el-color-warning);
}

.nurse-ls-card__name {
  color: var(--el-text-color-primary);
  font-size: 17px;
  font-weight: 800;
  line-height: 1.15;
}

.nurse-ls-card__ward {
  color: var(--el-text-color-regular);
  font-size: 11px;
  font-weight: 500;
  line-height: 1.2;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nurse-ls-card__status {
  border-radius: 999px;
  flex-shrink: 0;
  font-size: 10px;
  font-weight: 600;
  line-height: 1;
  padding: 2px 6px;
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

.nurse-ls-card__form {
  display: grid;
  gap: 3px 5px;
  grid-template-columns: 1fr 1fr;
  padding: 0 8px 0 10px;
}

.nurse-ls-card__field {
  align-items: center;
  background: #eef2f7;
  border: 1px solid #d7dde7;
  border-radius: 3px;
  display: flex;
  gap: 4px;
  min-height: 22px;
  min-width: 0;
  padding: 1px 6px;
}

.nurse-ls-card__field--full {
  grid-column: 1 / -1;
}

.nurse-ls-card__label {
  color: var(--el-text-color-regular);
  flex-shrink: 0;
  font-size: 11px;
  line-height: 1.25;
  width: 5em;
}

.nurse-ls-card__value {
  color: var(--el-text-color-primary);
  flex: 1;
  font-size: 11px;
  font-weight: 600;
  line-height: 1.25;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nurse-ls-card__value.is-strong {
  font-size: 12px;
  font-weight: 800;
}

.nurse-ls-card__value.is-mono {
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.02em;
}

.nurse-ls-card__value.is-insure {
  color: var(--el-color-primary);
  font-weight: 700;
}

.nurse-ls-card__value.is-wrap {
  overflow: visible;
  text-overflow: unset;
  white-space: normal;
  word-break: break-all;
}

.nurse-ls-card__diag {
  margin: 0 8px 0 10px;
}

.nurse-ls-card__diag-title {
  color: var(--el-text-color-primary);
  font-size: 11px;
  font-weight: 700;
  margin-bottom: 2px;
}

.nurse-ls-card__diag-table {
  border: 1px solid #d7dde7;
  border-radius: 4px;
  overflow: hidden;
}

.nurse-ls-card__diag-head,
.nurse-ls-card__diag-row {
  display: grid;
  gap: 1px;
  grid-template-columns: 1.4fr 1fr;
}

.nurse-ls-card__diag-head {
  background: #e8edf4;
}

.nurse-ls-card__diag-head span,
.nurse-ls-card__diag-row span {
  background: #f8fafc;
  font-size: 11px;
  line-height: 1.25;
  min-width: 0;
  overflow: hidden;
  padding: 2px 6px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nurse-ls-card__diag-head span {
  background: #e8edf4;
  color: var(--el-text-color-regular);
  font-weight: 600;
}

.nurse-ls-card__diag-row span.is-diag {
  color: #b42318;
  font-weight: 600;
  white-space: normal;
  word-break: break-all;
}

.nurse-ls-card__footer {
  color: var(--el-text-color-regular);
  display: flex;
  font-size: 11px;
  gap: 8px;
  justify-content: space-between;
  padding: 0 8px 0 10px;
}

.nurse-ls-card__money {
  display: grid;
  gap: 1px;
  grid-template-columns: 1fr 1fr;
  margin: 0 8px 0 10px;
  overflow: hidden;
  border: 1px solid #d7dde7;
  border-radius: 4px;
  background: #d7dde7;
}

.nurse-ls-card__money-item {
  align-items: baseline;
  background: #f5f7fb;
  display: flex;
  gap: 4px;
  min-width: 0;
  padding: 3px 8px;
}

.nurse-ls-card__money-val {
  color: var(--el-text-color-primary);
  font-size: 12px;
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
  color: var(--el-text-color-regular);
  flex-shrink: 0;
  font-size: 11px;
}

.nurse-ls-card__tasks {
  display: flex;
  gap: 4px;
  padding: 0 8px 0 10px;
}

.nurse-ls-card__task {
  background: #f1f4f8;
  border-radius: 999px;
  color: var(--el-text-color-regular);
  font-size: 11px;
  line-height: 1;
  padding: 3px 6px;
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
  gap: 3px;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  margin-top: 0;
  padding: 0 8px 1px 10px;
}

.nurse-ls-card__shortcut {
  background: #fff;
  border: 1px solid #c5ccd8;
  border-radius: 4px;
  color: var(--el-text-color-primary);
  cursor: pointer;
  font-size: 11px;
  font-weight: 600;
  line-height: 1.15;
  min-width: 0;
  padding: 3px 1px;
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
    grid-template-columns: minmax(0, 1fr);
  }

  .nurse-ls-card__form {
    grid-template-columns: 1fr;
  }
}

@media (min-width: 1600px) {
  .nurse-ls__grid {
    grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  }
}
</style>
