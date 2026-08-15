<script setup lang="ts">
import type { HomepageFeeItemKey, HomepageFormModel } from '../homepage-form.types';

import { ElFormItem, ElInputNumber } from 'element-plus';

import { HOMEPAGE_FEE_ITEM_OPTIONS } from '../../mrms.constants';

defineOptions({ name: 'HomepageFeeForm' });

interface Props {
  readonly?: boolean;
}

withDefaults(defineProps<Props>(), {
  readonly: false,
});

const form = defineModel<HomepageFormModel>('modelValue', { required: true });

const emit = defineEmits<{
  dirty: [];
}>();

/** 标脏 */
function markDirty() {
  emit('dirty');
}

/** 读取分项金额 */
function feeValue(key: HomepageFeeItemKey): number | undefined {
  return form.value[key];
}

/** 写入分项金额 */
function setFeeValue(key: HomepageFeeItemKey, value: number | undefined) {
  form.value[key] = value;
  markDirty();
}
</script>

<template>
  <div class="hp-fee-form">
    <div class="hp-fee-form__summary">
      <ElFormItem label="总费用" class="hp-fee-form__sum-item" label-width="88px">
        <ElInputNumber
          v-model="form.medfee_sumamt"
          :min="0"
          :precision="2"
          :step="0.01"
          :controls="false"
          :disabled="readonly"
          class="w-full"
          @change="markDirty"
        />
      </ElFormItem>
      <ElFormItem label="自付金额" class="hp-fee-form__sum-item" label-width="88px">
        <ElInputNumber
          v-model="form.selfpay_amt"
          :min="0"
          :precision="2"
          :step="0.01"
          :controls="false"
          :disabled="readonly"
          class="w-full"
          @change="markDirty"
        />
      </ElFormItem>
    </div>

    <div class="hp-fee-form__grid">
      <ElFormItem
        v-for="item in HOMEPAGE_FEE_ITEM_OPTIONS"
        :key="item.key"
        :label="item.label"
        class="hp-fee-form__item"
        label-width="160px"
      >
        <ElInputNumber
          :model-value="feeValue(item.key as HomepageFeeItemKey)"
          :min="0"
          :precision="2"
          :step="0.01"
          :controls="false"
          :disabled="readonly"
          class="w-full"
          @update:model-value="(v) => setFeeValue(item.key as HomepageFeeItemKey, v ?? undefined)"
        />
      </ElFormItem>
    </div>

    <p class="hp-fee-form__tip">ICU、输血子表本期占位，上传阶段再补齐。</p>
  </div>
</template>

<style scoped>
.hp-fee-form {
  padding: 8px 12px 4px;
  background: var(--el-bg-color);
  border-radius: 4px;
}

.hp-fee-form__summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(220px, 320px));
  gap: 8px 16px;
  margin-bottom: 8px;
}

.hp-fee-form__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(280px, 1fr));
  gap: 4px 12px;
}

.hp-fee-form__tip {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--el-text-color-regular);
  line-height: 1.5;
}

@media (max-width: 1100px) {
  .hp-fee-form__summary,
  .hp-fee-form__grid {
    grid-template-columns: 1fr;
  }
}
</style>
