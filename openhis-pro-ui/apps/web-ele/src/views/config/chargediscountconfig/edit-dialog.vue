<script setup lang="ts">
import type {
  ChargeDiscountConfigItem,
  CreateChargeDiscountConfigParams,
  DiscountCatalogOption,
  DiscountRowShape,
  ModifyChargeDiscountConfigParams,
} from '#/api/basic/charge-discount-config';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, reactive, ref, watch } from 'vue';

import { useVbenModal } from '@zxplat/common-ui';

import {
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElSelect,
} from 'element-plus';

import {
  DISCOUNT_CATALOG_SEARCH_MODE,
  createChargeDiscountConfigApi,
  discountRateToZhe,
  modifyChargeDiscountConfigApi,
  resolveDiscountRowShape,
  searchDiscountCatalogOptionsApi,
  zheToDiscountRate,
} from '#/api/basic/charge-discount-config';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

defineOptions({ name: 'BasicChargeDiscountConfigEditDialog' });

interface Props {
  visible?: boolean;
  formData?: null | Partial<ChargeDiscountConfigItem>;
  productTableOptions?: ApiOptionItem[];
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  formData: null,
  productTableOptions: () => [],
});

const emit = defineEmits<{
  success: [];
  'update:visible': [value: boolean];
}>();

interface FormModel {
  productTable: string;
  rowShape: DiscountRowShape;
  categoryCode: string;
  productId: string;
  /** 折值；空=款员定率 */
  discountZhe: null | number;
  remark: string;
}

const formRef = ref<InstanceType<typeof ElForm>>();
const formModel = reactive<FormModel>({
  productTable: '',
  rowShape: 'CATEGORY',
  categoryCode: '',
  productId: '',
  discountZhe: null,
  remark: '',
});

const categoryOptions = ref<DiscountCatalogOption[]>([]);
const productOptions = ref<DiscountCatalogOption[]>([]);
const categoryLoading = ref(false);
const productLoading = ref(false);

const isEditMode = computed(() => !!preserveApiIdForRequest(props.formData?.id));

const dialogTitle = computed(() => (isEditMode.value ? '编辑收费折扣配置' : '新增收费折扣配置'));

const productTableSelectOptions = computed(() =>
  (props.productTableOptions ?? []).map((o) => ({
    label: o.label ?? String(o.value ?? ''),
    value: String(o.value ?? ''),
  })),
);

/** 打开弹窗时回填或重置表单 */
async function resetForm() {
  categoryOptions.value = [];
  productOptions.value = [];

  const row = props.formData;
  if (row && preserveApiIdForRequest(row.id)) {
    const shape = resolveDiscountRowShape(row);
    formModel.productTable = row.productTable ?? '';
    formModel.rowShape = shape;
    formModel.categoryCode = row.categoryCode?.trim() || '';
    formModel.productId = preserveApiIdForRequest(row.productId) || '';
    formModel.discountZhe = discountRateToZhe(row.discountRate);
    formModel.remark = row.remark?.trim() || '';

    // 回显：把当前选中项塞进下拉 options，避免 label 空白
    if (shape === 'CATEGORY' && formModel.categoryCode) {
      categoryOptions.value = [
        {
          value: formModel.categoryCode,
          label: row.categoryName
            ? `${row.categoryName}（${formModel.categoryCode}）`
            : formModel.categoryCode,
        },
      ];
    }
    if (shape === 'PRODUCT' && formModel.productId) {
      productOptions.value = [
        {
          id: formModel.productId,
          value: formModel.productId,
          label: row.productName || formModel.productId,
        },
      ];
    }
  } else {
    const preferredTable = props.formData?.productTable?.trim();
    const firstTable = productTableSelectOptions.value[0]?.value ?? '';
    const hasPreferred =
      !!preferredTable && productTableSelectOptions.value.some((o) => o.value === preferredTable);
    formModel.productTable = hasPreferred ? preferredTable! : firstTable;
    // 从列表「新增单项覆盖」进入时默认单项；普通新增默认大类
    formModel.rowShape = hasPreferred ? 'PRODUCT' : 'CATEGORY';
    formModel.categoryCode = '';
    formModel.productId = '';
    formModel.discountZhe = null;
    formModel.remark = '';
  }

  formRef.value?.clearValidate();
}

/** 目录切换时清空大类/单项选择 */
function handleProductTableChange() {
  formModel.categoryCode = '';
  formModel.productId = '';
  categoryOptions.value = [];
  productOptions.value = [];
}

/** 行形态切换时清空对方键 */
function handleRowShapeChange(shape: DiscountRowShape) {
  formModel.rowShape = shape;
  if (shape === 'CATEGORY') {
    formModel.productId = '';
    productOptions.value = [];
  } else {
    formModel.categoryCode = '';
    categoryOptions.value = [];
  }
}

/** 远程检索大类术语 */
async function remoteSearchCategory(keyword: string) {
  if (!formModel.productTable) {
    categoryOptions.value = [];
    return;
  }
  categoryLoading.value = true;
  try {
    const res = await searchDiscountCatalogOptionsApi({
      productTable: formModel.productTable,
      searchMode: DISCOUNT_CATALOG_SEARCH_MODE.CATEGORY,
      keyword: keyword?.trim() || undefined,
      pageNo: 1,
      pageSize: 50,
    });
    const parsed = parsePagedRowsFromApiData<DiscountCatalogOption>(res);
    categoryOptions.value = parsed.content;
  } catch {
    categoryOptions.value = [];
  } finally {
    categoryLoading.value = false;
  }
}

/** 远程检索目录项目 */
async function remoteSearchProduct(keyword: string) {
  if (!formModel.productTable) {
    productOptions.value = [];
    return;
  }
  productLoading.value = true;
  try {
    const res = await searchDiscountCatalogOptionsApi({
      productTable: formModel.productTable,
      searchMode: DISCOUNT_CATALOG_SEARCH_MODE.ITEM,
      keyword: keyword?.trim() || undefined,
      pageNo: 1,
      pageSize: 50,
    });
    const parsed = parsePagedRowsFromApiData<DiscountCatalogOption>(res);
    // 单项 value 统一 string，避免 Long 精度问题
    productOptions.value = parsed.content.map((item) => ({
      ...item,
      id: preserveApiIdForRequest(item.id) ?? preserveApiIdForRequest(item.value),
      value: preserveApiIdForRequest(item.value) ?? preserveApiIdForRequest(item.id),
    }));
  } catch {
    productOptions.value = [];
  } finally {
    productLoading.value = false;
  }
}

/** 组装写请求体（大类/单项互斥） */
function buildWritePayload(): CreateChargeDiscountConfigParams | null {
  const productTable = formModel.productTable?.trim();
  if (!productTable) {
    ElMessage.error('请选择目录');
    return null;
  }

  const remark = formModel.remark?.trim() || null;
  // 空表示款员定率，显式传 null 以便修改时清空原固定率
  let discountRate: null | number = null;
  if (formModel.discountZhe !== null && formModel.discountZhe !== undefined) {
    discountRate = zheToDiscountRate(formModel.discountZhe);
    if (discountRate === null) {
      ElMessage.error('折扣值须为空或属于 (0,10] 折，例如 8.5');
      return null;
    }
  }

  if (formModel.rowShape === 'CATEGORY') {
    const categoryCode = formModel.categoryCode?.trim();
    if (!categoryCode) {
      ElMessage.error('请选择大类');
      return null;
    }
    return {
      productTable,
      categoryCode,
      productId: null,
      discountRate,
      remark,
    };
  }

  const productId = preserveApiIdForRequest(formModel.productId);
  if (!productId) {
    ElMessage.error('请选择项目');
    return null;
  }
  return {
    productTable,
    categoryCode: null,
    productId,
    discountRate,
    remark,
  };
}

/** 提交新增/修改 */
async function handleSubmit() {
  try {
    await formRef.value?.validate();
  } catch {
    return;
  }
  const payload = buildWritePayload();
  if (!payload) {
    return;
  }

  modalApi.lock(true);
  try {
    if (isEditMode.value) {
      const id = preserveApiIdForRequest(props.formData?.id);
      if (!id) {
        ElMessage.error('缺少配置主键');
        return;
      }
      const modifyPayload: ModifyChargeDiscountConfigParams = { ...payload, id };
      await modifyChargeDiscountConfigApi(modifyPayload);
      ElMessage.success('修改成功');
    } else {
      await createChargeDiscountConfigApi(payload);
      ElMessage.success('新增成功');
    }
    emit('success');
    modalApi.close();
  } catch (error: unknown) {
    const err = error as { message?: string; msg?: string };
    ElMessage.error(err.msg || err.message || '保存失败，请稍后重试');
  } finally {
    modalApi.unlock();
  }
}

const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen: boolean) => {
    emit('update:visible', isOpen);
  },
  onOpened: async () => {
    await resetForm();
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
    class="w-[560px]"
    :title="dialogTitle"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
  >
    <ElForm ref="formRef" :model="formModel" label-width="96px" class="charge-discount-edit-form">
      <ElFormItem
        label="目录"
        prop="productTable"
        :rules="[{ required: true, message: '请选择目录', trigger: 'change' }]"
      >
        <ElSelect
          v-model="formModel.productTable"
          placeholder="药品 / 诊疗 / 耗材"
          style="width: 100%"
          @change="handleProductTableChange"
        >
          <ElOption
            v-for="item in productTableSelectOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </ElSelect>
      </ElFormItem>

      <ElFormItem label="行形态" required>
        <ElRadioGroup
          :model-value="formModel.rowShape"
          @update:model-value="(v) => handleRowShapeChange(v as DiscountRowShape)"
        >
          <ElRadio value="CATEGORY">大类行</ElRadio>
          <ElRadio value="PRODUCT">单项行</ElRadio>
        </ElRadioGroup>
      </ElFormItem>

      <ElFormItem
        v-if="formModel.rowShape === 'CATEGORY'"
        label="大类"
        prop="categoryCode"
        :rules="[{ required: true, message: '请选择大类', trigger: 'change' }]"
      >
        <ElSelect
          v-model="formModel.categoryCode"
          filterable
          remote
          clearable
          reserve-keyword
          placeholder="输入关键字检索大类"
          :remote-method="remoteSearchCategory"
          :loading="categoryLoading"
          style="width: 100%"
        >
          <ElOption
            v-for="item in categoryOptions"
            :key="String(item.value)"
            :label="item.label || String(item.value)"
            :value="String(item.value ?? '')"
          />
        </ElSelect>
      </ElFormItem>

      <ElFormItem
        v-if="formModel.rowShape === 'PRODUCT'"
        label="项目"
        prop="productId"
        :rules="[{ required: true, message: '请选择项目', trigger: 'change' }]"
      >
        <ElSelect
          v-model="formModel.productId"
          filterable
          remote
          clearable
          reserve-keyword
          placeholder="输入关键字检索项目"
          :remote-method="remoteSearchProduct"
          :loading="productLoading"
          style="width: 100%"
        >
          <ElOption
            v-for="item in productOptions"
            :key="String(item.value)"
            :label="item.label || String(item.value)"
            :value="String(item.value ?? '')"
          />
        </ElSelect>
      </ElFormItem>

      <ElFormItem label="折扣值">
        <div class="zhe-field">
          <ElInputNumber
            v-model="formModel.discountZhe"
            :min="0.1"
            :max="10"
            :step="0.1"
            :precision="1"
            controls-position="right"
            placeholder="空=款员定率"
            style="width: 100%"
          />
          <span class="zhe-suffix">折</span>
        </div>
        <div class="form-hint">例如 8.5 折表示原价 × 0.85；不填则由款员收费时定率</div>
      </ElFormItem>

      <ElFormItem label="备注">
        <ElInput
          v-model="formModel.remark"
          type="textarea"
          :rows="2"
          maxlength="200"
          show-word-limit
          placeholder="可选"
        />
      </ElFormItem>
    </ElForm>
  </Modal>
</template>

<style scoped>
.charge-discount-edit-form {
  padding: 4px 8px 0;
}

.zhe-field {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.zhe-suffix {
  flex-shrink: 0;
  color: var(--el-text-color-regular);
}

.form-hint {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-text-color-regular);
}
</style>
