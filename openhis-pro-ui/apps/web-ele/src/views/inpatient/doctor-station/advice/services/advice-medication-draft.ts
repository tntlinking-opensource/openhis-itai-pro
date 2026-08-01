import type { InpatientDoctorStationAdviceDraftLineRequest } from '#/api/inpatient/doctor-station/inpatient-doctor-station-advice.types';

import type { AdviceDrugItem } from './advice.types';
import { resolveMedicationContractId } from './advice-edit';
import {
  inferAdviceLineEditorKind,
  isInpatientLongTermMedicationTherapy,
  parseColonDefinitionId,
  parseProductTableNameFromSelectedId,
} from './advice-row';
import {
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
  const { dose, doseUnitCode } = resolveMedicationDraftDosePayload(item);
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
    quantity: qty,
    unitCode,
    skinTestFlag: Boolean(item.skinTest),
    // 始终传备注（含空串），UPDATE 时可清空
    remark,
    ...(therapyEnum ? { therapyEnum } : {}),
    ...(defId ? { medicationDefinitionId: defId } : {}),
    ...(dose === undefined ? {} : { dose }),
    ...(doseUnitCode ? { doseUnitCode } : {}),
    ...(longTerm &&
    item.firstDose !== undefined &&
    item.firstDose !== null &&
    Number(item.firstDose) > 0
      ? {
          firstDose: Number(item.firstDose),
          firstDoseUnitCode:
            String(
              item.firstDoseUnitCode ?? item.totalAmountUnit ?? item.doseSubUnit ?? '',
            ).trim() || undefined,
        }
      : {}),
    ...(dispensePerDuration === undefined ? {} : { dispensePerDuration }),
    ...(methodCode ? { methodCode } : {}),
    ...(rateCode ? { rateCode } : {}),
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
 * 将编辑卡多行组装为暂存批次 lines：按行区分药品 / 诊疗 / 耗材（与目录复合 selectedId、productTableName 一致）。
 * 住院开立不绑诊断，不传 encounterDiagnosisId / conditionDefinitionId。
 */
export function buildAdviceDraftLines(
  items: AdviceDrugItem[],
): InpatientDoctorStationAdviceDraftLineRequest[] {
  return items.map((item, index) => {
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
