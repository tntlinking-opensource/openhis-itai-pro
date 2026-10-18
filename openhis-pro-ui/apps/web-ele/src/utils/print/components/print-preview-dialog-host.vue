<!--
  业务打印预览宿主：展示 hiprint getHtml；打印走 hiprint print2。
  库房单据在预览右侧选纸张、规格与打印机；份数不在预览里改。
  多页 A4（如病案首页）可勾选要打印的页。
-->
<script setup lang="ts">
import type { HiprintPrinterItem } from '#/utils/print/fetch-printer-list';
import type {
  InvFormPaperChangePayload,
  InvFormPaperKind,
  InvFormTripleSpec,
} from '#/utils/print/form-print-preference';
import type { PrintPageSelection } from '#/utils/print/runtime/print-page-selection';
import type { PrintResult } from '#/utils/print/runtime/types';

import { computed, onMounted, ref, watch } from 'vue';

import { Printer } from '@element-plus/icons-vue';
import {
  ElAlert,
  ElButton,
  ElCheckbox,
  ElCheckboxGroup,
  ElDialog,
  ElMessage,
  ElOption,
  ElSelect,
} from 'element-plus';

import { fetchHiprintPrinterList } from '#/utils/print/fetch-printer-list';
import {
  clampFormCopies,
  INV_FORM_PAPER_KIND,
  INV_FORM_TRIPLE_SPEC,
  isInvFormTriplePaper,
  normalizeInvFormPaperKind,
  normalizeInvFormTripleSpec,
} from '#/utils/print/form-print-preference';
import { buildDefaultPageIndexes } from '#/utils/print/runtime/print-page-selection';
import { PRINT_PLUGIN_INSTALL_MESSAGE } from '#/utils/print/runtime/print-user-error';

import InvFormPaperKindCards from './inv-form-paper-kind-cards.vue';
import PrintChromeFlagShortcut from './print-chrome-flag-shortcut.vue';

import '#/hiprint/styles/sv-print-preview.css';

defineOptions({ name: 'PrintPreviewDialogHost' });

interface Props {
  copies?: number;
  foldCount?: number;
  onPrint: (
    copies: number,
    formPaperKind?: InvFormPaperKind,
    tripleSpec?: InvFormTripleSpec,
    printerName?: string,
    pageSelection?: PrintPageSelection,
  ) => Promise<PrintResult>;
  previewHtml: string;
  /** 针式联单：折与折贴紧，齿孔用虚线 */
  continuousForm?: boolean;
  /** 库房单据当前纸张 */
  formPaperKind?: InvFormPaperKind;
  /** 库房单据当前三联单规格 */
  formTripleSpec?: InvFormTripleSpec;
  /** 库房单据：预览右侧展示选纸与打印机 */
  showFormPaperKind?: boolean;
  /** 已保存的场景打印机，加载列表后优先选中 */
  initialPrinterName?: string;
  /** 切换纸张或规格后重渲染预览 */
  onFormPaperChange?: (payload: InvFormPaperChangePayload) => {
    foldCount: number;
    previewHtml: string;
  };
}

const props = withDefaults(defineProps<Props>(), {
  copies: 1,
  foldCount: 1,
  continuousForm: false,
  formPaperKind: undefined,
  formTripleSpec: undefined,
  showFormPaperKind: false,
  initialPrinterName: '',
  onFormPaperChange: undefined,
});

const emit = defineEmits<{
  closed: [];
}>();

const visible = ref(true);
const printing = ref(false);
const copies = ref(clampFormCopies(props.copies));
const paperKind = ref(
  props.formPaperKind ? normalizeInvFormPaperKind(props.formPaperKind) : INV_FORM_PAPER_KIND.triple,
);
const tripleSpec = ref(
  normalizeInvFormTripleSpec(props.formTripleSpec ?? INV_FORM_TRIPLE_SPEC.third),
);
const previewHtml = ref(props.previewHtml);
const foldCount = ref(Math.max(1, Math.floor(props.foldCount || 1)));
const printers = ref<HiprintPrinterItem[]>([]);
const printersLoading = ref(false);
const printersError = ref('');
const selectedPrinter = ref('');
/** 多页 A4：勾选要打的页（1-based） */
const selectedPages = ref<number[]>(buildDefaultPageIndexes(foldCount.value));

const troubleshootingMessage = PRINT_PLUGIN_INSTALL_MESSAGE;

/** 三联单预览贴紧折缝；A4 用分页阴影 */
const isContinuousForm = computed(() => {
  if (props.showFormPaperKind) {
    return isInvFormTriplePaper(paperKind.value);
  }
  return props.continuousForm;
});

/** 非联单且 ≥2 页：显示页码勾选 */
const showPageSelection = computed(() => !isContinuousForm.value && foldCount.value >= 2);

const pageOptions = computed(() =>
  buildDefaultPageIndexes(foldCount.value).map((page) => ({
    label: `第${page}页`,
    value: page,
  })),
);

/** 标题：联单带折数，多页 A4 带页数，其它只写预览 */
const previewTitleText = computed(() => {
  if (isContinuousForm.value) {
    const folds = Math.max(1, Math.floor(foldCount.value || 1));
    return `打印预览（共 ${folds} 折）`;
  }
  if (foldCount.value >= 2) {
    return `打印预览（共 ${foldCount.value} 页）`;
  }
  return '打印预览';
});

/** 右侧已选出可用打印机时才允许打印 */
const canPrint = computed(() => {
  if (showPageSelection.value && selectedPages.value.length === 0) {
    return false;
  }
  if (!props.showFormPaperKind) {
    return true;
  }
  if (printersLoading.value || printersError.value) {
    return false;
  }
  return Boolean(selectedPrinter.value.trim());
});

/** 折/页数变化时重置页码勾选（纸张切换重渲染） */
watch(foldCount, (next) => {
  selectedPages.value = buildDefaultPageIndexes(next);
});

/** 打印机列表变化时补全默认选中项（优先沿用已保存的打印机） */
function ensureSelectedPrinter(): void {
  if (selectedPrinter.value && printers.value.some((item) => item.name === selectedPrinter.value)) {
    return;
  }
  const saved = props.initialPrinterName?.trim() ?? '';
  if (saved && printers.value.some((item) => item.name === saved)) {
    selectedPrinter.value = saved;
    return;
  }
  const fallback =
    printers.value.find((item) => item.isDefault)?.name ?? printers.value[0]?.name ?? '';
  selectedPrinter.value = fallback;
}

/** 拉取本机 hiprint 打印机并回填选中项 */
async function loadPrinters(): Promise<void> {
  printersLoading.value = true;
  printersError.value = '';
  try {
    printers.value = await fetchHiprintPrinterList();
    ensureSelectedPrinter();
    if (printers.value.length === 0) {
      printersError.value = '未发现本机打印机，请确认打印客户端已启动';
    }
  } catch (error) {
    printers.value = [];
    selectedPrinter.value = '';
    printersError.value =
      error instanceof Error ? error.message : '无法连接打印客户端，请确认 Zxplat.Win 已启动';
  } finally {
    printersLoading.value = false;
  }
}

/** 按当前纸张与规格重渲染预览 */
function rerenderPreview(prevKind: InvFormPaperKind, prevSpec: InvFormTripleSpec): void {
  if (!props.onFormPaperChange) {
    return;
  }
  try {
    const result = props.onFormPaperChange({
      paperKind: paperKind.value,
      tripleSpec: tripleSpec.value,
    });
    previewHtml.value = result.previewHtml;
    foldCount.value = Math.max(1, Math.floor(result.foldCount || 1));
  } catch (error) {
    paperKind.value = prevKind;
    tripleSpec.value = prevSpec;
    console.warn('[print] 切换纸张预览失败', error);
  }
}

/** 预览内切换纸张或三联单规格 */
function handlePaperChange(payload: InvFormPaperChangePayload): void {
  const nextKind = normalizeInvFormPaperKind(payload.paperKind);
  const nextSpec = normalizeInvFormTripleSpec(payload.tripleSpec);
  if (nextKind === paperKind.value && nextSpec === tripleSpec.value) {
    return;
  }
  const prevKind = paperKind.value;
  const prevSpec = tripleSpec.value;
  paperKind.value = nextKind;
  tripleSpec.value = nextSpec;
  rerenderPreview(prevKind, prevSpec);
}

/** 右侧选纸：切换三联单 / A4 */
function handleKindSelect(kind: InvFormPaperKind): void {
  handlePaperChange({ paperKind: kind, tripleSpec: tripleSpec.value });
}

/** 右侧选纸：切换整张 / 二等分 / 三等分 */
function handleSpecSelect(spec: InvFormTripleSpec): void {
  handlePaperChange({ paperKind: paperKind.value, tripleSpec: spec });
}

/** 组装页码选项 */
function buildPageSelection(): PrintPageSelection | undefined {
  if (!showPageSelection.value) {
    return undefined;
  }
  const pages = [...selectedPages.value].sort((a, b) => a - b);
  if (pages.length === 0) {
    return undefined;
  }
  return {
    pageIndexes: pages,
  };
}

/** 预览内打印：hiprint print2，成功后关窗 */
async function handlePrint(): Promise<void> {
  if (printing.value || !canPrint.value) {
    return;
  }
  if (showPageSelection.value && selectedPages.value.length === 0) {
    ElMessage.warning('请至少选择一页打印');
    return;
  }
  printing.value = true;
  try {
    const kind = props.showFormPaperKind ? paperKind.value : props.formPaperKind;
    const spec = props.showFormPaperKind ? tripleSpec.value : props.formTripleSpec;
    const printerName = props.showFormPaperKind ? selectedPrinter.value.trim() : undefined;
    const result = await props.onPrint(
      clampFormCopies(copies.value),
      kind,
      spec,
      printerName,
      buildPageSelection(),
    );
    if (result.ok) {
      visible.value = false;
    }
  } finally {
    printing.value = false;
  }
}

/** 对话框关闭后通知宿主卸载 */
function handleClosed(): void {
  emit('closed');
}

onMounted(() => {
  if (props.showFormPaperKind) {
    void loadPrinters();
  }
});
</script>

<template>
  <ElDialog
    v-model="visible"
    class="hiprint-preview-dialog"
    destroy-on-close
    top="2vh"
    width="min(1680px, 96vw)"
    @closed="handleClosed"
  >
    <template #header>
      <div class="preview-dialog-header">
        <span class="preview-dialog-title">{{ previewTitleText }}</span>
        <div class="preview-dialog-actions">
          <div v-if="showPageSelection" class="preview-page-tools">
            <span class="preview-page-tools-label">打印页</span>
            <ElCheckboxGroup v-model="selectedPages" class="preview-page-checks">
              <ElCheckbox v-for="opt in pageOptions" :key="opt.value" :value="opt.value">
                {{ opt.label }}
              </ElCheckbox>
            </ElCheckboxGroup>
          </div>
          <ElButton
            class="shrink-0"
            type="primary"
            :icon="Printer"
            :loading="printing"
            :disabled="!canPrint"
            @click="handlePrint"
          >
            打印
          </ElButton>
        </div>
      </div>
    </template>
    <div class="preview-body">
      <div class="preview-wrap">
        <!-- hiprint getHtml 输出，非用户输入 -->
        <!-- eslint-disable-next-line vue/no-v-html -->
        <div
          class="preview-content"
          :class="{ 'is-continuous-form': isContinuousForm }"
          v-html="previewHtml"
        ></div>
      </div>
      <aside v-if="showFormPaperKind" class="preview-paper-pane">
        <div class="preview-paper-pane-label">纸张</div>
        <InvFormPaperKindCards
          :model-value="paperKind"
          :triple-spec="tripleSpec"
          @update:model-value="handleKindSelect"
          @update:triple-spec="handleSpecSelect"
        />
        <div class="preview-paper-pane-label">打印机</div>
        <div v-if="printersLoading" class="preview-paper-pane-hint">正在加载本机打印机…</div>
        <ElAlert
          v-else-if="printersError"
          type="error"
          :closable="false"
          show-icon
          :title="printersError"
        >
          <p class="m-0 whitespace-pre-line text-sm leading-relaxed">
            {{ troubleshootingMessage }}
          </p>
          <div class="mt-3">
            <PrintChromeFlagShortcut />
          </div>
          <div class="mt-3">
            <ElButton size="small" type="primary" @click="loadPrinters">重试</ElButton>
          </div>
        </ElAlert>
        <ElSelect
          v-else
          v-model="selectedPrinter"
          class="preview-paper-pane-select"
          filterable
          placeholder="请选择打印机"
          :disabled="printers.length === 0"
        >
          <ElOption
            v-for="item in printers"
            :key="item.name"
            :label="item.displayName"
            :value="item.name"
          />
        </ElSelect>
      </aside>
    </div>
  </ElDialog>
</template>
