<script lang="ts" setup>
import type { FunctionEditorContext } from '../core/types';

import { computed, ref } from 'vue';

import { ElButton, ElDialog, ElInput, ElMessage } from 'element-plus';

import { FORMATTER_SNIPPETS } from '../presets/formatter-snippets';

defineOptions({ name: 'HiprintFunctionCodeDialog' });

const visible = ref(false);
const codeText = ref('');
const context = ref<FunctionEditorContext | null>(null);

const dialogTitle = computed(() => context.value?.label ?? '编辑函数');

const filteredSnippets = computed(() => {
  const optionName = context.value?.optionName;
  if (!optionName) {
    return FORMATTER_SNIPPETS;
  }
  return FORMATTER_SNIPPETS.filter((s) => !s.options?.length || s.options.includes(optionName));
});

/** 打开函数编辑器 */
function open(ctx: FunctionEditorContext): void {
  context.value = ctx;
  codeText.value = ctx.value ?? '';
  visible.value = true;
}

/** 应用函数代码到属性面板 */
function handleApply(): void {
  const trimmed = codeText.value.trim();
  if (trimmed && !trimmed.startsWith('function')) {
    ElMessage.warning('函数须以 function 开头');
    return;
  }
  context.value?.apply(trimmed);
  visible.value = false;
}

/** 插入代码片段 */
function insertSnippet(code: string): void {
  codeText.value = code;
}

defineExpose({ open });
</script>

<template>
  <ElDialog v-model="visible" destroy-on-close :title="dialogTitle" width="820px">
    <div class="fn-editor-layout">
      <aside class="fn-snippets">
        <div class="fn-snippets-title">代码片段</div>
        <ElButton
          v-for="item in filteredSnippets"
          :key="item.name"
          class="snippet-btn"
          size="small"
          @click="insertSnippet(item.code)"
        >
          {{ item.name }}
        </ElButton>
      </aside>
      <ElInput
        v-model="codeText"
        :autosize="{ minRows: 18, maxRows: 24 }"
        class="fn-code"
        placeholder="function(title, value, options, templateData) { ... }"
        type="textarea"
      />
    </div>
    <p class="fn-hint">
      格式化函数签名：(title, value, options, templateData)；样式函数：(value, options, target,
      templateData)
    </p>
    <template #footer>
      <ElButton @click="visible = false">取消</ElButton>
      <ElButton type="primary" @click="handleApply">应用</ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.fn-editor-layout {
  display: flex;
  gap: 12px;
}

.fn-snippets {
  width: 160px;
  flex-shrink: 0;
}

.fn-snippets-title {
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.snippet-btn {
  display: block;
  width: 100%;
  margin: 0 0 6px;
}

.fn-code {
  flex: 1;
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 13px;
}

.fn-hint {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
