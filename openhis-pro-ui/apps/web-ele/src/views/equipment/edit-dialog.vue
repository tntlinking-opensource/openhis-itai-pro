<script lang="ts" setup>
import type {
  CreateEmqConfigListParams,
  EmqConfigListInitResponse,
  EmqConfigListItem,
} from '#/api';

import { computed, reactive, ref, watch } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { ElMessage } from 'element-plus';

import { useVbenForm } from '#/adapter/form';
import { createEmqConfigListApi, getEmqConfigListApi, updateEmqConfigListApi } from '#/api';

interface Props {
  visible?: boolean;
  formData?: Partial<EmqConfigListItem>;
  initOptions?: EmqConfigListInitResponse;
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  formData: () => ({}),
  initOptions: () => ({}) as EmqConfigListInitResponse,
});

const emit = defineEmits<{
  success: [];
  'update:visible': [value: boolean];
}>();

const loading = ref(false);
const originalData = ref<Partial<EmqConfigListItem>>({});

const isEditMode = computed(() => {
  return !!props.formData?.id;
});

type OptionItem = { label: string; value: number | string };

const isOptionArray = (value: unknown): value is OptionItem[] => {
  return (
    Array.isArray(value) &&
    value.every(
      (item) =>
        typeof item === 'object' &&
        item !== null &&
        'label' in item &&
        'value' in item &&
        (typeof item.value === 'string' || typeof item.value === 'number'),
    )
  );
};

const getOptions = (value: unknown): OptionItem[] => {
  return isOptionArray(value) ? value : [];
};

const convertOptions = (
  options: Array<{ label: string; value: number | string }> | null | undefined,
) => {
  if (!options || !Array.isArray(options)) {
    return [];
  }
  return options.map((item) => ({
    label: item.label,
    // 将 value 统一转为数字，与后端枚举字段类型保持一致
    value: typeof item.value === 'number' ? item.value : Number(item.value) || item.value,
  }));
};

const deviceTypeOptions = computed(() => getOptions(props.initOptions?.deviceTypeOptions));
const protocolOptions = computed(() => getOptions(props.initOptions?.protocolOptions));
const connectModeOptions = computed(() => getOptions(props.initOptions?.connectModeOptions));
const organizationOptions = computed(() => getOptions(props.initOptions?.organizationOptions));
const locationOptions = computed(() => getOptions(props.initOptions?.locationOptions));

// 默认选项（当 initOptions 未加载时使用）
const defaultDeviceTypeOptions = [
  { label: '监护仪', value: 1 },
  { label: '呼吸机', value: 2 },
  { label: '输液泵', value: 3 },
  { label: '其他', value: 4 },
];
const defaultProtocolOptions = [
  { label: 'HL7', value: 1 },
  { label: 'Socket', value: 2 },
  { label: 'HTTP', value: 3 },
];
const defaultConnectModeOptions = [
  { label: '客户端主动连接', value: 1 },
  { label: '服务端监听', value: 2 },
];
const brandOptions = [
  { label: '理邦', value: '理邦' },
  { label: '迈瑞', value: '迈瑞' },
];

async function handleSubmit() {
  const { valid } = await formApi.validate();
  if (!valid) {
    return;
  }

  modalApi.lock(true);
  try {
    const formValues = await formApi.getValues();
    const mergedData = {
      ...originalData.value,
      ...formValues,
    };

    if (isEditMode.value) {
      await updateEmqConfigListApi(mergedData);
      ElMessage.success('更新成功');
    } else {
      const { id: _id, ...restData } = mergedData;
      const createData: CreateEmqConfigListParams = {
        name: typeof restData.name === 'string' ? restData.name : '',
        deviceTypeEnum: typeof restData.deviceTypeEnum === 'number' ? restData.deviceTypeEnum : 1,
        protocolEnum: typeof restData.protocolEnum === 'number' ? restData.protocolEnum : 1,
        brand: typeof restData.brand === 'string' ? restData.brand : undefined,
        modelNumber: typeof restData.modelNumber === 'string' ? restData.modelNumber : undefined,
        ipAddress: typeof restData.ipAddress === 'string' ? restData.ipAddress : undefined,
        port: typeof restData.port === 'number' ? restData.port : undefined,
        localPort: typeof restData.localPort === 'number' ? restData.localPort : undefined,
        connectModeEnum:
          typeof restData.connectModeEnum === 'number' ? restData.connectModeEnum : undefined,
        orgId:
          typeof restData.orgId === 'number'
            ? restData.orgId
            : typeof restData.orgId === 'string'
              ? Number(restData.orgId) || undefined
              : undefined,
        locationId:
          typeof restData.locationId === 'number'
            ? restData.locationId
            : typeof restData.locationId === 'string'
              ? Number(restData.locationId) || undefined
              : undefined,
        bedNo: typeof restData.bedNo === 'string' ? restData.bedNo : undefined,
        description: typeof restData.description === 'string' ? restData.description : undefined,
        reportInterval:
          typeof restData.reportInterval === 'number' ? restData.reportInterval : undefined,
        alarmEnabled: typeof restData.alarmEnabled === 'number' ? restData.alarmEnabled : undefined,
        hl7AppName: typeof restData.hl7AppName === 'string' ? restData.hl7AppName : undefined,
        hl7FacilityName:
          typeof restData.hl7FacilityName === 'string' ? restData.hl7FacilityName : undefined,
        extConfig: typeof restData.extConfig === 'string' ? restData.extConfig : undefined,
      };
      await createEmqConfigListApi(createData);
      ElMessage.success('创建成功');
    }
    emit('success');
    modalApi.close();
  } catch (error) {
    console.error(isEditMode.value ? '更新失败:' : '创建失败:', error);
    ElMessage.error(isEditMode.value ? '更新失败，请稍后重试' : '创建失败，请稍后重试');
  } finally {
    modalApi.unlock();
  }
}

async function loadFormData() {
  const id = props.formData?.id;
  if (id) {
    loading.value = true;
    try {
      const response = await getEmqConfigListApi(String(id));
      const data = response.data;
      if (data) {
        originalData.value = { ...data };
        formApi.setValues({ ...data });
      } else {
        ElMessage.warning('未找到设备信息');
        modalApi.close();
      }
    } catch (error) {
      console.error('获取设备详情失败:', error);
      ElMessage.error('获取设备详情失败，请稍后重试');
      modalApi.close();
      return;
    } finally {
      loading.value = false;
    }
  } else {
    if (props.formData && Object.keys(props.formData).length > 0) {
      originalData.value = { ...props.formData };
      formApi.setValues({ ...props.formData });
    } else {
      originalData.value = {};
      await formApi.resetForm();
    }
  }
}

const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen: boolean) => {
    emit('update:visible', isOpen);
  },
  onOpened: async () => {
    await loadFormData();
  },
  onConfirm: handleSubmit,
  showCancelButton: true,
  showConfirmButton: true,
  confirmText: '确定',
  cancelText: '取消',
});

const [Form, formApi] = useVbenForm(
  reactive({
    commonConfig: {
      componentProps: {
        style: { width: '220px' },
      },
    },
    layout: 'horizontal',
    wrapperClass: 'flex flex-wrap flex-row ',
    schema: computed(() => [
      // 基本信息
      {
        component: 'Input',
        fieldName: 'name',
        label: '设备名称',
        rules: 'required',
      },
      {
        component: 'Select',
        fieldName: 'deviceTypeEnum',
        label: '设备类型',
        rules: 'required',
        componentProps: {
          options:
            deviceTypeOptions.value.length > 0
              ? convertOptions(deviceTypeOptions.value)
              : defaultDeviceTypeOptions,
          filterable: true,
          placeholder: '请选择设备类型',
          clearable: true,
        },
      },
      {
        component: 'Select',
        fieldName: 'brand',
        label: '设备品牌',
        componentProps: {
          options: brandOptions,
          filterable: true,
          placeholder: '请选择设备品牌',
          clearable: true,
        },
      },
      {
        component: 'Input',
        fieldName: 'modelNumber',
        label: '设备型号',
      },
      // 连接配置
      {
        component: 'Select',
        fieldName: 'protocolEnum',
        label: '连接协议',
        rules: 'required',
        componentProps: {
          options:
            protocolOptions.value.length > 0
              ? convertOptions(protocolOptions.value)
              : defaultProtocolOptions,
          filterable: true,
          placeholder: '请选择连接协议',
          clearable: true,
        },
      },
      {
        component: 'Input',
        fieldName: 'ipAddress',
        label: 'IP地址',
        rules: 'required',
        componentProps: {
          placeholder: '如: 192.168.1.100',
        },
      },
      {
        component: 'InputNumber',
        fieldName: 'port',
        label: '设备端口',
        rules: 'required',
        componentProps: {
          min: 1,
          max: 65535,
          placeholder: '如: 3000',
        },
      },
      {
        component: 'InputNumber',
        fieldName: 'localPort',
        label: '监听端口',
        componentProps: {
          min: 1,
          max: 65535,
          placeholder: 'Socket服务端模式使用',
        },
      },
      {
        component: 'Select',
        fieldName: 'connectModeEnum',
        label: '连接模式',
        componentProps: {
          options:
            connectModeOptions.value.length > 0
              ? convertOptions(connectModeOptions.value)
              : defaultConnectModeOptions,
          filterable: true,
          placeholder: '请选择连接模式',
          clearable: true,
        },
      },
      // 位置信息
      {
        component: 'ApiTreeSelect',
        fieldName: 'orgId',
        label: '所属科室',
        componentProps: {
          data: organizationOptions.value,
          checkStrictly: true,
          placeholder: '请选择所属科室',
          clearable: true,
        },
      },
      {
        component: 'ApiTreeSelect',
        fieldName: 'locationId',
        label: '所属病区',
        componentProps: {
          data: locationOptions.value,
          checkStrictly: true,
          placeholder: '请选择所属病区',
          clearable: true,
        },
      },
      {
        component: 'Input',
        fieldName: 'bedNo',
        label: '床位号',
        componentProps: {
          placeholder: '如: Bed3333',
        },
      },
      // HL7 配置
      {
        component: 'Input',
        fieldName: 'hl7AppName',
        label: 'HL7应用名称',
        componentProps: {
          placeholder: 'MSH-3，如: hospital',
        },
      },
      {
        component: 'Input',
        fieldName: 'hl7FacilityName',
        label: 'HL7设施名称',
        componentProps: {
          placeholder: 'MSH-4',
        },
      },
      // 其他配置
      {
        component: 'InputNumber',
        fieldName: 'reportInterval',
        label: '上报间隔(秒)',
        componentProps: {
          min: 1,
          placeholder: '数据上报间隔',
        },
      },
      {
        component: 'Checkbox',
        fieldName: 'alarmEnabled',
        label: '启用报警',
      },
      {
        component: 'InputArea',
        fieldName: 'description',
        label: '备注',
      },
    ]),
    showDefaultActions: false,
  }),
);

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
    class="w-[60%]"
    :title="isEditMode ? '编辑设备配置' : '新增设备配置'"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
  >
    <Form />
  </Modal>
</template>
