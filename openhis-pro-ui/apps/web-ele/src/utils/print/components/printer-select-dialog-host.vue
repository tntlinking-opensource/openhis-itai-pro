<script setup lang="ts">
import type { HiprintPrinterItem } from '../fetch-printer-list';

import { onMounted, ref } from 'vue';

import PrinterSelectDialog from './printer-select-dialog.vue';

defineOptions({ name: 'PrinterSelectDialogHost' });

const props = withDefaults(defineProps<Props>(), {
  title: '选择打印机',
  bindingLabel: '',
  confirmText: '打印',
  initialPrinterName: '',
  initialSkipPrompt: false,
  unavailableHint: '',
});

const emit = defineEmits<{
  cancel: [];
  confirm: [
    payload: {
      printerName: string;
      skipPrompt: boolean;
    },
  ];
}>();

interface Props {
  title?: string;
  bindingLabel?: string;
  confirmText?: string;
  initialPrinterName?: string;
  initialSkipPrompt?: boolean;
  unavailableHint?: string;
  loadPrinters: () => Promise<HiprintPrinterItem[]>;
}

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
function handleConfirm(payload: { printerName: string; skipPrompt: boolean }): void {
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
    @confirm="handleConfirm"
    @cancel="handleCancel"
    @retry-load="loadPrinterList"
  />
</template>
