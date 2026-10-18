<script setup lang="ts">
import type { IEditorData } from '@hufe921/canvas-editor';

import type { FormItemOption } from '@zxplat-core/table-layout-ui';

import type { TemplateContentDto, TemplateDto } from '#/api/emr/template';

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

import { getOrganizationApiByLoginUser } from '#/api/core/auth';
import {
  createTemplateApi,
  getTemplateContentApi,
  serializeEmrTemplateContent,
  updateTemplateApi,
} from '#/api/emr/template';
import CanvasEmrEditor from '#/emr-canvas/components/CanvasEmrEditor.vue';
import {
  defaultCanvasTemplateData,
  isTiptapDoc,
  parseTemplateContent,
  toCanvasEditorData,
  wrapCanvasPayload,
} from '#/emr-canvas/utils/emr-content';
import { unwrapApiList } from '#/utils/api/unwrap-api-list';
import {
  preserveApiIdForRequest,
  resolveLoginPractitionerIdForRequest,
} from '#/utils/format/preserve-api-id';

import {
  EMR_TEMPLATE_ENCOUNTER_CLASS,
  EMR_TEMPLATE_ENCOUNTER_CLASS_OPTIONS,
  EMR_TEMPLATE_SCOPE_LEVEL,
  EMR_TEMPLATE_SCOPE_LEVEL_OPTIONS,
} from '#/views/basic/emr-template/emr-template-scope.constants';

defineOptions({
  name: 'EmrCanvasTemplateEditDialog',
});

const userStore = useUserStore();

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

/** create / edit / view；列表「复制」在内容加载完成后会切回 create */
const mode = ref<'create' | 'edit' | 'view'>('create');
/** 当前正在编辑的模板 ID；纯新建或复制预填完成后为 null */
const templateId = ref<null | string>(null);
/** 从列表进「编辑」时锁定编码不可改；复制得到的新模板不锁 */
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

/** 挂到画布上的纸面；null 表示尚未就绪，避免空实例闪一下 */
const editorData = ref<IEditorData | null>(null);
const editorRef = ref<InstanceType<typeof CanvasEmrEditor> | null>(null);
/** 旧 Tiptap 稿打开时提示一次 */
const legacyTiptapWarned = ref(false);

/** 文书类型下拉选项，由列表页传入并可在字典加载后更新 */
const documentTypeOptions = ref<FormItemOption[]>([]);

const documentTypeName = computed(() => {
  const code = form.documentTypeCode.trim();
  if (!code) return '';
  const hit = documentTypeOptions.value.find((opt) => String(opt.value) === code);
  return String(hit?.label ?? '').trim();
});

const pendingCopyAfterLoad = ref(false);
const loadLoading = ref(false);
const saveLoadingRef = ref(false);

const readOnly = () => mode.value === 'view';
const saveLoading = () => saveLoadingRef.value;
/** 仅在有源模板且非新建态时展示（查看/编辑下可复制为新模板） */
const showCopyInDialog = () => templateId.value !== null && mode.value !== 'create';

/** 新建默认纸面：页眉医院名称 + 文书标题 */
function cloneDefaultDoc(): IEditorData {
  return defaultCanvasTemplateData(documentTypeName.value || form.name || '病历');
}

function closeDialog() {
  visible.value = false;
  editorData.value = null;
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
 */
function suggestCopyCode(code: string) {
  const base = (code.trim() || 'tpl').replace(/_c\d{6,}$/, '');
  const suffix = `_c${String(Date.now()).slice(-8)}`;
  const max = 64;
  const combined = base + suffix;
  if (combined.length <= max) return combined;
  return `${base.slice(0, Math.max(0, max - suffix.length))}${suffix}`;
}

/** getContent 成功后：清空 ID、切新建态、名称编码按复制规则生成 */
function applyCopyPrefillFromDto() {
  templateId.value = null;
  mode.value = 'create';
  codeLocked.value = false;
  form.name = suggestCopyName(form.name);
  form.code = suggestCopyCode(form.code);
  dialogTitle.value = '新建模板（复制）';
}

/** 将接口 content 转成画布 data；旧 Tiptap 给空白模板纸并提示 */
function contentToEditorData(raw: unknown): IEditorData {
  const parsed = parseTemplateContent(raw);
  if (isTiptapDoc(parsed) && !legacyTiptapWarned.value) {
    legacyTiptapWarned.value = true;
    ElMessage.warning('该模板是旧版文书，已打开为空白 Canvas 纸面，保存后会覆盖为新格式');
  }
  return toCanvasEditorData(raw).data;
}

/** 将详情 DTO 写入表单并挂载画布 */
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
  editorData.value = contentToEditorData(dto.content);
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
  editorData.value = cloneDefaultDoc();
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

/**
 * 弹窗内「复制为新模板」：不调后端 copy 接口，基于当前表单与文档深拷贝后切到新建。
 */
function handleCopyAsNew() {
  if (templateId.value === null) return;
  form.name = suggestCopyName(form.name);
  form.code = suggestCopyCode(form.code);
  templateId.value = null;
  mode.value = 'create';
  codeLocked.value = false;
  const current = editorRef.value?.getData?.() ?? editorData.value;
  editorData.value = current ? structuredClone(current) : cloneDefaultDoc();
  dialogTitle.value = '新建模板（复制）';
  ElMessage.success('已带入当前内容，修改名称/编码后保存即可新建');
}

/**
 * 打开弹窗。
 * - create：空白表单 + Canvas 页眉模板纸
 * - edit/view：带 row.id 拉 getContent
 * - copy：用 row.id 只拉内容，加载完成后切换为新建
 */
async function open(options: {
  defaultDocumentTypeCode?: string;
  documentTypeOptions?: FormItemOption[];
  mode: 'copy' | 'create' | 'edit' | 'view';
  row?: TemplateDto;
}) {
  if (options.documentTypeOptions) {
    setDocumentTypeOptions(options.documentTypeOptions);
  }
  await loadOrganizationOptions();
  pendingCopyAfterLoad.value = false;
  legacyTiptapWarned.value = false;

  if (options.mode === 'create') {
    mode.value = 'create';
    visible.value = true;
    codeLocked.value = false;
    dialogTitle.value = '新建病历模板';
    resetCreate();
    const defaultType = options.defaultDocumentTypeCode?.trim();
    if (defaultType) {
      form.documentTypeCode = defaultType;
      editorData.value = cloneDefaultDoc();
    }
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
    editorData.value = null;
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
  editorData.value = null;
  await loadContentById(strId);
}

/** 提交到模板服务（只读不保存）；正文打成 Canvas 信封 */
async function handleSave() {
  if (readOnly()) return;
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
  const data = editorRef.value?.getData?.() ?? editorData.value ?? cloneDefaultDoc();
  const contentJson = serializeEmrTemplateContent(wrapCanvasPayload(data));
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
    class="emr-canvas-template-editor-dialog"
    :show-close="true"
  >
    <template #header>
      <div class="emr-canvas-template-editor-dialog__header">
        <span class="emr-canvas-template-editor-dialog__title">{{ dialogTitle }}</span>
        <div class="emr-canvas-template-editor-dialog__header-actions">
          <ElButton v-if="showCopyInDialog()" size="small" @click="handleCopyAsNew">
            复制为新模板
          </ElButton>
          <ElButton size="small" @click="closeDialog">取消</ElButton>
          <ElButton
            v-if="!readOnly()"
            size="small"
            type="primary"
            :loading="saveLoading()"
            @click="handleSave"
          >
            保存
          </ElButton>
        </div>
      </div>
    </template>
    <div v-loading="loadLoading" class="emr-canvas-template-editor-dialog__body">
      <aside class="emr-canvas-template-editor-dialog__aside">
        <div class="emr-canvas-template-editor-dialog__aside-title">模板信息</div>
        <ElForm :model="form" label-position="top" class="emr-canvas-template-editor-dialog__form">
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

      <div class="emr-canvas-template-editor-dialog__editor">
        <div v-if="loadLoading" class="emr-canvas-template-editor-dialog__loading">
          加载模板正文…
        </div>
        <CanvasEmrEditor
          v-else-if="editorData"
          :key="(templateId ?? form.code) || 'new'"
          ref="editorRef"
          :model-value="editorData"
          :auto-apply-metadata="false"
          :show-metadata-sidebar="true"
          :show-apply-patient="false"
          :read-only="readOnly()"
        />
      </div>
    </div>
  </ElDialog>
</template>

<style scoped>
.emr-canvas-template-editor-dialog__body {
  display: flex;
  min-height: 0;
  flex: 1;
  gap: 12px;
  overflow: hidden;
}

.emr-canvas-template-editor-dialog__aside {
  display: flex;
  width: 232px;
  flex-shrink: 0;
  flex-direction: column;
  overflow: auto;
  padding: 8px 10px 8px 0;
  border-right: 1px solid var(--el-border-color-lighter);
}

.emr-canvas-template-editor-dialog__aside-title {
  flex-shrink: 0;
  margin-bottom: 12px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.emr-canvas-template-editor-dialog__form :deep(.el-form-item__label) {
  padding-bottom: 4px;
  line-height: 1.3;
}

.emr-canvas-template-editor-dialog__editor {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  overflow: hidden;
}

.emr-canvas-template-editor-dialog__loading {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

.emr-canvas-template-editor-dialog__editor :deep(.canvas-emr-shell) {
  height: 100%;
  min-height: 0;
  flex: 1;
}
</style>

<style>
.emr-canvas-template-editor-dialog.el-dialog.is-fullscreen {
  display: flex;
  flex-direction: column;
  margin: 0;
}

.emr-canvas-template-editor-dialog.is-fullscreen .el-dialog__header {
  flex-shrink: 0;
  margin-right: 0;
  padding: 6px 16px 6px 12px;
}

.emr-canvas-template-editor-dialog.is-fullscreen .el-dialog__title {
  font-size: 15px;
  line-height: 1.25;
}

.emr-canvas-template-editor-dialog.is-fullscreen .el-dialog__headerbtn {
  top: 8px;
  width: 32px;
  height: 32px;
}

.emr-canvas-template-editor-dialog.is-fullscreen .el-dialog__body {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  overflow: hidden;
  padding: 0 10px 8px;
}

.emr-canvas-template-editor-dialog.is-fullscreen .el-dialog__footer {
  display: none;
}

.emr-canvas-template-editor-dialog__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-right: 36px;
}

.emr-canvas-template-editor-dialog__title {
  min-width: 0;
  overflow: hidden;
  font-size: 15px;
  font-weight: 600;
  line-height: 24px;
  color: var(--el-text-color-primary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.emr-canvas-template-editor-dialog__header-actions {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 8px;
}
</style>
