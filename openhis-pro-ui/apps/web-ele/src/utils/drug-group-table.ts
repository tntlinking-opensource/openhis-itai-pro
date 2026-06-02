/**
 * 医嘱/处方表格中「成组药品」行合并与可选窄视图模型。
 *
 * 单元格版式与括线请使用 `components/outpatient/drug-order-group-cell.vue`（插槽内自定字段）；
 * 下方 `DrugGroupDisplayItem` 仅为 `mergeConsecutiveRowsByDrugGroup` 的 toGroupItem 常用形状示例，业务可改用任意 items 元素类型。
 */

/** 合并工具中 toGroupItem 的常用返回形状（护士站演示等可继续沿用；非展示层强制契约） */
export interface DrugGroupDisplayItem {
  selectedDrugName: string;
  selectedDrugSpec: string;
  pharmacy: string;
  singleDose: number | string;
  doseSubUnit: string;
  totalAmount: number | string;
  totalAmountUnit: string;
  usage: string;
  frequency: string;
  medicationDays: number | string;
}

export type DrugGroupKey = string | number | null | undefined;

/**
 * 将列表中「组标识相同且连续」的多条药品行合并为一条，便于表格一行展示并用括线包裹。
 * 组标识为空（null / undefined / ''）的行不参与合并，每条单独成行。
 *
 * @param rows 原始行（例如接口按药品拆行返回）
 * @param options.getGroupKey 从行上读取组号；与医嘱开立界面 groupId / groupNo 等字段对齐即可
 * @param options.toGroupItem 将单行映射为单元格展示用的结构
 * @param options.buildMergedRow 由调用方把「组合并结果」写回自己的行类型（通常保留首行业务字段并挂上 groupItems）
 */
export function mergeConsecutiveRowsByDrugGroup<T>(
  rows: readonly T[],
  options: {
    getGroupKey: (row: T) => DrugGroupKey;
    toGroupItem: (row: T) => DrugGroupDisplayItem;
    buildMergedRow: (params: {
      /** 被合并的原始行切片，长度 ≥ 1 */
      slice: T[];
      /** 与 slice 一一对应的展示项 */
      groupItems: DrugGroupDisplayItem[];
    }) => T;
  },
): T[] {
  const { getGroupKey, toGroupItem, buildMergedRow } = options;
  const out: T[] = [];
  let i = 0;
  while (i < rows.length) {
    const first = rows[i] as T;
    const key = getGroupKey(first);
    const keyEmpty = key === null || key === undefined || key === '';
    if (keyEmpty) {
      out.push(
        buildMergedRow({
          slice: [first],
          groupItems: [toGroupItem(first)],
        }),
      );
      i += 1;
      continue;
    }
    const slice: T[] = [first];
    const groupItems: DrugGroupDisplayItem[] = [toGroupItem(first)];
    i += 1;
    while (i < rows.length) {
      const next = rows[i] as T;
      const nextKey = getGroupKey(next);
      if (nextKey !== key) break;
      slice.push(next);
      groupItems.push(toGroupItem(next));
      i += 1;
    }
    out.push(buildMergedRow({ slice, groupItems }));
  }
  return out;
}
