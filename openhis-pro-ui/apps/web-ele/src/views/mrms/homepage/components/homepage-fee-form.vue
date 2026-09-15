<script setup lang="ts">
/**
 * 费用结算：对齐老 feeGrid；金额读首页费用快照只读，点「重新统计」才按当前病案结算项重算。
 */
import type { HomepageFeeItemKey, HomepageFormModel } from '../homepage-form.types';

import { computed } from 'vue';

import { ElFormItem, ElInputNumber, ElTable, ElTableColumn } from 'element-plus';

import { HOMEPAGE_FEE_ITEM_OPTIONS } from '../../mrms.constants';
import { HOMEPAGE_EMPTY_DISPLAY } from '../homepage.constants';

defineOptions({ name: 'HomepageFeeForm' });

interface Props {
  readonly?: boolean;
}

withDefaults(defineProps<Props>(), {
  /** 费用章只读：金额来自快照，不支持手改 */
  readonly: true,
});

const form = defineModel<HomepageFormModel>('modelValue', { required: true });

const emit = defineEmits<{
  dirty: [];
}>();

/** 费用行（大类 + 项目） */
interface FeeRow {
  key: HomepageFeeItemKey;
  category: string;
  label: string;
  /** 同大类连续行数；仅首行有值供合并 */
  categoryRowspan: number;
}

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

/**
 * 按大类生成行，并计算费用类型列 rowspan（对齐老 mergeGridColCells）。
 */
const feeRows = computed<FeeRow[]>(() => {
  const rows: FeeRow[] = [];
  let i = 0;
  const list = HOMEPAGE_FEE_ITEM_OPTIONS;
  while (i < list.length) {
    const start = list[i]!;
    let span = 1;
    // 统计同大类连续条数
    while (i + span < list.length && list[i + span]!.category === start.category) {
      span += 1;
    }
    for (let j = 0; j < span; j += 1) {
      const item = list[i + j]!;
      rows.push({
        key: item.key as HomepageFeeItemKey,
        category: item.category,
        label: item.label,
        categoryRowspan: j === 0 ? span : 0,
      });
    }
    i += span;
  }
  return rows;
});

/**
 * 费用类型列合并单元格。
 *
 * @param param ElTable span-method 入参
 */
function spanMethod(param: {
  row: FeeRow;
  column: { property?: string };
  rowIndex: number;
  columnIndex: number;
}): number[] | { rowspan: number; colspan: number } {
  // 仅合并「费用类型」列
  if (param.columnIndex === 0 || param.column.property === 'category') {
    const span = param.row.categoryRowspan;
    if (span > 0) {
      return { rowspan: span, colspan: 1 };
    }
    return { rowspan: 0, colspan: 0 };
  }
  return { rowspan: 1, colspan: 1 };
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
          :placeholder="HOMEPAGE_EMPTY_DISPLAY"
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
          :placeholder="HOMEPAGE_EMPTY_DISPLAY"
        />
      </ElFormItem>
    </div>

    <ElTable
      :data="feeRows"
      border
      size="small"
      class="hp-fee-form__table"
      :span-method="spanMethod"
    >
      <ElTableColumn prop="category" label="费用类型" width="160" align="center" />
      <ElTableColumn prop="label" label="费用项目" min-width="220" />
      <ElTableColumn label="金额（元）" width="180" align="right">
        <template #default="{ row }">
          <ElInputNumber
            :model-value="feeValue(row.key)"
            :min="0"
            :precision="2"
            :step="0.01"
            :controls="false"
            :disabled="readonly"
            class="w-full"
            @update:model-value="(v) => setFeeValue(row.key, v ?? undefined)"
            :placeholder="HOMEPAGE_EMPTY_DISPLAY"
          />
        </template>
      </ElTableColumn>
    </ElTable>
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

.hp-fee-form__table {
  width: 100%;
}

@media (max-width: 1100px) {
  .hp-fee-form__summary {
    grid-template-columns: 1fr;
  }
}
</style>
