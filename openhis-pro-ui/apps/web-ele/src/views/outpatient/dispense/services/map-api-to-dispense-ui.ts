import type { DispensePrescriptionBlock, DispenseQueuePatient } from '../components/types';

import type {
  EncounterMedicationDispenseSlipRowDto,
  MedicationDispenseInventoryDetailDto,
  PendingDispenseEncounterQueueRowDto,
} from '#/api/outpatient-pharmacy-dispense/outpatient-pharmacy-dispense.types';

import { formatDateTime } from '@vben/utils';

import { formatMoneyDisplay } from '#/utils/format/money-scale';

import { encodeInventoryPick } from './inventory-pick-codec';
import { HERBAL_MEDICATION_CATEGORY_CODE } from '#/views/outpatient/doctor-station/advice/services/advice-tab.constants';
import {
  buildTcmHerbGridRowsFromSlipRows,
  buildTcmPrescriptionUsageTextFromSlipHead,
} from '#/utils/tcm-prescription-display';

/** 接口 number/string 统一为展示用字符串 */
function scalarToTrimmedString(value: null | number | string | undefined): string {
  if (value === null || value === undefined) return '';
  return String(value).trim();
}

/** 是否按中药分组/展示（仅认 tcmFlag / medicationCategoryCode） */
export function isHerbalSlipRow(row: EncounterMedicationDispenseSlipRowDto): boolean {
  if (row.tcmFlag === true) return true;
  if (row.tcmFlag === false) return false;
  return String(row.medicationCategoryCode ?? '').trim() === HERBAL_MEDICATION_CATEGORY_CODE;
}

/** 队列行 DTO → 左侧列表患者模型 */
export function mapPendingQueueRowToPatient(
  row: PendingDispenseEncounterQueueRowDto,
): DispenseQueuePatient {
  const ge = row.genderEnum;
  let genderEnum: number | undefined;
  if (ge !== null && ge !== undefined && ge !== '') {
    const n = Number(ge);
    if (!Number.isNaN(n)) genderEnum = n;
  }
  const start = row.startTime ? formatDateTime(row.startTime) : '';
  return {
    id: row.encounterId,
    patientId: row.patientId?.trim() || undefined,
    patientName: row.patientName?.trim() || '—',
    genderLabel: '',
    genderEnum,
    ageLabel: row.ageText?.trim() || '—',
    visitNo: row.encounterBusNo?.trim() || '—',
    visitTimeLabel: start || '—',
    rxCount: 0,
  };
}

/** 同一处方组内根据发放状态推导组状态标签 */
function blockStatusFromRows(
  rows: EncounterMedicationDispenseSlipRowDto[],
): 'blocking' | 'completed' | 'partial' | 'pending' | 'prepared' {
  const set = new Set(rows.map((r) => r.statusEnum));
  if (set.has('PREPARATION')) return 'pending';
  // 部分已发 + 部分已配未发（PART_COMPLETED 等混合态）
  if (set.has('COMPLETED') && (set.has('IN_PROGRESS') || set.has('PREPARED'))) return 'partial';
  // 保存配药后后端为 IN_PROGRESS（待发药），与 PREPARED（已配药）同属可核发态
  if (set.has('IN_PROGRESS') || set.has('PREPARED')) return 'prepared';
  if (set.has('COMPLETED')) return 'completed';
  return 'blocking';
}

/** 处方区块标题：仅 med_medication_request.prescription_no，禁止回落发放 busNo */
function resolvePrescriptionBlockNo(row: EncounterMedicationDispenseSlipRowDto): string {
  const prescriptionNo = row.prescriptionNo?.trim();
  if (prescriptionNo) {
    return prescriptionNo;
  }
  return '—';
}

/** 将发放行列表聚合成处方块（按 prescription_no 分块；无处方号时按发放行兜底） */
export function mapSlipRowsToPrescriptionBlocks(
  rows: EncounterMedicationDispenseSlipRowDto[],
): DispensePrescriptionBlock[] {
  const groups = new Map<string, EncounterMedicationDispenseSlipRowDto[]>();
  for (const r of rows) {
    const tcm = isHerbalSlipRow(r);
    const prescriptionNo = r.prescriptionNo?.trim();
    const key = prescriptionNo
      ? `${prescriptionNo}|${tcm ? 't' : 'w'}`
      : `dispense|${r.dispenseId}`;
    const list = groups.get(key);
    if (list) list.push(r);
    else groups.set(key, [r]);
  }

  const blocks: DispensePrescriptionBlock[] = [];
  for (const [, groupRows] of groups) {
    groupRows.sort((a, b) =>
      (a.requestAuthoredTime ?? '').localeCompare(b.requestAuthoredTime ?? ''),
    );
    const head = groupRows[0];
    if (!head) continue;
    const tcm = isHerbalSlipRow(head);
    const rxNo = resolvePrescriptionBlockNo(head);
    const issued =
      head.requestAuthoredTime !== null &&
      head.requestAuthoredTime !== undefined &&
      head.requestAuthoredTime !== ''
        ? formatDateTime(head.requestAuthoredTime)
        : '—';
    const items = groupRows.map((line) => {
      const invList = line.inventoryDetailList ?? [];
      const inventoryPickOptions =
        invList.length > 0
          ? invList.map((inv) => ({
              value: encodeInventoryPick({
                pharmacyOrganizationId: String(inv.pharmacyOrganizationId),
                lotNumber: inv.lotNumber,
              }),
              label: formatInventoryOptionLabel(inv),
            }))
          : undefined;
      return {
        id: line.dispenseId,
        medicationId: String(line.medicationId ?? '').trim() || undefined,
        itemName: line.medicationName?.trim() || line.merchandiseName?.trim() || '—',
        quantity: formatDispenseQuantityLabel(line),
        spec: line.specificationText?.trim() || '—',
        traceCode: line.traceNo?.trim() || '—',
        batchNo: line.lotNumber?.trim() || '—',
        amount:
          line.totalPrice !== null &&
          line.totalPrice !== undefined &&
          scalarToTrimmedString(line.totalPrice) !== ''
            ? formatMoneyDisplay(line.totalPrice)
            : '—',
        pharmacy:
          line.dispenseLocationName?.trim() ||
          (line.dispenseLocationId ? `库房 ${line.dispenseLocationId}` : '—'),
        dispenseLocationId: String(line.dispenseLocationId ?? '').trim() || undefined,
        inventoryPickOptions,
        factory: line.manufacturerName?.trim() || '—',
        diagnosis: line.conditionSummaryText?.trim() || '—',
        freq: line.frequencyText?.trim() || line.frequencyCode?.trim() || '—',
        dosageInstructionText:
          line.dosageInstructionText?.trim() || line.dosageInstructionCode?.trim() || '—',
      };
    });
    blocks.push({
      id: `block-${head.dispenseId}-${rxNo}`,
      rxNo,
      rxType: tcm ? 'herbal' : 'western',
      deptName: head.orderingDepartmentName?.trim() || '—',
      doctorName: head.orderingPractitionerName?.trim() || '—',
      issuedAt: issued,
      status: blockStatusFromRows(groupRows),
      items,
      ...(tcm
        ? {
            tcmHerbGridRows: buildTcmHerbGridRowsFromSlipRows(groupRows),
            tcmUsageText: buildTcmPrescriptionUsageTextFromSlipHead(head),
          }
        : {}),
    });
  }
  blocks.sort((a, b) => a.issuedAt.localeCompare(b.issuedAt));
  return blocks;
}

/** 列表「数量」列：绑定 slip.quantity，后接 unitText（不拼 unitCode） */
function formatDispenseQuantityLabel(line: EncounterMedicationDispenseSlipRowDto): string {
  const q = scalarToTrimmedString(line.quantity);
  const u = line.unitText?.trim() || '';
  if (!q && !u) return '—';
  if (!u) return q || '—';
  if (!q) return u;
  return `${q}${u}`;
}

function formatInventoryOptionLabel(inv: MedicationDispenseInventoryDetailDto): string {
  const name = inv.pharmacyDisplayName?.trim() || `库房 ${inv.pharmacyOrganizationId}`;
  const lot = inv.lotNumber?.trim();
  return lot ? `${name} / 批号 ${lot}` : name;
}
