<script setup lang="ts">
import { computed, ref, watch } from 'vue';

import { Delete, Plus } from '@element-plus/icons-vue';
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElMessage,
} from 'element-plus';

import { WIDGET_OPTION_PRESETS } from '#/emr-canvas/constants/widget-options';

defineOptions({ name: 'InsertWidgetDialog' });

export type InsertWidgetKind = 'text' | 'number' | 'select' | 'radio' | 'date' | 'checkbox';

export interface InsertWidgetResult {
  kind: InsertWidgetKind;
  label: string;
  placeholder: string;
  options: { code: string; label: string }[];
}

const props = withDefaults(
  defineProps<{
    modelValue: boolean;
    kind: InsertWidgetKind;
  }>(),
  {
    modelValue: false,
    kind: 'text',
  },
);

const emit = defineEmits<{
  'update:modelValue': [boolean];
  confirm: [InsertWidgetResult];
}>();

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
});

const label = ref('');
const placeholder = ref('');
const options = ref<{ code: string; label: string }[]>([
  { code: '', label: '' },
  { code: '', label: '' },
]);

const dialogTitle = computed(() => {
  if (props.kind === 'text') return '插入自由文本';
  if (props.kind === 'number') return '插入数字';
  if (props.kind === 'select') return '插入下拉选择';
  if (props.kind === 'radio') return '插入单选';
  if (props.kind === 'date') return '插入日期';
  return '插入复选框';
});

const needOptions = computed(() => props.kind === 'select' || props.kind === 'radio');
const needPlaceholder = computed(
  () => props.kind === 'text' || props.kind === 'number' || props.kind === 'select' || props.kind === 'date',
);

function resetForm() {
  if (props.kind === 'text') {
    label.value = '填写';
    placeholder.value = '请在这里填写';
    options.value = [];
    return;
  }
  if (props.kind === 'number') {
    label.value = '数字';
    placeholder.value = '请在这里填写数字';
    options.value = [];
    return;
  }
  if (props.kind === 'select') {
    label.value = '';
    placeholder.value = '请选择';
    options.value = [
      { code: '', label: '' },
      { code: '', label: '' },
    ];
    return;
  }
  if (props.kind === 'radio') {
    label.value = '';
    placeholder.value = '';
    options.value = [
      { code: '', label: '' },
      { code: '', label: '' },
    ];
    return;
  }
  if (props.kind === 'date') {
    label.value = '日期';
    placeholder.value = '请选择日期';
    options.value = [];
    return;
  }
  label.value = '';
  placeholder.value = '';
  options.value = [];
}

watch(
  () => [props.modelValue, props.kind] as const,
  ([open]) => {
    if (open) resetForm();
  },
);

function applyPreset(key: string) {
  const preset = WIDGET_OPTION_PRESETS.find((item) => item.key === key);
  if (!preset) return;
  options.value = preset.items.map((item) => ({ code: item.code, label: item.label }));
  if (!label.value.trim()) label.value = preset.name;
  if (props.kind === 'select' && (!placeholder.value.trim() || placeholder.value === '请选择')) {
    placeholder.value = `请选择${preset.name}`;
  }
}

function addOptionRow() {
  options.value.push({ code: '', label: '' });
}

function removeOptionRow(index: number) {
  if (options.value.length <= 1) {
    options.value[0] = { code: '', label: '' };
    return;
  }
  options.value.splice(index, 1);
}

function onConfirm() {
  if (props.kind === 'checkbox') {
    const text = label.value.trim();
    if (!text) {
      ElMessage.warning('请填写复选框旁显示的文字');
      return;
    }
    emit('confirm', {
      kind: props.kind,
      label: text,
      placeholder: '',
      options: [{ code: '1', label: text }],
    });
    visible.value = false;
    return;
  }

  const name = label.value.trim() || (props.kind === 'date' ? '日期' : '填写');
  let tip = placeholder.value.trim();
  if (!tip) {
    if (props.kind === 'date') tip = '请选择日期';
    else if (props.kind === 'select') tip = '请选择';
    else tip = `请在这里填写${name}`;
  }

  if (needOptions.value) {
    const cleaned = options.value
      .map((item) => ({
        code: item.code.trim() || item.label.trim(),
        label: item.label.trim() || item.code.trim(),
      }))
      .filter((item) => item.code && item.label);
    if (cleaned.length < 2) {
      ElMessage.warning('请至少填写 2 个选项');
      return;
    }
    emit('confirm', { kind: props.kind, label: name, placeholder: tip, options: cleaned });
    visible.value = false;
    return;
  }

  emit('confirm', { kind: props.kind, label: name, placeholder: tip, options: [] });
  visible.value = false;
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="dialogTitle"
    width="520px"
    destroy-on-close
    append-to-body
  >
    <el-form label-width="96px" @submit.prevent>
      <el-form-item v-if="kind === 'checkbox'" label="勾选文字" required>
        <el-input v-model="label" maxlength="64" placeholder="如：同意以上条款、无药物过敏" clearable />
        <p class="hint">插入后显示为「□ 勾选文字」。</p>
      </el-form-item>
      <template v-else>
        <el-form-item :label="kind === 'text' || kind === 'number' ? '提示名称' : '显示名称'" required>
          <el-input
            v-model="label"
            maxlength="64"
            :placeholder="kind === 'text' ? '如：主诉、现病史' : '如：是否、性别、就诊日期'"
            clearable
          />
        </el-form-item>
        <el-form-item v-if="needPlaceholder" label="占位提示">
          <el-input
            v-model="placeholder"
            maxlength="64"
            :placeholder="kind === 'text' ? '空槽灰色提示，如：请在这里填写主诉' : '未选择时的灰色提示'"
            clearable
          />
        </el-form-item>
      </template>
      <template v-if="needOptions">
        <el-form-item label="快捷预设">
          <div class="presets">
            <el-button v-for="item in WIDGET_OPTION_PRESETS" :key="item.key" size="small" @click="applyPreset(item.key)">
              {{ item.name }}
            </el-button>
          </div>
        </el-form-item>
        <el-form-item label="数据源" required>
          <div class="opt-head">
            <span>存储值</span>
            <span>显示名</span>
            <span />
          </div>
          <div v-for="(row, index) in options" :key="index" class="opt-row">
            <el-input v-model="row.code" placeholder="如 Y / M" maxlength="64" />
            <el-input v-model="row.label" placeholder="如 是 / 男" maxlength="64" />
            <el-button type="danger" link :icon="Delete" @click="removeOptionRow(index)" />
          </div>
          <el-button type="primary" link @click="addOptionRow">
            <el-icon><Plus /></el-icon>
            添加选项
          </el-button>
          <p class="hint">文书存「存储值」，纸面显示「显示名」。不要用手输逗号拼选项。</p>
        </el-form-item>
      </template>
      <el-form-item v-else-if="kind === 'date'" label="说明">
        <p class="hint hint--solo">插入后为行内日期选择，值以 YYYY-MM-DD 写入。</p>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" @click="onConfirm">插入</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.presets {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.opt-head,
.opt-row {
  display: grid;
  grid-template-columns: 1fr 1fr 36px;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}

.opt-head {
  margin-bottom: 4px;
  font-size: 12px;
  color: #8c8c8c;
}

.hint {
  margin: 8px 0 0;
  width: 100%;
  font-size: 12px;
  line-height: 1.5;
  color: #8c8c8c;
}

.hint--solo {
  margin: 0;
}
</style>
