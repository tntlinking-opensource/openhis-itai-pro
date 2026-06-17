<script setup lang="ts">
import type {
  ReportDefinitionItemDto,
  ReportFilterFieldDto,
  ReportQueryPageResultDto,
} from '#/api/report/report-query';

import { computed, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';

import {
  ElButton,
  ElCard,
  ElDatePicker,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import {
  downloadReportExportFile,
  listReportDefinitionsApi,
  parseReportFilters,
  reportExportExcelApi,
  reportQueryPageApi,
  resolveReportHeaderLines,
} from '#/api/report/report-query';
import ReportPdfPreviewDialog from '#/components/report/ReportPdfPreviewDialog.vue';
import { useReportCClassPdf } from '#/composables/use-report-cclass-pdf';
import { useRequest } from '#/hooks/use-request';

defineOptions({
  name: 'WhaleReportCenter',
});

const definitions = ref<ReportDefinitionItemDto[]>([]);
const selectedReportCode = ref('');
const queryResult = ref<ReportQueryPageResultDto | null>(null);
const previewVisible = ref(false);
const filterValues = ref<Record<string, string>>({});

const { previewLoading, previewBlob, previewPdf, downloadPdf, printPdf, isPrinting } =
  useReportCClassPdf();

const { run: runListDefinitions, loading: definitionsLoading } = useRequest(
  listReportDefinitionsApi,
  {
    manual: true,
    onSuccess: (rows) => {
      definitions.value = Array.isArray(rows) ? rows : [];
      if (!selectedReportCode.value && definitions.value.length > 0) {
        selectedReportCode.value = definitions.value[0]?.reportCode ?? '';
      }
    },
  },
);

const { run: runExportExcel, loading: exportLoading } = useRequest(reportExportExcelApi, {
  manual: true,
  onSuccess: (data) => {
    downloadReportExportFile(data);
  },
});

const { run: runQueryPage, loading: queryLoading } = useRequest(reportQueryPageApi, {
  manual: true,
  onSuccess: (data) => {
    queryResult.value = data ?? null;
  },
});

const activeDefinition = computed(() =>
  definitions.value.find((item) => item.reportCode === selectedReportCode.value),
);

const filterFields = computed(() => parseReportFilters(activeDefinition.value?.filterJson));

const pdfEnabled = computed(
  () =>
    Boolean(activeDefinition.value?.exportPdfEnabled) &&
    Boolean(activeDefinition.value?.pdfTemplateCode?.trim()),
);

const tableColumns = computed(
  () => queryResult.value?.columns ?? activeDefinition.value?.columns ?? [],
);
const tableData = computed(() => queryResult.value?.rows ?? []);

const headerBlock = computed(() => queryResult.value?.header ?? {});
const headerLines = computed(() => resolveReportHeaderLines(headerBlock.value));
const showHeaderCard = computed(
  () =>
    Boolean(headerBlock.value.title || headerBlock.value.subtitle) || headerLines.value.length > 0,
);
const footerBlock = computed(() => queryResult.value?.footer ?? {});

const summaryEntries = computed(() => {
  const summary = queryResult.value?.summary;
  if (!summary) {
    return [];
  }
  return Object.entries(summary).map(([key, value]) => ({
    key,
    value: value == null ? '—' : String(value),
  }));
});

function resetFiltersForDefinition(def?: ReportDefinitionItemDto | null) {
  const next: Record<string, string> = {};
  for (const field of parseReportFilters(def?.filterJson)) {
    if (field.field) {
      next[field.field] = '';
    }
  }
  filterValues.value = next;
}

watch(activeDefinition, (def) => {
  resetFiltersForDefinition(def);
});

function buildQueryRequest() {
  const reportCode = selectedReportCode.value?.trim();
  const params: Record<string, unknown> = { ...filterValues.value };
  return {
    reportCode: reportCode ?? '',
    pageNo: 1,
    pageSize: 200,
    dateFrom: filterValues.value.dateFrom || undefined,
    dateTo: filterValues.value.dateTo || undefined,
    keyword: filterValues.value.keyword || undefined,
    params,
  };
}

function buildPdfParams() {
  const pdfCode = activeDefinition.value?.pdfTemplateCode?.trim();
  if (!pdfCode) {
    return null;
  }
  const req = buildQueryRequest();
  return {
    pdfTemplateCode: pdfCode,
    reportCode: req.reportCode,
    reportName: activeDefinition.value?.name ?? req.reportCode,
    params: {
      ...req.params,
      dateFrom: req.dateFrom,
      dateTo: req.dateTo,
      reportCode: req.reportCode,
    },
  };
}

async function handleQuery() {
  if (!selectedReportCode.value?.trim()) {
    ElMessage.warning('请选择报表');
    return;
  }
  await runQueryPage(buildQueryRequest());
}

async function handleExportExcel() {
  if (!selectedReportCode.value?.trim()) {
    ElMessage.warning('请选择报表');
    return;
  }
  await runExportExcel({ ...buildQueryRequest(), pageSize: 5000 });
}

async function handlePreviewPdf() {
  const pdfParams = buildPdfParams();
  if (!pdfParams) {
    ElMessage.warning('当前报表未配置 PDF 模板');
    return;
  }
  previewVisible.value = true;
  await previewPdf(pdfParams);
}

async function handleRefreshPreview() {
  const pdfParams = buildPdfParams();
  if (pdfParams) {
    await previewPdf(pdfParams);
  }
}

async function handleDownloadPdf() {
  const pdfParams = buildPdfParams();
  if (!pdfParams) {
    ElMessage.warning('当前报表未配置 PDF 模板');
    return;
  }
  await downloadPdf(pdfParams, `${pdfParams.reportCode ?? 'report'}.pdf`);
}

async function handlePrintPdf() {
  const pdfParams = buildPdfParams();
  if (!pdfParams) {
    ElMessage.warning('当前报表未配置 PDF 模板');
    return;
  }
  await printPdf(pdfParams);
}

function renderFilterControl(field: ReportFilterFieldDto) {
  const key = field.field ?? '';
  if (field.type === 'date') {
    return 'date';
  }
  return 'text';
}

void runListDefinitions({ status: 'published' });
</script>

<template>
  <Page auto-content-height>
    <div class="flex h-full min-h-0 flex-col gap-2 p-2">
      <ElCard shadow="never">
        <div class="flex flex-wrap items-end gap-3">
          <div class="flex min-w-56 flex-col gap-1">
            <span class="text-xs text-[var(--el-text-color-secondary)]">报表</span>
            <ElSelect
              v-model="selectedReportCode"
              placeholder="选择已发布报表"
              filterable
              :loading="definitionsLoading"
            >
              <ElOption
                v-for="item in definitions"
                :key="item.reportCode"
                :label="item.name ?? item.reportCode"
                :value="item.reportCode ?? ''"
              />
            </ElSelect>
          </div>

          <template v-for="field in filterFields" :key="field.field">
            <div v-if="renderFilterControl(field) === 'date'" class="flex min-w-40 flex-col gap-1">
              <span class="text-xs text-[var(--el-text-color-secondary)]">
                {{ field.label ?? field.field }}
              </span>
              <ElDatePicker
                v-model="filterValues[field.field!]"
                type="date"
                value-format="YYYY-MM-DD"
                class="!w-full"
                clearable
              />
            </div>
            <div v-else class="flex min-w-44 flex-col gap-1">
              <span class="text-xs text-[var(--el-text-color-secondary)]">
                {{ field.label ?? field.field }}
              </span>
              <ElInput v-model="filterValues[field.field!]" clearable />
            </div>
          </template>

          <div class="flex flex-wrap gap-2 pb-0.5">
            <ElButton type="primary" :loading="queryLoading" @click="handleQuery">查询</ElButton>
            <ElButton :loading="exportLoading" @click="handleExportExcel">导出 Excel</ElButton>
            <template v-if="pdfEnabled">
              <ElButton :loading="previewLoading" @click="handlePreviewPdf">打印预览</ElButton>
              <ElButton :loading="previewLoading" @click="handleDownloadPdf">导出 PDF</ElButton>
              <ElButton type="primary" :loading="isPrinting" @click="handlePrintPdf">打印</ElButton>
            </template>
          </div>
        </div>
      </ElCard>

      <ElCard v-if="showHeaderCard" shadow="never" class="shrink-0">
        <div class="text-center">
          <div v-if="headerBlock.title" class="text-lg font-semibold">
            {{ headerBlock.title }}
          </div>
          <div v-if="headerBlock.subtitle" class="text-sm text-[var(--el-text-color-secondary)]">
            {{ headerBlock.subtitle }}
          </div>
          <template v-for="(line, idx) in headerLines" :key="idx">
            <div
              v-if="line.type === 'split'"
              class="mt-1 flex justify-between text-xs text-[var(--el-text-color-secondary)]"
            >
              <span>{{ line.left }}</span>
              <span>{{ line.right }}</span>
            </div>
            <div
              v-else
              class="mt-1 text-xs text-[var(--el-text-color-secondary)]"
              :class="{
                'text-left': line.align === 'left',
                'text-center': line.align === 'center',
                'text-right': line.align === 'right',
              }"
            >
              {{ line.text }}
            </div>
          </template>
        </div>
      </ElCard>

      <ElCard v-if="summaryEntries.length" shadow="never" class="shrink-0">
        <div class="grid grid-cols-2 gap-x-6 gap-y-1 text-sm md:grid-cols-4">
          <div v-for="item in summaryEntries" :key="item.key" class="flex justify-between gap-2">
            <span class="text-[var(--el-text-color-secondary)]">{{ item.key }}</span>
            <span class="font-medium">{{ item.value }}</span>
          </div>
        </div>
      </ElCard>

      <ElCard shadow="never" class="min-h-0 flex-1" body-class="h-full">
        <ElTable
          v-loading="queryLoading || definitionsLoading"
          :data="tableData"
          height="100%"
          stripe
          border
        >
          <ElTableColumn
            v-for="col in tableColumns"
            :key="col.field"
            :prop="col.field"
            :label="col.label ?? col.field"
            :min-width="col.width ?? 120"
            :align="(col.align as 'center' | 'left' | 'right') ?? 'left'"
            show-overflow-tooltip
          />
        </ElTable>
      </ElCard>

      <ElCard
        v-if="footerBlock.notes || (Array.isArray(footerBlock.lines) && footerBlock.lines.length)"
        shadow="never"
        class="shrink-0"
      >
        <p v-if="footerBlock.notes" class="text-sm text-[var(--el-text-color-secondary)]">
          {{ footerBlock.notes }}
        </p>
        <div v-for="(line, idx) in footerBlock.lines as string[]" :key="idx" class="text-sm">
          {{ line }}
        </div>
      </ElCard>
    </div>

    <ReportPdfPreviewDialog
      v-model="previewVisible"
      :loading="previewLoading"
      :pdf-blob="previewBlob"
      :title="`打印预览 — ${activeDefinition?.name ?? selectedReportCode}`"
      @refresh="handleRefreshPreview"
    />
  </Page>
</template>
