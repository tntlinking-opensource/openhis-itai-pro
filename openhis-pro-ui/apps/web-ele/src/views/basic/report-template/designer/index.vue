<script setup lang="ts">
import type { ElementPackDto, PrintElementDefDto } from '#/api/report/report-design';
import type { TemplateDesignDto } from '#/api/report/report-template';
import type { LayoutDocument, LayoutItem, LayoutBox } from '#/types/report-layout';
import type { PreviewLayoutMetaDto } from '#/utils/report/report-preview';

import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';
import { printPdfFromBlob } from '@vben/utils';

import {
  ElAlert,
  ElButton,
  ElCollapse,
  ElCollapseItem,
  ElMessage,
  ElMessageBox,
  ElSpace,
  ElTag,
} from 'element-plus';

import {
  getElementPackApi,
  previewBase64ToBlob,
  reportDesignPreviewApi,
  validateLayoutApi,
} from '#/api/report/report-design';
import {
  forkTemplateDraftApi,
  getTemplateDesignApi,
  publishTemplateApi,
  saveTemplateDraftApi,
} from '#/api/report/report-template';
import CopyTemplateDialog from '../copy-template-dialog.vue';
import FloatingReportPreviewPanel from '#/components/report/designer/FloatingReportPreviewPanel.vue';
import FlowDesignCanvas from '#/components/report/designer/FlowDesignCanvas.vue';
import LayoutItemPanel from '#/components/report/designer/LayoutItemPanel.vue';
import LayoutItemPropertyPanel from '#/components/report/designer/LayoutItemPropertyPanel.vue';
import LayoutPagePanel from '#/components/report/designer/LayoutPagePanel.vue';
import ReportPreviewPanel from '#/components/report/ReportPreviewPanel.vue';
import ReportPdfPreviewDialog from '#/components/report/ReportPdfPreviewDialog.vue';
import { usePrintJob } from '#/composables/use-print-job';
import { useRequest } from '#/hooks/use-request';
import { normalizeLayoutDocument } from '#/types/report-layout';
import {
  boxFromDef,
  hasBoxGeometry,
  hydrateOverlayLayoutItems,
  isPositionedRenderType,
} from '#/utils/report/overlay-layout';
import { formatReportLayoutMode, formatReportStatus } from '../report-template.constants';

defineOptions({ name: 'ReportTemplateDesigner' });

const route = useRoute();
const router = useRouter();

const templateCode = computed(() => String(route.query.code ?? '').trim());
const design = ref<TemplateDesignDto | null>(null);
const layout = ref<LayoutDocument>(normalizeLayoutDocument());
const elementPack = ref<ElementPackDto | null>(null);
const selectedIndex = ref(0);
const dirty = ref(false);
const pdfBlob = ref<Blob | null>(null);
const previewMeta = ref<PreviewLayoutMetaDto | null>(null);
const previewDialogVisible = ref(false);
const copyDialogRef = ref<InstanceType<typeof CopyTemplateDialog> | null>(null);
const collapseActiveNames = ref(['page']);

const editable = computed(() => design.value?.editable === true);
const isPlatformTemplate = computed(() => design.value?.source === 'platform');
const isDraft = computed(() => design.value?.status === 'draft');
const layoutMode = computed(() => layout.value.page?.layoutMode ?? 'flow');
const isFlowLayout = computed(() => layoutMode.value === 'flow');
const pipeline = computed(() => layout.value.pipeline ?? design.value?.pipeline ?? 'page');
const packCode = computed(
  () => design.value?.datasetCode ?? elementPack.value?.packCode ?? 'outpatient_prescription',
);

const selectedItem = computed(() => layout.value.items?.[selectedIndex.value] ?? null);
const selectedDef = computed(() =>
  elementPack.value?.elements?.find((e) => e.elementCode === selectedItem.value?.elementCode),
);

const printOffsetMm = computed(() => layout.value.page?.overlay?.printOffsetMm);

const { run: runLoad, loading: loadLoading } = useRequest(getTemplateDesignApi, { manual: true });
const { run: runFork, loading: forkLoading } = useRequest(forkTemplateDraftApi, { manual: true });
const { run: runSave, loading: saveLoading } = useRequest(saveTemplateDraftApi, { manual: true });
const { run: runPublish, loading: publishLoading } = useRequest(publishTemplateApi, {
  manual: true,
});
const { run: runPreview, loading: previewLoading } = useRequest(reportDesignPreviewApi, {
  manual: true,
  onSuccess: (result) => {
    pdfBlob.value = previewBase64ToBlob(result);
    previewMeta.value = result.previewMeta ?? null;
  },
});
const { isPrinting, runPrintJob } = usePrintJob();

let previewTimer: ReturnType<typeof setTimeout> | undefined;

function applyDesign(dto: TemplateDesignDto) {
  design.value = dto;
  layout.value = normalizeLayoutDocument(
    (dto.layoutJson as LayoutDocument) ?? undefined,
    dto.pipeline ?? 'page',
  );
  dirty.value = false;
}

async function loadPack() {
  // overlay 需完整元素目录（含 flow 共用且带 box_json 的项），否则落格框无法解析 defaultBox
  const pack = await getElementPackApi({
    packCode: packCode.value,
    layoutMode: layoutMode.value === 'overlay' ? undefined : layoutMode.value,
  });
  elementPack.value = pack;
  if (layoutMode.value === 'overlay') {
    layout.value = hydrateOverlayLayoutItems(layout.value, pack.elements);
  }
}

async function loadAll() {
  if (!templateCode.value) {
    ElMessage.warning('缺少模板编码');
    return;
  }
  const dto = await runLoad(templateCode.value);
  applyDesign(dto);
  await loadPack();
  resetPreviewState();
  if (isFlowLayout.value) {
    schedulePreview(true);
  }
}

async function handleFork() {
  const dto = await runFork(templateCode.value);
  applyDesign(dto);
  ElMessage.success('已复制为院点草稿，可编辑');
  await loadPack();
  resetPreviewState();
  if (isFlowLayout.value) {
    schedulePreview(true);
  }
}

function resetPreviewState() {
  pdfBlob.value = null;
  previewMeta.value = null;
}

function serializeLayout() {
  return JSON.stringify(layout.value);
}

function markDirty() {
  dirty.value = true;
  if (isFlowLayout.value) {
    schedulePreview();
  }
}

function schedulePreview(immediate = false) {
  if (!isFlowLayout.value) {
    return;
  }
  if (previewTimer) {
    clearTimeout(previewTimer);
  }
  const delay = immediate ? 0 : 700;
  previewTimer = setTimeout(() => {
    void runPreviewNow();
  }, delay);
}

async function runPreviewNow(options?: { openDialog?: boolean }) {
  await runPreview({
    templateCode: templateCode.value || undefined,
    packCode: packCode.value,
    layoutJson: serializeLayout(),
    useSampleData: true,
  });
  if (options?.openDialog && pdfBlob.value) {
    previewDialogVisible.value = true;
  }
}

async function handlePreview() {
  await runPreviewNow({ openDialog: true });
}

async function handlePreviewDialogRefresh() {
  await runPreviewNow();
}

async function handleTestPrint() {
  if (previewLoading.value || isPrinting.value) {
    return;
  }
  if (!pdfBlob.value || dirty.value) {
    await runPreviewNow();
  }
  if (!pdfBlob.value) {
    ElMessage.error('预览生成失败，无法试打');
    return;
  }
  await runPrintJob({
    pdfBlob: pdfBlob.value,
    jobName: `${templateCode.value || 'report-design'}-test`,
    onFallbackBrowser: printPdfFromBlob,
  });
}

async function handleSave() {
  if (!editable.value || !design.value?.id) {
    ElMessage.warning('当前不可保存');
    return;
  }
  const validation = await validateLayoutApi({
    packCode: packCode.value,
    layoutJson: serializeLayout(),
    forPublish: !isDraft.value,
  });
  if (!validation.valid) {
    ElMessage.error(validation.errors?.join('；') ?? '校验失败');
    return;
  }
  const dto = await runSave(design.value.id, serializeLayout());
  applyDesign(dto);
  ElMessage.success(isDraft.value ? '草稿已保存' : '已保存并生效');
}

async function handlePublish() {
  if (!editable.value || !design.value?.id) {
    return;
  }
  const validation = await validateLayoutApi({
    packCode: packCode.value,
    layoutJson: serializeLayout(),
    forPublish: true,
  });
  if (!validation.valid) {
    ElMessage.error(validation.errors?.join('；') ?? '校验未通过');
    return;
  }
  await ElMessageBox.confirm('发布后业务打印将使用此版本，确认发布？', '发布模板', {
    type: 'warning',
  });
  if (dirty.value) {
    await runSave(design.value.id, serializeLayout());
  }
  const dto = await runPublish(design.value.id);
  applyDesign(dto);
  ElMessage.success('发布成功');
}

function goBack() {
  router.push('/basic/report-template');
}

function openCopyAsNew() {
  if (!templateCode.value) {
    return;
  }
  copyDialogRef.value?.open({
    code: templateCode.value,
    name: design.value?.name,
    pipeline: pipeline.value,
    layoutMode: layoutMode.value,
  });
}

function onCopyAsNewSuccess(newCode: string) {
  ElMessage.success('新模板已创建');
  router.push({
    path: '/basic/report-template/designer',
    query: { code: newCode },
  });
}

function onItemsChange(items: LayoutItem[]) {
  layout.value = { ...layout.value, items };
  if (selectedIndex.value >= items.length) {
    selectedIndex.value = Math.max(0, items.length - 1);
  }
  markDirty();
}

function onAddElement(code: string, insertIndex?: number) {
  const def = elementPack.value?.elements?.find((e) => e.elementCode === code);
  const items = [...(layout.value.items ?? [])];
  const existingIdx = items.findIndex((i) => i.elementCode === code);
  if (existingIdx >= 0) {
    const existing = items[existingIdx]!;
    if (existing.visible === false) {
      const restored: LayoutItem = { ...existing, visible: true };
      if (layoutMode.value === 'overlay' && !hasBoxGeometry(restored.box)) {
        const fromDef = boxFromDef(def);
        if (fromDef) {
          restored.box = fromDef;
        }
      }
      items[existingIdx] = restored;
      onItemsChange(items);
      selectedIndex.value = existingIdx;
      return;
    }
    selectedIndex.value = existingIdx;
    return;
  }

  const item: LayoutItem = {
    elementCode: code,
    visible: true,
  };
  if (layoutMode.value === 'overlay' || isPositionedRenderType(def?.renderType)) {
    const fromDef = boxFromDef(def);
    if (fromDef) {
      item.box = fromDef;
    }
  }
  const at = insertIndex == null ? items.length : Math.max(0, Math.min(insertIndex, items.length));
  items.splice(at, 0, item);
  onItemsChange(items);
  selectedIndex.value = at;
}

function patchSelected(patch: Partial<LayoutItem>) {
  const items = [...(layout.value.items ?? [])];
  const idx = selectedIndex.value;
  if (!items[idx]) {
    return;
  }
  items[idx] = { ...items[idx], ...patch };
  onItemsChange(items);
}

function patchPage(patch: Partial<NonNullable<LayoutDocument['page']>>) {
  const nextPage = { ...layout.value.page, ...patch } as NonNullable<LayoutDocument['page']>;
  for (const key of Object.keys(patch) as (keyof typeof patch)[]) {
    if (patch[key] === undefined) {
      delete nextPage[key];
    }
  }
  layout.value = {
    ...layout.value,
    page: nextPage,
  };
  markDirty();
}

function patchOverlay(patch: Partial<NonNullable<LayoutDocument['page']>['overlay']>) {
  layout.value = {
    ...layout.value,
    page: {
      ...layout.value.page,
      overlay: { ...layout.value.page?.overlay, ...patch },
    },
  };
  markDirty();
}

function onOverlaySelect(elementCode: string) {
  const idx = layout.value.items?.findIndex((i) => i.elementCode === elementCode) ?? -1;
  if (idx >= 0) {
    selectedIndex.value = idx;
  }
}

function onOverlayBoxChange(payload: { elementCode: string; box: LayoutBox }) {
  const idx = layout.value.items?.findIndex((i) => i.elementCode === payload.elementCode) ?? -1;
  if (idx < 0) {
    return;
  }
  const items = [...(layout.value.items ?? [])];
  items[idx] = { ...items[idx], box: payload.box };
  selectedIndex.value = idx;
  onItemsChange(items);
}

onMounted(() => {
  void loadAll();
});

onBeforeUnmount(() => {
  if (previewTimer) {
    clearTimeout(previewTimer);
  }
});

watch(templateCode, (code, prev) => {
  if (code && code !== prev) {
    void loadAll();
  }
});
</script>

<template>
  <Page auto-content-height>
    <div v-loading="loadLoading" class="report-designer">
      <header class="report-designer__header">
        <ElSpace wrap>
          <ElButton @click="goBack">返回列表</ElButton>
          <strong>{{ design?.name ?? templateCode }}</strong>
          <ElTag size="small">{{ design?.code }}</ElTag>
          <ElTag size="small" type="info">{{ formatReportLayoutMode(layoutMode) }}</ElTag>
          <ElTag size="small"
            >{{ formatReportStatus(design?.status) }} v{{ design?.version }}</ElTag
          >
        </ElSpace>
        <ElSpace wrap>
          <ElButton @click="openCopyAsNew">复制为新模板</ElButton>
          <ElButton
            v-if="isPlatformTemplate"
            type="primary"
            :loading="forkLoading"
            @click="handleFork"
          >
            复制为草稿
          </ElButton>
          <template v-if="!isFlowLayout">
            <ElButton :loading="previewLoading" @click="handlePreview">预览</ElButton>
            <ElButton :loading="previewLoading || isPrinting" @click="handleTestPrint">
              试打
            </ElButton>
          </template>
          <ElButton v-else :loading="previewLoading" @click="runPreviewNow">试打</ElButton>
          <ElButton
            v-if="editable"
            type="primary"
            plain
            :loading="saveLoading"
            :disabled="!dirty"
            @click="handleSave"
          >
            {{ isDraft ? '保存草稿' : '保存' }}
          </ElButton>
          <ElButton
            v-if="editable && isDraft"
            type="primary"
            :loading="publishLoading"
            @click="handlePublish"
          >
            发布
          </ElButton>
        </ElSpace>
      </header>

      <ElAlert
        v-if="isPlatformTemplate"
        type="info"
        :closable="false"
        show-icon
        class="report-designer__alert"
        title="当前为出厂模板。可「复制为草稿」同编码定制，或「复制为新模板」选择 flow/overlay 与纸张管线后独立维护。"
      />

      <div class="report-designer__body">
        <aside class="report-designer__left">
          <h4 class="report-designer__section-title">排版项</h4>
          <LayoutItemPanel
            :items="layout.items ?? []"
            :elements="elementPack?.elements ?? []"
            :selected-index="selectedIndex"
            :editable="editable"
            :layout-mode="layoutMode"
            :print-offset-mm="printOffsetMm"
            @update:selected-index="(i) => (selectedIndex = i)"
            @update:items="onItemsChange"
            @add="onAddElement"
          />
          <ElCollapse v-model="collapseActiveNames" class="report-designer__collapse">
            <ElCollapseItem title="页面设置" name="page">
              <LayoutPagePanel
                :layout="layout"
                :pipeline="pipeline"
                :editable="editable"
                @patch-page="patchPage"
                @patch-overlay="patchOverlay"
              />
            </ElCollapseItem>
          </ElCollapse>
        </aside>

        <main class="report-designer__center">
          <div v-if="isFlowLayout" class="report-designer__flow-center">
            <FlowDesignCanvas
              class="report-designer__flow-canvas"
              :items="layout.items ?? []"
              :elements="elementPack?.elements ?? []"
              :selected-index="selectedIndex"
              :editable="editable"
              :page="layout.page"
              :pipeline="pipeline"
              @update:selected-index="(i) => (selectedIndex = i)"
              @update:items="onItemsChange"
              @add="onAddElement"
              @box-change="onOverlayBoxChange"
            />
            <FloatingReportPreviewPanel
              :pdf-blob="pdfBlob"
              :preview-meta="previewMeta"
              :loading="previewLoading"
            />
          </div>
          <ReportPreviewPanel
            v-else
            class="report-designer__overlay-canvas"
            fill-container
            :pdf-blob="pdfBlob"
            :preview-meta="previewMeta"
            :loading="previewLoading"
            :overlay-layout-items="layout.items ?? []"
            :overlay-elements="elementPack?.elements ?? []"
            :overlay-page="layout.page"
            :selected-element-code="selectedItem?.elementCode ?? null"
            :overlay-editable="editable && layoutMode === 'overlay'"
            :print-offset-mm="printOffsetMm"
            :overlay-sample-data="elementPack?.sampleData ?? null"
            @overlay-select="onOverlaySelect"
            @overlay-box-change="onOverlayBoxChange"
          />
        </main>

        <aside class="report-designer__right">
          <h4 class="report-designer__section-title">属性</h4>
          <LayoutItemPropertyPanel
            :item="selectedItem"
            :element-def="selectedDef"
            :items="layout.items ?? []"
            :elements="elementPack?.elements ?? []"
            :page="layout.page ?? null"
            :layout-mode="layoutMode"
            :pipeline="pipeline"
            :print-offset-mm="printOffsetMm"
            :editable="editable"
            @patch="patchSelected"
          />
        </aside>
      </div>
    </div>
    <CopyTemplateDialog ref="copyDialogRef" @success="onCopyAsNewSuccess" />
    <ReportPdfPreviewDialog
      v-model="previewDialogVisible"
      :pdf-blob="pdfBlob"
      :loading="previewLoading"
      :title="`预览 — ${design?.name ?? templateCode}`"
      @refresh="handlePreviewDialogRefresh"
    />
  </Page>
</template>

<style scoped>
.report-designer {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  min-width: 0;
  gap: 12px;
}

.report-designer__header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.report-designer__alert {
  flex-shrink: 0;
}

.report-designer__body {
  display: grid;
  grid-template-columns: minmax(300px, 320px) minmax(0, 1fr) minmax(260px, 280px);
  gap: 12px;
  flex: 1;
  min-height: 0;
  min-width: 0;
}

.report-designer__left,
.report-designer__right {
  overflow-x: hidden;
  overflow-y: auto;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  padding: 10px 12px;
  min-height: 0;
  min-width: 0;
}

.report-designer__center {
  overflow: hidden;
  min-height: 0;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  background: var(--el-fill-color-lighter);
}

.report-designer__overlay-canvas {
  flex: 1;
  min-height: 0;
  min-width: 0;
  height: 100%;
  padding: 12px;
}

.report-designer__flow-center {
  position: relative;
  flex: 1;
  min-height: 0;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 8px;
}

.report-designer__flow-canvas {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  background: var(--el-bg-color);
  border-radius: 4px;
}

.report-designer__section-title {
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 600;
}

.report-designer__collapse {
  margin-top: 12px;
  border: none;
}

.report-designer__collapse :deep(.el-collapse-item__header) {
  padding-left: 0;
  padding-right: 0;
  font-size: 13px;
  font-weight: 600;
}

.report-designer__collapse :deep(.el-collapse-item__wrap),
.report-designer__collapse :deep(.el-collapse-item__content) {
  padding-left: 0;
  padding-right: 0;
}

@media (max-width: 1100px) {
  .report-designer__body {
    grid-template-columns: 1fr;
    grid-template-rows: auto auto auto;
  }
}
</style>
