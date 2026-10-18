<script setup lang="ts">
import type { JSONContent } from '@tiptap/core';

import type { FormItemOption } from '@zxplat-core/table-layout-ui';

import type { TemplateContentDto, TemplateDto } from '#/api/emr/template';
import type { EmrPrintPaperPayload, GenerateDocumentPdfParams } from '#/api/emr/document';

import { getOrganizationApiByLoginUser } from '#/api/core/auth';
import { generateDocumentPdfApi } from '#/api/emr/document';
import {
  createTemplateApi,
  getTemplateContentApi,
  serializeEmrTemplateContent,
  updateTemplateApi,
} from '#/api/emr/template';
import { unwrapApiList } from '#/utils/api/unwrap-api-list';
import {
  preserveApiIdForRequest,
  resolveLoginPractitionerIdForRequest,
} from '#/utils/format/preserve-api-id';

import { computed, nextTick, reactive, ref, watch } from 'vue';

import { useUserStore } from '@zxplat/stores';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
  ElSwitch,
} from 'element-plus';

import EmrPdfPreviewDialog from '#/components/outpatient/emr-pdf-preview-dialog.vue';
import { SimpleEditor, normalizeEmrRootDoc, useEmrMetadata } from '#/emrEditor';
import {
  DEFAULT_EDITOR_PAGE_MARGINS,
  defaultEmrEditorDoc,
} from '#/emrEditor/components/simple-editor/editor-defaults';
import {
  createDefaultEmrPageChrome,
  type EmrPageChrome,
} from '#/emrEditor/components/simple-editor/emr-page-chrome';
import {
  packEmrDocWithSidecar,
  readEmrDocSidecar,
  stripEmrDocSidecar,
} from '#/emrEditor/components/simple-editor/emr-doc-sidecar';
import {
  buildEmrPrintConfigPayload,
  collectScreenPrintMetricsFromDom,
} from '#/emrEditor/components/simple-editor/emr-print-config';
import { resolvePrintHospitalName } from '#/utils/print/resolve-print-hospital-name';

import {
  EMR_TEMPLATE_ENCOUNTER_CLASS,
  EMR_TEMPLATE_ENCOUNTER_CLASS_OPTIONS,
  EMR_TEMPLATE_SCOPE_LEVEL,
  EMR_TEMPLATE_SCOPE_LEVEL_OPTIONS,
} from './emr-template-scope.constants';

const userStore = useUserStore();

const { metadataFields: templateMetadataFields } = useEmrMetadata({
  loadValues: false,
  loadFieldDefinitions: true,
});

const organizationOptions = ref<Array<{ label: string; value: string }>>([]);

const loginPractitionerId = computed(() =>
  resolveLoginPractitionerIdForRequest(userStore.userInfo as Record<string, unknown> | undefined),
);

/** 保存成功后通知列表页刷新 */
const emit = defineEmits<{
  success: [];
}>();

const visible = ref(false);
const dialogTitle = ref('新建模板');

/** create / edit / view；列表「复制」在内容加载完成后会切回 create（仍走创建接口保存） */
const mode = ref<'create' | 'edit' | 'view'>('create');
/** 当前正在编辑的模板 ID；纯新建或复制预填完成后为 null */
const templateId = ref<null | string>(null);
/** 从列表进「编辑」时锁定编码不可改；复制得到的新模板不锁，可改编码 */
const codeLocked = ref(false);

/** 与后端模板字段对齐：名称、编码、文书类型、启用 */
const form = reactive({
  code: '',
  documentTypeCode: '' as string,
  enabled: true,
  name: '',
  scopeLevel: EMR_TEMPLATE_SCOPE_LEVEL.HOSPITAL as string,
  encounterClassCode: EMR_TEMPLATE_ENCOUNTER_CLASS.AMB as string,
  organizationId: '' as string,
  ownerPractitionerId: '' as string,
});

const showOrganizationField = computed(
  () => form.scopeLevel === EMR_TEMPLATE_SCOPE_LEVEL.DEPARTMENT,
);

const showOwnerField = computed(() => form.scopeLevel === EMR_TEMPLATE_SCOPE_LEVEL.PERSONAL);

watch(
  () => form.scopeLevel,
  (scope) => {
    if (scope === EMR_TEMPLATE_SCOPE_LEVEL.HOSPITAL) {
      form.organizationId = '';
      form.ownerPractitionerId = '';
    } else if (scope === EMR_TEMPLATE_SCOPE_LEVEL.DEPARTMENT) {
      form.ownerPractitionerId = '';
      if (!form.organizationId && organizationOptions.value.length === 1) {
        form.organizationId = organizationOptions.value[0]?.value ?? '';
      }
    } else if (scope === EMR_TEMPLATE_SCOPE_LEVEL.PERSONAL) {
      form.organizationId = '';
      form.ownerPractitionerId = loginPractitionerId.value ?? '';
    }
  },
);

/** 模板正文：Tiptap JSON，与 SimpleEditor 双向绑定 */
const docJson = ref<JSONContent | null>(null);

/** 页边距：屏上默认贴近 Word；正式国标可切工具栏「打印标准」 */
const pageMargins = ref({ ...DEFAULT_EDITOR_PAGE_MARGINS });
const pageChrome = ref<EmrPageChrome>(createDefaultEmrPageChrome());
const editorRef = ref<InstanceType<typeof SimpleEditor> | null>(null);

const pdfPreviewVisible = ref(false);
const pdfPreviewBase64 = ref('');
const pdfPreviewPageCount = ref<number | undefined>();
const pdfPreviewTitle = ref('打印预览');
const pdfLoading = ref(false);

/** 文书类型下拉选项，由列表页传入并可在字典加载后更新 */
const documentTypeOptions = ref<FormItemOption[]>([]);

const documentTypeName = computed(() => {
  const code = form.documentTypeCode.trim();
  if (!code) return '';
  const hit = documentTypeOptions.value.find((o) => String(o.value) === code);
  return String(hit?.label ?? '').trim();
});

async function handlePrintPreview() {
  if (pdfLoading.value) return;
  editorRef.value?.exitPageChromeEdit?.();
  await nextTick();
  const content = editorRef.value?.getJSON?.() ?? docJson.value;
  if (!content) {
    ElMessage.warning('没有可预览的内容');
    return;
  }
  pdfLoading.value = true;
  try {
    const result = await requestTemplatePdf(content);
    if (!result) return;
    pdfPreviewTitle.value = `${form.name.trim() || '模板'} · 预览`;
    pdfPreviewBase64.value = result.pdfBase64;
    pdfPreviewPageCount.value = result.pageCount;
    pdfPreviewVisible.value = true;
  } catch {
    // 拦截器已提示
  } finally {
    pdfLoading.value = false;
  }
}

/** 按当前模板正文生成 PDF；paper 来自预览弹窗 */
async function requestTemplatePdf(content: JSONContent, paper?: EmrPrintPaperPayload) {
  const orgName = resolvePrintHospitalName();
  const metadata: Record<string, string> = {};
  if (orgName) {
    // 院名字段在模板里多为 orgName；兼容 hospitalName
    metadata.orgName = orgName;
    metadata.hospitalName = orgName;
  }
  const pm = editorRef.value?.getEditor?.()?.view?.dom;
  const screenMetrics = pm instanceof HTMLElement ? collectScreenPrintMetricsFromDom(pm) : null;
  const params: GenerateDocumentPdfParams = {
    content: applyPreviewMetadataToContent(content, metadata),
    previewMode: true,
    documentTitle: (form.name.trim() || '模板').trim(),
    documentTypeName: documentTypeName.value || undefined,
    metadata: Object.keys(metadata).length > 0 ? metadata : undefined,
    printConfig: buildEmrPrintConfigPayload(pageMargins.value, pageChrome.value, {
      isOutpatientRecord: form.documentTypeCode.trim() === 'OUTPATIENT_RECORD',
      screenMetrics,
      paper,
    }),
  };
  const result = await generateDocumentPdfApi(params);
  if (!result?.pdfBase64?.trim()) {
    ElMessage.warning('未生成 PDF 内容');
    return null;
  }
  return result;
}

/** 预览弹窗改纸张：重出 PDF，不改模板编辑器纸面 */
async function onPreviewPaperChange(paper: EmrPrintPaperPayload) {
  if (pdfLoading.value) return;
  const content = editorRef.value?.getJSON?.() ?? docJson.value;
  if (!content) return;
  pdfLoading.value = true;
  try {
    const result = await requestTemplatePdf(content, paper);
    if (!result) return;
    pdfPreviewBase64.value = result.pdfBase64;
    pdfPreviewPageCount.value = result.pageCount;
  } catch {
    // 拦截器已提示
  } finally {
    pdfLoading.value = false;
  }
}

/**
 * 模板预览：将 metadata 写入正文空数据元（不改编辑器原稿）。
 * 后端 PDF 也会按 metadata 回退取值；双端都填，避免院名空白。
 */
function applyPreviewMetadataToContent(
  content: JSONContent,
  metadata: Record<string, string>,
): JSONContent {
  if (!metadata || Object.keys(metadata).length === 0) {
    return content;
  }
  const clone = structuredClone(content) as JSONContent;
  const walk = (node: JSONContent) => {
    const type = String(node.type ?? '');
    if (type === 'emrDataElement' || type === 'emrMetadataChip') {
      const attrs = (node.attrs ?? {}) as Record<string, unknown>;
      const id = String(attrs.elementId ?? attrs.fieldKey ?? '').trim();
      const cur = String(attrs.value ?? '').trim();
      if (id && !cur && metadata[id]) {
        node.attrs = { ...attrs, value: metadata[id] };
      }
    }
    const children = node.content;
    if (Array.isArray(children)) {
      for (const child of children) {
        walk(child);
      }
    }
  };
  walk(clone);
  return clone;
}
/** 新建模板时的默认空文档结构 */
function cloneDefaultDoc(): JSONContent {
  return structuredClone(defaultEmrEditorDoc);
}

function resetPageMargins() {
  pageMargins.value = { ...DEFAULT_EDITOR_PAGE_MARGINS };
  pageChrome.value = createDefaultEmrPageChrome();
}

/** 将接口返回的 content 转成编辑器可用的 doc（空 text 清洗见 normalizeEmrRootDoc） */
function normalizeContent(raw: unknown): JSONContent {
  return normalizeEmrRootDoc(raw);
}

function closeDialog() {
  visible.value = false;
}

/** 列表「复制」专用：加载完成后切换为新建并预填名称/编码 */
const pendingCopyAfterLoad = ref(false);

const loadLoading = ref(false);
const saveLoadingRef = ref(false);

/** 从后端拉取模板详情（含正文 JSON） */
async function loadContentById(id: string) {
  loadLoading.value = true;
  try {
    const res = await getTemplateContentApi({ templateId: id });
    if (!res) {
      ElMessage.error('模板不存在或已被删除');
      pendingCopyAfterLoad.value = false;
      return;
    }
    applyContentDto(res);
    if (pendingCopyAfterLoad.value) {
      pendingCopyAfterLoad.value = false;
      applyCopyPrefillFromDto();
    }
  } catch (error) {
    console.error(error);
    pendingCopyAfterLoad.value = false;
    ElMessage.error('加载模板失败');
  } finally {
    loadLoading.value = false;
  }
}

/** 复制建议名称：空则用默认；已含「(副本)」则不再重复追加 */
function suggestCopyName(name: string) {
  const t = name.trim();
  if (!t) return '未命名 (副本)';
  if (t.includes('(副本)')) return t;
  return `${t} (副本)`;
}

/**
 * 复制建议编码：在原名后加 `_c` + 时间戳片段，满足库内唯一性；总长不超过 64。
 * 会先去掉末尾 `_c数字` 再拼新后缀，避免多次复制把编码撑爆上限。
 */
function suggestCopyCode(code: string) {
  const base = (code.trim() || 'tpl').replace(/_c\d{6,}$/, '');
  const suffix = `_c${String(Date.now()).slice(-8)}`;
  const max = 64;
  const combined = base + suffix;
  if (combined.length <= max) return combined;
  return `${base.slice(0, Math.max(0, max - suffix.length))}${suffix}`;
}

/** getContent 成功后：清空 ID、切新建态、名称编码按复制规则生成（保存走 create） */
function applyCopyPrefillFromDto() {
  templateId.value = null;
  mode.value = 'create';
  codeLocked.value = false;
  form.name = suggestCopyName(form.name);
  form.code = suggestCopyCode(form.code);
  dialogTitle.value = '新建模板（复制）';
}

/** 将详情 DTO 写入表单并刷新编辑器文档 */
function applyContentDto(dto: TemplateContentDto) {
  form.name = dto.name ?? '';
  form.code = dto.code ?? '';
  form.documentTypeCode = dto.documentTypeCode ?? '';
  form.enabled = dto.isEnabled !== false && dto.enabled !== false;
  form.scopeLevel = dto.scopeLevel ?? EMR_TEMPLATE_SCOPE_LEVEL.HOSPITAL;
  form.encounterClassCode = dto.encounterClassCode ?? EMR_TEMPLATE_ENCOUNTER_CLASS.AMB;
  form.organizationId = preserveApiIdForRequest(dto.organizationId) ?? '';
  form.ownerPractitionerId =
    preserveApiIdForRequest(dto.ownerPractitionerId) ?? loginPractitionerId.value ?? '';
  docJson.value = stripEmrDocSidecar(normalizeContent(dto.content));
  const sidecar = readEmrDocSidecar(dto.content);
  pageMargins.value = { ...sidecar.pageMargins };
  pageChrome.value = sidecar.pageChrome;
}

/** 打开纯新建弹窗时重置表单与文档 */
function resetCreate() {
  templateId.value = null;
  codeLocked.value = false;
  form.name = '';
  form.code = '';
  form.documentTypeCode = '';
  form.enabled = true;
  form.scopeLevel = EMR_TEMPLATE_SCOPE_LEVEL.HOSPITAL;
  form.encounterClassCode = EMR_TEMPLATE_ENCOUNTER_CLASS.AMB;
  form.organizationId = '';
  form.ownerPractitionerId = loginPractitionerId.value ?? '';
  docJson.value = cloneDefaultDoc();
  resetPageMargins();
}

async function loadOrganizationOptions() {
  try {
    const response = await getOrganizationApiByLoginUser();
    const list = unwrapApiList<{ id?: number | string; name?: string; displayName?: string }>(
      response,
    );
    organizationOptions.value = list
      .map((item) => {
        const value = preserveApiIdForRequest(item.id);
        if (!value) return null;
        const label = (item.name ?? item.displayName ?? value).trim() || value;
        return { label, value };
      })
      .filter((x): x is { label: string; value: string } => x != null);
  } catch {
    organizationOptions.value = [];
  }
}

/** 供父组件在文书类型字典就绪后注入下拉选项 */
function setDocumentTypeOptions(options: FormItemOption[]) {
  documentTypeOptions.value = options;
}

const readOnly = () => mode.value === 'view';

const saveLoading = () => saveLoadingRef.value;

/** 仅在有源模板且非新建态时展示（查看/编辑下可复制为新模板） */
const showCopyInDialog = () => templateId.value !== null && mode.value !== 'create';

/**
 * 弹窗内「复制为新模板」：不调后端 copy 接口，基于当前表单与文档深拷贝后切到新建，
 * 用户改名称/编码后点保存即 create。
 */
function handleCopyAsNew() {
  if (templateId.value === null) return;
  form.name = suggestCopyName(form.name);
  form.code = suggestCopyCode(form.code);
  templateId.value = null;
  mode.value = 'create';
  codeLocked.value = false;
  docJson.value = docJson.value ? structuredClone(docJson.value) : cloneDefaultDoc();
  dialogTitle.value = '新建模板（复制）';
  ElMessage.success('已带入当前内容，修改名称/编码后保存即可新建');
}

/**
 * 打开弹窗。
 * - create：空白表单
 * - edit/view：带 row.id 拉 getContent
 * - copy：用 row.id 只拉内容，加载完成后切换为新建并预填（保存仍 create）
 */
async function open(options: {
  documentTypeOptions?: FormItemOption[];
  mode: 'copy' | 'create' | 'edit' | 'view';
  row?: TemplateDto;
}) {
  if (options.documentTypeOptions) {
    setDocumentTypeOptions(options.documentTypeOptions);
  }
  await loadOrganizationOptions();

  pendingCopyAfterLoad.value = false;

  if (options.mode === 'create') {
    mode.value = 'create';
    visible.value = true;
    codeLocked.value = false;
    dialogTitle.value = '新建病历模板';
    resetCreate();
    return;
  }

  const id = options.row?.id;
  if (id === undefined || id === null || id === '') {
    ElMessage.error('缺少模板 ID');
    return;
  }
  const strId = String(id).trim();
  if (!strId) {
    ElMessage.error('模板 ID 无效');
    return;
  }

  if (options.mode === 'copy') {
    visible.value = true;
    pendingCopyAfterLoad.value = true;
    mode.value = 'create';
    codeLocked.value = false;
    templateId.value = null;
    dialogTitle.value = `复制模板 · ${options.row?.name ?? strId}`;
    form.name = options.row?.name ?? '';
    form.code = options.row?.code ?? '';
    form.documentTypeCode = options.row?.documentTypeCode ?? '';
    form.enabled = options.row?.isEnabled !== false;
    docJson.value = null;
    await loadContentById(strId);
    return;
  }

  mode.value = options.mode;
  visible.value = true;
  codeLocked.value = options.mode === 'edit';
  templateId.value = strId;
  dialogTitle.value =
    options.mode === 'view'
      ? `查看模板 · ${options.row?.name ?? strId}`
      : `编辑模板 · ${options.row?.name ?? strId}`;
  form.name = options.row?.name ?? '';
  form.code = options.row?.code ?? '';
  form.documentTypeCode = options.row?.documentTypeCode ?? '';
  form.enabled = options.row?.isEnabled !== false;
  // 等 getContent 返回再挂载编辑器，避免先空文档再 setContent 失败时一直空白
  docJson.value = null;
  await loadContentById(strId);
}

/** 提交到 zxplat-emr TemplateAppService（只读不保存） */
async function handleSave() {
  if (readOnly()) return;
  editorRef.value?.exitPageChromeEdit?.();
  await nextTick();
  const name = form.name.trim();
  const code = form.code.trim();
  if (!name) {
    ElMessage.warning('请填写模板名称');
    return;
  }
  if (!code) {
    ElMessage.warning('请填写模板编码');
    return;
  }
  const contentJson = serializeEmrTemplateContent(
    packEmrDocWithSidecar(docJson.value ?? cloneDefaultDoc(), {
      pageChrome: pageChrome.value,
      pageMargins: pageMargins.value,
    }),
  );
  const documentTypeCode = form.documentTypeCode.trim() || undefined;
  const scopeLevel = form.scopeLevel.trim();
  const encounterClassCode = form.encounterClassCode.trim();
  if (!encounterClassCode) {
    ElMessage.warning('请选择适用就诊类别');
    return;
  }
  if (!scopeLevel) {
    ElMessage.warning('请选择使用范围');
    return;
  }
  if (scopeLevel === EMR_TEMPLATE_SCOPE_LEVEL.DEPARTMENT && !form.organizationId.trim()) {
    ElMessage.warning('科室模板请选择适用科室');
    return;
  }
  if (scopeLevel === EMR_TEMPLATE_SCOPE_LEVEL.PERSONAL && !form.ownerPractitionerId.trim()) {
    ElMessage.warning('个人模板缺少医务人员 ID，请确认账号已关联 practitioner');
    return;
  }

  const scopePayload = {
    scopeLevel,
    encounterClassCode,
    organizationId:
      scopeLevel === EMR_TEMPLATE_SCOPE_LEVEL.DEPARTMENT ? form.organizationId : undefined,
    ownerPractitionerId:
      scopeLevel === EMR_TEMPLATE_SCOPE_LEVEL.PERSONAL ? form.ownerPractitionerId : undefined,
  };

  const isCreate = mode.value === 'create';
  saveLoadingRef.value = true;
  try {
    if (isCreate) {
      await createTemplateApi({
        code,
        content: contentJson,
        documentTypeCode,
        enabled: form.enabled,
        name,
        ...scopePayload,
      });
    } else {
      if (templateId.value === null) return;
      await updateTemplateApi({
        code,
        content: contentJson,
        documentTypeCode,
        enabled: form.enabled,
        id: templateId.value,
        name,
        ...scopePayload,
      });
    }
    ElMessage.success(isCreate ? '新建成功' : '保存成功');
    closeDialog();
    emit('success');
  } catch (error) {
    const msg = error instanceof Error ? error.message : '保存失败';
    ElMessage.error(msg);
  } finally {
    saveLoadingRef.value = false;
  }
}

defineExpose({
  open,
  setDocumentTypeOptions,
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
    class="emr-template-editor-dialog"
  >
    <div v-loading="loadLoading" class="emr-template-editor-dialog__body">
      <aside class="emr-template-editor-dialog__aside">
        <div class="emr-template-editor-dialog__aside-title">模板信息</div>
        <ElForm :model="form" label-position="top" class="emr-template-editor-dialog__form">
          <ElFormItem label="模板名称" required class="!mb-3">
            <ElInput
              v-model="form.name"
              placeholder="请输入名称"
              :disabled="readOnly()"
              maxlength="128"
              show-word-limit
            />
          </ElFormItem>
          <ElFormItem label="模板编码" required class="!mb-3">
            <ElInput
              v-model="form.code"
              placeholder="唯一编码"
              :disabled="readOnly() || (mode === 'edit' && codeLocked)"
              maxlength="64"
              show-word-limit
            />
          </ElFormItem>
          <ElFormItem label="文书类型" class="!mb-3">
            <ElSelect
              v-model="form.documentTypeCode"
              placeholder="可选"
              clearable
              filterable
              :disabled="readOnly()"
              class="w-full"
            >
              <ElOption
                v-for="opt in documentTypeOptions"
                :key="String(opt.value)"
                :label="opt.label"
                :value="opt.value as string"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="适用就诊" required class="!mb-3">
            <ElSelect
              v-model="form.encounterClassCode"
              placeholder="门诊 / 住院"
              :disabled="readOnly()"
              class="w-full"
            >
              <ElOption
                v-for="opt in EMR_TEMPLATE_ENCOUNTER_CLASS_OPTIONS"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="使用范围" required class="!mb-3">
            <ElSelect
              v-model="form.scopeLevel"
              placeholder="个人 / 科室 / 全院"
              :disabled="readOnly()"
              class="w-full"
            >
              <ElOption
                v-for="opt in EMR_TEMPLATE_SCOPE_LEVEL_OPTIONS"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem v-if="showOrganizationField" label="适用科室" required class="!mb-3">
            <ElSelect
              v-model="form.organizationId"
              placeholder="选择科室"
              filterable
              :disabled="readOnly()"
              class="w-full"
            >
              <ElOption
                v-for="opt in organizationOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem v-if="showOwnerField" label="所有者" class="!mb-3">
            <ElInput v-model="form.ownerPractitionerId" disabled placeholder="当前医务人员" />
          </ElFormItem>
          <ElFormItem label="启用" class="!mb-0">
            <ElSwitch v-model="form.enabled" :disabled="readOnly()" />
          </ElFormItem>
        </ElForm>
      </aside>

      <div class="emr-template-editor-dialog__editor">
        <div v-if="loadLoading" class="emr-template-editor-dialog__loading">加载模板正文…</div>
        <SimpleEditor
          v-else-if="docJson"
          ref="editorRef"
          :key="(templateId ?? form.code) || 'new'"
          v-model="docJson"
          v-model:page-margins="pageMargins"
          v-model:page-chrome="pageChrome"
          :document-type-name="documentTypeName"
          :document-title="(documentTypeName || form.name || '').trim()"
          placeholder="编辑模板正文"
          surface="template"
          :read-only="readOnly()"
          :metadata-fields="templateMetadataFields"
          @print="handlePrintPreview"
        />
      </div>
    </div>

    <template #footer>
      <div class="emr-template-editor-dialog__footer">
        <ElButton :loading="pdfLoading" :disabled="!docJson" @click="handlePrintPreview">
          打印预览
        </ElButton>
        <ElButton v-if="showCopyInDialog()" @click="handleCopyAsNew">复制为新模板</ElButton>
        <ElButton @click="closeDialog">取消</ElButton>
        <ElButton v-if="!readOnly()" type="primary" :loading="saveLoading()" @click="handleSave">
          保存
        </ElButton>
      </div>
    </template>
  </ElDialog>

  <EmrPdfPreviewDialog
    v-model="pdfPreviewVisible"
    :pdf-base64="pdfPreviewBase64"
    :page-count="pdfPreviewPageCount"
    :title="pdfPreviewTitle"
    :loading="pdfLoading"
    @paper-change="onPreviewPaperChange"
  />
</template>

<style scoped>
.emr-template-editor-dialog__body {
  display: flex;
  min-height: 0;
  flex: 1;
  gap: 12px;
  overflow: hidden;
}

.emr-template-editor-dialog__aside {
  display: flex;
  width: 248px;
  flex-shrink: 0;
  flex-direction: column;
  overflow: auto;
  padding-right: 12px;
  border-right: 1px solid var(--el-border-color-lighter);
}

.emr-template-editor-dialog__aside-title {
  flex-shrink: 0;
  margin-bottom: 12px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.emr-template-editor-dialog__form :deep(.el-form-item__label) {
  padding-bottom: 4px;
  line-height: 1.3;
}

.emr-template-editor-dialog__editor {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  overflow: hidden;
}

.emr-template-editor-dialog__loading {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

.emr-template-editor-dialog__editor :deep(.simple-editor) {
  height: 100%;
  min-height: 0;
  flex: 1;
}
</style>

<style>
/* 全屏弹窗：header / body / footer 纵向 flex，正文区占满剩余高度 */
.emr-template-editor-dialog.el-dialog.is-fullscreen {
  display: flex;
  flex-direction: column;
  margin: 0;
}

.emr-template-editor-dialog.is-fullscreen .el-dialog__header {
  flex-shrink: 0;
  margin-right: 0;
  padding: 6px 12px 4px;
}

.emr-template-editor-dialog.is-fullscreen .el-dialog__title {
  font-size: 15px;
  line-height: 1.25;
}

.emr-template-editor-dialog.is-fullscreen .el-dialog__headerbtn {
  top: 6px;
  width: 32px;
  height: 32px;
}

.emr-template-editor-dialog.is-fullscreen .el-dialog__body {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  overflow: hidden;
  padding: 4px 12px 6px;
}

.emr-template-editor-dialog.is-fullscreen .el-dialog__footer {
  flex-shrink: 0;
  min-height: 0;
  padding: 0 16px 8px;
}

.emr-template-editor-dialog__footer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  margin: 0;
  padding: 0;
  min-height: 0;
}
</style>
