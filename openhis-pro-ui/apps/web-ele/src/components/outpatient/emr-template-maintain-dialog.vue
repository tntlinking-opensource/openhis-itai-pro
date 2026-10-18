<script setup lang="ts">
import type { JSONContent } from '@tiptap/core';

import type { TemplateContentDto, TemplateDto } from '#/api/emr/template';
import type { EmrTemplateStationKind } from '#/views/basic/emr-template/emr-template-scope.constants';

import { computed, nextTick, ref, watch } from 'vue';

import { useUserStore } from '@zxplat/stores';

import { Document, Folder, FolderOpened } from '@element-plus/icons-vue';
import {
  ElButton,
  ElDialog,
  ElDropdown,
  ElDropdownItem,
  ElDropdownMenu,
  ElIcon,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElTag,
  ElTree,
} from 'element-plus';

import {
  createTemplateApi,
  deleteTemplateApi,
  getTemplateContentApi,
  listTemplatesForStationApi,
  serializeEmrTemplateContent,
  updateTemplateApi,
} from '#/api/emr/template';
import { SimpleEditor, normalizeEmrRootDoc } from '#/emrEditor';
import {
  DEFAULT_EDITOR_PAGE_MARGINS,
  defaultEmrEditorDoc,
} from '#/emrEditor/components/simple-editor/editor-defaults';
import { useAuthStore } from '#/store';
import { unwrapApiList } from '#/utils/api/unwrap-api-list';
import {
  preserveApiIdForRequest,
  resolveLoginPractitionerIdForRequest,
} from '#/utils/format/preserve-api-id';
import {
  EMR_TEMPLATE_ENCOUNTER_CLASS,
  EMR_TEMPLATE_SCOPE_LEVEL,
  EMR_TEMPLATE_STATION_KIND,
  formatEncounterClassLabel,
  formatTemplateScopeTag,
} from '#/views/basic/emr-template/emr-template-scope.constants';

defineOptions({ name: 'EmrTemplateMaintainDialog' });

interface Props {
  modelValue: boolean;
  /** 门诊 OUTPATIENT / 住院 INPATIENT */
  stationKind?: EmrTemplateStationKind;
}

const props = withDefaults(defineProps<Props>(), {
  stationKind: EMR_TEMPLATE_STATION_KIND.OUTPATIENT,
});

const emit = defineEmits<{
  changed: [];
  'update:modelValue': [value: boolean];
}>();

interface MaintainTreeNode {
  id: string;
  label: string;
  isGroup?: boolean;
  scope?: 'department' | 'personal';
  template?: TemplateDto;
  children?: MaintainTreeNode[];
}

const userStore = useUserStore();
const authStore = useAuthStore();

const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v),
});

const treeRef = ref<InstanceType<typeof ElTree>>();
const filterText = ref('');
const treeLoading = ref(false);
const contentLoading = ref(false);
const saveLoading = ref(false);
const allRows = ref<TemplateDto[]>([]);
/** 关闭时是否需要通知父级刷新侧栏模板树 */
const dirtyList = ref(false);
/** 当前编辑区相对已加载快照是否有未保存改动 */
const editorDirty = ref(false);
/** 加载/切换时抑制 dirty 标记 */
const suppressDirty = ref(false);

const selectedId = ref('');
const selectedTpl = ref<null | TemplateDto>(null);
/** 新建草稿（尚未入库） */
const isCreating = ref(false);
const createScope = ref<'department' | 'personal'>('personal');
const editName = ref('');
const docJson = ref<JSONContent | null>(null);
const editorKey = ref(0);
const pageMargins = ref({ ...DEFAULT_EDITOR_PAGE_MARGINS });

/** 右侧是否处于可编辑会话（已有模板或新建草稿） */
const hasActiveEditor = computed(() => isCreating.value || Boolean(selectedId.value));

const personalList = computed(() => {
  const me = resolveLoginPractitionerIdForRequest(
    userStore.userInfo as Record<string, unknown> | undefined,
  );
  // 仅本人个人模板
  return allRows.value.filter((row) => {
    if (
      String(row.scopeLevel ?? '')
        .trim()
        .toUpperCase() !== EMR_TEMPLATE_SCOPE_LEVEL.PERSONAL
    ) {
      return false;
    }
    const owner = preserveApiIdForRequest(row.ownerPractitionerId) ?? '';
    return Boolean(me && owner && owner === me);
  });
});

const departmentList = computed(() => {
  const loginOrg = preserveApiIdForRequest(authStore.getSelectedOrganization()) ?? '';
  // 仅当前登录科室的科室模板
  return allRows.value.filter((row) => {
    if (
      String(row.scopeLevel ?? '')
        .trim()
        .toUpperCase() !== EMR_TEMPLATE_SCOPE_LEVEL.DEPARTMENT
    ) {
      return false;
    }
    const org = preserveApiIdForRequest(row.organizationId) ?? '';
    return Boolean(loginOrg && org && org === loginOrg);
  });
});

const treeData = computed<MaintainTreeNode[]>(() => {
  const toLeaf = (row: TemplateDto, scope: 'department' | 'personal'): MaintainTreeNode => {
    const id = preserveApiIdForRequest(row.id) ?? '';
    const name = (row.name ?? '').trim() || (row.code ?? '').trim() || '未命名模板';
    return {
      id: id || `tmp-${scope}-${name}`,
      label: name,
      scope,
      template: row,
    };
  };
  return [
    {
      id: 'group-personal',
      label: `个人模板（${personalList.value.length}）`,
      isGroup: true,
      scope: 'personal',
      children: personalList.value.map((r) => toLeaf(r, 'personal')),
    },
    {
      id: 'group-department',
      label: `科室模板（${departmentList.value.length}）`,
      isGroup: true,
      scope: 'department',
      children: departmentList.value.map((r) => toLeaf(r, 'department')),
    },
  ];
});

const selectedMeta = computed(() => {
  if (isCreating.value) {
    const scopeLabel = createScope.value === 'personal' ? '个人' : '科室';
    const enc =
      props.stationKind === EMR_TEMPLATE_STATION_KIND.INPATIENT
        ? formatEncounterClassLabel(EMR_TEMPLATE_ENCOUNTER_CLASS.IMP)
        : formatEncounterClassLabel(EMR_TEMPLATE_ENCOUNTER_CLASS.AMB);
    return [scopeLabel, '新建', enc].filter(Boolean).join(' · ');
  }
  const tpl = selectedTpl.value;
  if (!tpl) return '';
  const parts = [
    formatTemplateScopeTag(tpl.scopeLevel) || '',
    (tpl.documentTypeName || tpl.documentTypeCode || '').trim(),
    formatEncounterClassLabel(tpl.encounterClassCode) || '',
  ].filter(Boolean);
  return parts.join(' · ');
});

/** 医生站新建模板默认文书类型 */
function resolveDefaultDocumentTypeCode(): string {
  if (props.stationKind === EMR_TEMPLATE_STATION_KIND.INPATIENT) {
    return '';
  }
  return 'OUTPATIENT_RECORD';
}

/** 医生站场景对应就诊类别 */
function resolveEncounterClassForStation(): string {
  if (props.stationKind === EMR_TEMPLATE_STATION_KIND.INPATIENT) {
    return EMR_TEMPLATE_ENCOUNTER_CLASS.IMP;
  }
  return EMR_TEMPLATE_ENCOUNTER_CLASS.AMB;
}

/** 生成医生站模板编码（库内唯一） */
function suggestDoctorStationTemplateCode(name: string): string {
  const base = (name.trim() || 'tpl')
    .replaceAll(/\s+/g, '_')
    .replaceAll(/[^\w-]/g, '')
    .slice(0, 32);
  return `${base}_ds_${String(Date.now()).slice(-8)}`.slice(0, 64);
}

/** 拉取医生站可见模板，供个人/科室树并列展示 */
async function loadTemplates() {
  treeLoading.value = true;
  try {
    const list = await listTemplatesForStationApi({
      stationKind: props.stationKind,
      enabled: true,
    });
    allRows.value = unwrapApiList<TemplateDto>(list);
  } catch {
    allRows.value = [];
  } finally {
    treeLoading.value = false;
  }
}

/** 清空右侧编辑区 */
function clearEditor() {
  suppressDirty.value = true;
  isCreating.value = false;
  createScope.value = 'personal';
  selectedId.value = '';
  selectedTpl.value = null;
  editName.value = '';
  docJson.value = null;
  editorDirty.value = false;
  void nextTick(() => {
    suppressDirty.value = false;
  });
}

/** 未保存时询问是否丢弃 */
async function confirmDiscardIfDirty(): Promise<boolean> {
  if (!editorDirty.value) return true;
  try {
    await ElMessageBox.confirm('当前模板有未保存的修改，是否丢弃？', '提示', {
      type: 'warning',
      confirmButtonText: '丢弃',
      cancelButtonText: '继续编辑',
    });
    return true;
  } catch {
    return false;
  }
}

/** 将详情写入右侧编辑器 */
function applyContentDto(dto: TemplateContentDto, tpl: TemplateDto) {
  suppressDirty.value = true;
  selectedTpl.value = { ...tpl, ...dto };
  editName.value = (dto.name ?? tpl.name ?? '').trim();
  docJson.value = normalizeEmrRootDoc(dto.content);
  editorKey.value += 1;
  editorDirty.value = false;
  void nextTick(() => {
    suppressDirty.value = false;
  });
}

/** 选中左侧叶子模板并加载正文 */
async function selectTemplate(tpl: TemplateDto) {
  const id = preserveApiIdForRequest(tpl.id) ?? '';
  if (!id) {
    ElMessage.warning('该模板缺少有效 ID');
    return;
  }
  if (id === selectedId.value && !isCreating.value) return;
  if (!(await confirmDiscardIfDirty())) return;

  isCreating.value = false;
  selectedId.value = id;
  contentLoading.value = true;
  try {
    const res = await getTemplateContentApi({ templateId: id });
    if (!res) {
      ElMessage.error('模板不存在或已被删除');
      clearEditor();
      return;
    }
    applyContentDto(res, tpl);
    await nextTick();
    treeRef.value?.setCurrentKey?.(id);
  } catch {
    clearEditor();
  } finally {
    contentLoading.value = false;
  }
}

/** 进入新建草稿：空白正文，保存时入库 */
async function startCreate(scope: 'department' | 'personal') {
  if (!(await confirmDiscardIfDirty())) return;

  if (scope === 'personal') {
    const me = resolveLoginPractitionerIdForRequest(
      userStore.userInfo as Record<string, unknown> | undefined,
    );
    if (!me) {
      ElMessage.warning('当前账号未关联医务人员，无法新建个人模板');
      return;
    }
  } else {
    const orgId = preserveApiIdForRequest(authStore.getSelectedOrganization()) ?? '';
    if (!orgId) {
      ElMessage.warning('请先选择登录科室后再新建科室模板');
      return;
    }
  }

  let name = '';
  try {
    const { value } = await ElMessageBox.prompt('请输入模板名称', '新建病历模板', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputValue: scope === 'personal' ? '新建个人模板' : '新建科室模板',
      inputPattern: /\S+/,
      inputErrorMessage: '模板名称不能为空',
    });
    name = String(value ?? '').trim();
  } catch {
    return;
  }
  if (!name) return;

  suppressDirty.value = true;
  isCreating.value = true;
  createScope.value = scope;
  selectedId.value = '';
  selectedTpl.value = null;
  editName.value = name;
  docJson.value = structuredClone(defaultEmrEditorDoc);
  editorKey.value += 1;
  editorDirty.value = true;
  await nextTick();
  treeRef.value?.setCurrentKey?.(null as unknown as string);
  suppressDirty.value = false;
}

/** 下拉：新建个人 / 科室模板 */
function handleCreateCommand(command: string | number | object) {
  const cmd = String(command);
  if (cmd === 'personal') {
    void startCreate('personal');
    return;
  }
  if (cmd === 'department') {
    void startCreate('department');
  }
}

/** 树节点点击：分组忽略，叶子加载 */
async function handleNodeClick(data: MaintainTreeNode) {
  if (data.isGroup || !data.template) return;
  await selectTemplate(data.template);
}

/** 树过滤：按模板名称/编码 */
function filterTreeNode(value: string, data: MaintainTreeNode) {
  if (!value) return true;
  if (data.isGroup) return true;
  const q = value.trim().toLowerCase();
  if (!q) return true;
  const name = (data.label ?? '').toLowerCase();
  const code = String(data.template?.code ?? '').toLowerCase();
  const typeName = String(data.template?.documentTypeName ?? '').toLowerCase();
  return name.includes(q) || code.includes(q) || typeName.includes(q);
}

/** 保存：新建走 create，已有走 update */
async function handleSave() {
  const name = editName.value.trim();
  if (!name) {
    ElMessage.warning('模板名称不能为空');
    return;
  }
  if (!docJson.value) {
    ElMessage.warning('模板正文为空');
    return;
  }

  if (isCreating.value) {
    if (createScope.value === 'personal') {
      const practitionerId = resolveLoginPractitionerIdForRequest(
        userStore.userInfo as Record<string, unknown> | undefined,
      );
      if (!practitionerId) {
        ElMessage.warning('当前账号未关联医务人员，无法保存个人模板');
        return;
      }
    } else {
      const orgId = preserveApiIdForRequest(authStore.getSelectedOrganization()) ?? '';
      if (!orgId) {
        ElMessage.warning('请先选择登录科室后再保存科室模板');
        return;
      }
    }

    saveLoading.value = true;
    try {
      const created = await createTemplateApi({
        name,
        code: suggestDoctorStationTemplateCode(name),
        documentTypeCode: resolveDefaultDocumentTypeCode() || undefined,
        scopeLevel:
          createScope.value === 'personal'
            ? EMR_TEMPLATE_SCOPE_LEVEL.PERSONAL
            : EMR_TEMPLATE_SCOPE_LEVEL.DEPARTMENT,
        encounterClassCode: resolveEncounterClassForStation(),
        organizationId:
          createScope.value === 'department'
            ? (preserveApiIdForRequest(authStore.getSelectedOrganization()) ?? undefined)
            : undefined,
        ownerPractitionerId:
          createScope.value === 'personal'
            ? resolveLoginPractitionerIdForRequest(
                userStore.userInfo as Record<string, unknown> | undefined,
              ) || undefined
            : undefined,
        fromDoctorStation: true,
        content: serializeEmrTemplateContent(docJson.value),
        enabled: true,
      });
      ElMessage.success(createScope.value === 'personal' ? '已新建个人模板' : '已新建科室模板');
      editorDirty.value = false;
      dirtyList.value = true;
      isCreating.value = false;
      await loadTemplates();
      const newId = preserveApiIdForRequest(created?.id) ?? '';
      if (newId) {
        const hit = allRows.value.find((r) => (preserveApiIdForRequest(r.id) ?? '') === newId);
        if (hit) {
          await selectTemplate(hit);
          return;
        }
      }
      clearEditor();
    } catch {
      // 拦截器已提示
    } finally {
      saveLoading.value = false;
    }
    return;
  }

  const tpl = selectedTpl.value;
  const id = preserveApiIdForRequest(tpl?.id) ?? selectedId.value;
  if (!id) {
    ElMessage.warning('请先选择模板');
    return;
  }
  saveLoading.value = true;
  try {
    await updateTemplateApi({
      id,
      name,
      content: docJson.value,
    });
    ElMessage.success('已保存');
    editorDirty.value = false;
    dirtyList.value = true;
    // 同步树节点展示名
    if (selectedTpl.value) {
      selectedTpl.value = { ...selectedTpl.value, name };
    }
    await loadTemplates();
  } catch {
    // 拦截器已提示
  } finally {
    saveLoading.value = false;
  }
}

/** 删除当前选中模板（新建草稿仅清空） */
async function handleDelete() {
  if (isCreating.value) {
    if (!(await confirmDiscardIfDirty())) return;
    clearEditor();
    return;
  }
  const tpl = selectedTpl.value;
  const id = preserveApiIdForRequest(tpl?.id) ?? selectedId.value;
  if (!id) {
    ElMessage.warning('请先选择模板');
    return;
  }
  const title = editName.value.trim() || (tpl?.name ?? '').trim() || '模板';
  try {
    await ElMessageBox.confirm(`确定删除模板「${title}」吗？删除后不可恢复。`, '提示', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }
  try {
    await deleteTemplateApi({ id });
    ElMessage.success('已删除');
    dirtyList.value = true;
    clearEditor();
    await loadTemplates();
  } catch {
    // 拦截器已提示
  }
}

/** 关闭前若有未保存改动则确认 */
async function handleBeforeClose(done: () => void) {
  if (!(await confirmDiscardIfDirty())) return;
  done();
}

/** 手动刷新左侧树 */
async function handleRefreshTree() {
  if (!(await confirmDiscardIfDirty())) return;
  const keepId = isCreating.value ? '' : selectedId.value;
  clearEditor();
  await loadTemplates();
  if (!keepId) return;
  const hit = allRows.value.find((r) => (preserveApiIdForRequest(r.id) ?? '') === keepId);
  if (!hit) return;
  editorDirty.value = false;
  await selectTemplate(hit);
}

watch(filterText, (val) => {
  treeRef.value?.filter(val);
});

watch(editName, () => {
  if (!suppressDirty.value && hasActiveEditor.value) {
    editorDirty.value = true;
  }
});

watch(
  docJson,
  () => {
    if (!suppressDirty.value && hasActiveEditor.value) {
      editorDirty.value = true;
    }
  },
  { deep: true },
);

watch(visible, (open) => {
  if (open) {
    dirtyList.value = false;
    filterText.value = '';
    clearEditor();
    void loadTemplates();
    return;
  }
  if (dirtyList.value) {
    emit('changed');
  }
});
</script>

<template>
  <ElDialog
    v-model="visible"
    title="病历模板维护"
    fullscreen
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
    :before-close="handleBeforeClose"
    class="emr-tpl-maintain-ws"
  >
    <div class="emr-tpl-maintain-ws__body">
      <!-- 左侧：个人 / 科室并列模板树 -->
      <aside v-loading="treeLoading" class="emr-tpl-maintain-ws__aside">
        <div class="emr-tpl-maintain-ws__aside-toolbar">
          <ElInput v-model="filterText" clearable size="small" placeholder="搜索模板名称/编码" />
          <ElDropdown trigger="click" @command="handleCreateCommand">
            <ElButton type="primary" size="small">新增</ElButton>
            <template #dropdown>
              <ElDropdownMenu>
                <ElDropdownItem command="personal">个人模板</ElDropdownItem>
                <ElDropdownItem command="department">科室模板</ElDropdownItem>
              </ElDropdownMenu>
            </template>
          </ElDropdown>
          <ElButton size="small" :loading="treeLoading" @click="handleRefreshTree">刷新</ElButton>
        </div>
        <p class="emr-tpl-maintain-ws__aside-hint">
          个人与科室模板并列展示；可新增，或点选后在右侧编辑正文并保存。
        </p>
        <ElTree
          ref="treeRef"
          class="emr-tpl-maintain-ws__tree"
          :data="treeData"
          node-key="id"
          highlight-current
          default-expand-all
          :expand-on-click-node="false"
          :current-node-key="selectedId || undefined"
          :filter-node-method="filterTreeNode"
          @node-click="handleNodeClick"
        >
          <template #default="{ data }">
            <div class="emr-tpl-maintain-ws__tree-node">
              <ElIcon class="emr-tpl-maintain-ws__tree-icon">
                <FolderOpened v-if="data.isGroup && data.scope === 'personal'" />
                <Folder v-else-if="data.isGroup" />
                <Document v-else />
              </ElIcon>
              <span class="emr-tpl-maintain-ws__tree-label" :title="data.label">
                {{ data.label }}
              </span>
              <ElTag
                v-if="!data.isGroup && data.scope === 'personal'"
                size="small"
                type="success"
                effect="plain"
                class="emr-tpl-maintain-ws__tree-tag"
              >
                个人
              </ElTag>
              <ElTag
                v-else-if="!data.isGroup"
                size="small"
                type="warning"
                effect="plain"
                class="emr-tpl-maintain-ws__tree-tag"
              >
                科室
              </ElTag>
            </div>
          </template>
        </ElTree>
      </aside>

      <!-- 右侧：名称 + 编辑器 -->
      <section class="emr-tpl-maintain-ws__main">
        <div class="emr-tpl-maintain-ws__main-toolbar">
          <template v-if="hasActiveEditor">
            <ElInput
              v-model="editName"
              class="emr-tpl-maintain-ws__name"
              placeholder="模板名称"
              maxlength="128"
              show-word-limit
            />
            <span v-if="selectedMeta" class="emr-tpl-maintain-ws__meta">{{ selectedMeta }}</span>
            <ElTag v-if="isCreating" size="small" type="primary" effect="plain">新建</ElTag>
            <ElTag v-if="editorDirty" size="small" type="danger" effect="plain">未保存</ElTag>
            <div class="emr-tpl-maintain-ws__actions">
              <ElButton
                type="primary"
                :loading="saveLoading"
                :disabled="contentLoading"
                @click="handleSave"
              >
                保存
              </ElButton>
              <ElButton type="danger" plain :disabled="contentLoading" @click="handleDelete">
                {{ isCreating ? '取消新建' : '删除' }}
              </ElButton>
            </div>
          </template>
          <span v-else class="emr-tpl-maintain-ws__empty-title">
            请从左侧选择模板，或点击「新增」创建
          </span>
        </div>

        <div v-loading="contentLoading" class="emr-tpl-maintain-ws__editor">
          <SimpleEditor
            v-if="hasActiveEditor && docJson"
            :key="editorKey"
            v-model="docJson"
            v-model:page-margins="pageMargins"
            placeholder="编辑模板正文"
            surface="template"
            :show-metadata-sidebar="false"
          />
          <div v-else class="emr-tpl-maintain-ws__placeholder">
            个人与科室模板并列列出；可新增空白模板，或点选已有模板修改后保存。
          </div>
        </div>
      </section>
    </div>

    <template #footer>
      <div class="emr-tpl-maintain-ws__footer">
        <ElButton @click="visible = false">关闭</ElButton>
        <ElButton
          type="primary"
          :loading="saveLoading"
          :disabled="!hasActiveEditor || contentLoading"
          @click="handleSave"
        >
          保存
        </ElButton>
      </div>
    </template>
  </ElDialog>
</template>

<style scoped>
.emr-tpl-maintain-ws__body {
  display: flex;
  min-height: 0;
  flex: 1;
  gap: 0;
  overflow: hidden;
}

.emr-tpl-maintain-ws__aside {
  display: flex;
  width: 280px;
  flex-shrink: 0;
  flex-direction: column;
  overflow: hidden;
  padding-right: 12px;
  border-right: 1px solid var(--el-border-color-lighter);
}

.emr-tpl-maintain-ws__aside-toolbar {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
  margin-bottom: 8px;
}

.emr-tpl-maintain-ws__aside-toolbar :deep(.el-input) {
  flex: 1;
  min-width: 0;
}

.emr-tpl-maintain-ws__aside-hint {
  flex-shrink: 0;
  margin: 0 0 8px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-text-color-secondary);
}

.emr-tpl-maintain-ws__tree {
  flex: 1;
  overflow: auto;
  min-height: 0;
}

.emr-tpl-maintain-ws__tree-node {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  padding-right: 4px;
}

.emr-tpl-maintain-ws__tree-icon {
  flex-shrink: 0;
  color: var(--el-text-color-secondary);
}

.emr-tpl-maintain-ws__tree-label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--el-text-color-primary);
}

.emr-tpl-maintain-ws__tree-tag {
  flex-shrink: 0;
}

.emr-tpl-maintain-ws__main {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  overflow: hidden;
  padding-left: 12px;
}

.emr-tpl-maintain-ws__main-toolbar {
  display: flex;
  flex-shrink: 0;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  min-height: 32px;
}

.emr-tpl-maintain-ws__name {
  width: 280px;
  max-width: 40%;
}

.emr-tpl-maintain-ws__meta {
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.emr-tpl-maintain-ws__actions {
  display: flex;
  gap: 8px;
  margin-left: auto;
}

.emr-tpl-maintain-ws__empty-title {
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.emr-tpl-maintain-ws__editor {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  overflow: hidden;
}

.emr-tpl-maintain-ws__editor :deep(.simple-editor) {
  height: 100%;
  min-height: 0;
  flex: 1;
}

.emr-tpl-maintain-ws__placeholder {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  padding: 24px;
  font-size: 14px;
  color: var(--el-text-color-secondary);
  text-align: center;
}

.emr-tpl-maintain-ws__footer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}
</style>

<style>
/* 全屏：header / body / footer 纵向占满 */
.emr-tpl-maintain-ws.el-dialog.is-fullscreen {
  display: flex;
  flex-direction: column;
  margin: 0;
}

.emr-tpl-maintain-ws.is-fullscreen .el-dialog__header {
  flex-shrink: 0;
  margin-right: 0;
  padding: 6px 12px 4px;
}

.emr-tpl-maintain-ws.is-fullscreen .el-dialog__title {
  font-size: 15px;
  line-height: 1.25;
}

.emr-tpl-maintain-ws.is-fullscreen .el-dialog__headerbtn {
  top: 6px;
  width: 32px;
  height: 32px;
}

.emr-tpl-maintain-ws.is-fullscreen .el-dialog__body {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  overflow: hidden;
  padding: 4px 12px 6px;
}

.emr-tpl-maintain-ws.is-fullscreen .el-dialog__footer {
  flex-shrink: 0;
  padding: 0 16px 8px;
}
</style>
