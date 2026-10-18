<script setup lang="ts">
import type { DoctorStationPatient } from './doctorPatientStore';

import type {
  OutpatientPatientCardPayload,
  PatientCardPatient,
} from '#/components/outpatient/patient-card.types';

import { computed, ref } from 'vue';

import { RotateCw, Search } from '@zxplat/icons';
import { formatDateTime, getOutpatientStatusTagStyles, getPaymentTypeTagStyles } from '@zxplat/utils';

import dayjs from 'dayjs';
import {
  ElBadge,
  ElButton,
  ElDatePicker,
  ElInput,
  ElRadioButton,
  ElRadioGroup,
} from 'element-plus';

import PatientCard from '#/components/outpatient/patient-card.vue';
import { formatInsutype, formatInsutypeShort } from '#/chs/constants/reconciliation';
import { useSingleDayDatePickerShortcuts } from '#/composables/use-single-day-date-picker-shortcuts';
import { encounterQueueStatusDisplayText } from '#/utils/outpatient/encounter-queue-status-text';

import PatientAllergySkinTestSection from './components/patient-allergy-skin-test-section.vue';

/* eslint-disable vue/define-macros-order -- defineModel 未列入团队 eslint 顺序表，与 defineEmits 交叉时误报 */

defineOptions({
  name: 'DoctorPatientList',
});

/** 患者队列 Tab 内：全部 / 待诊 / 在诊 / 完诊 */
export type DoctorWaitingQueueStatusFilter = 'all' | 'completed' | 'in-progress' | 'waiting';

interface Props {
  currentQueue?: 'away' | 'completed' | 'current' | 'waiting';
  patients?: DoctorStationPatient[];
  selectedPatientId?: string;
  loading?: boolean;
  /** 待诊患者总数，用于「患者队列」页签角标 */
  waitingCount?: number;
  /** 吸底统计：待诊人数（当前关键字结果集） */
  waitingStatusStatCount?: number;
  /** 吸底统计：已接诊人数（在诊/暂离，当前关键字结果集） */
  receivedStatusStatCount?: number;
  /** 父级正在提交接诊，忽略队列内二次双击避免重复请求 */
  receptionLocked?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  patients: () => [],
  selectedPatientId: '',
  loading: false,
  waitingCount: 0,
  waitingStatusStatCount: 0,
  receivedStatusStatCount: 0,
  receptionLocked: false,
});

const filterDate = defineModel<string>('filterDate', { required: true });
const queueStatusFilter = defineModel<DoctorWaitingQueueStatusFilter>('queueStatusFilter', {
  default: 'waiting',
});

const emit = defineEmits<{
  accept: [patient: DoctorStationPatient];
  dateChange: [date: string];
  queueChange: [queue: 'away' | 'completed' | 'current' | 'waiting'];
  queueStatusFilterChange: [filter: DoctorWaitingQueueStatusFilter];
  quickAction: [
    action:
      | 'allergy'
      | 'exam'
      | 'lab'
      | 'pathology'
      | 'report'
      | 'surgery'
      | 'transfusion'
      | 'treatment',
  ];
  refresh: [];
  search: [keyword: string];
  select: [patient: DoctorStationPatient];
}>();

const { dateShortcuts, singleDayPopperClass } = useSingleDayDatePickerShortcuts(
  () => filterDate.value,
  (ymd) => {
    filterDate.value = ymd;
    emit('dateChange', ymd);
  },
);

const searchKeyword = ref('');
const isRefreshing = ref(false);

const handleSearch = () => {
  emit('search', searchKeyword.value);
};

const handleSelect = (patient: DoctorStationPatient) => {
  emit('select', patient);
};

/** PatientCard emit 为并集类型，医生站侧栏仅处理门诊 DTO。 */
const handlePatientCardSelect = (card: PatientCardPatient) => {
  if ('patientType' in card && card.patientType === 'inpatient') return;
  const id = (card as OutpatientPatientCardPayload).encounterId;
  const row = props.patients.find((p) => p.encounterId === id);
  if (row) handleSelect(row);
};

const handleAccept = (patient: DoctorStationPatient) => {
  emit('accept', patient);
};

// 患者队列内双击卡片触发接诊（单击仍为选中）；在诊态不调写接口由父级处理，此处仍须把事件交给父级以完成切 Tab / 选中。
const handleQueueCardDblClick = (patient: DoctorStationPatient) => {
  if (props.receptionLocked) return;
  handleAccept(patient);
};

const formatField = (value: null | number | string | undefined) => {
  if (value === null || value === undefined || value === '') return '-';
  return String(value);
};

/** 展示性别：直接使用接口 `genderEnumText` */
function outpatientGenderDisplay(patient: DoctorStationPatient): string {
  return (patient.genderEnumText ?? '').trim() || '-';
}

/** 与 PatientCard 门诊卡片一致：年龄行带「岁」 */
const ageLineOutpatient = (ageText: null | string | undefined) => {
  const t = (ageText ?? '').trim();
  if (!t) return '-';
  if (t.includes('岁')) return t;
  return `${t}岁`;
};

/** 与 PatientCard `formatOutpatientVisitTime` 一致 */
const formatOutpatientVisitTime = (patient: DoctorStationPatient) => {
  const r = patient.receptionTime ? formatDateTime(patient.receptionTime) : '';
  if (r && r !== '-') return r;
  const s = patient.startTime ? formatDateTime(patient.startTime) : '';
  if (s && s !== '-') return s;
  if (patient.receptionTime) return String(patient.receptionTime);
  if (patient.startTime) return String(patient.startTime);
  return '-';
};

/** 与 PatientCard `bottomLeftNo` 一致：就诊业务号优先，否则 encounterId */
const cardBottomRecordNo = (patient: DoctorStationPatient) => {
  const bus = (patient.encounterBusNo ?? '').trim();
  if (bus) return bus;
  const id = (patient.encounterId ?? '').trim();
  return id || '-';
};

const outpatientStatusChipStyle = (patient: DoctorStationPatient) => {
  const text =
    encounterQueueStatusDisplayText(patient.statusEnum, patient.statusEnumText).trim() || '待诊';
  return getOutpatientStatusTagStyles(text);
};

/** 当前接诊卡费用性质胶囊样式（读后端 contractDisplay） */
const feeNatureChipStyle = (patient: DoctorStationPatient) => {
  const label = (patient.contractDisplay ?? '').trim() || '自费';
  return getPaymentTypeTagStyles(label);
};

const feeNatureChipLabel = (patient: DoctorStationPatient) =>
  (patient.contractDisplay ?? '').trim() || '自费';

const insutypeChipLabel = (patient: DoctorStationPatient) => formatInsutypeShort(patient.insutype);

const insutypeChipTitle = (patient: DoctorStationPatient) => {
  const full = formatInsutype(patient.insutype);
  return full && full !== '—' ? full : '';
};

const outpatientStatusChipInlineStyle = (patient: DoctorStationPatient) => {
  const c = outpatientStatusChipStyle(patient);
  return { backgroundColor: c.bgColor, color: c.textColor };
};

/** 「当前接诊」禁止手点切换，仅接诊成功后由父级程序切入 */
const handleQueueRadioChange = (val: boolean | number | string | undefined) => {
  if (val === 'current') return;
  if (val === 'away' || val === 'completed' || val === 'waiting') {
    emit('queueChange', val);
  }
};

/** 患者队列底栏：全部 / 待诊 / 在诊 / 完诊文字 Tab 项 */
const queueStatusTabs: Array<{ label: string; value: DoctorWaitingQueueStatusFilter }> = [
  { label: '全部', value: 'all' },
  { label: '待诊', value: 'waiting' },
  { label: '在诊', value: 'in-progress' },
  { label: '完诊', value: 'completed' },
];

/** 患者队列内切换待诊 / 在诊 / 完诊筛选 */
const handleQueueStatusFilterPick = (filter: DoctorWaitingQueueStatusFilter) => {
  if (queueStatusFilter.value === filter) return;
  queueStatusFilter.value = filter;
  emit('queueStatusFilterChange', filter);
};

/** 患者队列空态文案：随状态筛选变化 */
const waitingQueueEmptyHint = computed(() => {
  if (queueStatusFilter.value === 'all') return '当前无患者';
  if (queueStatusFilter.value === 'in-progress') return '当前无在诊患者';
  if (queueStatusFilter.value === 'completed') return '当前无完诊患者';
  return '当前无待诊患者';
});

const handleQuickAction = (
  action:
    | 'allergy'
    | 'exam'
    | 'lab'
    | 'pathology'
    | 'report'
    | 'surgery'
    | 'transfusion'
    | 'treatment',
) => {
  emit('quickAction', action);
};

// 日期变更后写回 v-model 并通知父级拉列表（父级可 watch filterDate 或监听本事件）。
const handleDateChange = (date: string | undefined) => {
  const next = date && date !== '' ? date : dayjs().format('YYYY-MM-DD');
  filterDate.value = next;
  emit('dateChange', next);
};

const handleRefresh = () => {
  isRefreshing.value = true;
  emit('refresh');
  setTimeout(() => {
    isRefreshing.value = false;
  }, 600);
};
</script>

<template>
  <div
    class="doctor-patient-list bg-background-card flex h-full flex-shrink-0 flex-col rounded-none transition-all duration-300"
  >
    <div class="doctor-patient-list__header mb-2 flex flex-col gap-2 px-3 pt-2">
      <ElRadioGroup
        class="doctor-queue-radio flex w-full gap-2"
        :model-value="currentQueue"
        @change="handleQueueRadioChange"
      >
        <div class="doctor-queue-tab-first relative flex min-w-0 flex-1">
          <ElRadioButton value="waiting" class="min-w-0 flex-1">患者队列</ElRadioButton>
          <ElBadge
            class="pointer-events-none absolute inset-0 z-[1]"
            :value="waitingCount"
            :max="99"
            :hidden="waitingCount < 1"
          >
            <span class="block h-full w-full" aria-hidden="true"></span>
          </ElBadge>
        </div>
        <ElRadioButton
          value="current"
          class="min-w-0 flex-1"
          disabled
          title="请从患者队列双击接诊进入"
        >
          当前接诊
        </ElRadioButton>
      </ElRadioGroup>
      <div class="flex items-center gap-2">
        <ElDatePicker
          v-model="filterDate"
          class="w-full flex-1"
          type="date"
          format="YYYY-MM-DD"
          value-format="YYYY-MM-DD"
          placeholder="请选择日期"
          :shortcuts="dateShortcuts"
          :popper-class="singleDayPopperClass"
          @change="handleDateChange"
        />
        <ElButton type="default" class="!px-2" @click="handleRefresh">
          <RotateCw class="h-4 w-4" :class="{ 'animate-spin': isRefreshing }" />
        </ElButton>
      </div>
      <ElInput
        v-if="currentQueue === 'waiting'"
        v-model="searchKeyword"
        placeholder="请输入患者名"
        clearable
        class="w-full"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      >
        <template #prefix>
          <Search class="h-4 w-4" />
        </template>
      </ElInput>
    </div>

    <div class="doctor-patient-list__scroll min-h-0 flex-1 overflow-y-auto px-3">
      <div v-if="loading" class="text-text-sub py-8 text-center text-sm">加载中...</div>
      <div v-else-if="patients.length === 0" class="text-text-sub py-8 text-center text-sm">
        {{
          currentQueue === 'waiting'
            ? waitingQueueEmptyHint
            : '暂无当前接诊患者，请从患者队列双击接诊'
        }}
      </div>
      <div v-else-if="currentQueue === 'current'" class="doctor-patient-list__card-list space-y-2">
        <div
          v-for="patient in patients"
          :key="patient.encounterId"
          class="patient-item"
          :class="{ 'is-selected': patient.encounterId === selectedPatientId }"
          @click="handleSelect(patient)"
        >
          <div class="patient-item__header">
            <div class="patient-item__name-row">
              <span class="patient-item__name">{{ formatField(patient.patientName) }}</span>
              <span class="patient-item__meta">
                {{ outpatientGenderDisplay(patient) }} · {{ ageLineOutpatient(patient.ageText) }} ·
                {{ formatField((patient.serviceTypeName ?? '').trim()) }}
              </span>
            </div>
            <span
              class="patient-item__status-chip shrink-0 rounded-full px-2.5 py-0.5 text-xs font-medium"
              :style="outpatientStatusChipInlineStyle(patient)"
            >
              {{
                encounterQueueStatusDisplayText(patient.statusEnum, patient.statusEnumText) ||
                '待诊'
              }}
            </span>
          </div>
          <div class="patient-item__body">
            <div class="patient-item__row">
              <span class="patient-item__label">病历号</span>
              <span class="patient-item__value">{{ cardBottomRecordNo(patient) }}</span>
            </div>
            <div class="patient-item__row patient-item__row--chips">
              <span class="patient-item__label">费用性质</span>
              <span class="patient-item__value patient-item__value--chips">
                <span
                  class="rounded-full px-2.5 py-0.5 text-xs font-medium"
                  :style="{
                    backgroundColor: feeNatureChipStyle(patient).bgColor,
                    color: feeNatureChipStyle(patient).textColor,
                  }"
                >
                  {{ feeNatureChipLabel(patient) }}
                </span>
                <span
                  v-if="insutypeChipLabel(patient)"
                  class="ml-1 rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-700"
                  :title="insutypeChipTitle(patient) || undefined"
                >
                  {{ insutypeChipLabel(patient) }}
                </span>
                <span
                  v-if="patient.workInjuryEncounter"
                  class="ml-1 rounded-full bg-amber-100 px-2.5 py-0.5 text-xs font-medium text-amber-800"
                  :title="
                    patient.workInjuryApprovalStatusLabel
                      ? `工伤备案：${patient.workInjuryApprovalStatusLabel}`
                      : '工伤就诊（备案请在挂号处办理）'
                  "
                >
                  工伤{{
                    patient.workInjuryQualificationValid
                      ? '·已备案'
                      : patient.workInjuryApprovalStatusLabel
                        ? `·${patient.workInjuryApprovalStatusLabel}`
                        : '·未备案'
                  }}
                </span>
              </span>
            </div>
            <div class="patient-item__row">
              <span class="patient-item__label">电话号</span>
              <span class="patient-item__value">{{ formatField(patient.phone) }}</span>
            </div>
            <div class="patient-item__row">
              <span class="patient-item__label">身份证号</span>
              <span class="patient-item__value">{{ formatField(patient.idCard) }}</span>
            </div>
            <div class="patient-item__row">
              <span class="patient-item__label">就诊时间</span>
              <span class="patient-item__value">{{ formatOutpatientVisitTime(patient) }}</span>
            </div>
            <div class="patient-item__row">
              <span class="patient-item__label">挂号科室</span>
              <span class="patient-item__value">{{ formatField(patient.organizationName) }}</span>
            </div>
            <div class="patient-item__row">
              <span class="patient-item__label">地址</span>
              <span class="patient-item__value">{{ formatField(patient.address) }}</span>
            </div>
            <PatientAllergySkinTestSection
              :patient-id="patient.patientId ?? ''"
              :encounter-id="patient.encounterId"
            />
          </div>
        </div>
      </div>
      <div v-else class="doctor-patient-list__card-list space-y-2">
        <div
          v-for="patient in patients"
          :key="patient.encounterId"
          class="patient-card-dblclick-wrap"
          @dblclick="handleQueueCardDblClick(patient)"
        >
          <PatientCard
            variant="doctor"
            :patient="patient"
            :selected="patient.encounterId === selectedPatientId"
            @select="handlePatientCardSelect"
          />
        </div>
      </div>
    </div>

    <div
      v-if="currentQueue === 'waiting'"
      class="doctor-queue-bottom-bar border-border-base mt-auto border-t"
    >
      <div class="doctor-queue-bottom-bar__tabs" role="tablist" aria-label="患者队列状态筛选">
        <span
          v-for="tab in queueStatusTabs"
          :key="tab.value"
          role="tab"
          class="doctor-queue-status-tab"
          :class="{ 'is-active': queueStatusFilter === tab.value }"
          :aria-selected="queueStatusFilter === tab.value"
          @click="handleQueueStatusFilterPick(tab.value)"
        >
          {{ tab.label }}
        </span>
      </div>
      <div class="doctor-queue-bottom-bar__stats">
        <span>待诊 {{ waitingStatusStatCount }}</span>
        <span class="doctor-queue-bottom-bar__stats-sep">·</span>
        <span>已接诊 {{ receivedStatusStatCount }}</span>
      </div>
    </div>

    <div
      v-if="currentQueue === 'current'"
      class="quick-action-footer border-border-base mt-auto border-t"
    >
      <div class="quick-action-list">
        <button
          type="button"
          class="quick-action-item"
          :disabled="!selectedPatientId"
          @click="handleQuickAction('exam')"
        >
          <span>检查申请</span>
          <span class="quick-action-arrow">></span>
        </button>
        <button
          type="button"
          class="quick-action-item"
          :disabled="!selectedPatientId"
          @click="handleQuickAction('lab')"
        >
          <span>检验申请</span>
          <span class="quick-action-arrow">></span>
        </button>
        <button
          type="button"
          class="quick-action-item"
          :disabled="!selectedPatientId"
          @click="handleQuickAction('treatment')"
        >
          <span>治疗申请</span>
          <span class="quick-action-arrow">></span>
        </button>
        <button
          type="button"
          class="quick-action-item"
          :disabled="!selectedPatientId"
          @click="handleQuickAction('transfusion')"
        >
          <span>输血申请</span>
          <span class="quick-action-arrow">></span>
        </button>
        <button
          type="button"
          class="quick-action-item"
          :disabled="!selectedPatientId"
          @click="handleQuickAction('pathology')"
        >
          <span>病理申请</span>
          <span class="quick-action-arrow">></span>
        </button>
        <button
          type="button"
          class="quick-action-item"
          :disabled="!selectedPatientId"
          @click="handleQuickAction('surgery')"
        >
          <span>手术申请</span>
          <span class="quick-action-arrow">></span>
        </button>
        <button
          type="button"
          class="quick-action-item"
          :disabled="!selectedPatientId"
          @click="handleQuickAction('report')"
        >
          <span>报告查询</span>
          <span class="quick-action-arrow">></span>
        </button>
        <button
          type="button"
          class="quick-action-item"
          :disabled="!selectedPatientId"
          @click="handleQuickAction('allergy')"
        >
          <span>过敏史</span>
          <span class="quick-action-arrow">></span>
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.doctor-patient-list {
  /* default：DOCTOR_STATION_SIDEBAR_WIDTH_PX.patientQueue */
  --doctor-queue-width: 260px;
  width: var(--doctor-queue-width);
}

.doctor-queue-radio :deep(.el-radio-button) {
  flex: 1;
}

.doctor-queue-radio :deep(.el-radio-button__inner) {
  width: 100%;
  padding-left: 8px;
  padding-right: 8px;
  font-size: 12px;
  font-weight: 600;
}

/* 与左侧块拉开间距后：未选中时补回左边框；选中时勿用浅色左边框，并去掉 EP 为「接缝」画的左侧阴影，避免左侧异色竖线。 */
.doctor-queue-radio
  :deep(.doctor-queue-tab-first + .el-radio-button:not(.is-active) .el-radio-button__inner) {
  border-left: 1px solid var(--el-border-color);
}

.doctor-queue-radio
  :deep(.doctor-queue-tab-first + .el-radio-button.is-active .el-radio-button__inner) {
  box-shadow: none !important;
}

/* 程序切入「当前接诊」后：disabled 仍保持选中态主色，避免灰掉看不清 */
.doctor-queue-radio :deep(.el-radio-button.is-active.is-disabled .el-radio-button__inner) {
  color: var(--el-radio-button-checked-text-color, var(--el-color-white));
  background-color: var(--el-radio-button-checked-bg-color, var(--el-color-primary));
  border-color: var(--el-radio-button-checked-border-color, var(--el-color-primary));
  cursor: not-allowed;
  opacity: 1;
}

.doctor-queue-bottom-bar {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px 12px 10px;
  background-color: var(--el-fill-color-blank);
}

.doctor-queue-bottom-bar__tabs {
  display: flex;
  align-items: center;
  justify-content: center;
}

.doctor-queue-status-tab {
  position: relative;
  flex: 1;
  padding: 2px 0 8px;
  font-size: 13px;
  line-height: 20px;
  text-align: center;
  color: var(--el-text-color-regular);
  cursor: pointer;
  transition: color 0.15s ease;
  user-select: none;
}

.doctor-queue-status-tab:not(:last-child)::after {
  content: '';
  position: absolute;
  top: 50%;
  right: 0;
  width: 1px;
  height: 12px;
  transform: translateY(calc(-50% - 3px));
  background-color: var(--el-border-color-lighter);
}

.doctor-queue-status-tab.is-active {
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.doctor-queue-status-tab.is-active::before {
  content: '';
  position: absolute;
  bottom: 0;
  left: 50%;
  width: 18px;
  height: 2px;
  border-radius: 999px;
  transform: translateX(-50%);
  background-color: var(--el-color-primary);
}

.doctor-queue-status-tab:hover:not(.is-active) {
  color: var(--el-text-color-primary);
}

.doctor-queue-bottom-bar__stats {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 13px;
  line-height: 18px;
  color: var(--el-text-color-regular);
}

.doctor-queue-bottom-bar__stats-sep {
  color: var(--el-text-color-secondary);
}

.patient-card-dblclick-wrap {
  display: block;
}

.patient-item {
  cursor: pointer;
  overflow: hidden;
  border: 1px solid var(--el-border-color-extra-light);
  border-radius: 12px;
  background-color: var(--el-bg-color);
  box-shadow: 0 1px 2px rgb(0 0 0 / 4%);
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}

.patient-item:hover {
  border-color: var(--el-border-color-lighter);
  box-shadow: 0 2px 8px rgb(0 0 0 / 6%);
}

.patient-item.is-selected {
  border-color: var(--el-border-color);
  box-shadow:
    0 0 0 1px var(--el-border-color-lighter),
    0 2px 10px rgb(0 0 0 / 7%);
}

.patient-item__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  padding: 12px 12px 10px;
  background-color: var(--el-fill-color-blank);
  border-bottom: 1px solid var(--el-border-color-extra-light);
}

.patient-item__name-row {
  min-width: 0;
}

.patient-item__name {
  display: block;
  font-size: 16px;
  line-height: 22px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  letter-spacing: 0.02em;
}

.patient-item__meta {
  margin-top: 2px;
  font-size: 12px;
  line-height: 18px;
  color: var(--el-text-color-regular);
}

/* 与 PatientCard 就诊状态 pill 一致，配色由 getOutpatientStatusTagStyles 内联 */
.patient-item__status-chip {
  flex-shrink: 0;
  line-height: 18px;
}

.patient-item__body {
  padding: 4px 12px 10px;
}

.patient-item__row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid var(--el-border-color-extra-light);
}

.patient-item__row--last {
  border-bottom: none;
  padding-bottom: 4px;
}

.patient-item__row--stack {
  flex-wrap: wrap;
}

.patient-item__row--stack .patient-item__value {
  flex: 1 1 100%;
  margin-top: 2px;
  text-align: left;
}

.patient-item__label {
  flex-shrink: 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 18px;
}

.patient-item__value {
  min-width: 0;
  color: var(--el-text-color-primary);
  font-size: 12px;
  line-height: 18px;
  text-align: right;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.patient-item__value--wrap {
  white-space: normal;
  overflow: visible;
  text-overflow: unset;
  line-height: 1.45;
}

.patient-item__value--chips {
  overflow: visible;
  flex-shrink: 0;
}

.quick-action-footer {
  padding: 10px 10px 12px;
  background-color: var(--el-fill-color-blank);
}

.quick-action-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.quick-action-item {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background-color: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
  box-shadow: 0 1px 2px rgb(0 0 0 / 4%);
  font-size: 13px;
  line-height: 20px;
  padding: 8px 12px;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    background-color 0.2s ease;
}

.quick-action-item:hover:not(:disabled) {
  border-color: var(--el-border-color);
  background-color: var(--el-fill-color);
  box-shadow: 0 1px 4px rgb(0 0 0 / 7%);
}

.quick-action-item:disabled {
  cursor: not-allowed;
  border-color: var(--el-border-color-extra-light);
  box-shadow: none;
  color: var(--el-text-color-disabled);
  background-color: var(--el-fill-color-lighter);
}

.quick-action-arrow {
  font-size: 12px;
  font-weight: 500;
  color: var(--el-text-color-placeholder);
  opacity: 0.85;
}
</style>
