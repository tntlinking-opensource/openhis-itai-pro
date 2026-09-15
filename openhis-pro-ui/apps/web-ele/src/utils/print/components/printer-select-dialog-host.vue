<script setup lang="ts">
import type { HiprintPrinterItem } from '../fetch-printer-list';
import type { InvFormPaperKind, InvFormTripleSpec } from '../form-print-preference';

import { onMounted, ref } from 'vue';

import { INV_FORM_PAPER_KIND, INV_FORM_TRIPLE_SPEC } from '../form-print-preference';
import PrinterSelectDialog from './printer-select-dialog.vue';

defineOptions({ name: 'PrinterSelectDialogHost' });

interface Props {
  title?: string;
  bindingLabel?: string;
  confirmText?: string;
  initialPrinterName?: string;
  initialSkipPrompt?: boolean;
  unavailableHint?: string;
  loadPrinters: () => Promise<HiprintPrinterItem[]>;
  showPaperKind?: boolean;
  initialPaperKind?: InvFormPaperKind;
  initialTripleSpec?: InvFormTripleSpec;
  showSkipPrompt?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  title: '选择打印机',
  bindingLabel: '',
  confirmText: '打印',
  initialPrinterName: '',
  initialSkipPrompt: false,
  unavailableHint: '',
  showPaperKind: false,
  initialPaperKind: INV_FORM_PAPER_KIND.triple,
  initialTripleSpec: INV_FORM_TRIPLE_SPEC.third,
  showSkipPrompt: true,
});

const emit = defineEmits<{
  cancel: [];
  confirm: [
    payload: {
      paperKind?: InvFormPaperKind;
      printerName: string;
      skipPrompt: boolean;
      tripleSpec?: InvFormTripleSpec;
    },
  ];
}>();

const visible = ref(true);
const loading = ref(true);
const loadError = ref('');
const printers = ref<HiprintPrinterItem[]>([]);

/** 拉取本机打印机列表 */
async function loadPrinterList(): Promise<void> {
  loading.value = true;
  loadError.value = '';
  try {
    printers.value = await props.loadPrinters();
    if (printers.value.length === 0) {
      loadError.value = '未检测到本机打印机';
    }
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '无法连接本机打印客户端';
    printers.value = [];
  } finally {
    loading.value = false;
  }
}

/** 用户确认选择 */
function handleConfirm(payload: {
  paperKind?: InvFormPaperKind;
  printerName: string;
  skipPrompt: boolean;
  tripleSpec?: InvFormTripleSpec;
}): void {
  visible.value = false;
  emit('confirm', payload);
}

/** 用户取消 */
function handleCancel(): void {
  visible.value = false;
  emit('cancel');
}

onMounted(() => {
  void loadPrinterList();
});
</script>

<template>
  <PrinterSelectDialog
    :visible="visible"
    :title="title"
    :binding-label="bindingLabel"
    :confirm-text="confirmText"
    :initial-printer-name="initialPrinterName"
    :initial-skip-prompt="initialSkipPrompt"
    :unavailable-hint="unavailableHint"
    :loading="loading"
    :load-error="loadError"
    :printers="printers"
    :show-paper-kind="showPaperKind"
    :initial-paper-kind="initialPaperKind"
    :initial-triple-spec="initialTripleSpec"
    :show-skip-prompt="showSkipPrompt"
    @confirm="handleConfirm"
    @cancel="handleCancel"
    @retry-load="loadPrinterList"
  />
</template>
