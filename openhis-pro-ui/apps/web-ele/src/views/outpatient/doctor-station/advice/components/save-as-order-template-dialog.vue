<script setup lang="ts">
import type { AdviceRowItem } from '../services/advice.types';
import type { SaveOrderTemplateCandidateRow } from '../services/save-order-template-from-advice.service';

import { computed, nextTick, ref, watch } from 'vue';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
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
import { preserveApiIdForRequest } from '#/utils/preserve-api-id';

import {
  buildSaveOrderTemplateCandidates,
  toAdviceSourceLineKey,
} from '../services/save-order-template-from-advice.service';

defineOptions({ name: 'SaveAsOrderTemplateDialog' });

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
const tableSelection = ref<SaveOrderTemplateCandidateRow[]>([]);
const saveLoading = ref(false);
const tableRef = ref<InstanceType<typeof ElTable> | null>(null);

/** 弹窗候选行：展平成组并标记是否可提交 */
const candidateRows = computed(() => buildSaveOrderTemplateCandidates(props.adviceRows));

/** 可勾选的候选行（须含 requestId + productTableName） */
const selectableRows = computed(() => candidateRows.value.filter((row) => row.selectable));

/** 打开弹窗时重置表单并默认全选 */
function resetFormOnOpen() {
  templateName.value = '';
  bindingEnum.value = ORDER_TEMPLATE_BINDING_DEPARTMENT;
  tableSelection.value = [...selectableRows.value];
  void nextTick(() => {
    const table = tableRef.value;
    if (!table) return;
    table.clearSelection();
    for (const row of selectableRows.value) {
      table.toggleRowSelection(row, true);
    }
  });
}

/** 表格勾选变化 */
function handleSelectionChange(rows: SaveOrderTemplateCandidateRow[]) {
  tableSelection.value = rows;
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
  const selected = tableSelection.value.filter((row) => row.selectable);
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
    ElMessage.success(`已另存为组套「${name}」（${selected.length} 项）`);
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
    title="另存为组套"
    width="760px"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
  >
    <ElForm label-width="88px" class="save-as-order-template-form">
      <ElFormItem label="组套名称" required>
        <ElInput
          v-model="templateName"
          maxlength="64"
          show-word-limit
          placeholder="请输入组套名称"
          clearable
        />
      </ElFormItem>
      <ElFormItem label="归属范围">
        <ElRadioGroup v-model="bindingEnum">
          <ElRadio :value="ORDER_TEMPLATE_BINDING_DEPARTMENT">科室组套</ElRadio>
          <ElRadio :value="ORDER_TEMPLATE_BINDING_PERSONAL">个人组套</ElRadio>
        </ElRadioGroup>
      </ElFormItem>
      <ElFormItem label="包含医嘱">
        <div class="w-full">
          <div class="mb-2 text-xs text-[var(--el-text-color-regular)]">
            已选 {{ tableSelection.length }} /
            {{ selectableRows.length }} 项（默认全选当前列表医嘱）
          </div>
          <ElTable
            ref="tableRef"
            :data="candidateRows"
            border
            stripe
            max-height="360"
            row-key="key"
            @selection-change="handleSelectionChange"
          >
            <ElTableColumn
              type="selection"
              width="42"
              align="center"
              :selectable="(row: SaveOrderTemplateCandidateRow) => row.selectable"
            />
            <ElTableColumn prop="status" label="状态" width="88" align="center" />
            <ElTableColumn prop="type" label="类型" width="88" align="center" />
            <ElTableColumn prop="content" label="医嘱内容" min-width="280" show-overflow-tooltip />
          </ElTable>
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
</style>
