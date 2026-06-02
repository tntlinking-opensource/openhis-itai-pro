<script setup lang="ts">
import { computed } from 'vue';

import dayjs from 'dayjs';

interface SettlementPatientItem {
  id: string;
  bedNo: string;
  name: string;
  gender: string;
  age: number;
  nurseLevel: string;
  /** 费用性质。 */
  feeType?: string;
  /** 主要诊断。 */
  diagnosis?: string;
  wardName: string;
  riskTags: string[];
}

interface SettlementSheetItem {
  id: string;
  printTime: string;
  admissionTime: string;
  dischargeTime: string;
  hospitalizationDays: number;
  patientName: string;
  inpatientNo: string;
  departmentName: string;
  bedNo: string;
  totalCost: string;
  todayCost: string;
  balance: string;
  costDate: string;
  prepayAmount: string;
  note: string;
}

interface Props {
  patients?: SettlementPatientItem[];
}

defineOptions({
  name: 'InpatientNurseStationDailySettlementModule',
});

const props = withDefaults(defineProps<Props>(), {
  patients: () => [],
});

/** 根据患者信息构建住院日结清单展示数据。 */
const settlementSheets = computed<SettlementSheetItem[]>(() =>
  props.patients.map((patient, index) => {
    const baseDate = dayjs().subtract(index, 'day');
    const totalCost = 94.72 + index * 36;
    const todayCost = index % 2 === 0 ? 0 : 12.5;
    return {
      id: patient.id,
      printTime: dayjs().format('YYYY-MM-DD'),
      admissionTime: baseDate.subtract(16, 'day').format('YYYY-MM-DD HH:mm:ss'),
      dischargeTime: '',
      hospitalizationDays: 16 + index,
      patientName: patient.name,
      inpatientNo: patient.id.replace('p-', '20250000'),
      departmentName: patient.wardName || '神经内一科住院科室',
      bedNo: `${patient.bedNo || '--'}床`,
      totalCost: totalCost.toFixed(2),
      todayCost: todayCost.toFixed(2),
      balance: (todayCost - totalCost).toFixed(2),
      costDate: dayjs().format('YYYY-MM-DD'),
      prepayAmount: '0.00',
      note: '以上费用如有异议，请及时向所在科室咨询。患者在24小时内不质疑，视为认可该费用。',
    };
  }),
);
</script>

<template>
  <div
    class="daily-settlement-module flex h-full min-h-0 w-full flex-1 flex-col overflow-hidden rounded-md bg-[#eef9fd] p-3"
  >
    <section
      v-if="settlementSheets.length > 0"
      class="flex min-h-0 flex-1 flex-col gap-3 overflow-auto pr-1"
    >
      <article
        v-for="sheet in settlementSheets"
        :key="sheet.id"
        class="daily-settlement-sheet max-w-240 mx-auto w-full rounded-md border border-solid border-[var(--el-border-color)] bg-white px-6 pb-4 pt-5"
      >
        <header class="mb-4 text-center">
          <h3 class="text-8 font-600 m-0 text-[var(--el-text-color-primary)]">
            住院患者日费用清单
          </h3>
        </header>

        <div class="sheet-grid">
          <div class="sheet-cell"><span>打印时间：</span>{{ sheet.printTime }}</div>
          <div class="sheet-cell"><span>入院时间：</span>{{ sheet.admissionTime }}</div>
          <div class="sheet-cell"><span>出院时间：</span>{{ sheet.dischargeTime || '-' }}</div>
          <div class="sheet-cell"><span>住院天数：</span>{{ sheet.hospitalizationDays }}天</div>
          <div class="sheet-cell"><span>姓名：</span>{{ sheet.patientName }}</div>
          <div class="sheet-cell"><span>住院号：</span>{{ sheet.inpatientNo }}</div>
          <div class="sheet-cell"><span>科室：</span>{{ sheet.departmentName }}</div>
          <div class="sheet-cell"><span>床号：</span>{{ sheet.bedNo }}</div>
        </div>

        <div class="sheet-amount-grid">
          <div class="sheet-cell"><span>累计费用：</span>{{ sheet.totalCost }}</div>
          <div class="sheet-cell"><span>当日费用：</span>{{ sheet.todayCost }}</div>
          <div class="sheet-cell"><span>费用日期：</span>{{ sheet.costDate }}</div>
          <div class="sheet-cell"><span>预交金额：</span>{{ sheet.prepayAmount }}</div>
          <div class="sheet-cell"><span>结余：</span>{{ sheet.balance }}</div>
        </div>

        <div class="sheet-note"><span>备注：</span>{{ sheet.note }}</div>
      </article>
    </section>
    <section v-else class="flex min-h-0 min-w-0 flex-1 items-center justify-center bg-[#eef9fd]">
      <ElEmpty description="请先多选患者后查看住院日结" />
    </section>
  </div>
</template>

<style scoped>
.daily-settlement-sheet {
  box-shadow: 0 1px 2px rgb(15 23 42 / 4%);
}

.sheet-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px 18px;
  margin-bottom: 24px;
}

.sheet-amount-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px 18px;
  margin-bottom: 18px;
}

.sheet-cell {
  color: var(--el-text-color-primary);
  font-size: 14px;
  line-height: 1.5;
}

.sheet-cell > span,
.sheet-note > span {
  color: var(--el-text-color-regular);
  font-weight: 600;
}

.sheet-note {
  border-top: 1px solid var(--el-border-color);
  color: var(--el-text-color-primary);
  font-size: 13px;
  line-height: 1.7;
  padding-top: 10px;
}

@media (max-width: 1280px) {
  .sheet-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .sheet-amount-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
