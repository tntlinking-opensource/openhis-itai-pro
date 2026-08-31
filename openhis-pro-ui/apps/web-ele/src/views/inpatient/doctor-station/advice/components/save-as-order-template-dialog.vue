<script setup lang="ts">
import type { AdviceRowItem } from '../services/advice.types';
import type { SaveOrderTemplateCandidateRow } from '../services/save-order-template-from-advice.service';

import { computed, ref, watch } from 'vue';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElRadio,
  ElRadioGroup,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import {
  ORDER_TEMPLATE_BINDING_DEPARTMENT,
  ORDER_TEMPLATE_BINDING_PERSONAL,
  saveOrderTemplateFromAdviceApi,
} from '#/api/basic/order-template';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import {
  buildSaveOrderTemplateCandidates,
  toAdviceSourceLineKey,
} from '../services/save-order-template-from-advice.service';

defineOptions({ name: 'InpatientSaveAsOrderTemplateDialog' });

const props = withDefaults(defineProps<Props>(), {
  encounterId: '',
  patientId: '',
  adviceRows: () => [],
});

const emit = defineEmits<{
  saved: [];
}>();

interface Props {
  encounterId?: string;
  patientId?: string;
  adviceRows?: AdviceRowItem[];
}

const open = defineModel<boolean>('open', { default: false });

const templateName = ref('');
const bindingEnum = ref(ORDER_TEMPLATE_BINDING_DEPARTMENT);
/** 选中区行键（有序） */
const selectedKeys = ref<string[]>([]);
const saveLoading = ref(false);

/** 弹窗候选行：展平成组并标记是否可提交 */
const candidateRows = computed(() => buildSaveOrderTemplateCandidates(props.adviceRows));

/** 可另存候选行（须含 requestId + productTableName） */
const selectableRows = computed(() => candidateRows.value.filter((row) => row.selectable));

/** 选中区 key 集合 */
const selectedKeySet = computed(() => new Set(selectedKeys.value));

/** 左侧：尚未选中的全部项目 */
const availableRows = computed(() =>
  selectableRows.value.filter((row) => !selectedKeySet.value.has(row.key)),
);

/** 右侧：选中区行（保持点击加入顺序） */
const selectedRows = computed(() => {
  const byKey = new Map(selectableRows.value.map((row) => [row.key, row]));
  const rows: SaveOrderTemplateCandidateRow[] = [];
  for (const key of selectedKeys.value) {
    const row = byKey.get(key);
    if (row) {
      rows.push(row);
    }
  }
  return rows;
});

/** 打开弹窗时重置表单与选中区 */
function resetFormOnOpen() {
  templateName.value = '';
  bindingEnum.value = ORDER_TEMPLATE_BINDING_DEPARTMENT;
  selectedKeys.value = [];
}

/** 左侧行点击：加入选中区 */
function handleAddFromSource(row: SaveOrderTemplateCandidateRow) {
  if (!row.selectable || selectedKeySet.value.has(row.key)) {
    return;
  }
  selectedKeys.value = [...selectedKeys.value, row.key];
}

/** 右侧行点击：移出选中区 */
function handleRemoveFromSelected(row: SaveOrderTemplateCandidateRow) {
  selectedKeys.value = selectedKeys.value.filter((key) => key !== row.key);
}

/** 清空选中区 */
function handleClearSelected() {
  selectedKeys.value = [];
}

/** 全部加入选中区 */
function handleAddAll() {
  selectedKeys.value = selectableRows.value.map((row) => row.key);
}

/** 提交另存组套 */
async function handleConfirmSave() {
  const encounter = preserveApiIdForRequest(props.encounterId ?? '');
  const patient = preserveApiIdForRequest(props.patientId ?? '');
  if (!encounter || !patient) {
    ElMessage.warning('请先选择患者与就诊');
    return;
  }
  const name = templateName.value.trim();
  if (!name) {
    ElMessage.warning('请输入组套名称');
    return;
  }
  const selected = selectedRows.value;
  if (selected.length === 0) {
    ElMessage.warning('请至少选择一条可另存的医嘱');
    return;
  }
  saveLoading.value = true;
  try {
    await saveOrderTemplateFromAdviceApi({
      encounterId: encounter,
      patientId: patient,
      templateName: name,
      bindingEnum: bindingEnum.value,
      sourceLines: selected.map((row) => toAdviceSourceLineKey(row)),
    });
    ElMessage.success(`已另存组套「${name}」（${selected.length} 项）`);
    open.value = false;
    emit('saved');
  } catch {
    // 错误由请求拦截器提示
  } finally {
    saveLoading.value = false;
  }
}

watch(open, (visible) => {
  if (visible) {
    if (selectableRows.value.length === 0) {
      ElMessage.warning('当前没有可另存的医嘱（需已保存且含有效标识）');
      open.value = false;
      return;
    }
    resetFormOnOpen();
  }
});
</script>

<template>
  <ElDialog
    v-model="open"
    title="另存组套"
    width="1200px"
    top="6vh"
    append-to-body
    destroy-on-close
    class="save-as-order-template-dialog"
    :close-on-click-modal="false"
  >
    <ElForm label-width="88px" class="save-as-order-template-form">
      <div class="save-as-order-template-meta">
        <ElFormItem label="组套名称" required class="save-as-order-template-meta__name">
          <ElInput
            v-model="templateName"
            maxlength="64"
            show-word-limit
            placeholder="请输入组套名称"
            clearable
          />
        </ElFormItem>
        <ElFormItem label="归属范围" class="save-as-order-template-meta__binding">
          <ElRadioGroup v-model="bindingEnum">
            <ElRadio :value="ORDER_TEMPLATE_BINDING_DEPARTMENT">科室组套</ElRadio>
            <ElRadio :value="ORDER_TEMPLATE_BINDING_PERSONAL">个人组套</ElRadio>
          </ElRadioGroup>
        </ElFormItem>
      </div>
      <ElFormItem label="包含医嘱" class="save-as-order-template-form__lines">
        <div class="save-as-order-template-panels w-full">
          <div class="save-as-order-template-panel">
            <div class="save-as-order-template-panel__head">
              <div>
                <div class="save-as-order-template-panel__title">
                  所有项目（{{ availableRows.length }}）
                </div>
                <div class="save-as-order-template-panel__hint">点击行加入选中区</div>
              </div>
              <ElButton
                link
                type="primary"
                :disabled="availableRows.length === 0"
                @click="handleAddAll"
              >
                全部添加
              </ElButton>
            </div>
            <ElTable
              :data="availableRows"
              border
              stripe
              max-height="420"
              row-key="key"
              class="save-as-order-template-table"
              empty-text="暂无待选医嘱"
              @row-click="handleAddFromSource"
            >
              <ElTableColumn prop="type" label="类型" width="80" align="center" />
              <ElTableColumn
                prop="content"
                label="医嘱内容"
                min-width="280"
                show-overflow-tooltip
              />
            </ElTable>
          </div>
          <div class="save-as-order-template-panel">
            <div class="save-as-order-template-panel__head">
              <div>
                <div class="save-as-order-template-panel__title">
                  选中区（{{ selectedRows.length }}）
                </div>
                <div class="save-as-order-template-panel__hint">点击行移出选中区</div>
              </div>
              <ElButton
                link
                type="danger"
                :disabled="selectedRows.length === 0"
                @click="handleClearSelected"
              >
                清空
              </ElButton>
            </div>
            <ElTable
              :data="selectedRows"
              border
              stripe
              max-height="420"
              row-key="key"
              class="save-as-order-template-table"
              empty-text="请从左侧点击添加"
              @row-click="handleRemoveFromSelected"
            >
              <ElTableColumn prop="type" label="类型" width="80" align="center" />
              <ElTableColumn
                prop="content"
                label="医嘱内容"
                min-width="280"
                show-overflow-tooltip
              />
            </ElTable>
          </div>
        </div>
      </ElFormItem>
    </ElForm>

    <template #footer>
      <ElButton @click="open = false">取消</ElButton>
      <ElButton type="primary" :loading="saveLoading" @click="handleConfirmSave">保存</ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.save-as-order-template-form :deep(.el-form-item) {
  margin-bottom: 14px;
}

.save-as-order-template-form__lines {
  margin-bottom: 0;
}

.save-as-order-template-meta {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(280px, 0.8fr);
  gap: 8px 24px;
  align-items: start;
}

.save-as-order-template-meta__name,
.save-as-order-template-meta__binding {
  margin-bottom: 12px;
}

.save-as-order-template-panels {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.save-as-order-template-panel {
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-blank);
}

.save-as-order-template-panel__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
}

.save-as-order-template-panel__title {
  font-size: 14px;
  font-weight: 600;
  line-height: 22px;
  color: var(--el-text-color-primary);
}

.save-as-order-template-panel__hint {
  margin-top: 2px;
  font-size: 12px;
  line-height: 18px;
  color: var(--el-text-color-regular);
}

.save-as-order-template-table :deep(.el-table__row) {
  cursor: pointer;
}

.save-as-order-template-table :deep(.el-table__body tr:hover > td.el-table__cell) {
  background: var(--el-color-primary-light-9) !important;
}

.save-as-order-template-table :deep(.el-table__header-wrapper th.el-table__cell) {
  background: #eaf4ff !important;
}
</style>
