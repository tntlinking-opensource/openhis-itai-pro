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

/** 下拉行：label 仍是名称，busNo 只在列表里展示，避免写入规则名称。 */
interface CatalogPickOption extends ApiOptionItem {
  busNo?: string;
}

const itemTable = ref<string>();
const distributionCategoryCode = ref<string>();
const selectedItemIds = ref<string[]>([]);
const itemOptions = ref<CatalogPickOption[]>([]);
/** 已选项缓存。远程检索会换掉当前页，确认时仍要带上之前勾过的物品。 */
const selectedOptionCache = ref<CatalogPickOption[]>([]);
const itemLoading = ref(false);
let itemRequestSeq = 0;

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

/** 把仍处于勾选状态的选项留在缓存里，供确认时取名称。 */
function retainSelectedOptions(options: CatalogPickOption[]) {
  const selectedIds = new Set(selectedItemIds.value.map((id) => String(id)));
  const merged = new Map<string, CatalogPickOption>();
  for (const option of selectedOptionCache.value) {
    const id = String(option.value);
    if (selectedIds.has(id)) {
      merged.set(id, option);
    }
  }
  for (const option of options) {
    const id = String(option.value);
    if (selectedIds.has(id)) {
      merged.set(id, option);
    }
  }
  selectedOptionCache.value = [...merged.values()];
}

/** 目录行转下拉项；主键保持字符串，避免 Long 精度丢失。 */
function toCatalogPickOption(id: unknown, name: unknown, busNo: unknown): CatalogPickOption | undefined {
  const value = String(id ?? '').trim();
  if (!value) {
    return undefined;
  }
  const code = String(busNo ?? '').trim();
  const title = String(name ?? '').trim();
  return {
    value,
    label: title || code,
    busNo: code || undefined,
  };
}

/** 按发放类别检索目录。空关键字只拉一页；有关键字时服务端匹配名称、拼音、五笔和业务编码。 */
async function loadItemOptions(keyword?: string) {
  if (!itemTable.value || !distributionCategoryCode.value) {
    itemOptions.value = [];
    return;
  }
  const seq = ++itemRequestSeq;
  const trimmedKeyword = keyword?.trim() ?? '';
  itemLoading.value = true;
  try {
    let mapped: CatalogPickOption[] = [];
    if (USE_SERVICE_LOCATION_MOCK) {
      const mockOptions = getMockItemDefinitions(itemTable.value, distributionCategoryCode.value);
      mapped = mockOptions.filter((item) => {
        if (!trimmedKeyword) {
          return true;
        }
        const kw = trimmedKeyword.toLowerCase();
        return item.label.toLowerCase().includes(kw) || String(item.value).toLowerCase().includes(kw);
      });
    } else {
      // 空关键字也只取 50：原先 500 会把下拉选项一次撑爆，弹窗打开即卡
      const params = {
        pageNo: 1,
        pageSize: 50,
        categoryCode: distributionCategoryCode.value,
        keyword: trimmedKeyword || undefined,
      };
      if (isMedicationItemTable(itemTable.value)) {
        const res = await pageMedicationCatalogApi(params);
        const content = res?.data?.content ?? [];
        mapped = content.flatMap((item) => {
          const option = toCatalogPickOption(item.id ?? item.medicationDefId, item.name, item.busNo);
          return option ? [option] : [];
        });
      } else if (isDeviceItemTable(itemTable.value)) {
        const res = await pageDeviceCatalogApi(params);
        const content = res?.data?.content ?? [];
        mapped = content.flatMap((item) => {
          const option = toCatalogPickOption(item.id, item.name ?? item.merchandiseName, item.busNo);
          return option ? [option] : [];
        });
      }
    }
    if (seq !== itemRequestSeq) {
      return;
    }
    itemOptions.value = mapped;
    retainSelectedOptions(mapped);
  } catch {
    if (seq === itemRequestSeq) {
      ElMessage.error('检索物品失败，请稍后重试');
    }
  } finally {
    if (seq === itemRequestSeq) {
      itemLoading.value = false;
    }
  }
}

/** 输入关键字后远程检索；下拉展开时组件会以空关键字调用一次。 */
function onItemKeywordSearch(keyword: string) {
  loadItemOptions(keyword);
}

/** 丢掉进行中的检索，并清空已选与候选。 */
function resetItemPickState() {
  itemRequestSeq += 1;
  selectedItemIds.value = [];
  selectedOptionCache.value = [];
  itemOptions.value = [];
  itemLoading.value = false;
}

/** 物品类型变更时重置已选 */
function onItemTableChange() {
  distributionCategoryCode.value = undefined;
  resetItemPickState();
}

/** 发放类别变更时重置已选并刷新目录 */
function onCategoryChange() {
  resetItemPickState();
  loadItemOptions();
}

/** 关闭并重置表单 */
function handleClose() {
  dialogVisible.value = false;
  itemTable.value = undefined;
  distributionCategoryCode.value = undefined;
  resetItemPickState();
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
  retainSelectedOptions(itemOptions.value);
  const picked = selectedItemIds.value.flatMap((id) => {
    const option = selectedOptionCache.value.find((item) => String(item.value) === String(id));
    return option ? [option] : [];
  });
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
      resetItemPickState();
    }
  },
);

watch(selectedItemIds, () => {
  retainSelectedOptions(itemOptions.value);
});
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
          remote
          reserve-keyword
          collapse-tags
          collapse-tags-tooltip
          placeholder="输入编码、名称或拼音检索，可多选"
          style="width: 400px"
          :loading="itemLoading"
          :disabled="!itemTable || !distributionCategoryCode"
          :remote-method="onItemKeywordSearch"
        >
          <ElOption
            v-for="item in selectableItemOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          >
            <span v-if="item.busNo" class="mr-2 text-[var(--el-text-color-secondary)]">{{
              item.busNo
            }}</span>
            <span>{{ item.label }}</span>
          </ElOption>
        </ElSelect>
      </div>
    </div>
    <template #footer>
      <ElButton @click="handleClose">取消</ElButton>
      <ElButton type="primary" @click="handleConfirm">确定添加</ElButton>
    </template>
  </ElDialog>
</template>
