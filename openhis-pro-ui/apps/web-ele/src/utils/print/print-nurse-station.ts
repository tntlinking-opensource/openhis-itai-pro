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
  resolveInfusionLabelPrintTimesPerDay,
} from './mappers/outpatient-infusion-label';
import {
  normalizeIpInfusionLabelPrintJson,
  rebindOutpatientInfusionLabelDoseColumn,
} from './normalize-ip-infusion-label-print-json';
import { normalizeIpSpecimenBarcodePrintJson } from './normalize-ip-specimen-barcode-print-json';
import { normalizeOpSpecimenBarcodePrintJson } from './normalize-op-specimen-barcode-print-json';
import { resolveNursePrescriptionPrintTargets } from './nurse-prescription-print';
import { resolveSpecimenBarcodeLabelCount } from './nurse-station-print-targets';
import { resolveSpecimenBarcodeUrgencyText } from './constants/service-request-priority';
import { normalizeSpecimenBarcodePrintJson } from './normalize-specimen-barcode-print-json';
import { print, PRINT_SCENARIO } from './runtime';
import { resolveOutpatientPrescriptionPrintScenario } from './resolve-outpatient-prescription-scenario';

function strId(value: null | number | string | undefined): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value).trim();
}

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
    bizRef: {
      scenarioCode: PRINT_SCENARIO.opTreatmentPrint,
      encounterId,
    },
    jobName: options.jobName ?? '门诊处置单',
  });

  return result;
}

export interface PrintInfusionLabelOptions {
  encounterId?: null | number | string;
  source: OutpatientInfusionLabelPrintSource;
  jobName?: string;
  /** 默认门诊瓶签；住院护士站请传 PRINT_SCENARIO.ipInfusionLabelPrint */
  scenarioCode?: string;
  /** 合并进每张瓶签 bizRef（如打印中心 listRowKeys） */
  bizRefExtra?: Record<string, unknown>;
  /** 显式指定打印机时跳过选机弹窗 */
  printer?: string;
}

/**
 * 护士站输液瓶签打印：
 * - 按输液组逐张（同组药品同一张）
 * - 按用药天数逐日
 * - 按用药频次每日次数拆张（门诊/住院相同；如 BID 日两次 → 当日 2 张）
 * - 有发药总量时按「天数×次数」整除到每张；住院无总量时每张印每次剂量
 */
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

  const scenarioCode =
    String(options.scenarioCode ?? '').trim() || PRINT_SCENARIO.opInfusionLabelPrint;
  const baseExecuteTimeText = String(options.source.executeTimeText ?? '').trim();
  // 入参可覆盖每日次数（住院槽位回落）；否则按组内 rateCode 解析
  const timesPerDayOverride = (() => {
    const raw = options.source.timesPerDay;
    if (raw === null || raw === undefined || String(raw).trim() === '') {
      return 0;
    }
    const n = typeof raw === 'number' ? raw : Number(String(raw).trim());
    if (!Number.isFinite(n) || n <= 0) {
      return 0;
    }
    return Math.trunc(n);
  })();
  let printedCount = 0;
  for (let groupIndex = 0; groupIndex < groups.length; groupIndex += 1) {
    const group = groups[groupIndex]!;
    const dayCount = resolveInfusionLabelPrintDayCount(group.medicationLines);
    const timesPerDay =
      timesPerDayOverride > 0
        ? timesPerDayOverride
        : await resolveInfusionLabelPrintTimesPerDay(group.medicationLines);
    const quantitySplitCount = dayCount * timesPerDay;
    const medicationRequestIds = group.medicationLines
      .map((line) => String(line.requestId ?? '').trim())
      .filter((id) => id.length > 0);
    for (let dayIndex = 0; dayIndex < dayCount; dayIndex += 1) {
      for (let timeIndex = 0; timeIndex < timesPerDay; timeIndex += 1) {
        const suffixParts: string[] = [];
        if (groups.length > 1) {
          suffixParts.push(`${groupIndex + 1}/${groups.length}`);
        }
        if (dayCount > 1) {
          suffixParts.push(`第${dayIndex + 1}/${dayCount}天`);
        }
        if (timesPerDay > 1) {
          suffixParts.push(`第${timeIndex + 1}/${timesPerDay}次`);
        }
        const suffix = suffixParts.length > 0 ? ` (${suffixParts.join('·')})` : '';
        const result = await print({
          scenarioCode,
          source: {
            ...options.source,
            medicationLines: group.medicationLines,
            executeTimeText: buildExecuteTimeTextForInfusionDay(baseExecuteTimeText, dayIndex),
            quantitySplitCount,
          },
          // 住院：国基才收紧贴面，红旗沿用库模板；门诊只把剂量列改成医生开的计量单位
          transformPrintJson:
            scenarioCode === PRINT_SCENARIO.ipInfusionLabelPrint
              ? normalizeIpInfusionLabelPrintJson
              : rebindOutpatientInfusionLabelDoseColumn,
          bizRef: {
            scenarioCode,
            encounterId,
            groupId: group.groupId,
            medicationRequestIds,
            dayIndex: dayIndex + 1,
            dayCount,
            timeIndex: timeIndex + 1,
            timesPerDay,
            quantitySplitCount,
            ...(options.bizRefExtra ?? {}),
          },
          jobName: `${options.jobName ?? '输液瓶签'}${suffix}`,
          printer: options.printer,
          selectPrinter: options.printer?.trim() ? 'never' : undefined,
        });
        if (result.ok) {
          printedCount += 1;
        }
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

export interface PrintSpecimenBarcodeItem {
  /** 检验申请主键（bizRef / 回落条码；string） */
  serviceRequestId: string;
  /**
   * 条码内容（与 LIS applyNo 一致：SR.busNo）；空则回落 serviceRequestId。
   */
  barcode?: string;
  /** 检验项目名称 */
  itemName?: string;
  /** 开立数量对应贴数（如「2次」→ 2）；缺省 1 */
  labelCount?: number;
  /** 医嘱优先级 code（EM=急） */
  priorityEnum?: string;
  /**
   * 合管组内全部申请主键；有值时写入 bizRef.serviceRequestIds，
   * 避免只记锚点导致组内其余行仍显示未打。
   */
  memberServiceRequestIds?: string[];
}

export interface PrintSpecimenBarcodeOptions {
  encounterId?: null | number | string;
  patientName?: string;
  genderEnumText?: string;
  ageText?: string;
  /** 住院采血贴床号；门诊可空 */
  bedNo?: string;
  /** 检验项目列表，每张贴一个条码；同条可按 labelCount 打多份 */
  items: PrintSpecimenBarcodeItem[];
  jobName?: string;
  /** 默认门诊采血贴；住院护士站请传 PRINT_SCENARIO.ipSpecimenBarcodePrint */
  scenarioCode?: string;
}

/** 采血贴打印前改模板：住院放大条码，门诊项目名不折行 */
function resolveSpecimenBarcodePrintJson(scenarioCode: string) {
  if (scenarioCode === PRINT_SCENARIO.ipSpecimenBarcodePrint) {
    return normalizeIpSpecimenBarcodePrintJson;
  }
  if (scenarioCode === PRINT_SCENARIO.opSpecimenBarcodePrint) {
    return normalizeOpSpecimenBarcodePrintJson;
  }
  return undefined;
}

/** 护士站采血贴：按检验项目主键逐张打印；开立数量 N 打 N 份 */
export async function printSpecimenBarcode(options: PrintSpecimenBarcodeOptions) {
  const encounterId = strId(options.encounterId);
  if (!encounterId) {
    ElMessage.warning('请先选择患者');
    return { ok: false as const, count: 0 };
  }

  // 去重并保持顺序；主键一律 string，避免雪花 Long 精度丢失
  const items: PrintSpecimenBarcodeItem[] = [];
  const seen = new Set<string>();
  for (const raw of options.items ?? []) {
    const serviceRequestId = strId(raw.serviceRequestId);
    if (!serviceRequestId || seen.has(serviceRequestId)) {
      continue;
    }
    seen.add(serviceRequestId);
    const explicitBarcode = strId(raw.barcode);
    // 无 busNo 时回落主键：与 LIS applyNo=SR.busNo 可能不一致，打印仍发出但打日志便于排查
    if (!explicitBarcode) {
      console.warn(
        '[print] 采血贴缺少 busNo，条码回落 serviceRequestId，可能与 LIS applyNo 不一致',
        serviceRequestId,
      );
    }
    const barcode = explicitBarcode || serviceRequestId;
    const memberServiceRequestIds = (raw.memberServiceRequestIds ?? [])
      .map((id) => strId(id))
      .filter(Boolean);
    if (!memberServiceRequestIds.includes(serviceRequestId)) {
      memberServiceRequestIds.unshift(serviceRequestId);
    }
    items.push({
      serviceRequestId,
      barcode,
      itemName: strId(raw.itemName),
      labelCount: resolveSpecimenBarcodeLabelCount(raw.labelCount),
      priorityEnum: strId(raw.priorityEnum) || undefined,
      memberServiceRequestIds,
    });
  }
  if (!items.length) {
    ElMessage.warning('没有可打印的采血/检验医嘱');
    return { ok: false as const, count: 0 };
  }

  const scenarioCode =
    String(options.scenarioCode ?? '').trim() || PRINT_SCENARIO.opSpecimenBarcodePrint;
  const patientName = strId(options.patientName);
  const genderEnumText = strId(options.genderEnumText);
  const ageText = strId(options.ageText);
  const bedNo = strId(options.bedNo);

  let printedCount = 0;
  for (let index = 0; index < items.length; index += 1) {
    const item = items[index]!;
    const labelCount = resolveSpecimenBarcodeLabelCount(item.labelCount);
    const suffix = items.length > 1 ? ` (${index + 1}/${items.length})` : '';
    const serviceRequestIds =
      item.memberServiceRequestIds && item.memberServiceRequestIds.length > 0
        ? item.memberServiceRequestIds
        : [item.serviceRequestId];
    const result = await print({
      scenarioCode,
      source: {
        bedNo,
        patientName,
        genderEnumText,
        ageText,
        itemName: item.itemName,
        serviceRequestId: item.serviceRequestId,
        barcode: item.barcode || item.serviceRequestId,
        priorityEnum: item.priorityEnum,
        urgencyText: resolveSpecimenBarcodeUrgencyText(item.priorityEnum),
      },
      copies: labelCount,
      bizRef: {
        scenarioCode,
        encounterId,
        serviceRequestId: item.serviceRequestId,
        serviceRequestIds,
      },
      jobName: `${options.jobName ?? '采血贴'}${suffix}`,
      // 国基才改采血贴 JSON；红旗沿用库模板
      transformPrintJson: (printJson) => {
        const next = normalizeSpecimenBarcodePrintJson(printJson);
        const extra = resolveSpecimenBarcodePrintJson(scenarioCode);
        return extra ? extra(next) : next;
      },
    });
    if (result.ok) {
      printedCount += labelCount;
    }
  }

  if (printedCount <= 0) {
    return { ok: false as const, count: 0 };
  }
  // 多条检验一次打完时汇总提示；单条（含按数量多份）由调用方提示，避免双 toast
  if (items.length > 1) {
    ElMessage.success(`已发送 ${printedCount} 张采血贴`);
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
        scenarioCode: resolveOutpatientPrescriptionPrintScenario(group),
        source: group,
        bizRef: {
          scenarioCode: resolveOutpatientPrescriptionPrintScenario(group),
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
