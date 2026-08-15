<script setup lang="ts">
import type {
  AdviceProductOption,
  ProductColumn,
  ProductQueryParams,
} from '#/views/outpatient/doctor-station/advice';

import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';

import { Delete } from '@element-plus/icons-vue';
import { ElIcon, ElInputNumber, ElOption, ElSelect } from 'element-plus';

import { decimalMul } from '#/utils/format/decimal-util';
import { calcMoneyLineTotal, formatMoneyDisplay } from '#/utils/format/money-scale';
import { AdviceOrderSelect } from '#/views/outpatient/doctor-station/advice';

defineOptions({ name: 'TcmHerbCard' });

const props = withDefaults(defineProps<Props>(), {
  autofocusProduct: false,
  blurIgnoreSelectors: () => [],
  dosageInstructionOptions: () => [],
  editing: false,
  readonly: false,
  selected: false,
});

const emit = defineEmits<{
  autofocusProductConsumed: [];
  editEnd: [];
  editStart: [];
  /** 剂量框上下键：切换到相邻行剂量焦点（禁用数字增减） */
  navigateDoseFocus: [direction: 'down' | 'up'];
  productSelect: [product: AdviceProductOption];
  remove: [];
  select: [];
  updateDose: [value: number];
  updateDosageInstruction: [value: string];
}>();

export interface TcmHerbCardItem {
  clientLineKey: string;
  dose: number;
  doseUnitCode: string;
  doseUnitName?: string;
  medicationDefinitionId: string;
  medicationName: string;
  retailPrice: number;
  dosageInstructionCode?: string;
  dosageInstructionText?: string;
  /** 医保等级展示名（目录带出） */
  chrgitmLvText?: string;
  /** 行级费用性质展示文案 */
  feeNatureText?: string;
  /** 行级费用态码（ChargeItemStatus） */
  chargeStatusEnum?: string;
  /** 行级费用态展示文案 */
  chargeStatusText?: string;
}

interface DosageInstructionOption {
  label: string;
  value: string;
}

interface Props {
  autofocusProduct?: boolean;
  /** 点击这些区域时不退出编辑（如组套维护页模板属性区） */
  blurIgnoreSelectors?: string[];
  chineseHerbsDoseQuantity?: number;
  /** 特殊煎法下拉（dosage_instruction） */
  dosageInstructionOptions?: DosageInstructionOption[];
  editing?: boolean;
  item: TcmHerbCardItem;
  productColumns: ProductColumn[];
  queryProductOptions: (params: ProductQueryParams) => Promise<{
    content: AdviceProductOption[];
    totalElements: number;
  }>;
  readonly?: boolean;
  selected?: boolean;
}

const cardRef = ref<HTMLElement | null>(null);
const doseInputRef = ref<{ focus?: () => void; $el?: HTMLElement } | null>(null);
const expandNonce = ref(0);
/** 用于区分单击选中与双击编辑 */
let clickSelectTimer: null | ReturnType<typeof setTimeout> = null;

/** 单剂用量单位展示名 */
const doseUnitDisplay = computed(() => {
  const name = String(props.item.doseUnitName ?? '').trim();
  if (name) return name;
  return (props.item.doseUnitCode || 'g').trim() || 'g';
});

/** 格式化用量数字（克数仅整数） */
function formatDoseQty(value: number): string {
  return String(Math.round(value));
}

const perDoseQtyText = computed(() => {
  const dose = Number(props.item.dose || 0);
  return `${formatDoseQty(dose)}${doseUnitDisplay.value}`;
});

/** 当前付数（未填时不按 1 推算） */
function resolveDoseCount(): number | undefined {
  const doseCount = props.chineseHerbsDoseQuantity;
  if (doseCount == null || doseCount <= 0) {
    return undefined;
  }
  return doseCount;
}

/** 单剂 × 付数 = 请求总量 */
const totalQtyText = computed(() => {
  const dose = Number(props.item.dose || 0);
  const doseCount = resolveDoseCount();
  if (doseCount == null) {
    return perDoseQtyText.value;
  }
  const total = decimalMul(dose, doseCount);
  return `${formatDoseQty(total)}${doseUnitDisplay.value}`;
});

const doseDisplayText = computed(() => {
  const doseCount = resolveDoseCount();
  if (doseCount == null || doseCount <= 1) {
    return perDoseQtyText.value;
  }
  return `${perDoseQtyText.value}/付 · 共${totalQtyText.value}`;
});

/** 收起态特殊煎法展示 */
const decoctionDisplayText = computed(() => String(props.item.dosageInstructionText ?? '').trim());

/** 行内修改特殊煎法 */
function handleDosageInstructionChange(value: string | undefined) {
  emit('updateDosageInstruction', String(value ?? ''));
}

/** 单剂行金额（6 位） */
const lineTotalAmount = computed(() =>
  calcMoneyLineTotal(props.item.retailPrice, props.item.dose || 0),
);

/** 付数合计金额（6 位） */
const linePrescriptionTotalAmount = computed(() => {
  const doseCount = resolveDoseCount();
  if (doseCount == null) {
    return lineTotalAmount.value;
  }
  const totalQty = Number(props.item.dose || 0) * doseCount;
  return calcMoneyLineTotal(props.item.retailPrice, totalQty);
});

/** 单价展示文案 */
const unitPriceDisplayText = computed(
  () => `¥${formatMoneyDisplay(props.item.retailPrice || 0)}/${doseUnitDisplay.value}`,
);

/** 本行单剂小计展示文案 */
const lineTotalDisplayText = computed(() => {
  const doseCount = resolveDoseCount();
  if (doseCount == null || doseCount <= 1) {
    return `合计 ¥${formatMoneyDisplay(lineTotalAmount.value)}`;
  }
  return `单剂 ¥${formatMoneyDisplay(lineTotalAmount.value)} · ${doseCount}付 ¥${formatMoneyDisplay(linePrescriptionTotalAmount.value)}`;
});

/** 编辑态剂量：0 显示为空，避免预填默认值 */
const doseEditValue = computed(() => {
  const dose = Number(props.item.dose || 0);
  return dose > 0 ? Math.round(dose) : undefined;
});

/** 单击：延迟触发选中，避免与双击编辑冲突 */
function handleCardClick() {
  if (props.readonly || props.editing) return;
  if (clickSelectTimer) {
    clearTimeout(clickSelectTimer);
  }
  clickSelectTimer = setTimeout(() => {
    emit('select');
    clickSelectTimer = null;
  }, 220);
}

/** 双击：进入编辑态 */
function handleCardDblClick() {
  if (props.readonly) return;
  if (clickSelectTimer) {
    clearTimeout(clickSelectTimer);
    clickSelectTimer = null;
  }
  emit('editStart');
}

/** 删除药材行 */
function handleRemove(event: MouseEvent) {
  event.stopPropagation();
  emit('remove');
}

/** 下拉选药回写后聚焦剂量框 */
function handleProductSelect(product: AdviceProductOption) {
  emit('productSelect', product);
  void nextTick(() => {
    requestAnimationFrame(() => {
      focusDoseInput();
    });
  });
}

/** 聚焦剂量输入并选中，便于直接改数 */
function focusDoseInput() {
  const inst = doseInputRef.value;
  if (!inst) return;
  inst.focus?.();
  const input = inst.$el?.querySelector?.('input') as HTMLInputElement | null | undefined;
  if (input) {
    input.focus();
    input.select();
  }
}

/**
 * 剂量框键盘：禁用上下键增减数值，改为通知父级切换相邻行剂量焦点。
 * 使用 capture，确保先于 ElInputNumber 内部增减逻辑处理。
 */
function handleDoseKeydown(event: KeyboardEvent) {
  if (event.key !== 'ArrowUp' && event.key !== 'ArrowDown') return;
  event.preventDefault();
  event.stopPropagation();
  emit('navigateDoseFocus', event.key === 'ArrowUp' ? 'up' : 'down');
}

/** 行内修改单次剂量 */
function handleDoseChange(value: number | undefined) {
  // 克数仅整数
  emit('updateDose', Math.round(Number(value ?? 0)));
}

defineExpose({ focusDoseInput });

/** 判断节点是否落在卡片或浮层选药/下拉内 */
function isInsideEditableSurface(target: EventTarget | null) {
  if (!(target instanceof Node)) return false;
  if (cardRef.value?.contains(target)) return true;
  if (!(target instanceof Element)) return false;
  for (const selector of props.blurIgnoreSelectors) {
    if (selector && target.closest(selector)) {
      return true;
    }
  }
  return Boolean(
    target.closest('.advice-order-select-dropdown') ||
      target.closest('.advice-order-select-trigger-host') ||
      target.closest('.vxe-pulldown--panel') ||
      target.closest('.vxe-pulldown--wrapper') ||
      target.closest('.el-select-dropdown') ||
      target.closest('.el-popper'),
  );
}

/** 编辑态失焦：点击卡片外且非下拉浮层时退出编辑 */
function handleDocumentMouseDown(event: MouseEvent) {
  if (!props.editing) return;
  if (isInsideEditableSurface(event.target)) return;
  emit('editEnd');
}

let documentListenerBound = false;

function bindDocumentListener() {
  if (documentListenerBound) return;
  document.addEventListener('mousedown', handleDocumentMouseDown, true);
  documentListenerBound = true;
}

function unbindDocumentListener() {
  if (!documentListenerBound) return;
  document.removeEventListener('mousedown', handleDocumentMouseDown, true);
  documentListenerBound = false;
}

/** 新增药材编辑卡：自动展开选药下拉 */
async function tryExpandProductSelect() {
  if (!props.editing || !props.autofocusProduct) return;
  await nextTick();
  expandNonce.value += 1;
  emit('autofocusProductConsumed');
}

watch(
  () => props.editing,
  (editing) => {
    if (editing) {
      bindDocumentListener();
      void tryExpandProductSelect();
      return;
    }
    unbindDocumentListener();
  },
  { immediate: true },
);

watch(
  () => props.autofocusProduct,
  (shouldFocus) => {
    if (shouldFocus && props.editing) {
      void tryExpandProductSelect();
    }
  },
);

onBeforeUnmount(() => {
  if (clickSelectTimer) {
    clearTimeout(clickSelectTimer);
  }
  unbindDocumentListener();
});
</script>

<template>
  <div
    ref="cardRef"
    class="tcm-herb-card"
    :class="{
      'tcm-herb-card--selected': selected && !editing && !readonly,
      'tcm-herb-card--editing': editing && !readonly,
      'tcm-herb-card--readonly': readonly,
    }"
    @click="handleCardClick"
    @dblclick="handleCardDblClick"
  >
    <template v-if="editing && !readonly">
      <div class="tcm-herb-card__panel">
        <div class="tcm-herb-card__product-field" @click.stop>
          <AdviceOrderSelect
            :model-value="item.medicationDefinitionId"
            :display-label="item.medicationName"
            :columns="productColumns"
            :remote-method="queryProductOptions"
            :expand-nonce="expandNonce"
            class="tcm-herb-card__product-select"
            placeholder="检索药材"
            @select="handleProductSelect"
          />
        </div>

        <div class="tcm-herb-card__row tcm-herb-card__row--meta" @click.stop>
          <div class="tcm-herb-card__dose-edit" @keydown.capture="handleDoseKeydown">
            <ElInputNumber
              ref="doseInputRef"
              :model-value="doseEditValue"
              class="tcm-herb-card__dose-input"
              :min="0"
              :precision="0"
              :step="1"
              :controls="false"
              @update:model-value="handleDoseChange"
            />
            <span class="tcm-herb-card__dose-unit">{{ doseUnitDisplay }}</span>
          </div>
          <ElSelect
            :model-value="item.dosageInstructionCode ?? ''"
            class="tcm-herb-card__decoction-select"
            placeholder="煎法"
            clearable
            @update:model-value="handleDosageInstructionChange"
          >
            <ElOption
              v-for="opt in dosageInstructionOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </ElSelect>
          <button
            type="button"
            class="tcm-herb-card__remove-btn"
            aria-label="删除药材"
            @click.stop="handleRemove"
          >
            <ElIcon :size="14">
              <Delete />
            </ElIcon>
          </button>
        </div>

        <div class="tcm-herb-card__divider"></div>

        <div class="tcm-herb-card__row tcm-herb-card__row--footer">
          <div class="tcm-herb-card__price-group">
            <span class="tcm-herb-card__unit-price">{{ unitPriceDisplayText }}</span>
            <span v-if="item.chrgitmLvText" class="tcm-herb-card__yb-level">
              医保等级：{{ item.chrgitmLvText }}
            </span>
            <span class="tcm-herb-card__total-price">{{ lineTotalDisplayText }}</span>
          </div>
        </div>
      </div>
    </template>

    <template v-else>
      <div class="tcm-herb-card__panel">
        <div class="tcm-herb-card__row tcm-herb-card__row--head">
          <div class="tcm-herb-card__name-line">
            <span class="tcm-herb-card__name">{{ item.medicationName || '未选药材' }}</span>
            <span v-if="decoctionDisplayText" class="tcm-herb-card__decoction-text">
              · {{ decoctionDisplayText }}
            </span>
          </div>
          <div class="tcm-herb-card__head-actions">
            <span class="tcm-herb-card__dose-text">{{ doseDisplayText }}</span>
            <button
              v-if="!readonly"
              type="button"
              class="tcm-herb-card__remove-btn tcm-herb-card__remove-btn--hoverable"
              aria-label="删除药材"
              @click.stop="handleRemove"
            >
              <ElIcon :size="14">
                <Delete />
              </ElIcon>
            </button>
          </div>
        </div>

        <div class="tcm-herb-card__divider"></div>

        <div class="tcm-herb-card__row tcm-herb-card__row--footer">
          <div class="tcm-herb-card__price-group">
            <span class="tcm-herb-card__unit-price">{{ unitPriceDisplayText }}</span>
            <span v-if="item.chrgitmLvText" class="tcm-herb-card__yb-level">
              医保等级：{{ item.chrgitmLvText }}
            </span>
            <span class="tcm-herb-card__total-price">{{ lineTotalDisplayText }}</span>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.tcm-herb-card {
  position: relative;
  display: flex;
  flex-direction: column;
  width: 100%;
  min-height: 108px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  background: var(--el-fill-color-blank);
  cursor: pointer;
  box-sizing: border-box;
  transition:
    border-color 0.2s ease,
    background-color 0.2s ease,
    box-shadow 0.2s ease;
}

.tcm-herb-card:hover:not(.tcm-herb-card--editing):not(.tcm-herb-card--readonly) {
  border-color: var(--el-border-color);
  box-shadow: 0 2px 8px rgb(15 23 42 / 6%);
}

.tcm-herb-card--selected {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.tcm-herb-card--editing {
  border-color: var(--el-color-primary);
  background: var(--el-fill-color-blank);
  box-shadow: 0 0 0 1px var(--el-color-primary-light-8);
  cursor: default;
  min-height: 132px;
}

.tcm-herb-card--readonly {
  background: var(--el-fill-color-light);
  cursor: default;
  box-shadow: none;
}

.tcm-herb-card__head-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.tcm-herb-card__remove-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  margin: 0;
  padding: 0;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--el-color-danger);
  cursor: pointer;
  flex-shrink: 0;
  transition:
    opacity 0.15s ease,
    background-color 0.15s ease,
    color 0.15s ease;
}

.tcm-herb-card__remove-btn--hoverable {
  opacity: 0;
  pointer-events: none;
}

.tcm-herb-card:hover:not(.tcm-herb-card--readonly) .tcm-herb-card__remove-btn--hoverable,
.tcm-herb-card__remove-btn--hoverable:focus-visible {
  opacity: 1;
  pointer-events: auto;
}

.tcm-herb-card__remove-btn:hover {
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}

.tcm-herb-card__panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  padding: 12px 16px 8px;
  box-sizing: border-box;
}

.tcm-herb-card__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.tcm-herb-card__row--head {
  flex: 1;
  min-height: 32px;
}

.tcm-herb-card__row--meta {
  margin-top: 8px;
  gap: 6px;
}

.tcm-herb-card__decoction-select {
  flex: 1;
  min-width: 0;
}

.tcm-herb-card__decoction-select :deep(.el-select__wrapper) {
  min-height: 30px;
  padding: 0 8px;
  border-radius: 6px;
  box-shadow: 0 0 0 1px var(--el-border-color) inset;
}

.tcm-herb-card__decoction-select :deep(.el-select__wrapper.is-focused) {
  box-shadow: 0 0 0 1px var(--el-color-primary) inset;
}

.tcm-herb-card__decoction-select :deep(.el-select__placeholder),
.tcm-herb-card__decoction-select :deep(.el-select__selected-item) {
  font-size: 13px;
}

.tcm-herb-card__product-field {
  width: 100%;
  max-width: 100%;
  margin-bottom: 8px;
}

.tcm-herb-card__product-select {
  width: 100%;
  max-width: 100%;
}

.tcm-herb-card__product-select :deep(.advice-order-select-trigger-host) {
  width: 100% !important;
  max-width: 100%;
}

.tcm-herb-card__row--footer {
  flex-shrink: 0;
  height: 30px;
}

.tcm-herb-card__divider {
  flex-shrink: 0;
  height: 1px;
  margin: 0 0 6px;
  background: var(--el-border-color-extra-light);
}

.tcm-herb-card__name-line {
  min-width: 0;
  overflow: hidden;
  flex: 1;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tcm-herb-card__name {
  color: var(--el-text-color-primary);
  font-size: 16px;
  font-weight: 600;
  line-height: 24px;
}

.tcm-herb-card__decoction-text {
  color: var(--el-text-color-regular);
  font-size: 14px;
  font-weight: 400;
  line-height: 24px;
}

.tcm-herb-card__dose-text {
  flex-shrink: 0;
  max-width: 100%;
  overflow: hidden;
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  line-height: 20px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tcm-herb-card__price-group {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  height: 100%;
  flex-shrink: 0;
  margin-left: auto;
  width: 100%;
}

.tcm-herb-card__unit-price {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  line-height: 20px;
  white-space: nowrap;
}

.tcm-herb-card__yb-level {
  color: var(--el-text-color-regular);
  font-size: 12px;
  line-height: 20px;
  white-space: nowrap;
}

.tcm-herb-card__total-price {
  color: var(--el-text-color-primary);
  font-size: 13px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  line-height: 20px;
  white-space: nowrap;
}

.tcm-herb-card__dose-edit {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.tcm-herb-card__dose-input {
  width: 64px;
}

.tcm-herb-card__dose-input :deep(.el-input__wrapper) {
  min-height: 30px;
  padding: 0 10px;
  border-radius: 6px;
  box-shadow: 0 0 0 1px var(--el-border-color) inset;
}

.tcm-herb-card__dose-input :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--el-color-primary) inset;
}

.tcm-herb-card__dose-input :deep(.el-input__inner) {
  text-align: center;
  font-weight: 600;
}

.tcm-herb-card__dose-unit {
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
}
</style>
