<script lang="ts" setup>
import type { CreateWardBedParams, WardBed, WardBedInitResponse } from '#/api/basic/ward-bed';

import { computed, nextTick, reactive, ref, watch } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { ElMessage } from 'element-plus';

import { useVbenForm } from '#/adapter/form';
import {
  createWardBedApi,
  editWardBedApi,
  getWardListApi,
  getWardBedByIdApi,
  getWardBedPageApi,
  wardBedInitApi,
  WARD_BED_FORM_CODE,
  WARD_BED_REGISTER_DEFAULT,
} from '#/api/basic/ward-bed';

interface Props {
  visible?: boolean;
  formData?: Partial<WardBed & { id: string; roomParentId?: string }>;
  parentWard?: null | WardBed;
  wardList?: WardBed[];
  roomList?: WardBed[];
  /** 从列表上下文打开时锁定类型，隐藏类型切换 */
  lockFormEnum?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  formData: () => ({}),
  parentWard: null,
  wardList: () => [],
  roomList: () => [],
  lockFormEnum: false,
});

const emit = defineEmits<{
  success: [];
  'update:visible': [value: boolean];
}>();

const originalData = ref<Record<string, any>>({});
const initOptions = ref<WardBedInitResponse>();
const wardOptions = ref<Array<{ label: string; value: string }>>([]);
const roomOptions = ref<Array<{ label: string; value: string }>>([]);
const initOptionsLoaded = ref(false);
const initLoading = ref(false);
const roomOptionsCache = ref<Map<string, Array<{ label: string; value: string }>>>(new Map());
const loadingRoomOptions = ref<Map<string, Promise<void>>>(new Map());
const roomLoading = ref(false);
const isEditMode = computed(() => !!props.formData?.id);

async function loadInitOptions(force = false) {
  if (initOptionsLoaded.value && !force) {
    if (props.wardList && props.wardList.length > 0) {
      wardOptions.value = props.wardList.map((item) => ({
        label: item.name || '',
        value: item.id || '',
      }));
    }
    return;
  }
  initLoading.value = true;
  try {
    const response = await wardBedInitApi();
    initOptions.value = response;
    if (props.wardList && props.wardList.length > 0) {
      wardOptions.value = props.wardList.map((item) => ({
        label: item.name || '',
        value: item.id || '',
      }));
    } else {
      const wards = await getWardListApi();
      wardOptions.value =
        wards?.map((item) => ({ label: item.name || '', value: item.id || '' })) || [];
    }
    initOptionsLoaded.value = true;
  } catch (error) {
    console.error('加载初始化选项失败:', error);
  } finally {
    initLoading.value = false;
  }
}

async function loadRoomOptions(wardId: string) {
  if (!wardId) {
    roomOptions.value = [];
    return;
  }
  if (props.roomList && props.roomList.length > 0 && props.parentWard?.id === wardId) {
    const options = props.roomList.map((item) => ({
      label: item.name || item.busNo || '',
      value: item.id || '',
    }));
    roomOptions.value = options;
    roomOptionsCache.value.set(wardId, options);
    return;
  }
  if (roomOptionsCache.value.has(wardId)) {
    roomOptions.value = roomOptionsCache.value.get(wardId) || [];
    return;
  }
  if (loadingRoomOptions.value.has(wardId)) {
    roomLoading.value = true;
    try {
      await loadingRoomOptions.value.get(wardId);
      roomOptions.value = roomOptionsCache.value.get(wardId) || [];
    } finally {
      roomLoading.value = false;
    }
    return;
  }

  const loadPromise = (async () => {
    try {
      roomLoading.value = true;
      const rooms = await getWardBedPageApi({ id: wardId });
      const options =
        rooms?.map((item) => ({ label: item.name || item.busNo || '', value: item.id || '' })) ||
        [];
      roomOptions.value = options;
      roomOptionsCache.value.set(wardId, options);
    } catch (error) {
      console.error('加载病房列表失败:', error);
      roomOptions.value = [];
    } finally {
      loadingRoomOptions.value.delete(wardId);
      roomLoading.value = false;
    }
  })();

  loadingRoomOptions.value.set(wardId, loadPromise);
  await loadPromise;
}

/** 创建时解析机构 ID（病房/床位表单不展示机构，须从所属病区带入） */
function resolveOrganizationId(submitData: Record<string, unknown>): number | undefined {
  const candidates = [
    submitData.organizationId,
    props.parentWard?.organizationId,
    props.formData?.organizationId,
    originalData.value.organizationId,
  ];
  for (const raw of candidates) {
    if (raw === null || raw === undefined || raw === '') continue;
    const num = Number(raw);
    if (!Number.isNaN(num) && num > 0) return num;
  }
  return undefined;
}

async function handleSubmit() {
  const { valid } = await formApi.validate();
  if (!valid) return;
  modalApi.lock(true);
  try {
    const formValues = await formApi.getValues();
    const mergedData = { ...originalData.value, ...formValues };
    const submitData: Record<string, any> = {};
    Object.keys(mergedData).forEach((key) => {
      const value = mergedData[key];
      if (value !== null && value !== undefined && value !== '') submitData[key] = value;
    });
    if (submitData.roomParentId !== undefined) {
      submitData.parentId = submitData.roomParentId;
      delete submitData.roomParentId;
    }
    if (!isEditMode.value) {
      const organizationId = resolveOrganizationId(submitData);
      if (organizationId === undefined) {
        ElMessage.warning('机构不能为空，请确认所属病区已关联机构');
        return;
      }
      submitData.organizationId = organizationId;
    }
    if (isEditMode.value) {
      await editWardBedApi(submitData as CreateWardBedParams & { id: string });
      ElMessage.success('更新成功');
    } else {
      const { id: _id, ...createData } = submitData;
      await createWardBedApi({
        ...WARD_BED_REGISTER_DEFAULT,
        ...createData,
      } as CreateWardBedParams);
      ElMessage.success('创建成功');
    }
    emit('success');
    modalApi.close();
  } catch (error: any) {
    console.error(error.message);
    ElMessage.error(error.message);
  } finally {
    modalApi.unlock();
  }
}

async function loadFormData() {
  const id = props.formData?.id;
  if (id) {
    modalApi.setState({ loading: true });
    try {
      const data = await getWardBedByIdApi(id);
      if (data) {
        originalData.value = { ...data };
        const formData: Record<string, any> = { ...data };
        if (data.formEnum === WARD_BED_FORM_CODE.BED) {
          formData.roomParentId = data.parent?.id;
        } else {
          formData.parentId = data.parent?.id;
        }
        if (data.formEnum === WARD_BED_FORM_CODE.BED) {
          await loadInitOptions();
          const wardId =
            props.parentWard?.id ||
            (data.parent as WardBed | undefined)?.parentId ||
            (data.parent as WardBed | undefined)?.parent?.id;
          if (wardId) await loadRoomOptions(String(wardId));
        } else if (
          data.formEnum === WARD_BED_FORM_CODE.HOUSE ||
          data.formEnum === WARD_BED_FORM_CODE.WARD
        ) {
          await loadInitOptions();
        }
        await formApi.setValues(formData);
      } else {
        ElMessage.warning('未找到病区位置信息');
        modalApi.close();
      }
    } catch (error) {
      console.error('获取病区位置详情失败:', error);
      ElMessage.error('获取病区位置详情失败，请稍后重试');
      modalApi.close();
      return;
    } finally {
      modalApi.setState({ loading: false });
    }
  } else {
    if (props.formData && Object.keys(props.formData).length > 0) {
      originalData.value = {
        ...props.formData,
        organizationId:
          props.formData.organizationId ?? props.parentWard?.organizationId ?? undefined,
      };
      const formData: Record<string, any> = { ...originalData.value };
      if (props.formData.formEnum === WARD_BED_FORM_CODE.BED && props.formData.roomParentId)
        formData.roomParentId = props.formData.roomParentId;
      const formEnum = props.formData.formEnum;
      if (formEnum === WARD_BED_FORM_CODE.HOUSE && props.formData.parentId) await loadInitOptions();
      else if (formEnum === WARD_BED_FORM_CODE.BED) {
        await loadInitOptions();
        const wardId = props.parentWard?.id || props.formData.parentId;
        if (wardId) await loadRoomOptions(String(wardId));
      }
      formApi.setValues(formData);
    } else {
      originalData.value = {};
      await formApi.resetForm();
      if (props.formData?.formEnum) {
        await formApi.setFieldValue('formEnum', props.formData.formEnum);
        if (props.formData.formEnum === WARD_BED_FORM_CODE.HOUSE && props.parentWard?.id) {
          await formApi.setFieldValue('parentId', props.parentWard.id);
          await loadInitOptions();
        } else if (props.formData.formEnum === WARD_BED_FORM_CODE.WARD) {
          await loadInitOptions();
        }
      } else if (props.parentWard?.id) {
        await formApi.setFieldValue('parentId', props.parentWard.id);
        await formApi.setFieldValue('formEnum', WARD_BED_FORM_CODE.HOUSE);
        await loadInitOptions();
      } else {
        await formApi.setFieldValue('formEnum', WARD_BED_FORM_CODE.WARD);
        await loadInitOptions();
      }
    }
  }
}

const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen: boolean) => emit('update:visible', isOpen),
  onOpened: async () => {
    await nextTick();
    modalApi.setState({ loading: true });
    try {
      await loadInitOptions();
      await loadFormData();
    } finally {
      modalApi.setState({ loading: false });
    }
  },
  onConfirm: handleSubmit,
  showCancelButton: true,
  showConfirmButton: true,
  confirmText: '确定',
  cancelText: '取消',
});

const formConfig = reactive({
  commonConfig: { componentProps: { style: { width: '220px' } } },
  layout: 'horizontal' as const,
  wrapperClass: 'flex flex-wrap flex-row',
  schema: [
    {
      component: 'RadioGroup',
      fieldName: 'formEnum',
      label: '类型',
      rules: 'required',
      componentProps: {
        options: [
          { label: '病区', value: WARD_BED_FORM_CODE.WARD },
          { label: '病房', value: WARD_BED_FORM_CODE.HOUSE },
          { label: '床位', value: WARD_BED_FORM_CODE.BED },
        ],
      },
      dependencies: {
        triggerFields: ['formEnum'],
        show: () => !props.lockFormEnum,
        disabled: () => isEditMode.value || props.lockFormEnum,
      },
    },
    {
      component: 'Input',
      fieldName: 'name',
      label: '名称',
      rules: 'required',
      componentProps: { placeholder: '请输入名称' },
      dependencies: {
        triggerFields: ['formEnum'],
        show: (values: Record<string, any>) =>
          [WARD_BED_FORM_CODE.WARD, WARD_BED_FORM_CODE.HOUSE, WARD_BED_FORM_CODE.BED].includes(
            values.formEnum,
          ),
        componentProps: (values: Record<string, any>) => {
          if (values.formEnum === WARD_BED_FORM_CODE.WARD) return { placeholder: '请输入病区名称' };
          if (values.formEnum === WARD_BED_FORM_CODE.BED) return { placeholder: '请输入床位名称' };
          if (values.formEnum === WARD_BED_FORM_CODE.HOUSE)
            return { placeholder: '请输入病房名称' };
          return { placeholder: '请输入名称' };
        },
        label: (values: Record<string, any>) => {
          if (values.formEnum === WARD_BED_FORM_CODE.WARD) return '病区名称';
          if (values.formEnum === WARD_BED_FORM_CODE.HOUSE) return '病房名称';
          if (values.formEnum === WARD_BED_FORM_CODE.BED) return '床位名称';
          return '名称';
        },
      },
    },
    {
      component: 'Select',
      fieldName: 'organizationId',
      label: '关联科室',
      dependencies: {
        triggerFields: ['formEnum'],
        show: (values: Record<string, any>) => values.formEnum === WARD_BED_FORM_CODE.WARD,
        componentProps: () => ({
          options: initOptions.value?.organizationOptions || [],
          filterable: true,
          clearable: true,
          loading: initLoading.value,
        }),
      },
    },
    {
      component: 'Select',
      fieldName: 'parentId',
      label: '所属病区',
      dependencies: {
        triggerFields: ['formEnum', 'parentId'],
        show: (values: Record<string, any>) => values.formEnum === WARD_BED_FORM_CODE.HOUSE,
        trigger: async (values: Record<string, any>) => {
          const wardId = values.parentId;
          if (wardId) await loadRoomOptions(wardId);
        },
        componentProps: () => ({
          options: wardOptions.value,
          filterable: true,
          clearable: true,
          loading: initLoading.value,
        }),
      },
    },
    {
      component: 'Select',
      fieldName: 'roomParentId',
      label: '所属病房',
      dependencies: {
        triggerFields: ['formEnum'],
        show: (values: Record<string, any>) => values.formEnum === WARD_BED_FORM_CODE.BED,
        componentProps: () => ({
          options: roomOptions.value,
          filterable: true,
          clearable: true,
          loading: roomLoading.value,
        }),
      },
    },
    {
      component: 'InputNumber',
      fieldName: 'bedCount',
      label: '病床数',
      dependencies: {
        triggerFields: ['formEnum'],
        show: (values: Record<string, any>) =>
          [WARD_BED_FORM_CODE.WARD, WARD_BED_FORM_CODE.HOUSE].includes(values.formEnum),
        componentProps: () => ({
          placeholder: '请输入病床数',
          min: 0,
          max: 999,
          controlsPosition: 'right',
          style: { width: '220px' },
        }),
      },
    },
  ],
  showDefaultActions: false,
});

const [Form, formApi] = useVbenForm(formConfig);

watch(
  () => props.visible,
  (visible) => {
    if (visible) modalApi.open();
    else modalApi.close();
  },
  { immediate: true },
);
</script>

<template>
  <Modal
    class="w-[600px]"
    :title="isEditMode ? '编辑' : '新增'"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
  >
    <Form />
  </Modal>
</template>
