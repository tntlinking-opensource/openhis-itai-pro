<script setup lang="ts">
import type { HiprintPrinterItem } from '../fetch-printer-list';
import type { InvFormPaperKind, InvFormTripleSpec } from '../form-print-preference';
import type { ServiceApplyPaperSize } from '../service-apply-paper-preference';

import { computed, onMounted, ref, watch } from 'vue';

import {
  ElAlert,
  ElButton,
  ElCheckbox,
  ElDialog,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElSelect,
} from 'element-plus';

import {
  INV_FORM_PAPER_KIND,
  INV_FORM_TRIPLE_SPEC,
  normalizeInvFormPaperKind,
  normalizeInvFormTripleSpec,
} from '../form-print-preference';
import { PRINT_PLUGIN_INSTALL_MESSAGE } from '../runtime/print-user-error';
import {
  DEFAULT_SERVICE_APPLY_PAPER_SIZE,
  normalizeServiceApplyPaperSize,
  SERVICE_APPLY_PAPER_SIZE,
  SERVICE_APPLY_PAPER_SIZE_LABELS,
} from '../service-apply-paper-preference';
import InvFormPaperKindCards from './inv-form-paper-kind-cards.vue';
import PrintChromeFlagShortcut from './print-chrome-flag-shortcut.vue';

defineOptions({ name: 'PrinterSelectDialog' });

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
  /** 库房单据：同窗选择三联单 / A4 */
  showPaperKind?: boolean;
  initialPaperKind?: InvFormPaperKind;
  initialTripleSpec?: InvFormTripleSpec;
  /** 门诊申请单：同窗选择 A4 / A5 */
  showServiceApplyPaperSize?: boolean;
  initialServiceApplyPaperSize?: ServiceApplyPaperSize;
  /** 是否展示「记住本次选择，下次不再提示」 */
  showSkipPrompt?: boolean;
}

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
  showPaperKind: false,
  initialPaperKind: INV_FORM_PAPER_KIND.triple,
  initialTripleSpec: INV_FORM_TRIPLE_SPEC.third,
  showServiceApplyPaperSize: false,
  initialServiceApplyPaperSize: DEFAULT_SERVICE_APPLY_PAPER_SIZE,
  showSkipPrompt: true,
});

const emit = defineEmits<{
  cancel: [];
  confirm: [
    payload: {
      paperKind?: InvFormPaperKind;
      printerName: string;
      serviceApplyPaperSize?: ServiceApplyPaperSize;
      skipPrompt: boolean;
      tripleSpec?: InvFormTripleSpec;
    },
  ];
  retryLoad: [];
}>();

const dialogVisible = ref(props.visible);
const selectedPrinter = ref('');
const skipPrompt = ref(true);
const paperKind = ref<InvFormPaperKind>(normalizeInvFormPaperKind(props.initialPaperKind));
const tripleSpec = ref<InvFormTripleSpec>(normalizeInvFormTripleSpec(props.initialTripleSpec));
const serviceApplyPaperSize = ref<ServiceApplyPaperSize>(
  normalizeServiceApplyPaperSize(props.initialServiceApplyPaperSize),
);
/** 确认/取消只结算一次，避免关窗时 @close 再触发 cancel */
let settled = false;

const dialogTitle = computed(() => {
  const label = props.bindingLabel?.trim();
  const base = props.title;
  if (label) {
    return `${base} · ${label}`;
  }
  return base;
});

const showAnyPaper = computed(
  () => props.showPaperKind || props.showServiceApplyPaperSize,
);

const dialogWidth = computed(() => (showAnyPaper.value ? '560px' : '480px'));

const troubleshootingMessage = PRINT_PLUGIN_INSTALL_MESSAGE;

/** 将外部传入的初始值同步到表单 */
function syncFormFromProps(): void {
  selectedPrinter.value = props.initialPrinterName?.trim() ?? '';
  skipPrompt.value = props.initialSkipPrompt !== false;
  paperKind.value = normalizeInvFormPaperKind(props.initialPaperKind);
  tripleSpec.value = normalizeInvFormTripleSpec(props.initialTripleSpec);
  serviceApplyPaperSize.value = normalizeServiceApplyPaperSize(props.initialServiceApplyPaperSize);
}

/** 打印机列表变化时补全默认选中项（优先沿用已保存的打印机） */
function ensureSelectedPrinter(): void {
  if (selectedPrinter.value && props.printers.some((item) => item.name === selectedPrinter.value)) {
    return;
  }
  const saved = props.initialPrinterName?.trim() ?? '';
  if (saved && props.printers.some((item) => item.name === saved)) {
    selectedPrinter.value = saved;
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
      settled = false;
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
  () =>
    [
      props.initialPrinterName,
      props.initialSkipPrompt,
      props.initialPaperKind,
      props.initialTripleSpec,
      props.initialServiceApplyPaperSize,
    ] as const,
  () => {
    if (dialogVisible.value) {
      syncFormFromProps();
      ensureSelectedPrinter();
    }
  },
);

/** 关闭弹窗并通知取消 */
function handleCancel(): void {
  if (settled) {
    return;
  }
  settled = true;
  dialogVisible.value = false;
  emit('cancel');
}

/** 校验后提交所选打印机（及纸张） */
function handleConfirm(): void {
  const printerName = selectedPrinter.value.trim();
  if (!printerName || settled) {
    return;
  }
  settled = true;
  dialogVisible.value = false;
  emit('confirm', {
    printerName,
    skipPrompt: props.showSkipPrompt ? skipPrompt.value : props.initialSkipPrompt !== false,
    paperKind: props.showPaperKind ? paperKind.value : undefined,
    tripleSpec: props.showPaperKind ? tripleSpec.value : undefined,
    serviceApplyPaperSize: props.showServiceApplyPaperSize
      ? serviceApplyPaperSize.value
      : undefined,
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
    :width="dialogWidth"
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
      <template v-if="showPaperKind">
        <div class="mb-2 text-sm text-[var(--el-text-color-regular)]">纸张</div>
        <InvFormPaperKindCards v-model="paperKind" v-model:triple-spec="tripleSpec" class="mb-4" />
      </template>
      <template v-else-if="showServiceApplyPaperSize">
        <div class="mb-2 text-sm text-[var(--el-text-color-regular)]">纸张</div>
        <ElRadioGroup v-model="serviceApplyPaperSize" class="service-apply-paper-options mb-4">
          <ElRadio :value="SERVICE_APPLY_PAPER_SIZE.a4" border>
            {{ SERVICE_APPLY_PAPER_SIZE_LABELS[SERVICE_APPLY_PAPER_SIZE.a4] }}
            <span class="service-apply-paper-hint">210×297mm 竖版</span>
          </ElRadio>
          <ElRadio :value="SERVICE_APPLY_PAPER_SIZE.a5" border>
            {{ SERVICE_APPLY_PAPER_SIZE_LABELS[SERVICE_APPLY_PAPER_SIZE.a5] }}
            <span class="service-apply-paper-hint">148×210mm 竖版</span>
          </ElRadio>
        </ElRadioGroup>
      </template>
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
      <ElCheckbox v-if="showSkipPrompt" v-model="skipPrompt">
        记住本次选择，下次不再提示
      </ElCheckbox>
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

<style scoped>
.service-apply-paper-options {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: 100%;
}

.service-apply-paper-options :deep(.el-radio) {
  width: 100%;
  margin-right: 0;
  height: auto;
  padding: 12px 16px;
}

.service-apply-paper-hint {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
