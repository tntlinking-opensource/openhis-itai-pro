import type { AdviceDrugItem, AdviceRowItem } from './advice.types';

import type { DoctorStationAdviceDraftLineRequest } from '#/api/doctor-station/advice.types';
import type { EncounterDiagnosisRowDto } from '#/api/doctor-station/diagnosis.types';
import type { DiagnosisItem } from '#/components/doctor-station/diagnosis';

import {
  filterValidEncounterDiagnosisIds,
  joinEncounterDiagnosisIds,
  parseEncounterDiagnosisIds,
  resolveConditionDefinitionIdFromEncounterRows,
} from '#/components/doctor-station/diagnosis/services/encounter-diagnosis-select';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import { resolveMedicationContractId, resolveMedicationEncounterDiagnosisId } from './advice-edit';
import {
  inferAdviceLineEditorKind,
  parseColonDefinitionId,
  parseProductTableNameFromSelectedId,
} from './advice-row';
import {
  assignMedicationSortNumbersBeforeSave,
  resolveMedicationDraftSortNumber,
  resolveOptionalDraftSortNumber,
} from './advice-sort-number';
import { MEDICATION_DEFINITION_TABLE } from './advice-tab.constants';
import { resolvePersistMedicationQuantityUnit } from '#/utils/doctor-station/medication-unit-band-query';

import { resolveMedicationDraftDosePayload } from './medication-unit-bands';
import { resolveServiceBillingQuantity } from './service-quantity-calc';

function normalizeMedicationDaysForGroupCompare(raw: unknown): number | undefined {
  const n = Number(raw);
  if (!Number.isFinite(n) || n <= 0) {
    return undefined;
  }
  return Math.trunc(n);
}

/** 编辑行是否为输液/注射药品（目录 inject_flag 或列表回填） */
export function isInfusionMedicationDrugItem(item: Pick<AdviceDrugItem, 'injectFlag'>): boolean {
  return item.injectFlag === true;
}

/** 主表行是否为输液/注射药品（列表 injectionFlag） */
export function isInfusionMedicationAdviceRow(row: Pick<AdviceRowItem, 'injectionFlag'>): boolean {
  return row.injectionFlag === true;
}

/** 同组诊断比对键：优先就诊诊断有序 ids，否则回落展示名 */
function normalizeMedicationDiagnosisForGroupCompare(
  encounterDiagnosisIds: string | string[] | null | undefined,
  diagnosisName?: null | string,
): string {
  const ids = parseEncounterDiagnosisIds(encounterDiagnosisIds);
  if (ids.length > 0) {
    return ids.join(',');
  }
  const name = String(diagnosisName ?? '').trim();
  return name === '—' ? '' : name;
}

type MedicationGroupSharedFields = {
  usage: string;
  frequency: string;
  days: number | undefined;
  diagnosis: string;
};

/** 同组字段（用法/频次/用药天数/诊断）不一致时的提示文案 */
function medicationGroupSharedFieldMismatchMessage(
  leader: MedicationGroupSharedFields,
  member: MedicationGroupSharedFields,
): string | undefined {
  if (member.usage !== leader.usage) {
    return '同组药品的用法必须一致';
  }
  if (member.frequency !== leader.frequency) {
    return '同组药品的频次必须一致';
  }
  if (member.days !== leader.days) {
    return '同组药品的用药天数必须一致';
  }
  if (member.diagnosis !== leader.diagnosis) {
    return '同组药品的诊断必须一致';
  }
  return undefined;
}

/** 同组成组药品：须用法、频次、用药天数、诊断一致（保存前校验） */
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
    const leaderFields: MedicationGroupSharedFields = {
      usage: String(leader.usage ?? '').trim(),
      frequency: String(leader.frequency ?? '').trim(),
      days: normalizeMedicationDaysForGroupCompare(leader.medicationDays),
      diagnosis: normalizeMedicationDiagnosisForGroupCompare(
        leader.encounterDiagnosisIds,
        leader.diagnosis,
      ),
    };
    for (let i = 1; i < members.length; i += 1) {
      const member = members[i];
      if (!member) {
        continue;
      }
      const mismatch = medicationGroupSharedFieldMismatchMessage(leaderFields, {
        usage: String(member.usage ?? '').trim(),
        frequency: String(member.frequency ?? '').trim(),
        days: normalizeMedicationDaysForGroupCompare(member.medicationDays),
        diagnosis: normalizeMedicationDiagnosisForGroupCompare(
          member.encounterDiagnosisIds,
          member.diagnosis,
        ),
      });
      if (mismatch) {
        return mismatch;
      }
    }
  }

  return undefined;
}

/** 并组前：勾选草稿须用法/频次/用药天数/诊断一致 */
export function validateMedicationAssignGroupRows(rows: AdviceRowItem[]): string | undefined {
  if (rows.length === 0) {
    return undefined;
  }
  if (rows.length <= 1) {
    return undefined;
  }
  const leader = rows[0];
  if (!leader) {
    return undefined;
  }
  const leaderFields: MedicationGroupSharedFields = {
    usage: String(leader.methodCode ?? '').trim(),
    frequency: String(leader.rateCode ?? '').trim(),
    days: normalizeMedicationDaysForGroupCompare(leader.dispensePerDuration),
    diagnosis: normalizeMedicationDiagnosisForGroupCompare(
      leader.encounterDiagnosisIds,
      leader.conditionDefinitionName || leader.diagnosisSummary,
    ),
  };
  for (let i = 1; i < rows.length; i += 1) {
    const member = rows[i];
    if (!member) {
      continue;
    }
    const mismatch = medicationGroupSharedFieldMismatchMessage(leaderFields, {
      usage: String(member.methodCode ?? '').trim(),
      frequency: String(member.rateCode ?? '').trim(),
      days: normalizeMedicationDaysForGroupCompare(member.dispensePerDuration),
      diagnosis: normalizeMedicationDiagnosisForGroupCompare(
        member.encounterDiagnosisIds,
        member.conditionDefinitionName || member.diagnosisSummary,
      ),
    });
    if (mismatch) {
      return mismatch;
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

/** 组包药品草稿：按行诊断名匹配；无名称时仅唯一诊断可回落（不默认主诊）。 */
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

  const withId = patientDiagnoses
    .map((d) => String(d.conditionDefinitionId ?? '').trim())
    .filter(Boolean);
  if (withId.length === 1) return withId[0];
  return undefined;
}

/** 药品行 → 有序就诊诊断有序 ids 串（主诊第一；兼容仅有展示名；不默认主诊） */
function resolveMedicationEncounterDiagnosisIds(
  item: Pick<AdviceDrugItem, 'diagnosis' | 'encounterDiagnosisIds'>,
  encounterRows: EncounterDiagnosisRowDto[],
): string | undefined {
  // 仅保留属于本次就诊的诊断 id，避免历史/脏 id 透传
  let ids = filterValidEncounterDiagnosisIds(item.encounterDiagnosisIds, encounterRows);
  if (ids.length === 0) {
    const fromName = resolveMedicationEncounterDiagnosisId(item.diagnosis, encounterRows);
    if (fromName) {
      ids = [fromName];
    }
  }
  if (ids.length === 0) return undefined;
  const idsText = joinEncounterDiagnosisIds(ids, encounterRows);
  if (!idsText) return undefined;
  // 逐段保 Long 精度后再拼 ids 串
  return parseEncounterDiagnosisIds(idsText)
    .map((id) => preserveApiIdForRequest(id) ?? id)
    .join(',');
}

/** 诊疗/耗材：仅在行上已显式绑定时透传 ids 串（不默认主诊，对齐 AS 本期口径） */
function resolveOptionalEncounterDiagnosisIds(
  item: Pick<AdviceDrugItem, 'diagnosis' | 'encounterDiagnosisIds'>,
  encounterRows: EncounterDiagnosisRowDto[],
): string | undefined {
  // 仅保留属于本次就诊的诊断 id
  let ids = filterValidEncounterDiagnosisIds(item.encounterDiagnosisIds, encounterRows);
  if (ids.length === 0) {
    const name = String(item.diagnosis ?? '').trim();
    if (!name) return undefined;
    const fromName = resolveMedicationEncounterDiagnosisId(name, encounterRows);
    if (fromName) {
      ids = [fromName];
    }
  }
  if (ids.length === 0) return undefined;
  const idsText = joinEncounterDiagnosisIds(ids, encounterRows);
  if (!idsText) return undefined;
  return parseEncounterDiagnosisIds(idsText)
    .map((id) => preserveApiIdForRequest(id) ?? id)
    .join(',');
}

function buildMedicationDraftLine(
  item: AdviceDrugItem,
  encounterRows: EncounterDiagnosisRowDto[],
): DoctorStationAdviceDraftLineRequest {
  const defId = parseMedicationDefinitionId(item.selectedId);
  const rid = item.medicationRequestId?.trim();
  const op = rid ? 'UPDATE' : 'CREATE';
  if (op === 'CREATE' && !defId) {
    throw new Error('请从目录选择药品后再保存（需带 med_medication_definition 主键）');
  }
  const encounterDiagnosisIds = resolveMedicationEncounterDiagnosisIds(item, encounterRows);
  const contractId = resolveMedicationContractId(item);
  const qtyRaw = Number(item.totalAmount ?? 1);
  const qty = Number.isFinite(qtyRaw) && qtyRaw > 0 ? qtyRaw : 1;
  const unitCodeRaw = String(item.totalAmountUnit || 'U').trim() || 'U';
  const pharmacyRaw = String(item.pharmacy || '').trim();
  const dispenseLocationId = /^\d+$/.test(pharmacyRaw) ? pharmacyRaw : undefined;
  const methodCode = String(item.usage || '').trim() || undefined;
  const rateCode = String(item.frequency || '').trim() || undefined;
  const { dose, doseUnitCode } = resolveMedicationDraftDosePayload(item);
  // Gate：unitCode 须在目录 unitBands；非法时按剂量回落拆零
  const { quantity: qtyPersist, unitCode } = resolvePersistMedicationQuantityUnit({
    quantity: qty,
    unitCode: unitCodeRaw,
    bands: item.unitBands,
    dose,
    doseUnitCode,
    drugName: item.selectedDrugName,
  });
  const daysRaw = Number(item.medicationDays ?? Number.NaN);
  const dispensePerDuration =
    Number.isFinite(daysRaw) && daysRaw > 0 ? Math.trunc(daysRaw) : undefined;

  const productTableName =
    String(item.productTableName ?? '').trim() ||
    parseProductTableNameFromSelectedId(item.selectedId);

  const sortNumber = resolveMedicationDraftSortNumber(item);

  const gid = String(item.groupId ?? '').trim();
  const medicationPayload: DoctorStationAdviceDraftLineRequest['medicationPayload'] = {
    quantity: qtyPersist,
    unitCode,
    skinTestFlag: Boolean(item.skinTest),
    ...(defId ? { medicationDefinitionId: defId } : {}),
    ...(dose === undefined ? {} : { dose }),
    ...(doseUnitCode ? { doseUnitCode } : {}),
    ...(dispensePerDuration === undefined ? {} : { dispensePerDuration }),
    ...(methodCode ? { methodCode } : {}),
    ...(rateCode ? { rateCode } : {}),
    ...(dispenseLocationId ? { dispenseLocationId } : {}),
    ...(encounterDiagnosisIds ? { encounterDiagnosisIds } : {}),
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

/**
 * 诊疗：定义主键、单次量×次数→quantity、频次、执行次数、单位等与 `servicePayload` 顶层一致。
 */
function buildServiceDraftLine(
  item: AdviceDrugItem,
  encounterRows: EncounterDiagnosisRowDto[],
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
  const execRaw = Number(item.totalAmount ?? 0);
  const executionCount = Number.isFinite(execRaw) && execRaw > 0 ? Math.trunc(execRaw) : 1;
  // 账单 quantity = 单次执行数量 × 执行次数（后端仍只认总量）
  const billingQty = resolveServiceBillingQuantity(item.serviceQuantity, executionCount);
  if (billingQty === undefined) {
    throw new Error('请填写大于 0 的单次执行数量与执行次数');
  }
  const quantity = billingQty;
  const unitCode = String(item.totalAmountUnit ?? '').trim();
  if (!unitCode) {
    throw new Error(
      '该诊疗项目未维护销售/计价单位，请先在「基础数据 > 诊疗目录」维护包装单位后再保存',
    );
  }
  const rateCode = String(item.frequency || '').trim() || undefined;
  const encounterDiagnosisIds = resolveOptionalEncounterDiagnosisIds(item, encounterRows);
  const contractId = resolveMedicationContractId(item);
  const sortNumber = resolveOptionalDraftSortNumber(item);
  const servicePayload: NonNullable<DoctorStationAdviceDraftLineRequest['servicePayload']> = {
    activityDefinitionId: defId || '',
    quantity,
    unitCode,
    ...(performOrgRaw && /^\d+$/.test(performOrgRaw) ? { performOrgId: performOrgRaw } : {}),
    ...(rateCode ? { rateCode } : {}),
    executionCount,
    ...(encounterDiagnosisIds ? { encounterDiagnosisIds } : {}),
    ...(contractId ? { contractId } : {}),
    // 检验/检查加急：勾选→EM，未勾→空串清空
    priorityEnum: String(item.priorityEnum ?? '').trim() || '',
  };
  return {
    lineOp: op,
    requestId: rid || undefined,
    productTableName,
    ...(sortNumber === undefined ? {} : { sortNumber }),
    servicePayload,
  };
}

/** 耗材：定义主键、数量、单位与目录选行回填一致（不绑定诊断）。 */
function buildDeviceDraftLine(
  item: AdviceDrugItem,
  _encounterRows: EncounterDiagnosisRowDto[],
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
  const contractId = resolveMedicationContractId(item);
  const sortNumber = resolveOptionalDraftSortNumber(item);
  const devicePayload: NonNullable<DoctorStationAdviceDraftLineRequest['devicePayload']> = {
    deviceDefinitionId: defId || '',
    quantity: qty,
    unitCode,
    ...(performOrgRaw && /^\d+$/.test(performOrgRaw) ? { performOrgId: performOrgRaw } : {}),
    ...(contractId ? { contractId } : {}),
  };
  return {
    lineOp: op,
    requestId: rid || undefined,
    productTableName,
    ...(sortNumber === undefined ? {} : { sortNumber }),
    devicePayload,
  };
}

/**
 * 将编辑卡多行组装为暂存批次 lines：按行区分药品 / 诊疗 / 耗材（与目录复合 selectedId、productTableName 一致）。
 */
export interface BuildAdviceDraftLinesOptions {
  encounterDiagnosisRows?: EncounterDiagnosisRowDto[];
  /** 当前就诊主表行，用于药/诊/材 sortNumber 追加分配 */
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
      return buildServiceDraftLine(item, encounterRows);
    }
    if (kind === 'DEVICE') {
      return buildDeviceDraftLine(item, encounterRows);
    }
    return buildMedicationDraftLine(item, encounterRows);
  });
}
