import type {
  InpatientReturnMedicineDetailRowDto,
  InpatientReturnPatientRowDto,
} from '#/api/inpatient-pharmacy-return-medicine/inpatient-pharmacy-return-medicine.types';

import type {
  InpatientReturnMedicineDetailRow,
  InpatientReturnMedicineQueuePatient,
} from '../components/types';

function scalarToTrimmedString(value: null | number | string | undefined): string {
  if (value === null || value === undefined) return '';
  return String(value).trim();
}

/** 队列行 DTO → 左侧患者卡片模型 */
export function mapInpatientReturnPatientRowToQueuePatient(
  row: InpatientReturnPatientRowDto,
): InpatientReturnMedicineQueuePatient {
  // 费用性质：透传后端合同名；空则卡片组件回落「自费」
  const feeNature = row.contractDisplay?.trim() || undefined;
  return {
    id: String(row.encounterId ?? '').trim(),
    patientName: row.patientName?.trim() || '—',
    genderEnumText: row.genderEnumText?.trim() || '—',
    ageLabel: row.age?.trim() || '—',
    hospitalizationNo: row.encounterNo?.trim() || '—',
    wardName: row.wardName?.trim() || '—',
    bedName: row.bedName?.trim() || '—',
    refundEnum: row.refundEnum?.trim() || '',
    refundEnumText: row.refundEnumText?.trim() || '—',
    feeNatureLabel: feeNature,
  };
}

/** 明细 DTO → 表格行（优先使用后端 selectable） */
export function mapInpatientReturnDetailRowToUi(
  row: InpatientReturnMedicineDetailRowDto,
): InpatientReturnMedicineDetailRow {
  const refundEnum = row.refundEnum?.trim() || '';
  const dispenseId = String(row.dispenseId ?? '').trim();
  return {
    id: dispenseId,
    dispenseId,
    requestId: String(row.requestId ?? '').trim(),
    itemName: row.itemName?.trim() || '—',
    lotNumber: row.lotNumber?.trim() || '—',
    quantity: scalarToTrimmedString(row.quantity) || '—',
    unitText: row.unitCodeText?.trim() || '—',
    doctorName: row.doctorName?.trim() || '—',
    refundEnum,
    refundEnumText: row.refundEnumText?.trim() || '—',
    traceNo: row.traceNo?.trim() || '—',
    selectable: row.selectable ?? refundEnum === 'PENDING_REFUND',
  };
}

/** 初始化下拉 → 前端选项 */
export function mapApiOptionItems(
  items: { label?: string; value?: number | string }[] | undefined,
): { label: string; value: string }[] {
  return (items ?? [])
    .map((item) => ({
      label: String(item.label ?? '').trim(),
      value: String(item.value ?? '').trim(),
    }))
    .filter((item) => item.label && item.value);
}
