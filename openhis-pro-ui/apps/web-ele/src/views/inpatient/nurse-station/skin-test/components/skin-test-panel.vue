<script setup lang="ts">
import type {
  NurseStationSkinTestEnumOptionDto,
  NurseStationSkinTestRowDto,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-skin-test.types';

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
  ElTooltip,
} from 'element-plus';

import SectionTitle from '#/components/layouts/section-title.vue';

import {
  calcInpatientSkinTestLastOccurrence,
  canDeleteInpatientSkinTestRow,
  canSaveInpatientSkinTestRow,
  isInpatientSkinTestNegativeClinicalCode,
  isInpatientSkinTestPositiveClinicalCode,
  mapInpatientSkinTestEnumOptions,
  mapInpatientSkinTestRowToDraft,
  resolveInpatientSkinTestRowKey,
  type InpatientSkinTestRowDraft,
} from '../services/map-nurse-station-skin-test';

defineOptions({
  name: 'InpatientNurseStationSkinTestPanel',
});

const props = withDefaults(defineProps<Props>(), {
  loading: false,
  rows: () => [],
  savingSkinTestServiceRequestId: '',
  deletingAllergyRecordId: '',
});

const emit = defineEmits<{
  deleteRow: [allergyRecordId: string];
  refresh: [];
  saveRow: [draft: InpatientSkinTestRowDraft];
}>();

interface Props {
  accountId: string;
  clinicalStatusOptions: NurseStationSkinTestEnumOptionDto[];
  verificationStatusOptions: NurseStationSkinTestEnumOptionDto[];
  criticalityOptions: NurseStationSkinTestEnumOptionDto[];
  severityOptions: NurseStationSkinTestEnumOptionDto[];
  practitionerOptions: { label: string; value: string }[];
  rows: NurseStationSkinTestRowDto[];
  loading: boolean;
  defaultPractitionerId?: string;
  /** 正在保存的 companion SR id */
  savingSkinTestServiceRequestId?: string;
  deletingAllergyRecordId?: string;
}

/** 草稿按 companion SR id 索引（同一皮试药可多行） */
const draftBySkinTestServiceRequestId = ref<Record<string, InpatientSkinTestRowDraft>>({});

const clinicalSelectOptions = computed(() =>
  mapInpatientSkinTestEnumOptions(props.clinicalStatusOptions),
);

const showEmptyState = computed(() => !props.loading && props.rows.length === 0);

/** 同步接口行到本地草稿 */
function syncDraftsFromRows(rows: NurseStationSkinTestRowDto[]) {
  const next: Record<string, InpatientSkinTestRowDraft> = {};
  // 按 companion SR 主键挂草稿，避免同药多行互相覆盖
  for (const row of rows) {
    const key = resolveInpatientSkinTestRowKey(row);
    if (!key) {
      continue;
    }
    next[key] = mapInpatientSkinTestRowToDraft(
      row,
      props.accountId,
      props.defaultPractitionerId,
      props.verificationStatusOptions,
    );
  }
  draftBySkinTestServiceRequestId.value = next;
}

watch(
  () => [props.rows, props.accountId, props.defaultPractitionerId] as const,
  ([rows]) => {
    syncDraftsFromRows(rows);
  },
  { immediate: true, deep: true },
);

/** 取行草稿 */
function getDraft(row: NurseStationSkinTestRowDto): InpatientSkinTestRowDraft {
  const key = resolveInpatientSkinTestRowKey(row);
  const existing = draftBySkinTestServiceRequestId.value[key];
  if (existing) {
    return existing;
  }
  return mapInpatientSkinTestRowToDraft(
    row,
    props.accountId,
    props.defaultPractitionerId,
    props.verificationStatusOptions,
  );
}

/** 行是否已登记 */
function isRowRegistered(row: NurseStationSkinTestRowDto): boolean {
  return Boolean(String(row.allergyRecordId ?? '').trim());
}

/** 表单是否禁用（未执行或已登记只读） */
function isRowFormDisabled(row: NurseStationSkinTestRowDto): boolean {
  if (row.executedFlag !== true) {
    return true;
  }
  if (row.readOnlyFlag === true) {
    return true;
  }
  return isRowRegistered(row);
}

/** 执行态 Tag */
function executedTagType(row: NurseStationSkinTestRowDto): 'success' | 'warning' {
  return row.executedFlag === true ? 'success' : 'warning';
}

/** 合并行内已保存护士，避免下拉只显示 ID */
function practitionerOptionsForRow(
  row: NurseStationSkinTestRowDto,
): { label: string; value: string }[] {
  const merged = [...props.practitionerOptions];
  const append = (id: string | null | undefined, name?: string | null) => {
    const value = String(id ?? '').trim();
    if (!value || merged.some((opt) => opt.value === value)) {
      return;
    }
    merged.push({
      value,
      label: String(name ?? '').trim() || value,
    });
  };
  append(row.practitionerId, row.practitionerName);
  append(row.checkPractitionerId, row.checkPractitionerName);
  return merged;
}

/** 校验单行草稿 */
function validateDraft(draft: InpatientSkinTestRowDraft): string | null {
  if (!(draft.lotNumber ?? '').trim()) {
    return '请选择本次取药批号（须先执行皮试药）';
  }
  if (!draft.clinicalStatusCode.trim()) {
    return '请选择临床结果';
  }
  if (!draft.practitionerId.trim()) {
    return '请选择执行护士';
  }
  if (!draft.checkPractitionerId.trim()) {
    return '请选择核对护士';
  }
  if (!(draft.recordedDate ?? '').trim()) {
    return '请选择开始时间';
  }
  return null;
}

/** 开始时间变更：末次反应时间联动为 +20 分钟 */
function handleStartTimeChange(draft: InpatientSkinTestRowDraft, value: string | null) {
  const start = (value ?? '').trim() || undefined;
  draft.recordedDate = start;
  draft.lastOccurrence = calcInpatientSkinTestLastOccurrence(start);
}

/** 行是否可点保存（含批号） */
function canSaveRow(row: NurseStationSkinTestRowDto): boolean {
  return canSaveInpatientSkinTestRow(row, getDraft(row).lotNumber);
}

/** 保存按钮禁用时的提示文案 */
function saveDisabledTooltip(row: NurseStationSkinTestRowDto): string {
  if (row.executedFlag !== true) {
    return '请先在「医嘱执行」完成皮试检查';
  }
  if (isRowRegistered(row) || row.readOnlyFlag === true) {
    return '已登记结果只读，误录请删除后重登';
  }
  return '请选择本次取药批号';
}

/** 提交当前行保存 */
function handleSaveRow(row: NurseStationSkinTestRowDto) {
  if (isRowFormDisabled(row)) {
    if (row.executedFlag !== true) {
      ElMessage.warning('请先在「医嘱执行」完成皮试检查后再登记结果');
      return;
    }
    ElMessage.warning('该皮试行已登记，如需修正请先删除误录结果');
    return;
  }
  const draft = getDraft(row);
  if (!canSaveInpatientSkinTestRow(row, draft.lotNumber)) {
    ElMessage.warning('请选择本次取药批号（须先执行皮试药）');
    return;
  }
  const err = validateDraft(draft);
  if (err) {
    ElMessage.warning(err);
    return;
  }
  emit('saveRow', draft);
}

/** 删除误录行 */
function handleDeleteRow(row: NurseStationSkinTestRowDto) {
  const allergyRecordId = String(row.allergyRecordId ?? '').trim();
  if (!allergyRecordId || !canDeleteInpatientSkinTestRow(row)) {
    return;
  }
  emit('deleteRow', allergyRecordId);
}

/** 批号下拉候选 */
function lotOptionsForRow(row: NurseStationSkinTestRowDto) {
  return (row.lotNumberOptions ?? []).filter((opt) => (opt.lotNumber ?? '').trim().length > 0);
}

/** 当前行是否保存中 */
function isRowSaving(row: NurseStationSkinTestRowDto): boolean {
  return props.savingSkinTestServiceRequestId === resolveInpatientSkinTestRowKey(row);
}

/** 当前行是否删除中 */
function isRowDeleting(row: NurseStationSkinTestRowDto): boolean {
  return props.deletingAllergyRecordId === String(row.allergyRecordId ?? '').trim();
}

/** 临床结果是否为阳性（左侧条红色） */
function isPositiveClinicalRow(row: NurseStationSkinTestRowDto): boolean {
  return isInpatientSkinTestPositiveClinicalCode(getDraft(row).clinicalStatusCode);
}

/** 临床结果是否为阴性（左侧条绿色） */
function isNegativeClinicalRow(row: NurseStationSkinTestRowDto): boolean {
  return isInpatientSkinTestNegativeClinicalCode(getDraft(row).clinicalStatusCode);
}
</script>

<template>
  <div class="skin-test-center flex h-full min-h-0 min-w-0 flex-1 flex-col">
    <div class="skin-test-center__header bg-background flex flex-col gap-2 px-2 pb-1.5 pt-2">
      <SectionTitle title="皮试列表" />
      <div class="flex min-w-0 flex-wrap items-center justify-end gap-2">
        <ElButton size="default" :loading="loading" @click="emit('refresh')">刷新</ElButton>
      </div>
    </div>

    <div
      v-loading="loading"
      class="skin-test-center__body bg-background flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden px-2 pb-2 pt-0 sm:px-2.5"
    >
      <div v-if="!showEmptyState" class="skin-test-center__list">
        <p class="skin-test-center__summary">
          共 <strong>{{ rows.length }}</strong>
          项皮试；请先在「医嘱执行」完成皮试检查后，再逐项登记结果并双签保存。
        </p>

        <article
          v-for="row in rows"
          :key="resolveInpatientSkinTestRowKey(row)"
          class="skin-test-card"
          :class="{
            'skin-test-card--registered': isRowRegistered(row),
            'skin-test-card--negative': isNegativeClinicalRow(row),
            'skin-test-card--positive': isPositiveClinicalRow(row),
          }"
        >
          <header class="skin-test-card__header">
            <div class="skin-test-card__title-group">
              <ElTag
                size="small"
                effect="light"
                :type="isRowRegistered(row) ? 'success' : 'primary'"
              >
                {{ isRowRegistered(row) ? '已登记' : '待登记' }}
              </ElTag>
              <h3 class="skin-test-card__title" :title="row.medicationName ?? ''">
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
              <ElTag size="small" effect="light" :type="executedTagType(row)">
                {{ row.executedFlag === true ? '已执行' : '待执行' }}
              </ElTag>
            </div>
          </header>

          <div class="skin-test-card__body">
            <p class="skin-test-card__section-title">皮试结果</p>
            <ElRow :gutter="12" class="skin-test-card__row">
              <ElCol :xs="24" :sm="12" :md="6">
                <ElFormItem
                  label="批号"
                  label-position="top"
                  required
                  class="skin-test-card__field"
                >
                  <ElSelect
                    v-model="getDraft(row).lotNumber"
                    class="w-full"
                    clearable
                    filterable
                    placeholder="选批号（须先执行皮试药）"
                    :disabled="isRowFormDisabled(row)"
                  >
                    <ElOption
                      v-for="opt in lotOptionsForRow(row)"
                      :key="`${opt.medicationId}-${opt.locationId}-${opt.lotNumber}`"
                      :label="opt.lotNumber ?? ''"
                      :value="opt.lotNumber ?? ''"
                    />
                  </ElSelect>
                </ElFormItem>
              </ElCol>
              <ElCol :xs="24" :sm="12" :md="6">
                <ElFormItem
                  label="临床结果"
                  label-position="top"
                  required
                  class="skin-test-card__field"
                >
                  <ElSelect
                    v-model="getDraft(row).clinicalStatusCode"
                    class="w-full"
                    placeholder="必选"
                    :disabled="isRowFormDisabled(row)"
                  >
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
                  label="执行护士"
                  label-position="top"
                  required
                  class="skin-test-card__field"
                >
                  <ElSelect
                    v-model="getDraft(row).practitionerId"
                    class="w-full"
                    placeholder="必选"
                    filterable
                    :disabled="isRowFormDisabled(row)"
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
                    v-model="getDraft(row).checkPractitionerId"
                    class="w-full"
                    placeholder="必选"
                    filterable
                    :disabled="isRowFormDisabled(row)"
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
            </ElRow>

            <ElRow :gutter="12" class="skin-test-card__row skin-test-card__row--spaced">
              <ElCol :xs="24" :sm="12" :md="6">
                <ElFormItem
                  label="开始时间"
                  label-position="top"
                  required
                  class="skin-test-card__field"
                >
                  <ElDatePicker
                    :model-value="getDraft(row).recordedDate"
                    class="!w-full"
                    type="datetime"
                    format="YYYY-MM-DD HH:mm:ss"
                    value-format="YYYY-MM-DD HH:mm:ss"
                    placeholder="默认当前"
                    clearable
                    :disabled="isRowFormDisabled(row)"
                    @update:model-value="(value) => handleStartTimeChange(getDraft(row), value)"
                  />
                </ElFormItem>
              </ElCol>
              <ElCol :xs="24" :sm="12" :md="6">
                <ElFormItem label="结束时间" label-position="top" class="skin-test-card__field">
                  <ElDatePicker
                    :model-value="getDraft(row).lastOccurrence"
                    class="!w-full"
                    type="datetime"
                    format="YYYY-MM-DD HH:mm:ss"
                    value-format="YYYY-MM-DD HH:mm:ss"
                    placeholder="开始时间 + 20 分钟"
                    disabled
                  />
                </ElFormItem>
              </ElCol>
            </ElRow>

            <ElRow :gutter="12" class="skin-test-card__row">
              <ElCol :span="24">
                <ElFormItem label="反应描述" label-position="top" class="skin-test-card__field">
                  <ElInput
                    v-model="getDraft(row).description"
                    placeholder="可选，记录局部反应或备注"
                    clearable
                    :disabled="isRowFormDisabled(row)"
                  />
                </ElFormItem>
              </ElCol>
            </ElRow>
          </div>

          <footer class="skin-test-card__footer">
            <ElTooltip v-if="!canSaveRow(row)" :content="saveDisabledTooltip(row)" placement="top">
              <ElButton type="primary" disabled>保存</ElButton>
            </ElTooltip>
            <ElButton v-else type="primary" :loading="isRowSaving(row)" @click="handleSaveRow(row)">
              保存
            </ElButton>
            <ElButton
              v-if="canDeleteInpatientSkinTestRow(row)"
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
        <ElEmpty description="暂无已校对的皮试医嘱">
          <p class="skin-test-center__empty-hint">
            请先校对皮试药与皮试检查，并在「医嘱执行」完成皮试检查后再登记结果。
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

.skin-test-card--negative {
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
