/**
 * 药品多行列表的可选抽离实现；当前开立页编辑卡为内联逻辑，本 composable 保留给后续合并或复用。
 */
import type { AdviceDrugItem, AdviceEditForm, AdviceProductOption } from '../services/advice.types';

import { ref, watch } from 'vue';

import { ElMessage } from 'element-plus';

import { useFormKeyboardNavigation } from '#/hooks/use-form-keyboard-navigation';

import {
  defaultTotalAmountUnitCode,
  doseConvertRatioSplitToDose,
  resolveAdviceLineUnitPrice,
  totalAmountSplitToSalesFactor,
  unitCodeByBandKind,
  unitNameByBandKind,
} from '../services/medication-unit-bands';
import { preferredTotalUnitBandKindFromOrderCalcRule } from '../services/medication-order-total-calc';

type ProductOption = AdviceProductOption;

import { calcMoneyLineTotal, formatMoneyDisplay } from '#/utils/format/money-scale';

import { inferAdviceLineEditorKind } from '../services/advice-row';
import { resolveServicePreviewBillQuantity } from '../services/service-quantity-calc';

/** 单价 × 计价量草稿预览（诊疗=单次×次数；药/材=总量）；入账以 ChargeItem 为准。 */
export function calcAdviceTotalFee(item: AdviceDrugItem) {
  const price = Number(item.selectedDrugPrice || 0);
  const kind = inferAdviceLineEditorKind(item);
  const amount =
    kind === 'SERVICE'
      ? resolveServicePreviewBillQuantity(item.serviceQuantity, item.totalAmount)
      : Number(item.totalAmount || 0);
  if (price <= 0 || amount <= 0) {
    return '0.00';
  }
  return formatMoneyDisplay(calcMoneyLineTotal(price, amount));
}

export interface UseAdviceDrugListParams {
  /** 与卡片 props 同步：初始模板行 */
  modelValue: () => AdviceEditForm;
  initialGroup: () => AdviceDrugItem[] | undefined;
  isTcm: () => boolean;
  validateRow: (item: AdviceDrugItem) => boolean;
  /** 返回 `true` 允许继续；返回 `string` 时提示并阻止「添加药品」 */
  addDrugGuard?: (ctx: { drugItems: AdviceDrugItem[]; isTcm: boolean }) => boolean | string;
  onEditorDirtyChange?: (dirty: boolean) => void;
}

/** 从规格文案解析 “12g” 类片段，作为中药默认单次量与单位。 */
function tcmDosePatchFromSpec(spec: string): Pick<AdviceDrugItem, 'doseSubUnit' | 'singleDose'> {
  const m = spec.match(/(\d+(\.\d+)?)g/i);
  if (!m) return { singleDose: 0, doseSubUnit: '' };
  const n = Number(m[1]);
  return { singleDose: Number.isFinite(n) ? n : 0, doseSubUnit: 'g' };
}

/**
 * 西药：目录 `unitBands` + 默认用法/频次 → 回填剂量侧与总量单位相关字段。
 * 纯函数，便于与编辑卡逻辑对照、单测。
 */
function westernPatchFromCatalogRow(row: ProductOption): Partial<AdviceDrugItem> {
  const bands = row.unitBands;
  const splitName = unitNameByBandKind(bands, 'SPLIT');
  const doseName = unitNameByBandKind(bands, 'DOSE');
  const ratio = doseConvertRatioSplitToDose(bands);
  const splitCode = unitCodeByBandKind(bands, 'SPLIT');
  const salesCode = unitCodeByBandKind(bands, 'SALES');
  const salesName = unitNameByBandKind(bands, 'SALES');
  const packageCode = unitCodeByBandKind(bands, 'PACKAGE');
  const packageName = unitNameByBandKind(bands, 'PACKAGE');
  const totalSk = totalAmountSplitToSalesFactor(bands);

  const patch: Partial<AdviceDrugItem> = {};
  const mc = row.methodCode?.trim();
  const rc = row.rateCode?.trim();
  if (mc) patch.usage = mc;
  if (rc) patch.frequency = rc;
  patch.injectFlag = row.injectFlag === true;
  const categoryCode = String(row.categoryCode ?? '').trim();
  if (categoryCode) {
    patch.catalogCategoryCode = categoryCode;
  }
  if (splitName) patch.doseMainUnit = splitName;
  if (doseName) patch.doseSubUnit = doseName;
  if (ratio !== undefined && Number.isFinite(ratio)) patch.doseConvertRatio = ratio;
  patch.unitBands = bands;
  if (row.catalogUnitPrice !== undefined && Number.isFinite(row.catalogUnitPrice)) {
    patch.catalogUnitPrice = row.catalogUnitPrice;
  }
  patch.totalUnitSplitCode = splitCode;
  patch.totalUnitSplitName = splitName;
  patch.totalUnitSalesCode = salesCode;
  patch.totalUnitSalesName = salesName;
  patch.totalUnitPackageCode = packageCode;
  patch.totalUnitPackageName = packageName;
  patch.totalAmountSplitToSalesK =
    totalSk !== undefined && Number.isFinite(totalSk) && totalSk > 0 ? totalSk : undefined;
  const partAttr = String(row.partAttributeEnum ?? '').trim();
  if (partAttr) patch.partAttributeEnum = partAttr;
  const totalUnit = defaultTotalAmountUnitCode(
    bands,
    preferredTotalUnitBandKindFromOrderCalcRule(partAttr),
  );
  patch.totalAmountUnit = totalUnit;
  const linePrice = resolveAdviceLineUnitPrice({
    bands,
    catalogUnitPrice: patch.catalogUnitPrice ?? row.catalogUnitPrice ?? row.price,
    totalAmountUnit: totalUnit,
  });
  if (linePrice !== undefined) {
    patch.selectedDrugPrice = linePrice;
  }
  // 医保等级：目录带出，仅展示
  const lvRaw = row.chrgitmLv;
  if (lvRaw !== null && lvRaw !== undefined && String(lvRaw).trim() !== '') {
    patch.chrgitmLv = lvRaw;
  } else {
    patch.chrgitmLv = undefined;
  }
  const lvText = String(row.chrgitmLvText ?? '').trim();
  patch.chrgitmLvText = lvText || undefined;
  // 库存：目录带出，供药品信息行展示
  const qtyRaw = row.availableQuantity;
  patch.availableQuantity =
    qtyRaw !== null && qtyRaw !== undefined && String(qtyRaw).trim() !== '' ? qtyRaw : undefined;
  const stockText = String(row.availableQuantityText ?? '').trim();
  patch.availableQuantityText = stockText || undefined;
  patch.inventoryList = Array.isArray(row.inventoryList) ? row.inventoryList : undefined;
  return patch;
}

/** 仅切换「当前展开编辑」的行索引，其它行 `expanded: false`。 */
function withExpandedIndex(items: AdviceDrugItem[], index: number): AdviceDrugItem[] {
  return items.map((item, i) => ({ ...item, expanded: i === index }));
}

/** 从上一行复制「成组默认」字段，新开空白药品行（不继承诊断，避免误绑）。 */
function seedFromLastRow(last: AdviceDrugItem): Partial<AdviceDrugItem> {
  return {
    diagnosis: '',
    encounterDiagnosisIds: [],
    contractId: last.contractId,
    feeNature: lastItemField(last, 'feeNature'),
    frequency: last.frequency,
    medicationDays: last.medicationDays,
    orderType: lastItemField(last, 'orderType'),
    pharmacy: lastItemField(last, 'pharmacy'),
    skinTest: last.skinTest,
    usage: last.usage,
    groupId: String(last.groupId ?? '').trim() || undefined,
  };
}

function lastItemField(last: AdviceDrugItem, key: 'feeNature' | 'orderType' | 'pharmacy'): string {
  return String(last[key] ?? '').trim();
}

/** 多行药品 ref、选药回填、增删改与保存前校验（与编辑卡行为对齐的独立版本）。 */
export function useAdviceDrugList(params: UseAdviceDrugListParams) {
  /** 种子里的数字字段： composable 版用 0 作缺省（与编辑卡 undefined 策略不同，历史行为保留）。 */
  const coalesceNum = (v: unknown, fallback: number) => {
    const x = Number(v);
    return Number.isFinite(x) ? x : fallback;
  };

  /** 新建一行：清空药品展示字段，剂量/频次等来自 seed 或默认 0/空串。 */
  const createDrugItem = (seed?: Partial<AdviceDrugItem>): AdviceDrugItem => ({
    orderType: String(seed?.orderType ?? '').trim(),
    selectedId: seed?.selectedId || '',
    diagnosis: seed?.diagnosis || '',
    feeNature: String(seed?.feeNature ?? '').trim(),
    skinTest: seed?.skinTest || false,
    pharmacy: String(seed?.pharmacy ?? '').trim(),
    singleDose: coalesceNum(seed?.singleDose, 0),
    frequency: seed?.frequency ?? '',
    usage: seed?.usage ?? '',
    medicationDays: coalesceNum(seed?.medicationDays, 0),
    totalAmount: coalesceNum(seed?.totalAmount, 0),
    selectedDrugName: '',
    selectedDrugSpec: '',
    selectedDrugPrice: '',
    doseMainUnit: '',
    doseSubUnit: '',
    doseConvertRatio: 0,
    totalAmountUnit: '',
    expanded: true,
    groupId: String(seed?.groupId ?? '').trim() || undefined,
  });

  /** `initialGroup` 有值时用父传入组；否则用 `modelValue` 模板造一行。 */
  const getInitialDrugItems = (): AdviceDrugItem[] => {
    const group = params.initialGroup();
    if (group?.length) {
      return group.map((item, index) => ({
        ...item,
        expanded: index === 0,
      }));
    }
    const mv = params.modelValue();
    if (!params.isTcm()) {
      return [createDrugItem({ ...mv, groupId: String(Date.now()) })];
    }
    return [createDrugItem(mv)];
  };

  const drugItems = ref<AdviceDrugItem[]>(getInitialDrugItems());

  /** 合并更新单行（一次不可变替换，避免连续多次 `setField`）。 */
  const patchRow = (index: number, patch: Partial<AdviceDrugItem>) => {
    const next = [...drugItems.value];
    const cur = next[index];
    if (!cur) return;
    next[index] = { ...cur, ...patch };
    drugItems.value = next;
  };

  const setField = <K extends keyof AdviceDrugItem>(
    index: number,
    key: K,
    value: AdviceDrugItem[K],
  ) => {
    patchRow(index, { [key]: value } as Partial<AdviceDrugItem>);
  };

  /** 选目录行：写名称/规格/价 + 中西药分支字段，并只展开当前行。 */
  const handleProductSelect = (index: number, row: ProductOption) => {
    const base: Partial<AdviceDrugItem> = {
      selectedDrugName: row.name || '',
      selectedDrugSpec: row.spec || '',
      selectedDrugPrice: row.price ?? '',
    };
    if (params.isTcm()) {
      patchRow(index, {
        ...base,
        ...tcmDosePatchFromSpec(row.spec),
        totalAmount: 0,
      });
    } else {
      patchRow(index, {
        ...base,
        ...westernPatchFromCatalogRow(row),
      });
    }
    drugItems.value = withExpandedIndex(drugItems.value, index);
  };

  const formAreaRef = ref<HTMLElement | null>(null);
  const { handleFormKeydown } = useFormKeyboardNavigation(formAreaRef);

  /** 焦点进某药品块时，把键盘导航根节点挂到该块（与 `useFormKeyboardNavigation` 配合）。 */
  const setFormScope = (event: FocusEvent) => {
    const target = event.currentTarget;
    if (target instanceof HTMLElement) {
      formAreaRef.value = target;
    }
  };

  /** 切换展开行：若当前另有展开行且未通过校验，则拦截并提示。 */
  const handleEditDrug = (index: number) => {
    if (!drugItems.value[index]) return;
    const editingIdx = drugItems.value.findIndex((item) => item.expanded);
    if (editingIdx !== -1 && editingIdx !== index) {
      const editingItem = drugItems.value[editingIdx];
      if (editingItem && !params.validateRow(editingItem)) {
        ElMessage.warning(`请先完善第${editingIdx + 1}条药品的必填项`);
        return;
      }
    }
    drugItems.value = withExpandedIndex(drugItems.value, index);
  };

  /** 至少保留一行；删掉的是展开行时，默认展开第一行。 */
  const handleDeleteDrug = (index: number) => {
    if (drugItems.value.length <= 1) {
      ElMessage.warning('至少保留一条药品记录');
      return;
    }
    const wasExpanded = drugItems.value[index]?.expanded ?? false;
    let next = drugItems.value.filter((_, i) => i !== index);
    if (wasExpanded && next.length > 0) {
      next = withExpandedIndex(next, 0);
    }
    drugItems.value = next;
  };

  /** 追加行：`addDrugGuard` → 校验末行 → 末行收起 + 新行（继承诊断/用法等）。 */
  const handleAddDrug = () => {
    const lastItem = drugItems.value[drugItems.value.length - 1];
    if (!lastItem) return;

    const guard = params.addDrugGuard?.({
      drugItems: drugItems.value,
      isTcm: params.isTcm(),
    });
    if (guard !== undefined && guard !== true) {
      const message = typeof guard === 'string' ? guard : '';
      if (message) ElMessage.warning(message);
      return;
    }

    if (!params.validateRow(lastItem)) {
      ElMessage.warning('请先完善上一条药品的必填项后再添加');
      return;
    }

    const sessionGroupId = params.isTcm()
      ? undefined
      : String(lastItem.groupId ?? '').trim() || String(Date.now());
    const nextItems = [...drugItems.value];
    nextItems[nextItems.length - 1] = {
      ...lastItem,
      expanded: false,
      ...(sessionGroupId ? { groupId: sessionGroupId } : {}),
    };
    nextItems.push({
      ...createDrugItem({
        ...seedFromLastRow(lastItem),
        ...(sessionGroupId ? { groupId: sessionGroupId } : {}),
      }),
      expanded: false,
    });
    drugItems.value = nextItems;
  };

  /** 全行校验通过后，把当前列表交给调用方（通常 emit 保存）。 */
  const handleSaveGroup = (emitSave: (items: AdviceDrugItem[]) => void) => {
    const invalidIndex = drugItems.value.findIndex((item) => !params.validateRow(item));
    if (invalidIndex !== -1) {
      ElMessage.warning(`第${invalidIndex + 1}条药品必填项未完成`);
      return;
    }
    emitSave(drugItems.value);
  };

  watch(
    () => params.initialGroup(),
    () => {
      drugItems.value = getInitialDrugItems();
    },
    { deep: true },
  );

  watch(
    () => drugItems.value,
    (value) => {
      params.onEditorDirtyChange?.(value.some((item) => Boolean(item.selectedId)));
    },
    { deep: true, immediate: true },
  );

  return {
    drugItems,
    setField,
    handleProductSelect,
    formAreaRef,
    handleFormKeydown,
    setFormScope,
    handleEditDrug,
    handleDeleteDrug,
    handleAddDrug,
    handleSaveGroup,
    calcTotalFee: calcAdviceTotalFee,
  };
}
