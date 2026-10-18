import type { InpatientDoctorStationAdviceDraftLineRequest } from '#/api/inpatient/doctor-station/inpatient-doctor-station-advice.types';

import type { AdviceDrugItem } from './advice.types';
import { resolveMedicationContractId } from './advice-edit';
import {
  inferAdviceLineEditorKind,
  isInpatientLongTermMedicationTherapy,
  parseColonDefinitionId,
  parseProductTableNameFromSelectedId,
} from './advice-row';
import { resolvePersistMedicationQuantityUnit } from '#/utils/doctor-station/medication-unit-band-query';

import {
  resolveFirstDosePersistPayload,
  resolveDoseQuantitySchedulePersistPayload,
  resolveLongTermMedicationQuantityPayload,
  resolveMedicationDraftDosePayload,
} from './medication-unit-bands';
import { MEDICATION_DEFINITION_TABLE } from './advice-tab.constants';

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
): InpatientDoctorStationAdviceDraftLineRequest {
  const defId = parseMedicationDefinitionId(item.selectedId);
  const rid = item.medicationRequestId?.trim();
  const op = rid ? 'UPDATE' : 'CREATE';
  if (op === 'CREATE' && !defId) {
    throw new Error('请从目录选择药品后再保存（需带 med_medication_definition 主键）');
  }
  const contractId = resolveMedicationContractId(item);
  const therapyEnum = String(item.therapyEnum ?? '').trim() || undefined;
  const longTerm = isInpatientLongTermMedicationTherapy(therapyEnum);
  let qty: number;
  let unitCode: string;
  if (longTerm) {
    ({ quantity: qty, unitCode } = resolveLongTermMedicationQuantityPayload(item));
  } else {
    const qtyRaw = Number(item.totalAmount ?? 1);
    qty = Number.isFinite(qtyRaw) && qtyRaw > 0 ? qtyRaw : 1;
    unitCode = String(item.totalAmountUnit || 'U').trim() || 'U';
  }
  const methodCode = String(item.usage || '').trim() || undefined;
  const rateCode = String(item.frequency || '').trim() || undefined;
  // 非输液且无码：传空串清空；有码（含同组同步到溶媒）或输液药：落库
  const rawSpeed = String(item.speedCode ?? '').trim();
  const speedCode = item.injectFlag === true || rawSpeed ? rawSpeed : '';
  const { dose, doseUnitCode } = resolveMedicationDraftDosePayload(item);
  // 首日：界面计量 → 落库最小单位；长期无值显式 null 清空；有值却换算失败则拒绝保存
  const firstDosePersist = longTerm ? resolveFirstDosePersistPayload(item) : undefined;
  const firstDoseDisplayRaw = Number(item.firstDose);
  const hasFirstDoseDisplay =
    longTerm && Number.isFinite(firstDoseDisplayRaw) && firstDoseDisplayRaw > 0;
  if (hasFirstDoseDisplay && !firstDosePersist) {
    throw new Error('首日用量无法换算为最小单位，请重新选择药品后再保存');
  }
  // 分次用量不一致：长期西药多框 → doseSchedule/quantitySchedule；关开关显式 null 清空
  const schedulePersist =
    longTerm && item.unevenDoseEnabled
      ? resolveDoseQuantitySchedulePersistPayload(item)
      : undefined;
  if (longTerm && item.unevenDoseEnabled && !schedulePersist) {
    throw new Error('请按频次填全各次剂量，或取消「变动剂量」');
  }
  // 有 schedule：dose/quantity 仍写序列首项（列必填）；读侧有序列则按段取量，不按单值算全日每次量
  let persistDose = dose;
  let persistDoseUnitCode = doseUnitCode;
  let persistQty = qty;
  let persistUnitCode = unitCode;
  if (schedulePersist) {
    persistDose = schedulePersist.firstDoseQty;
    persistDoseUnitCode = schedulePersist.doseUnitCode;
    persistQty = schedulePersist.firstQuantity;
    persistUnitCode = schedulePersist.unitCode;
  }
  // Gate：unitCode 须在目录 unitBands；非法时按剂量回落拆零（防成组带入溶媒单位）
  ({ quantity: persistQty, unitCode: persistUnitCode } = resolvePersistMedicationQuantityUnit({
    quantity: persistQty,
    unitCode: persistUnitCode,
    bands: item.unitBands,
    dose: persistDose,
    doseUnitCode: persistDoseUnitCode,
    drugName: item.selectedDrugName,
  }));
  const daysRaw = Number(item.medicationDays ?? Number.NaN);
  const dispensePerDuration =
    !longTerm && Number.isFinite(daysRaw) && daysRaw > 0 ? Math.trunc(daysRaw) : undefined;

  const productTableName =
    String(item.productTableName ?? '').trim() ||
    parseProductTableNameFromSelectedId(item.selectedId);

  const sortNumber = normalizedSortNumber(item, index);

  const gid = String(item.groupId ?? '').trim();
  const remark = String(item.remark ?? '').trim();

  const medicationPayload: InpatientDoctorStationAdviceDraftLineRequest['medicationPayload'] = {
    quantity: persistQty,
    unitCode: persistUnitCode,
    skinTestFlag: Boolean(item.skinTest),
    // 始终传备注（含空串），UPDATE 时可清空
    remark,
    ...(therapyEnum ? { therapyEnum } : {}),
    ...(defId ? { medicationDefinitionId: defId } : {}),
    ...(persistDose === undefined ? {} : { dose: persistDose }),
    ...(persistDoseUnitCode ? { doseUnitCode: persistDoseUnitCode } : {}),
    ...(longTerm
      ? firstDosePersist
        ? {
            firstDose: firstDosePersist.firstDose,
            firstDoseUnitCode: firstDosePersist.firstDoseUnitCode,
          }
        : {
            // 显式 null：后端 syncFirstDose 覆盖清空（仅无首日量时）
            firstDose: null,
            firstDoseUnitCode: null,
          }
      : {}),
    ...(longTerm
      ? schedulePersist
        ? {
            doseSchedule: schedulePersist.doseSchedule,
            quantitySchedule: schedulePersist.quantitySchedule,
          }
        : {
            // 显式 null：关闭「用量不一致」后清空列
            doseSchedule: null,
            quantitySchedule: null,
          }
      : {}),
    ...(dispensePerDuration === undefined ? {} : { dispensePerDuration }),
    ...(methodCode ? { methodCode } : {}),
    ...(rateCode ? { rateCode } : {}),
    // UPDATE 始终传 speedCode（含空串），域层可清空旧滴速
    speedCode,
    ...(contractId ? { contractId } : {}),
    ...(gid ? { groupId: gid } : {}),
  };

  return {
    lineOp: op,
    requestId: rid || undefined,
    ...(productTableName ? { productTableName } : {}),
    sortNumber,
    ...(therapyEnum ? { therapyTypeCode: therapyEnum, therapyEnum } : {}),
    medicationPayload,
  };
}

/** 诊疗：定义主键、数量、频次、执行次数、单位等与 `servicePayload` 顶层字段一致。 */
function buildServiceDraftLine(
  item: AdviceDrugItem,
  index: number,
): InpatientDoctorStationAdviceDraftLineRequest {
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
  const therapyEnum = String(item.therapyEnum ?? '').trim() || undefined;
  const longTerm = isInpatientLongTermMedicationTherapy(therapyEnum);
  const qtyRaw = Number(item.serviceQuantity ?? Number.NaN);
  const quantity = Number.isFinite(qtyRaw) && qtyRaw > 0 ? qtyRaw : 1;
  const execRaw = Number(item.totalAmount ?? 0);
  const executionCount = Number.isFinite(execRaw) && execRaw > 0 ? execRaw : 1;
  const unitCode = String(item.totalAmountUnit ?? '').trim() || '次';
  const rateCode = String(item.frequency || '').trim() || undefined;
  const entrustContent = String(item.entrustContent ?? '').trim() || undefined;
  // 始终传备注（含空串），UPDATE 时可清空；套餐覆盖由后端优先 childOverridesJson
  const remark = String(item.remark ?? '').trim();
  const servicePayload: NonNullable<
    InpatientDoctorStationAdviceDraftLineRequest['servicePayload']
  > = {
    activityDefinitionId: defId || '',
    quantity,
    unitCode,
    remark,
    ...(performOrgRaw && /^\d+$/.test(performOrgRaw) ? { performOrgId: performOrgRaw } : {}),
    ...(rateCode ? { rateCode } : {}),
    ...(!longTerm ? { executionCount } : {}),
    ...(entrustContent ? { entrustContent } : {}),
    priorityEnum: String(item.priorityEnum ?? '').trim() || '',
  };
  return {
    lineOp: op,
    requestId: rid || undefined,
    productTableName,
    sortNumber: normalizedSortNumber(item, index),
    ...(therapyEnum ? { therapyTypeCode: therapyEnum, therapyEnum } : {}),
    servicePayload,
  };
}

/**
 * 同组药品滴速一致：把组内首条非空 speedCode 同步到同组全部药品行。
 */
export function syncGroupSpeedAcrossAdviceItems(items: AdviceDrugItem[]): AdviceDrugItem[] {
  const indicesByGroup = new Map<string, number[]>();
  items.forEach((item, index) => {
    if (inferAdviceLineEditorKind(item) !== 'MEDICATION') {
      return;
    }
    const gid = String(item.groupId ?? '').trim();
    if (!gid) {
      return;
    }
    const list = indicesByGroup.get(gid) ?? [];
    list.push(index);
    indicesByGroup.set(gid, list);
  });
  if (indicesByGroup.size === 0) {
    return items;
  }
  const next = items.map((item) => ({ ...item }));
  for (const indices of indicesByGroup.values()) {
    if (indices.length < 2) {
      continue;
    }
    let speed = '';
    for (const index of indices) {
      const code = String(next[index]?.speedCode ?? '').trim();
      if (code) {
        speed = code;
        break;
      }
    }
    if (!speed) {
      continue;
    }
    for (const index of indices) {
      const row = next[index];
      if (!row) {
        continue;
      }
      next[index] = { ...row, speedCode: speed };
    }
  }
  return next;
}

/**
 * 将编辑卡多行组装为暂存批次 lines：按行区分药品 / 诊疗 / 耗材（与目录复合 selectedId、productTableName 一致）。
 * 住院开立不绑诊断，不传 encounterDiagnosisId / conditionDefinitionId。
 */
export function buildAdviceDraftLines(
  items: AdviceDrugItem[],
): InpatientDoctorStationAdviceDraftLineRequest[] {
  const synced = syncGroupSpeedAcrossAdviceItems(items);
  return synced.map((item, index) => {
    const kind = inferAdviceLineEditorKind(item);
    if (kind === 'SERVICE') {
      return buildServiceDraftLine(item, index);
    }
    if (kind === 'DEVICE') {
      throw new Error('住院医生站不支持开立耗材医嘱');
    }
    return buildMedicationDraftLine(item, index);
  });
}
