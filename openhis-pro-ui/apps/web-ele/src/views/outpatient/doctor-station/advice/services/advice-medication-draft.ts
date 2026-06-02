import type { DiagnosisItem } from '#/components/doctor-station/diagnosis';
import type { DoctorStationAdviceDraftLineRequest } from '#/api/doctor-station/advice.types';
import type { EncounterDiagnosisRowDto } from '#/api/doctor-station/diagnosis.types';

import type { AdviceDrugItem } from './advice.types';
import {
  pickMainDiagnosisDisplayName,
  resolveConditionDefinitionIdFromEncounterRows,
} from './advice-edit';
import {
  inferAdviceLineEditorKind,
  parseColonDefinitionId,
  parseProductTableNameFromSelectedId,
} from './advice-row';
import { MEDICATION_DEFINITION_TABLE } from './advice-tab.constants';
import { resolveMedicationDraftDosePayload } from './medication-unit-bands';

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

function normalizedSortNumber(item: AdviceDrugItem, index: number): number {
  const sortRaw = item.sortNumber;
  const sortParsed = Number(sortRaw);
  const hasSort =
    sortRaw !== undefined && sortRaw !== null && Number.isFinite(sortParsed) && sortParsed > 0;
  return hasSort ? Math.trunc(sortParsed) : index + 1;
}

function buildMedicationDraftLine(
  item: AdviceDrugItem,
  index: number,
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

  const sortNumber = normalizedSortNumber(item, index);

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
  index: number,
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
  const unitCode = String(item.totalAmountUnit ?? '').trim() || '次';
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
    sortNumber: normalizedSortNumber(item, index),
    servicePayload,
  };
}

/** 耗材：定义主键、数量、单位与目录选行回填一致。 */
function buildDeviceDraftLine(
  item: AdviceDrugItem,
  index: number,
): DoctorStationAdviceDraftLineRequest {
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
    sortNumber: normalizedSortNumber(item, index),
    devicePayload,
  };
}

/**
 * 将编辑卡多行组装为暂存批次 lines：按行区分药品 / 诊疗 / 耗材（与目录复合 selectedId、productTableName 一致）。
 */
export interface BuildAdviceDraftLinesOptions {
  encounterDiagnosisRows?: EncounterDiagnosisRowDto[];
}

export function buildAdviceDraftLines(
  items: AdviceDrugItem[],
  patientDiagnoses: DiagnosisItem[],
  options?: BuildAdviceDraftLinesOptions,
): DoctorStationAdviceDraftLineRequest[] {
  const encounterRows = options?.encounterDiagnosisRows ?? [];
  return items.map((item, index) => {
    const kind = inferAdviceLineEditorKind(item);
    if (kind === 'SERVICE') {
      return buildServiceDraftLine(item, index, patientDiagnoses);
    }
    if (kind === 'DEVICE') {
      return buildDeviceDraftLine(item, index);
    }
    return buildMedicationDraftLine(item, index, patientDiagnoses, encounterRows);
  });
}
