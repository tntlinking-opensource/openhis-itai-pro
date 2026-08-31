/**
 * 码上放心前端展示常量。
 */
import type { MsfxUploadStatusEnum } from '#/api/msfx/types';

export const MSFX_HIS_SCENE = {
  WAREHOUSE_PURCHASE_IN: 'WAREHOUSE_PURCHASE_IN',
  SUPPLIER_RETURN: 'SUPPLIER_RETURN',
  OP_DISPENSE: 'OP_DISPENSE',
  IP_DISPENSE: 'IP_DISPENSE',
  OP_RETURN: 'OP_RETURN',
  IP_RETURN: 'IP_RETURN',
} as const;

/** 上传状态 → 中文标签 */
export const MSFX_UPLOAD_STATUS_LABEL: Record<string, string> = {
  PENDING: '待上传',
  QUEUED: '已入队',
  PROCESSING: '处理中',
  SUCCESS: '成功',
  PARTIAL: '部分成功',
  FAILED: '失败',
  SKIPPED: '已跳过',
};

/** 尝试类型 → 中文 */
export const MSFX_ATTEMPT_TYPE_LABEL: Record<string, string> = {
  UPLOAD: '上传',
  BILL_STATUS: '查处理状态',
  PART_FAIL_DETAIL: '查部分失败码',
};

/** HIS 场景 → 中文 */
export const MSFX_HIS_SCENE_LABEL: Record<string, string> = {
  WAREHOUSE_PURCHASE_IN: '药库进货',
  SUPPLIER_RETURN: '退供应商',
  OP_DISPENSE: '门诊发药',
  IP_DISPENSE: '住院发药',
  OP_RETURN: '门诊退药',
  IP_RETURN: '住院退药',
};

/** bill_type → 中文（运维筛选/展示） */
export const MSFX_BILL_TYPE_LABEL: Record<string, string> = {
  '102': '102 采购入库',
  '202': '202 退供应商',
  '321': '321 发药出库',
  '116': '116 退药入库',
};

/** 上传状态 → Element Plus Tag type */
export const MSFX_UPLOAD_STATUS_TAG: Record<string, 'info' | 'success' | 'warning' | 'danger'> = {
  PENDING: 'info',
  QUEUED: 'warning',
  PROCESSING: 'warning',
  SUCCESS: 'success',
  PARTIAL: 'warning',
  FAILED: 'danger',
  SKIPPED: 'info',
};

export function msfxUploadStatusLabel(status?: string | null): string {
  if (!status) return '未上传';
  return MSFX_UPLOAD_STATUS_LABEL[status] ?? status;
}

export function msfxAttemptTypeLabel(type?: string | null): string {
  if (!type) return '—';
  return MSFX_ATTEMPT_TYPE_LABEL[type] ?? type;
}

export function msfxHisSceneLabel(scene?: string | null): string {
  if (!scene) return '—';
  return MSFX_HIS_SCENE_LABEL[scene] ?? scene;
}

export function msfxBillTypeLabel(billType?: string | null): string {
  if (!billType) return '—';
  return MSFX_BILL_TYPE_LABEL[billType] ?? billType;
}

export function msfxUploadStatusTagType(
  status?: string | null,
): 'info' | 'success' | 'warning' | 'danger' {
  if (!status) return 'info';
  return MSFX_UPLOAD_STATUS_TAG[status] ?? 'info';
}

/** 可手工上传的状态（含无账） */
export function canManualMsfxUpload(status?: MsfxUploadStatusEnum | string | null): boolean {
  if (!status) return true;
  return status === 'PENDING' || status === 'FAILED' || status === 'PARTIAL';
}

export function canRefreshMsfxStatus(status?: MsfxUploadStatusEnum | string | null): boolean {
  return status === 'QUEUED' || status === 'PROCESSING' || status === 'PARTIAL';
}

export function canRetryMsfxUpload(status?: MsfxUploadStatusEnum | string | null): boolean {
  return status === 'FAILED' || status === 'PARTIAL';
}
