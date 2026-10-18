import type { OutpatientDailySettlementPrintSource } from './mappers/outpatient-daily-settlement';

import { ElMessage } from 'element-plus';

import { isWorkInjuryUiEnabled } from '#/utils/project-optional-modules';

import { resolvePrintHospitalName } from './resolve-print-hospital-name';
import { print, PRINT_SCENARIO } from './runtime';

const WORK_INJURY_CATEGORY_FIELD = 'workInjuryCategoryRows';
const WORK_INJURY_NATURE_FIELD = 'workInjuryNatureRows';
const WORK_INJURY_SECTION_TITLE = '工伤统计（不计入总合计）';
const WORK_INJURY_RULE_TITLE = '工伤分隔线';

/** 合计单且院点开启工伤、窗内有工伤流水时才印工伤块。 */
function shouldPrintWorkInjuryBlock(source: OutpatientDailySettlementPrintSource): boolean {
  if (source.printMode === 'patient') {
    return false;
  }
  if (!isWorkInjuryUiEnabled()) {
    return false;
  }
  return Boolean(source.summary?.workInjurySummary);
}

function readPrintElements(printJson: Record<string, unknown>): Record<string, unknown>[] {
  const panels = printJson.panels;
  if (!Array.isArray(panels) || panels.length === 0) {
    return [];
  }
  const panel = panels[0] as Record<string, unknown>;
  const elements = panel.printElements;
  if (!Array.isArray(elements)) {
    return [];
  }
  return elements as Record<string, unknown>[];
}

function isWorkInjuryPrintElement(element: Record<string, unknown>): boolean {
  const options = element.options as Record<string, unknown> | undefined;
  const field = options?.field;
  if (field === WORK_INJURY_CATEGORY_FIELD || field === WORK_INJURY_NATURE_FIELD) {
    return true;
  }
  if (options?.title === WORK_INJURY_SECTION_TITLE) {
    return true;
  }
  const elementType = element.printElementType as Record<string, unknown> | undefined;
  return elementType?.title === WORK_INJURY_RULE_TITLE;
}

function elementBottom(element: Record<string, unknown>): number {
  const options = element.options as Record<string, unknown> | undefined;
  const top = Number(options?.top ?? 0);
  const height = Number(options?.height ?? 0);
  if (!Number.isFinite(top) || !Number.isFinite(height)) {
    return 0;
  }
  return top + height;
}

/** 库模板尚未含工伤元素时，按红旗日结版式补在内容区下方。 */
function buildWorkInjuryPrintElements(originTop: number): Record<string, unknown>[] {
  const hlineTop = originTop + 10;
  const titleTop = hlineTop + 10;
  const categoryTop = titleTop + 18;
  const natureTop = categoryTop + 48;
  const labelStyler =
    "function(value,row,index,options){return {fontWeight:'600',color:'#222'};}";
  const gridColumns = [1, 2, 3, 4].flatMap((index) => [
    {
      align: 'left',
      field: `label${index}`,
      title: '',
      width: 72,
      styler: labelStyler,
      checked: true,
      colspan: 1,
      rowspan: 1,
      columnId: `label${index}`,
    },
    {
      align: 'right',
      field: `amount${index}`,
      title: '',
      width: 62.65,
      checked: true,
      colspan: 1,
      rowspan: 1,
      columnId: `amount${index}`,
    },
  ]);
  return [
    {
      options: {
        top: hlineTop,
        left: 28.3,
        width: 538.6,
        height: 1,
        borderWidth: 1,
      },
      printElementType: { type: 'hline', title: WORK_INJURY_RULE_TITLE },
    },
    {
      options: {
        top: titleTop,
        left: 28.3,
        title: WORK_INJURY_SECTION_TITLE,
        width: 538.6,
        height: 16,
        fontSize: 10,
        textAlign: 'left',
        fontWeight: 'bold',
        testData: WORK_INJURY_SECTION_TITLE,
      },
      printElementType: { type: 'text', title: '文本' },
    },
    {
      options: {
        top: categoryTop,
        left: 28.3,
        field: WORK_INJURY_CATEGORY_FIELD,
        width: 538.6,
        height: 40,
        columns: [gridColumns],
        fontSize: 9.5,
        hideTitle: true,
        textAlign: 'left',
        tableBorder: 'noBorder',
        tableHeaderBorder: 'noBorder',
        tableHeaderRepeat: 'none',
        tableBodyRowBorder: 'noBorder',
        tableBodyRowHeight: 18,
        tableBodyCellBorder: 'noBorder',
        tableHeaderRowHeight: 0.1,
        tableHeaderCellBorder: 'noBorder',
      },
      printElementType: {
        type: 'table',
        title: WORK_INJURY_CATEGORY_FIELD,
        editable: true,
        columnResizable: true,
      },
    },
    {
      options: {
        top: natureTop,
        left: 28.3,
        field: WORK_INJURY_NATURE_FIELD,
        width: 538.6,
        height: 56,
        columns: [
          [
            {
              align: 'left',
              field: 'name',
              title: '费用性质',
              width: 148,
              checked: true,
              colspan: 1,
              rowspan: 1,
              columnId: 'name',
            },
            {
              align: 'center',
              field: 'personCount',
              title: '人数',
              width: 44,
              checked: true,
              colspan: 1,
              rowspan: 1,
              columnId: 'personCount',
            },
            {
              align: 'right',
              field: 'cashAmount',
              title: '个人自付(元)',
              width: 86.65,
              checked: true,
              colspan: 1,
              rowspan: 1,
              columnId: 'cashAmount',
            },
            {
              align: 'right',
              field: 'fundAmount',
              title: '统筹(元)',
              width: 80.65,
              checked: true,
              colspan: 1,
              rowspan: 1,
              columnId: 'fundAmount',
            },
            {
              align: 'right',
              field: 'accountAmount',
              title: '个人账户(元)',
              width: 86.65,
              checked: true,
              colspan: 1,
              rowspan: 1,
              columnId: 'accountAmount',
            },
            {
              align: 'right',
              field: 'totalAmount',
              title: '总额(元)',
              width: 92.65,
              checked: true,
              colspan: 1,
              rowspan: 1,
              columnId: 'totalAmount',
            },
          ],
        ],
        fontSize: 9.5,
        hideTitle: true,
        textAlign: 'left',
        tableBorder: 'border',
        tableHeaderBorder: 'border',
        tableHeaderRepeat: 'firstPage',
        tableBodyRowBorder: 'border',
        tableBodyRowHeight: 18,
        tableBodyCellBorder: 'border',
        tableHeaderFontSize: 9,
        tableHeaderRowHeight: 22,
        tableHeaderBackground: '#ffffff',
        tableHeaderCellBorder: 'border',
        tableHeaderFontWeight: 'bold',
      },
      printElementType: {
        type: 'table',
        title: WORK_INJURY_NATURE_FIELD,
        editable: true,
        columnResizable: true,
      },
    },
  ];
}

/**
 * 打印前对齐工伤块：无工伤流水时去掉模板里的工伤元素；
 * 库模板还没绑字段时，按当前版式补上，数据仍走 PrintData。
 */
function applyWorkInjuryPrintJson(
  printJson: Record<string, unknown>,
  source: OutpatientDailySettlementPrintSource,
): Record<string, unknown> {
  const panels = printJson.panels;
  if (!Array.isArray(panels) || panels.length === 0) {
    return printJson;
  }
  const panel = panels[0] as Record<string, unknown>;
  const elements = readPrintElements(printJson);
  const kept = elements.filter((element) => !isWorkInjuryPrintElement(element));
  if (!shouldPrintWorkInjuryBlock(source)) {
    panel.printElements = kept;
    return printJson;
  }
  const alreadyBound = elements.some((element) => {
    const options = element.options as Record<string, unknown> | undefined;
    return options?.field === WORK_INJURY_CATEGORY_FIELD;
  });
  if (alreadyBound) {
    return printJson;
  }
  const originTop = kept.reduce((maxTop, element) => Math.max(maxTop, elementBottom(element)), 0);
  panel.printElements = [...kept, ...buildWorkInjuryPrintElements(originTop)];
  return printJson;
}

export interface PrintDailySettlementOptions {
  source: OutpatientDailySettlementPrintSource;
  /** 打印作业业务引用（交账单 settlementId，或预览窗 orgId+日期+收款人） */
  bizRef?: Record<string, unknown>;
  jobName?: string;
}

/** 门诊/住院收费日结单打印（基于当前预览汇总；字段与页面动态同构） */
export async function printDailySettlement(options: PrintDailySettlementOptions) {
  const isPatientMode = options.source.printMode === 'patient';
  if (isPatientMode) {
    if (!options.source.patientSummaries?.length) {
      ElMessage.warning('暂无患者分项可打印');
      return { ok: false as const };
    }
  } else if (!options.source.summary) {
    ElMessage.warning('请先查询日结汇总后再打印');
    return { ok: false as const };
  }

  const scenarioCode = isPatientMode
    ? PRINT_SCENARIO.ipDailySettlementPatientPrint
    : PRINT_SCENARIO.opDailySettlementPrint;

  const result = await print({
    scenarioCode,
    source: {
      hospitalName: options.source.hospitalName?.trim() || resolvePrintHospitalName(),
      documentTitle:
        options.source.documentTitle?.trim() ||
        (isPatientMode ? '住院收费日结单（患者分项）' : '门诊收费日结单'),
      cashierName: options.source.cashierName,
      settlementPeriodText: options.source.settlementPeriodText,
      rows: options.source.rows,
      summary: options.source.summary,
      patientSummaries: options.source.patientSummaries,
      printMode: options.source.printMode,
    },
    bizRef: {
      scenarioCode,
      ...(options.bizRef ?? {}),
    },
    jobName: options.jobName ?? (isPatientMode ? '住院收费日结单（患者分项）' : '门诊收费日结单'),
    transformPrintJson: (printJson) => applyWorkInjuryPrintJson(printJson, options.source),
  });

  return result;
}
