import type { OutpatientInfusionLabelPrintSource } from './mappers/outpatient-infusion-label';
import type { OutpatientTreatmentSheetPrintSource } from './mappers/outpatient-treatment-sheet';

import type { EncounterMedicationDispenseSlipRowDto } from '#/api/outpatient-pharmacy-dispense/outpatient-pharmacy-dispense.types';
import type { NurseAdviceExecuteRow } from '#/views/outpatient/nurse-station/components/types';

import { ElMessage } from 'element-plus';

import { httpPageEncounterMedicationDispenseSlips } from '#/api/outpatient-pharmacy-dispense/outpatient-pharmacy-dispense.http';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

import { groupSlipRowsByPrescriptionNo } from './mappers/outpatient-prescription';
import {
  buildExecuteTimeTextForInfusionDay,
  groupInfusionMedicationLinesForPrint,
  resolveInfusionLabelPrintDayCount,
} from './mappers/outpatient-infusion-label';
import { resolveNursePrescriptionPrintTargets } from './nurse-prescription-print';
import { print, PRINT_SCENARIO } from './runtime';

export interface PrintTreatmentSheetOptions {
  encounterId?: null | number | string;
  source: OutpatientTreatmentSheetPrintSource;
  jobName?: string;
}

/** 门诊护士站处置单打印 */
export async function printTreatmentSheet(options: PrintTreatmentSheetOptions) {
  const encounterId = String(options.encounterId ?? '').trim();
  if (!encounterId) {
    ElMessage.warning('请先选择患者');
    return { ok: false as const };
  }
  const lineCount =
    (options.source.serviceLines?.length ?? 0) + (options.source.deviceLines?.length ?? 0);
  if (lineCount <= 0) {
    ElMessage.warning('当前就诊暂无处置项目可打印');
    return { ok: false as const };
  }

  const result = await print({
    scenarioCode: PRINT_SCENARIO.opTreatmentPrint,
    source: options.source,
    bizRef: { encounterId },
    jobName: options.jobName ?? '门诊处置单',
  });

  return result;
}

export interface PrintInfusionLabelOptions {
  encounterId?: null | number | string;
  source: OutpatientInfusionLabelPrintSource;
  jobName?: string;
}

/** 门诊护士站输液瓶签打印（按输液组逐张打印，同组药品在同一张瓶贴；按用药天数逐日打印） */
export async function printInfusionLabel(options: PrintInfusionLabelOptions) {
  const encounterId = String(options.encounterId ?? '').trim();
  if (!encounterId) {
    ElMessage.warning('请先选择患者');
    return { ok: false as const, count: 0 };
  }
  const groups = groupInfusionMedicationLinesForPrint(options.source.medicationLines ?? []);
  if (groups.length <= 0) {
    ElMessage.warning('当前就诊暂无输液药品可打印瓶签');
    return { ok: false as const, count: 0 };
  }

  const baseExecuteTimeText = String(options.source.executeTimeText ?? '').trim();
  let printedCount = 0;
  for (let groupIndex = 0; groupIndex < groups.length; groupIndex += 1) {
    const group = groups[groupIndex]!;
    const dayCount = resolveInfusionLabelPrintDayCount(group.medicationLines);
    const medicationRequestIds = group.medicationLines
      .map((line) => String(line.requestId ?? '').trim())
      .filter((id) => id.length > 0);
    for (let dayIndex = 0; dayIndex < dayCount; dayIndex += 1) {
      const suffixParts: string[] = [];
      if (groups.length > 1) {
        suffixParts.push(`${groupIndex + 1}/${groups.length}`);
      }
      if (dayCount > 1) {
        suffixParts.push(`第${dayIndex + 1}/${dayCount}天`);
      }
      const suffix = suffixParts.length > 0 ? ` (${suffixParts.join('·')})` : '';
      const result = await print({
        scenarioCode: PRINT_SCENARIO.opInfusionLabelPrint,
        source: {
          ...options.source,
          medicationLines: group.medicationLines,
          executeTimeText: buildExecuteTimeTextForInfusionDay(baseExecuteTimeText, dayIndex),
        },
        bizRef: {
          encounterId,
          groupId: group.groupId,
          medicationRequestIds,
          dayIndex: dayIndex + 1,
          dayCount,
        },
        jobName: `${options.jobName ?? '输液瓶签'}${suffix}`,
      });
      if (result.ok) {
        printedCount += 1;
      }
    }
  }

  if (printedCount <= 0) {
    return { ok: false as const, count: 0 };
  }
  if (printedCount > 1) {
    ElMessage.success(`已发送 ${printedCount} 张输液瓶贴`);
  }
  return { ok: true as const, count: printedCount };
}

export interface PrintNursePrescriptionOptions {
  encounterId?: null | number | string;
  medicationRequestIds: string[];
  executeRows: NurseAdviceExecuteRow[];
  jobName?: string;
}

/** 门诊护士站处方单打印（按药品申请主键解析 slip 后逐张打印） */
export async function printNursePrescription(options: PrintNursePrescriptionOptions) {
  const encounterId = String(options.encounterId ?? '').trim();
  if (!encounterId) {
    ElMessage.warning('请先选择患者');
    return { ok: false as const, count: 0 };
  }
  if (!options.medicationRequestIds.length) {
    ElMessage.warning('没有可打印的药品医嘱');
    return { ok: false as const, count: 0 };
  }

  const targets = await resolveNursePrescriptionPrintTargets(
    encounterId,
    options.medicationRequestIds,
    options.executeRows,
  );
  if (!targets.length) {
    ElMessage.warning('没有可打印的处方');
    return { ok: false as const, count: 0 };
  }

  const res = await httpPageEncounterMedicationDispenseSlips({
    encounterId,
    pageNo: 1,
    pageSize: 500,
  });
  const { content: allSlips } =
    parsePagedRowsFromApiData<EncounterMedicationDispenseSlipRowDto>(res);

  let printedCount = 0;
  for (const target of targets) {
    const idSet = new Set(target.medicationRequestIds);
    const slipRows = allSlips.filter((row) => idSet.has(String(row.medReqId ?? '').trim()));
    if (!slipRows.length) {
      continue;
    }
    const groups = groupSlipRowsByPrescriptionNo(slipRows);
    for (const group of groups) {
      const prescriptionNo = group[0]?.prescriptionNo?.trim() ?? '';
      const result = await print({
        scenarioCode: PRINT_SCENARIO.outpatientRx,
        source: group,
        bizRef: {
          encounterId,
          prescriptionNo: prescriptionNo || undefined,
        },
        jobName: options.jobName ?? '护士处方',
      });
      if (result.ok) {
        printedCount += 1;
      }
    }
  }

  if (printedCount <= 0) {
    ElMessage.warning('没有可打印的处方数据');
    return { ok: false as const, count: 0 };
  }
  return { ok: true as const, count: printedCount };
}
