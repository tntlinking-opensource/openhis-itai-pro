<script setup lang="ts">
import type {
  ReportColumnDto,
  ReportDefinitionItemDto,
  ReportFilterFieldDto,
  ReportQueryPageResultDto,
} from '#/api/report/report';

import { computed, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';

import {
  ElButton,
  ElCard,
  ElCheckbox,
  ElDatePicker,
  ElDialog,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import {
  downloadReportExportFile,
  getReportColumnOverrideApi,
  listReportDefinitionsApi,
  parseReportFilters,
  resolveDefinitionFilters,
  reportExportExcelApi,
  reportQueryPageApi,
  resetReportColumnOverrideApi,
  resolveReportHeaderLines,
  saveReportColumnOverrideApi,
} from '#/api/report/report';
import { useRequest } from '#/hooks/use-request';

defineOptions({
  name: 'WhaleReportCenter',
});

const definitions = ref<ReportDefinitionItemDto[]>([]);
const selectedReportCode = ref('');
const queryResult = ref<ReportQueryPageResultDto | null>(null);
const filterValues = ref<Record<string, string | string[]>>({});
const columnDialogVisible = ref(false);
const columnDraft = ref<ReportColumnDto[]>([]);
const columnSaving = ref(false);
const columnLoading = ref(false);

const { run: runListDefinitions, loading: definitionsLoading } = useRequest(
  listReportDefinitionsApi,
  {
    manual: true,
    showErrorMessage: false,
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
  showErrorMessage: false,
  onSuccess: (data) => {
    downloadReportExportFile(data);
  },
});

const { run: runQueryPage, loading: queryLoading } = useRequest(reportQueryPageApi, {
  manual: true,
  showErrorMessage: false,
  onSuccess: (data) => {
    queryResult.value = data ?? null;
  },
});

const activeDefinition = computed(() =>
  definitions.value.find((item) => item.reportCode === selectedReportCode.value),
);

const filterFields = computed(() => resolveDefinitionFilters(activeDefinition.value));

const columnCustomizeEnabled = computed(
  () => activeDefinition.value?.allowColumnCustomize !== false,
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
  const next: Record<string, string | string[]> = {};
  for (const field of resolveDefinitionFilters(def)) {
    if (field.field) {
      next[field.field] = field.type === 'daterange' ? [] : '';
    }
  }
  filterValues.value = next;
}

watch(activeDefinition, (def) => {
  resetFiltersForDefinition(def);
});

watch(selectedReportCode, () => {
  queryResult.value = null;
});

function buildFilterParams(): Record<string, unknown> {
  const params: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(filterValues.value)) {
    if (key === 'dateFrom' || key === 'dateTo' || key === 'keyword') {
      continue;
    }
    if (value == null || value === '') {
      continue;
    }
    if (Array.isArray(value)) {
      const cleaned = value.filter((item) => item != null && item !== '');
      if (cleaned.length === 0) {
        continue;
      }
      params[key] = cleaned.join(',');
      continue;
    }
    params[key] = value;
  }
  return params;
}

function filterValueAsString(key: string): string {
  const value = filterValues.value[key];
  if (Array.isArray(value)) {
    return value[0] ?? '';
  }
  return value ?? '';
}

function buildQueryRequest() {
  const reportCode = selectedReportCode.value?.trim();
  return {
    reportCode: reportCode ?? '',
    pageNo: 1,
    pageSize: 200,
    dateFrom: filterValueAsString('dateFrom') || undefined,
    dateTo: filterValueAsString('dateTo') || undefined,
    keyword: filterValueAsString('keyword') || undefined,
    params: buildFilterParams(),
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
  const total = queryResult.value?.totalCount;
  const rowCount = queryResult.value?.rows?.length ?? 0;
  if ((total != null && total >= 5000) || rowCount >= 5000) {
    ElMessage.warning('导出数据最多 5000 行，超出部分不会包含在文件中');
  }
  await runExportExcel(buildQueryRequest());
}

async function openColumnSettings() {
  const reportCode = selectedReportCode.value?.trim();
  if (!reportCode) {
    ElMessage.warning('请选择报表');
    return;
  }
  columnLoading.value = true;
  try {
    const result = await getReportColumnOverrideApi({ reportCode });
    columnDraft.value = (result.columns ?? []).map((col) => ({
      ...col,
      visible: col.visible !== false,
    }));
    columnDialogVisible.value = true;
  } finally {
    columnLoading.value = false;
  }
}

function moveColumn(index: number, delta: number) {
  const nextIndex = index + delta;
  if (nextIndex < 0 || nextIndex >= columnDraft.value.length) {
    return;
  }
  const rows = [...columnDraft.value];
  const [row] = rows.splice(index, 1);
  if (!row) {
    return;
  }
  rows.splice(nextIndex, 0, row);
  columnDraft.value = rows;
}

async function handleSaveColumnSettings() {
  const reportCode = selectedReportCode.value?.trim();
  if (!reportCode) {
    return;
  }
  columnSaving.value = true;
  try {
    await saveReportColumnOverrideApi({
      reportCode,
      columns: columnDraft.value.map((col) => ({
        ...col,
        visible: col.visible !== false,
      })),
    });
    ElMessage.success('列设置已保存');
    columnDialogVisible.value = false;
    if (queryResult.value) {
      await runQueryPage(buildQueryRequest());
    }
  } finally {
    columnSaving.value = false;
  }
}

async function handleResetColumnSettings() {
  const reportCode = selectedReportCode.value?.trim();
  if (!reportCode) {
    return;
  }
  columnSaving.value = true;
  try {
    await resetReportColumnOverrideApi({ reportCode });
    const result = await getReportColumnOverrideApi({ reportCode });
    columnDraft.value = (result.columns ?? []).map((col) => ({
      ...col,
      visible: col.visible !== false,
    }));
    ElMessage.success('已恢复默认列');
    if (queryResult.value) {
      await runQueryPage(buildQueryRequest());
    }
  } finally {
    columnSaving.value = false;
  }
}

function filterControlType(field: ReportFilterFieldDto): string {
  return field.type ?? 'text';
}

function resolveFilterSelectOptions(field: ReportFilterFieldDto) {
  if (!Array.isArray(field.options)) {
    return [];
  }
  return field.options.map((opt) => ({
    label: opt.label ?? opt.value ?? '',
    value: opt.value ?? '',
  }));
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
            <span
              v-if="!definitionsLoading && definitions.length === 0"
              class="text-xs text-[var(--el-text-color-secondary)]"
            >
              暂无已发布报表，请先在报表模板中发布
            </span>
          </div>

          <template v-for="field in filterFields" :key="field.field">
            <div v-if="filterControlType(field) === 'date'" class="flex min-w-40 flex-col gap-1">
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
            <div
              v-else-if="filterControlType(field) === 'month'"
              class="flex min-w-40 flex-col gap-1"
            >
              <span class="text-xs text-[var(--el-text-color-secondary)]">
                {{ field.label ?? field.field }}
              </span>
              <ElDatePicker
                v-model="filterValues[field.field!]"
                type="month"
                value-format="YYYY-MM"
                class="!w-full"
                clearable
              />
            </div>
            <div
              v-else-if="filterControlType(field) === 'daterange'"
              class="flex min-w-72 flex-col gap-1"
            >
              <span class="text-xs text-[var(--el-text-color-secondary)]">
                {{ field.label ?? field.field }}
              </span>
              <ElDatePicker
                v-model="filterValues[field.field!]"
                type="daterange"
                value-format="YYYY-MM-DD"
                class="!w-full"
                range-separator="至"
                start-placeholder="开始"
                end-placeholder="结束"
                clearable
              />
            </div>
            <div
              v-else-if="filterControlType(field) === 'select'"
              class="flex min-w-44 flex-col gap-1"
            >
              <span class="text-xs text-[var(--el-text-color-secondary)]">
                {{ field.label ?? field.field }}
              </span>
              <ElSelect
                v-model="filterValues[field.field!]"
                class="!w-full"
                clearable
                filterable
                placeholder="请选择"
              >
                <ElOption
                  v-for="opt in resolveFilterSelectOptions(field)"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </ElSelect>
            </div>
            <div v-else class="flex min-w-44 flex-col gap-1">
              <span class="text-xs text-[var(--el-text-color-secondary)]">
                {{ field.label ?? field.field }}
              </span>
              <ElInput
                :model-value="String(filterValues[field.field!] ?? '')"
                clearable
                @update:model-value="
                  (v: string) => {
                    if (field.field) filterValues[field.field] = v;
                  }
                "
              />
            </div>
          </template>

          <div class="flex flex-wrap gap-2 pb-0.5">
            <ElButton type="primary" :loading="queryLoading" @click="handleQuery">查询</ElButton>
            <ElButton :loading="exportLoading" @click="handleExportExcel">导出 Excel</ElButton>
            <ElButton
              v-if="columnCustomizeEnabled"
              :loading="columnLoading"
              @click="openColumnSettings"
            >
              列设置
            </ElButton>
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

    <ElDialog v-model="columnDialogVisible" title="列设置" width="520px" destroy-on-close>
      <ElTable :data="columnDraft" border size="small" max-height="360">
        <ElTableColumn label="显示" width="72" align="center">
          <template #default="{ row }">
            <ElCheckbox v-model="row.visible" />
          </template>
        </ElTableColumn>
        <ElTableColumn prop="label" label="列名" min-width="160">
          <template #default="{ row }">
            {{ row.label ?? row.field }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="顺序" width="120" align="center">
          <template #default="{ $index }">
            <ElButton link size="small" :disabled="$index === 0" @click="moveColumn($index, -1)">
              上移
            </ElButton>
            <ElButton
              link
              size="small"
              :disabled="$index === columnDraft.length - 1"
              @click="moveColumn($index, 1)"
            >
              下移
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <template #footer>
        <ElButton @click="handleResetColumnSettings" :loading="columnSaving">恢复默认</ElButton>
        <ElButton @click="columnDialogVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="columnSaving" @click="handleSaveColumnSettings">
          保存
        </ElButton>
      </template>
    </ElDialog>
  </Page>
</template>
