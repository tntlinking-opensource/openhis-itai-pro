<script lang="ts" setup>
import type { ServiceCatalogInitResponse, ServiceCatalogItem } from '#/api';

import { computed, reactive, ref, watch } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Delete, Plus } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';

import { useVbenForm } from '#/adapter/form';
import { createServiceCatalogApi, getServiceCatalogApi, updateServiceCatalogApi } from '#/api';
import ServiceCatalogSelect from '#/components/select-service-catalog.vue';

interface Props {
  visible?: boolean;
  formData?: Partial<ServiceCatalogItem>;
  initOptions?: ServiceCatalogInitResponse;
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  formData: () => ({}),
  initOptions: () => ({}),
});

const emit = defineEmits<{
  success: [];
  'update:visible': [value: boolean];
}>();

const loading = ref(false);
const originalData = ref<Partial<ServiceCatalogItem>>({});

const isEditMode = computed(() => {
  return !!props.formData?.id;
});

const defaultChildrenRow = () => ({
  childrenId: undefined as string | undefined,
  childrenName: '',
  childrenNum: undefined as number | undefined,
});

/** 清空弹窗表单与子项列表 */
async function resetFormState() {
  originalData.value = {};
  await formApi.resetForm();
  childrenList.value = [defaultChildrenRow()];
}

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
    } as Record<string, unknown>;

    // 过滤诊疗子项中的空行（名称和执行次数都为空的行不提交）
    if (Array.isArray(mergedData.children)) {
      mergedData.children = mergedData.children.filter(
        (item: { childrenId: null | string; childrenName: string; childrenNum: null | number }) => {
          if (!item) return false;
          const hasName = !!item.childrenName;
          const hasNum =
            item.childrenNum !== null && item.childrenNum !== undefined && item.childrenNum !== 0;
          return hasName || hasNum;
        },
      );
      if (mergedData.children.length === 0) {
        delete mergedData.children;
      }
    }

    mapLegacyPackageUnitFields(mergedData);
    delete mergedData.salesUnitCode;

    const submitData: Partial<ServiceCatalogItem> = {};
    Object.keys(mergedData).forEach((key) => {
      const value = mergedData[key];
      if (value !== null && value !== undefined && value !== '') {
        submitData[key] = value;
      }
    });

    if (isEditMode.value) {
      await updateServiceCatalogApi(submitData);
      ElMessage.success('更新成功');
    } else {
      const { id: _id, ...createData } = submitData;
      await createServiceCatalogApi(createData);
      ElMessage.success('创建成功');
    }
    emit('success');
    await resetFormState();
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
      const response = await getServiceCatalogApi({ id: String(id) });
      const data = response.data;
      if (data) {
        const normalizedData = normalizeFormSelectValues(data as Record<string, unknown>);
        originalData.value = { ...normalizedData };
        formApi.setValues(normalizedData);
        childrenList.value =
          (data.children as Array<{
            childrenId: null | string;
            childrenName: string;
            childrenNum: null | number;
          }>) && Array.isArray(data.children)
            ? [...data.children]
            : [
                {
                  childrenId: null,
                  childrenName: '',
                  childrenNum: null,
                },
              ];
      } else {
        ElMessage.warning('未找到服务目录信息');
        modalApi.close();
      }
    } catch (error) {
      console.error('获取服务目录详情失败:', error);
      ElMessage.error('获取服务目录详情失败，请稍后重试');
      modalApi.close();
      return;
    } finally {
      loading.value = false;
    }
  } else {
    if (props.formData && Object.keys(props.formData).length > 0) {
      const normalizedData = normalizeFormSelectValues(props.formData as Record<string, unknown>);
      originalData.value = { ...normalizedData };
      formApi.setValues(normalizedData);
      // 初始化子项列表
      childrenList.value =
        props.formData.children && Array.isArray(props.formData.children)
          ? [...props.formData.children]
          : [];
      if (childrenList.value.length === 0) {
        childrenList.value = [
          {
            childrenId: undefined,
            childrenName: '',
            childrenNum: undefined,
          },
        ];
      }
    } else {
      originalData.value = {};
      await resetFormState();
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
  onClosed: async () => {
    await resetFormState();
  },
  onConfirm: handleSubmit,
  showCancelButton: true,
  showConfirmButton: true,
  confirmText: '确定',
  cancelText: '取消',
});

// 转换选项格式
const convertOptions = (options: unknown) => {
  if (!options || !Array.isArray(options)) {
    return [];
  }
  return options
    .filter(
      (item): item is { label: string; value: number | string } =>
        typeof item === 'object' &&
        item !== null &&
        'label' in item &&
        'value' in item &&
        (typeof item.value === 'string' || typeof item.value === 'number'),
    )
    .map((item) => ({
      label: item.label,
      value: String(item.value),
    }));
};

/** 历史表单 salesUnitCode 与后端 packageUnitCode 对齐 */
function mapLegacyPackageUnitFields(data: Record<string, unknown>) {
  if (
    data.salesUnitCode !== undefined &&
    data.salesUnitCode !== null &&
    data.salesUnitCode !== '' &&
    (data.packageUnitCode === undefined ||
      data.packageUnitCode === null ||
      data.packageUnitCode === '')
  ) {
    data.packageUnitCode = data.salesUnitCode;
  }
}

function normalizeFormSelectValues(data: Record<string, unknown>) {
  const next = { ...data };
  mapLegacyPackageUnitFields(next);
  // 这些字段在表单里是 Select，需要和 options.value 类型保持一致
  ['typeEnum', 'chrgitmLv', 'packageUnitCode', 'categoryCode', 'orgId', 'ruleId', 'ybType'].forEach(
    (field) => {
      const v = next[field];
      if (v !== undefined && v !== null && v !== '') {
        next[field] = String(v);
      }
    },
  );
  return next;
}

// 转换树形选项格式
interface TreeOption {
  label: string;
  value: number | string;
  children?: TreeOption[];
}

const convertTreeOptions = (options: unknown): TreeOption[] => {
  if (!options || !Array.isArray(options)) {
    return [];
  }
  return options
    .filter(
      (item): item is { children?: unknown[]; label: string; value: number | string } =>
        typeof item === 'object' &&
        item !== null &&
        'label' in item &&
        'value' in item &&
        (typeof item.value === 'string' || typeof item.value === 'number'),
    )
    .map((item) => ({
      label: item.label,
      value: item.value,
      children: item.children ? convertTreeOptions(item.children) : undefined,
    }));
};

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
      {
        component: 'Input',
        fieldName: 'name',
        label: '诊疗名称',
        rules: 'required',
      },
      {
        component: 'Input',
        fieldName: 'busNo',
        label: '业务编号',
        componentProps: {
          disabled: true,
          placeholder: '系统自动生成',
        },
      },
      {
        component: 'Select',
        fieldName: 'categoryCode',
        label: '目录分类',
        componentProps: {
          options: convertOptions(props.initOptions?.diagnosisCategoryOptions),
          filterable: true,
          placeholder: '请选择类别',
        },
      },
      {
        component: 'ApiTreeSelect',
        fieldName: 'orgId',
        label: '部门',
        componentProps: {
          data: convertTreeOptions(props.initOptions?.exeOrganizations),
          filterable: true,
          placeholder: '请选择部门',
          checkStrictly: true,
        },
      },
      {
        component: 'ApiTreeSelect',
        fieldName: 'packageUnitCode',
        label: '销售/计价单位',
        componentProps: {
          data: convertTreeOptions(props.initOptions?.unitCodeOptions),
          filterable: true,
          placeholder: '请选择销售/计价单位',
          checkStrictly: true,
        },
      },
      {
        component: 'Input',
        fieldName: 'pyStr',
        label: '拼音',
      },
      {
        component: 'Input',
        fieldName: 'wbStr',
        label: '五笔',
      },
      {
        component: 'RadioGroup',
        fieldName: 'ybFlag',
        label: '医保标志',
        componentProps: {
          options: convertOptions(props.initOptions?.statusWeatherOption),
        },
      },
      {
        component: 'Input',
        fieldName: 'ybNo',
        label: '医保编号',
      },
      {
        component: 'Select',
        fieldName: 'ybType',
        label: '医保类别',
        componentProps: {
          options: convertOptions(props.initOptions?.medChrgitmTypeOptions),
          filterable: true,
          placeholder: '请选择医保类别',
        },
      },
      {
        component: 'RadioGroup',
        fieldName: 'ybMatchFlag',
        label: '医保对码标记',
        componentProps: {
          options: convertOptions(props.initOptions?.statusWeatherOption),
        },
      },
      {
        component: 'Select',
        fieldName: 'ruleId',
        label: '财务类型',
        componentProps: {
          options: convertOptions(props.initOptions?.finTypeCodeOptions),
          filterable: true,
          placeholder: '请选择财务类型',
        },
      },
      {
        component: 'Select',
        fieldName: 'typeEnum',
        label: '业务类型',
        componentProps: {
          options: convertOptions(props.initOptions?.typeEnumOptions),
          placeholder: '请选择类型',
        },
      },
      {
        component: 'Select',
        fieldName: 'chrgitmLv',
        label: '医保等级',
        componentProps: {
          options: convertOptions(props.initOptions?.chrgitmLvOptions),
          placeholder: '请选择医保等级',
        },
      },
      {
        component: 'InputNumber',
        fieldName: 'purchasePrice',
        label: '成本价',
        componentProps: {
          precision: 2,
          min: 0,
          placeholder: '请输入成本价',
        },
      },
      {
        component: 'InputNumber',
        fieldName: 'retailPrice',
        label: '零售价',
        componentProps: {
          precision: 2,
          min: 0,
          placeholder: '请输入零售价',
        },
      },
      {
        component: 'InputNumber',
        fieldName: 'maximumRetailPrice',
        label: '最高零售价',
        componentProps: {
          precision: 2,
          min: 0,
          placeholder: '请输入最高零售价',
        },
      },
      {
        component: 'RadioGroup',
        fieldName: 'pricingFlag',
        label: '划价标记',
        componentProps: {
          options: convertOptions(props.initOptions?.statusWeatherOption),
        },
      },
      {
        component: 'InputArea',
        fieldName: 'descriptionText',
        label: '描述',
        componentProps: {
          rows: 3,
        },
      },
      {
        component: 'custom',
        fieldName: 'children',
        label: '诊疗子项',
        showLabel: false,
        slot: 'children',
      },
    ]),
    showDefaultActions: false,
  }),
);

// 子项列表管理
const childrenList = ref<
  Array<{
    activityId?: number;
    childrenId?: string;
    childrenName?: string;
    childrenNum?: number;
    id?: number;
  }>
>([]);

const addChild = () => {
  childrenList.value.push({
    childrenId: undefined,
    childrenName: '',
    childrenNum: undefined,
  });
};

const removeChild = (index: number) => {
  childrenList.value.splice(index, 1);
};

// 监听表单值变化，同步 children
watch(
  () => props.formData,
  async (formData) => {
    if (formData?.children && Array.isArray(formData.children)) {
      childrenList.value = [...formData.children];
    } else {
      const values = await formApi.getValues();
      if (values?.children && Array.isArray(values.children)) {
        childrenList.value = [...values.children];
      }
    }
  },
  { immediate: true, deep: true },
);

// 监听 childrenList 变化，同步到表单
watch(
  childrenList,
  (newList) => {
    formApi.setFieldValue('children', newList);
  },
  { deep: true },
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
    class="w-[750px]"
    :title="isEditMode ? '编辑' : '新增'"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
  >
    <Form>
      <template #children>
        <div class="w-full space-y-2">
          <div class="flex items-center justify-between">
            <el-button type="primary" :icon="Plus" @click="addChild"> 添加子项 </el-button>
          </div>
          <div class="w-full space-y-2">
            <div
              v-for="(item, index) in childrenList"
              :key="index"
              class="flex w-full items-center gap-2 rounded border border-gray-200 bg-gray-50 p-2"
            >
              <div class="flex flex-1 items-center gap-2">
                <div class="flex flex-1 items-center gap-2">
                  <label class="w-16 flex-shrink-0 text-sm text-gray-600">名称:</label>
                  <div class="flex-1">
                    <ServiceCatalogSelect
                      v-model="item.childrenId"
                      :exclude-id="isEditMode ? originalData.id : null"
                      placeholder="请选择诊疗项目"
                      @change="(items) => (item.childrenName = items[0]?.name)"
                      :is-selectable="true"
                      :multiple="false"
                    />
                  </div>
                </div>
                <div class="flex flex-1 items-center gap-2">
                  <label class="w-20 flex-shrink-0 text-sm text-gray-600">执行次数:</label>
                  <el-input-number
                    v-model="item.childrenNum"
                    :min="0"
                    :precision="0"
                    placeholder="请输入执行次数"
                    class="flex-1"
                    controls-position="right"
                  />
                </div>
              </div>
              <el-button
                type="danger"
                :icon="Delete"
                circle
                class="flex-shrink-0"
                @click="removeChild(index)"
              />
            </div>
          </div>
        </div>
      </template>
    </Form>
  </Modal>
</template>
