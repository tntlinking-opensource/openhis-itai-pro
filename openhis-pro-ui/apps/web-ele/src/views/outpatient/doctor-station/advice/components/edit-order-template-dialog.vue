<script setup lang="ts">
import type {
  OrderTemplateHeaderItem,
  OrderTemplateLineEditItem,
  OrderTemplateLineRowItem,
} from '#/api/basic/order-template';

import { ref, watch } from 'vue';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElRadio,
  ElRadioGroup,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import {
  getOrderTemplateDetailApi,
  ORDER_TEMPLATE_BINDING_DEPARTMENT,
  ORDER_TEMPLATE_BINDING_PERSONAL,
  ORDER_TEMPLATE_LINE_KIND_DEVICE,
  ORDER_TEMPLATE_LINE_KIND_MEDICATION,
  ORDER_TEMPLATE_LINE_KIND_SERVICE,
  saveMaintainedOrderTemplateHeaderApi,
  saveMaintainedOrderTemplateLinesApi,
} from '#/api/basic/order-template';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

defineOptions({ name: 'EditOrderTemplateDialog' });

interface Props {
  /** 待编辑组套头（打开弹窗时传入） */
  template?: null | OrderTemplateHeaderItem;
}

interface EditLineRow {
  dosageText: string;
  key: string;
  kindLabel: string;
  lineId: string;
  lineKindEnum: string;
  productName: string;
  quantity: number;
  source: OrderTemplateLineRowItem;
  unitName: string;
}

const props = withDefaults(defineProps<Props>(), {
  template: null,
});

const emit = defineEmits<{
  saved: [];
}>();

const open = defineModel<boolean>('open', { default: false });

const detailLoading = ref(false);
const saveLoading = ref(false);
const templateName = ref('');
/** 归属范围：个人 / 科室（与另存弹窗一致） */
const bindingEnum = ref(ORDER_TEMPLATE_BINDING_DEPARTMENT);
const headerSnapshot = ref<null | OrderTemplateHeaderItem>(null);
const editLines = ref<EditLineRow[]>([]);
/** 打开时的全部原始行，保存时对删除行打 deleted 做全量对齐 */
const originalLines = ref<OrderTemplateLineRowItem[]>([]);

/** 行类型展示 */
function resolveLineKindLabel(line: OrderTemplateLineRowItem): string {
  const text = String(line.lineKindEnumText ?? '').trim();
  if (text) {
    return text;
  }
  const kind = String(line.lineKindEnum ?? '')
    .trim()
    .toUpperCase();
  if (kind === ORDER_TEMPLATE_LINE_KIND_MEDICATION) {
    return '药品';
  }
  if (kind === ORDER_TEMPLATE_LINE_KIND_SERVICE) {
    return '诊疗';
  }
  if (kind === ORDER_TEMPLATE_LINE_KIND_DEVICE) {
    return '耗材';
  }
  return kind || '—';
}

/** 详情行 → 弹窗编辑行 */
function mapDetailLineToEditRow(line: OrderTemplateLineRowItem, index: number): EditLineRow {
  const lineId = preserveApiIdForRequest(line.lineId) ?? '';
  const quantityRaw = Number(line.defaultQuantity);
  return {
    key: lineId || `line-${index}`,
    lineId,
    lineKindEnum: String(line.lineKindEnum ?? '').trim(),
    kindLabel: resolveLineKindLabel(line),
    productName: String(line.productName ?? '').trim() || '未命名项目',
    quantity: Number.isFinite(quantityRaw) && quantityRaw > 0 ? quantityRaw : 1,
    unitName: String(line.defaultUnitName ?? '').trim(),
    dosageText: String(line.dosageText ?? '').trim(),
    source: line,
  };
}

/** 打开弹窗后拉详情并填充表单 */
async function loadDetailForEdit() {
  const orderTemplateId = preserveApiIdForRequest(props.template?.orderTemplateId);
  if (!orderTemplateId) {
    ElMessage.warning('组套无效，无法编辑');
    open.value = false;
    return;
  }
  detailLoading.value = true;
  templateName.value = String(props.template?.name ?? '').trim();
  const initialBinding = String(props.template?.bindingEnum ?? '')
    .trim()
    .toUpperCase();
  bindingEnum.value =
    initialBinding === ORDER_TEMPLATE_BINDING_PERSONAL
      ? ORDER_TEMPLATE_BINDING_PERSONAL
      : ORDER_TEMPLATE_BINDING_DEPARTMENT;
  headerSnapshot.value = props.template ? { ...props.template } : null;
  editLines.value = [];
  originalLines.value = [];
  try {
    const detail = await getOrderTemplateDetailApi({ orderTemplateId });
    const header = detail?.header ?? props.template ?? null;
    headerSnapshot.value = header ? { ...header } : null;
    templateName.value = String(header?.name ?? props.template?.name ?? '').trim();
    const loadedBinding = String(header?.bindingEnum ?? '')
      .trim()
      .toUpperCase();
    bindingEnum.value =
      loadedBinding === ORDER_TEMPLATE_BINDING_PERSONAL
        ? ORDER_TEMPLATE_BINDING_PERSONAL
        : ORDER_TEMPLATE_BINDING_DEPARTMENT;
    const lines = detail?.lines ?? [];
    originalLines.value = lines.map((line) => ({ ...line }));
    editLines.value = lines.map((line, index) => mapDetailLineToEditRow(line, index));
    if (editLines.value.length === 0) {
      ElMessage.warning('该组套暂无明细');
    }
  } catch {
    open.value = false;
  } finally {
    detailLoading.value = false;
  }
}

/** 更新数量 */
function handleQuantityChange(row: EditLineRow, value: number | undefined) {
  if (!Number.isFinite(value) || (value ?? 0) <= 0) {
    return;
  }
  row.quantity = Number(value);
}

/** 移除明细行（本次保存后生效） */
function handleRemoveLine(row: EditLineRow) {
  editLines.value = editLines.value.filter((item) => item.key !== row.key);
}

/** 行 → 保存入参（保留原行字段） */
function toLineEditItem(
  line: OrderTemplateLineRowItem,
  options: { deleted?: boolean; quantity?: number; sortOrder: number },
): OrderTemplateLineEditItem {
  return {
    lineId: preserveApiIdForRequest(line.lineId),
    deleted: options.deleted ?? false,
    lineKindEnum: String(line.lineKindEnum ?? '').trim(),
    medicationDefinitionId: preserveApiIdForRequest(line.medicationDefinitionId),
    activityDefinitionId: preserveApiIdForRequest(line.activityDefinitionId),
    deviceDefinitionId: preserveApiIdForRequest(line.deviceDefinitionId),
    groupKey: line.groupKey,
    sortOrder: options.sortOrder,
    defaultQuantity: options.quantity ?? line.defaultQuantity,
    defaultUnitCode: line.defaultUnitCode,
    defaultDays: line.defaultDays,
    frequencyId: preserveApiIdForRequest(line.frequencyId),
    routeCode: line.routeCode,
    dosageText: line.dosageText,
    defaultPerformOrgId: preserveApiIdForRequest(line.defaultPerformOrgId),
    lineRemark: line.lineRemark,
    chineseHerbsDoseQuantity: line.chineseHerbsDoseQuantity,
    sufferingFlag: line.sufferingFlag,
  };
}

/** 组装全量对齐行（含删除行） */
function buildSubmitLines(): OrderTemplateLineEditItem[] {
  const keptById = new Map(
    editLines.value
      .map((row) => [preserveApiIdForRequest(row.lineId) ?? row.key, row] as const)
      .filter(([id]) => !!id),
  );
  const result: OrderTemplateLineEditItem[] = [];
  let visibleOrder = 0;
  for (const source of originalLines.value) {
    const lineId = preserveApiIdForRequest(source.lineId) ?? '';
    const kept = lineId ? keptById.get(lineId) : undefined;
    if (!kept) {
      result.push(
        toLineEditItem(source, {
          deleted: true,
          sortOrder: source.sortOrder ?? 1,
        }),
      );
      continue;
    }
    visibleOrder += 1;
    result.push(
      toLineEditItem(source, {
        quantity: kept.quantity,
        sortOrder: visibleOrder,
      }),
    );
  }
  return result;
}

/** 保存组套名称与明细 */
async function handleConfirmSave() {
  const orderTemplateId = preserveApiIdForRequest(
    headerSnapshot.value?.orderTemplateId ?? props.template?.orderTemplateId,
  );
  if (!orderTemplateId) {
    ElMessage.warning('组套无效，无法保存');
    return;
  }
  const name = templateName.value.trim();
  if (!name) {
    ElMessage.warning('请输入组套名称');
    return;
  }
  if (editLines.value.length === 0) {
    ElMessage.warning('请至少保留一条组套明细');
    return;
  }
  if (editLines.value.some((row) => !Number.isFinite(row.quantity) || row.quantity <= 0)) {
    ElMessage.warning('请填写有效数量');
    return;
  }
  const header = headerSnapshot.value ?? props.template;
  const nextBinding = String(bindingEnum.value ?? '').trim();
  const sceneEnum = String(header?.sceneEnum ?? '').trim();
  const templateKindEnum = String(header?.templateKindEnum ?? '').trim();
  if (!nextBinding || !sceneEnum || !templateKindEnum) {
    ElMessage.warning('组套基本信息不完整，无法保存');
    return;
  }
  // 归属变更时不传原 objectId，由后端按个人/科室会话解析新归属
  const originalBinding = String(header?.bindingEnum ?? '')
    .trim()
    .toUpperCase();
  const bindingChanged = nextBinding.toUpperCase() !== originalBinding;
  saveLoading.value = true;
  try {
    await saveMaintainedOrderTemplateHeaderApi({
      orderTemplateId,
      bindingEnum: nextBinding,
      objectId: bindingChanged
        ? undefined
        : preserveApiIdForRequest(header?.objectId) || undefined,
      sceneEnum,
      templateKindEnum,
      name,
      sortWeight: header?.sortWeight,
      description: header?.description,
      remark: header?.remark,
      defaultChineseHerbsDoseQuantity: header?.defaultChineseHerbsDoseQuantity,
      defaultSufferingFlag: header?.defaultSufferingFlag,
    });
    await saveMaintainedOrderTemplateLinesApi({
      orderTemplateId,
      lines: buildSubmitLines(),
    });
    ElMessage.success(`已保存组套「${name}」`);
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
    void loadDetailForEdit();
  }
});
</script>

<template>
  <ElDialog
    v-model="open"
    title="编辑组套"
    width="960px"
    top="8vh"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
    class="edit-order-template-dialog"
  >
    <div v-loading="detailLoading">
      <ElForm label-width="88px" class="edit-order-template-form">
        <div class="edit-order-template-meta">
          <ElFormItem label="组套名称" required class="edit-order-template-meta__name">
            <ElInput
              v-model="templateName"
              maxlength="64"
              show-word-limit
              placeholder="请输入组套名称"
              clearable
            />
          </ElFormItem>
          <ElFormItem label="归属范围" class="edit-order-template-meta__binding">
            <ElRadioGroup v-model="bindingEnum">
              <ElRadio :value="ORDER_TEMPLATE_BINDING_DEPARTMENT">科室组套</ElRadio>
              <ElRadio :value="ORDER_TEMPLATE_BINDING_PERSONAL">个人组套</ElRadio>
            </ElRadioGroup>
          </ElFormItem>
        </div>
        <ElFormItem label="组套明细" class="edit-order-template-form__lines">
          <ElTable
            :data="editLines"
            border
            stripe
            max-height="420"
            row-key="key"
            empty-text="暂无明细"
            class="edit-order-template-table w-full"
          >
            <ElTableColumn prop="kindLabel" label="类型" width="80" align="center" />
            <ElTableColumn prop="productName" label="项目名称" min-width="220" show-overflow-tooltip />
            <ElTableColumn label="数量" width="180" align="center">
              <template #default="{ row }">
                <div class="edit-order-template-qty">
                  <ElInputNumber
                    class="edit-order-template-qty__input"
                    :model-value="row.quantity"
                    :min="0.01"
                    :step="1"
                    :precision="2"
                    :controls="false"
                    @update:model-value="(value) => handleQuantityChange(row, value)"
                  />
                  <span v-if="row.unitName" class="edit-order-template-qty__unit">{{
                    row.unitName
                  }}</span>
                </div>
              </template>
            </ElTableColumn>
            <ElTableColumn prop="dosageText" label="剂量/说明" min-width="140" show-overflow-tooltip />
            <ElTableColumn label="操作" width="72" align="center" fixed="right">
              <template #default="{ row }">
                <ElButton type="danger" link size="small" @click="handleRemoveLine(row)">
                  删除
                </ElButton>
              </template>
            </ElTableColumn>
          </ElTable>
        </ElFormItem>
      </ElForm>
    </div>

    <template #footer>
      <ElButton @click="open = false">取消</ElButton>
      <ElButton
        type="primary"
        :loading="saveLoading"
        :disabled="detailLoading || editLines.length === 0"
        @click="handleConfirmSave"
      >
        保存
      </ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.edit-order-template-form :deep(.el-form-item) {
  margin-bottom: 14px;
}

.edit-order-template-form__lines {
  margin-bottom: 0;
}

.edit-order-template-meta {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(280px, 0.8fr);
  gap: 8px 24px;
  align-items: start;
}

.edit-order-template-meta__name,
.edit-order-template-meta__binding {
  margin-bottom: 12px;
}

.edit-order-template-qty {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.edit-order-template-qty__input {
  width: 96px;
}

.edit-order-template-qty__unit {
  color: var(--el-text-color-regular);
  font-size: 13px;
  white-space: nowrap;
}
</style>
