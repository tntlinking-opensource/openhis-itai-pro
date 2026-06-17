<script setup lang="ts">
import type { NurseStationModulePatient } from '../components/nurse-station-module-patient.types';

import { ref } from 'vue';

import dayjs from 'dayjs';
import { ElButton, ElDatePicker, ElMessage } from 'element-plus';

import { useInpatientDailyChargePrint } from '#/composables/use-inpatient-daily-charge-print';

interface Props {
  patients?: NurseStationModulePatient[];
}

defineOptions({
  name: 'InpatientNurseStationDailySettlementModule',
});

const props = withDefaults(defineProps<Props>(), {
  patients: () => [],
});

const billDate = ref(dayjs().format('YYYY-MM-DD'));
const { isPrinting, previewLoading, printInpatientDailyChargeLists } =
  useInpatientDailyChargePrint();

async function handlePrintDailyChargeList() {
  if (!props.patients.length) {
    ElMessage.warning('请先选择患者');
    return;
  }
  const date = billDate.value?.trim() || dayjs().format('YYYY-MM-DD');
  try {
    await printInpatientDailyChargeLists(
      props.patients.map((patient) => ({
        encounterId: patient.id,
        billDate: date,
        jobName: `住院日费用清单-${patient.name}`,
      })),
    );
    ElMessage.success(`已发送 ${props.patients.length} 份住院日费用清单`);
  } catch {
    // composable 已记录日志
  }
}
</script>

<template>
  <div
    class="daily-settlement-module flex h-full min-h-0 w-full flex-1 flex-col overflow-hidden rounded-md bg-[#eef9fd] p-3"
  >
    <header
      v-if="patients.length > 0"
      class="mb-3 flex flex-wrap items-center justify-between gap-2 rounded-md border border-solid border-[var(--el-border-color-lighter)] bg-white px-4 py-3"
    >
      <div class="flex flex-wrap items-center gap-3">
        <span class="text-sm text-[var(--el-text-color-regular)]">费用日期</span>
        <ElDatePicker
          v-model="billDate"
          type="date"
          value-format="YYYY-MM-DD"
          format="YYYY-MM-DD"
          placeholder="请选择费用日期"
          class="w-40"
        />
        <span class="text-sm text-[var(--el-text-color-secondary)]">
          已选 {{ patients.length }} 位患者
        </span>
      </div>
      <ElButton
        type="primary"
        :loading="isPrinting || previewLoading"
        @click="handlePrintDailyChargeList"
      >
        打印日费用清单
      </ElButton>
    </header>

    <section
      v-if="patients.length > 0"
      class="flex min-h-0 flex-1 flex-col gap-3 overflow-auto pr-1"
    >
      <article
        v-for="patient in patients"
        :key="patient.id"
        class="daily-settlement-sheet max-w-240 mx-auto w-full rounded-md border border-solid border-[var(--el-border-color)] bg-white px-6 pb-4 pt-5"
      >
        <header class="mb-4 text-center">
          <h3 class="text-8 font-600 m-0 text-[var(--el-text-color-primary)]">
            住院患者日费用清单
          </h3>
          <p class="m-0 mt-1 text-sm text-[var(--el-text-color-secondary)]">
            预览摘要（打印以 PDF 为准）
          </p>
        </header>

        <div class="sheet-grid">
          <div class="sheet-cell"><span>费用日期：</span>{{ billDate }}</div>
          <div class="sheet-cell"><span>姓名：</span>{{ patient.name }}</div>
          <div class="sheet-cell"><span>床号：</span>{{ patient.bedNo || '—' }}床</div>
          <div class="sheet-cell">
            <span>科室：</span>{{ patient.wardName || patient.regionName || '—' }}
          </div>
        </div>

        <div class="sheet-note">
          <span>说明：</span>点击「打印日费用清单」将按所选费用日期从收费系统取数生成 A4 PDF。
        </div>
      </article>
    </section>
    <section v-else class="flex min-h-0 min-w-0 flex-1 items-center justify-center bg-[#eef9fd]">
      <ElEmpty description="请先选择患者后查看住院日结" />
    </section>
  </div>
</template>

<style scoped>
.daily-settlement-sheet {
  box-shadow: 0 1px 2px rgb(15 23 42 / 4%);
}

.sheet-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 18px;
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
</style>
