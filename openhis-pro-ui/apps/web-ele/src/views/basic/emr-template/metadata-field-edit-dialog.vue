<script setup lang="ts">
import type { MetadataFieldDto } from '#/api/emr/metadata-field';

import { computed, reactive, ref, watch } from 'vue';

import {
  ElAlert,
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElSelect,
  ElSwitch,
} from 'element-plus';

import { createMetadataFieldApi, updateMetadataFieldApi } from '#/api/emr/metadata-field';

import {
  findMetadataSourcePathOption,
  getMetadataSourcePathOptions,
  getMetadataValueSourceHelp,
  METADATA_DATA_TYPE_OPTIONS,
  METADATA_FIELD_FORM_FIELD_HINTS,
  METADATA_FIELD_FORM_INTRO,
  METADATA_MASK_RULE_OPTIONS,
  METADATA_VALUE_SOURCE_OPTIONS,
} from './metadata-field.constants';

const emit = defineEmits<{
  success: [];
}>();

const visible = ref(false);
const dialogTitle = ref('新增元数据字段');
const saving = ref(false);
const mode = ref<'create' | 'edit'>('create');
const editingId = ref<null | string>(null);
const fieldKeyLocked = ref(false);

const form = reactive({
  fieldKey: '',
  label: '',
  dataType: 'string',
  editableDefault: true,
  valueSource: 'PATIENT',
  sourcePath: '',
  sourceConfigJson: '',
  defaultValue: '',
  maskRule: 'NONE',
  formatPattern: '',
  sortOrder: 0,
  isEnabled: true,
  remark: '',
});

const needsSourcePath = computed(() => {
  const s = form.valueSource;
  return ![
    'CONST',
    'MANUAL',
    'REQUEST',
    'FIELD_REF',
    'EXPR',
    'DICT',
    'READ_MODEL',
    'HTTP_API',
    'SETTING',
  ].includes(s);
});

const needsSourceConfig = computed(() =>
  ['DICT', 'READ_MODEL', 'EXPR', 'HTTP_API', 'FIELD_REF', 'SETTING'].includes(form.valueSource),
);

const sourcePathOptions = computed(() => getMetadataSourcePathOptions(form.valueSource));

const valueSourceHelp = computed(() => getMetadataValueSourceHelp(form.valueSource));

const sourcePathSelectHint = computed(() => {
  if (sourcePathOptions.value.length === 0) {
    return '当前取值来源暂无预置路径，可手动输入；需后端上下文已贡献该键才能解析出值。';
  }
  const matched = findMetadataSourcePathOption(form.valueSource, form.sourcePath);
  if (matched) {
    return matched.label;
  }
  if (form.sourcePath.trim() && !matched) {
    return '自定义路径：请确认后端已实现该键，否则将回退默认值。';
  }
  return '从列表选择路径；也可输入自定义路径（需后端支持）。';
});

watch(
  () => form.valueSource,
  (valueSource) => {
    const path = form.sourcePath.trim();
    if (!path) {
      return;
    }
    const options = getMetadataSourcePathOptions(valueSource);
    if (options.length > 0 && !options.some((o) => o.value === path)) {
      form.sourcePath = '';
    }
  },
);

function resetForm() {
  form.fieldKey = '';
  form.label = '';
  form.dataType = 'string';
  form.editableDefault = true;
  form.valueSource = 'PATIENT';
  form.sourcePath = '';
  form.sourceConfigJson = '';
  form.defaultValue = '';
  form.maskRule = 'NONE';
  form.formatPattern = '';
  form.sortOrder = 0;
  form.isEnabled = true;
  form.remark = '';
}

function parseSourceConfig(): Record<string, unknown> | undefined {
  const raw = form.sourceConfigJson.trim();
  if (!raw) return undefined;
  try {
    return JSON.parse(raw) as Record<string, unknown>;
  } catch {
    throw new Error('source_config 不是合法 JSON');
  }
}

function openCreate() {
  mode.value = 'create';
  editingId.value = null;
  fieldKeyLocked.value = false;
  dialogTitle.value = '新增元数据字段';
  resetForm();
  visible.value = true;
}

function openEdit(row: MetadataFieldDto) {
  const id = row.id;
  if (id === undefined || id === null) {
    ElMessage.error('缺少元数据字段 ID');
    return;
  }
  mode.value = 'edit';
  editingId.value = String(id);
  fieldKeyLocked.value = true;
  dialogTitle.value = '编辑元数据字段';
  form.fieldKey = row.fieldKey ?? '';
  form.label = row.label ?? '';
  form.dataType = row.dataType ?? 'string';
  form.editableDefault = row.editableDefault !== false;
  form.valueSource = row.valueSource ?? 'PATIENT';
  form.sourcePath = row.sourcePath ?? '';
  form.sourceConfigJson =
    row.sourceConfig && Object.keys(row.sourceConfig).length > 0
      ? JSON.stringify(row.sourceConfig, null, 2)
      : '';
  form.defaultValue = row.defaultValue ?? '';
  form.maskRule = row.maskRule ?? 'NONE';
  form.formatPattern = row.formatPattern ?? '';
  form.sortOrder = row.sortOrder ?? 0;
  form.isEnabled = row.isEnabled !== false;
  form.remark = row.remark ?? '';
  visible.value = true;
}

async function handleSave() {
  const fieldKey = form.fieldKey.trim();
  const label = form.label.trim();
  if (!fieldKey) {
    ElMessage.warning('请填写 fieldKey');
    return;
  }
  if (!label) {
    ElMessage.warning('请填写显示名');
    return;
  }
  if (!form.valueSource) {
    ElMessage.warning('请选择取值来源');
    return;
  }

  let sourceConfig: Record<string, unknown> | undefined;
  try {
    sourceConfig = needsSourceConfig.value ? parseSourceConfig() : undefined;
  } catch (e) {
    ElMessage.warning(e instanceof Error ? e.message : '配置 JSON 无效');
    return;
  }

  saving.value = true;
  try {
    if (mode.value === 'create') {
      await createMetadataFieldApi({
        fieldKey,
        label,
        dataType: form.dataType,
        editableDefault: form.editableDefault,
        valueSource: form.valueSource,
        sourcePath: form.sourcePath.trim() || undefined,
        sourceConfig,
        defaultValue: form.defaultValue.trim() || undefined,
        maskRule: form.maskRule,
        formatPattern: form.formatPattern.trim() || undefined,
        sortOrder: form.sortOrder,
        isEnabled: form.isEnabled,
        remark: form.remark.trim() || undefined,
      });
      ElMessage.success('新增成功');
    } else if (editingId.value) {
      await updateMetadataFieldApi({
        id: editingId.value,
        label,
        dataType: form.dataType,
        editableDefault: form.editableDefault,
        valueSource: form.valueSource,
        sourcePath: form.sourcePath.trim() || undefined,
        sourceConfig,
        defaultValue: form.defaultValue.trim() || undefined,
        maskRule: form.maskRule,
        formatPattern: form.formatPattern.trim() || undefined,
        sortOrder: form.sortOrder,
        isEnabled: form.isEnabled,
        remark: form.remark.trim() || undefined,
      });
      ElMessage.success('保存成功');
    }
    visible.value = false;
    emit('success');
  } catch (error) {
    console.error(error);
  } finally {
    saving.value = false;
  }
}

function closeDialog() {
  visible.value = false;
}

defineExpose({
  openCreate,
  openEdit,
});
</script>

<template>
  <ElDialog
    v-model="visible"
    :title="dialogTitle"
    width="720px"
    destroy-on-close
    @closed="resetForm"
  >
    <ElAlert class="mb-4" type="info" :closable="false" show-icon title="元数据字段说明">
      <p class="m-0 whitespace-pre-line text-xs leading-relaxed">
        {{ METADATA_FIELD_FORM_INTRO }}
      </p>
    </ElAlert>

    <ElForm label-width="108px" @submit.prevent="handleSave">
      <ElFormItem label="fieldKey" required>
        <ElInput
          v-model="form.fieldKey"
          :disabled="fieldKeyLocked"
          placeholder="如 patientName"
          maxlength="64"
        />
        <p class="metadata-field-form-hint">{{ METADATA_FIELD_FORM_FIELD_HINTS.fieldKey }}</p>
      </ElFormItem>
      <ElFormItem label="显示名" required>
        <ElInput v-model="form.label" placeholder="如 患者姓名" maxlength="128" />
        <p class="metadata-field-form-hint">{{ METADATA_FIELD_FORM_FIELD_HINTS.label }}</p>
      </ElFormItem>
      <ElFormItem label="数据类型">
        <ElSelect v-model="form.dataType" class="w-full">
          <ElOption
            v-for="opt in METADATA_DATA_TYPE_OPTIONS"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </ElSelect>
        <p class="metadata-field-form-hint">{{ METADATA_FIELD_FORM_FIELD_HINTS.dataType }}</p>
      </ElFormItem>
      <ElFormItem label="取值来源" required>
        <ElSelect v-model="form.valueSource" class="w-full" filterable>
          <ElOption
            v-for="opt in METADATA_VALUE_SOURCE_OPTIONS"
            :key="opt.value"
            :label="`${opt.label} (${opt.value})`"
            :value="opt.value"
          />
        </ElSelect>
        <p class="metadata-field-form-hint">{{ valueSourceHelp }}</p>
      </ElFormItem>
      <ElFormItem v-if="needsSourcePath" label="source_path">
        <ElSelect
          v-model="form.sourcePath"
          class="w-full"
          filterable
          allow-create
          clearable
          default-first-option
          placeholder="选择或输入点分路径，如 patient.name"
        >
          <ElOption
            v-for="opt in sourcePathOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </ElSelect>
        <p class="metadata-field-form-hint">{{ sourcePathSelectHint }}</p>
      </ElFormItem>
      <ElFormItem v-if="needsSourceConfig" label="source_config">
        <ElInput
          v-model="form.sourceConfigJson"
          type="textarea"
          :rows="4"
          placeholder='JSON，如 {"expr":"{patient.name}-{encounter.busNo}"}'
        />
        <p class="metadata-field-form-hint">{{ METADATA_FIELD_FORM_FIELD_HINTS.sourceConfig }}</p>
      </ElFormItem>
      <ElFormItem label="默认值">
        <ElInput v-model="form.defaultValue" placeholder="解析失败或 CONST 时使用" />
        <p class="metadata-field-form-hint">{{ METADATA_FIELD_FORM_FIELD_HINTS.defaultValue }}</p>
      </ElFormItem>
      <ElFormItem label="默认可编辑">
        <ElSwitch v-model="form.editableDefault" />
        <p class="metadata-field-form-hint">
          {{ METADATA_FIELD_FORM_FIELD_HINTS.editableDefault }}
        </p>
      </ElFormItem>
      <ElFormItem label="脱敏规则">
        <ElSelect v-model="form.maskRule" class="w-full">
          <ElOption
            v-for="opt in METADATA_MASK_RULE_OPTIONS"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </ElSelect>
        <p class="metadata-field-form-hint">{{ METADATA_FIELD_FORM_FIELD_HINTS.maskRule }}</p>
      </ElFormItem>
      <ElFormItem label="格式">
        <ElInput v-model="form.formatPattern" placeholder="日期等展示格式，可选" />
        <p class="metadata-field-form-hint">{{ METADATA_FIELD_FORM_FIELD_HINTS.formatPattern }}</p>
      </ElFormItem>
      <ElFormItem label="排序">
        <ElInputNumber v-model="form.sortOrder" :min="0" :max="9999" class="w-full" />
        <p class="metadata-field-form-hint">{{ METADATA_FIELD_FORM_FIELD_HINTS.sortOrder }}</p>
      </ElFormItem>
      <ElFormItem label="启用">
        <ElSwitch v-model="form.isEnabled" />
        <p class="metadata-field-form-hint">{{ METADATA_FIELD_FORM_FIELD_HINTS.isEnabled }}</p>
      </ElFormItem>
      <ElFormItem label="备注">
        <ElInput v-model="form.remark" type="textarea" :rows="2" />
        <p class="metadata-field-form-hint">{{ METADATA_FIELD_FORM_FIELD_HINTS.remark }}</p>
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton @click="closeDialog">取消</ElButton>
      <ElButton type="primary" :loading="saving" @click="handleSave">保存</ElButton>
    </template>
  </ElDialog>
</template>

<style scoped lang="scss">
.metadata-field-form-hint {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}
</style>
