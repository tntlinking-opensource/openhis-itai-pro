<script lang="ts" setup>
import { nextTick, ref } from 'vue';

import { ElButton, ElDialog } from 'element-plus';

import { createPreviewTemplate, renderPreviewHtml } from '../utils/preview-render';
import { buildPreviewPrintData } from '../utils/print-data';

import '../styles/sv-print-preview.css';

defineOptions({ name: 'HiprintPreviewDialog' });

const visible = ref(false);
const loading = ref(false);
const previewHtml = ref('');
const templateJson = ref<Record<string, unknown>>({});
const printData = ref<Record<string, unknown>>({});

/** 用 PrintJson + 数据渲染预览（非 design 态，避免版式错乱） */
async function show(json: Record<string, unknown>, data: Record<string, unknown>): Promise<void> {
  templateJson.value = json;
  printData.value = data;
  visible.value = true;
  loading.value = true;
  previewHtml.value = '';
  await nextTick();
  try {
    previewHtml.value = renderPreviewHtml(json, data);
  } catch {
    previewHtml.value = '<p style="padding:16px;color:#c0392b;">预览渲染失败</p>';
  } finally {
    loading.value = false;
  }
}

/** 浏览器打印（同样基于 PrintJson 重建实例，与预览一致） */
function handlePrint(): void {
  const tpl = createPreviewTemplate(templateJson.value);
  const mergedPrintData = buildPreviewPrintData(templateJson.value, {
    samplePrintData: printData.value,
  });
  tpl.print(
    mergedPrintData,
    {},
    {
      callback: () => {
        visible.value = false;
      },
    },
  );
}

defineExpose({ show });
</script>

<template>
  <ElDialog
    v-model="visible"
    class="hiprint-preview-dialog"
    destroy-on-close
    top="6vh"
    width="1560px"
  >
    <template #header>
      <div class="preview-dialog-header">
        <span class="preview-dialog-title">打印预览</span>
        <ElButton type="primary" @click="handlePrint">打印</ElButton>
      </div>
    </template>
    <div v-loading="loading" class="preview-wrap">
      <!-- hiprint 预览 HTML 为受控模板输出，非用户输入 -->
      <!-- eslint-disable-next-line vue/no-v-html -->
      <div class="preview-content" v-html="previewHtml"></div>
    </div>
  </ElDialog>
</template>

<style scoped>
.preview-wrap {
  max-height: calc(70vh + 90px);
  overflow: auto;
  background: var(--el-fill-color-light);
}

.preview-content {
  /* 底部留白避免滚到最底时纸张阴影被裁切 */
  padding: 16px 16px 24px;
}

.preview-dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-right: 32px;
}

.preview-dialog-title {
  font-size: 16px;
  font-weight: 600;
  line-height: 24px;
}
</style>

<style>
/* ElDialog teleport 到 body，需非 scoped */
.hiprint-preview-dialog.el-dialog {
  margin-bottom: 0;
}

.hiprint-preview-dialog .el-dialog__header {
  padding-bottom: 8px;
}

.hiprint-preview-dialog .el-dialog__body {
  padding-top: 4px;
  padding-bottom: 0;
}
</style>
