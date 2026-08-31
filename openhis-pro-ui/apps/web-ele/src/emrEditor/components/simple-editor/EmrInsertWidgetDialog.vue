<script setup lang="ts">
import type { EmrDataElementWidget, EmrWidgetOptionItem } from './data-element-types';

import { computed, ref, watch } from 'vue';

import { Delete, Plus } from '@element-plus/icons-vue';
import {
  ElButton,
  ElCheckbox,
  ElDialog,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElMessage,
} from 'element-plus';

import { isOptionListWidget } from './data-element-types';
import { EMR_WIDGET_OPTION_PRESETS, serializeEmrWidgetOptions } from './emr-widget-options';

defineOptions({ name: 'EmrInsertWidgetDialog' });

export interface EmrInsertWidgetResult {
  label: string;
  optionsJson: string;
  placeholder: string;
  required: boolean;
  requiredMessage: string;
  widget: EmrDataElementWidget;
}

interface Props {
  modelValue: boolean;
  /** 本次插入的控件类型 */
  widget: EmrDataElementWidget;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: false,
  widget: 'select',
});

const emit = defineEmits<{
  'update:modelValue': [boolean];
  confirm: [EmrInsertWidgetResult];
}>();

const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v),
});

const label = ref('');
const placeholder = ref('');
const required = ref(false);
const requiredMessage = ref('');
const options = ref<EmrWidgetOptionItem[]>([
  { value: '', label: '' },
  { value: '', label: '' },
]);

const dialogTitle = computed(() => {
  if (props.widget === 'text') return '插入自由文本';
  if (props.widget === 'select') return '插入下拉选择';
  if (props.widget === 'radio') return '插入单选';
  if (props.widget === 'date') return '插入日期';
  if (props.widget === 'checkbox') return '插入复选框';
  return '插入控件';
});

const needOptions = computed(() => isOptionListWidget(props.widget));
const needPlaceholder = computed(
  () => props.widget === 'text' || props.widget === 'select' || props.widget === 'date',
);

/** 按控件类型重置表单 */
function resetForm() {
  required.value = false;
  requiredMessage.value = '';
  if (props.widget === 'text') {
    label.value = '填写';
    placeholder.value = '填写';
    options.value = [];
    return;
  }
  if (props.widget === 'select') {
    label.value = '';
    placeholder.value = '请选择';
    options.value = [
      { value: '', label: '' },
      { value: '', label: '' },
    ];
    return;
  }
  if (props.widget === 'radio') {
    label.value = '';
    placeholder.value = '';
    options.value = [
      { value: '', label: '' },
      { value: '', label: '' },
    ];
    return;
  }
  if (props.widget === 'date') {
    label.value = '日期';
    placeholder.value = '选择日期';
    options.value = [];
    return;
  }
  if (props.widget === 'checkbox') {
    label.value = '';
    placeholder.value = '';
    options.value = [];
    return;
  }
  label.value = '';
  placeholder.value = '';
  options.value = [];
}

watch(
  () => [props.modelValue, props.widget] as const,
  ([open]) => {
    if (open) resetForm();
  },
);

/** 套用本地预设到选项表 */
function applyPreset(key: string) {
  const preset = EMR_WIDGET_OPTION_PRESETS.find((p) => p.key === key);
  if (!preset) return;
  options.value = preset.items.map((it) => ({ ...it }));
  if (!label.value.trim()) {
    label.value = preset.name;
  }
  // 仅下拉补占位；单选不需要 placeholder
  if (props.widget === 'select' && (!placeholder.value.trim() || placeholder.value === '请选择')) {
    placeholder.value = `请选择${preset.name}`;
  }
}

/** 新增一行选项 */
function addOptionRow() {
  options.value.push({ value: '', label: '' });
}

/** 删除一行选项 */
function removeOptionRow(index: number) {
  if (options.value.length <= 1) {
    options.value[0] = { value: '', label: '' };
    return;
  }
  options.value.splice(index, 1);
}

/** 组装必填字段 */
function buildRequiredFields(name: string): {
  required: boolean;
  requiredMessage: string;
} {
  if (!required.value) {
    return { required: false, requiredMessage: '' };
  }
  return {
    required: true,
    requiredMessage: requiredMessage.value.trim(),
  };
}

/** 校验并确认插入 */
function onConfirm() {
  if (props.widget === 'checkbox') {
    const text = label.value.trim();
    if (!text) {
      ElMessage.warning('请填写复选框旁显示的文字');
      return;
    }
    const req = buildRequiredFields(text);
    emit('confirm', {
      widget: props.widget,
      label: text,
      placeholder: '',
      optionsJson: '[]',
      ...req,
    });
    visible.value = false;
    return;
  }

  const name = label.value.trim() || (props.widget === 'date' ? '日期' : '填写');
  let tip = placeholder.value.trim();
  if (!tip) {
    if (props.widget === 'date') tip = '选择日期';
    else if (props.widget === 'select') tip = '请选择';
    else if (props.widget === 'radio' || props.widget === 'checkbox') tip = '';
    else tip = name;
  }

  if (needOptions.value) {
    const cleaned = options.value
      .map((it) => ({
        value: String(it.value ?? '').trim(),
        label: String(it.label ?? '').trim(),
      }))
      .filter((it) => it.value || it.label)
      .map((it) => ({
        value: it.value || it.label,
        label: it.label || it.value,
      }));
    if (cleaned.length < 2) {
      ElMessage.warning('请至少填写 2 个选项（存储值或显示名）');
      return;
    }
    const codes = new Set(cleaned.map((c) => c.value));
    if (codes.size !== cleaned.length) {
      ElMessage.warning('选项的存储值不能重复');
      return;
    }
    const req = buildRequiredFields(name);
    emit('confirm', {
      widget: props.widget,
      label: name,
      placeholder: tip,
      optionsJson: serializeEmrWidgetOptions(cleaned),
      ...req,
    });
    visible.value = false;
    return;
  }

  const req = buildRequiredFields(name);
  emit('confirm', {
    widget: props.widget,
    label: name,
    placeholder: tip,
    optionsJson: '[]',
    ...req,
  });
  visible.value = false;
}

/** 取消关闭 */
function onCancel() {
  visible.value = false;
}
</script>

<template>
  <ElDialog
    v-model="visible"
    :title="dialogTitle"
    width="520px"
    destroy-on-close
    append-to-body
    @closed="onCancel"
  >
    <ElForm label-width="96px" @submit.prevent>
      <ElFormItem v-if="widget === 'checkbox'" label="勾选文字" required>
        <ElInput
          v-model="label"
          maxlength="64"
          placeholder="如：同意以上条款、无药物过敏"
          clearable
        />
        <p class="emr-insert-widget__hint">
          插入后显示为「□ 勾选文字」。勾选「必填」表示保存前必须勾选。
        </p>
      </ElFormItem>

      <template v-else>
        <ElFormItem :label="widget === 'text' ? '提示名称' : '显示名称'" required>
          <ElInput
            v-model="label"
            maxlength="64"
            :placeholder="widget === 'text' ? '如：主诉、现病史' : '如：是否、性别、就诊日期'"
            clearable
          />
        </ElFormItem>

        <ElFormItem v-if="needPlaceholder" label="占位提示">
          <ElInput
            v-model="placeholder"
            maxlength="64"
            :placeholder="
              widget === 'text'
                ? '空槽灰色提示，如：请填写主诉'
                : '未选择时显示的灰色提示，如：请选择性别'
            "
            clearable
          />
        </ElFormItem>
      </template>

      <template v-if="needOptions">
        <ElFormItem label="快捷预设">
          <div class="emr-insert-widget__presets">
            <ElButton
              v-for="p in EMR_WIDGET_OPTION_PRESETS"
              :key="p.key"
              size="small"
              @click="applyPreset(p.key)"
            >
              {{ p.name }}
            </ElButton>
          </div>
        </ElFormItem>

        <ElFormItem label="数据源" required>
          <div class="emr-insert-widget__options">
            <div class="emr-insert-widget__option-head">
              <span>存储值</span>
              <span>显示名</span>
              <span></span>
            </div>
            <div v-for="(row, idx) in options" :key="idx" class="emr-insert-widget__option-row">
              <ElInput v-model="row.value" placeholder="如 Y / M" maxlength="64" />
              <ElInput v-model="row.label" placeholder="如 是 / 男" maxlength="64" />
              <ElButton
                type="danger"
                link
                :icon="Delete"
                aria-label="删除选项"
                @click="removeOptionRow(idx)"
              />
            </div>
            <ElButton type="primary" link @click="addOptionRow">
              <ElIcon><Plus /></ElIcon>
              添加选项
            </ElButton>
            <p class="emr-insert-widget__hint">
              选项仅保存在本模板 JSON 中。文书存「存储值」，纸面显示「显示名」。
            </p>
          </div>
        </ElFormItem>
      </template>

      <ElFormItem v-else-if="widget === 'date'" label="说明">
        <p class="emr-insert-widget__hint emr-insert-widget__hint--solo">
          插入后为行内日期选择，值以 YYYY-MM-DD 写入文书 JSON。
        </p>
      </ElFormItem>

      <ElFormItem label="必填校验">
        <div class="emr-insert-widget__required">
          <ElCheckbox v-model="required">保存前必须填写</ElCheckbox>
          <ElInput
            v-if="required"
            v-model="requiredMessage"
            maxlength="128"
            placeholder="错误提示（可空，默认：请填写{名称}）"
            clearable
          />
        </div>
      </ElFormItem>
    </ElForm>

    <template #footer>
      <ElButton @click="onCancel">取消</ElButton>
      <ElButton type="primary" @click="onConfirm">插入</ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.emr-insert-widget__presets {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.emr-insert-widget__options {
  width: 100%;
}

.emr-insert-widget__option-head,
.emr-insert-widget__option-row {
  display: grid;
  grid-template-columns: 1fr 1fr 36px;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}

.emr-insert-widget__option-head {
  margin-bottom: 4px;
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.emr-insert-widget__hint {
  margin: 8px 0 0;
  width: 100%;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-regular);
}

.emr-insert-widget__hint--solo {
  margin: 0;
}

.emr-insert-widget__required {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}
</style>
