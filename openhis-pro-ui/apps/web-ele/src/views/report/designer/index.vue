<script setup lang="ts">
import type { ReportDefinitionItemDto, ReportHeaderLineDto } from '#/api/report/report-query';

import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import {
  ElButton,
  ElCard,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
  ElSwitch,
  ElTabPane,
  ElTabs,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import {
  buildReportHeaderJson,
  getReportDefinitionApi,
  parseReportHeaderConfig,
  publishReportDefinitionApi,
  parseReportHeaderFooter,
  reportQueryPageApi,
  saveReportDesignApi,
} from '#/api/report/report-query';
import { useRequest } from '#/hooks/use-request';

defineOptions({
  name: 'WhaleReportDesigner',
});

const route = useRoute();
const router = useRouter();
const reportId = computed(() => String(route.query.id ?? '').trim());
const definition = ref<ReportDefinitionItemDto | null>(null);
const previewRows = ref<Array<Record<string, unknown>>>([]);
const previewColumns = ref<Array<{ field?: string; label?: string }>>([]);

const form = reactive({
  name: '',
  querySql: '',
  headerTitle: '',
  headerSubtitle: '',
  headerLines: [] as ReportHeaderLineDto[],
  footerNotes: '',
  footerLinesText: '',
  filterJson: '',
  columnsJson: '',
  exportPdfEnabled: false,
  pdfTemplateCode: '',
});

const isSqlReport = computed(() => definition.value?.sourceType === 'sql');

const { run: runLoad, loading: loadLoading } = useRequest(getReportDefinitionApi, { manual: true });
const { run: runSave, loading: saveLoading } = useRequest(saveReportDesignApi, { manual: true });
const { run: runPublish, loading: publishLoading } = useRequest(publishReportDefinitionApi, {
  manual: true,
});
const { run: runPreview, loading: previewLoading } = useRequest(reportQueryPageApi, {
  manual: true,
});

function applyDefinition(row: ReportDefinitionItemDto) {
  definition.value = row;
  form.name = row.name ?? '';
  form.querySql = row.querySql ?? '';
  form.filterJson = row.filterJson ?? '[]';
  form.exportPdfEnabled = Boolean(row.exportPdfEnabled);
  form.pdfTemplateCode = row.pdfTemplateCode ?? '';
  form.columnsJson = JSON.stringify(row.columns ?? [], null, 2);

  const header = parseReportHeaderConfig(row.headerJson);
  form.headerTitle = header.title ?? '';
  form.headerSubtitle = header.subtitle ?? '';
  form.headerLines = header.lines?.length
    ? header.lines.map((line) => ({ ...line }))
    : [{ type: 'split', left: '编制单位：________', right: '打印日期：{{dateRange}}' }];

  const footer = parseReportHeaderFooter(row.footerJson);
  form.footerNotes = String(footer.notes ?? '');
  const lines = Array.isArray(footer.lines) ? footer.lines : [];
  form.footerLinesText = lines.map((line) => String(line)).join('\n');
}

async function loadDefinition() {
  if (!reportId.value) {
    ElMessage.warning('缺少报表 ID，请从报表模板进入');
    return;
  }
  const row = await runLoad(reportId.value);
  applyDefinition(row);
}

function buildHeaderJson() {
  return buildReportHeaderJson({
    title: form.headerTitle,
    subtitle: form.headerSubtitle,
    lines: form.headerLines,
  });
}

function addHeaderLine(type: ReportHeaderLineDto['type'] = 'text') {
  if (type === 'split') {
    form.headerLines.push({ type: 'split', left: '', right: '' });
    return;
  }
  form.headerLines.push({ type: 'text', text: '', align: 'left' });
}

function removeHeaderLine(index: number) {
  form.headerLines.splice(index, 1);
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
}

function buildFooterJson() {
  const lines = form.footerLinesText
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean);
  return JSON.stringify({ notes: form.footerNotes, lines });
}

async function handleSave() {
  if (!definition.value?.id) {
    return;
  }
  await runSave({
    id: definition.value.id,
    name: form.name?.trim() || undefined,
    querySql: isSqlReport.value ? form.querySql : undefined,
    headerJson: buildHeaderJson(),
    footerJson: buildFooterJson(),
    filterJson: form.filterJson,
    defaultColumnsJson: form.columnsJson,
    exportPdfEnabled: form.exportPdfEnabled,
    pdfTemplateCode: form.pdfTemplateCode?.trim() || '',
  });
  ElMessage.success('已保存');
  await loadDefinition();
}

async function handlePreview() {
  const code = definition.value?.reportCode?.trim();
  if (!code) {
    return;
  }
  await handleSave();
  const result = await runPreview({
    reportCode: code,
    pageNo: 1,
    pageSize: 50,
    designPreview: true,
  });
  previewRows.value = result?.rows ?? [];
  previewColumns.value = (result?.columns ?? []).map((col) => ({
    field: col.field,
    label: col.label ?? col.field,
  }));
}

async function handlePublish() {
  if (!definition.value?.id) {
    return;
  }
  await handleSave();
  await runPublish(definition.value.id);
  ElMessage.success('已发布，可在报表中心使用');
  await loadDefinition();
}

function goBack() {
  void router.push('/reports/report-template');
}

watch(
  () => route.query.id,
  () => {
    void loadDefinition();
  },
);

onMounted(() => {
  void loadDefinition();
});
</script>

<template>
  <Page auto-content-height>
    <div v-loading="loadLoading" class="flex h-full min-h-0 flex-col gap-2 p-2">
      <ElCard shadow="never" class="shrink-0">
        <div class="flex flex-wrap items-center gap-2">
          <ElButton @click="goBack">返回模板列表</ElButton>
          <span class="text-base font-medium">
            {{ definition?.name ?? '报表设计' }}
            <span class="text-sm text-[var(--el-text-color-secondary)]">
              ({{ definition?.reportCode }})
            </span>
          </span>
          <div class="flex-1" />
          <ElButton :loading="previewLoading" @click="handlePreview">试查</ElButton>
          <ElButton type="primary" :loading="saveLoading" @click="handleSave">保存</ElButton>
          <ElButton type="success" :loading="publishLoading" @click="handlePublish">发布</ElButton>
        </div>
      </ElCard>

      <ElCard shadow="never" class="min-h-0 flex-1" body-class="h-full overflow-auto">
        <ElTabs>
          <ElTabPane label="查询 SQL">
            <p v-if="!isSqlReport" class="mb-2 text-sm text-[var(--el-text-color-secondary)]">
              出厂 Loader 报表数据来自 Java Loader，此处 SQL 仅对「SQL 报表」生效。
            </p>
            <ElInput
              v-model="form.querySql"
              type="textarea"
              :rows="14"
              :disabled="!isSqlReport"
              placeholder="SELECT ... WHERE apply_time >= :dateFrom AND apply_time <= :dateTo"
            />
            <p class="mt-2 text-xs text-[var(--el-text-color-secondary)]">
              仅允许 SELECT；可用参数 :dateFrom、:dateTo、:keyword 及筛选器自定义字段。
            </p>
          </ElTabPane>

          <ElTabPane label="表头">
            <ElForm label-width="96px" class="max-w-4xl">
              <ElFormItem label="标题">
                <ElInput v-model="form.headerTitle" placeholder="报表主标题" />
              </ElFormItem>
              <ElFormItem label="副标题">
                <ElInput
                  v-model="form.headerSubtitle"
                  placeholder="可用 {{dateRange}}、{{reportTitle}}、{{rowCount}}"
                />
              </ElFormItem>
              <ElFormItem label="动态行">
                <div class="w-full space-y-2">
                  <div class="flex flex-wrap gap-2">
                    <ElButton size="small" @click="addHeaderLine('text')">添加文本行</ElButton>
                    <ElButton size="small" @click="addHeaderLine('split')">添加左右分栏行</ElButton>
                  </div>
                  <ElTable
                    :data="form.headerLines"
                    border
                    size="small"
                    empty-text="暂无表头行，请添加"
                  >
                    <ElTableColumn label="类型" width="100">
                      <template #default="{ row }">
                        {{ row.type === 'split' ? '左右分栏' : '文本行' }}
                      </template>
                    </ElTableColumn>
                    <ElTableColumn label="内容" min-width="320">
                      <template #default="{ row }">
                        <div v-if="row.type === 'split'" class="flex gap-2">
                          <ElInput v-model="row.left" placeholder="左侧，如：编制单位：某某医院" />
                          <ElInput
                            v-model="row.right"
                            placeholder="右侧，如：统计日期：{{dateRange}}"
                          />
                        </div>
                        <ElInput v-else v-model="row.text" placeholder="单行文本" />
                      </template>
                    </ElTableColumn>
                    <ElTableColumn label="对齐" width="120">
                      <template #default="{ row }">
                        <ElSelect v-if="row.type !== 'split'" v-model="row.align" class="!w-full">
                          <ElOption label="左对齐" value="left" />
                          <ElOption label="居中" value="center" />
                          <ElOption label="右对齐" value="right" />
                        </ElSelect>
                        <span v-else class="text-xs text-[var(--el-text-color-secondary)]">—</span>
                      </template>
                    </ElTableColumn>
                    <ElTableColumn label="操作" width="160" fixed="right">
                      <template #default="{ $index }">
                        <ElButton
                          link
                          type="primary"
                          :disabled="$index === 0"
                          @click="moveHeaderLine($index, -1)"
                        >
                          上移
                        </ElButton>
                        <ElButton
                          link
                          type="primary"
                          :disabled="$index === form.headerLines.length - 1"
                          @click="moveHeaderLine($index, 1)"
                        >
                          下移
                        </ElButton>
                        <ElButton link type="danger" @click="removeHeaderLine($index)"
                          >删除</ElButton
                        >
                      </template>
                    </ElTableColumn>
                  </ElTable>
                  <p class="text-xs text-[var(--el-text-color-secondary)]">
                    占位符：{{ dateRange }}、{{ reportTitle }}、{{ reportCode }}、{{
                      rowCount
                    }}。可任意增删、排序表头行。
                  </p>
                </div>
              </ElFormItem>
            </ElForm>
          </ElTabPane>

          <ElTabPane label="表底">
            <ElForm label-width="96px" class="max-w-2xl">
              <ElFormItem label="脚注">
                <ElInput v-model="form.footerNotes" type="textarea" :rows="3" />
              </ElFormItem>
              <ElFormItem label="签章行">
                <ElInput
                  v-model="form.footerLinesText"
                  type="textarea"
                  :rows="4"
                  placeholder="每行一条，如：制表人：________"
                />
              </ElFormItem>
            </ElForm>
          </ElTabPane>

          <ElTabPane label="筛选器">
            <ElInput
              v-model="form.filterJson"
              type="textarea"
              :rows="12"
              placeholder='[{"field":"dateFrom","label":"开始日期","type":"date"}]'
            />
          </ElTabPane>

          <ElTabPane label="列定义">
            <ElInput
              v-model="form.columnsJson"
              type="textarea"
              :rows="12"
              placeholder='[{"field":"busNo","label":"单据号","align":"left","width":140}]'
            />
            <p class="mt-2 text-xs text-[var(--el-text-color-secondary)]">
              留空则按 SQL 结果集列名自动生成。
            </p>
          </ElTabPane>

          <ElTabPane label="导出 PDF">
            <ElForm label-width="108px" class="max-w-xl">
              <ElFormItem label="启用 PDF">
                <ElSwitch v-model="form.exportPdfEnabled" />
              </ElFormItem>
              <ElFormItem label="PDF 模板编码">
                <ElInput v-model="form.pdfTemplateCode" placeholder="关联打印模板 code" />
              </ElFormItem>
            </ElForm>
          </ElTabPane>
        </ElTabs>
      </ElCard>

      <ElCard v-if="previewRows.length" shadow="never" class="shrink-0">
        <div class="mb-2 text-sm font-medium">试查结果（前 50 行）</div>
        <ElTable :data="previewRows" stripe border max-height="280">
          <ElTableColumn
            v-for="col in previewColumns"
            :key="col.field"
            :prop="col.field"
            :label="col.label"
            min-width="120"
            show-overflow-tooltip
          />
        </ElTable>
      </ElCard>
    </div>
  </Page>
</template>
