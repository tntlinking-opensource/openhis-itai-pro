<script lang="ts" setup>
import type { CreateWardBedParams, WardBed, WardBedInitResponse } from '#/api/basic/ward-bed';

import { computed, nextTick, reactive, ref, watch } from 'vue';

import { useVbenModal } from '@zxplat/common-ui';

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
import { unwrapApiList } from '#/utils/api/unwrap-api-list';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

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

function normalizeOrganizationId(value: unknown): string | undefined {
  const id = preserveApiIdForRequest(value);
  return id || undefined;
}

/** 科室下拉：value 统一 string，避免雪花 ID 精度丢失导致只显示裸 id */
function buildOrganizationSelectOptions(currentOrgId?: string) {
  const options = (initOptions.value?.organizationOptions || []).map((item) => ({
    label: item.label,
    value: String(item.value),
  }));
  if (currentOrgId && !options.some((item) => item.value === currentOrgId)) {
    const matched = (initOptions.value?.organizationOptions || []).find(
      (item) => String(item.value) === currentOrgId,
    );
    options.unshift({
      label: matched?.label || currentOrgId,
      value: currentOrgId,
    });
  }
  return options;
}

const organizationSelectOptions = computed(() => {
  // 多科挂靠回显：确保已选科室（含不在当前下拉快照中的）仍可选中展示
  const currentIds = resolveOrganizationIdsForForm({
    organizationId: originalData.value.organizationId ?? props.formData?.organizationId,
    organizationIds: originalData.value.organizationIds ?? props.formData?.organizationIds,
  });
  const options = buildOrganizationSelectOptions(currentIds[0]);
  for (const id of currentIds) {
    if (!options.some((item) => item.value === id)) {
      const matched = (initOptions.value?.organizationOptions || []).find(
        (item) => String(item.value) === id,
      );
      options.unshift({
        label: matched?.label || id,
        value: id,
      });
    }
  }
  return options;
});

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
      const wards = unwrapApiList<WardBed>(await getWardListApi());
      wardOptions.value = wards.map((item) => ({
        label: item.name || '',
        value: preserveApiIdForRequest(item.id) || '',
      }));
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
      const rooms = unwrapApiList<WardBed>(await getWardBedPageApi({ id: wardId }));
      const options = rooms.map((item) => ({
        label: item.name || item.busNo || '',
        value: preserveApiIdForRequest(item.id) || '',
      }));
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
function resolveOrganizationId(submitData: Record<string, unknown>): string | undefined {
  const candidates = [
    submitData.organizationId,
    props.parentWard?.organizationId,
    props.formData?.organizationId,
    originalData.value.organizationId,
  ];
  for (const raw of candidates) {
    const id = normalizeOrganizationId(raw);
    if (id) return id;
  }
  return undefined;
}

/**
 * 规范化病区多选挂靠科室：去重保序，首项写入 organizationId。
 *
 * @param submitData 待提交表单
 */
function applyWardOrganizationIds(submitData: Record<string, any>) {
  const rawList = submitData.organizationIds;
  const ordered: string[] = [];
  if (Array.isArray(rawList)) {
    for (const raw of rawList) {
      const id = normalizeOrganizationId(raw);
      if (id && !ordered.includes(id)) {
        ordered.push(id);
      }
    }
  }
  if (ordered.length === 0) {
    const single = normalizeOrganizationId(submitData.organizationId);
    if (single) {
      ordered.push(single);
    }
  }
  if (ordered.length === 0) {
    return;
  }
  submitData.organizationIds = ordered;
  submitData.organizationId = ordered[0];
}

/**
 * 详情/草稿回填病区挂靠科室多选值。
 *
 * @param data 接口或上下文行
 */
function resolveOrganizationIdsForForm(data: Record<string, any>): string[] {
  const fromList = data.organizationIds;
  if (Array.isArray(fromList) && fromList.length > 0) {
    const ordered: string[] = [];
    for (const raw of fromList) {
      const id = normalizeOrganizationId(raw);
      if (id && !ordered.includes(id)) {
        ordered.push(id);
      }
    }
    if (ordered.length > 0) {
      return ordered;
    }
  }
  const single = normalizeOrganizationId(data.organizationId);
  if (single) {
    return [single];
  }
  return [];
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
    delete submitData.bedCount;
    // 病区：多选挂靠科室；病房/床位：从所属病区继承主挂靠
    if (submitData.formEnum === WARD_BED_FORM_CODE.WARD) {
      applyWardOrganizationIds(submitData);
      if (!submitData.organizationId) {
        ElMessage.warning('请至少选择一个关联科室');
        return;
      }
    } else if (!isEditMode.value) {
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
        if (formData.organizationId != null) {
          formData.organizationId = normalizeOrganizationId(formData.organizationId);
        }
        if (data.formEnum === WARD_BED_FORM_CODE.WARD) {
          formData.organizationIds = resolveOrganizationIdsForForm(formData);
        }
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
      if (formData.organizationId != null) {
        formData.organizationId = normalizeOrganizationId(formData.organizationId);
      }
      if (formData.formEnum === WARD_BED_FORM_CODE.WARD) {
        formData.organizationIds = resolveOrganizationIdsForForm(formData);
      }
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
      fieldName: 'organizationIds',
      label: '关联科室',
      rules: 'required',
      dependencies: {
        triggerFields: ['formEnum'],
        show: (values: Record<string, any>) => values.formEnum === WARD_BED_FORM_CODE.WARD,
        componentProps: () => ({
          options: organizationSelectOptions.value,
          filterable: true,
          clearable: true,
          multiple: true,
          collapseTags: true,
          collapseTagsTooltip: true,
          loading: initLoading.value,
          placeholder: '可多选；多科共用同一病区',
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
