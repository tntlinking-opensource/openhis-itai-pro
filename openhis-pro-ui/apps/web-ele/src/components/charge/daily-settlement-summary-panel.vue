<script setup lang="ts">
import type {
  DailySettlementExpenseNatureSummaryDto,
  DailySettlementInsuranceTypeSummaryDto,
} from '#/api/outpatient/daily-settlement';

import { computed, ref, watch } from 'vue';

import { ElButton } from 'element-plus';

defineOptions({ name: 'DailySettlementSummaryPanel' });

export interface DailySettlementSummaryPanelSource {
  totalIncome?: string;
  wechatAmount?: string;
  alipayAmount?: string;
  onlineBankAmount?: string;
  cashAmount?: string;
  cashDueAmount?: string;
  insuranceCompensationAmount?: string;
  depositAmount?: string;
  physicalExamCount?: number;
  expenseNatureSummaries?: DailySettlementExpenseNatureSummaryDto[];
}

interface MetricItem {
  key: string;
  label: string;
  value: string;
  emphasize?: boolean;
}

interface InsutypeBlock {
  key: string;
  title: string;
  personCountText: string;
  metrics: MetricItem[];
}

interface NatureBlock {
  key: string;
  title: string;
  personCountText: string;
  insurance: boolean;
  metrics: MetricItem[];
  types: InsutypeBlock[];
}

const props = defineProps<{
  summary?: DailySettlementSummaryPanelSource | null;
}>();

const UNKNOWN_CONTRACT_NAME = '未标注费用性质';
const UNKNOWN_INSUTYPE_NAME = '未分险种';

/** 医保费用性质下险种明细展开态（按 nature.key） */
const expandedNatureKeys = ref<Record<string, boolean>>({});

function formatMoney(value: string | undefined): string {
  const raw = (value ?? '').trim();
  if (!raw) {
    return '0.00 元';
  }
  return raw.includes('元') ? raw : `${raw} 元`;
}

function formatPersonCount(count: number | undefined): string {
  return `${count ?? 0} 人`;
}

function moneyMetrics(
  parts: Array<{ key: string; label: string; value?: string; emphasize?: boolean }>,
): MetricItem[] {
  return parts.map((part) => ({
    key: part.key,
    label: part.label,
    value: formatMoney(part.value),
    emphasize: part.emphasize,
  }));
}

/** 四列网格切行，与上半区财务分类同构 */
function chunkMetrics(items: MetricItem[]): MetricItem[][] {
  const rows: MetricItem[][] = [];
  for (let i = 0; i < items.length; i += 4) {
    rows.push(items.slice(i, i + 4));
  }
  return rows;
}

function buildInsutypeBlocks(
  natureKey: string,
  types: DailySettlementInsuranceTypeSummaryDto[] | undefined,
): InsutypeBlock[] {
  const blocks: InsutypeBlock[] = [];
  (types ?? []).forEach((type, typeIndex) => {
    if (!type) {
      return;
    }
    const title = type.insutypeName?.trim() || UNKNOWN_INSUTYPE_NAME;
    // 「未分险种」与费用性质汇总重复，不单独展示
    if (title === UNKNOWN_INSUTYPE_NAME) {
      return;
    }
    const typeKey = `${natureKey}-insu-${type.insutype ?? typeIndex}`;
    blocks.push({
      key: typeKey,
      title,
      personCountText: formatPersonCount(type.personCount),
      metrics: moneyMetrics([
        { key: `${typeKey}-cash`, label: '现金', value: type.cashAmount },
        { key: `${typeKey}-fund`, label: '统筹', value: type.fundAmount },
        { key: `${typeKey}-account`, label: '账户', value: type.accountAmount },
        {
          key: `${typeKey}-total`,
          label: '总额',
          value: type.totalAmount,
          emphasize: true,
        },
      ]),
    });
  });
  return blocks;
}

const channelMetrics = computed<MetricItem[]>(() => {
  const summary = props.summary;
  if (!summary) {
    return [];
  }
  const items = moneyMetrics([
    { key: 'totalIncome', label: '总收入', value: summary.totalIncome, emphasize: true },
    { key: 'wechatAmount', label: '微信', value: summary.wechatAmount },
    { key: 'alipayAmount', label: '支付宝', value: summary.alipayAmount },
    { key: 'onlineBankAmount', label: '银行卡', value: summary.onlineBankAmount },
    {
      key: 'cashDueAmount',
      label: '应交现金',
      value: summary.cashDueAmount ?? summary.cashAmount,
      emphasize: true,
    },
    {
      key: 'insuranceCompensationAmount',
      label: '医保补偿',
      value: summary.insuranceCompensationAmount,
      emphasize: true,
    },
  ]);
  if (summary.depositAmount !== undefined) {
    items.push({
      key: 'depositAmount',
      label: '预交金',
      value: formatMoney(summary.depositAmount),
    });
  }
  return items;
});

const natureBlocks = computed<NatureBlock[]>(() => {
  const natures = props.summary?.expenseNatureSummaries ?? [];
  const blocks: NatureBlock[] = [];
  natures.forEach((nature, index) => {
    if (!nature) {
      return;
    }
    const title = nature.contractName?.trim() || UNKNOWN_CONTRACT_NAME;
    const natureKey = `nature-${nature.contractNo ?? index}`;
    const insurance = Boolean(nature.insurance);
    const metrics = insurance
      ? moneyMetrics([
          { key: `${natureKey}-cash`, label: '现金', value: nature.cashAmount },
          { key: `${natureKey}-fund`, label: '统筹', value: nature.fundAmount },
          { key: `${natureKey}-account`, label: '账户', value: nature.accountAmount },
          {
            key: `${natureKey}-total`,
            label: '总额',
            value: nature.totalAmount,
            emphasize: true,
          },
        ])
      : moneyMetrics([
          {
            key: `${natureKey}-total`,
            label: '总额',
            value: nature.totalAmount,
            emphasize: true,
          },
        ]);
    blocks.push({
      key: natureKey,
      title,
      personCountText: formatPersonCount(nature.personCount),
      insurance,
      metrics,
      types: insurance ? buildInsutypeBlocks(natureKey, nature.insuranceTypeSummaries) : [],
    });
  });
  return blocks;
});

const channelMetricRows = computed(() => chunkMetrics(channelMetrics.value));

const hasContent = computed(() => channelMetrics.value.length > 0 || natureBlocks.value.length > 0);

/**
 * 省/市医保默认展开险种；工伤费用性质有数据时也默认展开。
 * 其余费用性质默认收起，仍可手动切换。
 */
function shouldExpandNatureTypesByDefault(block: NatureBlock): boolean {
  if (!block.types.length) {
    return false;
  }
  const title = block.title.trim();
  if (title.includes('省医保') || title.includes('市医保')) {
    return true;
  }
  if (title.includes('工伤')) {
    return true;
  }
  return false;
}

watch(
  natureBlocks,
  (blocks) => {
    const next: Record<string, boolean> = { ...expandedNatureKeys.value };
    for (const block of blocks) {
      if (!(block.key in next)) {
        next[block.key] = shouldExpandNatureTypesByDefault(block);
      }
    }
    expandedNatureKeys.value = next;
  },
  { immediate: true },
);

function isNatureExpanded(natureKey: string): boolean {
  return Boolean(expandedNatureKeys.value[natureKey]);
}

function toggleNatureTypes(natureKey: string) {
  expandedNatureKeys.value = {
    ...expandedNatureKeys.value,
    [natureKey]: !expandedNatureKeys.value[natureKey],
  };
}

defineExpose({ hasContent });
</script>

<template>
  <div v-if="hasContent" class="ds-summary flex flex-col gap-3">
    <div v-if="channelMetricRows.length" class="flex flex-col gap-3">
      <div
        v-for="(row, rowIndex) in channelMetricRows"
        :key="`channel-row-${rowIndex}`"
        class="grid grid-cols-4 gap-x-6 gap-y-2 text-sm"
      >
        <div
          v-for="item in row"
          :key="item.key"
          class="flex justify-between gap-2 text-[var(--el-text-color-primary)]"
        >
          <span class="text-[var(--el-text-color-regular)]">{{ item.label }}：</span>
          <span class="font-medium" :class="{ 'font-semibold': item.emphasize }">{{
            item.value
          }}</span>
        </div>
      </div>
    </div>

    <section
      v-for="block in natureBlocks"
      :key="block.key"
      class="border-t border-[var(--el-border-color-lighter)] pt-3"
    >
      <div class="mb-1.5 flex items-center justify-between gap-3 text-sm">
        <div class="flex min-w-0 items-baseline gap-2 text-[var(--el-text-color-primary)]">
          <span class="font-medium">{{ block.title }}</span>
          <span class="text-[var(--el-text-color-secondary)]">{{ block.personCountText }}</span>
        </div>
        <ElButton
          v-if="block.types.length"
          link
          type="primary"
          class="!h-auto shrink-0 !px-0 text-xs"
          @click="toggleNatureTypes(block.key)"
        >
          {{ isNatureExpanded(block.key) ? '收起' : '展开险种' }}
        </ElButton>
      </div>

      <div class="grid grid-cols-4 gap-x-6 gap-y-2 text-sm">
        <div
          v-for="item in block.metrics"
          :key="item.key"
          class="flex justify-between gap-2 text-[var(--el-text-color-primary)]"
        >
          <span class="text-[var(--el-text-color-regular)]">{{ item.label }}：</span>
          <span class="font-medium" :class="{ 'font-semibold': item.emphasize }">{{
            item.value
          }}</span>
        </div>
      </div>

      <div
        v-if="block.types.length && isNatureExpanded(block.key)"
        class="mt-2 space-y-2 rounded bg-[var(--el-fill-color-lighter)] px-3 py-2"
      >
        <div v-for="type in block.types" :key="type.key" class="space-y-1">
          <div class="flex items-baseline gap-2 text-xs text-[var(--el-text-color-regular)]">
            <span>{{ type.title }}</span>
            <span class="text-[var(--el-text-color-secondary)]">{{ type.personCountText }}</span>
          </div>
          <div class="grid grid-cols-4 gap-x-6 gap-y-1 text-sm">
            <div
              v-for="item in type.metrics"
              :key="item.key"
              class="flex justify-between gap-2 text-[var(--el-text-color-primary)]"
            >
              <span class="text-[var(--el-text-color-secondary)]">{{ item.label }}：</span>
              <span :class="item.emphasize ? 'font-medium' : ''">{{ item.value }}</span>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>
