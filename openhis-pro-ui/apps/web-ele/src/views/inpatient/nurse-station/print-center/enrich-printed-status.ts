import type { PrintDocType, PrintPendingRow } from './print-center.types';

import { httpQueryPrintedKeys } from '#/api/print/print-log';
import { PRINT_SCENARIO } from '#/utils/print/constants/print-scenario';

function str(value: unknown): string {
  return String(value ?? '').trim();
}

/** 回查规格：与 print-center-print 写入的 bizRef 主键对齐 */
interface PrintedEnrichSpec {
  scenarioCode: string;
  keyField: string;
  resolveKey: (row: PrintPendingRow) => string;
  /** 过滤 bizRef.queryDate（按日单据） */
  useQueryDate?: boolean;
  /** 过滤 bizRef.dateFrom/dateTo（区间单据） */
  useBizDateRange?: boolean;
}

const ENRICH_BY_DOC: Partial<Record<PrintDocType, PrintedEnrichSpec>> = {
  order_dispense_sheet: {
    scenarioCode: PRINT_SCENARIO.ipOrderDispenseSheetPrint,
    keyField: 'listRowKeys',
    resolveKey: (row) => str(row.listRowKey),
    useQueryDate: true,
  },
  oral_med_sheet: {
    scenarioCode: PRINT_SCENARIO.ipOralMedSheetPrint,
    keyField: 'listRowKeys',
    resolveKey: (row) => str(row.listRowKey),
    useQueryDate: true,
  },
  long_order: {
    scenarioCode: PRINT_SCENARIO.ipLongOrderPrint,
    keyField: 'listRowKeys',
    resolveKey: (row) => str(row.listRowKey) || str(row.id),
  },
  temp_order: {
    scenarioCode: PRINT_SCENARIO.ipTempOrderPrint,
    keyField: 'listRowKeys',
    resolveKey: (row) => str(row.listRowKey) || str(row.id),
  },
  oral_med_label: {
    scenarioCode: PRINT_SCENARIO.ipOralMedLabelPrint,
    keyField: 'listRowKeys',
    resolveKey: (row) => str(row.listRowKey) || str(row.orderId) || str(row.id),
    useQueryDate: true,
  },
  exam_apply: {
    scenarioCode: PRINT_SCENARIO.ipExamApplyPrint,
    keyField: 'serviceRequestIds',
    resolveKey: (row) => str(row.orderId),
  },
  lab_apply: {
    scenarioCode: PRINT_SCENARIO.ipLabApplyPrint,
    keyField: 'serviceRequestIds',
    resolveKey: (row) => str(row.orderId),
  },
  specimen_barcode: {
    scenarioCode: PRINT_SCENARIO.ipSpecimenBarcodePrint,
    keyField: 'serviceRequestIds',
    resolveKey: (row) => str(row.orderId),
  },
  patrol_card: {
    scenarioCode: PRINT_SCENARIO.ipPatrolCardPrint,
    keyField: 'listRowKeys',
    resolveKey: (row) => str(row.listRowKey) || str(row.id),
    useQueryDate: true,
  },
  wristband: {
    scenarioCode: PRINT_SCENARIO.ipWristbandPrint,
    keyField: 'encounterIds',
    resolveKey: (row) => str(row.encounterId),
  },
  bed_card: {
    scenarioCode: PRINT_SCENARIO.ipBedCardPrint,
    keyField: 'encounterIds',
    resolveKey: (row) => str(row.encounterId),
  },
  nursing_record: {
    scenarioCode: PRINT_SCENARIO.ipNursingRecordPrint,
    keyField: 'encounterIds',
    resolveKey: (row) => str(row.encounterId),
    useBizDateRange: true,
  },
  fee_list: {
    scenarioCode: PRINT_SCENARIO.ipFeeListPrint,
    keyField: 'encounterIds',
    resolveKey: (row) => str(row.encounterId),
    useBizDateRange: true,
  },
  fee_detail: {
    scenarioCode: PRINT_SCENARIO.ipFeeDetailPrint,
    keyField: 'encounterIds',
    resolveKey: (row) => str(row.encounterId),
    useBizDateRange: true,
  },
  dispense_summary: {
    scenarioCode: PRINT_SCENARIO.ipDispenseSummaryPrint,
    keyField: 'summaryNos',
    resolveKey: (row) => str(row.summaryNo),
  },
  dispense_sheet: {
    scenarioCode: PRINT_SCENARIO.ipDispenseSheetPrint,
    keyField: 'summaryNos',
    resolveKey: (row) => str(row.summaryNo),
  },
  infusion_label: {
    scenarioCode: PRINT_SCENARIO.ipInfusionLabelPrint,
    keyField: 'listRowKeys',
    resolveKey: (row) => str(row.listRowKey) || str(row.id),
    useQueryDate: true,
  },
};

export interface EnrichPrintedStatusOptions {
  docType: PrintDocType;
  rows: PrintPendingRow[];
  dateFrom?: string;
  dateTo?: string;
}

/**
 * 按审计 SUCCESS 回查，命中则 printStatus=已打印；不改业务 status。
 * 失败降级为未打印，不阻断待打列表。
 */
export async function enrichPrintPendingPrintedStatus(
  options: EnrichPrintedStatusOptions,
): Promise<void> {
  // 先统一落默认打印态，避免与业务 status 混写
  for (const row of options.rows) {
    row.printStatusCode = 'unprinted';
    row.printStatusText = '未打印';
  }
  const spec = ENRICH_BY_DOC[options.docType];
  if (!spec || options.rows.length <= 0) {
    return;
  }
  const keyByRow = new Map<PrintPendingRow, string>();
  const keys: string[] = [];
  for (const row of options.rows) {
    const key = spec.resolveKey(row);
    if (!key) {
      continue;
    }
    keyByRow.set(row, key);
    keys.push(key);
  }
  if (keys.length <= 0) {
    return;
  }
  const queryDate = options.dateFrom || options.dateTo || undefined;
  try {
    const result = await httpQueryPrintedKeys({
      scenarioCode: spec.scenarioCode,
      keyField: spec.keyField,
      keys,
      queryDate: spec.useQueryDate ? queryDate : undefined,
      bizDateFrom: spec.useBizDateRange ? options.dateFrom : undefined,
      bizDateTo: spec.useBizDateRange ? options.dateTo : undefined,
    });
    const printed = new Set((result.printedKeys ?? []).map((key) => str(key)).filter(Boolean));
    if (printed.size <= 0) {
      return;
    }
    for (const [row, key] of keyByRow) {
      if (!printed.has(key)) {
        continue;
      }
      row.printStatusCode = 'printed';
      row.printStatusText = '已打印';
    }
  } catch (error) {
    console.warn(`[print-center] ${options.docType} 已打印回查失败，按未打印展示`, error);
  }
}

/** 输液贴稳定回查键：就诊 + 组 + 查询日 */
export function buildInfusionPrintListRowKey(
  encounterId: string,
  groupKey: string,
  queryDate: string,
): string {
  return `${str(encounterId)}|${str(groupKey)}|${str(queryDate)}`;
}

/** 口服药贴稳定回查键：就诊 + 医嘱 + 查询日 */
export function buildOralMedPrintListRowKey(
  encounterId: string,
  orderKey: string,
  queryDate: string,
): string {
  return `${str(encounterId)}|${str(orderKey)}|${str(queryDate)}`;
}
