import type { AdvicePackageChildItemDto } from '#/api/doctor-station/advice.types';

import { EXPAND_ACTIVITY_DEFINITION_CHILDREN_ON_BUSINESS_PRINT } from '#/utils/print/activity-definition-children-print.config';
import { SERVICE_CATEGORY_PROOF } from '#/utils/print/constants/service-request-category-codes';

/**
 * 当前院点是否开启「检验带子项」业务打印展开。
 *
 * @return 国基 override 为 true；产品默认 false
 */
export function shouldExpandActivityDefinitionChildrenOnBusinessPrint(): boolean {
  return EXPAND_ACTIVITY_DEFINITION_CHILDREN_ON_BUSINESS_PRINT === true;
}

/**
 * 是否为检验类目（code 22 / 枚举名 PROOF）。检查、治疗等返回 false。
 *
 * @param categoryCode 诊疗类目 code 或枚举名
 * @return 检验为 true
 */
export function isLabServiceCategoryForPrint(categoryCode?: null | string): boolean {
  const trimmed = String(categoryCode ?? '').trim();
  if (!trimmed) {
    return false;
  }
  if (trimmed === SERVICE_CATEGORY_PROOF) {
    return true;
  }
  return trimmed.toUpperCase() === 'PROOF';
}

/**
 * 取出可打印的目录子项（须有展示名）。
 *
 * @param children 医嘱/执行行上的 packageChildren
 * @return 有名称的子项；无则空数组
 */
export function listPrintableActivityDefinitionChildren(
  children?: AdvicePackageChildItemDto[] | null,
): AdvicePackageChildItemDto[] {
  if (!children?.length) {
    return [];
  }
  const result: AdvicePackageChildItemDto[] = [];
  for (const child of children) {
    const name = String(child?.childrenName ?? '').trim();
    if (!name) {
      continue;
    }
    result.push(child);
  }
  return result;
}

/**
 * 检验带子项在业务打印中拆成多行：多子项时每子项一行；单子项仍打父项
 * （避免「血清C肽测定（餐后2小时）」被替换成「血清C肽测定(化学发光法)」）。
 *
 * 院点开关关闭、非检验行、或无子项时原样返回（检查/治疗仍打父项）。
 *
 * @param items 父项打印行
 * @param getChildren 从父行取目录子项
 * @param replaceWithChild 用子项覆盖名称等字段，得到打印行
 * @param shouldExpandItem 为 false 时本行不展开（检查/治疗）
 * @return 展开后的打印行
 */
export function expandActivityDefinitionChildrenForPrint<T>(
  items: T[],
  getChildren: (item: T) => AdvicePackageChildItemDto[] | null | undefined,
  replaceWithChild: (item: T, child: AdvicePackageChildItemDto, childIndex: number) => T,
  shouldExpandItem?: (item: T) => boolean,
): T[] {
  if (!shouldExpandActivityDefinitionChildrenOnBusinessPrint()) {
    return items;
  }
  if (!items.length) {
    return items;
  }
  const expanded: T[] = [];
  for (const item of items) {
    if (shouldExpandItem && !shouldExpandItem(item)) {
      expanded.push(item);
      continue;
    }
    const children = listPrintableActivityDefinitionChildren(getChildren(item));
    // 0/1 个子项：保留父项开立名（单子项多为时点别名挂同一收费方法）
    if (children.length <= 1) {
      expanded.push(item);
      continue;
    }
    let childIndex = 0;
    for (const child of children) {
      expanded.push(replaceWithChild(item, child, childIndex));
      childIndex += 1;
    }
  }
  return expanded;
}

/**
 * 打印数量：父数量 × children_num（系数缺省按 1）。
 *
 * @param parentQuantity 父项数量
 * @param childrenNum 子项系数
 * @return 展开后数量
 */
export function multiplyParentQuantityByChildrenNum(
  parentQuantity: number,
  childrenNum?: number | null,
): number {
  let factor = 1;
  if (childrenNum !== null && childrenNum !== undefined && childrenNum > 0) {
    factor = childrenNum;
  }
  return parentQuantity * factor;
}
