<script lang="ts" setup>
import type { HiprintDesignerExpose } from '../core/types';
import type { AlignType } from './AlignToolbar.vue';

import { computed, nextTick, onBeforeUnmount, onMounted, ref, useTemplateRef } from 'vue';
import { useRouter } from 'vue-router';

import { ElMessage, ElMessageBox } from 'element-plus';

import {
  ensureWritableDraftApi,
  getByBusNoApi,
  publishTemplateApi,
  saveDraftApi,
} from '#/api/print/print-template';

import { DEFAULT_PAPER_PRESET, useHiprintDesigner } from '../composables/use-hiprint-designer';
import { bindFunctionEditorBridge } from '../config/function-bridge';
import { bindPaperSizeBridge, unbindPaperSizeBridge } from '../config/paper-size-bridge';
import {
  applyHeaderFooterLinePresentation,
  applyPaperGridClass,
  removeHiprintInnerRulers,
} from '../utils/design-canvas';
import {
  downloadJsonFile,
  loadLocalDraft,
  pickJsonFile,
  saveLocalDraft,
} from '../utils/local-draft';
import { buildPreviewPrintData } from '../utils/print-data';
import {
  createInitialDesignerTemplate,
  normalizePrintJson,
  readPrintJsonFromTemplateDto,
  toSavePayload,
  validatePrintJson,
} from '../utils/print-json';
import DesignerRuler from './DesignerRuler.vue';
import DesignerToolbar from './DesignerToolbar.vue';
import ElementPalette from './ElementPalette.vue';
import FunctionCodeDialog from './FunctionCodeDialog.vue';
import JsonEditorDialog from './JsonEditorDialog.vue';
import PreviewDialog from './PreviewDialog.vue';

import '../styles/sv-print-icons.css';
import '../styles/sv-print-canvas.css';
import '../styles/sv-print-ruler.css';
import '../styles/sv-print-guides.css';
import '../styles/sv-print-toolbar.css';
import '../styles/sv-print-theme.css';

defineOptions({ name: 'HiprintDesigner' });

const router = useRouter();

const props = withDefaults(defineProps<Props>(), {
  busNo: '',
});

interface Props {
  /** 业务键：有值时从后端加载/保存草稿 */
  busNo?: string;
}

const loading = ref(true);
const saving = ref(false);
const samplePrintData = ref<Record<string, unknown>>({});
/** 后端模板元数据（名称等，保存草稿时带回） */
const templateMeta = ref<null | {
  busNo: string;
  id?: string;
  name?: string;
  scenarioCode?: string;
}>(null);

const previewRef = useTemplateRef<InstanceType<typeof PreviewDialog>>('previewRef');
const templateJsonEditorRef =
  useTemplateRef<InstanceType<typeof JsonEditorDialog>>('templateJsonEditorRef');
const printDataEditorRef =
  useTemplateRef<InstanceType<typeof JsonEditorDialog>>('printDataEditorRef');
const functionEditorRef =
  useTemplateRef<InstanceType<typeof FunctionCodeDialog>>('functionEditorRef');
const rulerRef = useTemplateRef<InstanceType<typeof DesignerRuler>>('rulerRef');

const selectedCount = ref(0);

/** 顶部栏副标题：展示当前编辑的 busNo / 名称 */
const toolbarSubtitle = computed(() => {
  const meta = templateMeta.value;
  if (!meta?.busNo) {
    return '';
  }
  if (meta.name?.trim()) {
    return `${meta.name} · ${meta.busNo}`;
  }
  return meta.busNo;
});

/** 有 busNo 时保存到后端草稿，否则保存到浏览器本地 */
const toolbarSaveLabel = computed(() => (templateMeta.value?.busNo ? '保存草稿' : '保存'));

const {
  templateRef,
  scaleValue,
  currentPaper,
  mountDesigner,
  destroyDesigner,
  setPaper,
  updateCurrentPaperSize,
  changeScale,
  undo,
  redo,
  clearTemplate,
  getTemplateJson,
  applyTemplateJson,
  getSelectedCount,
  setElsAlign,
} = useHiprintDesigner();

/** 同步 samplePrintData：实例 testData + 后端样例 */
function syncSamplePrintData(
  templateJson: Record<string, unknown>,
  sample?: null | Record<string, unknown>,
): void {
  samplePrintData.value = buildPreviewPrintData(templateJson, {
    samplePrintData: sample ?? samplePrintData.value,
    liveTemplate: templateRef.value,
  });
}

const DESIGN_SELECTOR = '#hiprint-design-container';
const SETTING_SELECTOR = '#PrintElementOptionSetting';
const PALETTE_SELECTOR = '#hiprint-element-palette';

/** 刷新选中状态（对齐工具栏禁用态） */
function refreshSelection(): void {
  selectedCount.value = getSelectedCount();
}

/** 刷新纸张 grid、移除内置标尺并同步外部标尺 */
async function refreshCanvas(): Promise<void> {
  applyPaperGridClass();
  removeHiprintInnerRulers();
  applyHeaderFooterLinePresentation();
  await nextTick();
  removeHiprintInnerRulers();
  applyHeaderFooterLinePresentation();
  await rulerRef.value?.updateRuler(scaleValue.value);
  requestAnimationFrame(() => {
    applyHeaderFooterLinePresentation();
  });
}

/** 挂载画布后同步纸张尺寸与标尺 */
async function syncPaperFromTemplate(json: Record<string, unknown>): Promise<void> {
  const panel = (json.panels as undefined | { height?: number; width?: number }[])?.[0];
  if (panel?.width && panel?.height) {
    updateCurrentPaperSize(panel.width, panel.height);
  } else {
    setPaper(DEFAULT_PAPER_PRESET);
  }
  await refreshCanvas();
  requestAnimationFrame(() => {
    void refreshCanvas();
  });
}

/** 首次挂载：始终 A4 空白（不自动恢复 localStorage，避免小票等尺寸残留） */
async function mountInitialDesigner(): Promise<void> {
  loading.value = true;
  try {
    templateMeta.value = null;
    samplePrintData.value = {};
    const initialJson = createInitialDesignerTemplate();
    await mountDesigner(initialJson, {
      designSelector: DESIGN_SELECTOR,
      settingSelector: SETTING_SELECTOR,
      paletteSelector: PALETTE_SELECTOR,
    });
    setPaper(DEFAULT_PAPER_PRESET);
    await nextTick();
    await syncPaperFromTemplate(getTemplateJson());
  } catch (error) {
    console.error('[hiprint] 设计器初始化失败', error);
    ElMessage.error('设计器初始化失败');
  } finally {
    loading.value = false;
  }
}

/** 按 busNo 拉取设计态模板并挂载画布 */
async function mountFromBackend(busNo: string): Promise<void> {
  loading.value = true;
  try {
    const dto = await getByBusNoApi(busNo);
    if (dto.status === 'archived') {
      ElMessage.warning('已归档模板不可编辑');
      await mountInitialDesigner();
      return;
    }
    templateMeta.value = {
      id: dto.id,
      busNo,
      name: dto.name,
      scenarioCode: dto.scenarioCode,
    };
    samplePrintData.value = dto.samplePrintData ?? {};
    const normalized = normalizePrintJson(readPrintJsonFromTemplateDto(dto));
    await mountDesigner(normalized, {
      designSelector: DESIGN_SELECTOR,
      settingSelector: SETTING_SELECTOR,
      paletteSelector: PALETTE_SELECTOR,
    });
    syncSamplePrintData(normalized, dto.samplePrintData ?? {});
    await nextTick();
    await syncPaperFromTemplate(normalized);
  } catch (error) {
    console.error('[hiprint] 加载模板失败', error);
    ElMessage.error('加载模板失败');
    await mountInitialDesigner();
  } finally {
    loading.value = false;
  }
}

/** 按 props.busNo 决定空白画布或后端模板 */
async function mountDesignerByContext(): Promise<void> {
  const busNo = props.busNo?.trim();
  if (busNo) {
    await mountFromBackend(busNo);
    return;
  }
  await mountInitialDesigner();
}

/** 对齐选中元素 */
function handleAlign(type: AlignType): void {
  setElsAlign(type);
  refreshSelection();
}

/** 包装纸张切换 */
async function handlePaperChange(preset: Parameters<typeof setPaper>[0]): Promise<void> {
  setPaper(preset);
  await nextTick();
  await refreshCanvas();
}

/** 包装缩放 */
async function handleZoom(delta: number): Promise<void> {
  changeScale(delta);
  await nextTick();
  await refreshCanvas();
}

/** 打开预览 */
function handlePreview(): void {
  const json = getTemplateJson();
  const printData = buildPreviewPrintData(json, {
    samplePrintData: samplePrintData.value,
    liveTemplate: templateRef.value,
  });
  previewRef.value?.show(json, printData);
}

/** 保存到浏览器 localStorage */
function handleSaveLocal(): void {
  saving.value = true;
  try {
    const payload = toSavePayload(getTemplateJson());
    validatePrintJson(payload);
    saveLocalDraft({
      PrintJson: payload,
      samplePrintData: samplePrintData.value,
    });
    ElMessage.success('已保存到浏览器本地');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败');
  } finally {
    saving.value = false;
  }
}

/** 校验后保存草稿到后端 */
async function handleSaveDraft(): Promise<void> {
  const meta = templateMeta.value;
  if (!meta?.busNo) {
    return;
  }
  saving.value = true;
  try {
    const dto = await persistDraftToBackend();
    if (dto?.id) {
      templateMeta.value = { ...meta, id: dto.id };
    }
    ElMessage.success('已保存草稿');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败');
  } finally {
    saving.value = false;
  }
}

/** 校验并写入后端草稿，供保存/发布复用 */
async function persistDraftToBackend() {
  const meta = templateMeta.value;
  if (!meta?.busNo) {
    throw new Error('未关联后端模板');
  }
  const payload = toSavePayload(getTemplateJson());
  validatePrintJson(payload);
  const draft = await ensureWritableDraftApi(meta.busNo);
  if (draft.id) {
    templateMeta.value = {
      ...meta,
      id: draft.id,
      name: draft.name ?? meta.name,
      scenarioCode: draft.scenarioCode ?? meta.scenarioCode,
    };
  }
  return saveDraftApi({
    id: draft.id,
    busNo: meta.busNo,
    name: draft.name ?? meta.name,
    scenarioCode: draft.scenarioCode?.trim() || meta.scenarioCode?.trim() || undefined,
    PrintJson: payload,
    samplePrintData: samplePrintData.value,
  });
}

/** 保存草稿后立即发布，成功后返回模板列表 */
async function handleSaveAndPublish(): Promise<void> {
  const meta = templateMeta.value;
  if (!meta?.busNo) {
    return;
  }

  saving.value = true;
  try {
    const saved = await persistDraftToBackend();
    if (saved?.id) {
      templateMeta.value = {
        ...meta,
        id: saved.id,
        name: saved.name ?? meta.name,
        scenarioCode: saved.scenarioCode ?? meta.scenarioCode,
      };
    }
    const published = await publishTemplateApi(meta.busNo);
    const versionLabel =
      published?.version !== null && published?.version !== undefined
        ? ` v${published.version}`
        : '';
    ElMessage.success(`已保存并发布${versionLabel}`);
    if (window.history.length > 1) {
      await router.back();
    } else {
      await router.push({ path: '/basic/print-template' });
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存并发布失败');
  } finally {
    saving.value = false;
  }
}

/** 顶部保存：有 busNo 写后端，否则写本地 */
function handleSave(): void {
  if (templateMeta.value?.busNo) {
    void handleSaveDraft();
    return;
  }
  handleSaveLocal();
}

/** 导出模板 JSON 文件 */
function handleExportJson(): void {
  try {
    const payload = toSavePayload(getTemplateJson());
    validatePrintJson(payload);
    downloadJsonFile('print-template.json', {
      PrintJson: payload,
      samplePrintData: samplePrintData.value,
    });
    ElMessage.success('已导出 JSON 文件');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导出失败');
  }
}

/** 从浏览器本地草稿或 JSON 文件加载 */
async function handleImportJson(): Promise<void> {
  try {
    const draft = loadLocalDraft();
    if (draft?.PrintJson) {
      const normalized = normalizePrintJson(draft.PrintJson);
      validatePrintJson(normalized);
      applyTemplateJson(normalized);
      syncSamplePrintData(normalized, draft.samplePrintData ?? {});
      const panel = (normalized.panels as undefined | { height?: number; width?: number }[])?.[0];
      if (panel?.width && panel?.height) {
        updateCurrentPaperSize(panel.width, panel.height);
      }
      await refreshCanvas();
      ElMessage.success('已加载本地草稿');
      return;
    }

    const fileData = await pickJsonFile();
    const printJson =
      (fileData.PrintJson as Record<string, unknown> | undefined) ??
      (fileData.panels ? fileData : null);
    if (!printJson) {
      throw new Error('文件中未找到 PrintJson 或 panels');
    }
    const normalized = normalizePrintJson(printJson);
    validatePrintJson(normalized);
    applyTemplateJson(normalized);
    syncSamplePrintData(
      normalized,
      fileData.samplePrintData && typeof fileData.samplePrintData === 'object'
        ? (fileData.samplePrintData as Record<string, unknown>)
        : {},
    );
    await refreshCanvas();
    ElMessage.success('导入成功');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导入失败');
  }
}

/** 编辑模板 JSON */
function handleEditTemplateJson(): void {
  templateJsonEditorRef.value?.open(getTemplateJson(), '编辑模板 JSON');
}

/** 应用模板 JSON 到画布 */
async function onApplyTemplateJson(json: Record<string, unknown>): Promise<void> {
  try {
    const normalized = normalizePrintJson(json);
    validatePrintJson(normalized);
    applyTemplateJson(normalized);
    syncSamplePrintData(normalized);
    await refreshCanvas();
    ElMessage.success('模板 JSON 已应用');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'JSON 无效');
  }
}

/** 编辑预览数据 JSON */
function handleEditPrintData(): void {
  printDataEditorRef.value?.open(samplePrintData.value, '编辑预览数据');
}

/** 应用预览数据 */
function onApplyPrintData(json: Record<string, unknown>): void {
  samplePrintData.value = json;
  ElMessage.success('预览数据已更新');
}

/** 清空画布确认 */
async function handleClear(): Promise<void> {
  try {
    await ElMessageBox.confirm('确认清空当前画布？', '警告', { type: 'warning' });
    clearTemplate();
    await refreshCanvas();
  } catch {
    // 用户取消
  }
}

onMounted(() => {
  bindFunctionEditorBridge((ctx) => {
    functionEditorRef.value?.open(ctx);
  });
  bindPaperSizeBridge(
    () => templateRef.value,
    (width, height) => {
      updateCurrentPaperSize(width, height);
      void refreshCanvas();
    },
  );
  mountDesignerByContext();
  document.addEventListener('mouseup', refreshSelection);
});

onBeforeUnmount(() => {
  document.removeEventListener('mouseup', refreshSelection);
  unbindPaperSizeBridge();
  destroyDesigner({
    designSelector: DESIGN_SELECTOR,
    settingSelector: SETTING_SELECTOR,
  });
});

defineExpose<HiprintDesignerExpose>({
  getTemplateJson,
  getSamplePrintData: () => samplePrintData.value,
});
</script>

<template>
  <div v-loading="loading" class="hiprint-designer-root">
    <DesignerToolbar
      :can-publish="Boolean(templateMeta?.busNo)"
      :current-paper-label="currentPaper.label"
      :has-selection="selectedCount > 0"
      :multi-selection="selectedCount >= 3"
      :saving="saving"
      :scale-value="scaleValue"
      :subtitle="toolbarSubtitle"
      :save-label="toolbarSaveLabel"
      @align="handleAlign"
      @clear="handleClear"
      @edit-print-data="handleEditPrintData"
      @edit-template-json="handleEditTemplateJson"
      @export-json="handleExportJson"
      @import-json="handleImportJson"
      @paper-change="handlePaperChange"
      @preview="handlePreview"
      @redo="redo"
      @save-and-publish="handleSaveAndPublish"
      @save-local="handleSave"
      @undo="undo"
      @zoom-in="handleZoom(0.1)"
      @zoom-out="handleZoom(-0.1)"
    />
    <div class="designer-body">
      <div id="hiprint-element-palette">
        <ElementPalette />
      </div>
      <div class="svp-designer">
        <div aria-hidden="true" class="global-ruler-corner"></div>
        <div id="global-ruler-x-host" class="global-ruler-x-host">
          <div class="global-ruler-x-track">
            <canvas id="ruler-x-oh"></canvas>
            <div id="global-ruler-x-rect-box" class="global-ruler-x-rect-box"></div>
            <div id="global-ruler-x-line" class="global-ruler-x-line">
              <span class="global-ruler-x-line-label"></span>
            </div>
          </div>
        </div>
        <div id="global-ruler-y-host" class="global-ruler-y-host">
          <div class="global-ruler-y-track">
            <canvas id="ruler-y-oh"></canvas>
            <div id="global-ruler-y-rect-box" class="global-ruler-y-rect-box"></div>
            <div id="global-ruler-y-line" class="global-ruler-y-line">
              <span class="global-ruler-y-line-label"></span>
            </div>
          </div>
        </div>
        <div class="designer-guide-overlay" aria-hidden="true">
          <div id="designer-guide-x" class="designer-guide-x"></div>
          <div id="designer-guide-y" class="designer-guide-y"></div>
          <span id="designer-guide-x-label" class="designer-guide-x-label"></span>
          <span id="designer-guide-y-label" class="designer-guide-y-label"></span>
        </div>
        <div id="paperBox" class="svp-paperBox">
          <div id="printTemplate" class="printTemplate">
            <div id="hiprint-design-container"></div>
          </div>
        </div>
        <DesignerRuler ref="rulerRef" :scale="scaleValue" :get-template="() => templateRef" />
      </div>
      <aside class="designer-options-panel">
        <div class="options-header">
          <span class="i-mdi:tune-variant options-header-icon"></span>
          元素属性
        </div>
        <div id="PrintElementOptionSetting" class="designer-options"></div>
      </aside>
    </div>

    <PreviewDialog ref="previewRef" />
    <JsonEditorDialog ref="templateJsonEditorRef" @apply="onApplyTemplateJson" />
    <JsonEditorDialog ref="printDataEditorRef" @apply="onApplyPrintData" />
    <FunctionCodeDialog ref="functionEditorRef" />
  </div>
</template>

<style scoped>
.hiprint-designer-root {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

/* designer-body 布局见 sv-print-canvas.css */

/* ── 右侧属性面板 ── */
.designer-options-panel {
  display: flex;
  flex-direction: column;
  width: 320px;
  flex-shrink: 0;
  overflow: hidden;
  background: var(--sv-panel-bg);
  border-left: 1px solid var(--sv-border);
}

.options-header {
  display: flex;
  flex-shrink: 0;
  gap: 6px;
  align-items: center;
  height: var(--sv-panel-header-h, 36px);
  padding: 0 12px;
  font-size: 13px;
  font-weight: 600;
  color: var(--sv-text-on-primary);
  background: var(--sv-primary);
}

.options-header-icon {
  font-size: 16px;
}

.designer-options {
  flex: 1;
  overflow: auto;
}
</style>
