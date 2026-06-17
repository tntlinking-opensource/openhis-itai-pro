import type { EncounterMedicationDispenseSlipRowDto } from '#/api/outpatient-pharmacy-dispense/outpatient-pharmacy-dispense.types';

import { httpPageEncounterMedicationDispenseSlips } from '#/api/outpatient-pharmacy-dispense/outpatient-pharmacy-dispense.http';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import type { NurseAdviceExecuteRow } from '#/views/outpatient/nurse-station/components/types';

export const OUTPATIENT_NURSE_PRESCRIPTION_TEMPLATE = 'outpatient_nurse_prescription';

export interface NursePrescriptionPrintTarget {
  prescriptionNo?: string;
  prescriptionGroupId?: string;
  medicationRequestIds: string[];
  westernOnly?: boolean;
  tcmOnly?: boolean;
}

function isHerbalSlipRow(row: EncounterMedicationDispenseSlipRowDto): boolean {
  if (row.tcmFlag === true) {
    return true;
  }
  if (row.tcmFlag === false) {
    return false;
  }
  const text = `${row.tcmFlagText ?? ''}${row.medicationCategoryText ?? ''}`;
  return text.includes('中药');
}

function isHerbalExecuteRow(row: NurseAdviceExecuteRow | undefined): boolean {
  if (!row) {
    return false;
  }
  const text = `${row.medCategoryCodeText ?? ''}${row.content ?? ''}`;
  return text.includes('中药');
}

/** 仅 med_medication_request.prescription_no；禁止用发放 busNo 冒充处方号 */
function resolveRowRxNo(row: EncounterMedicationDispenseSlipRowDto): string {
  return (row.prescriptionNo ?? '').trim();
}

function resolveExecuteRowRxNo(row: NurseAdviceExecuteRow): string {
  return (row.prescriptionNo ?? '').trim();
}

function prescriptionGroupKeyFromSlip(row: EncounterMedicationDispenseSlipRowDto): string {
  const herbal = isHerbalSlipRow(row);
  return `${resolveRowRxNo(row)}|${herbal ? 't' : 'w'}`;
}

function prescriptionGroupKeyFromExecuteRow(row: NurseAdviceExecuteRow): string {
  const herbal = isHerbalExecuteRow(row);
  return `${resolveExecuteRowRxNo(row)}|${herbal ? 't' : 'w'}`;
}

function buildTargetFromSlipGroup(
  groupRows: EncounterMedicationDispenseSlipRowDto[],
): NursePrescriptionPrintTarget | null {
  groupRows.sort((a, b) =>
    String(a.requestAuthoredTime ?? '').localeCompare(String(b.requestAuthoredTime ?? '')),
  );
  const head = groupRows[0];
  if (!head) {
    return null;
  }
  const herbal = isHerbalSlipRow(head);
  const medicationRequestIds = groupRows
    .map((row) => String(row.medReqId ?? '').trim())
    .filter(Boolean);
  return {
    prescriptionNo: resolveRowRxNo(head),
    prescriptionGroupId:
      head.prescriptionGroupId != null ? String(head.prescriptionGroupId) : undefined,
    medicationRequestIds,
    westernOnly: herbal ? undefined : true,
    tcmOnly: herbal ? true : undefined,
  };
}

function buildTargetFromExecuteGroup(
  rows: Array<{ row: NurseAdviceExecuteRow; requestId: string }>,
): NursePrescriptionPrintTarget {
  const head = rows[0]!;
  const herbal = isHerbalExecuteRow(head.row);
  const medicationRequestIds = rows.map((item) => item.requestId);
  return {
    prescriptionNo: resolveExecuteRowRxNo(head.row),
    medicationRequestIds,
    westernOnly: herbal ? undefined : true,
    tcmOnly: herbal ? true : undefined,
  };
}

function groupTargetsFromSlips(
  matched: EncounterMedicationDispenseSlipRowDto[],
): NursePrescriptionPrintTarget[] {
  const groups = new Map<string, EncounterMedicationDispenseSlipRowDto[]>();
  for (const row of matched) {
    const key = prescriptionGroupKeyFromSlip(row);
    const list = groups.get(key) ?? [];
    list.push(row);
    groups.set(key, list);
  }
  const targets: NursePrescriptionPrintTarget[] = [];
  for (const groupRows of groups.values()) {
    const target = buildTargetFromSlipGroup(groupRows);
    if (target) {
      targets.push(target);
    }
  }
  return targets;
}

function groupTargetsFromExecuteRows(
  medicationRequestIds: string[],
  executeRows: NurseAdviceExecuteRow[],
): NursePrescriptionPrintTarget[] {
  const rowById = new Map<string, NurseAdviceExecuteRow>();
  for (const row of executeRows) {
    const requestId = String(row.requestId ?? '').trim();
    if (requestId) {
      rowById.set(requestId, row);
    }
  }

  const groups = new Map<string, Array<{ row: NurseAdviceExecuteRow; requestId: string }>>();
  for (const requestId of medicationRequestIds) {
    const row = rowById.get(requestId) ?? {
      key: `MR:${requestId}`,
      status: '',
      type: '药品',
      content: '',
      startTime: '',
      signer: '',
      department: '',
      stopOrVoid: '',
      extension: '',
      requestId,
    };
    const key = prescriptionGroupKeyFromExecuteRow(row);
    const list = groups.get(key) ?? [];
    list.push({ row, requestId });
    groups.set(key, list);
  }

  return [...groups.values()].map(buildTargetFromExecuteGroup);
}

/** 根据就诊与药品申请主键，解析需逐张打印的护士处方目标（发药 slip 优先，无 slip 时按医嘱行分组） */
export async function resolveNursePrescriptionPrintTargets(
  encounterId: string,
  medicationRequestIds: string[],
  executeRows: NurseAdviceExecuteRow[] = [],
): Promise<NursePrescriptionPrintTarget[]> {
  const normalizedIds = medicationRequestIds.map((id) => id.trim()).filter(Boolean);
  if (!normalizedIds.length) {
    return [];
  }
  const idSet = new Set(normalizedIds);

  try {
    const res = await httpPageEncounterMedicationDispenseSlips({
      encounterId,
      pageNo: 1,
      pageSize: 500,
    });
    const { content } = parsePagedRowsFromApiData<EncounterMedicationDispenseSlipRowDto>(res);
    const matched = content.filter((row) => idSet.has(String(row.medReqId ?? '').trim()));
    if (matched.length) {
      return groupTargetsFromSlips(matched);
    }
  } catch {
    // 无发药单或接口不可用时回退至医嘱分组
  }

  return groupTargetsFromExecuteRows(normalizedIds, executeRows);
}
