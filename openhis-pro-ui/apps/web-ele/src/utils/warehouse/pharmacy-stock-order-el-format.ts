/**
 * 药库 ElTable / VXE 明细（采购退货等）共用的日期展示。
 */
import { formatDate, formatDateTime } from '@vben/utils';

export function formatPharmacyStockAuditTime(v: unknown): string {
  if (v === undefined || v === null || v === '') return '';
  return formatDateTime(v as Date | number | string);
}

export function formatPharmacyStockYmd(v: unknown): string {
  if (v === undefined || v === null || v === '') return '';
  return formatDate(v as Date | number | string, 'YYYY-MM-DD');
}
