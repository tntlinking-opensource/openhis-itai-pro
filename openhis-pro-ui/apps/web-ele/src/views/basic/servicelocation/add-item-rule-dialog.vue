<script lang="ts" setup>
import type { ApiOptionItem } from '#/types/api-response';

import { computed, ref, watch } from 'vue';

import { ElButton, ElDialog, ElMessage, ElOption, ElSelect } from 'element-plus';

import {
  isDeviceItemTable,
  isMedicationItemTable,
  resolveCategoryOptionsForItemTable,
} from '#/api/basic/service-location';
import { pageDeviceCatalogApi, pageMedicationCatalogApi } from '#/api/core/system/catalog';

import { USE_SERVICE_LOCATION_MOCK, getMockItemDefinitions } from './service-location-mock';

defineOptions({ name: 'ServiceLocationAddItemRuleDialog' });

interface Props {
  visible: boolean;
  organizationId: string;
  itemTableOptions: ApiOptionItem[];
  medicationCategoryOptions: ApiOptionItem[];
  deviceCategoryOptions: ApiOptionItem[];
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  organizationId: '',
  itemTableOptions: () => [],
  medicationCategoryOptions: () => [],
  deviceCategoryOptions: () => [],
});

export interface AddItemRuleConfirmPayload {
  itemTable: string;
  distributionCategoryCode: string;
  items: ApiOptionItem[];
}

const emit = defineEmits<{
  'update:visible': [value: boolean];
  confirm: [payload: AddItemRuleConfirmPayload];
}>();

const itemTable = ref<string>();
const distributionCategoryCode = ref<string>();
const selectedItemIds = ref<string[]>([]);
const itemOptions = ref<ApiOptionItem[]>([]);
const itemLoading = ref(false);

const dialogVisible = computed({
  get: () => props.visible,
  set: (value: boolean) => emit('update:visible', value),
});

/** 当前物品类型对应的发放类别下拉 */
const distributionCategoryOptions = computed(() =>
  resolveCategoryOptionsForItemTable(
    itemTable.value,
    props.medicationCategoryOptions,
    props.deviceCategoryOptions,
  ),
);

/** 目录物品选项（同物品可再配不同药房，不做已绑去重） */
const selectableItemOptions = computed(() => itemOptions.value);

/** 按类型与类别加载物品目录 */
async function loadItemOptions() {
  if (!itemTable.value) {
    itemOptions.value = [];
    return;
  }
  itemLoading.value = true;
  try {
    if (USE_SERVICE_LOCATION_MOCK) {
      itemOptions.value = getMockItemDefinitions(itemTable.value, distributionCategoryCode.value);
      return;
    }
    const params = {
      pageNo: 1,
      pageSize: 500,
      categoryCode: distributionCategoryCode.value,
    };
    if (isMedicationItemTable(itemTable.value)) {
      const res = await pageMedicationCatalogApi(params);
      const content = res?.data?.content ?? [];
      itemOptions.value = content.map((item) => ({
        value: String(item.id ?? item.medicationDefId ?? ''),
        label: String(item.name ?? ''),
      }));
      return;
    }
    if (isDeviceItemTable(itemTable.value)) {
      const res = await pageDeviceCatalogApi(params);
      const content = res?.data?.content ?? [];
      itemOptions.value = content.map((item) => ({
        value: String(item.id ?? ''),
        label: String(item.name ?? item.merchandiseName ?? ''),
      }));
    }
  } finally {
    itemLoading.value = false;
  }
}

/** 物品类型变更时重置已选 */
function onItemTableChange() {
  selectedItemIds.value = [];
  distributionCategoryCode.value = undefined;
  itemOptions.value = [];
}

/** 发放类别变更时重置已选并刷新目录 */
function onCategoryChange() {
  selectedItemIds.value = [];
  loadItemOptions();
}

/** 关闭并重置表单 */
function handleClose() {
  dialogVisible.value = false;
  itemTable.value = undefined;
  distributionCategoryCode.value = undefined;
  selectedItemIds.value = [];
  itemOptions.value = [];
}

/** 确认添加选中的具体物品 */
function handleConfirm() {
  if (!itemTable.value) {
    ElMessage.warning('请选择物品类型');
    return;
  }
  if (!distributionCategoryCode.value) {
    ElMessage.warning('请选择发放类别');
    return;
  }
  if (!selectedItemIds.value.length) {
    ElMessage.warning('请至少选择一个具体物品');
    return;
  }
  const picked = selectableItemOptions.value.filter((item) =>
    selectedItemIds.value.includes(String(item.value)),
  );
  if (!picked.length) {
    ElMessage.warning('请至少选择一个具体物品');
    return;
  }
  emit('confirm', {
    itemTable: itemTable.value,
    distributionCategoryCode: distributionCategoryCode.value,
    items: picked,
  });
  handleClose();
}

watch(
  () => props.visible,
  (open) => {
    if (open) {
      itemTable.value = undefined;
      distributionCategoryCode.value = undefined;
      selectedItemIds.value = [];
      itemOptions.value = [];
    }
  },
);
</script>

<template>
  <ElDialog
    v-model="dialogVisible"
    title="添加指定物品"
    width="560px"
    destroy-on-close
    @close="handleClose"
  >
    <p class="m-0 mb-3 text-sm text-[var(--el-text-color-regular)]">
      一次可勾选多个物品，确认后为每个物品各生成一条取药规则，再分别选择取药药房。
    </p>
    <div class="flex flex-col gap-3">
      <div class="flex items-center gap-2">
        <span class="w-20 shrink-0 text-[var(--el-text-color-regular)]">物品类型</span>
        <ElSelect
          v-model="itemTable"
          placeholder="请选择"
          style="width: 240px"
          @change="onItemTableChange"
        >
          <ElOption
            v-for="item in itemTableOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </ElSelect>
      </div>
      <div class="flex items-center gap-2">
        <span class="w-20 shrink-0 text-[var(--el-text-color-regular)]">发放类别</span>
        <ElSelect
          v-model="distributionCategoryCode"
          placeholder="请选择"
          style="width: 240px"
          :disabled="!itemTable"
          @change="onCategoryChange"
        >
          <ElOption
            v-for="item in distributionCategoryOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </ElSelect>
      </div>
      <div class="flex items-start gap-2">
        <span class="w-20 shrink-0 pt-1 text-[var(--el-text-color-regular)]">具体物品</span>
        <ElSelect
          v-model="selectedItemIds"
          multiple
          filterable
          collapse-tags
          collapse-tags-tooltip
          placeholder="可多选，已绑定的不显示"
          style="width: 400px"
          :loading="itemLoading"
          :disabled="!itemTable || !distributionCategoryCode"
          @visible-change="(open: boolean) => open && loadItemOptions()"
        >
          <ElOption
            v-for="item in selectableItemOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </ElSelect>
      </div>
    </div>
    <template #footer>
      <ElButton @click="handleClose">取消</ElButton>
      <ElButton type="primary" @click="handleConfirm">确定添加</ElButton>
    </template>
  </ElDialog>
</template>
