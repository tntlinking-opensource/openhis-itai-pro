<script setup lang="ts">
import type { TemplateDto } from '#/api/emr/template';

import { computed, ref } from 'vue';

import { ElButton, ElDialog, ElInput, ElMessage } from 'element-plus';

import {
  getTemplateContentApi,
  serializeEmrTemplateContent,
  updateTemplateApi,
} from '#/api/emr/template';
import { normalizeEmrRootDoc } from '#/emrEditor';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

defineOptions({
  name: 'EmrTemplateJsonDialog',
});

const emit = defineEmits<{
  success: [];
}>();

const visible = ref(false);
const dialogTitle = ref('编辑模板 JSON');
const templateId = ref('');
const readOnly = ref(false);
const jsonText = ref('');
const jsonError = ref('');
const loadLoading = ref(false);
const saveLoading = ref(false);

const hasJsonError = computed(() => Boolean(jsonError.value));

/** 将接口 content 规范化为可编辑 JSON 文本 */
function buildJsonTextFromContent(raw: unknown): string {
  const doc = normalizeEmrRootDoc(raw);
  return JSON.stringify(doc, null, 2);
}

/** 解析当前 JSON 文本为文档对象 */
function parseJsonText(): Record<string, unknown> | null {
  try {
    const parsed = JSON.parse(jsonText.value) as unknown;
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      throw new Error('根节点须为 JSON 对象');
    }
    jsonError.value = '';
    return normalizeEmrRootDoc(parsed) as Record<string, unknown>;
  } catch (error) {
    jsonError.value = error instanceof Error ? error.message : 'JSON 格式无效';
    return null;
  }
}

/** 从后端加载模板正文并回显到 JSON 编辑器 */
async function loadTemplateContent(id: string) {
  loadLoading.value = true;
  jsonText.value = '';
  jsonError.value = '';
  try {
    const res = await getTemplateContentApi({ templateId: id });
    if (!res) {
      ElMessage.error('模板不存在或已被删除');
      visible.value = false;
      return;
    }
    jsonText.value = buildJsonTextFromContent(res.content);
  } catch (error) {
    console.error(error);
    ElMessage.error('加载模板 JSON 失败');
    visible.value = false;
  } finally {
    loadLoading.value = false;
  }
}

/** 格式化 JSON 文本 */
function handleFormatJson() {
  const doc = parseJsonText();
  if (!doc) {
    ElMessage.error(jsonError.value || 'JSON 格式无效');
    return;
  }
  jsonText.value = JSON.stringify(doc, null, 2);
  ElMessage.success('已格式化');
}

/** 保存 JSON 到模板正文 */
async function handleSave() {
  if (readOnly.value) return;
  const doc = parseJsonText();
  if (!doc) {
    ElMessage.error(jsonError.value || 'JSON 格式无效，无法保存');
    return;
  }
  const id = templateId.value.trim();
  if (!id) {
    ElMessage.error('缺少模板 ID');
    return;
  }

  saveLoading.value = true;
  try {
    await updateTemplateApi({
      id,
      content: serializeEmrTemplateContent(doc),
    });
    ElMessage.success('保存成功');
    visible.value = false;
    emit('success');
  } catch (error) {
    const msg = error instanceof Error ? error.message : '保存失败';
    ElMessage.error(msg);
  } finally {
    saveLoading.value = false;
  }
}

/** 供列表页打开：加载指定模板 JSON */
async function open(row: TemplateDto, options?: { readOnly?: boolean }) {
  const id = preserveApiIdForRequest(row.id);
  if (!id) {
    ElMessage.error('缺少模板 ID');
    return;
  }
  readOnly.value = options?.readOnly === true;
  templateId.value = id;
  dialogTitle.value = readOnly.value
    ? `查看模板 JSON · ${row.name ?? id}`
    : `编辑模板 JSON · ${row.name ?? id}`;
  visible.value = true;
  await loadTemplateContent(id);
}

defineExpose({
  open,
});
</script>

<template>
  <ElDialog
    v-model="visible"
    :title="dialogTitle"
    fullscreen
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
    class="emr-template-json-dialog"
  >
    <div v-loading="loadLoading" class="emr-template-json-dialog__body">
      <div v-if="!readOnly" class="emr-template-json-dialog__toolbar">
        <ElButton size="small" @click="handleFormatJson">格式化</ElButton>
      </div>
      <ElInput
        v-model="jsonText"
        class="emr-template-json-dialog__input"
        type="textarea"
        :readonly="readOnly"
        placeholder="模板正文 JSON（Tiptap doc 结构）"
        :autosize="false"
      />
      <p v-if="hasJsonError" class="emr-template-json-dialog__error">{{ jsonError }}</p>
    </div>
    <template #footer>
      <ElButton @click="visible = false">{{ readOnly ? '关闭' : '取消' }}</ElButton>
      <ElButton v-if="!readOnly" type="primary" :loading="saveLoading" @click="handleSave">
        保存
      </ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.emr-template-json-dialog__body {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  overflow: hidden;
}

.emr-template-json-dialog__toolbar {
  display: flex;
  flex-shrink: 0;
  justify-content: flex-end;
  margin-bottom: 8px;
}

.emr-template-json-dialog__input {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
}

.emr-template-json-dialog__input :deep(textarea) {
  height: 100% !important;
  min-height: 360px;
  resize: none;
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.5;
}

.emr-template-json-dialog__error {
  flex-shrink: 0;
  margin: 8px 0 0;
  color: var(--el-color-danger);
  font-size: 13px;
}
</style>

<style>
.emr-template-json-dialog.el-dialog.is-fullscreen {
  display: flex;
  flex-direction: column;
  margin: 0;
}

.emr-template-json-dialog.is-fullscreen .el-dialog__header {
  flex-shrink: 0;
  margin-right: 0;
  padding: 8px 16px 6px;
}

.emr-template-json-dialog.is-fullscreen .el-dialog__body {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  overflow: hidden;
  padding: 8px 16px;
}

.emr-template-json-dialog.is-fullscreen .el-dialog__footer {
  flex-shrink: 0;
  padding: 0 16px 12px;
}
</style>
