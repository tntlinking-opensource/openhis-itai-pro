import type { PrintElementDefDto } from '#/api/report/report-design';
import type { LayoutItem } from '#/types/report-layout';

import { isPositionedRenderType } from '#/utils/report/overlay-layout';

/** 热敏 flow 模板中按 box 绝对定位的元素（qrcode 等），不参与 flow 排序 */
export function isAbsoluteLayoutItem(item: LayoutItem, def?: PrintElementDefDto | null): boolean {
  if (isPositionedRenderType(def?.renderType)) {
    return true;
  }
  return /qrcode|barcode|image/i.test(item.elementCode);
}

export function filterAbsoluteLayoutItems(
  items: LayoutItem[],
  elementMap: Map<string, PrintElementDefDto>,
): LayoutItem[] {
  return items.filter((item) => {
    if (item.visible === false) {
      return false;
    }
    return isAbsoluteLayoutItem(item, elementMap.get(item.elementCode));
  });
}
