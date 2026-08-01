<script setup lang="ts">
import type { PrintableReportRowDto } from '#/api/peis/report-print';
import { onMounted, reactive, ref } from 'vue';
import { httpPreviewReport, httpSearchPrintable } from '#/api/peis/report-print';
import SectionTitle from '#/components/layouts/section-title.vue';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { getDateRangeQuick } from '#/utils/format/date-range-quick';
import { printPeisReport } from '#/utils/print/print-peis-report';
import {
  ElButton,
  ElDatePicker,
  ElDialog,
  ElInput,
  ElPagination,
  ElTable,
  ElTableColumn,
} from 'element-plus';
defineOptions({ name: 'PeisReportPrint' });
const keyword = ref('');
const dateRange = ref<[string, string]>(
  getDateRangeQuick({ strategy: 'calendar-day' }) as [string, string],
);
const pager = reactive({ pageNo: 1, pageSize: 20 });
const records = ref<PrintableReportRowDto[]>([]);
const total = ref(0);
const loading = ref(false);
const previewVisible = ref(false);
const previewLoading = ref(false);
const previewSummary = ref('');
const previewConclusion = ref('');
const previewHint = ref('');
const selectedRow = ref<PrintableReportRowDto | null>(null);
const printing = ref(false);

/** 加载可打印列表 */

async function loadList() {
  loading.value = true;
  try {
    const res = await httpSearchPrintable({
      pageNo: pager.pageNo,
      pageSize: pager.pageSize,
      keyword: keyword.value.trim() || undefined,
      dateFrom: dateRange.value?.[0],
      dateTo: dateRange.value?.[1],
    });
    const parsed = parsePagedRowsFromApiData(res);
    records.value = parsed.content as PrintableReportRowDto[];
    total.value = parsed.totalCount;
  } finally {
    loading.value = false;
  }
}

/** 打开预览弹窗 */

async function handlePreview(row: PrintableReportRowDto) {
  if (!row.encounterId) {
    return;
  }
  selectedRow.value = row;
  previewVisible.value = true;
  previewLoading.value = true;
  try {
    const res = await httpPreviewReport({ encounterId: row.encounterId });
    previewSummary.value = res.summaryText ?? '';
    previewConclusion.value = res.conclusionText ?? '';
    previewHint.value = res.previewHint ?? '';
  } finally {
    previewLoading.value = false;
  }
}

/** 真打印（hiprint + 业务打印事件） */

async function handlePrint(row: PrintableReportRowDto) {
  if (!row.encounterId) {
    return;
  }
  printing.value = true;
  try {
    const result = await printPeisReport({ encounterId: row.encounterId });
    if (result.ok) {
      await loadList();
    }
  } finally {
    printing.value = false;
  }
}
onMounted(() => {
  loadList();
});
</script>

<template>
  <div class="p-4">
    <SectionTitle title="可打印报告" />
    <div class="mb-4 flex flex-wrap items-center gap-2">
      <ElDatePicker
        v-model="dateRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        start-placeholder="定稿开始"
        end-placeholder="定稿结束"
        @change="loadList"
      />
      <ElInput
        v-model="keyword"
        class="w-48"
        placeholder="姓名/流水号"
        clearable
        @keyup.enter="loadList"
      />
      <ElButton type="primary" :loading="loading" @click="loadList"> 查询 </ElButton>
    </div>
    <ElTable v-loading="loading" :data="records" stripe height="560">
      <ElTableColumn prop="patientName" label="姓名" min-width="90" />
      <ElTableColumn prop="busNo" label="流水号" min-width="120" />
      <ElTableColumn prop="finalizedAt" label="定稿时间" min-width="160" />
      <ElTableColumn label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <ElButton link type="primary" @click="handlePreview(row)"> 预览 </ElButton>
          <ElButton link type="primary" :loading="printing" @click="handlePrint(row)">
            打印
          </ElButton>
        </template>
      </ElTableColumn>
    </ElTable>
    <ElPagination
      v-model:current-page="pager.pageNo"
      v-model:page-size="pager.pageSize"
      class="mt-3 justify-end"
      layout="total, prev, pager, next"
      :total="total"
      @current-change="loadList"
      @size-change="loadList"
    />
    <ElDialog v-model="previewVisible" title="报告预览" width="640px">
      <div v-loading="previewLoading" class="flex flex-col gap-3">
        <div v-if="selectedRow" class="text-sm text-gray-600">
          {{ selectedRow.patientName }} · {{ selectedRow.busNo }}
        </div>
        <div>
          <div class="mb-1 text-sm font-medium">综述</div>
          <div class="whitespace-pre-wrap rounded border p-2 text-sm">
            {{ previewSummary || '（无）' }}
          </div>
        </div>
        <div>
          <div class="mb-1 text-sm font-medium">结论</div>
          <div class="whitespace-pre-wrap rounded border p-2 text-sm">
            {{ previewConclusion || '（无）' }}
          </div>
        </div>
        <p v-if="previewHint" class="text-xs text-amber-600">{{ previewHint }}</p>
      </div>
      <template #footer>
        <ElButton @click="previewVisible = false">关闭</ElButton>
        <ElButton
          type="primary"
          :loading="printing"
          @click="selectedRow && handlePrint(selectedRow)"
        >
          打印
        </ElButton>
      </template>
    </ElDialog>
  </div>
</template>
