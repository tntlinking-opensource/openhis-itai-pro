<script setup lang="ts">
import type { DoctorStationPatient } from './doctorPatientStore';

import type {
  OutpatientPatientCardPayload,
  PatientCardPatient,
} from '#/components/outpatient/patient-card.types';

import { ref } from 'vue';

import { RotateCw, Search } from '@vben/icons';
import { formatDateTime, getOutpatientStatusTagStyles, getPaymentTypeTagStyles } from '@vben/utils';

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
import { encounterQueueStatusDisplayText } from '#/utils/encounter-queue-status-text';

/* eslint-disable vue/define-macros-order -- defineModel 未列入团队 eslint 顺序表，与 defineEmits 交叉时误报 */

defineOptions({
  name: 'DoctorPatientList',
});

interface Props {
  currentQueue?: 'away' | 'completed' | 'current' | 'waiting';
  patients?: DoctorStationPatient[];
  selectedPatientId?: string;
  loading?: boolean;
  /** 待诊患者总数，用于「患者队列」页签角标 */
  waitingCount?: number;
  /** 父级正在提交接诊，忽略队列内二次双击避免重复请求 */
  receptionLocked?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  patients: () => [],
  selectedPatientId: '',
  loading: false,
  waitingCount: 0,
  receptionLocked: false,
});

const filterDate = defineModel<string>('filterDate', { required: true });

const emit = defineEmits<{
  accept: [patient: DoctorStationPatient];
  dateChange: [date: string];
  queueChange: [queue: 'away' | 'completed' | 'current' | 'waiting'];
  quickAction: [
    action: 'allergy' | 'exam' | 'lab' | 'pathology' | 'report' | 'surgery' | 'transfusion',
  ];
  refresh: [];
  search: [keyword: string];
  select: [patient: DoctorStationPatient];
}>();

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

/** 与 PatientCard 门诊底部费用标签一致（固定自费） */
const selfPayChipStyle = getPaymentTypeTagStyles('自费');

const outpatientStatusChipInlineStyle = (patient: DoctorStationPatient) => {
  const c = outpatientStatusChipStyle(patient);
  return { backgroundColor: c.bgColor, color: c.textColor };
};

const handleQueueRadioChange = (val: boolean | number | string | undefined) => {
  if (val === 'away' || val === 'completed' || val === 'current' || val === 'waiting') {
    emit('queueChange', val);
  }
};

const handleQuickAction = (
  action: 'allergy' | 'exam' | 'lab' | 'pathology' | 'report' | 'surgery' | 'transfusion',
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
    class="bg-background-card flex h-full w-[260px] flex-shrink-0 flex-col rounded-none transition-all duration-300"
  >
    <div class="mb-2 flex flex-col gap-2 px-3 pt-2">
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
        <ElRadioButton value="current" class="min-w-0 flex-1">在诊患者</ElRadioButton>
      </ElRadioGroup>
      <ElInput
        v-if="currentQueue !== 'current'"
        v-model="searchKeyword"
        placeholder="请输入患者名"
        clearable
        class="flex-1"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      >
        <template #prefix>
          <Search class="h-4 w-4" />
        </template>
      </ElInput>
      <div class="flex items-center gap-2">
        <ElDatePicker
          v-model="filterDate"
          class="w-full flex-1"
          type="date"
          format="YYYY-MM-DD"
          value-format="YYYY-MM-DD"
          placeholder="请选择日期"
          @change="handleDateChange"
        />
        <ElButton type="default" class="!px-2" @click="handleRefresh">
          <RotateCw class="h-4 w-4" :class="{ 'animate-spin': isRefreshing }" />
        </ElButton>
      </div>
    </div>

    <div class="flex-1 overflow-y-auto px-3">
      <div v-if="loading" class="text-text-sub py-8 text-center text-sm">加载中...</div>
      <div v-else-if="patients.length === 0" class="text-text-sub py-8 text-center text-sm">
        {{ currentQueue === 'waiting' ? '当前无待诊患者' : '双击左侧患者卡片接诊' }}
      </div>
      <div v-else-if="currentQueue === 'current'" class="space-y-2">
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
            <div class="patient-item__row">
              <span class="patient-item__label">性别</span>
              <span class="patient-item__value">{{ outpatientGenderDisplay(patient) }}</span>
            </div>
            <div class="patient-item__row patient-item__row--chips">
              <span class="patient-item__label">费用性质</span>
              <span class="patient-item__value patient-item__value--chips">
                <span
                  class="rounded-full px-2.5 py-0.5 text-xs font-medium"
                  :style="{
                    backgroundColor: selfPayChipStyle.bgColor,
                    color: selfPayChipStyle.textColor,
                  }"
                >
                  自费
                </span>
              </span>
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
              <span class="patient-item__label">电话号</span>
              <span class="patient-item__value">{{ formatField(patient.phone) }}</span>
            </div>
            <div class="patient-item__row patient-item__row--stack patient-item__row--last">
              <span class="patient-item__label">过敏史</span>
              <span class="patient-item__value patient-item__value--wrap">-</span>
            </div>
          </div>
        </div>
      </div>
      <div v-else class="space-y-2">
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
