<script lang="ts" setup>
import type { DepartmentOption, InitDepartmentResponse } from '#/api/core/system/department.types';

import { computed, reactive, ref, watch } from 'vue';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElMessage,
  ElOption,
  ElSelect,
} from 'element-plus';

import {
  ORDER_TEMPLATE_BINDING_DEPARTMENT,
  ORDER_TEMPLATE_BINDING_PERSONAL,
} from '#/api/basic/order-template';
import { initDepartmentApi } from '#/api/core/system/department';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

defineOptions({ name: 'OrderTemplateSaveBindingDialog' });

interface OwnerOption {
  label: string;
  value: string;
}

interface Props {
  /** 默认选中的归属对象 id（编辑回显） */
  defaultObjectId?: string;
}

const props = withDefaults(defineProps<Props>(), {
  defaultObjectId: '',
});

const emit = defineEmits<{
  confirm: [payload: { bindingEnum: string; objectId: string; objectName: string }];
}>();

const visible = ref(false);
const bindingEnum = ref('');
const submitting = ref(false);
const loadingOptions = ref(false);
const practitionerOptions = ref<OwnerOption[]>([]);
const departmentOptions = ref<OwnerOption[]>([]);

const form = reactive({
  objectId: '',
});

const dialogTitle = computed(() => {
  if (bindingEnum.value === ORDER_TEMPLATE_BINDING_PERSONAL) {
    return '存个人';
  }
  if (bindingEnum.value === ORDER_TEMPLATE_BINDING_DEPARTMENT) {
    return '存科室';
  }
  return '选择归属';
});

const ownerFieldLabel = computed(() => {
  if (bindingEnum.value === ORDER_TEMPLATE_BINDING_PERSONAL) {
    return '归属人员';
  }
  return '归属科室';
});

const ownerPlaceholder = computed(() => {
  if (bindingEnum.value === ORDER_TEMPLATE_BINDING_PERSONAL) {
    return '请选择医务人员';
  }
  return '请选择科室';
});

const ownerOptions = computed(() => {
  if (bindingEnum.value === ORDER_TEMPLATE_BINDING_PERSONAL) {
    return practitionerOptions.value;
  }
  return departmentOptions.value;
});

/** 扁平化科室树为下拉选项 */
function flattenDepartmentOptions(items: DepartmentOption[] | null | undefined): OwnerOption[] {
  const result: OwnerOption[] = [];
  const walk = (nodes: DepartmentOption[]) => {
    for (const node of nodes) {
      const value = preserveApiIdForRequest(node.value) ?? '';
      const label = String(node.label ?? '').trim();
      if (value && label) {
        result.push({ value, label });
      }
      if (node.children && node.children.length > 0) {
        walk(node.children);
      }
    }
  };
  walk(items ?? []);
  return result;
}

/** 加载人员/科室下拉（科室管理 init） */
async function loadOwnerOptions() {
  if (practitionerOptions.value.length > 0 || departmentOptions.value.length > 0) {
    return;
  }
  loadingOptions.value = true;
  try {
    const response = await initDepartmentApi();
    const responseData = response as InitDepartmentResponse | { data?: InitDepartmentResponse };
    const data = 'data' in responseData ? responseData.data : responseData;
    practitionerOptions.value = (data?.practitionerOptions ?? [])
      .map((item) => ({
        value: preserveApiIdForRequest(item.value) ?? '',
        label: String(item.label ?? '').trim(),
      }))
      .filter((item) => item.value && item.label);
    departmentOptions.value = flattenDepartmentOptions(data?.departmentOptions);
  } catch {
    practitionerOptions.value = [];
    departmentOptions.value = [];
    ElMessage.error('加载归属选项失败');
  } finally {
    loadingOptions.value = false;
  }
}

/** 打开弹窗：binding=个人或科室 */
async function open(nextBinding: string, presetObjectId?: string) {
  bindingEnum.value = String(nextBinding ?? '').trim();
  form.objectId =
    preserveApiIdForRequest(presetObjectId) || preserveApiIdForRequest(props.defaultObjectId) || '';
  visible.value = true;
  await loadOwnerOptions();
  // 仅一个选项时自动选中，减少多余点击
  if (!form.objectId && ownerOptions.value.length === 1) {
    form.objectId = ownerOptions.value[0]?.value ?? '';
  }
}

/** 取消关闭 */
function handleCancel() {
  visible.value = false;
}

/** 确认并回传归属 */
function handleConfirm() {
  const objectId = preserveApiIdForRequest(form.objectId);
  if (!objectId) {
    ElMessage.warning(`请选择${ownerFieldLabel.value}`);
    return;
  }
  const hit = ownerOptions.value.find((item) => item.value === objectId);
  submitting.value = true;
  try {
    emit('confirm', {
      bindingEnum: bindingEnum.value,
      objectId,
      objectName: String(hit?.label ?? '').trim(),
    });
    visible.value = false;
  } finally {
    submitting.value = false;
  }
}

watch(visible, (openFlag) => {
  if (!openFlag) {
    form.objectId = '';
    bindingEnum.value = '';
  }
});

defineExpose({ open });
</script>

<template>
  <ElDialog
    v-model="visible"
    :title="dialogTitle"
    width="420px"
    destroy-on-close
    append-to-body
    @closed="handleCancel"
  >
    <ElForm label-width="88px" @submit.prevent>
      <ElFormItem :label="ownerFieldLabel" required>
        <ElSelect
          v-model="form.objectId"
          filterable
          clearable
          class="w-full"
          :loading="loadingOptions"
          :placeholder="ownerPlaceholder"
        >
          <ElOption
            v-for="item in ownerOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </ElSelect>
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton @click="handleCancel">取消</ElButton>
      <ElButton type="primary" :loading="submitting" @click="handleConfirm">确定</ElButton>
    </template>
  </ElDialog>
</template>
