<script lang="ts" setup>
import type {
  CreateLabDeviceParams,
  LabDeviceItem,
  UpdateLabDeviceParams,
} from '#/api/basic/lab-device';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, reactive, ref, watch } from 'vue';

import { useVbenModal } from '@zxplat/common-ui';

import { ElMessage } from 'element-plus';

import { useVbenForm, z } from '#/adapter/form';
import { createLabDeviceApi, initLabDeviceApi, updateLabDeviceApi } from '#/api/basic/lab-device';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { matchSelectOptionKeyword } from '#/utils/select-option-keyword';

defineOptions({ name: 'BasicLabDeviceEditDialog' });

interface Props {
  visible?: boolean;
  formData?: Partial<LabDeviceItem>;
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  formData: () => ({}),
});

const emit = defineEmits<{
  success: [];
  'update:visible': [value: boolean];
}>();

const originalData = ref<Partial<LabDeviceItem>>({});
const categoryCodeOptions = ref<ApiOptionItem[]>([]);
const organizationOptions = ref<ApiOptionItem[]>([]);
const locationOptions = ref<ApiOptionItem[]>([]);
const manufacturerOptionsAll = ref<
  Array<ApiOptionItem & { busNo?: string; pyStr?: string; wbStr?: string }>
>([]);
const manufacturerOptions = ref<
  Array<ApiOptionItem & { busNo?: string; pyStr?: string; wbStr?: string }>
>([]);

const isEditMode = computed(() => !!props.formData?.id);

const dialogTitle = computed(() => (isEditMode.value ? '编辑检验设备' : '新增检验设备'));

const fullWidthStyle = { width: '100%' };

/** 加载维护页下拉选项 */
async function loadInitOptions() {
  try {
    const res = await initLabDeviceApi();
    const data =
      res && typeof res === 'object' && 'data' in res
        ? (res as { data?: Record<string, ApiOptionItem[] | undefined> }).data
        : (res as Record<string, ApiOptionItem[] | undefined>);
    categoryCodeOptions.value = data?.categoryCodeOptions ?? [];
    organizationOptions.value = (data?.organizationOptions ?? []).map((item) => ({
      ...item,
      value: preserveApiIdForRequest(item.value) ?? String(item.value ?? ''),
    }));
    locationOptions.value = (data?.locationOptions ?? []).map((item) => ({
      ...item,
      value: preserveApiIdForRequest(item.value) ?? String(item.value ?? ''),
    }));
    const dedicated = (data?.manufacturerOptions ?? []).map((item) => ({
      ...item,
      value: preserveApiIdForRequest(item.value) ?? String(item.value ?? ''),
    }));
    if (dedicated.length > 0) {
      manufacturerOptionsAll.value = dedicated;
    } else {
      manufacturerOptionsAll.value = (data?.supplierListOptions ?? []).map((item) => ({
        ...item,
        value: preserveApiIdForRequest(item.value) ?? String(item.value ?? ''),
      }));
    }
    manufacturerOptions.value = manufacturerOptionsAll.value;
  } catch {
    categoryCodeOptions.value = [];
    organizationOptions.value = [];
    locationOptions.value = [];
    manufacturerOptionsAll.value = [];
    manufacturerOptions.value = [];
  }
}

/** 厂家下拉关键字过滤 */
function filterManufacturerOptions(query: string) {
  const all = manufacturerOptionsAll.value;
  if (!String(query ?? '').trim()) {
    manufacturerOptions.value = all;
    return;
  }
  manufacturerOptions.value = all.filter((item) => matchSelectOptionKeyword(item, query));
}

/** 厂家下拉收起时恢复全量 */
function onManufacturerSelectVisibleChange(visible: boolean) {
  if (!visible) {
    manufacturerOptions.value = manufacturerOptionsAll.value;
  }
}

/** 提交新增或维护检验设备 */
async function handleSubmit() {
  const { valid } = await formApi.validate();
  if (!valid) {
    return;
  }

  modalApi.lock(true);
  try {
    const values = (await formApi.getValues()) as Record<string, unknown>;
    const merged = {
      ...originalData.value,
      ...values,
    };

    const payload: CreateLabDeviceParams = {
      name: String(merged.name ?? '').trim(),
      categoryCode: merged.categoryCode ? String(merged.categoryCode).trim() : undefined,
      orgId: merged.orgId ? preserveApiIdForRequest(merged.orgId) : undefined,
      locationId: merged.locationId ? preserveApiIdForRequest(merged.locationId) : undefined,
      modelNumber: merged.modelNumber ? String(merged.modelNumber).trim() : undefined,
      serialNumber: merged.serialNumber ? String(merged.serialNumber).trim() : undefined,
      manufacturerId: merged.manufacturerId
        ? preserveApiIdForRequest(merged.manufacturerId)
        : undefined,
      manufactureDate: merged.manufactureDate ? String(merged.manufactureDate) : undefined,
      expirationDate: merged.expirationDate ? String(merged.expirationDate) : undefined,
      description: merged.description ? String(merged.description).trim() : undefined,
    };

    if (isEditMode.value) {
      const id = props.formData?.id;
      if (!id) {
        ElMessage.warning('缺少设备ID');
        return;
      }
      const updatePayload: UpdateLabDeviceParams = {
        ...payload,
        id: String(id),
      };
      await updateLabDeviceApi(updatePayload);
      ElMessage.success('更新成功');
    } else {
      await createLabDeviceApi(payload);
      ElMessage.success('创建成功');
    }

    emit('success');
    modalApi.close();
  } catch (error) {
    console.error(isEditMode.value ? '更新失败' : '创建失败', error);
    ElMessage.error(isEditMode.value ? '更新失败，请稍后重试' : '创建失败，请稍后重试');
  } finally {
    modalApi.unlock();
  }
}

/** 打开弹窗时回填表单 */
async function loadFormData() {
  const { formData } = props;
  if (formData && Object.keys(formData).length > 0) {
    originalData.value = { ...formData };
    await formApi.setValues({
      busNo: formData.busNo ?? '',
      name: formData.name ?? '',
      categoryCode: formData.categoryCode ?? '',
      orgId: formData.orgId ? preserveApiIdForRequest(formData.orgId) : '',
      locationId: formData.locationId ? preserveApiIdForRequest(formData.locationId) : '',
      modelNumber: formData.modelNumber ?? '',
      serialNumber: formData.serialNumber ?? '',
      manufacturerId: formData.manufacturerId
        ? preserveApiIdForRequest(formData.manufacturerId)
        : '',
      manufactureDate: formData.manufactureDate ?? '',
      expirationDate: formData.expirationDate ?? '',
      description: formData.description ?? '',
    });
  } else {
    originalData.value = {};
    await formApi.resetForm();
    await formApi.setValues({
      busNo: '',
      name: '',
      categoryCode: '',
      orgId: '',
      locationId: '',
      modelNumber: '',
      serialNumber: '',
      manufacturerId: '',
      manufactureDate: '',
      expirationDate: '',
      description: '',
    });
  }
}

const formConfig = reactive({
  commonConfig: {
    labelWidth: 96,
    labelClass: 'justify-start text-left',
    componentProps: {
      style: fullWidthStyle,
    },
  },
  layout: 'horizontal' as const,
  wrapperClass: 'lab-device-form-dialog__grid grid grid-cols-1 md:grid-cols-2',
  schema: computed(() => {
    const schema = [
      {
        component: 'Input',
        fieldName: 'name',
        label: '设备名称',
        rules: z.string().min(1, { message: '请填写设备名称' }),
        componentProps: {
          placeholder: '如 生化分析仪',
        },
      },
      {
        component: 'Select',
        fieldName: 'categoryCode',
        label: '设备分类',
        componentProps: {
          options: categoryCodeOptions.value,
          placeholder: '请选择设备分类',
          clearable: true,
        },
      },
      {
        component: 'Select',
        fieldName: 'orgId',
        label: '归属科室',
        componentProps: {
          options: organizationOptions.value,
          placeholder: '请选择归属科室',
          clearable: true,
          filterable: true,
        },
      },
      {
        component: 'Select',
        fieldName: 'locationId',
        label: '所在位置',
        componentProps: {
          options: locationOptions.value,
          placeholder: '请选择所在位置',
          clearable: true,
          filterable: true,
        },
      },
      {
        component: 'Input',
        fieldName: 'modelNumber',
        label: '型号',
        componentProps: {
          placeholder: '设备型号',
        },
      },
      {
        component: 'Input',
        fieldName: 'serialNumber',
        label: '序列号',
        componentProps: {
          placeholder: '厂家序列号',
        },
      },
      {
        component: 'Select',
        fieldName: 'manufacturerId',
        label: '生产厂家',
        componentProps: () => ({
          options: manufacturerOptions.value,
          filterable: true,
          clearable: true,
          placeholder: '请选择生产厂家',
          filterMethod: filterManufacturerOptions,
          onVisibleChange: onManufacturerSelectVisibleChange,
        }),
      },
      {
        component: 'DatePicker',
        fieldName: 'manufactureDate',
        label: '生产日期',
        componentProps: {
          type: 'date',
          valueFormat: 'YYYY-MM-DD',
          placeholder: '请选择生产日期',
          clearable: true,
        },
      },
      {
        component: 'DatePicker',
        fieldName: 'expirationDate',
        label: '到期日期',
        componentProps: {
          type: 'date',
          valueFormat: 'YYYY-MM-DD',
          placeholder: '请选择到期日期',
          clearable: true,
        },
      },
      {
        component: 'Input',
        fieldName: 'description',
        label: '备注',
        formItemClass: 'md:col-span-2',
        componentProps: {
          type: 'textarea',
          rows: 3,
          placeholder: '设备说明',
        },
      },
    ];

    if (isEditMode.value) {
      schema.unshift({
        component: 'Input',
        fieldName: 'busNo',
        label: '业务编码',
        componentProps: () => ({
          disabled: true,
        }),
      });
    }

    return schema;
  }),
  showDefaultActions: false,
});

const [Form, formApi] = useVbenForm(formConfig);

const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen: boolean) => {
    emit('update:visible', isOpen);
  },
  onOpened: async () => {
    await nextTick();
    await loadInitOptions();
    await loadFormData();
  },
  onConfirm: handleSubmit,
  showCancelButton: true,
  showConfirmButton: true,
  confirmText: '确定',
  cancelText: '取消',
});

watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      modalApi.open();
    } else {
      modalApi.close();
    }
  },
  { immediate: true },
);
</script>

<template>
  <Modal
    class="lab-device-form-dialog w-[720px] max-w-[96vw]"
    :title="dialogTitle"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
  >
    <Form />
  </Modal>
</template>

<style scoped>
.lab-device-form-dialog :deep(.lab-device-form-dialog__grid) {
  align-items: start;
}

.lab-device-form-dialog :deep(.el-form-item) {
  margin-bottom: 12px;
}
</style>
