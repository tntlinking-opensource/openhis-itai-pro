<script setup lang="ts">
import type { ReportTemplateListItemDto } from '#/api/report/report-design';

import { computed, ref, watch } from 'vue';

import { ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElMessage } from 'element-plus';

import { useReportTemplatePrint } from '#/composables/use-report-template-print';

defineOptions({ name: 'ReportTemplateFormalPrintDialog' });

const visible = ref(false);
const activeRow = ref<ReportTemplateListItemDto | null>(null);
const encounterId = ref('');

const { isPrinting, printReportTemplate } = useReportTemplatePrint();

const dialogTitle = computed(
  () => `正式打印 — ${activeRow.value?.name ?? activeRow.value?.code ?? ''}`,
);

watch(visible, (open) => {
  if (!open) {
    activeRow.value = null;
    encounterId.value = '';
  }
});

async function open(row: ReportTemplateListItemDto) {
  const code = row.code?.trim();
  if (!code) {
    return;
  }
  activeRow.value = row;
  encounterId.value = '';
  visible.value = true;
}

async function handlePrint() {
  const code = activeRow.value?.code?.trim();
  if (!code) {
    ElMessage.warning('模板编码为空');
    return;
  }
  const id = encounterId.value.trim();
  if (!id) {
    ElMessage.warning('请输入就诊 ID（encounterId）');
    return;
  }
  await printReportTemplate({
    templateCode: code,
    encounterId: id,
    datasetCode: activeRow.value?.datasetCode,
    jobName: activeRow.value?.name ?? code,
  });
}

defineExpose({ open });
</script>

<template>
  <ElDialog v-model="visible" :title="dialogTitle" width="420px" destroy-on-close align-center>
    <ElForm label-width="88px" @submit.prevent="handlePrint">
      <ElFormItem label="就诊 ID" required>
        <ElInput
          v-model="encounterId"
          placeholder="输入 encounterId 后打印真实业务数据"
          clearable
        />
      </ElFormItem>
      <p class="text-xs leading-relaxed text-gray-500">
        无专用业务页入口的 B 类模板，可在此输入就诊主键触发正式打印（数据集按模板接线加载）。
      </p>
    </ElForm>
    <template #footer>
      <ElButton @click="visible = false">取消</ElButton>
      <ElButton type="primary" :loading="isPrinting" @click="handlePrint">打印</ElButton>
    </template>
  </ElDialog>
</template>
