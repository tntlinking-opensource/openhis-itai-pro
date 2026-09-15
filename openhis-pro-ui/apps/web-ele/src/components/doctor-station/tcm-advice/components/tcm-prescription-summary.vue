<script setup lang="ts">
import { formatMoneyDisplay } from '#/utils/format/money-scale';

import './tcm-prescription-summary.css';

defineOptions({ name: 'TcmPrescriptionSummary' });

interface Props {
  chineseHerbsDoseQuantity?: number;
  /** 代煎费合计（勾选代煎时，单价×付数） */
  decoctionFeeAmount?: number;
  /** 代煎项目名称 */
  decoctionFeeLabel?: string;
  herbCount: number;
  methodLabel?: string;
  /** 是否勾选代煎（即使暂无金额也展示） */
  showDecoctionFee?: boolean;
  /** 已签发未代煎：可补充代煎费 */
  showSupplementDecoctionAction?: boolean;
  /** 已签发方可单独取消代煎费 */
  showVoidDecoctionAction?: boolean;
  singleDoseAmount: number;
  /** 补充代煎费请求中 */
  supplementDecoctionLoading?: boolean;
  totalAmount: number;
  /** 取消代煎费请求中 */
  voidDecoctionLoading?: boolean;
}

withDefaults(defineProps<Props>(), {
  decoctionFeeAmount: 0,
  decoctionFeeLabel: '代煎中药',
  methodLabel: '',
  showDecoctionFee: false,
  showSupplementDecoctionAction: false,
  showVoidDecoctionAction: false,
  supplementDecoctionLoading: false,
  voidDecoctionLoading: false,
});

const emit = defineEmits<{
  'supplement-decoction': [];
  'void-decoction': [];
}>();
</script>

<template>
  <!-- 左摘要标签 + 右合计；布局走外挂 css -->
  <div class="tcm-prescription-summary">
    <div class="tcm-prescription-summary__meta">
      <span class="tcm-prescription-summary__count">
        共 <span class="tcm-prescription-summary__count-num">{{ herbCount }}</span> 味
      </span>
      <template v-if="methodLabel">
        <span class="tcm-prescription-summary__sep">·</span>
        <span>{{ methodLabel }}</span>
      </template>
      <span class="tcm-prescription-summary__sep">·</span>
      <span>单剂 {{ formatMoneyDisplay(singleDoseAmount) }} 元</span>
      <template v-if="showSupplementDecoctionAction">
        <span class="tcm-prescription-summary__sep">·</span>
        <button
          type="button"
          class="tcm-prescription-summary__supplement-decoction"
          :disabled="supplementDecoctionLoading"
          :aria-busy="supplementDecoctionLoading ? 'true' : undefined"
          @click="emit('supplement-decoction')"
        >
          {{ supplementDecoctionLoading ? '补充中…' : '补充代煎费' }}
        </button>
      </template>
      <template v-if="showDecoctionFee">
        <span class="tcm-prescription-summary__sep">·</span>
        <span class="tcm-prescription-summary__decoction">
          {{ decoctionFeeLabel }}
          <strong
            v-if="decoctionFeeAmount != null && decoctionFeeAmount > 0"
            class="tcm-prescription-summary__decoction-amt"
          >
            ¥{{ formatMoneyDisplay(decoctionFeeAmount) }}
          </strong>
          <span v-else class="tcm-prescription-summary__decoction-hint">（请维护付数/价格）</span>
          <button
            v-if="showVoidDecoctionAction"
            type="button"
            class="tcm-prescription-summary__void-decoction"
            :disabled="voidDecoctionLoading"
            :aria-busy="voidDecoctionLoading ? 'true' : undefined"
            @click="emit('void-decoction')"
          >
            {{ voidDecoctionLoading ? '取消中…' : '取消代煎' }}
          </button>
        </span>
      </template>
    </div>
    <div class="tcm-prescription-summary__amount">
      <template v-if="chineseHerbsDoseQuantity != null && chineseHerbsDoseQuantity > 0">
        <span class="tcm-prescription-summary__currency">¥</span>
        <strong class="tcm-prescription-summary__total">
          {{ formatMoneyDisplay(totalAmount) }}
        </strong>
        <span class="tcm-prescription-summary__amount-label">
          {{ chineseHerbsDoseQuantity }}付合计
          <template v-if="showDecoctionFee && decoctionFeeAmount != null && decoctionFeeAmount > 0">
            （含代煎）
          </template>
        </span>
      </template>
      <span v-else class="tcm-prescription-summary__hint">请先填写付数</span>
    </div>
  </div>
</template>
