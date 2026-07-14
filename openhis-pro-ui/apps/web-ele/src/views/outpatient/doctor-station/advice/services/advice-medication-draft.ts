import type { AdviceDrugItem, AdviceRowItem } from './advice.types';

import type { DoctorStationAdviceDraftLineRequest } from '#/api/doctor-station/advice.types';
import type { EncounterDiagnosisRowDto } from '#/api/doctor-station/diagnosis.types';
import type { DiagnosisItem } from '#/components/doctor-station/diagnosis';

import {
  pickMainDiagnosisDisplayName,
  resolveConditionDefinitionIdFromEncounterRows,
  resolveMedicationContractId,
  resolveMedicationEncounterDiagnosisId,
} from './advice-edit';
import {
  inferAdviceLineEditorKind,
  parseColonDefinitionId,
  parseProductTableNameFromSelectedId,
} from './advice-row';
import {
  assignMedicationSortNumbersBeforeSave,
  resolveMedicationDraftSortNumber,
} from './advice-sort-number';
import { MEDICATION_DEFINITION_TABLE } from './advice-tab.constants';
import { resolveMedicationDraftDosePayload } from './medication-unit-bands';

function normalizeMedicationDaysForGroupCompare(raw: unknown): number | undefined {
  const n = Number(raw);
  if (!Number.isFinite(n) || n <= 0) {
    return undefined;
  }
  return Math.trunc(n);
}

/** 同组成组药品：用法、用药天数须一致（保存前校验） */
export function validateMedicationGroupSharedFields(items: AdviceDrugItem[]): string | undefined {
  const byGroup = new Map<string, AdviceDrugItem[]>();
  for (const item of items) {
    if (inferAdviceLineEditorKind(item) !== 'MEDICATION') {
      continue;
    }
    const groupId = String(item.groupId ?? '').trim();
    if (!groupId) {
      continue;
    }
    const members = byGroup.get(groupId) ?? [];
    members.push(item);
    byGroup.set(groupId, members);
  }

  for (const members of byGroup.values()) {
    if (members.length <= 1) {
      continue;
    }
    const leader = members[0];
    if (!leader) {
      continue;
    }
    const leaderUsage = String(leader.usage ?? '').trim();
    const leaderDays = normalizeMedicationDaysForGroupCompare(leader.medicationDays);
    for (let i = 1; i < members.length; i += 1) {
      const member = members[i];
      if (!member) {
        continue;
      }
      const memberUsage = String(member.usage ?? '').trim();
      if (memberUsage !== leaderUsage) {
        return '同组药品的用法必须一致';
      }
      const memberDays = normalizeMedicationDaysForGroupCompare(member.medicationDays);
      if (memberDays !== leaderDays) {
        return '同组药品的用药天数必须一致';
      }
    }
  }

  return undefined;
}

/** 从 `med_medication_definition:{id}` 形态的 selectedId 取药品定义主键。 */
export function parseMedicationDefinitionId(selectedId: string): string | undefined {
  const s = String(selectedId || '').trim();
  const prefix = `${MEDICATION_DEFINITION_TABLE}:`;
  if (s.startsWith(prefix)) {
    const id = s.slice(prefix.length).trim();
    return id || undefined;
  }
  return undefined;
}

/** 将所选诊断名称映射为就诊诊断 `conditionDefinitionId`，写入草稿 medicationPayload。 */
export function resolveConditionDefinitionIdFromDiagnosisName(
  diagnosisName: string,
  patientDiagnoses: DiagnosisItem[],
): string | undefined {
  const name = diagnosisName.trim();
  if (!name) return undefined;
  const hit = patientDiagnoses.find((d) => d.name === name);
  const did = String(hit?.conditionDefinitionId ?? '').trim();
  return did || undefined;
}

/** 组包药品草稿：优先按行诊断名匹配，再回落就诊诊断快照 / 主诊断 / 唯一诊断。 */
export function resolveMedicationConditionDefinitionId(
  diagnosisName: string,
  patientDiagnoses: DiagnosisItem[],
  encounterRows: EncounterDiagnosisRowDto[] = [],
): string | undefined {
  const name = String(diagnosisName ?? '').trim();
  if (name) {
    const fromList = resolveConditionDefinitionIdFromDiagnosisName(name, patientDiagnoses);
    if (fromList) return fromList;
    const fromEncounter = resolveConditionDefinitionIdFromEncounterRows(name, encounterRows);
    if (fromEncounter) return fromEncounter;
  }

  const mainName = pickMainDiagnosisDisplayName(encounterRows);
  if (mainName) {
    const mainFromList = resolveConditionDefinitionIdFromDiagnosisName(mainName, patientDiagnoses);
    if (mainFromList) return mainFromList;
    const mainFromEncounter = resolveConditionDefinitionIdFromEncounterRows(
      mainName,
      encounterRows,
    );
    if (mainFromEncounter) return mainFromEncounter;
  }

  const withId = patientDiagnoses
    .map((d) => String(d.conditionDefinitionId ?? '').trim())
    .filter(Boolean);
  if (withId.length === 1) return withId[0];
  return undefined;
}

function buildMedicationDraftLine(
  item: AdviceDrugItem,
  patientDiagnoses: DiagnosisItem[],
  encounterRows: EncounterDiagnosisRowDto[],
): DoctorStationAdviceDraftLineRequest {
  const defId = parseMedicationDefinitionId(item.selectedId);
  const rid = item.medicationRequestId?.trim();
  const op = rid ? 'UPDATE' : 'CREATE';
  if (op === 'CREATE' && !defId) {
    throw new Error('请从目录选择药品后再保存（需带 med_medication_definition 主键）');
  }
  const conditionDefinitionId = resolveMedicationConditionDefinitionId(
    item.diagnosis,
    patientDiagnoses,
    encounterRows,
  );
  const encounterDiagnosisId = resolveMedicationEncounterDiagnosisId(item.diagnosis, encounterRows);
  const contractId = resolveMedicationContractId(item);
  const qtyRaw = Number(item.totalAmount ?? 1);
  const qty = Number.isFinite(qtyRaw) && qtyRaw > 0 ? qtyRaw : 1;
  const unitCode = String(item.totalAmountUnit || 'U').trim() || 'U';
  const pharmacyRaw = String(item.pharmacy || '').trim();
  const dispenseLocationId = /^\d+$/.test(pharmacyRaw) ? pharmacyRaw : undefined;
  const methodCode = String(item.usage || '').trim() || undefined;
  const rateCode = String(item.frequency || '').trim() || undefined;
  const { dose, doseUnitCode } = resolveMedicationDraftDosePayload(item);
  const daysRaw = Number(item.medicationDays ?? Number.NaN);
  const dispensePerDuration =
    Number.isFinite(daysRaw) && daysRaw > 0 ? Math.trunc(daysRaw) : undefined;

  const productTableName =
    String(item.productTableName ?? '').trim() ||
    parseProductTableNameFromSelectedId(item.selectedId);

  const sortNumber = resolveMedicationDraftSortNumber(item);

  const gid = String(item.groupId ?? '').trim();
  const medicationPayload: DoctorStationAdviceDraftLineRequest['medicationPayload'] = {
    quantity: qty,
    unitCode,
    skinTestFlag: Boolean(item.skinTest),
    ...(defId ? { medicationDefinitionId: defId } : {}),
    ...(dose === undefined ? {} : { dose }),
    ...(doseUnitCode ? { doseUnitCode } : {}),
    ...(dispensePerDuration === undefined ? {} : { dispensePerDuration }),
    ...(methodCode ? { methodCode } : {}),
    ...(rateCode ? { rateCode } : {}),
    ...(dispenseLocationId ? { dispenseLocationId } : {}),
    ...(conditionDefinitionId ? { conditionDefinitionId } : {}),
    ...(encounterDiagnosisId ? { encounterDiagnosisId } : {}),
    ...(contractId ? { contractId } : {}),
    ...(gid ? { groupId: gid } : {}),
  };

  return {
    lineOp: op,
    requestId: rid || undefined,
    ...(productTableName ? { productTableName } : {}),
    sortNumber,
    medicationPayload,
  };
}

/** 诊疗：定义主键、数量、频次、执行次数、单位等与 `servicePayload` 顶层字段一致。 */
function buildServiceDraftLine(
  item: AdviceDrugItem,
  _patientDiagnoses: DiagnosisItem[],
): DoctorStationAdviceDraftLineRequest {
  const defId = parseColonDefinitionId(item.selectedId);
  const rid = item.medicationRequestId?.trim();
  const op = rid ? 'UPDATE' : 'CREATE';
  if (op === 'CREATE' && !defId) {
    throw new Error('请从目录选择诊疗项目后再保存（需带 wkf_activity_definition 主键）');
  }
  const productTableName =
    String(item.productTableName ?? '').trim() ||
    parseProductTableNameFromSelectedId(item.selectedId) ||
    'wkf_activity_definition';
  const performOrgRaw = String(item.performOrgId ?? '').trim();
  const qtyRaw = Number(item.serviceQuantity ?? Number.NaN);
  const quantity = Number.isFinite(qtyRaw) && qtyRaw > 0 ? qtyRaw : 1;
  const execRaw = Number(item.totalAmount ?? 0);
  const executionCount = Number.isFinite(execRaw) && execRaw > 0 ? execRaw : 1;
  const unitCode = String(item.totalAmountUnit ?? '').trim();
  if (!unitCode) {
    throw new Error(
      '该诊疗项目未维护销售/计价单位，请先在「基础数据 > 诊疗目录」维护包装单位后再保存',
    );
  }
  const rateCode = String(item.frequency || '').trim() || undefined;
  const servicePayload: NonNullable<DoctorStationAdviceDraftLineRequest['servicePayload']> = {
    activityDefinitionId: defId || '',
    quantity,
    unitCode,
    ...(performOrgRaw && /^\d+$/.test(performOrgRaw) ? { performOrgId: performOrgRaw } : {}),
    ...(rateCode ? { rateCode } : {}),
    executionCount,
  };
  return {
    lineOp: op,
    requestId: rid || undefined,
    productTableName,
    servicePayload,
  };
}

/** 耗材：定义主键、数量、单位与目录选行回填一致。 */
function buildDeviceDraftLine(item: AdviceDrugItem): DoctorStationAdviceDraftLineRequest {
  const defId = parseColonDefinitionId(item.selectedId);
  const rid = item.medicationRequestId?.trim();
  const op = rid ? 'UPDATE' : 'CREATE';
  if (op === 'CREATE' && !defId) {
    throw new Error('请从目录选择耗材后再保存（需带 adm_device_definition 主键）');
  }
  const qtyRaw = Number(item.totalAmount ?? 1);
  const qty = Number.isFinite(qtyRaw) && qtyRaw > 0 ? qtyRaw : 1;
  const unitCode = String(item.totalAmountUnit ?? '').trim() || 'U';
  const productTableName =
    String(item.productTableName ?? '').trim() ||
    parseProductTableNameFromSelectedId(item.selectedId) ||
    'adm_device_definition';
  const performOrgRaw = String(item.performOrgId ?? '').trim();
  const devicePayload: NonNullable<DoctorStationAdviceDraftLineRequest['devicePayload']> = {
    deviceDefinitionId: defId || '',
    quantity: qty,
    unitCode,
    ...(performOrgRaw && /^\d+$/.test(performOrgRaw) ? { performOrgId: performOrgRaw } : {}),
  };
  return {
    lineOp: op,
    requestId: rid || undefined,
    productTableName,
    devicePayload,
  };
}

/**
 * 将编辑卡多行组装为暂存批次 lines：按行区分药品 / 诊疗 / 耗材（与目录复合 selectedId、productTableName 一致）。
 */
export interface BuildAdviceDraftLinesOptions {
  encounterDiagnosisRows?: EncounterDiagnosisRowDto[];
  /** 当前就诊主表行，用于药品 sortNumber 追加分配 */
  existingAdviceRows?: AdviceRowItem[];
}

/** 编辑行 → DELETE 暂存行（仅已落库 requestId） */
export function buildAdviceDraftDeleteLineFromDrugItem(
  item: AdviceDrugItem,
): DoctorStationAdviceDraftLineRequest | null {
  const requestId = String(item.medicationRequestId ?? '').trim();
  if (!requestId) {
    return null;
  }
  const productTableName =
    String(item.productTableName ?? '').trim() ||
    parseProductTableNameFromSelectedId(item.selectedId);
  return {
    lineOp: 'DELETE',
    requestId,
    ...(productTableName ? { productTableName } : {}),
  };
}

/** 编辑卡相对初始组被移除的已保存行 → DELETE 行 */
export function collectRemovedAdviceDraftDeleteLines(
  initialGroup: AdviceDrugItem[],
  currentItems: AdviceDrugItem[],
): DoctorStationAdviceDraftLineRequest[] {
  const survivingIds = new Set(
    currentItems.map((item) => String(item.medicationRequestId ?? '').trim()).filter(Boolean),
  );
  const lines: DoctorStationAdviceDraftLineRequest[] = [];
  for (const item of initialGroup) {
    const line = buildAdviceDraftDeleteLineFromDrugItem(item);
    if (!line) {
      continue;
    }
    const requestId = String(line.requestId ?? '').trim();
    if (requestId && !survivingIds.has(requestId)) {
      lines.push(line);
    }
  }
  return lines;
}

export function buildAdviceDraftLines(
  items: AdviceDrugItem[],
  patientDiagnoses: DiagnosisItem[],
  options?: BuildAdviceDraftLinesOptions,
): DoctorStationAdviceDraftLineRequest[] {
  const groupValidationError = validateMedicationGroupSharedFields(items);
  if (groupValidationError) {
    throw new Error(groupValidationError);
  }
  const encounterRows = options?.encounterDiagnosisRows ?? [];
  const existingAdviceRows = options?.existingAdviceRows ?? [];
  const itemsWithSort = assignMedicationSortNumbersBeforeSave(items, existingAdviceRows);
  return itemsWithSort.map((item) => {
    const kind = inferAdviceLineEditorKind(item);
    if (kind === 'SERVICE') {
      return buildServiceDraftLine(item, patientDiagnoses);
    }
    if (kind === 'DEVICE') {
      return buildDeviceDraftLine(item);
    }
    return buildMedicationDraftLine(item, patientDiagnoses, encounterRows);
  });
}
