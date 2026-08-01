<script setup lang="ts">
import type { TcmHerbCardItem } from '#/components/doctor-station/tcm-advice/components/tcm-herb-card.vue';

import type {
  AdviceProductOption,
  ProductColumn,
  ProductQueryParams,
} from '#/views/outpatient/doctor-station/advice';

import { computed, nextTick, onMounted, ref, watch } from 'vue';

import { ElButton, ElInputNumber, ElMessage, ElOption, ElScrollbar, ElSelect } from 'element-plus';

import { decimalMul } from '#/utils/format/decimal-util';
import { calcMoneyLineTotal, formatMoneyDisplay } from '#/utils/format/money-scale';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { AdviceOrderSelect } from '#/views/outpatient/doctor-station/advice';

import './tcm-herb-workspace.css';

defineOptions({ name: 'TcmHerbPanel' });

interface DosageInstructionOption {
  label: string;
  value: string;
}

interface Props {
  autofocusProductLineKey?: null | string;
  chineseHerbsDoseQuantity?: number;
  dosageInstructionOptions?: DosageInstructionOption[];
  editingLineKey?: null | string;
  lines?: TcmHerbCardItem[];
  productColumns?: ProductColumn[];
  queryProductOptions?: (params: ProductQueryParams) => Promise<{
    content: AdviceProductOption[];
    totalElements: number;
  }>;
  readonly?: boolean;
  selectedLineKeys?: Set<string>;
}

const props = withDefaults(defineProps<Props>(), {
  autofocusProductLineKey: null,
  chineseHerbsDoseQuantity: undefined,
  dosageInstructionOptions: () => [],
  editingLineKey: null,
  lines: () => [],
  productColumns: () => [],
  queryProductOptions: undefined,
  readonly: false,
  selectedLineKeys: () => new Set<string>(),
});

const emit = defineEmits<{
  add: [];
  addProduct: [product: AdviceProductOption];
  autofocusProductConsumed: [lineKey: string];
  clearSelection: [];
  editStart: [lineKey: string];
  productSelect: [lineKey: string, product: AdviceProductOption];
  remove: [lineKey: string];
  select: [lineKey: string];
  updateDosageInstruction: [lineKey: string, code: string];
  updateDose: [lineKey: string, dose: number];
}>();

/** 顶部加药检索：选中后清空，便于连续加药 */
const searchProductId = ref('');
/** 递增以展开通用选药下拉 */
const searchExpandNonce = ref(0);
const focusDoseLineKey = ref<null | string>(null);
const doseInputRefs = ref<Record<string, { focus?: () => void; $el?: HTMLElement } | null>>({});
/** 信息栏检索挂载点是否已就绪 */
const searchHostReady = ref(false);

/** 已选药材行 */
const displayLines = computed(() =>
  props.lines.filter((line) => String(line.medicationDefinitionId ?? '').trim()),
);

/** 行号 */
const rowIndexes = computed(() => {
  const map = new Map<string, number>();
  displayLines.value.forEach((line, index) => {
    map.set(line.clientLineKey, index + 1);
  });
  return map;
});

/** 已选 definitionId（禁重复） */
const selectedDefinitionIds = computed(() => {
  const ids: string[] = [];
  for (const line of displayLines.value) {
    const id = preserveApiIdForRequest(line.medicationDefinitionId);
    if (id) ids.push(id);
  }
  return ids;
});

watch(focusDoseLineKey, async (lineKey) => {
  if (!lineKey) return;
  await nextTick();
  // 下拉关闭会抢焦点：等关闭动画后再聚焦剂量框
  window.setTimeout(() => {
    focusDoseInput(lineKey);
    if (focusDoseLineKey.value === lineKey) {
      focusDoseLineKey.value = null;
    }
  }, 60);
});

/** 探测父级信息栏检索挂载点（与 mock 信息栏右对齐） */
function refreshSearchHostReady() {
  searchHostReady.value = Boolean(document.querySelector('.tcm-rx-panel__search-host'));
}

refreshSearchHostReady();
onMounted(() => {
  refreshSearchHostReady();
  void nextTick(() => {
    refreshSearchHostReady();
  });
});

/** 绑定剂量输入 ref */
function setDoseInputRef(lineKey: string, el: unknown) {
  doseInputRefs.value[lineKey] = el as { focus?: () => void; $el?: HTMLElement } | null;
}

/** 聚焦剂量输入并选中，便于直接改数 */
function focusDoseInput(lineKey: string) {
  const inst = doseInputRefs.value[lineKey];
  inst?.focus?.();
  // 组件实例 $el 或行内 data 属性兜底，避免 EP InputNumber 抢焦点失败
  const fromInst = inst?.$el?.querySelector?.('input') as HTMLInputElement | null | undefined;
  if (fromInst) {
    fromInst.focus();
    fromInst.select();
    return;
  }
  const fromDom = document.querySelector(
    `.tcm-herb-strip__dose[data-dose-key="${CSS.escape(lineKey)}"] input`,
  ) as HTMLInputElement | null;
  if (fromDom) {
    fromDom.focus();
    fromDom.select();
  }
}

/** 当前付数 */
function resolveDoseCount(): number | undefined {
  const doseCount = props.chineseHerbsDoseQuantity;
  if (doseCount == null || doseCount <= 0) return undefined;
  return doseCount;
}

/** 用量单位 */
function doseUnitDisplay(item: TcmHerbCardItem): string {
  const name = String(item.doseUnitName ?? '').trim();
  if (name) return name;
  return (item.doseUnitCode || 'g').trim() || 'g';
}

/** 只读剂量文案（无剂量时显示占位） */
function doseReadonlyText(item: TcmHerbCardItem): string {
  const dose = Number(item.dose || 0);
  if (dose <= 0) return '—';
  return `${Math.round(dose)}`;
}

/** 编辑态剂量：0 显示为空，避免预填默认值；克数仅整数 */
function doseEditValue(item: TcmHerbCardItem): number | undefined {
  const dose = Number(item.dose || 0);
  return dose > 0 ? Math.round(dose) : undefined;
}

/** 行小计金额（付数>1 时含总付） */
function lineTotalAmount(item: TcmHerbCardItem): number {
  const doseCount = resolveDoseCount();
  const single = calcMoneyLineTotal(item.retailPrice, Number(item.dose || 0));
  if (doseCount == null || doseCount <= 1) {
    return single;
  }
  return calcMoneyLineTotal(item.retailPrice, decimalMul(Number(item.dose || 0), doseCount));
}

/** 单价展示 */
function unitPriceDisplayText(item: TcmHerbCardItem): string {
  return formatMoneyDisplay(item.retailPrice || 0);
}

/** 小计展示 */
function lineTotalDisplayText(item: TcmHerbCardItem): string {
  return formatMoneyDisplay(lineTotalAmount(item));
}

/** 是否特殊煎法 */
function isSpecialDecoction(item: TcmHerbCardItem): boolean {
  const text = String(item.dosageInstructionText ?? '').trim();
  const code = String(item.dosageInstructionCode ?? '').trim();
  if (!code && !text) return false;
  if (text === '正常' || text === '常规') return false;
  return true;
}

/** 煎法只读文案 */
function decoctionReadonlyText(item: TcmHerbCardItem): string {
  const text = String(item.dosageInstructionText ?? '').trim();
  if (text) return text;
  return '正常';
}

/** 解析目录主键 */
function resolveDefinitionId(row: AdviceProductOption): string {
  return preserveApiIdForRequest(row.productDefinitionId) ?? preserveApiIdForRequest(row.id) ?? '';
}

/** 是否已在处方中 */
function isAlreadySelected(row: AdviceProductOption): boolean {
  const id = resolveDefinitionId(row);
  if (!id) return false;
  return selectedDefinitionIds.value.includes(id);
}

/** 通用下拉选药：加入处方后清空检索（中药不保留上一味），并聚焦新行剂量 */
async function handleSearchSelect(row: AdviceProductOption) {
  if (props.readonly) return;
  if (isAlreadySelected(row)) {
    ElMessage.warning('该药品已在处方中');
    searchProductId.value = '';
    return;
  }
  emit('addProduct', row);
  // 先清空 v-model，避免共用选品实例残留上一味 id/展示名
  searchProductId.value = '';
  await nextTick();
  await nextTick();
  const last = displayLines.value[displayLines.value.length - 1];
  if (last) {
    focusDoseLineKey.value = last.clientLineKey;
  }
}

/** 改剂量（克数仅整数） */
function handleRowDoseChange(lineKey: string, value: number | undefined) {
  emit('updateDose', lineKey, Math.round(Number(value ?? 0)));
}

/** 剂量回车：下一味或回到检索 */
function handleRowDoseEnter(lineKey: string) {
  const keys = displayLines.value.map((l) => l.clientLineKey);
  const idx = keys.indexOf(lineKey);
  const nextKey = idx >= 0 ? keys[idx + 1] : undefined;
  if (nextKey) {
    focusDoseLineKey.value = nextKey;
    return;
  }
  bumpAddFocus();
}

/**
 * 剂量框上下键：禁用 ElInputNumber 增减，改为切换上一味/下一味剂量焦点。
 * capture 阶段拦截，避免数值被步进。
 */
function handleRowDoseKeydown(lineKey: string, event: KeyboardEvent) {
  if (event.key !== 'ArrowUp' && event.key !== 'ArrowDown') return;
  event.preventDefault();
  event.stopPropagation();
  const keys = displayLines.value.map((l) => l.clientLineKey);
  const idx = keys.indexOf(lineKey);
  if (idx < 0) return;
  const targetKey = event.key === 'ArrowUp' ? keys[idx - 1] : keys[idx + 1];
  if (!targetKey) return;
  focusDoseLineKey.value = targetKey;
}

/** 改煎法 */
function handleRowDosageChange(lineKey: string, value: string | undefined) {
  emit('updateDosageInstruction', lineKey, String(value ?? ''));
}

/** 删除行 */
function handleRemove(lineKey: string) {
  emit('remove', lineKey);
}

/** 聚焦检索（组套套用后 / 新增处方） */
function bumpAddFocus() {
  if (props.readonly) return;
  searchExpandNonce.value += 1;
}

defineExpose({ bumpAddFocus });
</script>

<template>
  <!-- 朝阳：检索挂到信息栏；药表对齐 mock 单列表 -->
  <div class="tcm-herb-workspace flex min-h-0 flex-1 flex-col">
    <Teleport
      v-if="!readonly && queryProductOptions && searchHostReady"
      to=".tcm-rx-panel__search-host"
    >
      <div class="tcm-herb-workspace__search">
        <AdviceOrderSelect
          v-model="searchProductId"
          :key="`tcm-search-${productColumns.map((c) => c.prop).join('-')}`"
          :columns="productColumns"
          :remote-method="queryProductOptions"
          :expand-nonce="searchExpandNonce"
          placeholder="药名/拼音"
          @select="handleSearchSelect"
        />
      </div>
    </Teleport>

    <!-- 无挂载点时回退到表上方（兜底） -->
    <div
      v-if="!readonly && queryProductOptions && !searchHostReady"
      class="tcm-herb-workspace__toolbar flex shrink-0 items-center gap-3"
    >
      <div class="tcm-herb-workspace__search relative z-[5] shrink-0">
        <AdviceOrderSelect
          v-model="searchProductId"
          :key="`tcm-search-${productColumns.map((c) => c.prop).join('-')}`"
          :columns="productColumns"
          :remote-method="queryProductOptions"
          :expand-nonce="searchExpandNonce"
          placeholder="药名/拼音"
          @select="handleSearchSelect"
        />
      </div>
    </div>

    <div class="tcm-herb-workspace__list min-h-0 flex-1">
      <ElScrollbar class="tcm-herb-workspace__scroll h-full">
        <div class="tcm-herb-workspace__strips">
          <div class="tcm-herb-workspace__strips-head">
            <span class="tcm-herb-workspace__col-idx">#</span>
            <span class="tcm-herb-workspace__col-name">药名</span>
            <span class="tcm-herb-workspace__col-dose">剂量</span>
            <span class="tcm-herb-workspace__col-decoction">特殊煎法</span>
            <span class="tcm-herb-workspace__col-price">单价</span>
            <span class="tcm-herb-workspace__col-total">小计</span>
            <span class="tcm-herb-workspace__col-ops">操作</span>
          </div>

          <div v-if="displayLines.length === 0" class="tcm-herb-workspace__empty">
            {{ readonly ? '暂无药品' : '检索药名或拼音加药，或从左侧套用组套 / 历史' }}
          </div>

          <div v-for="line in displayLines" :key="line.clientLineKey" class="tcm-herb-strip">
            <span class="tcm-herb-strip__idx">
              {{ rowIndexes.get(line.clientLineKey) }}
            </span>
            <span class="tcm-herb-strip__name" :title="line.medicationName">
              {{ line.medicationName || '—' }}
            </span>

            <div
              class="tcm-herb-strip__dose"
              :data-dose-key="line.clientLineKey"
              @keydown.capture="handleRowDoseKeydown(line.clientLineKey, $event)"
            >
              <template v-if="readonly">
                <span class="tcm-herb-strip__dose-text">{{ doseReadonlyText(line) }}</span>
                <span class="tcm-herb-strip__unit">{{ doseUnitDisplay(line) }}</span>
              </template>
              <template v-else>
                <ElInputNumber
                  :ref="(el) => setDoseInputRef(line.clientLineKey, el)"
                  :model-value="doseEditValue(line)"
                  class="tcm-herb-strip__dose-input !w-[58px]"
                  size="small"
                  :min="0"
                  :precision="0"
                  :step="1"
                  :controls="false"
                  @update:model-value="handleRowDoseChange(line.clientLineKey, $event)"
                  @keydown.enter.prevent="handleRowDoseEnter(line.clientLineKey)"
                />
                <span class="tcm-herb-strip__unit">{{ doseUnitDisplay(line) }}</span>
              </template>
            </div>

            <div class="tcm-herb-strip__decoction">
              <template v-if="readonly">
                <span
                  class="tcm-herb-strip__decoction-tag"
                  :class="{ 'tcm-herb-strip__decoction-tag--special': isSpecialDecoction(line) }"
                >
                  {{ decoctionReadonlyText(line) }}
                </span>
              </template>
              <ElSelect
                v-else
                :model-value="line.dosageInstructionCode ?? ''"
                class="tcm-herb-strip__decoction-select"
                size="small"
                placeholder="正常"
                clearable
                @update:model-value="handleRowDosageChange(line.clientLineKey, $event)"
              >
                <ElOption
                  v-for="opt in dosageInstructionOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </ElSelect>
            </div>

            <span class="tcm-herb-strip__price">
              <span class="tcm-herb-strip__currency">¥</span>{{ unitPriceDisplayText(line) }}
            </span>
            <strong class="tcm-herb-strip__total">
              <span class="tcm-herb-strip__currency">¥</span>{{ lineTotalDisplayText(line) }}
            </strong>

            <div class="tcm-herb-strip__ops">
              <ElButton
                v-if="!readonly"
                link
                type="danger"
                @click="handleRemove(line.clientLineKey)"
              >
                删除
              </ElButton>
            </div>
          </div>
        </div>
      </ElScrollbar>
    </div>
  </div>
</template>
