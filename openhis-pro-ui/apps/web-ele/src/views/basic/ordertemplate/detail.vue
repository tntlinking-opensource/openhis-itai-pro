<script lang="ts" setup>
import type { TableColumn } from '@vben-core/table-layout-ui';

import type {
  OrderTemplateHeaderItem,
  OrderTemplateInitResponse,
  OrderTemplateLineEditItem,
  OrderTemplateLineRowItem,
} from '#/api/basic/order-template';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, onMounted, ref, watch } from 'vue';

import { TableLayout } from '@vben-core/table-layout-ui';

import {
  ElButton,
  ElCheckbox,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTag,
} from 'element-plus';

import {
  getOrderTemplateDetailApi,
  ORDER_TEMPLATE_KIND_PRICING,
  ORDER_TEMPLATE_KIND_TCM,
  ORDER_TEMPLATE_LINE_KIND_DEVICE,
  ORDER_TEMPLATE_LINE_KIND_MEDICATION,
  ORDER_TEMPLATE_LINE_KIND_SERVICE,
  saveMaintainedOrderTemplateLinesApi,
} from '#/api/basic/order-template';
import {
  pageDeviceCatalogApi,
  pageMedicationCatalogApi,
  pageServiceCatalogApi,
} from '#/api/core/system/catalog';
import SelectTable from '#/components/select-table.vue';
import { useRequest } from '#/hooks/use-request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import { buildLineKindSelectOptions } from './order-template-lines';

interface Props {
  orderTemplateId: string;
  templateKindEnum?: string;
  initOptions?: OrderTemplateInitResponse;
}

defineOptions({ name: 'BasicOrderTemplateDetail' });

const props = withDefaults(defineProps<Props>(), {
  templateKindEnum: 'GENERAL',
  initOptions: () => ({}),
});

const emit = defineEmits<{
  /** 明细保存成功后通知父级刷新列表头信息 */
  saved: [];
}>();

interface DraftLine extends OrderTemplateLineRowItem {
  _clientKey: string;
  _deleted?: boolean;
  _productLabel?: string;
}

const header = ref<OrderTemplateHeaderItem>({});
const draftLines = ref<DraftLine[]>([]);
const savedSnapshot = ref('');

/** 目录项下拉表格列 */
const productSelectColumns = [{ prop: 'label', label: '名称', width: 320 }];

let clientKeySeed = 0;

const isTcmKind = computed(() => props.templateKindEnum === ORDER_TEMPLATE_KIND_TCM);
const isPricingKind = computed(() => props.templateKindEnum === ORDER_TEMPLATE_KIND_PRICING);

/** 当前 Tab 可选项目类型（划价组套隐藏药品） */
const lineKindOptions = computed(() => {
  const all = buildLineKindSelectOptions(props.initOptions?.lineKindOptions ?? []);
  if (isPricingKind.value) {
    return all.filter((item) => item.value !== ORDER_TEMPLATE_LINE_KIND_MEDICATION);
  }
  return all;
});

const isDirty = computed(() => serializeLines(draftLines.value) !== savedSnapshot.value);

/** 仅草稿态允许维护明细行 */
const isReadonly = computed(() => header.value.statusEnum !== 'DRAFT');

const tableColumns = computed<TableColumn[]>(() => {
  const cols: TableColumn[] = [
    { type: 'index', label: '序号', width: 58, align: 'center' },
    { prop: 'lineKindEnum', label: '项目类型', width: 110, slot: 'lineKind' },
    { prop: 'productName', label: '目录项', minWidth: 180, slot: 'product' },
    { prop: 'defaultQuantity', label: '数量', width: 100, slot: 'quantity' },
    { prop: 'defaultUnitCode', label: '单位', width: 100, slot: 'unitCode' },
    { prop: 'sortOrder', label: '排序', width: 80, slot: 'sortOrder' },
    { prop: 'lineRemark', label: '备注', minWidth: 120, slot: 'remark' },
    { label: '操作', width: 80, fixed: 'right', slot: 'actions', align: 'center' },
  ];
  if (isTcmKind.value) {
    cols.splice(5, 0, {
      prop: 'chineseHerbsDoseQuantity',
      label: '付数',
      width: 90,
      slot: 'doseQuantity',
    });
    cols.splice(6, 0, {
      prop: 'sufferingFlag',
      label: '代煎',
      width: 70,
      slot: 'suffering',
    });
  }
  return cols;
});

const queryParams = ref({ pageNo: 1, pageSize: 50 });

const { run: runDetail, loading: detailLoading } = useRequest(getOrderTemplateDetailApi, {
  manual: true,
  onSuccess: (data) => {
    header.value = data.header ?? {};
    draftLines.value = (data.lines ?? []).map((line) => toDraftLine(line));
    savedSnapshot.value = serializeLines(draftLines.value);
  },
});

const { run: runSave, loading: saveLoading } = useRequest(saveMaintainedOrderTemplateLinesApi, {
  manual: true,
  onSuccess: () => {
    ElMessage.success('组套明细保存成功');
    runDetail({ orderTemplateId: props.orderTemplateId });
    emit('saved');
  },
});

const loading = computed(() => detailLoading.value || saveLoading.value);

/** 生成行草稿唯一键 */
function nextClientKey() {
  return `line:${++clientKeySeed}`;
}

/** 接口行转草稿行 */
function toDraftLine(line: OrderTemplateLineRowItem): DraftLine {
  return {
    ...line,
    _clientKey: line.lineId ? `persisted:${line.lineId}` : nextClientKey(),
    _productLabel: line.productName ?? '',
  };
}

/** 序列化用于脏检查 */
function serializeLines(lines: DraftLine[]) {
  return JSON.stringify(
    lines.map((line) => ({
      lineId: line.lineId ?? '',
      deleted: line._deleted ?? false,
      lineKindEnum: line.lineKindEnum ?? '',
      medicationDefinitionId: line.medicationDefinitionId ?? '',
      activityDefinitionId: line.activityDefinitionId ?? '',
      deviceDefinitionId: line.deviceDefinitionId ?? '',
      sortOrder: line.sortOrder ?? 0,
      defaultQuantity: line.defaultQuantity ?? '',
      defaultUnitCode: line.defaultUnitCode ?? '',
      lineRemark: line.lineRemark ?? '',
    })),
  );
}

/** 新增空白行 */
function handleAddLine() {
  draftLines.value.push({
    _clientKey: nextClientKey(),
    lineKindEnum: isPricingKind.value
      ? ORDER_TEMPLATE_LINE_KIND_SERVICE
      : ORDER_TEMPLATE_LINE_KIND_MEDICATION,
    sortOrder: draftLines.value.length + 1,
    defaultQuantity: 1,
  });
}

/** 标记删除行 */
function handleRemoveLine(row: DraftLine) {
  if (row.lineId) {
    row._deleted = true;
    return;
  }
  draftLines.value = draftLines.value.filter((item) => item._clientKey !== row._clientKey);
}

/** 可见行（未标记删除） */
const visibleLines = computed(() => draftLines.value.filter((line) => !line._deleted));

/** 解析表格 slot 行到 draftLines 源对象，避免 VxeGrid 代理导致选中丢失 */
function resolveDraftLine(row: DraftLine): DraftLine {
  return draftLines.value.find((item) => item._clientKey === row._clientKey) ?? row;
}

/** 读取当前行已选目录 id */
function getProductId(row: DraftLine): string {
  const line = resolveDraftLine(row);
  return String(
    line.medicationDefinitionId || line.activityDefinitionId || line.deviceDefinitionId || '',
  );
}

/** 切换行类型时清空目录引用 */
function handleLineKindChange(row: DraftLine) {
  const line = resolveDraftLine(row);
  line.medicationDefinitionId = undefined;
  line.activityDefinitionId = undefined;
  line.deviceDefinitionId = undefined;
  line._productLabel = '';
  line.productName = undefined;
}

/** 目录项映射为 SelectTable 行 */
function mapCatalogItemToOption(
  item: Record<string, unknown>,
  lineKindEnum: string | undefined,
): ApiOptionItem | null {
  let value = '';
  value =
    lineKindEnum === ORDER_TEMPLATE_LINE_KIND_MEDICATION
      ? String(item.id ?? item.medicationDefId ?? '')
      : String(item.id ?? '');
  if (!value) {
    return null;
  }
  const label =
    lineKindEnum === ORDER_TEMPLATE_LINE_KIND_DEVICE
      ? String(item.name ?? item.merchandiseName ?? '')
      : String(item.name ?? '');
  if (!label) {
    return null;
  }
  return { value, label };
}

/** 按行类型分页检索目录项（供 SelectTable 远程表格使用） */
async function searchProductPage(
  row: DraftLine,
  params: { keyword?: string; pageNo: number; pageSize: number },
) {
  const line = resolveDraftLine(row);
  const requestParams = {
    pageNo: params.pageNo,
    pageSize: params.pageSize,
    keyword: params.keyword?.trim() || undefined,
  };
  try {
    if (line.lineKindEnum === ORDER_TEMPLATE_LINE_KIND_MEDICATION) {
      const res = await pageMedicationCatalogApi(requestParams);
      const parsed = parsePagedRowsFromApiData<Record<string, unknown>>(res?.data);
      const content = parsed.content
        .map((item) => mapCatalogItemToOption(item, line.lineKindEnum))
        .filter((item): item is ApiOptionItem => !!item);
      return { content, totalCount: parsed.totalCount };
    }
    if (line.lineKindEnum === ORDER_TEMPLATE_LINE_KIND_SERVICE) {
      // 组套选品同医生站：隐藏已被套餐引用的明细子项
      const res = await pageServiceCatalogApi({
        ...requestParams,
        includeReferencedPackageChildren: false,
      });
      const parsed = parsePagedRowsFromApiData<Record<string, unknown>>(res?.data);
      const content = parsed.content
        .map((item) => mapCatalogItemToOption(item, line.lineKindEnum))
        .filter((item): item is ApiOptionItem => !!item);
      return { content, totalCount: parsed.totalCount };
    }
    if (line.lineKindEnum === ORDER_TEMPLATE_LINE_KIND_DEVICE) {
      const res = await pageDeviceCatalogApi(requestParams);
      const parsed = parsePagedRowsFromApiData<Record<string, unknown>>(res?.data);
      const content = parsed.content
        .map((item) => mapCatalogItemToOption(item, line.lineKindEnum))
        .filter((item): item is ApiOptionItem => !!item);
      return { content, totalCount: parsed.totalCount };
    }
  } catch {
    ElMessage.error('目录项加载失败，请稍后重试');
  }
  return { content: [], totalCount: 0 };
}

/** 选中目录项后写入对应 id 字段 */
function applyProductSelection(row: DraftLine, productId: string, productLabel?: string) {
  const line = resolveDraftLine(row);
  line.medicationDefinitionId = undefined;
  line.activityDefinitionId = undefined;
  line.deviceDefinitionId = undefined;
  const id = preserveApiIdForRequest(productId);
  if (!id) {
    line._productLabel = '';
    line.productName = undefined;
    return;
  }
  line._productLabel = productLabel ?? line._productLabel ?? line.productName ?? '';
  switch (line.lineKindEnum) {
    case ORDER_TEMPLATE_LINE_KIND_DEVICE: {
      line.deviceDefinitionId = id;

      break;
    }
    case ORDER_TEMPLATE_LINE_KIND_MEDICATION: {
      line.medicationDefinitionId = id;

      break;
    }
    case ORDER_TEMPLATE_LINE_KIND_SERVICE: {
      line.activityDefinitionId = id;

      break;
    }
    // No default
  }
}

/** SelectTable 确认选中目录项 */
function handleProductTableClose(
  row: DraftLine,
  item: Record<string, unknown>,
  productId: string,
): boolean {
  applyProductSelection(row, productId, String(item.label ?? ''));
  return true;
}

/** SelectTable 清空目录项 */
function handleProductIdChange(row: DraftLine, productId: null | string | string[] | undefined) {
  if (productId === null || productId === undefined || productId === '') {
    applyProductSelection(row, '');
  }
}

/** 校验单行 */
function validateLine(row: DraftLine, index: number): boolean {
  const label = `第 ${index + 1} 行`;
  if (!row.lineKindEnum) {
    ElMessage.error(`${label}：项目类型不能为空`);
    return false;
  }
  const hasProduct =
    row.medicationDefinitionId || row.activityDefinitionId || row.deviceDefinitionId;
  if (!hasProduct) {
    ElMessage.error(`${label}：请选择目录项`);
    return false;
  }
  if (!row.sortOrder || row.sortOrder < 1) {
    ElMessage.error(`${label}：排序须 ≥ 1`);
    return false;
  }
  if (isPricingKind.value && row.lineKindEnum === ORDER_TEMPLATE_LINE_KIND_MEDICATION) {
    ElMessage.error(`${label}：划价组套不允许药品行`);
    return false;
  }
  return true;
}

/** 组装 IF-05 全量对齐行（OrderTemplateLineEditDto） */
function buildSubmitLines(): OrderTemplateLineEditItem[] {
  return draftLines.value.map((row) => ({
    lineId: preserveApiIdForRequest(row.lineId),
    deleted: row._deleted ?? false,
    lineKindEnum: row.lineKindEnum ?? '',
    medicationDefinitionId: preserveApiIdForRequest(row.medicationDefinitionId),
    activityDefinitionId: preserveApiIdForRequest(row.activityDefinitionId),
    deviceDefinitionId: preserveApiIdForRequest(row.deviceDefinitionId),
    groupKey: row.groupKey,
    sortOrder: row.sortOrder ?? 1,
    defaultQuantity: row.defaultQuantity,
    defaultUnitCode: row.defaultUnitCode,
    defaultDays: row.defaultDays,
    frequencyId: preserveApiIdForRequest(row.frequencyId),
    routeCode: row.routeCode,
    dosageText: row.dosageText,
    defaultPerformOrgId: preserveApiIdForRequest(row.defaultPerformOrgId),
    lineRemark: row.lineRemark,
    chineseHerbsDoseQuantity: row.chineseHerbsDoseQuantity,
    sufferingFlag: row.sufferingFlag,
  }));
}

/** 保存全量明细 */
async function handleSaveLines() {
  const activeLines = draftLines.value.filter((line) => !line._deleted);
  for (const [i, row] of activeLines.entries()) {
    if (!row || !validateLine(row, i)) {
      return;
    }
  }
  try {
    await ElMessageBox.confirm('将按当前明细全量覆盖组套行，确定保存吗？', '保存明细', {
      type: 'warning',
    });
  } catch {
    return;
  }
  await runSave({
    orderTemplateId: preserveApiIdForRequest(props.orderTemplateId) ?? props.orderTemplateId,
    lines: buildSubmitLines(),
  });
}

watch(
  () => props.orderTemplateId,
  (id) => {
    if (id) {
      runDetail({ orderTemplateId: id });
    }
  },
);

onMounted(() => {
  if (props.orderTemplateId) {
    runDetail({ orderTemplateId: props.orderTemplateId });
  }
});
</script>

<template>
  <div
    class="flex min-h-[360px] flex-1 flex-col gap-2 border-t border-[var(--el-border-color-lighter)] pt-3"
  >
    <div class="flex flex-wrap items-center justify-between gap-2">
      <div class="flex flex-wrap items-center gap-2">
        <span class="font-semibold text-[var(--el-text-color-primary)]">组套明细</span>
        <span v-if="header.name" class="text-sm text-[var(--el-text-color-regular)]">
          （{{ header.name }}）
        </span>
        <ElTag v-if="header.statusEnumText" type="info">{{ header.statusEnumText }}</ElTag>
        <ElTag v-if="isDirty" type="warning">未保存</ElTag>
      </div>
      <ElButton type="primary" :loading="loading" :disabled="isReadonly" @click="handleSaveLines">
        保存明细
      </ElButton>
    </div>

    <p v-if="isReadonly" class="m-0 text-sm text-[var(--el-text-color-regular)]">
      仅草稿态组套可维护明细；已发布或已停用的组套须先停用后重新编辑，或新建组套。
    </p>

    <TableLayout
      :table-data="visibleLines"
      :loading="loading"
      :total="visibleLines.length"
      :query-params="queryParams"
      :form-items="[]"
      :table-columns="tableColumns"
      :show-top-query="false"
      :show-pagination="false"
      row-key="_clientKey"
    >
      <template #toolbar-actions>
        <ElButton type="primary" :disabled="isReadonly" @click="handleAddLine">新增行</ElButton>
      </template>

      <template #lineKind="{ row }">
        <ElSelect
          v-model="row.lineKindEnum"
          :disabled="isReadonly"
          placeholder="类型"
          style="width: 100px"
          @change="handleLineKindChange(row)"
        >
          <ElOption
            v-for="item in lineKindOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </ElSelect>
      </template>

      <template #product="{ row }">
        <SelectTable
          :model-value="getProductId(row)"
          :selected-label="row._productLabel || row.productName || ''"
          :disabled="isReadonly || !row.lineKindEnum"
          :remote-method="(params) => searchProductPage(row, params)"
          :columns="productSelectColumns"
          value-key="value"
          label-key="label"
          placeholder="搜索目录项"
          :panel-width="520"
          :before-close="(item, val) => handleProductTableClose(row, item, String(val))"
          @update:model-value="(val) => handleProductIdChange(row, val)"
        />
      </template>

      <template #quantity="{ row }">
        <ElInputNumber
          v-model="row.defaultQuantity"
          :disabled="isReadonly"
          :min="0"
          :precision="2"
          controls-position="right"
          style="width: 90px"
        />
      </template>

      <template #unitCode="{ row }">
        <ElInput
          v-model="row.defaultUnitCode"
          :disabled="isReadonly"
          placeholder="单位"
          style="width: 80px"
        />
      </template>

      <template #sortOrder="{ row }">
        <ElInputNumber
          v-model="row.sortOrder"
          :disabled="isReadonly"
          :min="1"
          controls-position="right"
          style="width: 70px"
        />
      </template>

      <template #doseQuantity="{ row }">
        <ElInputNumber
          v-model="row.chineseHerbsDoseQuantity"
          :disabled="isReadonly"
          :min="1"
          controls-position="right"
          style="width: 70px"
        />
      </template>

      <template #suffering="{ row }">
        <ElCheckbox v-model="row.sufferingFlag" :disabled="isReadonly" />
      </template>

      <template #remark="{ row }">
        <ElInput v-model="row.lineRemark" :disabled="isReadonly" placeholder="备注" clearable />
      </template>

      <template #actions="{ row }">
        <ElButton link type="danger" :disabled="isReadonly" @click="handleRemoveLine(row)">
          删除
        </ElButton>
      </template>
    </TableLayout>
  </div>
</template>
