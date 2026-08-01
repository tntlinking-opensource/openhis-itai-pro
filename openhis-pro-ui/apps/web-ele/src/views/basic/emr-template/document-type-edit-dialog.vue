<script setup lang="ts">
import type { DocumentTypeDto } from '#/api/emr/document-type';
import type { TemplateDto } from '#/api/emr/template';

import { reactive, ref, watch } from 'vue';

import {
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

import {
  createDocumentTypeApi,
  getDocumentTypeApi,
  updateDocumentTypeApi,
} from '#/api/emr/document-type';
import { getTemplateListApi } from '#/api/emr/template';

const emit = defineEmits<{
  success: [];
}>();

const visible = ref(false);
const dialogTitle = ref('新建文书类型');
const saving = ref(false);
const loading = ref(false);
const mode = ref<'create' | 'edit'>('create');
const editingId = ref<null | string>(null);

const form = reactive({
  code: '',
  name: '',
  defaultTemplateId: '' as string,
  sortOrder: undefined as number | undefined,
  categoryCode: 'INPATIENT' as string,
  isEnabled: true,
});

const categoryOptions = [
  { label: '门诊病历', value: 'OUTPATIENT' },
  { label: '住院病历', value: 'INPATIENT' },
  { label: '告知书', value: 'NOTICE' },
  { label: '手术病历', value: 'SURGERY' },
  { label: '其他', value: 'OTHER' },
];

const defaultTemplateOptions = ref<{ label: string; value: string }[]>([]);

/** 重置表单为新建默认值 */
function resetForm() {
  form.code = '';
  form.name = '';
  form.defaultTemplateId = '';
  form.sortOrder = undefined;
  form.categoryCode = 'INPATIENT';
  form.isEnabled = true;
  defaultTemplateOptions.value = [];
}

/** 按文书类型编码拉取可选默认模板 */
async function loadDefaultTemplateOptions(documentTypeCode: string) {
  if (!documentTypeCode.trim()) {
    defaultTemplateOptions.value = [];
    return;
  }
  const all: TemplateDto[] = [];
  let pageNo = 1;
  while (true) {
    const res = await getTemplateListApi({
      pageNo,
      pageSize: 200,
      documentTypeCode,
      enabled: true,
    });
    const batch = res?.content ?? [];
    all.push(...batch);
    if (batch.length === 0 || batch.length < 200) break;
    pageNo += 1;
  }
  defaultTemplateOptions.value = all
    .filter((item) => item.id !== undefined && item.id !== null)
    .map((item) => ({
      label: item.name?.trim() || String(item.code ?? item.id),
      value: String(item.id),
    }));
}

/** 打开新建弹窗 */
function openCreate() {
  mode.value = 'create';
  editingId.value = null;
  dialogTitle.value = '新建文书类型';
  resetForm();
  visible.value = true;
}

/** 打开编辑弹窗并回显详情 */
async function openEdit(row: DocumentTypeDto) {
  const id = row.id;
  if (id === undefined || id === null) {
    ElMessage.error('缺少文书类型 ID');
    return;
  }
  mode.value = 'edit';
  editingId.value = String(id);
  dialogTitle.value = '编辑文书类型';
  resetForm();
  visible.value = true;
  loading.value = true;
  try {
    const detail = await getDocumentTypeApi({ id: String(id) });
    form.code = detail.code ?? '';
    form.name = detail.name ?? '';
    form.defaultTemplateId =
      detail.defaultTemplateId !== undefined && detail.defaultTemplateId !== null
        ? String(detail.defaultTemplateId)
        : '';
    form.sortOrder = detail.sortOrder ?? undefined;
    form.categoryCode = detail.categoryCode?.trim() || 'INPATIENT';
    form.isEnabled = detail.isEnabled !== false;
    await loadDefaultTemplateOptions(form.code);
  } catch (error) {
    console.error(error);
    ElMessage.error('加载文书类型详情失败');
    visible.value = false;
  } finally {
    loading.value = false;
  }
}

/** 校验并提交新建或更新 */
async function handleSubmit() {
  const code = form.code.trim();
  const name = form.name.trim();
  if (!code) {
    ElMessage.warning('请输入文书类型编码');
    return;
  }
  if (!name) {
    ElMessage.warning('请输入文书类型名称');
    return;
  }

  saving.value = true;
  try {
    if (mode.value === 'create') {
      await createDocumentTypeApi({
        code,
        name,
        isEnabled: form.isEnabled,
        sortOrder: form.sortOrder,
        categoryCode: form.categoryCode || undefined,
      });
      ElMessage.success('新建文书类型成功');
    } else {
      if (!editingId.value) {
        ElMessage.error('缺少文书类型 ID');
        return;
      }
      const params: Parameters<typeof updateDocumentTypeApi>[0] = {
        id: editingId.value,
        name,
        isEnabled: form.isEnabled,
        sortOrder: form.sortOrder,
        categoryCode: form.categoryCode || undefined,
      };
      if (form.defaultTemplateId) {
        params.defaultTemplateId = form.defaultTemplateId;
      } else {
        params.clearDefaultTemplate = true;
      }
      await updateDocumentTypeApi(params);
      ElMessage.success('更新文书类型成功');
    }
    visible.value = false;
    emit('success');
  } catch (error: unknown) {
    console.error(error);
    const err = error as { message?: string; msg?: string };
    ElMessage.error(err.msg || err.message || '保存失败');
  } finally {
    saving.value = false;
  }
}

watch(
  () => form.code,
  (code) => {
    if (mode.value === 'edit' && visible.value) {
      void loadDefaultTemplateOptions(code);
    }
  },
);

defineExpose({
  openCreate,
  openEdit,
});
</script>

<template>
  <ElDialog
    v-model="visible"
    :title="dialogTitle"
    width="480px"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
  >
    <div v-loading="loading">
      <ElForm label-position="top">
        <ElFormItem label="类型编码" required>
          <ElInput
            v-model="form.code"
            placeholder="如 ADMISSION_NOTE"
            maxlength="32"
            show-word-limit
            :disabled="mode === 'edit'"
          />
        </ElFormItem>
        <ElFormItem label="类型名称" required>
          <ElInput v-model="form.name" placeholder="如 入院记录" maxlength="64" show-word-limit />
        </ElFormItem>
        <ElFormItem label="分类">
          <ElSelect v-model="form.categoryCode" placeholder="选择分类" class="w-full">
            <ElOption
              v-for="opt in categoryOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem v-if="mode === 'edit'" label="默认模板">
          <ElSelect
            v-model="form.defaultTemplateId"
            placeholder="可选，须为同类型且启用的模板"
            clearable
            filterable
            class="w-full"
          >
            <ElOption
              v-for="opt in defaultTemplateOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="排序">
          <ElInputNumber v-model="form.sortOrder" :min="0" :step="10" class="w-full" />
        </ElFormItem>
        <ElFormItem label="启用">
          <ElSwitch v-model="form.isEnabled" />
        </ElFormItem>
      </ElForm>
    </div>
    <template #footer>
      <ElButton @click="visible = false">取消</ElButton>
      <ElButton type="primary" :loading="saving" @click="handleSubmit">保存</ElButton>
    </template>
  </ElDialog>
</template>
