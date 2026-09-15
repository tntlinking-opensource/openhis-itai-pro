/** Opensource stub — UI constants only (no network). */
import type { MsfxUploadStatusEnum } from '#/api/msfx/types';

export const MSFX_HIS_SCENE = {
  WAREHOUSE_PURCHASE_IN: 'WAREHOUSE_PURCHASE_IN',
  SUPPLIER_RETURN: 'SUPPLIER_RETURN',
  OP_DISPENSE: 'OP_DISPENSE',
  IP_DISPENSE: 'IP_DISPENSE',
  OP_RETURN: 'OP_RETURN',
  IP_RETURN: 'IP_RETURN',
} as const;

export function msfxUploadStatusLabel(status?: string | null): string {
  return status ? String(status) : '未上传';
}

export function msfxAttemptTypeLabel(type?: string | null): string {
  return type ? String(type) : '—';
}

export function msfxHisSceneLabel(scene?: string | null): string {
  return scene ? String(scene) : '—';
}

export function msfxBillTypeLabel(billType?: string | null): string {
  return billType ? String(billType) : '—';
}

export function msfxUploadStatusTagType(
  _status?: string | null,
): 'info' | 'success' | 'warning' | 'danger' {
  return 'info';
}

export function canManualMsfxUpload(_status?: MsfxUploadStatusEnum | string | null): boolean {
  return false;
}

export function canRefreshMsfxStatus(_status?: MsfxUploadStatusEnum | string | null): boolean {
  return false;
}

export function canRetryMsfxUpload(_status?: MsfxUploadStatusEnum | string | null): boolean {
  return false;
}
