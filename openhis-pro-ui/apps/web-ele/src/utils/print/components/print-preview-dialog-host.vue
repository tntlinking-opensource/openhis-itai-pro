<!--
  业务打印预览宿主：展示 hiprint getHtml；打印走 hiprint print2。
  库房单据可在标题栏切换纸张；份数不在预览里改。
-->
<script setup lang="ts">
import type { InvFormPaperKind } from '#/utils/print/form-print-preference';
import type { PrintResult } from '#/utils/print/runtime/types';

import { computed, ref } from 'vue';

import { Printer } from '@element-plus/icons-vue';
import { ElButton, ElDialog } from 'element-plus';

import {
  clampFormCopies,
  INV_FORM_PAPER_KIND,
  isInvFormTriplePaper,
  normalizeInvFormPaperKind,
} from '#/utils/print/form-print-preference';

import InvFormPaperKindSwitch from './inv-form-paper-kind-switch.vue';

import '#/hiprint/styles/sv-print-preview.css';

defineOptions({ name: 'PrintPreviewDialogHost' });

interface Props {
  copies?: number;
  foldCount?: number;
  onPrint: (copies: number, formPaperKind?: InvFormPaperKind) => Promise<PrintResult>;
  previewHtml: string;
  /** 针式联单：折与折贴紧，齿孔用虚线 */
  continuousForm?: boolean;
  /** 库房单据当前纸张 */
  formPaperKind?: InvFormPaperKind;
  /** 库房单据：标题栏展示纸张切换 */
  showFormPaperKind?: boolean;
  /** 切换纸张后重渲染预览 */
  onFormPaperKindChange?: (kind: InvFormPaperKind) => {
    foldCount: number;
    previewHtml: string;
  };
}

const props = withDefaults(defineProps<Props>(), {
  copies: 1,
  foldCount: 1,
  continuousForm: false,
  formPaperKind: undefined,
  showFormPaperKind: false,
  onFormPaperKindChange: undefined,
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
const previewHtml = ref(props.previewHtml);
const foldCount = ref(Math.max(1, Math.floor(props.foldCount || 1)));

/** 三联单预览贴紧折缝；A4 用分页阴影 */
const isContinuousForm = computed(() => {
  if (props.showFormPaperKind) {
    return isInvFormTriplePaper(paperKind.value);
  }
  return props.continuousForm;
});

/** 标题：联单带折数，其它只写预览 */
const previewTitleText = computed(() => {
  if (!isContinuousForm.value) {
    return '打印预览';
  }
  const folds = Math.max(1, Math.floor(foldCount.value || 1));
  return `打印预览（共 ${folds} 折）`;
});

/** 预览内切换纸张并重渲染 */
function handlePaperKindChange(kind: InvFormPaperKind): void {
  const next = normalizeInvFormPaperKind(kind);
  if (next === paperKind.value) {
    return;
  }
  const prev = paperKind.value;
  paperKind.value = next;
  if (!props.onFormPaperKindChange) {
    return;
  }
  try {
    const result = props.onFormPaperKindChange(next);
    previewHtml.value = result.previewHtml;
    foldCount.value = Math.max(1, Math.floor(result.foldCount || 1));
  } catch (error) {
    paperKind.value = prev;
    console.warn('[print] 切换纸张预览失败', error);
  }
}

/** 预览内打印：hiprint print2，成功后关窗 */
async function handlePrint(): Promise<void> {
  if (printing.value) {
    return;
  }
  printing.value = true;
  try {
    const kind = props.showFormPaperKind ? paperKind.value : props.formPaperKind;
    const result = await props.onPrint(clampFormCopies(copies.value), kind);
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
</script>

<template>
  <ElDialog
    v-model="visible"
    class="hiprint-preview-dialog"
    destroy-on-close
    top="4vh"
    width="1280px"
    @closed="handleClosed"
  >
    <template #header>
      <div class="preview-dialog-header">
        <span class="preview-dialog-title">{{ previewTitleText }}</span>
        <div class="preview-dialog-actions">
          <InvFormPaperKindSwitch
            v-if="showFormPaperKind"
            :model-value="paperKind"
            @update:model-value="handlePaperKindChange"
          />
          <ElButton
            class="shrink-0"
            type="primary"
            :icon="Printer"
            :loading="printing"
            @click="handlePrint"
          >
            打印
          </ElButton>
        </div>
      </div>
    </template>
    <div class="preview-wrap">
      <!-- hiprint getHtml 输出，非用户输入 -->
      <!-- eslint-disable-next-line vue/no-v-html -->
      <div
        class="preview-content"
        :class="{ 'is-continuous-form': isContinuousForm }"
        v-html="previewHtml"
      ></div>
    </div>
  </ElDialog>
</template>
