<script lang="ts" setup>
import type {
  LisPacsItemMappingItem,
  SaveLisPacsItemMappingParams,
} from '#/api/basic/lis-pacs-item-mapping';

import { computed, reactive, ref, watch } from 'vue';

import { ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElMessage, ElSwitch } from 'element-plus';

import { saveLisPacsItemMappingApi } from '#/api/basic/lis-pacs-item-mapping';

defineOptions({ name: 'BasicLisPacsItemMappingEditDialog' });

interface Props {
  visible?: boolean;
  formData?: Partial<LisPacsItemMappingItem> & {
    id?: string;
    suggestedExternalCode?: string;
  };
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  formData: () => ({}),
});

const emit = defineEmits<{
  success: [];
  'update:visible': [value: boolean];
}>();

const saving = ref(false);

const form = reactive({
  id: undefined as string | undefined,
  activityId: '' as string,
  activityBusNo: '' as string,
  activityName: '' as string,
  categoryLabel: '' as string,
  externalCode: '',
  externalName: '',
  activeFlag: true,
  suggestedExternalCode: '' as string,
});

const activityDisplayLabel = computed(() => {
  const parts = [form.categoryLabel, form.activityBusNo, form.activityName].filter(
    (part) => part && String(part).trim(),
  );
  return parts.join(' · ') || '（未选择项目）';
});

const dialogVisible = computed({
  get: () => props.visible,
  set: (value: boolean) => emit('update:visible', value),
});

watch(
  () => props.visible,
  (visible) => {
    if (!visible) {
      return;
    }
    form.id = props.formData?.id ? String(props.formData.id) : undefined;
    form.activityId = props.formData?.activityId ? String(props.formData.activityId) : '';
    form.activityBusNo = props.formData?.activityBusNo ?? '';
    form.activityName = props.formData?.activityName ?? '';
    form.categoryLabel = props.formData?.categoryLabel ?? '';
    form.suggestedExternalCode = props.formData?.suggestedExternalCode ?? '';
    form.externalCode =
      props.formData?.externalCode?.trim() || props.formData?.suggestedExternalCode?.trim() || '';
    form.externalName =
      props.formData?.externalName?.trim() || props.formData?.activityName?.trim() || '';
    form.activeFlag = props.formData?.activeFlag !== false;
  },
);

function applySuggestedCode() {
  if (!form.suggestedExternalCode) {
    ElMessage.info('当前项目没有可解析的院内旧编码');
    return;
  }
  form.externalCode = form.suggestedExternalCode;
  if (!form.externalName.trim() && form.activityName) {
    form.externalName = form.activityName;
  }
}

async function handleSave() {
  if (!form.id || !form.activityId) {
    ElMessage.warning('仅支持编辑已有对照');
    return;
  }
  if (!form.externalCode.trim()) {
    ElMessage.warning('请填写外发码');
    return;
  }
  saving.value = true;
  try {
    const payload: SaveLisPacsItemMappingParams = {
      id: form.id,
      activityId: form.activityId,
      externalCode: form.externalCode.trim(),
      externalName: form.externalName.trim() || form.activityName || undefined,
      activeFlag: form.activeFlag,
    };
    await saveLisPacsItemMappingApi(payload);
    ElMessage.success('保存成功');
    dialogVisible.value = false;
    emit('success');
  } catch {
    ElMessage.error('保存失败');
  } finally {
    saving.value = false;
  }
}

function handleClose() {
  dialogVisible.value = false;
}
</script>

<template>
  <ElDialog
    v-model="dialogVisible"
    title="编辑对照"
    width="560px"
    destroy-on-close
    @close="handleClose"
  >
    <ElForm label-width="96px">
      <ElFormItem label="诊疗项目" required>
        <ElInput :model-value="activityDisplayLabel" disabled />
      </ElFormItem>
      <ElFormItem label="外发码" required>
        <div class="flex w-full flex-nowrap items-center gap-2">
          <div class="min-w-0 flex-1">
            <ElInput
              v-model="form.externalCode"
              class="w-full"
              placeholder="现网 LIS/PACS 项目码（groupCode）"
              clearable
            />
          </div>
          <ElButton v-if="form.suggestedExternalCode" class="shrink-0" @click="applySuggestedCode">
            采用建议码
          </ElButton>
        </div>
        <div v-if="form.suggestedExternalCode" class="mt-1 text-xs text-gray-500">
          建议外发码（院内旧编码）：{{ form.suggestedExternalCode }}
        </div>
      </ElFormItem>
      <ElFormItem label="外发名称">
        <ElInput v-model="form.externalName" placeholder="默认使用院内名称，可改" clearable />
      </ElFormItem>
      <ElFormItem label="启用">
        <ElSwitch v-model="form.activeFlag" />
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton @click="handleClose">取消</ElButton>
      <ElButton type="primary" :loading="saving" @click="handleSave">保存</ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.flex {
  display: flex;
  align-items: center;
}
.w-full {
  width: 100%;
}
.gap-2 {
  gap: 8px;
}
.mt-1 {
  margin-top: 4px;
}
.text-xs {
  font-size: 12px;
}
.text-gray-500 {
  color: var(--el-text-color-secondary);
}
</style>
