<script setup lang="ts">
import type { NurseSkinTestRowDraft, NurseSkinTestTableRow } from '../services/map-nurse-skin-test';
import type { NursePatient } from './types';

import type {
  OutpatientSkinTestEnumOptionDto,
  OutpatientSkinTestRowDto,
} from '#/api/outpatient-nurse-station/outpatient-nurse-station.types';
import type { PatientInfoBarPayload } from '#/components/outpatient/patient-card.types';

import { computed, ref, watch } from 'vue';

import {
  ElButton,
  ElCol,
  ElDatePicker,
  ElEmpty,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElRow,
  ElSelect,
  ElTag,
} from 'element-plus';

import SectionTitle from '#/components/layouts/section-title.vue';
import PatientInfoBar from '#/components/outpatient/patient-info-bar.vue';

import {
  isSkinTestPositiveClinicalCode,
  mapSkinTestEnumOptions,
  mapSkinTestRowToTableRow,
  mapSkinTestTableRowToDraft,
} from '../services/map-nurse-skin-test';

defineOptions({
  name: 'NurseSkinTestCenter',
});

const props = withDefaults(defineProps<Props>(), {
  skinTestTableLoading: false,
  savingServiceRequestId: '',
  deletingAllergyRecordId: '',
});

const emit = defineEmits<{
  deleteRow: [allergyRecordId: string];
  refresh: [];
  saveRow: [draft: NurseSkinTestRowDraft];
}>();

interface Props {
  clinicalStatusOptions: OutpatientSkinTestEnumOptionDto[];
  verificationStatusOptions: OutpatientSkinTestEnumOptionDto[];
  criticalityOptions: OutpatientSkinTestEnumOptionDto[];
  severityOptions: OutpatientSkinTestEnumOptionDto[];
  practitionerOptions: { label: string; value: string }[];
  currentPatient: null | NursePatient;
  patient: PatientInfoBarPayload;
  doctorName: string;
  skinTestRows: OutpatientSkinTestRowDto[];
  skinTestTableLoading?: boolean;
  defaultPractitionerId?: string;
  defaultPractitionerName?: string;
  savingServiceRequestId?: string;
  deletingAllergyRecordId?: string;
}

const cardRows = ref<NurseSkinTestTableRow[]>([]);

const clinicalSelectOptions = computed(() => mapSkinTestEnumOptions(props.clinicalStatusOptions));
const verificationSelectOptions = computed(() =>
  mapSkinTestEnumOptions(props.verificationStatusOptions),
);
const criticalitySelectOptions = computed(() => mapSkinTestEnumOptions(props.criticalityOptions));
const severitySelectOptions = computed(() => mapSkinTestEnumOptions(props.severityOptions));

const showEmptyState = computed(
  () => !props.skinTestTableLoading && props.skinTestRows.length === 0,
);

/** 合并行内已保存护士与默认登录护士，避免下拉只显示 ID */
function practitionerOptionsForRow(row: NurseSkinTestTableRow): { label: string; value: string }[] {
  const merged = [...props.practitionerOptions];
  const append = (id: string, name?: string) => {
    const value = String(id ?? '').trim();
    if (!value || merged.some((opt) => opt.value === value)) {
      return;
    }
    merged.push({
      value,
      label: String(name ?? '').trim() || value,
    });
  };
  const defaultName = String(props.defaultPractitionerName ?? '').trim();
  append(props.defaultPractitionerId ?? '', defaultName === '—' ? '' : defaultName);
  append(row.practitionerId, row.practitionerName);
  append(row.checkPractitionerId, row.checkPractitionerName);
  return merged;
}

/** 接口行同步到卡片草稿列表 */
function syncCardRowsFromApi() {
  cardRows.value = props.skinTestRows.map((row) =>
    mapSkinTestRowToTableRow(row, props.defaultPractitionerId),
  );
}

watch(
  () =>
    [
      props.skinTestRows,
      props.defaultPractitionerId,
      props.defaultPractitionerName,
      props.practitionerOptions,
    ] as const,
  () => {
    syncCardRowsFromApi();
  },
  { immediate: true },
);

/** 校验单行草稿必填项 */
function validateDraft(draft: NurseSkinTestRowDraft): null | string {
  if (!draft.clinicalStatusCode.trim()) {
    return '请选择临床结果';
  }
  if (!draft.verificationStatusCode.trim()) {
    return '请选择验证状态';
  }
  if (!draft.practitionerId.trim()) {
    return '请选择执行护士';
  }
  if (!draft.checkPractitionerId.trim()) {
    return '请选择核对护士';
  }
  if (!draft.patientId.trim()) {
    return '患者信息无效，请刷新列表';
  }
  return null;
}

/** 提交当前卡片保存 */
function handleSaveRow(row: NurseSkinTestTableRow) {
  const draft = mapSkinTestTableRowToDraft(row);
  const err = validateDraft(draft);
  if (err) {
    ElMessage.warning(err);
    return;
  }
  emit('saveRow', draft);
}

/** 删除已登记卡片 */
function handleDeleteRow(row: NurseSkinTestTableRow) {
  const allergyRecordId = String(row.allergyRecordId ?? '').trim();
  if (!allergyRecordId) {
    return;
  }
  emit('deleteRow', allergyRecordId);
}

/** 当前卡片是否保存中 */
function isRowSaving(row: NurseSkinTestTableRow): boolean {
  return props.savingServiceRequestId === String(row.serviceRequestId ?? '').trim();
}

/** 当前卡片是否删除中 */
function isRowDeleting(row: NurseSkinTestTableRow): boolean {
  return props.deletingAllergyRecordId === String(row.allergyRecordId ?? '').trim();
}

/** 临床结果是否为阳性（卡片高亮） */
function isPositiveClinicalRow(row: NurseSkinTestTableRow): boolean {
  return isSkinTestPositiveClinicalCode(row.clinicalStatusCode);
}
</script>

<template>
  <div class="skin-test-center flex h-full min-h-0 min-w-0 flex-1 flex-col">
    <div class="skin-test-center__header bg-background flex flex-col gap-2 px-2 pb-1.5 pt-2">
      <SectionTitle title="皮试列表" />
      <div class="flex min-w-0 flex-wrap items-center justify-between gap-2">
        <div class="min-w-0 flex-1">
          <PatientInfoBar v-if="currentPatient" :patient="patient" :doctor-name="doctorName" />
          <div v-else class="text-text-sub py-2 text-sm">请选择患者</div>
        </div>
        <ElButton size="default" :loading="skinTestTableLoading" @click="emit('refresh')">
          刷新
        </ElButton>
      </div>
    </div>

    <div
      v-loading="skinTestTableLoading"
      class="skin-test-center__body bg-background flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden px-2 pb-2 pt-0 sm:px-2.5"
    >
      <div v-if="!showEmptyState" class="skin-test-center__list">
        <p class="skin-test-center__summary">
          共 <strong>{{ cardRows.length }}</strong> 项皮试，请逐项登记结果并双签保存。
        </p>

        <article
          v-for="row in cardRows"
          :key="row.serviceRequestId"
          class="skin-test-card"
          :class="{
            'skin-test-card--registered': row.registered,
            'skin-test-card--positive': isPositiveClinicalRow(row),
          }"
        >
          <header class="skin-test-card__header">
            <div class="skin-test-card__title-group">
              <ElTag size="small" effect="light" :type="row.registered ? 'success' : 'primary'">
                {{ row.registered ? '已登记' : '待登记' }}
              </ElTag>
              <h3 class="skin-test-card__title" :title="row.medicationName">
                {{ row.medicationName }}
              </h3>
            </div>
            <div class="skin-test-card__meta">
              <span class="skin-test-card__meta-item">
                <span class="skin-test-card__meta-label">开立医生</span>
                <span class="skin-test-card__meta-value">
                  {{ String(row.openDoctorName ?? '').trim() || '—' }}
                </span>
              </span>
              <ElTag size="small" effect="light" type="success">已收费</ElTag>
            </div>
          </header>

          <div class="skin-test-card__body">
            <p class="skin-test-card__section-title">皮试结果</p>
            <ElRow :gutter="12" class="skin-test-card__row">
              <ElCol :xs="24" :sm="12" :md="6">
                <ElFormItem
                  label="临床结果"
                  label-position="top"
                  required
                  class="skin-test-card__field"
                >
                  <ElSelect v-model="row.clinicalStatusCode" class="w-full" placeholder="必选">
                    <ElOption
                      v-for="opt in clinicalSelectOptions"
                      :key="opt.value"
                      :label="opt.label"
                      :value="opt.value"
                    />
                  </ElSelect>
                </ElFormItem>
              </ElCol>
              <ElCol :xs="24" :sm="12" :md="6">
                <ElFormItem
                  label="验证状态"
                  label-position="top"
                  required
                  class="skin-test-card__field"
                >
                  <ElSelect v-model="row.verificationStatusCode" class="w-full" placeholder="必选">
                    <ElOption
                      v-for="opt in verificationSelectOptions"
                      :key="opt.value"
                      :label="opt.label"
                      :value="opt.value"
                    />
                  </ElSelect>
                </ElFormItem>
              </ElCol>
              <ElCol :xs="24" :sm="12" :md="6">
                <ElFormItem label="危险程度" label-position="top" class="skin-test-card__field">
                  <ElSelect
                    v-model="row.criticalityCode"
                    class="w-full"
                    placeholder="可选"
                    clearable
                  >
                    <ElOption
                      v-for="opt in criticalitySelectOptions"
                      :key="opt.value"
                      :label="opt.label"
                      :value="opt.value"
                    />
                  </ElSelect>
                </ElFormItem>
              </ElCol>
              <ElCol :xs="24" :sm="12" :md="6">
                <ElFormItem label="严重程度" label-position="top" class="skin-test-card__field">
                  <ElSelect v-model="row.severityCode" class="w-full" placeholder="可选" clearable>
                    <ElOption
                      v-for="opt in severitySelectOptions"
                      :key="opt.value"
                      :label="opt.label"
                      :value="opt.value"
                    />
                  </ElSelect>
                </ElFormItem>
              </ElCol>
            </ElRow>

            <ElRow :gutter="12" class="skin-test-card__row skin-test-card__row--spaced">
              <ElCol :xs="24" :sm="12" :md="6">
                <ElFormItem
                  label="执行护士"
                  label-position="top"
                  required
                  class="skin-test-card__field"
                >
                  <ElSelect
                    v-model="row.practitionerId"
                    class="w-full"
                    placeholder="必选"
                    filterable
                  >
                    <ElOption
                      v-for="opt in practitionerOptionsForRow(row)"
                      :key="opt.value"
                      :label="opt.label"
                      :value="opt.value"
                    />
                  </ElSelect>
                </ElFormItem>
              </ElCol>
              <ElCol :xs="24" :sm="12" :md="6">
                <ElFormItem
                  label="核对护士"
                  label-position="top"
                  required
                  class="skin-test-card__field"
                >
                  <ElSelect
                    v-model="row.checkPractitionerId"
                    class="w-full"
                    placeholder="必选"
                    filterable
                  >
                    <ElOption
                      v-for="opt in practitionerOptionsForRow(row)"
                      :key="`check-${opt.value}`"
                      :label="opt.label"
                      :value="opt.value"
                    />
                  </ElSelect>
                </ElFormItem>
              </ElCol>
              <ElCol :xs="24" :sm="12" :md="6">
                <ElFormItem label="记录时间" label-position="top" class="skin-test-card__field">
                  <ElDatePicker
                    v-model="row.recordedDate"
                    class="!w-full"
                    type="datetime"
                    format="YYYY-MM-DD HH:mm:ss"
                    value-format="YYYY-MM-DD HH:mm:ss"
                    placeholder="默认当前"
                    clearable
                  />
                </ElFormItem>
              </ElCol>
              <ElCol :xs="24" :sm="12" :md="6" />
            </ElRow>

            <ElRow :gutter="12" class="skin-test-card__row">
              <ElCol :span="24">
                <ElFormItem label="反应描述" label-position="top" class="skin-test-card__field">
                  <ElInput
                    v-model="row.description"
                    placeholder="可选，记录局部反应或备注"
                    clearable
                  />
                </ElFormItem>
              </ElCol>
            </ElRow>
          </div>

          <footer class="skin-test-card__footer">
            <ElButton type="primary" :loading="isRowSaving(row)" @click="handleSaveRow(row)">
              保存
            </ElButton>
            <ElButton
              v-if="row.registered"
              type="danger"
              plain
              :loading="isRowDeleting(row)"
              @click="handleDeleteRow(row)"
            >
              删除
            </ElButton>
          </footer>
        </article>
      </div>

      <div v-else class="skin-test-center__empty">
        <ElEmpty description="暂无待登记皮试">
          <p class="skin-test-center__empty-hint">
            医生已开立皮试但未收费时列表为空，请先完成门诊皮试费结算。
          </p>
        </ElEmpty>
      </div>
    </div>
  </div>
</template>

<style scoped>
.skin-test-center__body {
  min-height: 0;
}

.skin-test-center__list {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
  overflow: auto;
  padding-bottom: 4px;
  width: 100%;
}

.skin-test-center__summary {
  color: var(--el-text-color-regular);
  flex-shrink: 0;
  font-size: 13px;
  line-height: 1.5;
  margin: 0;
  padding: 2px 2px 0;
}

.skin-test-center__summary strong {
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.skin-test-card {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-left: 4px solid var(--el-color-primary);
  border-radius: 8px;
  box-shadow: 0 1px 3px rgb(15 23 42 / 5%);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  overflow: hidden;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease;
  width: 100%;
}

.skin-test-card:hover {
  box-shadow: 0 2px 8px rgb(15 23 42 / 8%);
}

.skin-test-card--registered {
  border-left-color: var(--el-color-success);
}

.skin-test-card--positive {
  border-left-color: var(--el-color-danger);
  box-shadow: 0 0 0 1px rgb(245 108 108 / 12%);
}

.skin-test-card__header {
  align-items: center;
  background: linear-gradient(180deg, #f5f9ff 0%, #fff 100%);
  border-bottom: 1px solid var(--el-border-color-lighter);
  display: flex;
  flex-wrap: wrap;
  gap: 10px 16px;
  justify-content: space-between;
  min-height: 52px;
  padding: 10px 14px;
}

.skin-test-card__title-group {
  align-items: center;
  display: flex;
  flex: 1;
  gap: 10px;
  min-width: 0;
}

.skin-test-card__title {
  color: var(--el-text-color-primary);
  font-size: 15px;
  font-weight: 600;
  line-height: 1.4;
  margin: 0;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.skin-test-card__meta {
  align-items: center;
  display: flex;
  flex-shrink: 0;
  gap: 12px;
}

.skin-test-card__meta-item {
  align-items: baseline;
  display: inline-flex;
  gap: 6px;
}

.skin-test-card__meta-label {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.skin-test-card__meta-value {
  color: var(--el-text-color-primary);
  font-size: 13px;
}

.skin-test-card__body {
  padding: 12px 14px 4px;
}

.skin-test-card__section-title {
  border-left: 3px solid #8ec5ff;
  color: var(--el-text-color-primary);
  font-size: 13px;
  font-weight: 600;
  line-height: 1.2;
  margin: 0 0 10px;
  padding-left: 8px;
}

.skin-test-card__row + .skin-test-card__section-title,
.skin-test-card__row--spaced {
  margin-top: 4px;
}

.skin-test-card__field {
  margin-bottom: 10px;
}

.skin-test-card__field :deep(.el-form-item__label) {
  color: var(--el-text-color-regular);
  font-size: 13px;
  line-height: 1.2;
  margin-bottom: 6px;
  padding: 0;
}

.skin-test-card__field :deep(.el-form-item__content) {
  line-height: 1;
}

.skin-test-card__footer {
  align-items: center;
  background: #fafbfc;
  border-top: 1px solid var(--el-border-color-lighter);
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  padding: 10px 14px;
}

.skin-test-center__empty {
  align-items: center;
  display: flex;
  flex: 1;
  justify-content: center;
  min-height: 240px;
}

.skin-test-center__empty-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin: 8px 0 0;
}
</style>
