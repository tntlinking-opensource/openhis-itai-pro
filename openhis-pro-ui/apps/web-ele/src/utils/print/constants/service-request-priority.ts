/**
 * 服务申请优先级 code（对齐 DM-WKF-004 PriorityLevel）。
 * 医生站仅暴露「急」勾选，落库 EM；采血贴按 code===EM 印「急」。
 */
export const SERVICE_REQUEST_PRIORITY_EM = 'EM';

/** 优先级 code → 采血贴展示文案；非急为空串 */
export function resolveSpecimenBarcodeUrgencyText(priorityEnum?: null | string): string {
  return String(priorityEnum ?? '').trim() === SERVICE_REQUEST_PRIORITY_EM ? '急' : '';
}
