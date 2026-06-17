<script lang="ts" setup>
import type { ServiceCatalogInitResponse, ServiceCatalogItem } from '#/api';

import type { NationalCatalogItem } from '#/chs/api';

import { computed, reactive, ref, watch } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Delete, Plus } from '@element-plus/icons-vue';
import { ElButton, ElMessage, ElMessageBox } from 'element-plus';

import { useVbenForm } from '#/adapter/form';
import {
  EXTENDED_FORM_KEYBOARD_SELECTORS,
  FORM_KEYBOARD_GRID_NAV_KEYS,
  useFormKeyboardNavigation,
} from '#/hooks/use-form-keyboard-navigation';
import { createServiceCatalogApi, getServiceCatalogApi, updateServiceCatalogApi } from '#/api';
import { getChargeTypeListApi } from '#/api/core/common';
import { revokeActivityCatalog3302Api, uploadActivityCatalog3301Api } from '#/chs/api';
import MedicationCatalogPickerDialog from '#/chs/components/medication-catalog-picker-dialog.vue';
import { catalogChrgitmLvToFormValue } from '#/chs/constants/catalog-chrgitm-lv';
import {
  filterInsuranceChargeTypes,
  resolveDefaultInsuranceBusNo,
} from '#/chs/constants/insurance-contract';
import { ensureInsuranceSignIn } from '#/chs/utils/ensure-insurance-sign-in';
import ServiceCatalogSelect from '#/components/select-service-catalog.vue';

defineOptions({
  name: 'ServiceCatalogEditDialog',
});

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
const upload3301Loading = ref(false);
const revoke3302Loading = ref(false);
const pickerVisible = ref(false);
const defaultContractBusNo = ref<string>();
const originalData = ref<Partial<ServiceCatalogItem>>({});
const insuranceFormData = ref<Partial<ServiceCatalogItem>>({});
const formAreaRef = ref<HTMLElement | null>(null);

const { handleFormKeydown: handleServiceFormKeydown } = useFormKeyboardNavigation(formAreaRef, {
  selectors: EXTENDED_FORM_KEYBOARD_SELECTORS,
  keys: FORM_KEYBOARD_GRID_NAV_KEYS,
  selectPolicy: true,
  navigationMode: 'grid',
});

const isEditMode = computed(() => {
  return !!props.formData?.id;
});

const activityDefId = computed(() => originalData.value?.id ?? props.formData?.id);

const pickerContext = computed(() => ({
  name: String(insuranceFormData.value?.name ?? ''),
  listType: '201',
}));

const canUpload3301 = computed(
  () => isEditMode.value && Boolean(String(insuranceFormData.value?.ybNo ?? '').trim()),
);

const canRevoke3302 = computed(
  () =>
    isEditMode.value &&
    Boolean(String(insuranceFormData.value?.ybNo ?? '').trim()) &&
    isYbMatched(insuranceFormData.value?.ybMatchFlag),
);

function isYbMatched(value: unknown): boolean {
  return value === true || value === 1 || value === '1' || value === 'YES';
}

function syncInsuranceFormData(data: Record<string, unknown>) {
  insuranceFormData.value = {
    name: data.name as string | undefined,
    ybNo: data.ybNo as string | undefined,
    ybMatchFlag: data.ybMatchFlag as ServiceCatalogItem['ybMatchFlag'],
    chrgitmLv: data.chrgitmLv as ServiceCatalogItem['chrgitmLv'],
  };
}

async function loadDefaultContractBusNo() {
  try {
    const list = await getChargeTypeListApi();
    const eligible = filterInsuranceChargeTypes(list ?? []);
    defaultContractBusNo.value = resolveDefaultInsuranceBusNo(eligible, defaultContractBusNo.value);
  } catch {
    defaultContractBusNo.value = undefined;
  }
}

function openCatalogPicker() {
  pickerVisible.value = true;
}

function applyCatalogSelection(row: NationalCatalogItem) {
  formApi.setFieldValue('ybNo', row.hilistCode ?? '');
  const lv = catalogChrgitmLvToFormValue(row.chrgitmLv);
  if (lv !== undefined) {
    formApi.setFieldValue('chrgitmLv', lv);
  }
  formApi.setFieldValue('ybMatchFlag', 'NO');
  syncInsuranceFormData({
    ...insuranceFormData.value,
    ybNo: row.hilistCode,
    chrgitmLv: lv,
    ybMatchFlag: 'NO',
  });
  ElMessage.success(`已选用医保编码 ${row.hilistCode}，保存诊疗后可上传 3301`);
}

async function handleUpload3301() {
  const id = activityDefId.value;
  if (!id) {
    ElMessage.warning('请先保存诊疗后再上传 3301');
    return;
  }
  await loadDefaultContractBusNo();
  if (!defaultContractBusNo.value) {
    ElMessage.warning('未配置医保契约');
    return;
  }
  const signedIn = await ensureInsuranceSignIn(defaultContractBusNo.value);
  if (!signedIn) return;
  upload3301Loading.value = true;
  try {
    const result = await uploadActivityCatalog3301Api({
      contractBusNo: defaultContractBusNo.value,
      activityDefId: id,
      listType: '201',
    });
    if (result.allSuccess) {
      ElMessage.success(result.summary ?? '3301 上传成功');
      formApi.setFieldValue('ybMatchFlag', 'YES');
      syncInsuranceFormData({ ...insuranceFormData.value, ybMatchFlag: 'YES' });
    } else {
      ElMessage.error(result.summary ?? '3301 上传失败');
    }
  } catch (error: unknown) {
    const msg = error instanceof Error ? error.message : '3301 上传失败';
    ElMessage.error(msg);
  } finally {
    upload3301Loading.value = false;
  }
}

async function handleRevoke3302() {
  const id = activityDefId.value;
  if (!id) {
    ElMessage.warning('请先保存诊疗后再撤销 3302');
    return;
  }
  try {
    await ElMessageBox.confirm(
      '将撤销该诊疗项目在医保平台的目录对照，本地「医保对码」将取消勾选。是否继续？',
      '撤销医保对照(3302)',
      { type: 'warning', confirmButtonText: '撤销', cancelButtonText: '取消' },
    );
  } catch {
    return;
  }
  await loadDefaultContractBusNo();
  if (!defaultContractBusNo.value) {
    ElMessage.warning('未配置医保契约');
    return;
  }
  const signedIn = await ensureInsuranceSignIn(defaultContractBusNo.value);
  if (!signedIn) return;
  revoke3302Loading.value = true;
  try {
    const result = await revokeActivityCatalog3302Api({
      contractBusNo: defaultContractBusNo.value,
      activityDefId: id,
      listType: '201',
    });
    if (result.allSuccess) {
      ElMessage.success(result.summary ?? '3302 撤销成功');
      formApi.setFieldValue('ybMatchFlag', 'NO');
      syncInsuranceFormData({ ...insuranceFormData.value, ybMatchFlag: 'NO' });
    } else {
      ElMessage.error(result.summary ?? '3302 撤销失败');
    }
  } catch (error: unknown) {
    const msg = error instanceof Error ? error.message : '3302 撤销失败';
    ElMessage.error(msg);
  } finally {
    revoke3302Loading.value = false;
  }
}

const defaultChildrenRow = () => ({
  childrenId: undefined as string | undefined,
  childrenName: '',
  childrenNum: undefined as number | undefined,
});

/** 清空弹窗表单与子项列表 */
async function resetFormState() {
  originalData.value = {};
  insuranceFormData.value = {};
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
    const normalizedYbFlag = normalizeWhetherFlagForSubmit(mergedData.ybFlag);
    if (normalizedYbFlag !== undefined) {
      mergedData.ybFlag = normalizedYbFlag;
    }
    const normalizedYbMatchFlag = normalizeWhetherFlagForSubmit(mergedData.ybMatchFlag);
    if (normalizedYbMatchFlag !== undefined) {
      mergedData.ybMatchFlag = normalizedYbMatchFlag;
    }

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
        syncInsuranceFormData(normalizedData);
        childrenList.value =
          Array.isArray(data.children) && data.children.length > 0
            ? data.children.map((child) => ({
                ...child,
                childrenId:
                  child.childrenId !== null && child.childrenId !== undefined
                    ? String(child.childrenId)
                    : undefined,
              }))
            : [defaultChildrenRow()];
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
      syncInsuranceFormData(normalizedData);
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
  contentClass: 'service-catalog-form-dialog__content',
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

function coerceWhetherOptionValue(value: unknown): string | undefined {
  if (value === true || value === 1 || value === '1') {
    return 'YES';
  }
  if (value === false || value === 0 || value === '0') {
    return 'NO';
  }
  if (value === 'YES' || value === 'NO') {
    return value;
  }
  return undefined;
}

function normalizeWhetherFlagForSubmit(value: unknown): number | undefined {
  if (value === true || value === 1 || value === '1' || value === 'YES') {
    return 1;
  }
  if (value === false || value === 0 || value === '0' || value === 'NO') {
    return 0;
  }
  return undefined;
}

function normalizeFormSelectValues(data: Record<string, unknown>) {
  const next = { ...data };
  mapLegacyPackageUnitFields(next);
  // 这些字段在表单里是 Select，需要和 options.value 类型保持一致
  ['typeEnum', 'chrgitmLv', 'packageUnitCode', 'categoryCode', 'itemTypeCode', 'ybType'].forEach(
    (field) => {
      const v = next[field];
      if (v !== undefined && v !== null && v !== '') {
        next[field] = String(v);
      }
    },
  );
  const ybFlag = coerceWhetherOptionValue(next.ybFlag);
  if (ybFlag !== undefined) {
    next.ybFlag = ybFlag;
  }
  const ybMatchFlag = coerceWhetherOptionValue(next.ybMatchFlag);
  if (ybMatchFlag !== undefined) {
    next.ybMatchFlag = ybMatchFlag;
  }
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
    layout: 'horizontal',
    commonConfig: {
      labelClass: 'justify-start text-left',
      formFieldProps: {
        validateOnBlur: false,
        validateOnChange: false,
        validateOnInput: false,
        validateOnModelUpdate: false,
      },
      componentProps: {
        validateEvent: false,
        class: 'w-full',
      },
    },
    wrapperClass: 'grid grid-cols-3 gap-x-4',
    schema: computed(() => [
      {
        component: 'SectionTitle',
        fieldName: '__section_basic',
        label: '基础信息',
        hideLabel: true,
        formItemClass: 'col-span-full !mb-0 !pb-0 service-catalog-form-dialog__section-title',
        componentProps: {
          title: '基础信息',
        },
      },
      {
        component: 'Input',
        fieldName: 'name',
        label: '诊疗名称',
        rules: 'required',
      },
      {
        component: 'Select',
        fieldName: 'categoryCode',
        label: '目录分类',
        rules: 'required',
        componentProps: {
          options: convertOptions(props.initOptions?.diagnosisCategoryOptions),
          filterable: true,
          placeholder: '请选择目录分类',
          clearable: true,
        },
      },
      {
        component: 'Select',
        fieldName: 'typeEnum',
        label: '业务类型',
        rules: 'required',
        componentProps: {
          options: convertOptions(props.initOptions?.typeEnumOptions),
          placeholder: '请选择业务类型',
          clearable: true,
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
          clearable: true,
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
        component: 'Select',
        fieldName: 'itemTypeCode',
        label: '财务类型',
        componentProps: {
          options: convertOptions(props.initOptions?.finTypeCodeOptions),
          filterable: true,
          placeholder: '请选择财务类型',
          clearable: true,
        },
      },
      {
        component: 'InputNumber',
        fieldName: 'purchasePrice',
        label: '成本价',
        componentProps: {
          precision: 2,
          min: 0,
          controlsPosition: 'right',
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
          controlsPosition: 'right',
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
          controlsPosition: 'right',
          placeholder: '请输入最高零售价',
        },
      },
      {
        component: 'InputArea',
        fieldName: 'descriptionText',
        label: '描述',
        formItemClass: 'col-span-full',
        componentProps: {
          rows: 3,
          class: 'w-full',
        },
      },
      {
        component: 'SectionTitle',
        fieldName: '__section_yb',
        label: '医保信息',
        hideLabel: true,
        formItemClass: 'col-span-full !mb-0 !pb-0 service-catalog-form-dialog__section-title',
        componentProps: {
          title: '医保信息',
        },
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
        componentProps: {
          onInput: (value: string) => {
            syncInsuranceFormData({ ...insuranceFormData.value, ybNo: value });
          },
        },
      },
      {
        component: 'Select',
        fieldName: 'ybType',
        label: '医保类别',
        componentProps: {
          options: convertOptions(props.initOptions?.medChrgitmTypeOptions),
          filterable: true,
          placeholder: '请选择医保类别',
          clearable: true,
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
        fieldName: 'chrgitmLv',
        label: '医保等级',
        componentProps: {
          options: convertOptions(props.initOptions?.chrgitmLvOptions),
          placeholder: '请选择医保等级',
          clearable: true,
        },
      },
      {
        component: 'SectionTitle',
        fieldName: '__section_children',
        label: '子项',
        hideLabel: true,
        formItemClass: 'col-span-full !mb-0 !pb-0 service-catalog-form-dialog__section-title',
        componentProps: {
          title: '子项',
        },
      },
      {
        component: 'custom',
        fieldName: 'children',
        label: '子项',
        hideLabel: true,
        formItemClass: 'col-span-full !mb-0',
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
    class="service-catalog-form-dialog w-[960px] max-w-[98vw]"
    :title="isEditMode ? '编辑诊疗' : '新增诊疗'"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
  >
    <div ref="formAreaRef" @keydown.capture="handleServiceFormKeydown">
      <Form>
        <template #children>
          <div class="w-full space-y-2">
            <div class="flex items-center justify-start">
              <el-button type="primary" :icon="Plus" @click="addChild">添加子项</el-button>
            </div>
            <div class="w-full space-y-2">
              <div
                v-for="(item, index) in childrenList"
                :key="index"
                class="service-catalog-form-dialog__child-row flex w-full items-center gap-2 rounded border p-2"
              >
                <div class="flex flex-1 items-center gap-2">
                  <label class="w-12 flex-shrink-0 text-sm">名称</label>
                  <div class="min-w-0 flex-1">
                    <ServiceCatalogSelect
                      v-model="item.childrenId"
                      :exclude-id="isEditMode ? originalData.id : null"
                      :selected-label="item.childrenName"
                      placeholder="请选择诊疗项目"
                      :multiple="false"
                      @change="(items) => (item.childrenName = items[0]?.name)"
                    />
                  </div>
                </div>
                <div class="flex w-[200px] items-center gap-2">
                  <label class="w-16 flex-shrink-0 text-sm">执行次数</label>
                  <el-input-number
                    v-model="item.childrenNum"
                    :min="0"
                    :precision="0"
                    placeholder="次数"
                    class="min-w-0 flex-1"
                    controls-position="right"
                  />
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
      <div
        v-if="isEditMode"
        class="mt-3 flex flex-wrap gap-2 border-t border-[var(--el-border-color-lighter)] pt-3"
      >
        <ElButton type="success" plain @click="openCatalogPicker">从国家目录选择</ElButton>
        <ElButton
          v-if="canUpload3301"
          type="warning"
          plain
          :loading="upload3301Loading"
          @click="handleUpload3301"
        >
          上传医保对照(3301)
        </ElButton>
        <ElButton
          v-if="canRevoke3302"
          type="danger"
          plain
          :loading="revoke3302Loading"
          @click="handleRevoke3302"
        >
          撤销医保对照(3302)
        </ElButton>
      </div>
    </div>
    <MedicationCatalogPickerDialog
      v-model="pickerVisible"
      :context="pickerContext"
      @confirm="applyCatalogSelection"
    />
  </Modal>
</template>

<style scoped>
.service-catalog-form-dialog__child-row {
  border-color: var(--el-border-color-lighter);
  background-color: var(--el-fill-color-lighter);
}

.service-catalog-form-dialog__child-row label {
  color: var(--el-text-color-regular);
}
</style>

<style>
.service-catalog-form-dialog {
  max-height: 94vh !important;
}

.service-catalog-form-dialog__content {
  overflow: visible !important;
}

.service-catalog-form-dialog .service-catalog-form-dialog__section-title {
  margin-top: 0;
  padding-bottom: 0 !important;
}

.service-catalog-form-dialog .service-catalog-form-dialog__section-title:not(:first-child) {
  margin-top: 8px;
}

.service-catalog-form-dialog :deep(.el-form-item) {
  margin-bottom: 14px;
}

.service-catalog-form-dialog :deep(.el-input-number) {
  width: 100%;
  max-width: 100%;
}

.service-catalog-form-dialog :deep(.el-input-number .el-input__wrapper) {
  width: 100%;
}
</style>
