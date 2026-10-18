import type { ServiceApplyKind } from '#/views/outpatient/doctor-station/service-apply/service-apply.types';

/** 申请单项目区一行：名称与部位/标本成对，按项目纵向排列 */
export interface ServiceApplyPrintItemLine {
  itemName: string;
  /** 检查部位 / 治疗部位 / 标本类型 */
  detailLabel: string;
  detailText: string;
}

/** 部位或标本栏目标题（检查、治疗、检验申请单共用项目区） */
export function resolveServiceApplyDetailLabel(applyKind: ServiceApplyKind): string {
  if (applyKind === 'lab') {
    return '标本类型';
  }
  if (applyKind === 'treatment') {
    return '治疗部位';
  }
  return '检查部位';
}

/** 单条项目；名称与部位都空则跳过 */
export function toServiceApplyItemLine(
  applyKind: ServiceApplyKind,
  itemName: string,
  detailText: string,
): null | ServiceApplyPrintItemLine {
  const name = itemName.trim();
  const detail = detailText.trim();
  if (!name && !detail) {
    return null;
  }
  return {
    itemName: name,
    detailLabel: resolveServiceApplyDetailLabel(applyKind),
    detailText: detail,
  };
}
