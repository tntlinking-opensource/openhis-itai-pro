<script setup lang="ts">
import type { HiprintPrinterItem } from '../fetch-printer-list';

import { computed, onMounted, ref, watch } from 'vue';

import { ElAlert, ElButton, ElCheckbox, ElDialog, ElOption, ElSelect } from 'element-plus';

import { PRINT_PLUGIN_INSTALL_MESSAGE } from '../runtime/print-user-error';
import PrintChromeFlagShortcut from './print-chrome-flag-shortcut.vue';

defineOptions({ name: 'PrinterSelectDialog' });

const props = withDefaults(defineProps<Props>(), {
  visible: true,
  title: '选择打印机',
  bindingLabel: '',
  printers: () => [],
  initialPrinterName: '',
  initialSkipPrompt: true,
  confirmText: '打印',
  unavailableHint: '',
  loading: false,
  loadError: '',
});

const emit = defineEmits<{
  cancel: [];
  confirm: [
    payload: {
      printerName: string;
      skipPrompt: boolean;
    },
  ];
  retryLoad: [];
}>();

interface Props {
  visible?: boolean;
  title?: string;
  bindingLabel?: string;
  printers?: HiprintPrinterItem[];
  initialPrinterName?: string;
  initialSkipPrompt?: boolean;
  confirmText?: string;
  unavailableHint?: string;
  loading?: boolean;
  loadError?: string;
}

const dialogVisible = ref(props.visible);
const selectedPrinter = ref('');
const skipPrompt = ref(true);

const dialogTitle = computed(() => {
  const label = props.bindingLabel?.trim();
  if (label) {
    return `${props.title} · ${label}`;
  }
  return props.title;
});

const troubleshootingMessage = PRINT_PLUGIN_INSTALL_MESSAGE;

/** 将外部传入的初始值同步到表单 */
function syncFormFromProps(): void {
  selectedPrinter.value = props.initialPrinterName?.trim() ?? '';
  skipPrompt.value = props.initialSkipPrompt !== false;
}

/** 打印机列表变化时补全默认选中项 */
function ensureSelectedPrinter(): void {
  if (selectedPrinter.value && props.printers.some((item) => item.name === selectedPrinter.value)) {
    return;
  }
  const fallback =
    props.printers.find((item) => item.isDefault)?.name ?? props.printers[0]?.name ?? '';
  selectedPrinter.value = fallback;
}

watch(
  () => props.visible,
  (value) => {
    dialogVisible.value = value;
    if (value) {
      syncFormFromProps();
      ensureSelectedPrinter();
    }
  },
);

watch(
  () => props.printers,
  () => {
    ensureSelectedPrinter();
  },
  { deep: true },
);

watch(
  () => [props.initialPrinterName, props.initialSkipPrompt] as const,
  () => {
    if (dialogVisible.value) {
      syncFormFromProps();
      ensureSelectedPrinter();
    }
  },
);

/** 关闭弹窗并通知取消 */
function handleCancel(): void {
  dialogVisible.value = false;
  emit('cancel');
}

/** 校验后提交所选打印机 */
function handleConfirm(): void {
  const printerName = selectedPrinter.value.trim();
  if (!printerName) {
    return;
  }
  dialogVisible.value = false;
  emit('confirm', {
    printerName,
    skipPrompt: skipPrompt.value,
  });
}

onMounted(() => {
  syncFormFromProps();
  ensureSelectedPrinter();
});
</script>

<template>
  <ElDialog
    v-model="dialogVisible"
    :title="dialogTitle"
    width="480px"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
    @close="handleCancel"
  >
    <div v-if="loading" class="py-6 text-center text-sm text-[var(--el-text-color-regular)]">
      正在加载本机打印机…
    </div>
    <template v-else>
      <ElAlert
        v-if="loadError"
        type="error"
        :closable="false"
        show-icon
        class="mb-4"
        :title="loadError"
      >
        <p class="m-0 whitespace-pre-line text-sm leading-relaxed">{{ troubleshootingMessage }}</p>
        <div class="mt-3">
          <PrintChromeFlagShortcut />
        </div>
      </ElAlert>
      <ElAlert
        v-else-if="unavailableHint"
        type="warning"
        :closable="false"
        show-icon
        class="mb-4"
        :title="unavailableHint"
      />
      <div class="mb-4">
        <div class="mb-2 text-sm text-[var(--el-text-color-regular)]">打印机</div>
        <ElSelect
          v-model="selectedPrinter"
          class="w-full"
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
      </div>
      <ElCheckbox v-model="skipPrompt">设为默认，下次不再询问</ElCheckbox>
    </template>
    <template #footer>
      <ElButton @click="handleCancel">取消</ElButton>
      <ElButton v-if="loadError" type="primary" @click="emit('retryLoad')">重试</ElButton>
      <ElButton
        v-else
        type="primary"
        :disabled="loading || printers.length === 0 || !selectedPrinter"
        @click="handleConfirm"
      >
        {{ confirmText }}
      </ElButton>
    </template>
  </ElDialog>
</template>
