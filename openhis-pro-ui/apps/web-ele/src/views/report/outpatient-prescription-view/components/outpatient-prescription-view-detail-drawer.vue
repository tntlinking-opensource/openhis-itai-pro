<!--
  门诊处方查看 · 详情 Drawer：方头摘要 + 药品明细表 + 处方打印（与药房发药同款）。
  分组列用括线标记（不展示 groupId）；抽屉内容铺满不设表内滚动。
-->
<script setup lang="ts">
import type {
  OutpatientPrescriptionDrugLineDto,
  OutpatientPrescriptionSummaryRow,
} from '#/api/report/outpatient-prescription-view';
import type { EncounterMedicationDispenseSlipRowDto } from '#/api/outpatient-pharmacy-dispense/outpatient-pharmacy-dispense.types';

import { computed, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { Printer } from '@element-plus/icons-vue';
import {
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElMessage,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { httpPageEncounterMedicationDispenseSlips } from '#/api/outpatient-pharmacy-dispense/outpatient-pharmacy-dispense.http';
import { pageOutpatientPrescriptionViewApi } from '#/api/report/outpatient-prescription-view';
import { useRequest } from '#/hooks/use-request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { PRINT_SCENARIO } from '#/utils/print/constants/print-scenario';
import { printOutpatientTcmPrescription } from '#/utils/print/print-outpatient-tcm-prescription';
import { resolveOutpatientPrescriptionPrintScenario } from '#/utils/print/resolve-outpatient-prescription-scenario';
import { groupSlipRowsByPrescriptionNo, print } from '#/utils/print/runtime';
import { REPORT_TABLE_NUM_CELL } from '#/views/report/shared';

defineOptions({ name: 'OutpatientPrescriptionViewDetailDrawer' });

interface OpenOptions {
  summary: OutpatientPrescriptionSummaryRow;
  openedDateFrom: string;
  openedDateTo: string;
}

/** 成组括线位置：无组 / 组首 / 组中 / 组末。 */
type GroupMark = 'none' | 'first' | 'middle' | 'last';

interface DetailTableRow extends OutpatientPrescriptionDrugLineDto {
  groupMark: GroupMark;
}

const DETAIL_PAGE_SIZE = 200;

const [Drawer, drawerApi] = useVbenDrawer();

const summary = ref<OutpatientPrescriptionSummaryRow | null>(null);
const detailLines = ref<DetailTableRow[]>([]);
const printSubmitting = ref(false);

const drawerTitle = computed(() => {
  const rxNo = String(summary.value?.prescriptionNo ?? '').trim();
  return rxNo ? `处方详情 · ${rxNo}` : '处方详情';
});

const drawerLoading = computed(() => detailLoading.value || printSubmitting.value);

const { run: runDetailPage, loading: detailLoading } = useRequest(
  pageOutpatientPrescriptionViewApi,
  {
    manual: true,
    onSuccess: (data) => {
      const records = data?.records ?? [];
      detailLines.value = annotateGroupMarks(records);
      // 点评记录等入口仅有处方号：用首行回填方头展示字段
      fillSummaryFromFirstLine(records[0]);
    },
  },
);

/** 方头缺患者等信息时，用明细首行补齐（不覆盖已有 enrichment）。 */
function fillSummaryFromFirstLine(first?: OutpatientPrescriptionDrugLineDto) {
  const head = summary.value;
  if (!head || !first) {
    return;
  }
  summary.value = {
    ...head,
    encounterId: head.encounterId || first.encounterId,
    encounterBusNo: head.encounterBusNo || first.encounterBusNo,
    patientId: head.patientId || first.patientId,
    patientName: head.patientName || first.patientName,
    patientPhone: head.patientPhone || first.patientPhone,
    diagnosisName: head.diagnosisName || first.diagnosisName,
    chargeStatusEnum: head.chargeStatusEnum || first.chargeStatusEnum,
    chargeStatusText: head.chargeStatusText || first.chargeStatusText,
    dispenseStatusEnum: head.dispenseStatusEnum || first.dispenseStatusEnum,
    dispenseStatusText: head.dispenseStatusText || first.dispenseStatusText,
    orderingPractitionerName: head.orderingPractitionerName || first.orderingPractitionerName,
    orderingDepartmentId: head.orderingDepartmentId || first.orderingDepartmentId,
    orderingDepartmentName: head.orderingDepartmentName || first.orderingDepartmentName,
    reqOpenedTime: head.reqOpenedTime || first.reqOpenedTime,
    rxTypeCode: head.rxTypeCode || first.rxTypeCode,
    rxTypeText: head.rxTypeText || first.rxTypeText,
    medTypeCode: head.medTypeCode || first.medTypeCode,
    medTypeText: head.medTypeText || first.medTypeText,
    contractCategoryText: head.contractCategoryText || first.contractCategoryText,
  };
}

/** 开方时间展示。 */
function formatDateTime(value?: string) {
  const text = String(value ?? '').trim();
  if (!text) {
    return '—';
  }
  return text.length >= 19 ? text.slice(0, 19) : text;
}

/** 数量/剂量数值展示。 */
function formatNumber(value?: number | string | null) {
  if (value === null || value === undefined || value === '') {
    return '—';
  }
  const n = typeof value === 'number' ? value : Number(value);
  if (!Number.isFinite(n)) {
    return String(value);
  }
  return n.toLocaleString('zh-CN', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 4,
  });
}

/** 单价（元）展示。 */
function formatUnitPrice(value?: number | string | null) {
  if (value === null || value === undefined || value === '') {
    return '—';
  }
  const n = typeof value === 'number' ? value : Number(value);
  if (!Number.isFinite(n)) {
    return String(value);
  }
  return n.toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

/** 剂量单位：文案优先，码回落。 */
function formatDoseUnit(row: OutpatientPrescriptionDrugLineDto) {
  return String(row.doseUnitText ?? '').trim() || String(row.doseUnitCode ?? '').trim() || '—';
}

/** 总量单位：文案优先，码回落。 */
function formatQtyUnit(row: OutpatientPrescriptionDrugLineDto) {
  return String(row.unitText ?? '').trim() || String(row.unitCode ?? '').trim() || '—';
}

/** 明细行键。 */
function lineRowKey(row: DetailTableRow) {
  return String(row.medicationRequestId ?? `${row.prescriptionNo}-${row.sortNumber}`);
}

/**
 * 按连续相同 groupId 标注成组括线位置；无组号或单行成组不画线。
 */
function annotateGroupMarks(rows: OutpatientPrescriptionDrugLineDto[]): DetailTableRow[] {
  const sorted = [...rows].sort((left, right) => {
    const leftSort = Number(left.sortNumber ?? 0);
    const rightSort = Number(right.sortNumber ?? 0);
    if (leftSort !== rightSort) {
      return leftSort - rightSort;
    }
    return String(left.medicationRequestId ?? '').localeCompare(
      String(right.medicationRequestId ?? ''),
    );
  });
  const out: DetailTableRow[] = sorted.map((row) => ({ ...row, groupMark: 'none' as GroupMark }));
  let index = 0;
  while (index < out.length) {
    const head = out[index]!;
    const groupKey = String(head.groupId ?? '').trim();
    // 无组号：不成组，跳过
    if (!groupKey) {
      index += 1;
      continue;
    }
    let span = 1;
    while (
      index + span < out.length &&
      String(out[index + span]?.groupId ?? '').trim() === groupKey
    ) {
      span += 1;
    }
    // 仅多行同组才画括线
    if (span >= 2) {
      for (let offset = 0; offset < span; offset += 1) {
        const row = out[index + offset]!;
        if (offset === 0) {
          row.groupMark = 'first';
        } else if (offset === span - 1) {
          row.groupMark = 'last';
        } else {
          row.groupMark = 'middle';
        }
      }
    }
    index += span;
  }
  return out;
}

/** 打开抽屉并按处方号重载整方明细。 */
async function open(options: OpenOptions) {
  const rxNo = String(options.summary.prescriptionNo ?? '').trim();
  if (!rxNo) {
    ElMessage.warning('缺少处方号，无法打开详情');
    return;
  }
  summary.value = options.summary;
  detailLines.value = [];
  drawerApi.open();
  await runDetailPage({
    openedDateFrom: options.openedDateFrom,
    openedDateTo: options.openedDateTo,
    prescriptionNo: rxNo,
    pageNo: 1,
    pageSize: DETAIL_PAGE_SIZE,
  });
}

/**
 * 处方打印：拉就诊发药 slip，按处方号过滤后走与药房一致的打印场景。
 */
async function handlePrint() {
  const head = summary.value;
  const rxNo = String(head?.prescriptionNo ?? '').trim();
  const encounterId = preserveApiIdForRequest(head?.encounterId);
  const patientId = preserveApiIdForRequest(head?.patientId);
  if (!rxNo) {
    ElMessage.warning('缺少处方号，无法打印');
    return;
  }
  if (!encounterId) {
    ElMessage.warning('缺少就诊信息，无法打印');
    return;
  }
  printSubmitting.value = true;
  try {
    const res = await httpPageEncounterMedicationDispenseSlips({
      encounterId,
      pageNo: 1,
      pageSize: DETAIL_PAGE_SIZE,
    });
    const { content: slipList } =
      parsePagedRowsFromApiData<EncounterMedicationDispenseSlipRowDto>(res);
    // 仅保留本方号 slip，与药房按处方号分组打印一致
    const matched = slipList.filter((row) => String(row.prescriptionNo ?? '').trim() === rxNo);
    if (matched.length === 0) {
      ElMessage.warning('未找到可打印的发药明细（可能尚未生成发放单）');
      return;
    }
    const groups = groupSlipRowsByPrescriptionNo(matched);
    const group = groups[0] ?? matched;
    const scenarioCode = resolveOutpatientPrescriptionPrintScenario(group);
    if (scenarioCode === PRINT_SCENARIO.outpatientTcmRx) {
      if (!patientId) {
        ElMessage.warning('缺少患者信息，无法打印中药处方诊断');
        return;
      }
      await printOutpatientTcmPrescription({
        slipRows: group,
        encounterId,
        patientId,
        prescriptionNo: rxNo,
      });
      return;
    }
    await print({
      scenarioCode,
      source: group,
      bizRef: {
        scenarioCode,
        encounterId,
        prescriptionNo: rxNo,
      },
    });
  } catch {
    ElMessage.error('处方打印失败');
  } finally {
    printSubmitting.value = false;
  }
}

defineExpose({ open });
</script>

<template>
  <Drawer
    :title="drawerTitle"
    class="op-rx-view-detail-drawer w-[min(1280px,98vw)]"
    :loading="drawerLoading"
    :footer="false"
  >
    <div class="op-rx-view-detail-content">
      <div class="mb-3 flex items-center justify-end">
        <ElButton type="primary" :icon="Printer" :loading="printSubmitting" @click="handlePrint">
          打印处方
        </ElButton>
      </div>

      <ElDescriptions v-if="summary" class="mb-4" :column="3" border size="small">
        <ElDescriptionsItem label="处方号">
          {{ summary.prescriptionNo || '—' }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="门诊号">
          {{ summary.encounterBusNo || '—' }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="患者">
          {{ summary.patientName || '—' }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="电话号">
          {{ summary.patientPhone || '—' }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="诊断">
          {{ summary.diagnosisName || '—' }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="收费状态">
          <ElTag v-if="summary.chargeStatusText" effect="light">
            {{ summary.chargeStatusText }}
          </ElTag>
          <span v-else>—</span>
        </ElDescriptionsItem>
        <ElDescriptionsItem label="发药状态">
          <ElTag v-if="summary.dispenseStatusText" type="info" effect="light">
            {{ summary.dispenseStatusText }}
          </ElTag>
          <span v-else>—</span>
        </ElDescriptionsItem>
        <ElDescriptionsItem label="开单科室">
          {{ summary.orderingDepartmentName || '—' }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="开单医生">
          {{ summary.orderingPractitionerName || '—' }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="开方时间">
          {{ formatDateTime(summary.reqOpenedTime) }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="处方类别">
          {{ summary.rxTypeText || '—' }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="医保类别">
          {{ summary.medTypeText || '—' }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="费用性质">
          {{ summary.contractCategoryText || '—' }}
        </ElDescriptionsItem>
        <template v-if="summary.reviewStatusEnum || summary.reviewId">
          <ElDescriptionsItem label="点评状态">
            <ElTag
              v-if="summary.reviewStatusText"
              :type="summary.reviewStatusEnum === 'REVIEWED' ? 'success' : 'info'"
              effect="light"
            >
              {{ summary.reviewStatusText }}
            </ElTag>
            <span v-else>—</span>
          </ElDescriptionsItem>
          <ElDescriptionsItem label="点评结果">
            <ElTag
              v-if="summary.reasonableText"
              :type="summary.reasonableFlag === false ? 'danger' : 'success'"
              effect="light"
            >
              {{ summary.reasonableText }}
            </ElTag>
            <span v-else>—</span>
          </ElDescriptionsItem>
          <ElDescriptionsItem label="点评人">
            {{ summary.reviewerName || '—' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="点评时间">
            {{ formatDateTime(summary.reviewedAt) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="点评原因" :span="2">
            {{ summary.reviewReason || '—' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="点评备注">
            {{ summary.reviewRemark || '—' }}
          </ElDescriptionsItem>
        </template>
      </ElDescriptions>

      <ElTable
        v-loading="detailLoading"
        :data="detailLines"
        :row-key="lineRowKey"
        border
        size="small"
      >
        <ElTableColumn label="分组" width="48" align="center" header-align="center">
          <template #default="{ row }">
            <span
              v-if="row.groupMark !== 'none'"
              class="op-rx-group-mark"
              :class="`op-rx-group-mark--${row.groupMark}`"
              aria-hidden="true"
            />
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="medicationName"
          label="药品"
          min-width="120"
          align="center"
          header-align="center"
          show-overflow-tooltip
        />
        <ElTableColumn
          prop="medicationSpec"
          label="规格"
          width="120"
          align="center"
          header-align="center"
          show-overflow-tooltip
        />
        <ElTableColumn
          prop="methodText"
          label="用药途径"
          width="88"
          align="center"
          header-align="center"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.methodText || '—' }}
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="rateText"
          label="用药频次"
          width="88"
          align="center"
          header-align="center"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.rateText || '—' }}
          </template>
        </ElTableColumn>
        <ElTableColumn
          label="单次剂量"
          width="88"
          align="right"
          header-align="right"
          :class-name="REPORT_TABLE_NUM_CELL"
        >
          <template #default="{ row }">
            {{ formatNumber(row.dose) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="剂量单位" width="80" align="center" header-align="center">
          <template #default="{ row }">
            {{ formatDoseUnit(row) }}
          </template>
        </ElTableColumn>
        <ElTableColumn
          label="用药天数"
          width="80"
          align="right"
          header-align="right"
          :class-name="REPORT_TABLE_NUM_CELL"
        >
          <template #default="{ row }">
            {{
              row.dispensePerDuration === null || row.dispensePerDuration === undefined
                ? '—'
                : row.dispensePerDuration
            }}
          </template>
        </ElTableColumn>
        <ElTableColumn
          label="总量"
          width="80"
          align="right"
          header-align="right"
          :class-name="REPORT_TABLE_NUM_CELL"
        >
          <template #default="{ row }">
            {{ formatNumber(row.quantity) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="单位" width="64" align="center" header-align="center">
          <template #default="{ row }">
            {{ formatQtyUnit(row) }}
          </template>
        </ElTableColumn>
        <ElTableColumn
          label="单价(元)"
          width="88"
          align="right"
          header-align="right"
          :class-name="REPORT_TABLE_NUM_CELL"
        >
          <template #default="{ row }">
            {{ formatUnitPrice(row.unitPrice) }}
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="dosageInstructionText"
          label="用法说明"
          min-width="100"
          align="center"
          header-align="center"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.dosageInstructionText || '—' }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="皮试" width="64" align="center" header-align="center">
          <template #default="{ row }">
            {{ row.skinTestFlagText || '—' }}
          </template>
        </ElTableColumn>
      </ElTable>
    </div>
  </Drawer>
</template>

<style scoped>
.op-rx-view-detail-content {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

/* 成组括线：不展示 groupId，仅用竖折线标记同组 */
.op-rx-group-mark {
  display: inline-block;
  width: 10px;
  height: 22px;
  box-sizing: border-box;
  border-left: 2px solid var(--el-color-primary-light-3);
  vertical-align: middle;
}

.op-rx-group-mark--first {
  border-top: 2px solid var(--el-color-primary-light-3);
  border-top-left-radius: 2px;
}

.op-rx-group-mark--middle {
  border-radius: 0;
}

.op-rx-group-mark--last {
  border-bottom: 2px solid var(--el-color-primary-light-3);
  border-bottom-left-radius: 2px;
}
</style>

<style>
/* 抽屉内容区不设固定最大高度，明细表完整展示；超出时由抽屉自身滚动 */
.op-rx-view-detail-drawer .el-drawer__body {
  overflow: visible;
  max-height: none;
}
</style>
