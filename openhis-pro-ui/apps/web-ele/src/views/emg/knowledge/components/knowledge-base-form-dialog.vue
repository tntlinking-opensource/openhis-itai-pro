<script setup lang="ts">
import type { KnowledgeBaseItem } from '#/api/emg/knowledge';

import { computed, reactive, ref, watch } from 'vue';

import { generatePinyinCode } from '#/utils';
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElOption,
  ElSelect,
} from 'element-plus';

interface Props {
  modelValue: boolean;
  knowledgeBase?: KnowledgeBaseItem | null;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: false,
  knowledgeBase: null,
});

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void;
  (e: 'success'): void;
}>();

const formRef = ref();

const form = reactive({
  id: null as null | number,
  name: '',
  pinyinCode: '',
  triageLevel: null as null | number,
  type: '',
  category: '',
  sortOrder: 0,
  status: 1,
});

const categories = [
  { label: '头部', value: 'HEAD' },
  { label: '胸部', value: 'CHEST' },
  { label: '腹部', value: 'ABDOMEN' },
  { label: '四肢', value: 'LIMBS' },
  { label: '全身', value: 'WHOLE' },
  { label: '其他', value: 'OTHER' },
];

const types = [
  { label: '症状', value: 'SYMPTOM' },
  { label: '疾病', value: 'DISEASE' },
  { label: '药物', value: 'MEDICINE' },
  { label: '其他', value: 'OTHER' },
];

const triageLevels = [
  { label: 'I 级-濒危', value: 1 },
  { label: 'II 级-危重', value: 2 },
  { label: 'III 级-急症', value: 3 },
  { label: 'IV 级-非急症', value: 4 },
  { label: 'V 级-亚急症', value: 5 },
];

const rules = {
  name: [{ required: true, message: '请输入知识库名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择知识库类型', trigger: 'change' }],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }],
  triageLevel: [{ required: true, message: '请选择分诊级别', trigger: 'change' }],
};

const dialogTitle = computed(() => {
  return props.knowledgeBase ? '编辑知识库' : '新增知识库';
});

watch(
  () => props.knowledgeBase,
  (newVal) => {
    if (newVal) {
      Object.assign(form, newVal);
    } else {
      resetForm();
    }
  },
  { immediate: true },
);

function resetForm() {
  Object.assign(form, {
    id: null,
    name: '',
    pinyinCode: '',
    triageLevel: null,
    type: '',
    category: '',
    sortOrder: 0,
    status: 1,
  });
}

function handleNameChange() {
  if (form.name) {
    form.pinyinCode = generatePinyinCode(form.name);
  }
}

function handleCancel() {
  emit('update:modelValue', false);
  resetForm();
}

async function handleSave() {
  if (!formRef.value) return;

  try {
    await formRef.value.validate();
    emit('update:modelValue', false);
    emit('success');
  } catch {
    // 验证失败，不执行后续操作
  }
}
</script>

<template>
  <ElDialog
    :model-value="modelValue"
    :title="dialogTitle"
    width="600px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <ElForm :model="form" :rules="rules" ref="formRef" label-width="100px">
      <ElFormItem label="名称" prop="name">
        <ElInput v-model="form.name" @input="handleNameChange" placeholder="请输入知识库名称" />
      </ElFormItem>
      <ElFormItem label="拼音码">
        <ElInput v-model="form.pinyinCode" disabled placeholder="自动生成" />
      </ElFormItem>
      <ElFormItem label="分诊级别" prop="triageLevel">
        <ElSelect v-model="form.triageLevel" placeholder="请选择分诊级别">
          <ElOption
            v-for="item in triageLevels"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="类型" prop="type">
        <ElSelect v-model="form.type" placeholder="请选择类型">
          <ElOption
            v-for="item in types"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="分类" prop="category">
        <ElSelect v-model="form.category" placeholder="请选择分类">
          <ElOption
            v-for="item in categories"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="序号">
        <ElInputNumber v-model="form.sortOrder" :min="0" controls-position="right" />
      </ElFormItem>
      <ElFormItem label="状态">
        <ElSelect v-model="form.status" placeholder="请选择状态">
          <ElOption label="启用" value="1" />
          <ElOption label="禁用" value="0" />
        </ElSelect>
      </ElFormItem>
    </ElForm>
    <template #footer>
      <span class="dialog-footer">
        <ElButton @click="handleCancel">取消</ElButton>
        <ElButton type="primary" @click="handleSave">保存</ElButton>
      </span>
    </template>
  </ElDialog>
</template>

<style scoped>
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

:deep(.el-form-item) {
  margin-bottom: 12px;
}
</style>
