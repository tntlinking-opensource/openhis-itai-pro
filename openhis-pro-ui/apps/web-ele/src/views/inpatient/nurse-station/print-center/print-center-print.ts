import type { NurseStationModulePatient } from '../components/nurse-station-module-patient.types';
import type { PrintDocType, PrintPendingRow } from './print-center.types';

import { ElMessage } from 'element-plus';
import dayjs from 'dayjs';

import type { NurseStationCostDetailStatusFilter } from '#/api/inpatient/nurse-station/inpatient-nurse-station-cost-detail.types';
import { httpListPatientAllergyHistory } from '#/api/clinical/patient-allergy-history.http';
import { resolvePrintScenarioLabel } from '#/utils/print/constants/print-scenario-labels';
import { printInfusionLabel } from '#/utils/print/print-nurse-station';
import { print, PRINT_SCENARIO } from '#/utils/print/runtime';
import type { PrintResult } from '#/utils/print/runtime/types';
import {
  groupPrintPendingSpecimenBarcodeRows,
  resolveSpecimenBarcodeLabelCount,
} from '#/utils/print/nurse-station-print-targets';
import { normalizeOralMedSheetPrintJson } from '#/utils/print/normalize-oral-med-sheet-print-json';
import { resolveInfusionLabelPrintTimesPerDay } from '#/utils/print/mappers/outpatient-infusion-label';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import {
  buildDispenseSheetPrintSource,
  buildDispenseSummaryPrintSource,
  buildFeeDetailPrintSource,
  buildFeeListPrintSource,
  buildInfusionPrintSource,
  buildLongOrderPrintSource,
  buildNursingRecordPrintSource,
  buildOralMedLabelPrintSource,
  buildOrderDispenseSheetPrintSource,
  buildOralMedSheetPrintSource,
  buildPatrolCardPrintSource,
  buildBedCardPrintSource,
  buildServiceApplyPrintSources,
  buildTempOrderPrintSource,
  buildWristbandPrintSource,
  resolvePatient,
  type OrderPrintDataSource,
} from './build-print-source';
import { buildInfusionPrintListRowKey } from './enrich-printed-status';

/**
 * 已对接 print() 模板的单据。
 * 体温单暂无打印模板，其余单据均可走真实打印链路。
 */
export const PRINT_CENTER_WIRED_DOC_TYPES: readonly PrintDocType[] = [
  'patrol_card',
  'wristband',
  'bed_card',
  'infusion_label',
  'oral_med_label',
  'exam_apply',
  'lab_apply',
  'specimen_barcode',
  'nursing_record',
  'long_order',
  'temp_order',
  'fee_list',
  'fee_detail',
  'dispense_summary',
  'dispense_sheet',
  'order_dispense_sheet',
  'oral_med_sheet',
] as const;

/** 腕带过敏史：按患者缓存摘要，避免批量打印重复请求 */
async function loadWristbandAllergyText(
  patient: NurseStationModulePatient | undefined,
  cache: Map<string, string>,
): Promise<string> {
  const patientId = preserveApiIdForRequest(patient?.patientId);
  const encounterId = preserveApiIdForRequest(patient?.id);
  if (!patientId || !encounterId) {
    return '未登记';
  }
  const cached = cache.get(patientId);
  if (cached) {
    return cached;
  }
  try {
    const data = await httpListPatientAllergyHistory({ patientId, encounterId });
    const text = String(data?.summaryText ?? '').trim() || '未登记';
    cache.set(patientId, text);
    return text;
  } catch {
    return '未登记';
  }
}

export interface PrintInpatientPrintCenterOptions {
  docType: PrintDocType;
  patients: NurseStationModulePatient[];
  selectedRows: PrintPendingRow[];
  copies?: number;
  dateFrom: string;
  dateTo: string;
  executionDeadline: string;
  /** 费用清单/明细单：与费用明细页「费用状态」一致；未传则待结算 */
  costStatusEnum?: NurseStationCostDetailStatusFilter;
  /**
   * 长期/临时医嘱明细按开立时间排序；true=正序（先开在上）。
   * 仅病案首页临床侧栏传 true；护士站打印中心不传，保持原序。
   */
  orderOpenedTimeAsc?: boolean;
  /**
   * 长/临医嘱取数：nurse_audited=护士已校对（打印中心）；doctor_advice=病案归档（医生站全量 + 已校对合并）。
   */
  orderPrintDataSource?: OrderPrintDataSource;
  /**
   * 输液巡视卡：是否按频次打印（默认 true）。
   * true：同一张卡按每日次数出纸（日两次打两张）；false：每患者一张。
   */
  splitByFrequency?: boolean;
}

async function printOnce(options: {
  scenarioCode: string;
  source: unknown;
  copies?: number;
  jobName?: string;
  bizRef?: Record<string, unknown>;
}): Promise<PrintResult> {
  return print({
    scenarioCode: options.scenarioCode,
    source: options.source,
    copies: options.copies ?? 1,
    jobName: options.jobName ?? resolvePrintScenarioLabel(options.scenarioCode),
    bizRef: options.bizRef,
    transformPrintJson:
      options.scenarioCode === PRINT_SCENARIO.ipOralMedSheetPrint
        ? normalizeOralMedSheetPrintJson
        : undefined,
  });
}

/** 住院护士站打印中心：按单据拉取业务数据后走 print() */
export async function printInpatientPrintCenter(
  options: PrintInpatientPrintCenterOptions,
): Promise<{ ok: boolean; count: number }> {
  const {
    docType,
    patients,
    selectedRows,
    copies = 1,
    dateFrom,
    dateTo,
    executionDeadline,
    costStatusEnum = 'PENDING_SETTLEMENT',
    orderOpenedTimeAsc = false,
    orderPrintDataSource = 'nurse_audited',
    splitByFrequency = true,
  } = options;
  const rows = selectedRows;

  if (docType === 'temp_sheet') {
    ElMessage.warning('体温单打印模板尚未制作，暂不可打印');
    return { ok: false, count: 0 };
  }

  if (!PRINT_CENTER_WIRED_DOC_TYPES.includes(docType)) {
    ElMessage.warning('未知打印单据');
    return { ok: false, count: 0 };
  }

  // 病案归档长/临嘱：可不勾待打行，直接按医生站已开立全量打
  const allowEmptySelection =
    orderPrintDataSource === 'doctor_advice' &&
    (docType === 'long_order' || docType === 'temp_order');
  if (rows.length <= 0 && !allowEmptySelection) {
    ElMessage.warning('请先勾选待打条目');
    return { ok: false, count: 0 };
  }

  try {
    if (docType === 'infusion_label') {
      let printedCount = 0;
      const fromDay = dateFrom || dateTo || dayjs().format('YYYY-MM-DD');
      for (const row of rows) {
        const patient = resolvePatient(patients, row);
        const encounterId = String(patient?.id || row.encounterId || '').trim();
        const groupKey = String(row.groupId || row.orderId || '').trim();
        const listRowKey =
          String(row.listRowKey ?? '').trim() ||
          buildInfusionPrintListRowKey(encounterId, groupKey, fromDay);
        // 查询日取区间起日（与待打列表 loadInfusionPending 同构；跨日区间后续再扩）
        const source = await buildInfusionPrintSource(patients, row, fromDay);
        const result = await printInfusionLabel({
          encounterId: encounterId || row.id,
          source,
          jobName: `输液贴·${row.bedNo}${row.patientName}`,
          scenarioCode: PRINT_SCENARIO.ipInfusionLabelPrint,
          bizRefExtra: {
            queryDate: fromDay,
            listRowKeys: listRowKey ? [listRowKey] : [],
            encounterIds: encounterId ? [encounterId] : [],
          },
        });
        if (result.ok) {
          printedCount += result.count;
        }
      }
      return { ok: printedCount > 0, count: printedCount };
    }

    if (docType === 'patrol_card') {
      let printedCount = 0;
      // 按就诊成卡：勾选组打在同一张巡视卡上，不是一组一张
      for (const encounterRows of groupPatrolRowsByEncounter(rows).values()) {
        const head = encounterRows[0];
        if (!head) {
          continue;
        }
        const encounterId = strBiz(head.encounterId);
        const patient = resolvePatient(patients, head);
        const groupKeys = collectPatrolGroupKeys(encounterRows);
        if (groupKeys.length <= 0) {
          continue;
        }
        const source = await buildPatrolCardPrintSource(
          patients,
          head,
          executionDeadline,
          groupKeys,
        );
        const timesPerDay = splitByFrequency
          ? await resolvePatrolEncounterTimesPerDay(encounterRows)
          : 1;
        const listRowKeys = collectListRowKeys(encounterRows);
        // 按每日次数出纸（日两次打两张同一张卡）
        for (let timeIndex = 0; timeIndex < timesPerDay; timeIndex += 1) {
          const suffix = timesPerDay > 1 ? ` (第${timeIndex + 1}/${timesPerDay}次)` : '';
          const result = await printOnce({
            scenarioCode: PRINT_SCENARIO.ipPatrolCardPrint,
            source,
            copies,
            jobName: `输液巡视卡·${head.bedNo}${head.patientName}${suffix}`,
            bizRef: {
              scenarioCode: PRINT_SCENARIO.ipPatrolCardPrint,
              encounterId: patient?.id ?? encounterId,
              encounterIds: [strBiz(patient?.id ?? encounterId)].filter(Boolean),
              listRowKeys,
              queryDate: resolveQueryDate(dateFrom, dateTo),
              timeIndex: timeIndex + 1,
              timesPerDay,
            },
          });
          if (result.ok) {
            printedCount += 1;
          }
        }
      }
      if (printedCount <= 0) {
        ElMessage.warning('没有可打印的输液巡视卡（需有输液药品）');
      }
      return { ok: printedCount > 0, count: printedCount };
    }

    if (docType === 'wristband') {
      let printedCount = 0;
      const allergyTextByPatientId = new Map<string, string>();
      for (const row of rows) {
        const patient = resolvePatient(patients, row);
        const allergyText = await loadWristbandAllergyText(patient, allergyTextByPatientId);
        const result = await printOnce({
          scenarioCode: PRINT_SCENARIO.ipWristbandPrint,
          source: buildWristbandPrintSource(patients, row, allergyText),
          copies,
          jobName: `腕带·${row.bedNo}${row.patientName}`,
          bizRef: {
            scenarioCode: PRINT_SCENARIO.ipWristbandPrint,
            encounterId: patient?.id ?? row.encounterId,
            encounterIds: [String(patient?.id ?? row.encounterId ?? '').trim()].filter(Boolean),
          },
        });
        if (result.ok) {
          printedCount += 1;
        }
      }
      return { ok: printedCount > 0, count: printedCount };
    }

    if (docType === 'bed_card') {
      let printedCount = 0;
      for (const row of rows) {
        const patient = resolvePatient(patients, row);
        const result = await printOnce({
          scenarioCode: PRINT_SCENARIO.ipBedCardPrint,
          source: buildBedCardPrintSource(patients, row),
          copies,
          jobName: `床头卡·${row.bedNo}${row.patientName}`,
          bizRef: {
            scenarioCode: PRINT_SCENARIO.ipBedCardPrint,
            encounterId: patient?.id ?? row.encounterId,
            encounterIds: [String(patient?.id ?? row.encounterId ?? '').trim()].filter(Boolean),
          },
        });
        if (result.ok) {
          printedCount += 1;
        }
      }
      return { ok: printedCount > 0, count: printedCount };
    }

    if (docType === 'oral_med_label') {
      let printedCount = 0;
      const fromDay = dateFrom || dateTo || dayjs().format('YYYY-MM-DD');
      const toDay = dateTo || fromDay;
      for (const row of rows) {
        const patient = resolvePatient(patients, row);
        const source = await buildOralMedLabelPrintSource(patients, row, fromDay, toDay);
        const listRowKey = String(row.listRowKey || row.orderId || row.id || '').trim();
        const result = await printOnce({
          scenarioCode: PRINT_SCENARIO.ipOralMedLabelPrint,
          source,
          copies,
          jobName: `口服药贴·${row.bedNo}${row.patientName}`,
          bizRef: {
            scenarioCode: PRINT_SCENARIO.ipOralMedLabelPrint,
            encounterId: patient?.id ?? row.encounterId,
            orderId: row.orderId,
            listRowKey,
            listRowKeys: listRowKey ? [listRowKey] : [],
            queryDate: fromDay,
            dateFrom: fromDay,
            dateTo: toDay,
          },
        });
        if (result.ok) {
          printedCount += 1;
        }
      }
      return { ok: printedCount > 0, count: printedCount };
    }

    if (docType === 'exam_apply' || docType === 'lab_apply') {
      const kind = docType === 'exam_apply' ? 'exam' : 'lab';
      const scenarioCode =
        docType === 'exam_apply' ? PRINT_SCENARIO.ipExamApplyPrint : PRINT_SCENARIO.ipLabApplyPrint;
      const jobPrefix = docType === 'exam_apply' ? '住院检查单' : '住院检验单';
      const sources = await buildServiceApplyPrintSources(
        patients,
        rows,
        executionDeadline,
        kind,
        dateFrom,
        dateTo,
      );
      if (sources.length <= 0) {
        ElMessage.warning(`没有可打印的${jobPrefix}`);
        return { ok: false, count: 0 };
      }
      let printedCount = 0;
      for (const item of sources) {
        const serviceRequestIds = collectOrderIds(
          rows.filter((row) => strBiz(row.encounterId) === strBiz(item.encounterId)),
        );
        const result = await printOnce({
          scenarioCode,
          source: item.source,
          copies,
          jobName: `${jobPrefix}·${item.bedNo}${item.patientName}`,
          bizRef: {
            scenarioCode,
            encounterId: item.encounterId,
            serviceRequestIds,
            kind,
          },
        });
        if (result.ok) {
          printedCount += 1;
        }
      }
      return { ok: printedCount > 0, count: printedCount };
    }

    // 采血贴：住院模板 ip-specimenBarcode-print；合管 device 一组；张数=开立数量×份数
    if (docType === 'specimen_barcode') {
      let printedCount = 0;
      const groupedTargets = groupPrintPendingSpecimenBarcodeRows(rows);
      for (const target of groupedTargets) {
        const anchorRow =
          rows.find((row) => String(row.orderId ?? '').trim() === target.serviceRequestId) ??
          rows.find((row) => String(row.encounterId ?? '').trim() === target.encounterId);
        const patient = anchorRow ? resolvePatient(patients, anchorRow) : undefined;
        const encounterId = String(
          target.encounterId ?? patient?.id ?? anchorRow?.encounterId ?? '',
        ).trim();
        const serviceRequestId = String(target.serviceRequestId ?? '').trim();
        if (!encounterId || !serviceRequestId) {
          continue;
        }
        const labelCount = resolveSpecimenBarcodeLabelCount(target.labelCount);
        const labelCopies = labelCount * Math.max(1, copies);
        const result = await printOnce({
          scenarioCode: PRINT_SCENARIO.ipSpecimenBarcodePrint,
          source: {
            bedNo: String(patient?.bedNo ?? target.bedNo ?? '').trim(),
            patientName: String(patient?.name ?? target.patientName ?? '').trim(),
            genderEnumText: String(patient?.gender ?? '').trim(),
            ageText: patient?.age == null ? '' : `${patient.age}岁`,
            itemName: String(target.itemName ?? '').trim(),
            serviceRequestId,
            priorityEnum: String(target.priorityEnum ?? '').trim() || undefined,
          },
          copies: labelCopies,
          jobName: `采血贴·${target.bedNo ?? ''}${target.patientName ?? ''}`,
          bizRef: {
            scenarioCode: PRINT_SCENARIO.ipSpecimenBarcodePrint,
            encounterId,
            serviceRequestId,
            serviceRequestIds: [serviceRequestId],
          },
        });
        if (result.ok) {
          printedCount += labelCopies;
        }
      }
      if (printedCount <= 0) {
        ElMessage.warning('没有可打印的采血贴');
      }
      return { ok: printedCount > 0, count: printedCount };
    }

    if (docType === 'nursing_record') {
      const source = await buildNursingRecordPrintSource(patients, dateFrom, dateTo);
      const result = await printOnce({
        scenarioCode: PRINT_SCENARIO.ipNursingRecordPrint,
        source,
        copies,
        bizRef: {
          scenarioCode: PRINT_SCENARIO.ipNursingRecordPrint,
          encounterId: patients[0]?.id,
          encounterIds: [String(patients[0]?.id ?? '').trim()].filter(Boolean),
          dateFrom,
          dateTo,
        },
      });
      return { ok: result.ok, count: result.ok ? 1 : 0 };
    }

    if (docType === 'fee_list') {
      const source = await buildFeeListPrintSource(patients, dateFrom, dateTo, costStatusEnum);
      if ((source.feeRows ?? []).length <= 0) {
        ElMessage.warning('没有可打印的费用清单');
        return { ok: false, count: 0 };
      }
      const result = await printOnce({
        scenarioCode: PRINT_SCENARIO.ipFeeListPrint,
        source,
        copies,
        bizRef: {
          scenarioCode: PRINT_SCENARIO.ipFeeListPrint,
          encounterId: patients[0]?.id,
          encounterIds: [String(patients[0]?.id ?? '').trim()].filter(Boolean),
          dateFrom,
          dateTo,
        },
      });
      return { ok: result.ok, count: result.ok ? 1 : 0 };
    }

    if (docType === 'fee_detail') {
      const source = await buildFeeDetailPrintSource(patients, dateFrom, dateTo, costStatusEnum);
      if ((source.feeRows ?? []).length <= 0) {
        ElMessage.warning('没有可打印的费用明细');
        return { ok: false, count: 0 };
      }
      const result = await printOnce({
        scenarioCode: PRINT_SCENARIO.ipFeeDetailPrint,
        source,
        copies,
        bizRef: {
          scenarioCode: PRINT_SCENARIO.ipFeeDetailPrint,
          encounterId: patients[0]?.id,
          encounterIds: [String(patients[0]?.id ?? '').trim()].filter(Boolean),
          dateFrom,
          dateTo,
        },
      });
      return { ok: result.ok, count: result.ok ? 1 : 0 };
    }

    if (docType === 'temp_order') {
      const source = await buildTempOrderPrintSource(
        patients,
        rows,
        executionDeadline,
        dateFrom,
        dateTo,
        orderOpenedTimeAsc,
        orderPrintDataSource,
      );
      if (source.orderRows.length <= 0) {
        ElMessage.warning('没有可打印的临时医嘱');
        return { ok: false, count: 0 };
      }
      const result = await printOnce({
        scenarioCode: PRINT_SCENARIO.ipTempOrderPrint,
        source,
        copies,
        bizRef: {
          scenarioCode: PRINT_SCENARIO.ipTempOrderPrint,
          encounterIds: collectEncounterIds(rows),
          listRowKeys: collectListRowKeys(rows),
          dateFrom,
          dateTo,
        },
      });
      return { ok: result.ok, count: result.ok ? 1 : 0 };
    }

    if (docType === 'long_order') {
      const source = await buildLongOrderPrintSource(
        patients,
        rows,
        executionDeadline,
        dateFrom,
        dateTo,
        orderOpenedTimeAsc,
        orderPrintDataSource,
      );
      if (source.orderRows.length <= 0) {
        ElMessage.warning('没有可打印的长期医嘱');
        return { ok: false, count: 0 };
      }
      const result = await printOnce({
        scenarioCode: PRINT_SCENARIO.ipLongOrderPrint,
        source,
        copies,
        jobName: '长期医嘱',
        bizRef: {
          scenarioCode: PRINT_SCENARIO.ipLongOrderPrint,
          encounterIds: collectEncounterIds(rows),
          listRowKeys: collectListRowKeys(rows),
          therapyType: 'long',
          dateFrom,
          dateTo,
        },
      });
      return { ok: result.ok, count: result.ok ? 1 : 0 };
    }

    if (docType === 'dispense_summary') {
      let okCount = 0;
      for (const row of rows) {
        const summaryNo = strSummaryNo(row);
        if (!summaryNo) {
          continue;
        }
        const source = await buildDispenseSummaryPrintSource(summaryNo);
        const result = await printOnce({
          scenarioCode: PRINT_SCENARIO.ipDispenseSummaryPrint,
          source,
          copies,
          jobName: row.title,
          bizRef: {
            scenarioCode: PRINT_SCENARIO.ipDispenseSummaryPrint,
            summaryNo,
            summaryNos: [summaryNo],
          },
        });
        if (result.ok) {
          okCount += 1;
        }
      }
      return { ok: okCount > 0, count: okCount };
    }

    if (docType === 'dispense_sheet') {
      let okCount = 0;
      for (const row of rows) {
        const summaryNo = strSummaryNo(row);
        if (!summaryNo) {
          continue;
        }
        const source = await buildDispenseSheetPrintSource(summaryNo);
        const result = await printOnce({
          scenarioCode: PRINT_SCENARIO.ipDispenseSheetPrint,
          source,
          copies,
          jobName: row.title,
          bizRef: {
            scenarioCode: PRINT_SCENARIO.ipDispenseSheetPrint,
            summaryNo,
            summaryNos: [summaryNo],
          },
        });
        if (result.ok) {
          okCount += 1;
        }
      }
      return { ok: okCount > 0, count: okCount };
    }

    if (docType === 'order_dispense_sheet') {
      // 一张连续多人：勾选药品按患者分块排在同一 PrintData（含口服）
      const fromDay = dateFrom || dateTo || dayjs().format('YYYY-MM-DD');
      const source = await buildOrderDispenseSheetPrintSource(patients, rows, fromDay);
      if ((source.patientBlocks ?? []).length <= 0) {
        ElMessage.warning('没有可打印的已执行药品');
        return { ok: false, count: 0 };
      }
      const listRowKeys = collectListRowKeys(rows);
      const encounterIds = collectEncounterIds(rows);
      const result = await printOnce({
        scenarioCode: PRINT_SCENARIO.ipOrderDispenseSheetPrint,
        source,
        copies,
        jobName: `摆药单·${fromDay}`,
        bizRef: {
          scenarioCode: PRINT_SCENARIO.ipOrderDispenseSheetPrint,
          queryDate: fromDay,
          listRowKeys,
          encounterIds,
        },
      });
      return { ok: result.ok, count: result.ok ? 1 : 0 };
    }

    if (docType === 'oral_med_sheet') {
      // 一张连续多人：勾选药品按患者分块排在同一 PrintData（每块含患者签字）
      const fromDay = dateFrom || dateTo || dayjs().format('YYYY-MM-DD');
      const source = await buildOralMedSheetPrintSource(patients, rows, fromDay);
      if ((source.patientBlocks ?? []).length <= 0) {
        ElMessage.warning('没有可打印的已执行药品');
        return { ok: false, count: 0 };
      }
      const listRowKeys = collectListRowKeys(rows);
      const encounterIds = collectEncounterIds(rows);
      const result = await printOnce({
        scenarioCode: PRINT_SCENARIO.ipOralMedSheetPrint,
        source,
        copies,
        jobName: `患者用药单·${fromDay}`,
        bizRef: {
          scenarioCode: PRINT_SCENARIO.ipOralMedSheetPrint,
          queryDate: fromDay,
          listRowKeys,
          encounterIds,
        },
      });
      return { ok: result.ok, count: result.ok ? 1 : 0 };
    }
  } catch (error) {
    const message = error instanceof Error ? error.message : '打印数据准备失败';
    ElMessage.error(message);
    return { ok: false, count: 0 };
  }

  return { ok: false, count: 0 };
}

function strSummaryNo(row: PrintPendingRow): string {
  return String(row.summaryNo ?? '').trim();
}

function strBiz(value: unknown): string {
  return String(value ?? '').trim();
}

/** 合打审计：收集稳定 listRowKey */
function collectListRowKeys(rows: PrintPendingRow[]): string[] {
  return rows.map((row) => strBiz(row.listRowKey)).filter((key) => key.length > 0);
}

function collectEncounterIds(rows: PrintPendingRow[]): string[] {
  return [...new Set(rows.map((row) => strBiz(row.encounterId)).filter((id) => id.length > 0))];
}

/** 检查/检验：orderId 即 serviceRequestId */
function collectOrderIds(rows: PrintPendingRow[]): string[] {
  return [...new Set(rows.map((row) => strBiz(row.orderId)).filter((id) => id.length > 0))];
}

function resolveQueryDate(dateFrom: string, dateTo: string): string {
  return dateFrom || dateTo || dayjs().format('YYYY-MM-DD');
}

/** 巡视卡勾选行按就诊归并（同一患者合成一张卡） */
function groupPatrolRowsByEncounter(rows: PrintPendingRow[]): Map<string, PrintPendingRow[]> {
  const byEncounter = new Map<string, PrintPendingRow[]>();
  for (const row of rows) {
    const encounterId = strBiz(row.encounterId);
    if (!encounterId) {
      continue;
    }
    const existing = byEncounter.get(encounterId);
    if (!existing) {
      byEncounter.set(encounterId, [row]);
      continue;
    }
    existing.push(row);
  }
  return byEncounter;
}

/** 巡视卡勾选组键（与待打行 groupId 对齐） */
function collectPatrolGroupKeys(rows: PrintPendingRow[]): string[] {
  const keys: string[] = [];
  const seen = new Set<string>();
  for (const row of rows) {
    const key = strBiz(row.groupId) || strBiz(row.orderId);
    if (!key || seen.has(key)) {
      continue;
    }
    seen.add(key);
    keys.push(key);
  }
  return keys;
}

/** 巡视卡按就诊成卡后的每日张数：勾选组里取最大频次 */
async function resolvePatrolEncounterTimesPerDay(rows: PrintPendingRow[]): Promise<number> {
  let maxTimes = 1;
  for (const row of rows) {
    const times = await resolvePatrolPrintTimesPerDay(row);
    if (times > maxTimes) {
      maxTimes = times;
    }
  }
  return maxTimes;
}

/** 巡视卡每日次数：优先 rateCode（与输液贴同源），否则当日槽位 */
async function resolvePatrolPrintTimesPerDay(row: PrintPendingRow): Promise<number> {
  const rateCode = strBiz(row.rateCode);
  if (rateCode) {
    const times = await resolveInfusionLabelPrintTimesPerDay([
      { rateCode } as Parameters<typeof resolveInfusionLabelPrintTimesPerDay>[0][number],
    ]);
    if (times > 0) {
      return times;
    }
  }
  const slots = row.daySlotCount ?? 0;
  return slots > 0 ? slots : 1;
}
