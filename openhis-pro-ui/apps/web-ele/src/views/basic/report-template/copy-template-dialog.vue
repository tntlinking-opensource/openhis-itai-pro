<script setup lang="ts">
import type { CopyTemplateAsNewRequest } from '#/api/report/report-template';

import { computed, reactive, ref, watch } from 'vue';

import { ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElOption, ElSelect } from 'element-plus';

import { copyTemplateAsNewApi } from '#/api/report/report-template';
import { useRequest } from '#/hooks/use-request';

import {
  formatReportLayoutMode,
  formatReportPipeline,
  REPORT_LAYOUT_MODE_COPY_OPTIONS,
  REPORT_PIPELINE_COPY_OPTIONS,
} from './report-template.constants';

defineOptions({ name: 'CopyTemplateDialog' });

export interface CopyTemplateSource {
  code: string;
  name?: string;
  pipeline?: string;
  layoutMode?: string;
}

const emit = defineEmits<{
  success: [templateCode: string];
}>();

const visible = ref(false);
const source = ref<CopyTemplateSource | null>(null);

const form = reactive({
  newTemplateCode: '',
  newTemplateName: '',
  layoutMode: 'flow' as 'flow' | 'overlay',
  pipeline: 'page' as 'page' | 'roll',
});

const { run: runCopy, loading } = useRequest(copyTemplateAsNewApi, { manual: true });

const sourceSummary = computed(() => {
  if (!source.value) {
    return '';
  }
  const mode = formatReportLayoutMode(source.value.layoutMode);
  const pipe = formatReportPipeline(source.value.pipeline);
  return `${source.value.name ?? source.value.code}（${pipe} / ${mode}）`;
});

function suggestCode(baseCode: string, layoutMode: string) {
  const suffix = layoutMode === 'overlay' ? '_overlay' : '_custom';
  let candidate = `${baseCode}${suffix}`;
  if (candidate.length > 64) {
    candidate = candidate.slice(0, 64);
  }
  return candidate;
}

function resetForm(row: CopyTemplateSource) {
  const layoutMode = row.layoutMode === 'overlay' ? 'overlay' : 'flow';
  const pipeline = row.pipeline === 'roll' ? 'roll' : 'page';
  form.newTemplateCode = suggestCode(row.code, layoutMode);
  form.newTemplateName = `${row.name ?? row.code}（${formatReportLayoutMode(layoutMode)}）`;
  form.layoutMode = layoutMode;
  form.pipeline = pipeline;
}

watch(visible, (open) => {
  if (!open) {
    source.value = null;
  }
});

watch(
  () => form.layoutMode,
  (mode) => {
    if (!source.value) {
      return;
    }
    form.newTemplateCode = suggestCode(source.value.code, mode);
    form.newTemplateName = `${source.value.name ?? source.value.code}（${formatReportLayoutMode(mode)}）`;
  },
);

function open(row: CopyTemplateSource) {
  const code = row.code?.trim();
  if (!code) {
    return;
  }
  source.value = { ...row, code };
  resetForm(source.value);
  visible.value = true;
}

async function handleSubmit() {
  if (!source.value?.code) {
    return;
  }
  const newCode = form.newTemplateCode.trim();
  if (!newCode) {
    return;
  }
  const payload: CopyTemplateAsNewRequest = {
    sourceTemplateCode: source.value.code,
    newTemplateCode: newCode,
    newTemplateName: form.newTemplateName.trim() || undefined,
    layoutMode: form.layoutMode,
    pipeline: form.pipeline,
  };
  await runCopy(payload);
  visible.value = false;
  emit('success', newCode);
}

defineExpose({ open });
</script>

<template>
  <ElDialog v-model="visible" title="复制为新模板" width="520px" destroy-on-close align-center>
    <p class="copy-template-dialog__source">源模板：{{ sourceSummary }}</p>
    <ElForm label-width="108px" size="default" @submit.prevent="handleSubmit">
      <ElFormItem label="新模板编码" required>
        <ElInput
          v-model="form.newTemplateCode"
          placeholder="字母开头，字母数字下划线"
          maxlength="64"
        />
      </ElFormItem>
      <ElFormItem label="新模板名称">
        <ElInput v-model="form.newTemplateName" maxlength="128" />
      </ElFormItem>
      <ElFormItem label="排版模式" required>
        <ElSelect v-model="form.layoutMode" style="width: 100%">
          <ElOption
            v-for="opt in REPORT_LAYOUT_MODE_COPY_OPTIONS"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="纸张管线" required>
        <ElSelect v-model="form.pipeline" style="width: 100%">
          <ElOption
            v-for="opt in REPORT_PIPELINE_COPY_OPTIONS"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </ElSelect>
      </ElFormItem>
      <p class="copy-template-dialog__hint">
        将创建院点草稿 v1。切换排版模式时会按目标元素包重排 items，并自动补全必填项与套打落格框。
      </p>
    </ElForm>
    <template #footer>
      <ElButton @click="visible = false">取消</ElButton>
      <ElButton type="primary" :loading="loading" @click="handleSubmit">创建并打开</ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.copy-template-dialog__source {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.copy-template-dialog__hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}
</style>
