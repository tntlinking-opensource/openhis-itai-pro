<script setup lang="ts">
import type {
  ReportColumnDto,
  ReportDefinitionItemDto,
  ReportFilterFieldDto,
  ReportHeaderLineDto,
  ReportSqlMetadataTableDto,
  ReportSqlPresetItemDto,
} from '#/api/report/report';

import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import {
  ElAlert,
  ElButton,
  ElCard,
  ElCollapse,
  ElCollapseItem,
  ElDatePicker,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElOptionGroup,
  ElSelect,
  ElStep,
  ElSteps,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  applySqlPresetApi,
  buildReportHeaderJson,
  getPlatformBaselineApi,
  getReportDefinitionApi,
  listSqlMetadataTablesApi,
  listSqlPresetsApi,
  parseReportHeaderConfig,
  publishReportDefinitionApi,
  parseReportHeaderFooter,
  resolveDefinitionFilters,
  resolveDefinitionFooter,
  resolveDefinitionHeader,
  reportQueryPageApi,
  saveReportDesignApi,
} from '#/api/report/report';
import ReportColumnEditor from '#/components/report/designer/ReportColumnEditor.vue';
import ReportDesignPreviewPanel from '#/components/report/designer/ReportDesignPreviewPanel.vue';
import ReportFilterBuilder from '#/components/report/designer/ReportFilterBuilder.vue';
import ReportSqlBuilder from '#/components/report/designer/ReportSqlBuilder.vue';
import { useRequest } from '#/hooks/use-request';
import {
  DESIGN_STEPS,
  buildPreviewParams,
  defaultPreviewDateRange,
  extractSqlParams,
  parseFiltersJson,
  serializeColumnsJson,
  serializeFiltersJson,
  normalizeColumn,
} from '#/utils/report/report-design-form';
import {
  groupPresetsByPriority,
  loadDesignerStep,
  saveDesignerStep,
} from '#/utils/report/report-builder';

defineOptions({
  name: 'WhaleReportDesigner',
});

const route = useRoute();
const router = useRouter();
const reportId = computed(() => String(route.query.id ?? '').trim());
const definition = ref<ReportDefinitionItemDto | null>(null);
const sqlPresets = ref<ReportSqlPresetItemDto[]>([]);
const sqlMetadataTables = ref<ReportSqlMetadataTableDto[]>([]);
const activeStep = ref(0);
const advancedSqlOpen = ref<string[]>([]);
const publishDialogVisible = ref(false);
const baselineDialogVisible = ref(false);
const baselineLoading = ref(false);
const platformBaseline = ref<ReportDefinitionItemDto | null>(null);
const previewSucceeded = ref(false);
const autoSaveHint = ref('');
const selectedPresetCode = ref('');

const previewRows = ref<Array<Record<string, unknown>>>([]);
const previewColumns = ref<Array<{ field?: string; label?: string }>>([]);
const previewTotal = ref(0);
const previewRange = reactive(defaultPreviewDateRange());

const form = reactive({
  name: '',
  querySql: '',
  headerTitle: '',
  headerSubtitle: '',
  headerLines: [] as ReportHeaderLineDto[],
  footerNotes: '',
  footerLinesText: '',
  filters: [] as ReportFilterFieldDto[],
  columns: [] as ReportColumnDto[],
});

const groupedPresets = computed(() => groupPresetsByPriority(sqlPresets.value));

const baselineDiffs = computed(() => {
  const baseline = platformBaseline.value;
  if (!baseline) {
    return [];
  }
  const diffs: Array<{ field: string; label: string; current: string; baseline: string }> = [];
  const pushDiff = (field: string, label: string, current?: string, base?: string) => {
    const left = (current ?? '').trim();
    const right = (base ?? '').trim();
    if (left !== right) {
      diffs.push({ field, label, current: left || '（空）', baseline: right || '（空）' });
    }
  };
  pushDiff('querySql', '查询 SQL', form.querySql, baseline.querySql);
  pushDiff(
    'filterJson',
    '筛选器',
    serializeFiltersJson(form.filters),
    serializeFiltersJson(baseline.filters ?? []),
  );
  pushDiff(
    'columns',
    '列定义',
    serializeColumnsJson(form.columns),
    serializeColumnsJson((baseline.columns ?? []).map((col) => normalizeColumn(col))),
  );
  pushDiff('headerJson', '表头', buildHeaderJson(), buildReportHeaderJson(baseline.header ?? {}));
  pushDiff('footerJson', '表底', buildFooterJson(), JSON.stringify(baseline.footer ?? {}));
  return diffs;
});
const isSqlSource = computed(() => definition.value?.sourceType === 'sql');

watch(activeStep, (step) => {
  if (reportId.value) {
    saveDesignerStep(reportId.value, step);
  }
});

const sqlParams = computed(() => extractSqlParams(form.querySql));

const statusTag = computed(() =>
  definition.value?.status === 'published'
    ? { type: 'success' as const, text: '已发布' }
    : { type: 'info' as const, text: '草稿' },
);

const publishChecks = computed(() => {
  const hasName = Boolean(form.name.trim());
  const hasTitle = Boolean(form.headerTitle.trim());
  const hasDateFilter = form.filters.some((f) => f.field === 'dateFrom' || f.field === 'dateTo');
  const hasSql = !isSqlSource.value || Boolean(form.querySql.trim());
  return [
    { key: 'name', label: '报表名称已填写', ok: hasName },
    { key: 'title', label: '表头标题已填写', ok: hasTitle },
    { key: 'date', label: '已配置日期筛选', ok: hasDateFilter },
    { key: 'sql', label: isSqlSource.value ? 'SQL 已配置' : '数据来源：出厂 Loader', ok: hasSql },
    { key: 'preview', label: '试查成功至少一次', ok: previewSucceeded.value },
  ];
});

const canPublish = computed(() => publishChecks.value.every((item) => item.ok));

const { run: runLoad, loading: loadLoading } = useRequest(getReportDefinitionApi, {
  manual: true,
  showErrorMessage: false,
});
const { run: runSave, loading: saveLoading } = useRequest(saveReportDesignApi, {
  manual: true,
  showErrorMessage: false,
});
const { run: runPublish, loading: publishLoading } = useRequest(publishReportDefinitionApi, {
  manual: true,
  showErrorMessage: false,
});
const { run: runPreview, loading: previewLoading } = useRequest(reportQueryPageApi, {
  manual: true,
  showErrorMessage: false,
});
const { run: runApplyPreset, loading: presetLoading } = useRequest(applySqlPresetApi, {
  manual: true,
  showErrorMessage: false,
});

let autoSaveTimer: ReturnType<typeof setTimeout> | undefined;
let dirtySinceSave = false;
let suppressFormWatch = false;
let lastSavedSnapshot = '';
const formReady = ref(false);

function snapshotForm(): string {
  const payload = buildSavePayload();
  return payload ? JSON.stringify(payload) : '';
}

function applyDefinition(row: ReportDefinitionItemDto) {
  suppressFormWatch = true;
  definition.value = row;
  form.name = row.name ?? '';
  form.querySql = row.querySql ?? '';
  form.filters = resolveDefinitionFilters(row);
  form.columns = (row.columns ?? []).map((col) => normalizeColumn(col));

  const header = resolveDefinitionHeader(row);
  form.headerTitle = header.title ?? '';
  form.headerSubtitle = header.subtitle ?? '';
  form.headerLines = header.lines?.length
    ? header.lines.map((line) => ({ ...line }))
    : [{ type: 'split', left: '编制单位：________', right: '打印日期：{{dateRange}}' }];

  const footer = resolveDefinitionFooter(row);
  form.footerNotes = String(footer.notes ?? '');
  const lines = Array.isArray(footer.lines) ? footer.lines : [];
  form.footerLinesText = lines.map((line) => String(line)).join('\n');
  dirtySinceSave = false;
  lastSavedSnapshot = snapshotForm();
  suppressFormWatch = false;
}

async function loadDefinition() {
  if (route.path !== '/reports/designer') {
    return;
  }
  if (!reportId.value) {
    ElMessage.warning('缺少报表 ID，请从报表模板进入');
    return;
  }
  formReady.value = false;
  const row = await runLoad(reportId.value);
  applyDefinition(row);
  const savedStep = loadDesignerStep(reportId.value);
  if (savedStep != null && savedStep < DESIGN_STEPS.length) {
    activeStep.value = savedStep;
  }
  formReady.value = true;
}

async function loadPresets() {
  const rows = await listSqlPresetsApi();
  sqlPresets.value = Array.isArray(rows) ? rows : [];
}

async function loadMetadataTables() {
  const rows = await listSqlMetadataTablesApi();
  sqlMetadataTables.value = Array.isArray(rows) ? rows : [];
}

function buildSavePayload() {
  if (!definition.value?.id) {
    return null;
  }
  return {
    id: definition.value.id,
    name: form.name?.trim() || undefined,
    querySql: isSqlSource.value ? form.querySql : undefined,
    headerJson: buildHeaderJson(),
    footerJson: buildFooterJson(),
    filterJson: serializeFiltersJson(form.filters),
    defaultColumnsJson: serializeColumnsJson(form.columns),
  };
}

function buildHeaderJson() {
  return buildReportHeaderJson({
    title: form.headerTitle,
    subtitle: form.headerSubtitle,
    lines: form.headerLines,
  });
}

function buildFooterJson() {
  const lines = form.footerLinesText
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean);
  return JSON.stringify({ notes: form.footerNotes, lines });
}

async function handleSave(showToast = true) {
  const payload = buildSavePayload();
  if (!payload) {
    return;
  }
  const row = await runSave(payload);
  applyDefinition(row);
  autoSaveHint.value = '已保存';
  if (showToast) {
    ElMessage.success('已保存');
  }
}

function scheduleAutoSave() {
  if (suppressFormWatch || !formReady.value || !definition.value?.id) {
    return;
  }
  const snap = snapshotForm();
  if (!snap || snap === lastSavedSnapshot) {
    return;
  }
  dirtySinceSave = true;
  autoSaveHint.value = '有未保存更改…';
  if (autoSaveTimer) {
    clearTimeout(autoSaveTimer);
  }
  autoSaveTimer = setTimeout(() => {
    if (dirtySinceSave && definition.value?.id && snapshotForm() !== lastSavedSnapshot) {
      void handleSave(false).catch(() => {
        autoSaveHint.value = '自动保存失败';
      });
    }
  }, 2000);
}

function addHeaderLine(type: ReportHeaderLineDto['type'] = 'text') {
  if (type === 'split') {
    form.headerLines.push({ type: 'split', left: '', right: '' });
  } else {
    form.headerLines.push({ type: 'text', text: '', align: 'left' });
  }
  scheduleAutoSave();
}

function removeHeaderLine(index: number) {
  form.headerLines.splice(index, 1);
  scheduleAutoSave();
}

function moveHeaderLine(index: number, delta: number) {
  const target = index + delta;
  if (target < 0 || target >= form.headerLines.length) {
    return;
  }
  const [item] = form.headerLines.splice(index, 1);
  if (item) {
    form.headerLines.splice(target, 0, item);
  }
  scheduleAutoSave();
}

function insertPlaceholder(target: 'headerTitle' | 'headerSubtitle', token: string) {
  form[target] = `${form[target]}${token}`;
  scheduleAutoSave();
}

async function handlePreview() {
  const code = definition.value?.reportCode?.trim();
  if (!code) {
    return;
  }
  await handleSave(false);
  const params = buildPreviewParams(serializeFiltersJson(form.filters), previewRange);
  const result = await runPreview({
    reportCode: code,
    pageNo: 1,
    pageSize: 50,
    dateFrom: params.dateFrom,
    dateTo: params.dateTo,
    keyword: params.keyword || undefined,
    params,
    designPreview: true,
  });
  previewRows.value = result?.rows ?? [];
  previewColumns.value = (result?.columns ?? []).map((col) => ({
    field: col.field,
    label: col.label ?? col.field,
  }));
  previewTotal.value = result?.totalCount ?? previewRows.value.length;
  previewSucceeded.value = true;
  ElMessage.success(`试查完成，共 ${previewTotal.value} 行`);
}

function importColumnsFromPreview() {
  if (!previewColumns.value.length) {
    ElMessage.warning('请先试查');
    return;
  }
  form.columns = previewColumns.value.map((col) => ({
    field: col.field,
    label: col.label ?? col.field,
    align: 'left',
    width: 120,
  }));
  scheduleAutoSave();
  ElMessage.success('已从试查结果导入列定义');
}

async function handleApplyPreset() {
  if (!definition.value?.id || !selectedPresetCode.value) {
    ElMessage.warning('请选择预制模板');
    return;
  }
  await ElMessageBox.confirm('将覆盖当前 SQL、筛选器、列与表头表底，是否继续？', '应用预制模板', {
    type: 'warning',
  });
  const row = await runApplyPreset({
    id: definition.value.id,
    presetReportCode: selectedPresetCode.value,
  });
  formReady.value = false;
  applyDefinition(row);
  formReady.value = true;
  previewSucceeded.value = false;
  ElMessage.success('已应用预制模板');
}

function handleSqlBuilderApply(payload: {
  sql: string;
  columns: ReportColumnDto[];
  filters: ReportFilterFieldDto[];
}) {
  form.querySql = payload.sql;
  if (payload.columns.length) {
    form.columns = payload.columns.map((col) => normalizeColumn(col));
  }
  if (payload.filters.length) {
    form.filters = payload.filters;
  }
  advancedSqlOpen.value = ['sql'];
  previewSucceeded.value = false;
  scheduleAutoSave();
  ElMessage.success('已生成 SQL，可在高级区微调');
}

async function openBaselineDialog() {
  if (!definition.value?.id) {
    return;
  }
  baselineDialogVisible.value = true;
  baselineLoading.value = true;
  platformBaseline.value = null;
  try {
    const row = await getPlatformBaselineApi(definition.value.id);
    platformBaseline.value = row ?? null;
    if (!row) {
      ElMessage.info('未找到同编码的平台预制基线');
    }
  } finally {
    baselineLoading.value = false;
  }
}

function applyBaselineToForm() {
  const baseline = platformBaseline.value;
  if (!baseline) {
    return;
  }
  form.querySql = baseline.querySql ?? form.querySql;
  form.filters = resolveDefinitionFilters(baseline);
  form.columns = (baseline.columns ?? []).map((col) => normalizeColumn(col));

  const header = resolveDefinitionHeader(baseline);
  form.headerTitle = header.title ?? form.headerTitle;
  form.headerSubtitle = header.subtitle ?? form.headerSubtitle;
  if (header.lines?.length) {
    form.headerLines = header.lines.map((line) => ({ ...line }));
  }

  const footer = resolveDefinitionFooter(baseline);
  form.footerNotes = String(footer.notes ?? form.footerNotes);
  const lines = Array.isArray(footer.lines) ? footer.lines : [];
  if (lines.length) {
    form.footerLinesText = lines.map((line) => String(line)).join('\n');
  }

  previewSucceeded.value = false;
  scheduleAutoSave();
  baselineDialogVisible.value = false;
  ElMessage.success('已恢复平台预制内容到表单，请保存');
}

async function confirmRestoreBaseline() {
  if (!platformBaseline.value) {
    return;
  }
  await ElMessageBox.confirm('将平台预制内容写入表单（未保存），是否继续？', '恢复平台预制', {
    type: 'warning',
  });
  applyBaselineToForm();
}

function openPublishDialog() {
  publishDialogVisible.value = true;
}

async function confirmPublish() {
  if (!definition.value?.id) {
    return;
  }
  if (!canPublish.value) {
    ElMessage.warning('请先完成发布前检查项');
    return;
  }
  await handleSave(false);
  await runPublish(definition.value.id);
  publishDialogVisible.value = false;
  ElMessage.success('已发布，可在报表中心使用');
  await loadDefinition();
}

function goBack() {
  void router.push('/reports/report-template');
}

watch(
  () => [
    form.name,
    form.querySql,
    form.headerTitle,
    form.headerSubtitle,
    form.footerNotes,
    form.footerLinesText,
    JSON.stringify(form.filters),
    JSON.stringify(form.columns),
    JSON.stringify(form.headerLines),
  ],
  () => {
    scheduleAutoSave();
  },
);

watch(
  () => route.query.id,
  (id, prevId) => {
    if (!id || id === prevId) {
      return;
    }
    previewSucceeded.value = false;
    void loadDefinition();
  },
);

onMounted(() => {
  void loadPresets();
  void loadMetadataTables();
  if (reportId.value) {
    void loadDefinition();
  } else if (route.path === '/reports/designer') {
    ElMessage.warning('缺少报表 ID，请从报表模板进入');
    void router.replace('/reports/report-template');
  }
});
</script>

<template>
  <Page auto-content-height>
    <div v-loading="loadLoading" class="flex h-full min-h-0 flex-col gap-2 p-2">
      <ElCard shadow="never" class="shrink-0">
        <div class="flex flex-wrap items-center gap-2">
          <ElButton @click="goBack">返回模板列表</ElButton>
          <span class="text-base font-medium">{{ definition?.name ?? '报表设计' }}</span>
          <span class="font-mono text-sm text-[var(--el-text-color-secondary)]">
            ({{ definition?.reportCode }})
          </span>
          <ElTag size="small" :type="statusTag.type">{{ statusTag.text }}</ElTag>
          <ElTag v-if="!isSqlSource" size="small" type="info">出厂 Loader</ElTag>
          <span class="text-xs text-[var(--el-text-color-secondary)]">{{ autoSaveHint }}</span>
          <div class="flex-1" />
          <ElButton v-if="isSqlSource" @click="openBaselineDialog">平台预制对比</ElButton>
          <ElButton :loading="previewLoading" @click="handlePreview">试查</ElButton>
          <ElButton type="primary" :loading="saveLoading" @click="handleSave()">保存</ElButton>
          <ElButton type="success" :loading="publishLoading" @click="openPublishDialog"
            >发布</ElButton
          >
        </div>
      </ElCard>

      <div class="grid min-h-0 flex-1 gap-2 lg:grid-cols-[minmax(0,1fr)_minmax(320px,42%)]">
        <ElCard shadow="never" class="min-h-0" body-class="h-full overflow-auto p-3">
          <ElSteps :active="activeStep" finish-status="success" simple class="mb-4">
            <ElStep
              v-for="(step, index) in DESIGN_STEPS"
              :key="step.key"
              :title="step.title"
              class="cursor-pointer"
              @click="activeStep = index"
            />
          </ElSteps>

          <div v-show="activeStep === 0" class="space-y-4">
            <ElForm label-width="96px" class="max-w-xl">
              <ElFormItem label="报表名称">
                <ElInput v-model="form.name" placeholder="显示名称" />
              </ElFormItem>
            </ElForm>

            <ElAlert
              v-if="!isSqlSource"
              type="info"
              :closable="false"
              show-icon
              title="数据来源：出厂 Java Loader"
              description="此类报表不可修改 SQL，可配置表头、筛选器、列与 PDF 导出。"
            />

            <div v-if="isSqlSource" class="space-y-3">
              <div class="space-y-2">
                <div class="text-sm font-medium">从预制模板导入（可选）</div>
                <div class="flex flex-wrap gap-2">
                  <ElSelect
                    v-model="selectedPresetCode"
                    filterable
                    clearable
                    placeholder="选择 V20 预制 SQL 报表"
                    class="min-w-[280px]"
                  >
                    <ElOptionGroup
                      v-for="group in groupedPresets"
                      :key="group.priority"
                      :label="group.label"
                    >
                      <ElOption
                        v-for="preset in group.items"
                        :key="preset.reportCode"
                        :label="`${preset.name} (${preset.reportCode})`"
                        :value="preset.reportCode ?? ''"
                      />
                    </ElOptionGroup>
                  </ElSelect>
                  <ElButton :loading="presetLoading" @click="handleApplyPreset">应用</ElButton>
                </div>
              </div>

              <ReportSqlBuilder :tables="sqlMetadataTables" @apply="handleSqlBuilderApply" />
            </div>
          </div>

          <div v-show="activeStep === 1">
            <ReportFilterBuilder v-model="form.filters" :sql-params="sqlParams" />
          </div>

          <div v-show="activeStep === 2">
            <ReportColumnEditor
              v-model="form.columns"
              @import-from-preview="importColumnsFromPreview"
            />
          </div>

          <div v-show="activeStep === 3" class="space-y-4">
            <ElForm label-width="96px">
              <ElFormItem label="标题">
                <div class="flex w-full gap-2">
                  <ElInput v-model="form.headerTitle" placeholder="报表主标题" />
                  <ElButton @click="insertPlaceholder('headerTitle', '{{reportTitle}}')"
                    >插入标题</ElButton
                  >
                </div>
              </ElFormItem>
              <ElFormItem label="副标题">
                <div class="flex w-full gap-2">
                  <ElInput v-model="form.headerSubtitle" placeholder="可用 {{dateRange}}" />
                  <ElButton @click="insertPlaceholder('headerSubtitle', '{{dateRange}}')"
                    >插入日期</ElButton
                  >
                </div>
              </ElFormItem>
              <ElFormItem label="动态行">
                <div class="w-full space-y-2">
                  <div class="flex gap-2">
                    <ElButton size="small" @click="addHeaderLine('text')">文本行</ElButton>
                    <ElButton size="small" @click="addHeaderLine('split')">左右分栏</ElButton>
                  </div>
                  <ElTable :data="form.headerLines" border size="small">
                    <ElTableColumn label="类型" width="88">
                      <template #default="{ row }">{{
                        row.type === 'split' ? '分栏' : '文本'
                      }}</template>
                    </ElTableColumn>
                    <ElTableColumn label="内容" min-width="280">
                      <template #default="{ row }">
                        <div v-if="row.type === 'split'" class="flex gap-2">
                          <ElInput v-model="row.left" />
                          <ElInput v-model="row.right" />
                        </div>
                        <ElInput v-else v-model="row.text" />
                      </template>
                    </ElTableColumn>
                    <ElTableColumn label="操作" width="140">
                      <template #default="{ $index }">
                        <ElButton
                          link
                          type="primary"
                          :disabled="$index === 0"
                          @click="moveHeaderLine($index, -1)"
                          >上</ElButton
                        >
                        <ElButton
                          link
                          type="primary"
                          :disabled="$index === form.headerLines.length - 1"
                          @click="moveHeaderLine($index, 1)"
                          >下</ElButton
                        >
                        <ElButton link type="danger" @click="removeHeaderLine($index)">删</ElButton>
                      </template>
                    </ElTableColumn>
                  </ElTable>
                </div>
              </ElFormItem>
              <ElFormItem label="脚注">
                <ElInput v-model="form.footerNotes" type="textarea" :rows="2" />
              </ElFormItem>
              <ElFormItem label="签章行">
                <ElInput
                  v-model="form.footerLinesText"
                  type="textarea"
                  :rows="3"
                  placeholder="每行一条"
                />
              </ElFormItem>
            </ElForm>
          </div>

          <ElCollapse v-if="isSqlSource" v-model="advancedSqlOpen" class="mt-4">
            <ElCollapseItem title="高级：查询 SQL" name="sql">
              <ElInput
                v-model="form.querySql"
                type="textarea"
                :rows="12"
                placeholder="SELECT ..."
              />
              <p class="mt-2 text-xs text-[var(--el-text-color-secondary)]">
                仅允许 SELECT；参数 :dateFrom、:dateTo、:keyword 及筛选器字段名。
              </p>
            </ElCollapseItem>
          </ElCollapse>

          <div class="mt-4 flex justify-between">
            <ElButton :disabled="activeStep === 0" @click="activeStep -= 1">上一步</ElButton>
            <ElButton :disabled="activeStep >= DESIGN_STEPS.length - 1" @click="activeStep += 1"
              >下一步</ElButton
            >
          </div>
        </ElCard>

        <ElCard shadow="never" class="min-h-0" body-class="h-full min-h-[360px] p-3">
          <div class="mb-2 flex flex-wrap items-center gap-2">
            <span class="text-sm font-medium">预览</span>
            <ElDatePicker
              v-model="previewRange.dateFrom"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="开始日期"
              size="small"
            />
            <span class="text-xs text-[var(--el-text-color-secondary)]">至</span>
            <ElDatePicker
              v-model="previewRange.dateTo"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="结束日期"
              size="small"
            />
          </div>
          <ReportDesignPreviewPanel
            :loading="previewLoading"
            :title="form.headerTitle"
            :subtitle="form.headerSubtitle"
            :header-lines="form.headerLines"
            :footer-notes="form.footerNotes"
            :footer-lines-text="form.footerLinesText"
            :columns="previewColumns.length ? previewColumns : form.columns"
            :rows="previewRows"
            :total-hint="
              previewRows.length ? `试查 ${previewRows.length} / ${previewTotal} 行` : ''
            "
          />
        </ElCard>
      </div>
    </div>

    <ElDialog v-model="publishDialogVisible" title="发布前检查" width="440px" align-center>
      <ul class="space-y-2">
        <li v-for="item in publishChecks" :key="item.key" class="flex items-center gap-2 text-sm">
          <ElTag size="small" :type="item.ok ? 'success' : 'warning'">{{
            item.ok ? '✓' : '!'
          }}</ElTag>
          <span>{{ item.label }}</span>
        </li>
      </ul>
      <template #footer>
        <ElButton @click="publishDialogVisible = false">取消</ElButton>
        <ElButton
          type="success"
          :loading="publishLoading"
          :disabled="!canPublish"
          @click="confirmPublish"
        >
          确认发布
        </ElButton>
      </template>
    </ElDialog>

    <ElDialog
      v-model="baselineDialogVisible"
      title="与平台预制对比"
      width="720px"
      align-center
      destroy-on-close
    >
      <div v-loading="baselineLoading">
        <ElAlert
          v-if="!baselineLoading && !platformBaseline"
          type="info"
          :closable="false"
          show-icon
          title="无平台基线"
          description="当前报表编码在平台租户（tenant=0）无对应预制，或预制无有效 SQL/表头。"
        />
        <template v-else-if="platformBaseline">
          <p class="mb-2 text-sm text-[var(--el-text-color-secondary)]">
            平台预制：{{ platformBaseline.name }}（{{ platformBaseline.reportCode }}）
          </p>
          <ElAlert
            v-if="baselineDiffs.length === 0"
            type="success"
            :closable="false"
            show-icon
            title="与平台预制一致"
          />
          <ElTable v-else :data="baselineDiffs" border size="small" max-height="360">
            <ElTableColumn prop="label" label="项" width="100" />
            <ElTableColumn prop="current" label="当前院点" min-width="200" show-overflow-tooltip />
            <ElTableColumn prop="baseline" label="平台预制" min-width="200" show-overflow-tooltip />
          </ElTable>
        </template>
      </div>
      <template #footer>
        <ElButton @click="baselineDialogVisible = false">关闭</ElButton>
        <ElButton type="warning" :disabled="!platformBaseline" @click="confirmRestoreBaseline">
          恢复平台预制
        </ElButton>
      </template>
    </ElDialog>
  </Page>
</template>
