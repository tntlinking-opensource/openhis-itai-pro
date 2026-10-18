import type { TableColumn } from '@zxplat-core/table-layout-ui';

import type { MedicationLineDraftSlice } from './order-template-medication-units';

import type {
  OrderTemplateInitResponse,
  OrderTemplateLineEditItem,
  OrderTemplateLineRowItem,
} from '#/api/basic/order-template';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, ref } from 'vue';

import { ElMessage } from 'element-plus';

import {
  ORDER_TEMPLATE_KIND_PRICING,
  ORDER_TEMPLATE_KIND_SERVICE_APPLY,
  ORDER_TEMPLATE_KIND_TCM,
  ORDER_TEMPLATE_LINE_KIND_DEVICE,
  ORDER_TEMPLATE_LINE_KIND_MEDICATION,
  ORDER_TEMPLATE_LINE_KIND_SERVICE,
  SERVICE_APPLY_TEMPLATE_CATEGORY_EXAM,
  SERVICE_APPLY_TEMPLATE_CATEGORY_LAB,
  SERVICE_APPLY_TEMPLATE_CATEGORY_OPTIONS,
  SERVICE_APPLY_TEMPLATE_CATEGORY_TREATMENT,
} from '#/api/basic/order-template';
import {
  pageDeviceCatalogApi,
  pageMedicationCatalogApi,
  pageServiceCatalogApi,
} from '#/api/core/system/catalog';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import {
  SERVICE_CATEGORY_PROOF,
  SERVICE_CATEGORY_TEST,
  SERVICE_CATEGORY_TREATMENT,
} from '#/utils/print/constants/service-request-category-codes';
import { parseOptionalNumberInput } from '#/views/outpatient/doctor-station/advice/services/advice-edit';

import {
  applyDeviceCatalogSelectionToLine,
  applyDoseQtyToSplitSide,
  applyMedicationCatalogSelectionToLine,
  applyPricingCatalogSelectionToLine,
  applyServiceCatalogSelectionToLine,
  applySplitDoseToDoseSide,
  buildDosageText,
  clearLineDoseFields,
  displayMedicationDoseQty,
  enrichMedicationLinesUnitBands,
  formatLineDoseDisplayText,
  getLinePricingUnitOptions,
  getLineTotalUnitOptions,
  handleLineTotalUnitChange,
  hydrateDraftLineDoseFromDosageText,
  parseDosageTextParts,
  resolvePricingCatalogUnitDisplayText,
} from './order-template-medication-units';

export type OrderTemplateLineColumnProfile = 'DEVICE' | 'MEDICATION' | 'SERVICE';

export interface DraftLine extends MedicationLineDraftSlice, OrderTemplateLineRowItem {
  _clientKey: string;
  _deleted?: boolean;
  _productLabel?: string;
}

export interface CatalogSelectRow extends ApiOptionItem {
  busNo?: string;
  specification?: string;
  categoryCode_dictText?: string;
  retailPrice?: number | string;
  size?: string;
  unitCode_dictText?: string;
  catalogUnitDisplay?: string;
  [key: string]: unknown;
}

const medicationProductSelectColumns = [
  { prop: 'busNo', label: '编码', width: 96 },
  { prop: 'label', label: '名称', minWidth: 180 },
  { prop: 'specification', label: '规格', minWidth: 88 },
  { prop: 'retailPrice', label: '售价', width: 72 },
];

const serviceProductSelectColumns = [
  { prop: 'busNo', label: '编码', width: 96 },
  { prop: 'label', label: '名称', minWidth: 180 },
  { prop: 'catalogUnitDisplay', label: '单位', width: 72 },
];

const deviceProductSelectColumns = [
  { prop: 'busNo', label: '编码', width: 96 },
  { prop: 'label', label: '名称', minWidth: 180 },
  { prop: 'size', label: '规格', minWidth: 88 },
  { prop: 'catalogUnitDisplay', label: '单位', width: 72 },
];

/** 项目类型选项展示：去掉后端枚举 display 末尾「行」字 */
export function formatLineKindOptionLabel(label: string | undefined): string {
  const text = String(label ?? '').trim();
  if (!text) {
    return '';
  }
  return text.endsWith('行') ? text.slice(0, -1) : text;
}

/** 初始化接口行类型选项 → 项目类型下拉 */
export function buildLineKindSelectOptions(items: ApiOptionItem[]): ApiOptionItem[] {
  return items.map((item) => ({
    ...item,
    label: formatLineKindOptionLabel(item.label) || item.label,
  }));
}

/** 按项目类型返回目录下拉列 */
export function getProductSelectColumns(lineKindEnum: string | undefined) {
  if (lineKindEnum === ORDER_TEMPLATE_LINE_KIND_SERVICE) {
    return serviceProductSelectColumns;
  }
  if (lineKindEnum === ORDER_TEMPLATE_LINE_KIND_DEVICE) {
    return deviceProductSelectColumns;
  }
  return medicationProductSelectColumns;
}

let clientKeySeed = 0;

/** 生成行草稿唯一键 */
export function nextLineClientKey() {
  return `line:${++clientKeySeed}`;
}

/** 接口行转草稿行 */
export function toDraftLine(line: OrderTemplateLineRowItem): DraftLine {
  const draft: DraftLine = {
    ...line,
    _clientKey: line.lineId ? `persisted:${line.lineId}` : nextLineClientKey(),
    _productLabel: line.productName ?? '',
  };
  hydrateDraftLineDoseFromDosageText(draft);
  return draft;
}

/** 重置行草稿计数器并返回空列表 */
export function resetDraftLinesState() {
  clientKeySeed = 0;
}

/** 申请单组套明细（样本/部位主键）写在 dosageText，避免被当成药品剂量解析 */
const SERVICE_APPLY_DETAIL_PREFIX = 'DETAIL:';

/** 从申请单组套行 dosageText 取出样本或部位主键（字符串，禁止 Number） */
export function decodeServiceApplyDetailIds(dosageText: string | undefined): string[] {
  const text = String(dosageText ?? '').trim();
  if (!text.startsWith(SERVICE_APPLY_DETAIL_PREFIX)) {
    return [];
  }
  const unique = new Set<string>();
  for (const part of text.slice(SERVICE_APPLY_DETAIL_PREFIX.length).split(',')) {
    const id = part.trim();
    if (id) {
      unique.add(id);
    }
  }
  return [...unique];
}

/** 把样本或部位主键写回 dosageText；空选择不落库 */
export function encodeServiceApplyDetailIds(ids: readonly string[]): string | undefined {
  const joined = ids
    .map((id) => String(id ?? '').trim())
    .filter(Boolean)
    .join(',');
  const unique = decodeServiceApplyDetailIds(`${SERVICE_APPLY_DETAIL_PREFIX}${joined}`);
  if (unique.length === 0) {
    return undefined;
  }
  return `${SERVICE_APPLY_DETAIL_PREFIX}${unique.join(',')}`;
}

/** 申请单类别名（TEST/PROOF/TREATMENT）→ 诊疗目录 categoryCode（23/22/21） */
function resolveActivityCategoryCode(applyCategory: string): string {
  if (applyCategory === SERVICE_APPLY_TEMPLATE_CATEGORY_EXAM) {
    return SERVICE_CATEGORY_TEST;
  }
  if (applyCategory === SERVICE_APPLY_TEMPLATE_CATEGORY_LAB) {
    return SERVICE_CATEGORY_PROOF;
  }
  if (applyCategory === SERVICE_APPLY_TEMPLATE_CATEGORY_TREATMENT) {
    return SERVICE_CATEGORY_TREATMENT;
  }
  return '';
}

/** 组套明细行编辑逻辑 */
export function useOrderTemplateLines(options: {
  /** 当前行内编辑键，用于按行类型切换列 */
  editingLineKey?: () => string | undefined;
  frequencyOptions: () => ApiOptionItem[];
  initOptions: () => OrderTemplateInitResponse;
  methodCodeOptions: () => ApiOptionItem[];
  templateKindEnum: () => string;
  /** 申请单组套整单类别（检查/检验/治疗），行上不再单独选 */
  applyCategoryCode?: () => string;
  unitCodeOptions: () => ApiOptionItem[];
}) {
  const draftLines = ref<DraftLine[]>([]);

  const isPricingKind = computed(() => options.templateKindEnum() === ORDER_TEMPLATE_KIND_PRICING);
  const isTcmKind = computed(() => options.templateKindEnum() === ORDER_TEMPLATE_KIND_TCM);
  const isServiceApplyKind = computed(
    () => options.templateKindEnum() === ORDER_TEMPLATE_KIND_SERVICE_APPLY,
  );

  const lineKindOptions = computed(() => {
    const all = buildLineKindSelectOptions(options.initOptions()?.lineKindOptions ?? []);
    if (isPricingKind.value) {
      return all.filter((item) => item.value !== ORDER_TEMPLATE_LINE_KIND_MEDICATION);
    }
    return all;
  });

  const visibleLines = computed(() => draftLines.value.filter((line) => !line._deleted));

  const showMedicationColumns = computed(
    () => !isPricingKind.value && !isTcmKind.value && !isServiceApplyKind.value,
  );
  const canGroupLines = computed(() => showMedicationColumns.value);

  /** 是否诊疗行 */
  function isServiceLineRow(row: DraftLine): boolean {
    return row.lineKindEnum === ORDER_TEMPLATE_LINE_KIND_SERVICE;
  }

  /** 是否耗材行 */
  function isDeviceLineRow(row: DraftLine): boolean {
    return row.lineKindEnum === ORDER_TEMPLATE_LINE_KIND_DEVICE;
  }

  /** 推断表格列布局：优先当前编辑行，否则单一类型时对齐该类型 */
  const activeLineColumnProfile = computed<OrderTemplateLineColumnProfile>(() => {
    const editingKey = options.editingLineKey?.();
    if (editingKey) {
      const editingRow = visibleLines.value.find((line) => line._clientKey === editingKey);
      if (editingRow?.lineKindEnum === ORDER_TEMPLATE_LINE_KIND_SERVICE) {
        return 'SERVICE';
      }
      if (editingRow?.lineKindEnum === ORDER_TEMPLATE_LINE_KIND_DEVICE) {
        return 'DEVICE';
      }
      return 'MEDICATION';
    }
    const kinds = new Set(
      visibleLines.value.map((line) => String(line.lineKindEnum ?? '').trim()).filter(Boolean),
    );
    if (kinds.size === 1) {
      const only = [...kinds][0];
      if (only === ORDER_TEMPLATE_LINE_KIND_SERVICE) {
        return 'SERVICE';
      }
      if (only === ORDER_TEMPLATE_LINE_KIND_DEVICE) {
        return 'DEVICE';
      }
    }
    return 'MEDICATION';
  });

  const lineSelection = ref<DraftLine[]>([]);

  /** 读取行成组键（保存字段 groupKey，对齐医生站 groupId） */
  function getLineGroupKey(row: DraftLine): string {
    return String(row.groupKey ?? '').trim();
  }

  /** 有效行按 groupKey 聚邻排序（成组行须连续） */
  function sortActiveLinesByGroupKey(lines: DraftLine[]): DraftLine[] {
    return [...lines].toSorted((a, b) => {
      const ag = getLineGroupKey(a);
      const bg = getLineGroupKey(b);
      const aGroupSort = ag || '\uFFFF';
      const bGroupSort = bg || '\uFFFF';
      const groupCompare = aGroupSort.localeCompare(bGroupSort);
      if (groupCompare !== 0) {
        return groupCompare;
      }
      return (a.sortOrder ?? 0) - (b.sortOrder ?? 0);
    });
  }

  /** 重排草稿行：未删行按 groupKey 聚邻，并重写 sortOrder */
  function reorderDraftLinesByGroup() {
    const active = draftLines.value.filter((line) => !line._deleted);
    const deleted = draftLines.value.filter((line) => line._deleted);
    const sorted = sortActiveLinesByGroupKey(active);
    sorted.forEach((line, index) => {
      line.sortOrder = index + 1;
    });
    draftLines.value = [...sorted, ...deleted];
  }

  /** 是否成组首行（用法/天数仅在首行编辑） */
  function isGroupLeaderRow(row: DraftLine): boolean {
    const groupKey = getLineGroupKey(row);
    if (!groupKey) {
      return true;
    }
    const groupLine = visibleLines.value.find((line) => getLineGroupKey(line) === groupKey);
    return groupLine?._clientKey === row._clientKey;
  }

  /** 成组括线位置（仅药品行） */
  function getGroupLinePosition(row: DraftLine): 'first' | 'last' | 'middle' | 'none' | 'single' {
    const groupKey = getLineGroupKey(row);
    if (!groupKey || !isMedicationLineRow(row)) {
      return 'none';
    }
    const groupLines = visibleLines.value.filter((line) => getLineGroupKey(line) === groupKey);
    if (groupLines.length <= 1) {
      return 'single';
    }
    const index = groupLines.findIndex((line) => line._clientKey === row._clientKey);
    if (index <= 0) {
      return 'first';
    }
    if (index >= groupLines.length - 1) {
      return 'last';
    }
    return 'middle';
  }

  /** 成组共享字段展示源：组员缺省时回落组首行 */
  function resolveGroupSharedFieldSource(row: DraftLine): DraftLine {
    const line = resolveDraftLine(row);
    const groupKey = getLineGroupKey(line);
    if (!groupKey || !isMedicationLineRow(line) || isGroupLeaderRow(line)) {
      return line;
    }
    const leader = visibleLines.value.find((item) => getLineGroupKey(item) === groupKey);
    if (!leader) {
      return line;
    }
    const resolvedLeader = resolveDraftLine(leader);
    return {
      ...line,
      routeCode: line.routeCode ?? resolvedLeader.routeCode,
      defaultDays: line.defaultDays ?? resolvedLeader.defaultDays,
      frequencyId: line.frequencyId ?? resolvedLeader.frequencyId,
      frequencyText: line.frequencyText ?? resolvedLeader.frequencyText,
    };
  }

  /** 将成组首行的用法/天数同步到同组其它行 */
  function syncGroupSharedFieldsFromLeader(row: DraftLine) {
    const line = resolveDraftLine(row);
    const groupKey = getLineGroupKey(line);
    if (!groupKey || !isGroupLeaderRow(line)) {
      return;
    }
    for (const sibling of visibleLines.value) {
      if (sibling._clientKey === line._clientKey || getLineGroupKey(sibling) !== groupKey) {
        continue;
      }
      const target = resolveDraftLine(sibling);
      target.routeCode = line.routeCode;
      target.defaultDays = line.defaultDays;
      target.frequencyId = line.frequencyId;
      target.frequencyText = line.frequencyText;
    }
  }

  /** 保存前补齐全部成组行的共享字段 */
  function syncAllGroupSharedFields() {
    const handled = new Set<string>();
    for (const row of visibleLines.value) {
      const groupKey = getLineGroupKey(row);
      if (!groupKey || handled.has(groupKey)) {
        continue;
      }
      handled.add(groupKey);
      syncGroupSharedFieldsFromLeader(row);
    }
  }

  /** 勾选变化 */
  function handleLineSelectionChange(selection: DraftLine[]) {
    lineSelection.value = selection;
  }

  /** 并组：勾选 ≥2 条药品行，写入同一 groupKey */
  function handleAssignGroup(): boolean {
    if (!canGroupLines.value) {
      return false;
    }
    const medicationRows = lineSelection.value.filter(
      (row) => isMedicationLineRow(row) && !row._deleted,
    );
    if (medicationRows.length < 2) {
      ElMessage.warning('请勾选至少两条药品明细行进行并组');
      return false;
    }
    if (medicationRows.length < lineSelection.value.length) {
      ElMessage.warning('已跳过非药品行');
    }
    const groupKey = String(Date.now());
    const leader = medicationRows[0];
    if (!leader) {
      return false;
    }
    const resolvedLeader = resolveDraftLine(leader);
    for (const row of medicationRows) {
      const line = resolveDraftLine(row);
      line.groupKey = groupKey;
      line.routeCode = resolvedLeader.routeCode;
      line.defaultDays = resolvedLeader.defaultDays;
      line.frequencyId = resolvedLeader.frequencyId;
      line.frequencyText = resolvedLeader.frequencyText;
    }
    reorderDraftLinesByGroup();
    lineSelection.value = [];
    ElMessage.success('并组成功');
    return true;
  }

  /** 拆组：清除勾选行的 groupKey */
  function handleUngroup(): boolean {
    if (!canGroupLines.value) {
      return false;
    }
    const groupedRows = lineSelection.value.filter(
      (row) => isMedicationLineRow(row) && getLineGroupKey(row) !== '',
    );
    if (groupedRows.length === 0) {
      ElMessage.warning('请勾选已并组的药品明细行');
      return false;
    }
    if (groupedRows.length < lineSelection.value.length) {
      ElMessage.warning('已跳过非药品或无组号的行');
    }
    for (const row of groupedRows) {
      resolveDraftLine(row).groupKey = undefined;
    }
    reorderDraftLinesByGroup();
    lineSelection.value = [];
    ElMessage.success('拆组成功');
    return true;
  }

  const tableColumns = computed<TableColumn[]>(() => {
    if (isServiceApplyKind.value) {
      const category = String(options.applyCategoryCode?.() ?? '').trim();
      const detailLabel = category === SERVICE_APPLY_TEMPLATE_CATEGORY_LAB ? '样本' : '部位';
      return [
        { type: 'index', label: '序号', width: '8%', align: 'center' },
        {
          prop: 'productName',
          label: '项目名称',
          width: '38%',
          showOverflowTooltip: true,
          slot: 'product',
        },
        { prop: 'defaultQuantity', label: '数量', width: '16%', slot: 'serviceQuantity' },
        { prop: 'dosageText', label: detailLabel, width: '26%', slot: 'applyDetail' },
        { label: '操作', width: '12%', slot: 'actions', align: 'center' },
      ];
    }
    if (isPricingKind.value) {
      return [
        { type: 'index', label: '序号', width: '8%', align: 'center' },
        { prop: 'lineKindEnum', label: '项目类型', width: '14%', slot: 'lineKind' },
        {
          prop: 'productName',
          label: '项目名称',
          width: '42%',
          showOverflowTooltip: true,
          slot: 'product',
        },
        { prop: 'defaultQuantity', label: '数量', width: '24%', slot: 'quantityWithUnit' },
        { label: '操作', width: '12%', slot: 'actions', align: 'center' },
      ];
    }

    const baseCols: TableColumn[] = [
      { type: 'selection', width: '6%', align: 'center' },
      { type: 'index', label: '序号', width: '8%', align: 'center' },
      { prop: 'lineKindEnum', label: '项目类型', width: '12%', slot: 'lineKind' },
      {
        prop: 'productName',
        label: '项目名称',
        width: '28%',
        showOverflowTooltip: true,
        slot: 'product',
      },
    ];
    const actionCol: TableColumn = {
      label: '操作',
      width: '8%',
      slot: 'actions',
      align: 'center',
    };

    const profile = activeLineColumnProfile.value;
    if (profile === 'SERVICE') {
      return [
        ...baseCols,
        { prop: 'defaultQuantity', label: '数量', width: '20%', slot: 'serviceQuantity' },
        { prop: 'defaultDays', label: '执行次数', width: '18%', slot: 'serviceExecutionCount' },
        actionCol,
      ];
    }
    if (profile === 'DEVICE') {
      return [
        ...baseCols,
        { prop: 'defaultQuantity', label: '数量', width: '38%', slot: 'deviceQuantity' },
        actionCol,
      ];
    }

    return [
      { type: 'selection', width: '4%', align: 'center' },
      { type: 'index', label: '序号', width: '6%', align: 'center' },
      { prop: 'lineKindEnum', label: '项目类型', width: '10%', slot: 'lineKind' },
      {
        prop: 'productName',
        label: '项目名称',
        width: '18%',
        showOverflowTooltip: true,
        slot: 'product',
      },
      { prop: 'dosageText', label: '单次剂量', width: '16%', slot: 'dose' },
      { prop: 'routeCode', label: '给药途径', width: '12%', slot: 'routeCode' },
      { prop: 'frequencyId', label: '频次', width: '10%', slot: 'frequencyId' },
      { prop: 'defaultDays', label: '用药天数', width: '10%', slot: 'defaultDays' },
      { prop: 'defaultQuantity', label: '总量', width: '8%', slot: 'totalAmount' },
      { label: '操作', width: '6%', slot: 'actions', align: 'center' },
    ];
  });

  /** 是否药品行（展示完整用药字段） */
  function isMedicationLineRow(row: DraftLine): boolean {
    return row.lineKindEnum === ORDER_TEMPLATE_LINE_KIND_MEDICATION;
  }

  /** 解析表格 slot 行到 draftLines 源对象 */
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

  /** 新增空白行（可沿用上一行用法/天数；不自动写入 groupKey，须手动并组） */
  function handleAddLine() {
    if (isServiceApplyKind.value) {
      draftLines.value.push({
        _clientKey: nextLineClientKey(),
        lineKindEnum: ORDER_TEMPLATE_LINE_KIND_SERVICE,
        groupKey: options.applyCategoryCode?.() || undefined,
        sortOrder: visibleLines.value.length + 1,
        defaultQuantity: 1,
        defaultDays: 1,
      });
      return;
    }
    if (isTcmKind.value) {
      draftLines.value.push({
        _clientKey: nextLineClientKey(),
        lineKindEnum: ORDER_TEMPLATE_LINE_KIND_MEDICATION,
        sortOrder: visibleLines.value.length + 1,
        _doseSubUnit: 'g',
      });
      return;
    }
    const lastLine = visibleLines.value[visibleLines.value.length - 1];
    const newLine: DraftLine = {
      _clientKey: nextLineClientKey(),
      lineKindEnum: isPricingKind.value
        ? ORDER_TEMPLATE_LINE_KIND_SERVICE
        : ORDER_TEMPLATE_LINE_KIND_MEDICATION,
      sortOrder: visibleLines.value.length + 1,
      defaultQuantity: 1,
    };
    // 划价组套不展示执行次数，诊疗行落库时补 1，避免保存被次数校验拦住
    if (isPricingKind.value) {
      newLine.defaultDays = 1;
    }
    if (lastLine && isMedicationLineRow(lastLine) && !isPricingKind.value) {
      const groupKey = getLineGroupKey(lastLine);
      if (groupKey) {
        newLine.routeCode = lastLine.routeCode;
        newLine.defaultDays = lastLine.defaultDays;
        newLine.frequencyId = lastLine.frequencyId;
        newLine.frequencyText = lastLine.frequencyText;
      }
    }
    draftLines.value.push(newLine);
  }

  /** 标记删除行 */
  function handleRemoveLine(row: DraftLine) {
    const line = resolveDraftLine(row);
    if (line.lineId) {
      line._deleted = true;
      return;
    }
    draftLines.value = draftLines.value.filter((item) => item._clientKey !== line._clientKey);
  }

  /** 切换行类型时清空目录引用与行内扩展字段 */
  function handleLineKindChange(row: DraftLine) {
    const line = resolveDraftLine(row);
    line.medicationDefinitionId = undefined;
    line.activityDefinitionId = undefined;
    line.deviceDefinitionId = undefined;
    line._productLabel = '';
    line.productName = undefined;
    line._unitBands = undefined;
    line.defaultUnitCode = undefined;
    clearLineDoseFields(line);
    line.routeCode = undefined;
    line.frequencyId = undefined;
    line.frequencyText = undefined;
    line.defaultDays = undefined;
    line.defaultPerformOrgId = undefined;
    line.defaultPerformOrgName = undefined;
    line.groupKey = undefined;
    switch (line.lineKindEnum) {
      case ORDER_TEMPLATE_LINE_KIND_DEVICE: {
        line.defaultQuantity = 1;

        break;
      }
      case ORDER_TEMPLATE_LINE_KIND_MEDICATION: {
        line.defaultQuantity = line.defaultQuantity ?? 1;
        line.defaultDays = line.defaultDays ?? 1;

        break;
      }
      case ORDER_TEMPLATE_LINE_KIND_SERVICE: {
        line.defaultQuantity = 1;
        line.defaultDays = 1;

        break;
      }
      // No default
    }
  }

  /** 项目类型展示文案 */
  function formatLineKindText(row: DraftLine): string {
    const fromApi = String(row.lineKindEnumText ?? '').trim();
    if (fromApi) {
      return formatLineKindOptionLabel(fromApi);
    }
    const code = String(row.lineKindEnum ?? '').trim();
    if (!code) {
      return '';
    }
    const label = lineKindOptions.value.find((item) => item.value === code)?.label ?? code;
    return formatLineKindOptionLabel(label) || label;
  }

  /** 目录项映射为 SelectTable 行（保留接口展示字段） */
  function mapCatalogItemToSelectRow(
    item: Record<string, unknown>,
    lineKindEnum: string | undefined,
  ): CatalogSelectRow | null {
    const value =
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
    return {
      ...item,
      value,
      label,
      busNo: item.busNo !== undefined && item.busNo !== null ? String(item.busNo) : undefined,
      specification:
        item.specification !== undefined && item.specification !== null
          ? String(item.specification)
          : undefined,
      categoryCode_dictText:
        item.categoryCode_dictText !== undefined && item.categoryCode_dictText !== null
          ? String(item.categoryCode_dictText)
          : undefined,
      retailPrice:
        item.retailPrice !== undefined && item.retailPrice !== null
          ? (item.retailPrice as number | string)
          : undefined,
      size: item.size !== undefined && item.size !== null ? String(item.size) : undefined,
      unitCode_dictText:
        item.unitCode_dictText !== undefined && item.unitCode_dictText !== null
          ? String(item.unitCode_dictText)
          : undefined,
      catalogUnitDisplay:
        (isPricingKind.value
          ? resolvePricingCatalogUnitDisplayText(item, { preferInhospital: true })
          : resolvePricingCatalogUnitDisplayText(item)) || undefined,
    };
  }

  /** 按行类型分页检索目录项 */
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
        const res = await pageMedicationCatalogApi({
          ...requestParams,
          ...(isTcmKind.value ? { categoryCode: '4' } : {}),
        });
        const parsed = parsePagedRowsFromApiData<Record<string, unknown>>(res?.data);
        const content = parsed.content
          .map((item) => mapCatalogItemToSelectRow(item, line.lineKindEnum))
          .filter((item): item is CatalogSelectRow => !!item);
        return { content, totalCount: parsed.totalCount };
      }
      if (line.lineKindEnum === ORDER_TEMPLATE_LINE_KIND_SERVICE) {
        const applyCategory = isServiceApplyKind.value
          ? String(options.applyCategoryCode?.() ?? '').trim()
          : '';
        const catalogCategoryCode = resolveActivityCategoryCode(applyCategory);
        // 申请单组套须先选定检查/检验/治疗，再按目录类别码检索
        if (isServiceApplyKind.value && !catalogCategoryCode) {
          return { content: [], totalCount: 0 };
        }
        // 组套选品同医生站：隐藏已被套餐引用的明细子项
        const res = await pageServiceCatalogApi({
          ...requestParams,
          includeReferencedPackageChildren: false,
          ...(catalogCategoryCode ? { categoryCode: catalogCategoryCode } : {}),
        });
        const parsed = parsePagedRowsFromApiData<Record<string, unknown>>(res?.data);
        const content = parsed.content
          .map((item) => mapCatalogItemToSelectRow(item, line.lineKindEnum))
          .filter((item): item is CatalogSelectRow => !!item);
        return { content, totalCount: parsed.totalCount };
      }
      if (line.lineKindEnum === ORDER_TEMPLATE_LINE_KIND_DEVICE) {
        const res = await pageDeviceCatalogApi(requestParams);
        const parsed = parsePagedRowsFromApiData<Record<string, unknown>>(res?.data);
        const content = parsed.content
          .map((item) => mapCatalogItemToSelectRow(item, line.lineKindEnum))
          .filter((item): item is CatalogSelectRow => !!item);
        return { content, totalCount: parsed.totalCount };
      }
    } catch {
      ElMessage.error('目录项加载失败，请稍后重试');
    }
    return { content: [], totalCount: 0 };
  }

  /** 选中目录项后写入对应 id 字段 */
  function applyProductSelection(
    row: DraftLine,
    productId: string,
    productLabel?: string,
    catalogItem?: Record<string, unknown>,
  ) {
    const line = resolveDraftLine(row);
    line.medicationDefinitionId = undefined;
    line.activityDefinitionId = undefined;
    line.deviceDefinitionId = undefined;
    const id = preserveApiIdForRequest(productId);
    if (!id) {
      line._productLabel = '';
      line.productName = undefined;
      line._unitBands = undefined;
      line.defaultUnitCode = undefined;
      clearLineDoseFields(line);
      return;
    }
    line._productLabel = productLabel ?? line._productLabel ?? line.productName ?? '';
    switch (line.lineKindEnum) {
      case ORDER_TEMPLATE_LINE_KIND_DEVICE: {
        line.deviceDefinitionId = id;
        if (catalogItem) {
          if (isPricingKind.value) {
            applyPricingCatalogSelectionToLine(line, catalogItem, { preferInhospital: true });
          } else {
            applyDeviceCatalogSelectionToLine(line, catalogItem);
          }
        }

        break;
      }
      case ORDER_TEMPLATE_LINE_KIND_MEDICATION: {
        line.medicationDefinitionId = id;
        if (catalogItem) {
          applyMedicationCatalogSelectionToLine(line, catalogItem, options.unitCodeOptions(), {
            hydrateFromDosageText: isTcmKind.value,
          });
          if (isTcmKind.value) {
            const priceRaw = Number(catalogItem.retailPrice);
            if (Number.isFinite(priceRaw)) {
              (line as DraftLine & { _retailPrice?: number })._retailPrice = priceRaw;
            }
          }
        }

        break;
      }
      case ORDER_TEMPLATE_LINE_KIND_SERVICE: {
        line.activityDefinitionId = id;
        if (catalogItem) {
          if (isPricingKind.value) {
            applyPricingCatalogSelectionToLine(line, catalogItem, { preferInhospital: true });
          } else {
            applyServiceCatalogSelectionToLine(line, catalogItem);
          }
        }

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
    applyProductSelection(row, productId, String(item.label ?? ''), item);
    return true;
  }

  /** SelectTable 清空目录项 */
  function handleProductIdChange(row: DraftLine, productId: null | string | string[] | undefined) {
    if (productId === null || productId === undefined || productId === '') {
      applyProductSelection(row, '');
    }
  }

  function handleLineTotalUnitCodeChange(row: DraftLine, unitCode: unknown) {
    handleLineTotalUnitChange(resolveDraftLine(row), unitCode);
  }

  /** 拆零侧单次剂量变更 */
  function handleLineSingleDoseChange(row: DraftLine, value: unknown) {
    const line = resolveDraftLine(row);
    const qty = parseOptionalNumberInput(value);
    line._singleDose = qty;
    if (qty !== undefined && Number.isFinite(qty)) {
      applySplitDoseToDoseSide(line, qty);
    }
  }

  /** 剂量侧单次剂量变更 */
  function handleLineDoseConvertRatioChange(row: DraftLine, value: unknown) {
    const line = resolveDraftLine(row);
    const qty = parseOptionalNumberInput(value);
    line._doseConvertRatio = qty;
    if (qty !== undefined && Number.isFinite(qty)) {
      applyDoseQtyToSplitSide(line, qty);
    }
  }

  function getTotalUnitOptions(row: DraftLine) {
    return getLineTotalUnitOptions(resolveDraftLine(row));
  }

  function getPricingUnitOptions(row: DraftLine) {
    return getLinePricingUnitOptions(resolveDraftLine(row), options.unitCodeOptions());
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
      ElMessage.error(`${label}：请选择项目`);
      return false;
    }
    if (isPricingKind.value && row.lineKindEnum === ORDER_TEMPLATE_LINE_KIND_MEDICATION) {
      ElMessage.error(`${label}：划价组套不允许药品行`);
      return false;
    }
    if (isServiceApplyKind.value) {
      const category = String(options.applyCategoryCode?.() ?? '').trim();
      row.groupKey = category || undefined;
      if (
        category === SERVICE_APPLY_TEMPLATE_CATEGORY_EXAM &&
        decodeServiceApplyDetailIds(row.dosageText).length === 0
      ) {
        ElMessage.error(`${label}：请选择部位`);
        return false;
      }
    }
    if (isServiceLineRow(row)) {
      const qty = Number(row.defaultQuantity);
      if (!Number.isFinite(qty) || qty <= 0) {
        ElMessage.error(`${label}：诊疗数量须大于 0`);
        return false;
      }
      const execCount = Number(row.defaultDays);
      if (!Number.isFinite(execCount) || execCount <= 0) {
        if (isPricingKind.value) {
          row.defaultDays = 1;
        } else {
          ElMessage.error(`${label}：诊疗执行次数须大于 0`);
          return false;
        }
      }
      return true;
    }
    if (isDeviceLineRow(row)) {
      const qty = Number(row.defaultQuantity);
      if (!Number.isFinite(qty) || qty <= 0) {
        ElMessage.error(`${label}：耗材数量须大于 0`);
        return false;
      }
      if (!String(row.defaultUnitCode ?? '').trim()) {
        ElMessage.error(`${label}：请选择或确认耗材单位`);
        return false;
      }
      return true;
    }
    if (isTcmKind.value && isMedicationLineRow(row)) {
      const parsed = parseDosageTextParts(buildDosageText(row) ?? row.dosageText);
      const doseQty = parsed.doseQty ?? Number(row._doseConvertRatio ?? row._singleDose);
      if (!Number.isFinite(Number(doseQty)) || Number(doseQty) <= 0) {
        ElMessage.error(`${label}：请填写药材剂量`);
        return false;
      }
    }
    return true;
  }

  /** 校验全部有效行 */
  function validateAllLines(): boolean {
    if (isServiceApplyKind.value) {
      const category = String(options.applyCategoryCode?.() ?? '').trim();
      const allowed = SERVICE_APPLY_TEMPLATE_CATEGORY_OPTIONS.some(
        (item) => item.value === category,
      );
      if (!allowed) {
        ElMessage.error('请选择申请类型（检查、检验或治疗）');
        return false;
      }
    }
    const activeLines = draftLines.value.filter((line) => !line._deleted);
    if (activeLines.length === 0) {
      ElMessage.warning('请至少添加一条组套明细');
      return false;
    }
    for (const [index, row] of activeLines.entries()) {
      if (!validateLine(row, index)) {
        return false;
      }
    }
    return true;
  }

  /** 组装 IF-05 全量对齐行（OrderTemplateLineEditDto） */
  /** 整单切换申请类型：类别写入每一行，并清掉与新类别不一致的已选项目 */
  function applyServiceApplyCategory(categoryCode: string) {
    const category = categoryCode.trim();
    for (const row of draftLines.value) {
      if (row._deleted) {
        continue;
      }
      if (getLineGroupKey(row) === category) {
        continue;
      }
      row.groupKey = category || undefined;
      row.activityDefinitionId = undefined;
      row._productLabel = '';
      row.productName = undefined;
      row.defaultUnitCode = undefined;
      row.dosageText = undefined;
    }
  }

  /** 申请单行写入样本或部位主键（检查部位必填，检验样本与治疗部位选填） */
  function writeServiceApplyDetailIds(row: DraftLine, ids: null | string[] | undefined) {
    resolveDraftLine(row).dosageText = encodeServiceApplyDetailIds(Array.isArray(ids) ? ids : []);
  }

  function buildSubmitLines(): OrderTemplateLineEditItem[] {
    // 申请单行的 groupKey 是检查/检验/治疗类别，不能按药品成组回写用法
    if (!isServiceApplyKind.value) {
      syncAllGroupSharedFields();
    }
    let visibleOrder = 0;
    return draftLines.value.map((row) => ({
      lineId: preserveApiIdForRequest(row.lineId),
      deleted: row._deleted ?? false,
      lineKindEnum: row.lineKindEnum ?? '',
      medicationDefinitionId: preserveApiIdForRequest(row.medicationDefinitionId),
      activityDefinitionId: preserveApiIdForRequest(row.activityDefinitionId),
      deviceDefinitionId: preserveApiIdForRequest(row.deviceDefinitionId),
      groupKey: getLineGroupKey(row) || undefined,
      sortOrder: row._deleted ? (row.sortOrder ?? 1) : ++visibleOrder,
      defaultQuantity: row.defaultQuantity,
      defaultUnitCode: row.defaultUnitCode,
      defaultDays: row.defaultDays,
      frequencyId: preserveApiIdForRequest(row.frequencyId),
      routeCode: row.routeCode,
      dosageText: isServiceApplyKind.value
        ? encodeServiceApplyDetailIds(decodeServiceApplyDetailIds(row.dosageText))
        : buildDosageText(row),
      defaultPerformOrgId: isServiceLineRow(row)
        ? undefined
        : preserveApiIdForRequest(row.defaultPerformOrgId),
      chineseHerbsDoseQuantity: row.chineseHerbsDoseQuantity,
      sufferingFlag: row.sufferingFlag,
    }));
  }

  /** 从详情接口回填行 */
  async function setLinesFromApi(lines: OrderTemplateLineRowItem[] | undefined) {
    resetDraftLinesState();
    draftLines.value = (lines ?? []).map((line) => toDraftLine(line));
    for (const line of draftLines.value) {
      if (line.lineKindEnum === ORDER_TEMPLATE_LINE_KIND_SERVICE) {
        if (line.defaultQuantity === undefined) {
          line.defaultQuantity = 1;
        }
        if (line.defaultDays === undefined) {
          line.defaultDays = 1;
        }
      }
      if (
        line.lineKindEnum === ORDER_TEMPLATE_LINE_KIND_DEVICE &&
        line.defaultQuantity === undefined
      ) {
        line.defaultQuantity = 1;
      }
    }
    if (isServiceApplyKind.value) {
      draftLines.value.forEach((line, index) => {
        line.sortOrder = index + 1;
      });
    } else {
      reorderDraftLinesByGroup();
      syncAllGroupSharedFields();
    }
    await enrichMedicationLinesUnitBands(draftLines.value, options.unitCodeOptions());
    if (visibleLines.value.length === 0) {
      handleAddLine();
    }
  }

  /** 新建时初始化一行空白明细 */
  function resetLinesForCreate() {
    resetDraftLinesState();
    draftLines.value = [];
    handleAddLine();
  }

  /** 项目名称展示文案 */
  function formatLineProductText(row: DraftLine): string {
    return String(row._productLabel || row.productName || '').trim();
  }

  /** 单次剂量展示文案 */
  function formatLineDoseText(row: DraftLine): string {
    return formatLineDoseDisplayText(resolveDraftLine(row));
  }

  /** 给药途径展示文案 */
  function formatLineRouteText(row: DraftLine): string {
    const source = resolveGroupSharedFieldSource(row);
    const code = String(source.routeCode ?? '').trim();
    if (!code) {
      return '—';
    }
    return options.methodCodeOptions().find((item) => item.value === code)?.label ?? code;
  }

  /** 频次展示文案 */
  function formatLineFrequencyText(row: DraftLine): string {
    const source = resolveGroupSharedFieldSource(row);
    const text = String(source.frequencyText ?? '').trim();
    if (text) {
      return text;
    }
    const id = preserveApiIdForRequest(source.frequencyId);
    if (!id) {
      return '—';
    }
    return options.frequencyOptions().find((item) => item.value === id)?.label ?? id;
  }

  /** 频次变更：回写展示名并同步同组成组首行字段 */
  function handleLineFrequencyChange(row: DraftLine, frequencyId: string | undefined) {
    const line = resolveDraftLine(row);
    const id = String(frequencyId ?? '').trim();
    line.frequencyId = id || undefined;
    line.frequencyText =
      options.frequencyOptions().find((item) => item.value === id)?.label ?? undefined;
    syncGroupSharedFieldsFromLeader(line);
  }

  /** 用药天数展示文案 */
  function formatLineDaysText(row: DraftLine): string {
    const source = resolveGroupSharedFieldSource(row);
    if (source.defaultDays === undefined || source.defaultDays === null) {
      return '—';
    }
    return String(source.defaultDays);
  }

  /** 总量展示文案 */
  function formatLineTotalText(row: DraftLine): string {
    const qty = row.defaultQuantity;
    if (qty === undefined || qty === null || !Number.isFinite(Number(qty))) {
      return '—';
    }
    const unitLabel =
      getLineTotalUnitOptions(row).find((item) => item.value === row.defaultUnitCode)?.label ??
      String(row.defaultUnitCode ?? '').trim();
    return unitLabel ? `${qty}${unitLabel}` : String(qty);
  }

  /** 诊疗数量展示文案 */
  function formatLineServiceQuantityText(row: DraftLine): string {
    const qty = row.defaultQuantity;
    if (qty === undefined || qty === null || !Number.isFinite(Number(qty))) {
      return '—';
    }
    const unitLabel =
      getLinePricingUnitOptions(row, options.unitCodeOptions())[0]?.label ??
      String(row.defaultUnitName ?? row.defaultUnitCode ?? '').trim();
    return unitLabel ? `${qty}${unitLabel}` : String(qty);
  }

  /** 诊疗执行次数展示文案（复用 defaultDays 存储） */
  function formatLineServiceExecutionCountText(row: DraftLine): string {
    if (row.defaultDays === undefined || row.defaultDays === null) {
      return '—';
    }
    return String(row.defaultDays);
  }

  /** 耗材数量展示文案 */
  function formatLineDeviceQuantityText(row: DraftLine): string {
    return formatLineServiceQuantityText(row);
  }

  /** 划价组套数量展示文案 */
  function formatLinePricingQuantityText(row: DraftLine): string {
    const qty = row.defaultQuantity;
    if (qty === undefined || qty === null || !Number.isFinite(Number(qty))) {
      return '—';
    }
    const unitLabel =
      getLinePricingUnitOptions(row, options.unitCodeOptions())[0]?.label ??
      String(row.defaultUnitCode ?? '').trim();
    return unitLabel ? `${qty}${unitLabel}` : String(qty);
  }

  /** 项目名称列成组括线样式（虚线跨行，不合并单元格） */
  function getProductGroupCellClass(row: DraftLine): string {
    const pos = getGroupLinePosition(row);
    if (pos === 'none') {
      return '';
    }
    return `order-template-product-cell--group-${pos}`;
  }

  return {
    draftLines,
    lineKindOptions,
    visibleLines,
    tableColumns,
    activeLineColumnProfile,
    showMedicationColumns,
    isTcmKind,
    isServiceApplyKind,
    canGroupLines,
    lineSelection,
    isMedicationLineRow,
    isServiceLineRow,
    isDeviceLineRow,
    isGroupLeaderRow,
    getGroupLinePosition,
    getProductId,
    handleAddLine,
    handleRemoveLine,
    handleLineKindChange,
    applyServiceApplyCategory,
    writeServiceApplyDetailIds,
    handleLineSelectionChange,
    handleAssignGroup,
    handleUngroup,
    syncGroupSharedFieldsFromLeader,
    searchProductPage,
    handleProductTableClose,
    handleProductIdChange,
    handleLineTotalUnitCodeChange,
    handleLineSingleDoseChange,
    handleLineDoseConvertRatioChange,
    handleLineFrequencyChange,
    getTotalUnitOptions,
    getPricingUnitOptions,
    validateAllLines,
    buildSubmitLines,
    setLinesFromApi,
    resetLinesForCreate,
    formatLineProductText,
    formatLineKindText,
    formatLineDoseText,
    displayMedicationDoseQty,
    formatLineRouteText,
    formatLineFrequencyText,
    formatLineDaysText,
    formatLineTotalText,
    formatLineServiceQuantityText,
    formatLineServiceExecutionCountText,
    formatLineDeviceQuantityText,
    formatLinePricingQuantityText,
    getProductGroupCellClass,
  };
}
