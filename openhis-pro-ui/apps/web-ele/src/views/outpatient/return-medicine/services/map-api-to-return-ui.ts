import type {
  ReturnMedicineDetailRowDto,
  ReturnPatientRowDto,
} from '#/api/outpatient-pharmacy-return-medicine/outpatient-pharmacy-return-medicine.types';

import { formatDateTime } from '@vben/utils';

import type { ReturnMedicineDetailRow, ReturnMedicineQueuePatient } from '../components/types';

function scalarToTrimmedString(value: null | number | string | undefined): string {
  if (value === null || value === undefined) return '';
  return String(value).trim();
}

/** 队列行 DTO → 左侧患者卡片模型 */
export function mapReturnPatientRowToQueuePatient(
  row: ReturnPatientRowDto,
): ReturnMedicineQueuePatient {
  const reception = row.receptionTime ? formatDateTime(row.receptionTime) : '';
  return {
    id: String(row.encounterId ?? '').trim(),
    patientName: row.patientName?.trim() || '—',
    genderEnumText: row.genderEnumText?.trim() || '—',
    ageLabel: row.age?.trim() || '—',
    visitNo: row.encounterNo?.trim() || '—',
    visitTimeLabel: reception || '—',
    departmentName: row.departmentName?.trim() || '—',
    refundEnum: row.refundEnum?.trim() || '',
    refundEnumText: row.refundEnumText?.trim() || '—',
  };
}

/** 明细 DTO → 表格行（仅 PENDING_REFUND 可勾选） */
export function mapReturnDetailRowToUi(row: ReturnMedicineDetailRowDto): ReturnMedicineDetailRow {
  const refundEnum = row.refundEnum?.trim() || '';
  const q = scalarToTrimmedString(row.quantity);
  const dispenseId = String(row.dispenseId ?? '').trim();
  const requestId = String(row.requestId ?? '').trim();
  return {
    id: dispenseId,
    dispenseId,
    requestId,
    itemName: row.itemName?.trim() || '—',
    lotNumber: row.lotNumber?.trim() || '—',
    quantity: q || '—',
    unitText: row.unitCodeText?.trim() || '—',
    totalPriceLabel: scalarToTrimmedString(row.totalPrice) || '—',
    doctorName: row.doctorName?.trim() || '—',
    refundEnum,
    refundEnumText: row.refundEnumText?.trim() || '—',
    reqStatusText: row.reqStatusText?.trim() || '—',
    traceNo: row.traceNo?.trim() || '—',
    selectable: refundEnum === 'PENDING_REFUND',
  };
}

/** 按当前 Tab 过滤队列（后端分页不区分状态，由就诊聚合 refundEnum 前端筛） */
export function filterQueuePatientsByRefundTab(
  patients: ReturnMedicineQueuePatient[],
  refundQueueStatus: string,
): ReturnMedicineQueuePatient[] {
  const tab = refundQueueStatus.trim();
  if (!tab) return patients;
  return patients.filter((p) => p.refundEnum === tab);
}
