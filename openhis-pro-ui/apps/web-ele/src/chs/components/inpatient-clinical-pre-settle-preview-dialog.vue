<script setup lang="ts">
import { computed } from 'vue';

import { ElButton, ElDescriptions, ElDescriptionsItem, ElTag } from 'element-plus';

import AppDialog from '#/components/app-dialog.vue';
import { formatMoneyDisplay, formatMoneyYuan } from '#/utils/format/money-scale';

interface InsuranceBreakdownItem {
  key: string;
  label: string;
  value: number | string;
}

defineOptions({
  name: 'InpatientClinicalPreSettlePreviewDialog',
});

const props = withDefaults(
  defineProps<{
    displayAmount?: number | string;
    insuranceBreakdownItems?: InsuranceBreakdownItem[];
    insurancePreSettled?: boolean;
    previewText?: string;
    receivableTotal?: number | string;
  }>(),
  {
    displayAmount: 0,
    insuranceBreakdownItems: () => [],
    insurancePreSettled: false,
    previewText: '',
    receivableTotal: 0,
  },
);

const visible = defineModel<boolean>({ required: true });

/** 金额两位小数。 */
function formatMoney(value: null | number | string | undefined) {
  const n = Number(value ?? 0);
  return Number.isFinite(n) ? formatMoneyDisplay(n) : '0.00';
}

const payableDisplay = computed(() => {
  if (props.insurancePreSettled && props.displayAmount != null) {
    return formatMoney(props.displayAmount);
  }
  return formatMoney(props.receivableTotal);
});

const formattedPreviewText = computed(() => {
  const text = props.previewText?.trim() ?? '';
  if (!text) {
    return '';
  }
  if (text.startsWith('预交金冲抵')) {
    return `预交金冲抵，应收合计：${formatMoneyYuan(props.receivableTotal)}`;
  }
  if (text.startsWith('患者实付')) {
    return `患者实付：${formatMoneyYuan(props.displayAmount)}`;
  }
  if (text.startsWith('应收合计')) {
    return `应收合计：${formatMoneyYuan(props.receivableTotal)}`;
  }
  return text;
});
</script>

<template>
  <AppDialog v-model="visible" title="住院完整预结算" width="640px" destroy-on-close>
    <div class="clinical-pre-settle-body">
      <div class="clinical-pre-settle-tags">
        <ElTag type="warning" effect="plain">仅预览 · 不落账</ElTag>
        <ElTag v-if="insurancePreSettled" type="success" effect="plain">医保预结算（2303）</ElTag>
      </div>
      <p v-if="formattedPreviewText" class="text-sm text-[var(--el-text-color-regular)]">
        {{ formattedPreviewText }}
      </p>
      <div class="clinical-pre-settle-amount">
        <span class="text-[var(--el-text-color-regular)]">患者实付</span>
        <strong class="text-[var(--el-color-primary)]">¥{{ payableDisplay }}</strong>
        <span v-if="insurancePreSettled" class="text-xs text-[var(--el-text-color-secondary)]">
          原价合计 ¥{{ formatMoney(receivableTotal) }}
        </span>
      </div>
      <div v-if="insuranceBreakdownItems.length > 0" class="clinical-pre-settle-section">
        <div class="clinical-pre-settle-section__title">医保分项</div>
        <ElDescriptions :column="2" border size="small">
          <ElDescriptionsItem
            v-for="item in insuranceBreakdownItems"
            :key="item.key"
            :label="item.label"
          >
            ¥{{ formatMoney(item.value) }}
          </ElDescriptionsItem>
        </ElDescriptions>
      </div>
      <p class="text-xs text-[var(--el-text-color-secondary)]">
        正式收费请到住院收费处确认结算。关闭本窗不影响收费处再次预结算。
      </p>
    </div>
    <template #footer>
      <ElButton type="primary" @click="visible = false">关闭</ElButton>
    </template>
  </AppDialog>
</template>

<style scoped>
.clinical-pre-settle-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.clinical-pre-settle-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.clinical-pre-settle-amount {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 10px;
  font-size: 16px;
}

.clinical-pre-settle-amount strong {
  font-size: 28px;
  font-weight: 700;
}

.clinical-pre-settle-section__title {
  margin-bottom: 8px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>
