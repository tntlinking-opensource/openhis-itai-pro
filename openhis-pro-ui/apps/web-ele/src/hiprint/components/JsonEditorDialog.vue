<script lang="ts" setup>
import { computed, ref } from 'vue';

import { ElButton, ElDialog, ElInput, ElMessage } from 'element-plus';

defineOptions({ name: 'HiprintJsonEditorDialog' });

const emit = defineEmits<{
  apply: [value: Record<string, unknown>];
}>();
const visible = ref(false);
const jsonText = ref('');
const title = ref('编辑 JSON');
const errorMessage = ref('');

const hasError = computed(() => Boolean(errorMessage.value));

/** 打开 JSON 编辑器 */
function open(initial: Record<string, unknown>, dialogTitle = '编辑 JSON'): void {
  title.value = dialogTitle;
  jsonText.value = JSON.stringify(initial, null, 2);
  errorMessage.value = '';
  visible.value = true;
}

/** 校验并应用 JSON */
function handleApply(): void {
  try {
    const parsed = JSON.parse(jsonText.value) as Record<string, unknown>;
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      throw new Error('根节点须为对象');
    }
    errorMessage.value = '';
    emit('apply', parsed);
    visible.value = false;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'JSON 格式无效';
    ElMessage.error(errorMessage.value);
  }
}

defineExpose({ open });
</script>

<template>
  <ElDialog v-model="visible" destroy-on-close :title="title" width="720px">
    <ElInput
      v-model="jsonText"
      :autosize="{ minRows: 16, maxRows: 28 }"
      class="json-editor"
      type="textarea"
    />
    <p v-if="hasError" class="json-error">{{ errorMessage }}</p>
    <template #footer>
      <ElButton @click="visible = false">取消</ElButton>
      <ElButton type="primary" @click="handleApply">应用</ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.json-editor {
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 13px;
}

.json-error {
  margin-top: 8px;
  color: var(--el-color-danger);
}
</style>
