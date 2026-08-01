import type { NurseStationModulePatient } from '../components/nurse-station-module-patient.types';
import type { PrintDocType, PrintPendingRow } from './print-center.types';

import { ElMessage } from 'element-plus';
import dayjs from 'dayjs';

import { resolvePrintScenarioLabel } from '#/utils/print/constants/print-scenario-labels';
import { printInfusionLabel } from '#/utils/print/print-nurse-station';
import { print, PRINT_SCENARIO } from '#/utils/print/runtime';
import type { PrintResult } from '#/utils/print/runtime/types';

import {
  buildDispenseSheetPrintSource,
  buildDispenseSummaryPrintSource,
  buildFeeListPrintSource,
  buildInfusionPrintSource,
  buildLongOrderPrintSource,
  buildNursingRecordPrintSource,
  buildOralMedLabelPrintSource,
  buildPatrolCardPrintSource,
  buildServiceApplyPrintSources,
  buildTempOrderPrintSource,
  buildWristbandPrintSource,
  resolvePatient,
} from './build-print-source';

/**
 * 已对接 print() 模板的单据。
 * 体温单暂无打印模板，其余单据均可走真实打印链路。
 */
export const PRINT_CENTER_WIRED_DOC_TYPES: readonly PrintDocType[] = [
  'patrol_card',
  'wristband',
  'infusion_label',
  'oral_med_label',
  'exam_apply',
  'lab_apply',
  'nursing_record',
  'long_order',
  'temp_order',
  'fee_list',
  'dispense_summary',
  'dispense_sheet',
] as const;

export interface PrintInpatientPrintCenterOptions {
  docType: PrintDocType;
  patients: NurseStationModulePatient[];
  selectedRows: PrintPendingRow[];
  copies?: number;
  dateFrom: string;
  dateTo: string;
  executionDeadline: string;
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

  if (rows.length <= 0) {
    ElMessage.warning('请先勾选待打条目');
    return { ok: false, count: 0 };
  }

  try {
    if (docType === 'infusion_label') {
      let printedCount = 0;
      // 输液贴按单日查询：dateFrom 即查询日
      const queryDate = dateFrom || dateTo || dayjs().format('YYYY-MM-DD');
      for (const row of rows) {
        const patient = resolvePatient(patients, row);
        const source = await buildInfusionPrintSource(patients, row, queryDate);
        const result = await printInfusionLabel({
          encounterId: patient?.id || row.encounterId || row.id,
          source,
          jobName: `输液贴·${row.bedNo}${row.patientName}`,
          scenarioCode: PRINT_SCENARIO.ipInfusionLabelPrint,
        });
        if (result.ok) {
          printedCount += result.count;
        }
      }
      return { ok: printedCount > 0, count: printedCount };
    }

    if (docType === 'patrol_card') {
      let printedCount = 0;
      for (const row of rows) {
        const patient = resolvePatient(patients, row);
        const source = await buildPatrolCardPrintSource(patients, row, executionDeadline);
        const result = await printOnce({
          scenarioCode: PRINT_SCENARIO.ipPatrolCardPrint,
          source,
          copies,
          jobName: `输液巡视卡·${row.bedNo}${row.patientName}`,
          bizRef: { encounterId: patient?.id, bedNo: row.bedNo },
        });
        if (result.ok) {
          printedCount += 1;
        }
      }
      if (printedCount <= 0) {
        ElMessage.warning('没有可打印的输液巡视卡（需有输液药品）');
      }
      return { ok: printedCount > 0, count: printedCount };
    }

    if (docType === 'wristband') {
      let printedCount = 0;
      for (const row of rows) {
        const patient = resolvePatient(patients, row);
        const result = await printOnce({
          scenarioCode: PRINT_SCENARIO.ipWristbandPrint,
          source: buildWristbandPrintSource(patients, row),
          copies,
          jobName: `腕带·${row.bedNo}${row.patientName}`,
          bizRef: { encounterId: patient?.id, bedNo: row.bedNo },
        });
        if (result.ok) {
          printedCount += 1;
        }
      }
      return { ok: printedCount > 0, count: printedCount };
    }

    if (docType === 'oral_med_label') {
      let printedCount = 0;
      for (const row of rows) {
        const patient = resolvePatient(patients, row);
        const source = await buildOralMedLabelPrintSource(patients, row, executionDeadline);
        const result = await printOnce({
          scenarioCode: PRINT_SCENARIO.ipOralMedLabelPrint,
          source,
          copies,
          jobName: `口服药贴·${row.bedNo}${row.patientName}`,
          bizRef: { encounterId: patient?.id, orderId: row.orderId },
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
      const sources = await buildServiceApplyPrintSources(patients, rows, executionDeadline, kind);
      if (sources.length <= 0) {
        ElMessage.warning(`没有可打印的${jobPrefix}`);
        return { ok: false, count: 0 };
      }
      let printedCount = 0;
      for (const item of sources) {
        const result = await printOnce({
          scenarioCode,
          source: item.source,
          copies,
          jobName: `${jobPrefix}·${item.bedNo}${item.patientName}`,
          bizRef: { encounterId: item.encounterId, kind },
        });
        if (result.ok) {
          printedCount += 1;
        }
      }
      return { ok: printedCount > 0, count: printedCount };
    }

    if (docType === 'nursing_record') {
      const source = await buildNursingRecordPrintSource(patients, dateFrom, dateTo);
      const result = await printOnce({
        scenarioCode: PRINT_SCENARIO.ipNursingRecordPrint,
        source,
        copies,
        bizRef: { encounterId: patients[0]?.id },
      });
      return { ok: result.ok, count: result.ok ? 1 : 0 };
    }

    if (docType === 'fee_list') {
      const source = await buildFeeListPrintSource(patients, dateFrom, dateTo);
      const result = await printOnce({
        scenarioCode: PRINT_SCENARIO.ipFeeListPrint,
        source,
        copies,
        bizRef: { encounterId: patients[0]?.id },
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
      );
      if (source.orderRows.length <= 0) {
        ElMessage.warning('没有可打印的临时医嘱');
        return { ok: false, count: 0 };
      }
      const result = await printOnce({
        scenarioCode: PRINT_SCENARIO.ipTempOrderPrint,
        source,
        copies,
        bizRef: { encounterId: patients[0]?.id },
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
      );
      if (source.orderRows.length <= 0) {
        ElMessage.warning('没有可打印的长期医嘱');
        return { ok: false, count: 0 };
      }
      const result = await printOnce({
        scenarioCode: PRINT_SCENARIO.ipLongOrderPrint,
        source,
        copies,
        jobName: '长嘱执行记录',
        bizRef: { encounterId: patients[0]?.id, therapyType: 'long' },
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
          bizRef: { summaryNo },
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
          bizRef: { summaryNo },
        });
        if (result.ok) {
          okCount += 1;
        }
      }
      return { ok: okCount > 0, count: okCount };
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
