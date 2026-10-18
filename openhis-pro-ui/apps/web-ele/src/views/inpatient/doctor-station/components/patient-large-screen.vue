<script setup lang="ts">
import type { InpatientDoctorStationPatient } from '../doctorPatientStore';

import { computed, onMounted, onUnmounted, ref, watch } from 'vue';

import { getPaymentTypeTagStyles } from '@zxplat/utils';
import dayjs from 'dayjs';

import { formatInsutype, formatInsutypeShort } from '#/chs/constants/reconciliation';
import { formatInpatientAccountBalancePrimaryText } from '#/utils/inpatient/account-balance-display';
import { isPatientAdmittedToday } from '#/utils/inpatient/is-patient-admitted-today';
import { formatMoneyYuan } from '#/utils/format/money-scale';

defineOptions({
  name: 'InpatientDoctorStationPatientLargeScreen',
});

/** 大屏快捷入口：医生站页签或病案首页跳转 */
type DoctorStationLargeScreenTab =
  | '住院病历'
  | '诊断录入'
  | '开立医嘱'
  | '报告查询'
  | '体温单'
  | '费用明细'
  | '病案首页';

type BoardFilter = 'all' | 'today';
type BoardSort = 'bed' | 'day' | 'name';

interface LargeScreenShortcut {
  key: string;
  label: string;
  tab: DoctorStationLargeScreenTab;
}

interface HouseFilterOption {
  count: number;
  key: string;
  label: string;
}

const WEEKDAY_LABELS = ['日', '一', '二', '三', '四', '五', '六'] as const;

/** 卡片快捷入口（统一白底描边，不强调开立医嘱；无手术） */
const SHORTCUTS: LargeScreenShortcut[] = [
  { key: 'order', label: '开立医嘱', tab: '开立医嘱' },
  { key: 'diag', label: '诊断录入', tab: '诊断录入' },
  { key: 'record', label: '住院病历', tab: '住院病历' },
  { key: 'homepage', label: '病案首页', tab: '病案首页' },
  { key: 'temp', label: '体温单', tab: '体温单' },
  { key: 'fee', label: '费用明细', tab: '费用明细' },
];

interface Props {
  /** 是否展开大屏 */
  modelValue?: boolean;
  /** 与左侧队列同源的在院患者 */
  patients?: InpatientDoctorStationPatient[];
  /** 当前选中就诊 */
  selectedEncounterId?: string;
  /** 队列加载中 */
  loading?: boolean;
  /** 与队列检索框同步的关键字 */
  keyword?: string;
  /** 当前登录医生姓名（顶栏展示） */
  currentDoctorName?: string;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: false,
  patients: () => [],
  selectedEncounterId: '',
  loading: false,
  keyword: '',
  currentDoctorName: '',
});

const emit = defineEmits<{
  'update:modelValue': [value: boolean];
  refresh: [];
  search: [keyword: string];
  select: [patient: InpatientDoctorStationPatient];
  shortcut: [payload: { patient: InpatientDoctorStationPatient; tab: DoctorStationLargeScreenTab }];
}>();

const clockTime = ref('--:--');
const clockDate = ref('----');
const searchKeyword = ref(props.keyword);
const boardFilter = ref<BoardFilter>('all');
/** 病房多选：空数组 = 不限；仅有患者的病房出现在选项里 */
const selectedHouseKeys = ref<string[]>([]);
const boardSort = ref<BoardSort>('bed');
let clockTimer: null | ReturnType<typeof setInterval> = null;

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
});

const organizationName = computed(() => {
  for (const row of props.patients) {
    const name = (row.organizationName ?? '').trim();
    if (name) {
      return name;
    }
  }
  return '住院医生站';
});

const doctorName = computed(() => {
  const name = (props.currentDoctorName ?? '').trim();
  if (!name || name === '—') {
    return '';
  }
  return name;
});

const filterItems = computed(() => [
  { key: 'all' as const, label: '全部', count: props.patients.length },
  {
    key: 'today' as const,
    label: '今日入院',
    count: props.patients.filter((row) => isPatientAdmittedToday(row)).length,
  },
]);

/** 当前队列里有患者的病房（无患者不进筛选） */
const houseOptions = computed<HouseFilterOption[]>(() => {
  const countByKey = new Map<string, number>();
  for (const row of props.patients) {
    const key = resolveHouseGroupKey(row);
    countByKey.set(key, (countByKey.get(key) ?? 0) + 1);
  }
  return [...countByKey.entries()]
    .sort((a, b) => compareHouseGroupKey(a[0], b[0]))
    .map(([key, count]) => ({
      key,
      count,
      label: formatHouseFilterLabel(key),
    }));
});

const displayPatients = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase();
  const houseKeys = selectedHouseKeys.value;
  const filtered = props.patients.filter((row) => {
    if (!matchBoardFilter(row)) {
      return false;
    }
    if (houseKeys.length > 0 && !houseKeys.includes(resolveHouseGroupKey(row))) {
      return false;
    }
    if (!keyword) {
      return true;
    }
    return buildSearchHaystack(row).includes(keyword);
  });
  return filtered.sort((a, b) => comparePatients(a, b));
});

const emptyText = computed(() => {
  if (props.loading) {
    return '加载中...';
  }
  if (props.patients.length === 0) {
    return '暂无在院患者';
  }
  return '未找到匹配的患者';
});

watch(
  () => props.keyword,
  (value) => {
    searchKeyword.value = value;
  },
);

/** 选中病房从队列消失时剔除无效项 */
watch(houseOptions, (options) => {
  if (selectedHouseKeys.value.length === 0) {
    return;
  }
  const valid = new Set(options.map((item) => item.key));
  selectedHouseKeys.value = selectedHouseKeys.value.filter((key) => valid.has(key));
});

watch(visible, (open) => {
  if (open) {
    startClock();
    return;
  }
  stopClock();
});

/** 本地筛选是否命中该行 */
function matchBoardFilter(row: InpatientDoctorStationPatient): boolean {
  if (boardFilter.value === 'today') {
    return isPatientAdmittedToday(row);
  }
  return true;
}

/** 无床位展示名视为未分床 */
function isBedUnassigned(row: InpatientDoctorStationPatient): boolean {
  return !(row.bedName ?? '').trim();
}

/** 本地检索字段：床号、姓名、诊断、医生、住院号、费用性质、险种 */
function buildSearchHaystack(row: InpatientDoctorStationPatient): string {
  return [
    row.bedName,
    row.patientName,
    row.diagnosisSummary,
    ...(row.diagnosisList ?? []),
    row.attendingDoctorName,
    row.encounterBusNo,
    formatFeeNature(row),
    formatInsutypeChip(row),
    formatInsutypeTitle(row),
  ]
    .map((item) => (item ?? '').trim().toLowerCase())
    .filter(Boolean)
    .join(' ');
}

/** 病房分组键：优先 houseName；无病房时回落床号前缀（如 504-9 → 504）；否则未分床 */
function resolveHouseGroupKey(row: InpatientDoctorStationPatient): string {
  const house = (row.houseName ?? '').trim();
  if (house) {
    return house;
  }
  const bedRaw = (row.bedName ?? '').trim();
  if (bedRaw) {
    const prefix = bedRaw.match(/^(\d+)/)?.[1];
    if (prefix) {
      return prefix;
    }
    return bedRaw;
  }
  return '未分床';
}

/** 病房筛选项文案 */
function formatHouseFilterLabel(key: string): string {
  if (key === '未分床') {
    return '未分床';
  }
  if (/^\d+$/.test(key)) {
    return `${key} 病房`;
  }
  if (key.includes('房') || key.includes('室')) {
    return key;
  }
  return `${key} 病房`;
}

/** 病房选项排序：未分床置后，其余按房号数字优先 */
function compareHouseGroupKey(a: string, b: string): number {
  if (a === '未分床' && b !== '未分床') return 1;
  if (b === '未分床' && a !== '未分床') return -1;
  return a.localeCompare(b, 'zh-CN', { numeric: true });
}

/** 按当前排序规则比较两名患者 */
function comparePatients(
  a: InpatientDoctorStationPatient,
  b: InpatientDoctorStationPatient,
): number {
  if (boardSort.value === 'name') {
    return (a.patientName ?? '').localeCompare(b.patientName ?? '', 'zh-CN');
  }
  if (boardSort.value === 'day') {
    return resolveInpatientDays(b) - resolveInpatientDays(a);
  }
  return compareByBedThenName(a, b);
}

/** 床号优先、未分床置后，其次姓名 */
function compareByBedThenName(
  a: InpatientDoctorStationPatient,
  b: InpatientDoctorStationPatient,
): number {
  const bedA = (a.bedName ?? '').trim();
  const bedB = (b.bedName ?? '').trim();
  if (!bedA && bedB) return 1;
  if (bedA && !bedB) return -1;
  if (bedA !== bedB) {
    return bedA.localeCompare(bedB, 'zh-CN', { numeric: true });
  }
  return (a.patientName ?? '').localeCompare(b.patientName ?? '', 'zh-CN');
}

/** 刷新时钟文案 */
function tickClock() {
  const now = dayjs();
  clockTime.value = now.format('HH:mm');
  clockDate.value = `${now.format('YYYY年M月D日')} 周${WEEKDAY_LABELS[now.day()]}`;
}

/** 启动时钟定时器 */
function startClock() {
  tickClock();
  if (clockTimer) {
    clearInterval(clockTimer);
  }
  clockTimer = setInterval(tickClock, 10_000);
}

/** 停止时钟定时器 */
function stopClock() {
  if (clockTimer) {
    clearInterval(clockTimer);
    clockTimer = null;
  }
}

/** 收起大屏回到工作台 */
function handleClose() {
  visible.value = false;
}

/** 触发队列刷新 */
function handleRefresh() {
  emit('refresh');
}

/** 回车后按关键字重拉队列 */
function handleSearch() {
  emit('search', searchKeyword.value);
}

/** 切换本地筛选 */
function changeBoardFilter(value: BoardFilter) {
  boardFilter.value = value;
}

/** Esc 收起大屏 */
function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && visible.value) {
    event.preventDefault();
    handleClose();
  }
}

/** 点击患者卡：选中并进入住院病历 */
function handleCardClick(row: InpatientDoctorStationPatient) {
  emit('select', row);
}

/** 快捷入口：选中并打开对应页签 */
function handleShortcutClick(
  row: InpatientDoctorStationPatient,
  tab: DoctorStationLargeScreenTab,
  event: MouseEvent,
) {
  event.stopPropagation();
  emit('shortcut', { patient: row, tab });
}

/** 床号徽章文案 */
function formatBedLabel(row: InpatientDoctorStationPatient): string {
  const bedRaw = (row.bedName ?? '').trim();
  if (!bedRaw) {
    return '未分床';
  }
  return bedRaw.includes('床') ? bedRaw : `${bedRaw} 床`;
}

/** 卡片状态点：今日入院 / 未分床 / 普通 */
function resolveCardStatus(row: InpatientDoctorStationPatient): 'new' | 'normal' | 'postop' {
  if (isBedUnassigned(row)) {
    return 'postop';
  }
  if (isPatientAdmittedToday(row)) {
    return 'new';
  }
  return 'normal';
}

/** 是否女性（头像配色） */
function isFemalePatient(row: InpatientDoctorStationPatient): boolean {
  const text = (row.genderEnumText ?? '').trim();
  if (text.includes('女')) {
    return true;
  }
  return String(row.genderEnum ?? '').trim() === '2';
}

/** 性别 · 年龄 */
function formatGenderAge(row: InpatientDoctorStationPatient): string {
  const genderCode = String(row.genderEnum ?? '').trim();
  const gender =
    (row.genderEnumText ?? '').trim() ||
    (genderCode === '1' ? '男' : genderCode === '2' ? '女' : '');
  const age = (row.ageText ?? '').trim();
  if (gender && age) {
    return `${gender} · ${age.includes('岁') ? age : `${age} 岁`}`;
  }
  return gender || age || '—';
}

/** 诊断摘要（首条，带 [主] 前缀） */
function formatDiagnosis(row: InpatientDoctorStationPatient): string {
  let raw = '';
  if (row.diagnosisList?.length) {
    raw = row.diagnosisList.map((line) => line.trim()).find(Boolean) ?? '';
  }
  if (!raw) {
    raw = (row.diagnosisSummary ?? '').trim();
  }
  if (!raw) {
    return '暂无诊断';
  }
  if (raw.startsWith('[主]') || raw.startsWith('【主】')) {
    return raw;
  }
  return `[主]${raw}`;
}

/** 余额是否负值（红字） */
function isBalanceNegative(row: InpatientDoctorStationPatient): boolean {
  const raw = row.availableBalance ?? row.accountBalance;
  if (raw == null || raw === '') {
    return false;
  }
  const num = Number(raw);
  return Number.isFinite(num) && num < 0;
}

/** 主治医生 */
function formatDoctor(row: InpatientDoctorStationPatient): string {
  return (row.attendingDoctorName ?? '').trim() || '—';
}

/** 护理级别 */
function formatNursingLevel(row: InpatientDoctorStationPatient): string {
  const raw = (row.nursingLevelName ?? '').trim();
  return raw || '—';
}

/** 住院号 */
function formatEncounterBusNo(row: InpatientDoctorStationPatient): string {
  return (row.encounterBusNo ?? '').trim() || '—';
}

/** 费用性质（合同名：自费 / 市医保 / 省医保等；缺失回落自费） */
function formatFeeNature(row: InpatientDoctorStationPatient): string {
  return (row.contractDisplay ?? '').trim() || '自费';
}

/** 费用性质胶囊配色（与侧栏 PatientCard 同口径） */
function feeNatureTagStyle(row: InpatientDoctorStationPatient): Record<string, string> {
  const styles = getPaymentTypeTagStyles(formatFeeNature(row));
  return {
    backgroundColor: styles.bgColor,
    color: styles.textColor,
  };
}

/** 险种短标签：职工 / 居民；未知码不展示 */
function formatInsutypeChip(row: InpatientDoctorStationPatient): string {
  return formatInsutypeShort(row.insutype);
}

/** 险种完整名（悬浮提示） */
function formatInsutypeTitle(row: InpatientDoctorStationPatient): string {
  const full = formatInsutype(row.insutype);
  return full && full !== '—' ? full : '';
}

/** 余额 */
function formatBalance(row: InpatientDoctorStationPatient): string {
  return formatInpatientAccountBalancePrimaryText(row);
}

/** 预交金（库内账户余额 prepayNetBalance，非 paidAmount） */
function formatPrepayAmount(row: InpatientDoctorStationPatient): string {
  if (row.prepayNetBalance == null || row.prepayNetBalance === '') {
    return '—';
  }
  const num = Number(row.prepayNetBalance);
  if (Number.isFinite(num)) {
    return formatMoneyYuan(num);
  }
  return String(row.prepayNetBalance);
}

/**
 * 住院天数：即进不计出（入院日计、出院日不计）；在院含当日。
 */
function resolveInpatientDays(row: InpatientDoctorStationPatient): number {
  const admissionRaw = (row.admissionTime ?? row.startTime ?? '').trim();
  if (!admissionRaw) {
    return 0;
  }
  const admission = dayjs(admissionRaw);
  if (!admission.isValid()) {
    return 0;
  }
  const endRaw = (row.endTime ?? '').trim();
  let hasDischarge = false;
  let end = dayjs();
  if (endRaw && dayjs(endRaw).isValid()) {
    hasDischarge = true;
    end = dayjs(endRaw);
  }
  const diff = end.startOf('day').diff(admission.startOf('day'), 'day');
  if (hasDischarge) {
    return Math.max(1, diff);
  }
  return Math.max(1, diff + 1);
}

/** 住院时长展示 */
function formatInpatientDays(row: InpatientDoctorStationPatient): string {
  const days = resolveInpatientDays(row);
  if (days <= 0) {
    return '—';
  }
  return `${days} 天`;
}

onMounted(() => {
  window.addEventListener('keydown', handleKeydown);
  if (visible.value) {
    startClock();
  }
});

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown);
  stopClock();
});
</script>

<template>
  <Transition name="ip-doc-ls-fade">
    <div v-if="visible" class="ip-doc-ls" role="dialog" aria-modal="true" aria-label="在院患者大屏">
      <header class="topbar">
        <div class="brand">
          <div class="logo">医</div>
          <div class="brand-t">
            <h1>{{ organizationName }}</h1>
            <p>住院医生站 · 患者大屏</p>
          </div>
        </div>
        <div class="top-meta">
          <span class="meta-pill">
            <i class="dot"></i>
            在屏 <b>{{ displayPatients.length }}</b>
          </span>
          <span v-if="doctorName" class="meta-pill">主管医生 <b>{{ doctorName }}</b></span>
        </div>
        <div class="top-actions">
          <div class="clock">
            <div class="t">{{ clockTime }}</div>
            <div class="d">{{ clockDate }}</div>
          </div>
          <button type="button" class="icon-btn" title="刷新队列" @click="handleRefresh">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 12a9 9 0 1 1-2.2-5.8" />
              <path d="M21 3v6h-6" />
            </svg>
          </button>
          <button type="button" class="icon-btn" title="收起（Esc）" @click="handleClose">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
              <path d="M18 6 6 18M6 6l12 12" />
            </svg>
          </button>
        </div>
      </header>

      <section class="toolbar-area">
        <div class="toolbar-card">
          <div class="seg-group" role="group" aria-label="患者筛选">
            <button
              v-for="item in filterItems"
              :key="item.key"
              type="button"
              class="seg-item"
              :class="{
                active: boardFilter === item.key,
                'is-today': item.key === 'today',
              }"
              @click="changeBoardFilter(item.key)"
            >
              {{ item.label }}
              <span class="cnt">{{ item.count }}</span>
            </button>
          </div>
          <div class="toolbar-right">
            <select v-model="boardSort" class="select" aria-label="排序">
              <option value="bed">按床号排序</option>
              <option value="day">按住院时长</option>
              <option value="name">按姓名排序</option>
            </select>
            <label class="search-wrap">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                <circle cx="11" cy="11" r="7" />
                <path d="M20 20l-3.5-3.5" />
              </svg>
              <input
                v-model="searchKeyword"
                type="search"
                placeholder="搜索床号、姓名、诊断或医生"
                autocomplete="off"
                @keyup.enter="handleSearch"
              />
            </label>
          </div>
        </div>
        <div
          v-if="houseOptions.length > 0"
          class="house-checks"
          role="group"
          aria-label="病房筛选"
        >
          <span class="house-checks__label">病房</span>
          <label
            v-for="item in houseOptions"
            :key="item.key"
            class="house-check"
            :class="{ checked: selectedHouseKeys.includes(item.key) }"
          >
            <input v-model="selectedHouseKeys" type="checkbox" :value="item.key" />
            <span class="house-check__box" aria-hidden="true"></span>
            <span class="house-check__text">{{ item.label }}</span>
            <span class="house-check__cnt">{{ item.count }}</span>
          </label>
        </div>
      </section>

      <main class="content">
        <div v-if="displayPatients.length === 0" class="empty">{{ emptyText }}</div>
        <div v-else class="grid">
          <article
            v-for="row in displayPatients"
            :key="row.encounterId"
            class="card"
            :class="{ 'is-selected': row.encounterId === selectedEncounterId }"
            :data-status="resolveCardStatus(row)"
            role="button"
            tabindex="0"
            :aria-label="`${row.patientName || '患者'} 进入住院病历`"
            @click="handleCardClick(row)"
            @keydown.enter.prevent="handleCardClick(row)"
          >
              <div class="card-head">
                <span class="bed-badge">
                  <i class="status-dot" :class="resolveCardStatus(row)"></i>
                  {{ formatBedLabel(row) }}
                </span>
                <div class="card-tags">
                  <span
                    class="tag tag-fee"
                    :style="feeNatureTagStyle(row)"
                    :title="formatFeeNature(row)"
                  >
                    {{ formatFeeNature(row) }}
                  </span>
                  <span
                    v-if="formatInsutypeChip(row)"
                    class="tag tag-insutype"
                    :title="formatInsutypeTitle(row) || undefined"
                  >
                    {{ formatInsutypeChip(row) }}
                  </span>
                  <span v-if="isPatientAdmittedToday(row)" class="tag tag-today">今日入院</span>
                  <span v-if="isBedUnassigned(row)" class="tag tag-postop">未分床</span>
                  <span v-if="row.lowBalanceWarning || isBalanceNegative(row)" class="tag tag-warning">
                    余额预警
                  </span>
                </div>
              </div>
              <div class="card-person">
                <div class="avatar" :class="isFemalePatient(row) ? 'female' : 'male'">
                  <!-- 女生：长发圆脸插画 -->
                  <svg
                    v-if="isFemalePatient(row)"
                    viewBox="0 0 64 64"
                    xmlns="http://www.w3.org/2000/svg"
                    aria-hidden="true"
                  >
                    <circle cx="32" cy="32" r="32" fill="#fce7f3" />
                    <path
                      d="M14 34c1.2-12 7.8-20 18-20s16.8 8 18 20c0 0-2.2 2.4-5.4 3.2 0-7.6-5-13.4-12.6-13.4S19.4 29.6 19.4 37.2C16.2 36.4 14 34 14 34z"
                      fill="#9d174d"
                    />
                    <ellipse cx="32" cy="30" rx="11.5" ry="12.5" fill="#f5d0b5" />
                    <path
                      d="M21.5 26.5c1.2-4.2 5-7 10.5-7s9.3 2.8 10.5 7c-1.8-2.6-5.2-4.2-10.5-4.2s-8.7 1.6-10.5 4.2z"
                      fill="#9d174d"
                    />
                    <circle cx="27.2" cy="30.2" r="1.35" fill="#5b3a29" />
                    <circle cx="36.8" cy="30.2" r="1.35" fill="#5b3a29" />
                    <path
                      d="M29.2 35.4c1.2 1.4 4.4 1.4 5.6 0"
                      fill="none"
                      stroke="#c06a6a"
                      stroke-linecap="round"
                      stroke-width="1.4"
                    />
                    <path
                      d="M16 46.5c3.8-5.2 9.2-8 16-8s12.2 2.8 16 8C42.5 54 37.5 58 32 58s-10.5-4-16-11.5z"
                      fill="#ec4899"
                    />
                  </svg>
                  <!-- 男生：短发圆脸插画 -->
                  <svg
                    v-else
                    viewBox="0 0 64 64"
                    xmlns="http://www.w3.org/2000/svg"
                    aria-hidden="true"
                  >
                    <circle cx="32" cy="32" r="32" fill="#dbeafe" />
                    <path
                      d="M20 24.5c1.5-7.5 6.8-12.5 12-12.5s10.5 5 12 12.5c-2.2-3.2-6.4-5.2-12-5.2s-9.8 2-12 5.2z"
                      fill="#1e3a5f"
                    />
                    <ellipse cx="32" cy="30" rx="11.5" ry="12.5" fill="#f5d0b5" />
                    <path
                      d="M21.8 25.8c1.4-3.6 5-5.8 10.2-5.8s8.8 2.2 10.2 5.8c-1.6-2-4.8-3.2-10.2-3.2s-8.6 1.2-10.2 3.2z"
                      fill="#1e3a5f"
                    />
                    <circle cx="27.2" cy="30.2" r="1.35" fill="#5b3a29" />
                    <circle cx="36.8" cy="30.2" r="1.35" fill="#5b3a29" />
                    <path
                      d="M29.2 35.6c1.2 1.2 4.4 1.2 5.6 0"
                      fill="none"
                      stroke="#c06a6a"
                      stroke-linecap="round"
                      stroke-width="1.4"
                    />
                    <path
                      d="M16 46.5c3.8-5.2 9.2-8 16-8s12.2 2.8 16 8C42.5 54 37.5 58 32 58s-10.5-4-16-11.5z"
                      fill="#3b82f6"
                    />
                  </svg>
                </div>
                <div class="person-t">
                  <div class="person-name">
                    <span class="n">{{ row.patientName || '—' }}</span>
                    <span class="d">{{ formatGenderAge(row) }}</span>
                  </div>
                  <div class="person-diag" :title="formatDiagnosis(row)">
                    {{ formatDiagnosis(row) }}
                  </div>
                </div>
              </div>
              <div class="card-meta">
                <div class="item">
                  <span class="k">住院号</span>
                  <span class="v">{{ formatEncounterBusNo(row) }}</span>
                </div>
                <div class="item">
                  <span class="k">主治医生</span>
                  <span class="v">{{ formatDoctor(row) }}</span>
                </div>
                <div class="item">
                  <span class="k">护理级别</span>
                  <span class="v is-nursing">{{ formatNursingLevel(row) }}</span>
                </div>
                <div class="item">
                  <span class="k">住院时长</span>
                  <span class="v">{{ formatInpatientDays(row) }}</span>
                </div>
                <div class="item">
                  <span class="k">余额</span>
                  <span class="v" :class="{ negative: isBalanceNegative(row) }">
                    {{ formatBalance(row) }}
                  </span>
                </div>
                <div class="item">
                  <span class="k">预交金</span>
                  <span class="v">{{ formatPrepayAmount(row) }}</span>
                </div>
              </div>
              <div class="actions" @click.stop>
                <button
                  v-for="item in SHORTCUTS"
                  :key="item.key"
                  type="button"
                  class="act-btn"
                  @click="handleShortcutClick(row, item.tab, $event)"
                >
                  <svg
                    v-if="item.key === 'order'"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  >
                    <path d="M9 3h6v4l4 12a2 2 0 0 1-2 3H7a2 2 0 0 1-2-3L9 7V3z" />
                    <path d="M9 3v4h6V3" />
                    <path d="M7.5 14h9" />
                  </svg>
                  <svg
                    v-else-if="item.key === 'diag'"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  >
                    <path d="M12 20h9" />
                    <path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z" />
                  </svg>
                  <svg
                    v-else-if="item.key === 'record'"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  >
                    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                    <path d="M14 2v6h6" />
                    <path d="M8 13h8M8 17h5" />
                  </svg>
                  <svg
                    v-else-if="item.key === 'homepage'"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  >
                    <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
                    <path d="M9 22V12h6v10" />
                  </svg>
                  <svg
                    v-else-if="item.key === 'temp'"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  >
                    <path d="M14 14.76V3.5a2.5 2.5 0 0 0-5 0v11.26a4.5 4.5 0 1 0 5 0z" />
                  </svg>
                  <svg
                    v-else
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  >
                    <rect x="2" y="5" width="20" height="14" rx="2" />
                    <path d="M2 10h20" />
                    <path d="M6 15h4" />
                  </svg>
                  <span>{{ item.label }}</span>
                </button>
              </div>
          </article>
        </div>
      </main>
    </div>
  </Transition>
</template>

<style scoped>
.ip-doc-ls {
  --bg: #f7f8fa;
  --surface: #ffffff;
  --surface-2: #f7f8fa;
  --line: #eef0f4;
  --line-2: #e2e5ec;
  --text: #18181b;
  --text-2: #52525b;
  --text-3: #a1a1aa;
  --accent: #2563eb;
  --accent-dark: #1d4ed8;
  --accent-soft: #eff6ff;
  --danger: #ef4444;
  --danger-soft: #fef2f2;
  --warn: #f59e0b;
  --warn-soft: #fffbeb;
  --ok: #10b981;
  --ok-soft: #ecfdf5;
  --r: 16px;
  --sh: 0 1px 2px rgb(16 24 40 / 4%), 0 1px 3px rgb(16 24 40 / 3%);
  --sh-h: 0 12px 28px rgb(16 24 40 / 8%), 0 2px 6px rgb(16 24 40 / 4%);
  background: var(--bg);
  bottom: 0;
  color: var(--text);
  display: flex;
  flex-direction: column;
  font-size: 14px;
  left: 0;
  line-height: 1.5;
  overflow: hidden;
  position: absolute;
  right: 0;
  top: 0;
  z-index: 40;
}

.ip-doc-ls :deep(svg) {
  display: block;
}

.topbar {
  align-items: center;
  background: var(--surface);
  border-bottom: 1px solid var(--line);
  display: flex;
  flex: none;
  gap: 16px;
  height: 48px;
  justify-content: space-between;
  padding: 0 20px;
  z-index: 30;
}

.brand {
  align-items: center;
  display: flex;
  gap: 12px;
  min-width: 0;
}

.logo {
  align-items: center;
  background: #1e3a5f;
  border-radius: 8px;
  color: #fff;
  display: grid;
  flex: none;
  font-size: 14px;
  font-weight: 700;
  height: 30px;
  justify-content: center;
  letter-spacing: 0.5px;
  width: 30px;
}

.brand-t h1 {
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.1px;
  line-height: 1.2;
  margin: 0;
  white-space: nowrap;
}

.brand-t p {
  color: var(--text-3);
  font-size: 11.5px;
  margin: 0;
}

.top-meta {
  display: flex;
  flex: 1;
  gap: 10px;
  justify-content: center;
}

.meta-pill {
  align-items: center;
  background: var(--surface-2);
  border: 1px solid var(--line);
  border-radius: 16px;
  color: var(--text-2);
  display: inline-flex;
  font-size: 12px;
  gap: 6px;
  padding: 3px 10px;
  white-space: nowrap;
}

.meta-pill .dot {
  background: var(--ok);
  border-radius: 50%;
  height: 6px;
  width: 6px;
}

.meta-pill b {
  color: var(--text);
  font-weight: 600;
}

.top-actions {
  align-items: center;
  display: flex;
  gap: 14px;
}

.clock {
  border-left: 1px solid var(--line);
  padding-left: 14px;
  text-align: right;
}

.clock .t {
  font-size: 17px;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  letter-spacing: 0.3px;
  line-height: 1.2;
}

.clock .d {
  color: var(--text-3);
  font-size: 11.5px;
  white-space: nowrap;
}

.icon-btn {
  align-items: center;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 9px;
  color: var(--text-2);
  cursor: pointer;
  display: grid;
  height: 35px;
  justify-content: center;
  transition: 0.18s;
  width: 35px;
}

.icon-btn:hover {
  background: var(--surface-2);
  border-color: var(--line-2);
  color: var(--text);
}

.toolbar-area {
  flex: none;
  padding: 8px 20px 0;
}

.toolbar-card {
  align-items: center;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 12px;
  box-shadow: var(--sh);
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: space-between;
  padding: 5px 8px 5px 8px;
}

.seg-group {
  background: #f1f3f8;
  border-radius: 10px;
  display: inline-flex;
  gap: 2px;
  padding: 3px;
}

.seg-item {
  align-items: center;
  background: none;
  border: none;
  border-radius: 7px;
  color: var(--text-2);
  cursor: pointer;
  display: inline-flex;
  font-size: 13px;
  gap: 7px;
  padding: 6px 13px;
  transition: 0.16s;
  white-space: nowrap;
}

.seg-item:hover {
  color: var(--text);
}

.seg-item.active {
  background: var(--surface);
  box-shadow:
    0 1px 2px rgb(16 24 40 / 8%),
    0 0 0 1px rgb(16 24 40 / 4%);
  color: var(--text);
  font-weight: 600;
}

.seg-item.is-today.active {
  color: var(--ok);
}

.seg-item.is-today.active .cnt {
  background: var(--ok);
  color: #fff;
}

.seg-item .cnt {
  align-items: center;
  background: rgb(16 24 40 / 7%);
  border-radius: 5px;
  color: var(--text-2);
  display: inline-grid;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  height: 17px;
  justify-content: center;
  min-width: 18px;
  padding: 0 5px;
}

.seg-item.active .cnt {
  background: var(--text);
  color: #fff;
}

.toolbar-right {
  align-items: center;
  border-left: 1px solid var(--line);
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding-left: 16px;
}

.house-checks {
  align-items: center;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 12px;
  box-shadow: var(--sh);
  display: flex;
  flex-wrap: wrap;
  gap: 6px 8px;
  margin-top: 6px;
  padding: 6px 10px;
}

.house-checks__label {
  color: var(--text-2);
  flex: none;
  font-size: 12.5px;
  font-weight: 600;
  margin-right: 2px;
}

.house-check {
  align-items: center;
  background: #f8fafc;
  border: 1px solid #d7e3f4;
  border-radius: 8px;
  color: var(--text);
  cursor: pointer;
  display: inline-flex;
  font-size: 13px;
  gap: 6px;
  padding: 4px 9px 4px 7px;
  transition: 0.16s;
  user-select: none;
}

.house-check:hover {
  background: #f1f6fc;
  border-color: #c5d6eb;
}

.house-check.checked {
  background: #eef4fb;
  border-color: #9eb6d4;
  color: var(--text);
}

.house-check input {
  position: absolute;
  opacity: 0;
  pointer-events: none;
}

.house-check__box {
  background: #fff;
  border: 1.5px solid #7a9cc4;
  border-radius: 4px;
  flex: none;
  height: 14px;
  position: relative;
  width: 14px;
}

.house-check.checked .house-check__box {
  background: #5b7fa8;
  border-color: #5b7fa8;
}

.house-check.checked .house-check__box::after {
  border: solid #fff;
  border-width: 0 1.5px 1.5px 0;
  content: '';
  height: 7px;
  left: 4px;
  position: absolute;
  top: 1px;
  transform: rotate(45deg);
  width: 4px;
}

.house-check__text {
  font-weight: 500;
  line-height: 1.2;
}

.house-check__cnt {
  background: #e4ecf6;
  border-radius: 5px;
  color: #5b7fa8;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  line-height: 1;
  padding: 2px 5px;
}

.house-check.checked .house-check__cnt {
  background: #d5e2f0;
  color: #4a6f96;
}

.select {
  appearance: none;
  background: var(--surface)
    url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='11' height='11' viewBox='0 0 24 24' fill='none' stroke='%2398a2b3' stroke-width='2.5' stroke-linecap='round'><path d='M6 9l6 6 6-6'/></svg>")
    no-repeat right 11px center;
  border: 1px solid var(--line);
  border-radius: 8px;
  color: var(--text-2);
  cursor: pointer;
  font-size: 13px;
  height: 34px;
  outline: none;
  padding: 0 30px 0 12px;
}

.select:hover {
  background-color: var(--surface-2);
  border-color: var(--line-2);
}

.select:focus {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px rgb(37 99 235 / 9%);
}

.search-wrap {
  position: relative;
  width: 250px;
}

.search-wrap svg {
  color: var(--text-3);
  height: 15px;
  left: 12px;
  pointer-events: none;
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 15px;
}

.search-wrap input {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 8px;
  font-size: 13.5px;
  height: 34px;
  outline: none;
  padding: 0 13px 0 35px;
  transition: 0.18s;
  width: 100%;
}

.search-wrap input::placeholder {
  color: var(--text-3);
}

.search-wrap input:hover {
  border-color: var(--line-2);
}

.search-wrap input:focus {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px rgb(37 99 235 / 9%);
}

.search-wrap input::-webkit-search-cancel-button {
  appearance: none;
}

.content {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 10px 20px 28px;
}

.grid {
  display: grid;
  gap: 18px;
  grid-template-columns: repeat(auto-fill, minmax(316px, 1fr));
}

.card {
  background: var(--surface);
  border: 1px solid #d8dde6;
  border-radius: var(--r);
  box-shadow:
    0 1px 2px rgb(16 24 40 / 5%),
    0 4px 12px rgb(16 24 40 / 6%);
  cursor: pointer;
  outline: none;
  padding: 16px 16px 14px;
  position: relative;
  transition:
    border-color 0.2s,
    box-shadow 0.25s,
    transform 0.2s;
}

.card:hover,
.card:focus-visible {
  border-color: #c5ccd8;
  box-shadow: var(--sh-h);
  transform: translateY(-2px);
}

.card.is-selected {
  border-color: var(--accent);
  box-shadow: 0 0 0 1px var(--accent), var(--sh-h);
}

.card-head {
  align-items: center;
  display: flex;
  gap: 10px;
  justify-content: space-between;
  margin-bottom: 14px;
}

.bed-badge {
  align-items: center;
  color: var(--text);
  display: inline-flex;
  font-size: 15px;
  font-variant-numeric: tabular-nums;
  font-weight: 700;
  gap: 7px;
  letter-spacing: -0.1px;
}

.status-dot {
  border-radius: 50%;
  flex: none;
  height: 7px;
  width: 7px;
}

.status-dot.new {
  background: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-soft);
}

.status-dot.postop {
  background: var(--warn);
  box-shadow: 0 0 0 3px var(--warn-soft);
}

.status-dot.normal {
  background: #d4d4d8;
  box-shadow: 0 0 0 3px #f4f4f5;
}

.card-tags {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: flex-end;
}

.tag {
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.1px;
  line-height: 1.6;
  padding: 2px 9px;
  white-space: nowrap;
}

.tag-critical,
.tag-warning {
  background: #fee2e2;
  color: #dc2626;
}

.tag-new,
.tag-today {
  background: var(--ok-soft);
  color: #047857;
}

.tag-postop {
  background: var(--warn-soft);
  color: #b45309;
}

.tag-insutype {
  background: #f1f5f9;
  color: #334155;
}

.card-person {
  align-items: center;
  display: flex;
  gap: 13px;
  margin-bottom: 13px;
}

.avatar {
  background: transparent;
  border-radius: 50%;
  box-shadow: 0 1px 3px rgb(16 24 40 / 10%);
  display: grid;
  flex: none;
  height: 46px;
  overflow: hidden;
  place-items: center;
  position: relative;
  width: 46px;
}

.avatar svg {
  display: block;
  height: 100%;
  width: 100%;
}

.person-t {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.person-name {
  align-items: baseline;
  display: flex;
  gap: 8px;
}

.person-name .n {
  color: var(--text);
  font-size: 19px;
  font-weight: 700;
  letter-spacing: -0.2px;
  line-height: 1.1;
}

.person-name .d {
  color: var(--text-2);
  font-size: 12.5px;
  font-weight: 400;
}

.person-diag {
  color: var(--text-2);
  font-size: 13px;
  font-weight: 400;
  line-height: 1.45;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-meta {
  background: var(--surface-2);
  border: 1px solid var(--line);
  border-radius: 10px;
  display: grid;
  gap: 11px 0;
  grid-template-columns: 1fr 1fr 1fr;
  margin-bottom: 13px;
  padding: 11px 4px;
}

.card-meta .item {
  border-left: 1px solid var(--line);
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
  padding: 0 12px;
}

.card-meta .item:nth-child(3n + 1) {
  border-left: none;
  padding-left: 14px;
}

.card-meta .item:nth-child(3n) {
  padding-right: 14px;
}

.card-meta .k {
  color: var(--text-3);
  font-size: 10.5px;
  font-weight: 500;
  letter-spacing: 0.2px;
  white-space: nowrap;
}

.card-meta .v {
  color: var(--text);
  font-size: 13px;
  font-weight: 600;
  letter-spacing: -0.1px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-meta .v.negative {
  color: var(--danger);
}

.card-meta .v.is-nursing {
  color: var(--accent);
}

.actions {
  display: grid;
  gap: 6px;
  grid-template-columns: 1fr 1fr;
}

.act-btn {
  align-items: center;
  background: #eef4ff;
  border: 1px solid #dbe6fb;
  border-radius: 9px;
  color: #3b5b9a;
  cursor: pointer;
  display: flex;
  font-size: 12.5px;
  font-weight: 500;
  gap: 5px;
  height: 32px;
  justify-content: center;
  letter-spacing: -0.1px;
  transition: 0.16s;
  white-space: nowrap;
}

.act-btn svg {
  flex: none;
  height: 13px;
  opacity: 0.75;
  width: 13px;
}

.act-btn:hover {
  background: #e3edff;
  border-color: #c9d9f8;
  color: var(--accent-dark);
}

.act-btn:hover svg {
  opacity: 1;
}

.act-btn:active {
  transform: scale(0.98);
}

.empty {
  color: var(--text-3);
  font-size: 13.5px;
  padding: 100px 0;
  text-align: center;
}

.ip-doc-ls-fade-enter-active,
.ip-doc-ls-fade-leave-active {
  transition: opacity 0.2s ease;
}

.ip-doc-ls-fade-enter-from,
.ip-doc-ls-fade-leave-to {
  opacity: 0;
}

@media (max-width: 1180px) {
  .top-meta {
    display: none;
  }
}

@media (max-width: 1000px) {
  .toolbar-card {
    align-items: stretch;
    flex-direction: column;
    gap: 10px;
  }

  .toolbar-right {
    border-left: none;
    border-top: 1px solid var(--line);
    padding-left: 0;
    padding-top: 10px;
  }

  .search-wrap {
    flex: 1;
    width: auto;
  }
}

@media (max-width: 900px) {
  .clock {
    display: none;
  }

  .seg-group {
    overflow-x: auto;
  }

  .grid {
    grid-template-columns: 1fr;
  }

  .content {
    padding: 8px 14px 24px;
  }

  .toolbar-area {
    padding: 6px 14px 0;
  }

  .topbar {
    height: 44px;
    padding: 0 14px;
  }

  .brand-t h1 {
    font-size: 14px;
  }
}
</style>
