<script setup lang="ts">
import type {
  DailySettlementExpenseNatureSummaryDto,
  DailySettlementInsuranceTypeSummaryDto,
  DailySettlementSummaryRowDto,
  DailySettlementWorkInjurySummaryDto,
} from '#/api/outpatient/daily-settlement';

import { computed } from 'vue';

import { ElTable, ElTableColumn } from 'element-plus';

import { isWorkInjuryUiEnabled } from '#/utils/project-optional-modules';

defineOptions({ name: 'DailySettlementSummaryPanel' });

const props = withDefaults(
  defineProps<{
    summary?: DailySettlementSummaryPanelSource | null;
    /** 为 true 时不展示医保上传差额（住院患者分项口径） */
    omitRetailUploadDiff?: boolean;
  }>(),
  {
    summary: null,
    omitRetailUploadDiff: false,
  },
);

export interface DailySettlementSummaryPanelSource {
  totalIncome?: string;
  wechatAmount?: string;
  alipayAmount?: string;
  onlineBankAmount?: string;
  cashAmount?: string;
  cashDueAmount?: string;
  insuranceCompensationAmount?: string;
  /** 个人账户合计（各医保费用性质个人账户代数和） */
  insuranceAccountAmount?: string;
  /** 医保上传差额（零售−上传；勾稽项，不入总收入） */
  chsRetailUploadDiffAmount?: string;
  depositAmount?: string;
  /** 退预交金合计（多退；住院） */
  depositRefundAmount?: string;
  /** 结算补收合计（结算渠道实付；非补预交金；住院） */
  settleSupplementAmount?: string;
  physicalExamCount?: number;
  expenseNatureSummaries?: DailySettlementExpenseNatureSummaryDto[];
  /** 工伤独立统计（不入总合计；院点启用工伤且有流水时展示） */
  workInjurySummary?: DailySettlementWorkInjurySummaryDto | null;
  /** 后端扁平行；字段缺省时回落取「医保上传差额」 */
  summaryRows?: DailySettlementSummaryRowDto[];
}

interface MetricItem {
  key: string;
  label: string;
  value: string;
  emphasize?: boolean;
}

/** 费用性质明细表行（与打印 natureRows 同构） */
interface NatureListRow {
  key: string;
  rowType: 'nature' | 'type';
  name: string;
  personCount: string;
  cashAmount: string;
  fundAmount: string;
  accountAmount: string;
  totalAmount: string;
}

const UNKNOWN_CONTRACT_NAME = '未标注费用性质';
const UNKNOWN_INSUTYPE_NAME = '未分险种';
const TYPE_NAME_INDENT = '　';

/** 按费用性质名称关键字取总额（渠道区市/省医保合计、自费合计） */
function findNatureTotalAmount(
  natures: DailySettlementExpenseNatureSummaryDto[] | undefined,
  keyword: string,
): string | undefined {
  const hit = (natures ?? []).find((nature) => (nature?.contractName ?? '').includes(keyword));
  return hit?.totalAmount;
}

/** 从 summaryRows 取扁平金额（兼容旧后端只落 summaryRows 的场景） */
function findSummaryRowValue(
  rows: DailySettlementSummaryRowDto[] | undefined,
  key: string,
): string | undefined {
  const hit = (rows ?? []).find((row) => (row?.key ?? '').trim() === key);
  const value = hit?.value?.trim();
  return value || undefined;
}

/** 解析医保上传差额：专用字段 → summaryRows → 0.00 */
function resolveRetailUploadDiffAmount(summary: DailySettlementSummaryPanelSource): string {
  const direct = summary.chsRetailUploadDiffAmount?.trim();
  if (direct) {
    return direct;
  }
  return findSummaryRowValue(summary.summaryRows, 'chsRetailUploadDiffAmount') ?? '0.00';
}

/** 金额是否为 0（空、0、0.00、0 元） */
function isZeroMoney(value: string | undefined): boolean {
  const raw = (value ?? '')
    .trim()
    .replace(/\s*元$/, '')
    .replace(/,/g, '');
  if (!raw) {
    return true;
  }
  const num = Number(raw);
  return Number.isFinite(num) && num === 0;
}

/** 渠道区金额：带「元」 */
function formatMoney(value: string | undefined): string {
  const raw = (value ?? '').trim();
  if (!raw) {
    return '0.00 元';
  }
  return raw.includes('元') ? raw : `${raw} 元`;
}

/** 明细表金额：与打印一致，不加「元」（单位在表头） */
function formatTableAmount(value: string | undefined, blankIfEmpty = false): string {
  const raw = (value ?? '').trim().replace(/\s*元$/, '');
  if (!raw) {
    return blankIfEmpty ? '' : '0.00';
  }
  return raw;
}

function formatPersonCount(count: number | undefined): string {
  return String(count ?? 0);
}

function moneyMetrics(
  parts: Array<{ emphasize?: boolean; key: string; label: string; value?: string }>,
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

/** 组装险种子行 */
function mapInsutypeRows(
  natureKey: string,
  types: DailySettlementInsuranceTypeSummaryDto[] | undefined,
): NatureListRow[] {
  const rows: NatureListRow[] = [];
  (types ?? []).forEach((type, typeIndex) => {
    if (!type) {
      return;
    }
    const title = type.insutypeName?.trim() || UNKNOWN_INSUTYPE_NAME;
    if (title === UNKNOWN_INSUTYPE_NAME) {
      return;
    }
    const typeKey = `${natureKey}-insu-${type.insutype ?? typeIndex}`;
    rows.push({
      key: typeKey,
      rowType: 'type',
      name: `${TYPE_NAME_INDENT}${title}`,
      personCount: formatPersonCount(type.personCount),
      cashAmount: formatTableAmount(type.cashAmount),
      fundAmount: formatTableAmount(type.fundAmount),
      accountAmount: formatTableAmount(type.accountAmount),
      totalAmount: formatTableAmount(type.totalAmount),
    });
  });
  return rows;
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
      label: '医保统筹',
      value: summary.insuranceCompensationAmount,
      emphasize: true,
    },
    {
      key: 'insuranceAccountAmount',
      label: '个人账户',
      value: summary.insuranceAccountAmount,
      emphasize: true,
    },
  ]);
  const retailDiff = resolveRetailUploadDiffAmount(summary);
  if (!props.omitRetailUploadDiff && !isZeroMoney(retailDiff)) {
    items.push(
      ...moneyMetrics([
        {
          key: 'chsRetailUploadDiffAmount',
          label: '医保上传差额',
          value: retailDiff,
          emphasize: true,
        },
      ]),
    );
  }
  const natures = summary.expenseNatureSummaries;
  items.push(
    ...moneyMetrics([
      {
        key: 'cityInsuranceTotal',
        label: '市医保合计',
        value: findNatureTotalAmount(natures, '市医保') ?? '0.00',
        emphasize: true,
      },
      {
        key: 'provinceInsuranceTotal',
        label: '省医保合计',
        value: findNatureTotalAmount(natures, '省医保') ?? '0.00',
        emphasize: true,
      },
      {
        key: 'selfPayTotal',
        label: '自费合计',
        value: findNatureTotalAmount(natures, '自费') ?? '0.00',
        emphasize: true,
      },
    ]),
  );
  if (summary.depositAmount !== undefined) {
    items.push({
      key: 'depositAmount',
      label: '预交金',
      value: formatMoney(summary.depositAmount),
    });
  }
  // 住院多退少补：有字段即展示（含 0.00），与预交金列同级独立
  if (summary.depositRefundAmount !== undefined) {
    items.push({
      key: 'depositRefundAmount',
      label: '退预交金',
      value: formatMoney(summary.depositRefundAmount),
    });
  }
  if (summary.settleSupplementAmount !== undefined) {
    items.push({
      key: 'settleSupplementAmount',
      label: '结算补收',
      value: formatMoney(summary.settleSupplementAmount),
    });
  }
  return items;
});

/** 费用性质 → 框线表行（与打印同构） */
const natureListRows = computed<NatureListRow[]>(() => {
  const natures = props.summary?.expenseNatureSummaries ?? [];
  const rows: NatureListRow[] = [];
  natures.forEach((nature, index) => {
    if (!nature) {
      return;
    }
    const title = nature.contractName?.trim() || UNKNOWN_CONTRACT_NAME;
    const natureKey = `nature-${nature.contractNo ?? index}`;
    const insurance = Boolean(nature.insurance);
    if (insurance) {
      rows.push(
        {
          key: natureKey,
          rowType: 'nature',
          name: title,
          personCount: formatPersonCount(nature.personCount),
          cashAmount: formatTableAmount(nature.cashAmount),
          fundAmount: formatTableAmount(nature.fundAmount),
          accountAmount: formatTableAmount(nature.accountAmount),
          totalAmount: formatTableAmount(nature.totalAmount),
        },
        ...mapInsutypeRows(natureKey, nature.insuranceTypeSummaries),
      );
      return;
    }
    rows.push({
      key: natureKey,
      rowType: 'nature',
      name: title,
      personCount: formatPersonCount(nature.personCount),
      cashAmount: '',
      fundAmount: '',
      accountAmount: '',
      totalAmount: formatTableAmount(nature.totalAmount),
    });
  });
  return rows;
});

const channelMetricRows = computed(() => chunkMetrics(channelMetrics.value));

/** 院点启用工伤且后端返回统计时展示独立块 */
const showWorkInjuryBlock = computed(() => {
  return isWorkInjuryUiEnabled() && Boolean(props.summary?.workInjurySummary);
});

/** 工伤分类金额四列网格（不入总合计） */
const workInjuryCategoryMetricRows = computed(() => {
  const wi = props.summary?.workInjurySummary;
  if (!wi) {
    return [] as MetricItem[][];
  }
  const items = moneyMetrics([
    {
      key: 'wiWesternPatent',
      label: '西药中成药',
      value: wi.westernPatentMedicineAmount,
    },
    { key: 'wiTreatment', label: '诊疗', value: wi.treatmentAmount },
    { key: 'wiConsumable', label: '耗材', value: wi.consumableAmount },
    { key: 'wiHerbal', label: '中草药', value: wi.herbalMedicineAmount },
    { key: 'wiConsultation', label: '诊察费', value: wi.consultationFeeAmount },
  ]);
  items.push({
    key: 'wiPersonCount',
    label: '人次',
    value: `${formatPersonCount(wi.personCount)} 人次`,
    emphasize: true,
  });
  return chunkMetrics(items);
});

/** 工伤费用性质明细一行（统筹 / 个人自付） */
const workInjuryNatureRows = computed<NatureListRow[]>(() => {
  const wi = props.summary?.workInjurySummary;
  if (!wi) {
    return [];
  }
  return [
    {
      key: 'work-injury-nature',
      rowType: 'nature',
      name: wi.contractName?.trim() || '工伤保险',
      personCount: formatPersonCount(wi.personCount),
      cashAmount: formatTableAmount(wi.cashAmount),
      fundAmount: formatTableAmount(wi.fundAmount),
      accountAmount: formatTableAmount(wi.accountAmount),
      totalAmount: formatTableAmount(wi.totalAmount),
    },
  ];
});

const hasContent = computed(
  () =>
    channelMetrics.value.length > 0 || natureListRows.value.length > 0 || showWorkInjuryBlock.value,
);

/** 表格行主键 */
function resolveNatureRowKey(row: NatureListRow): string {
  return row.key;
}

/** 性质汇总行加粗，与打印一致 */
function resolveNatureRowClassName({ row }: { row: NatureListRow }): string {
  return row.rowType === 'nature' ? 'ds-wire-row--nature' : 'ds-wire-row--type';
}

defineExpose({ hasContent });
</script>

<template>
  <div v-if="hasContent" class="ds-summary flex flex-col gap-2">
    <div v-if="channelMetricRows.length > 0" class="flex flex-col gap-1.5">
      <div
        v-for="(row, rowIndex) in channelMetricRows"
        :key="`channel-row-${rowIndex}`"
        class="grid grid-cols-4 gap-x-5 gap-y-1 text-sm leading-snug"
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

    <div v-if="natureListRows.length > 0" class="ds-wire-block">
      <div class="mb-1.5 text-sm font-semibold leading-tight text-[var(--el-text-color-primary)]">
        费用性质明细
      </div>
      <ElTable
        class="ds-wire-table"
        :data="natureListRows"
        :row-key="resolveNatureRowKey"
        :row-class-name="resolveNatureRowClassName"
        border
        :stripe="false"
        empty-text="暂无数据"
      >
        <ElTableColumn
          prop="name"
          label="费用性质/险种"
          min-width="168"
          align="left"
          header-align="center"
          show-overflow-tooltip
        />
        <ElTableColumn
          prop="personCount"
          label="人数"
          width="72"
          align="center"
          header-align="center"
        />
        <ElTableColumn
          prop="cashAmount"
          label="医保现金(元)"
          min-width="112"
          align="right"
          header-align="center"
        />
        <ElTableColumn
          prop="fundAmount"
          label="医保统筹(元)"
          min-width="100"
          align="right"
          header-align="center"
        />
        <ElTableColumn
          prop="accountAmount"
          label="个人账户(元)"
          min-width="112"
          align="right"
          header-align="center"
        />
        <ElTableColumn
          prop="totalAmount"
          label="总额(元)"
          min-width="112"
          align="right"
          header-align="center"
        />
      </ElTable>
    </div>

    <div v-if="showWorkInjuryBlock" class="ds-wire-block">
      <div class="mb-1.5 text-sm font-semibold leading-tight text-[var(--el-text-color-primary)]">
        工伤统计（不计入总合计）
      </div>
      <div v-if="workInjuryCategoryMetricRows.length > 0" class="mb-2 flex flex-col gap-1.5">
        <div
          v-for="(row, rowIndex) in workInjuryCategoryMetricRows"
          :key="`wi-cat-row-${rowIndex}`"
          class="grid grid-cols-4 gap-x-5 gap-y-1 text-sm leading-snug"
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
      <ElTable
        v-if="workInjuryNatureRows.length > 0"
        class="ds-wire-table"
        :data="workInjuryNatureRows"
        :row-key="resolveNatureRowKey"
        :row-class-name="resolveNatureRowClassName"
        border
        :stripe="false"
        empty-text="暂无数据"
      >
        <ElTableColumn
          prop="name"
          label="费用性质"
          min-width="168"
          align="left"
          header-align="center"
          show-overflow-tooltip
        />
        <ElTableColumn
          prop="personCount"
          label="人数"
          width="72"
          align="center"
          header-align="center"
        />
        <ElTableColumn
          prop="cashAmount"
          label="个人自付(元)"
          min-width="112"
          align="right"
          header-align="center"
        />
        <ElTableColumn
          prop="fundAmount"
          label="统筹(元)"
          min-width="100"
          align="right"
          header-align="center"
        />
        <ElTableColumn
          prop="accountAmount"
          label="个人账户(元)"
          min-width="112"
          align="right"
          header-align="center"
        />
        <ElTableColumn
          prop="totalAmount"
          label="总额(元)"
          min-width="112"
          align="right"
          header-align="center"
        />
      </ElTable>
    </div>
  </div>
</template>

<style scoped>
.ds-wire-block {
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color);
}

/* 纯框线表：白底、全边框、无斑马纹，贴近打印稿 */
.ds-wire-table {
  --el-table-border-color: #333;
  --el-table-header-bg-color: #fff;
  --el-table-tr-bg-color: #fff;
  --el-table-row-hover-bg-color: #fff;
  --el-table-bg-color: #fff;
  --el-fill-color-blank: #fff;

  width: 100%;
  font-size: 12px;
  color: #222;
  /* 只补表头顶边，左右/底边交给单元格边框，避免叠线变粗 */
  border: none;
  border-top: 1px solid #333;
}

/* 去掉 EP 伪边线，改由外框 + 单元格边框闭合，避免表头顶边丢失 */
.ds-wire-table :deep(.el-table__inner-wrapper::before),
.ds-wire-table :deep(.el-table__inner-wrapper::after) {
  display: none;
}

.ds-wire-table :deep(.el-table__border-left-patch) {
  display: none;
}

.ds-wire-table :deep(th.el-table__cell) {
  height: 36px;
  padding: 0;
  font-size: 12px;
  font-weight: 700;
  color: #111;
  background: #fff !important;
  border-color: #333 !important;
}

.ds-wire-table :deep(td.el-table__cell) {
  height: 34px;
  padding: 0;
  color: #222;
  background: #fff !important;
  border-color: #333 !important;
}

.ds-wire-table :deep(.el-table__body tr:hover > td.el-table__cell) {
  background: #fff !important;
}

.ds-wire-table :deep(.cell) {
  padding: 6px 8px;
  line-height: 1.4;
}

.ds-wire-table :deep(.ds-wire-row--nature td.el-table__cell) {
  font-weight: 700;
  color: #111;
}

.ds-wire-table :deep(.ds-wire-row--type td.el-table__cell) {
  font-weight: 400;
  color: #333;
}
</style>
