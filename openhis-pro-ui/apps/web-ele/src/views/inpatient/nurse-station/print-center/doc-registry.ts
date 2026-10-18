import type { PrintDocType, PrintNavCategory, PrintPendingRow } from './print-center.types';

import { PRINT_SCENARIO } from '#/utils/print/constants/print-scenario';
import {
  SERVICE_CATEGORY_ENTRUST,
  SERVICE_CATEGORY_PROCEDURE,
} from '#/utils/print/constants/service-request-category-codes';
import { INPATIENT_SURGERY_ANESTH_ENABLED } from '#/views/inpatient/surgery-anesth/inpatient-surgery-anesth.config';

import { findPrintDocType, PRINT_DOC_TYPES, PRINT_NAV_CATEGORIES } from './print-center.types';

/** 打印中心单据 → 打印机偏好场景码（体温单无模板，不绑定） */
const PRINT_CENTER_SCENARIO_BY_DOC: Partial<Record<PrintDocType, string>> = {
  patrol_card: PRINT_SCENARIO.ipPatrolCardPrint,
  wristband: PRINT_SCENARIO.ipWristbandPrint,
  bed_card: PRINT_SCENARIO.ipBedCardPrint,
  infusion_label: PRINT_SCENARIO.ipInfusionLabelPrint,
  oral_med_label: PRINT_SCENARIO.ipOralMedLabelPrint,
  exam_apply: PRINT_SCENARIO.ipExamApplyPrint,
  lab_apply: PRINT_SCENARIO.ipLabApplyPrint,
  specimen_barcode: PRINT_SCENARIO.ipSpecimenBarcodePrint,
  nursing_record: PRINT_SCENARIO.ipNursingRecordPrint,
  long_order: PRINT_SCENARIO.ipLongOrderPrint,
  temp_order: PRINT_SCENARIO.ipTempOrderPrint,
  fee_list: PRINT_SCENARIO.ipFeeListPrint,
  fee_detail: PRINT_SCENARIO.ipFeeDetailPrint,
  dispense_summary: PRINT_SCENARIO.ipDispenseSummaryPrint,
  dispense_sheet: PRINT_SCENARIO.ipDispenseSheetPrint,
  order_dispense_sheet: PRINT_SCENARIO.ipOrderDispenseSheetPrint,
  oral_med_sheet: PRINT_SCENARIO.ipOralMedSheetPrint,
};

/** 单据对应的打印机缓存场景码；未对接模板时返回空串 */
export function resolvePrintCenterScenarioCode(docType: PrintDocType): string {
  return PRINT_CENTER_SCENARIO_BY_DOC[docType] ?? '';
}

/**
 * 临时医嘱内容筛选：全部 / 手术 / 麻醉 / 嘱托（code，禁止用展示文案分支）。
 * 与手麻开关同组展示；手麻关闭时仅用 OrderEntrustFilter。
 */
export type TempOrderSurgeryAnesthFilter = 'all' | 'anesthesia' | 'entrust' | 'surgery';

/** 长期医嘱（及手麻关闭时的临嘱）嘱托筛选：全部 / 嘱托 */
export type OrderEntrustFilter = 'all' | 'entrust';

/** 输液贴 / 输液巡视卡长临筛选：全部 / 长期 / 临时（code，禁止用展示文案分支） */
export type InfusionTherapyFilter = 'all' | 'long' | 'temp';

/**
 * 申请单号是否麻醉申请（与后端 ScopeSupport 同口径：仅 AA 前缀）。
 *
 * @param prescriptionNo 方号/申请单号
 * @returns true=麻醉申请内容
 */
export function isAnesthesiaApplyPrescriptionNo(prescriptionNo?: null | string): boolean {
  return String(prescriptionNo ?? '').trim().startsWith('AA');
}

/** 待打表列定义（按单据切换表头语义） */
export interface PrintColumnDef {
  key: keyof Pick<
    PrintPendingRow,
    | 'bedNo'
    | 'patientName'
    | 'title'
    | 'subtitle'
    | 'frequencyDisplay'
    | 'therapyTypeText'
    | 'timeText'
    | 'statusText'
    | 'printStatusText'
    | 'pageHint'
  >;
  label: string;
  width?: number;
  minWidth?: number;
  align?: 'left' | 'center' | 'right';
}

const PRINT_STATUS_COLUMN: PrintColumnDef = {
  key: 'printStatusText',
  label: '打印状态',
  width: 96,
  align: 'center',
};

/**
 * 仅展示「打印状态」、不展示业务「状态」的单据。
 * 这类单据业务 status 恒为「待打」，与打印态重复。
 */
const PRINT_STATUS_ONLY_DOC_TYPES = new Set<PrintDocType>([
  'wristband',
  'bed_card',
  'fee_list',
  'fee_detail',
  'nursing_record',
]);

/**
 * 追加「打印状态」列。
 * print-only 单据会去掉业务 statusText，避免「待打」与「未打印」并列表。
 */
function withPrintStatusColumn(columns: PrintColumnDef[], docType: PrintDocType): PrintColumnDef[] {
  const hideBusinessStatus = PRINT_STATUS_ONLY_DOC_TYPES.has(docType);
  const result = columns
    .filter((col) => !(hideBusinessStatus && col.key === 'statusText'))
    .map((col) => ({ ...col }));
  if (result.some((col) => col.key === 'printStatusText')) {
    return result;
  }
  const statusIdx = result.findIndex((col) => col.key === 'statusText');
  if (statusIdx >= 0) {
    result.splice(statusIdx + 1, 0, { ...PRINT_STATUS_COLUMN });
    return result;
  }
  result.push({ ...PRINT_STATUS_COLUMN });
  return result;
}

/** 空态上下文（拼人话提示） */
export interface PrintEmptyHintContext {
  docType: PrintDocType;
  /** 查询区间文案，如 2026-08-15 或 2026-08-01 ~ 2026-08-15 */
  dateRangeLabel: string;
  oralMedInfusionFilter: 'all' | 'infusion' | 'non_infusion';
  /** 临时医嘱：全部 / 手术 / 麻醉 / 嘱托 */
  tempOrderSurgeryAnesthFilter?: TempOrderSurgeryAnesthFilter;
  /** 长期医嘱（及手麻关闭时的临嘱）：全部 / 嘱托 */
  orderEntrustFilter?: OrderEntrustFilter;
  /** 输液贴 / 输液巡视卡：全部 / 长期 / 临时 */
  infusionTherapyFilter?: InfusionTherapyFilter;
  patientScopeOk: boolean;
  scope: 'single' | 'multiple' | 'ward' | undefined;
}

/**
 * 常用单据（默认导航落点）。
 * code 顺序即「常用」Tab 展示顺序。
 */
export const PRINT_CENTER_FAVORITE_DOC_TYPES: readonly PrintDocType[] = [
  'patrol_card',
  'wristband',
  'bed_card',
  'infusion_label',
  'oral_med_label',
  'oral_med_sheet',
  'long_order',
  'temp_order',
] as const;

/**
 * 打印中心 UI 不展示的单据（能力未接或应走业务页）。
 * 体温单改走护士站「体温单」页签，避免中心假入口。
 */
export const PRINT_CENTER_UI_EXCLUDED_DOC_TYPES: readonly PrintDocType[] = ['temp_sheet'] as const;

const favoriteSet = new Set<PrintDocType>(PRINT_CENTER_FAVORITE_DOC_TYPES);
const uiExcludedSet = new Set<PrintDocType>(PRINT_CENTER_UI_EXCLUDED_DOC_TYPES);

const DEFAULT_COLUMNS: PrintColumnDef[] = [
  { key: 'bedNo', label: '床号', width: 80, align: 'center' },
  { key: 'patientName', label: '患者', width: 96, align: 'center' },
  { key: 'title', label: '内容', minWidth: 200 },
  { key: 'subtitle', label: '说明', width: 110, align: 'center' },
  { key: 'timeText', label: '时间', width: 160, align: 'center' },
  { key: 'statusText', label: '状态', width: 96, align: 'center' },
  { key: 'pageHint', label: '页/签', width: 80, align: 'center' },
];

/** 长嘱/临嘱：说明=医嘱类型（西药/嘱托/诊疗等），页签=长临 */
const ORDER_SHEET_COLUMNS: PrintColumnDef[] = [
  { key: 'bedNo', label: '床号', width: 80, align: 'center' },
  { key: 'patientName', label: '患者', width: 96, align: 'center' },
  { key: 'title', label: '内容', minWidth: 200 },
  { key: 'subtitle', label: '类型', width: 96, align: 'center' },
  { key: 'timeText', label: '时间', width: 140, align: 'center' },
  { key: 'statusText', label: '状态', width: 96, align: 'center' },
  { key: 'pageHint', label: '长临', width: 72, align: 'center' },
];

const DRUG_SHEET_COLUMNS: PrintColumnDef[] = [
  { key: 'bedNo', label: '床号', width: 80, align: 'center' },
  { key: 'patientName', label: '患者', width: 96, align: 'center' },
  { key: 'title', label: '药品', minWidth: 200 },
  { key: 'subtitle', label: '规格', width: 110, align: 'center' },
  { key: 'timeText', label: '最近执行', width: 160, align: 'center' },
  { key: 'statusText', label: '状态', width: 96, align: 'center' },
  { key: 'pageHint', label: '总量', width: 80, align: 'center' },
];

const LABEL_COLUMNS: PrintColumnDef[] = [
  { key: 'bedNo', label: '床号', width: 80, align: 'center' },
  { key: 'patientName', label: '患者', width: 96, align: 'center' },
  { key: 'title', label: '贴签内容', minWidth: 200 },
  { key: 'subtitle', label: '组/规格', width: 120, align: 'center' },
  { key: 'timeText', label: '时间', width: 160, align: 'center' },
  { key: 'statusText', label: '状态', width: 96, align: 'center' },
  { key: 'pageHint', label: '张数', width: 80, align: 'center' },
];

/** 输液贴：组行展示，内容可折行，带长临与频次 */
const INFUSION_GROUP_COLUMNS: PrintColumnDef[] = [
  { key: 'bedNo', label: '床号', width: 80, align: 'center' },
  { key: 'patientName', label: '患者', width: 88, align: 'center' },
  { key: 'title', label: '标签内容', minWidth: 260 },
  { key: 'subtitle', label: '组/规格', width: 140, align: 'center' },
  { key: 'therapyTypeText', label: '长临', width: 72, align: 'center' },
  { key: 'frequencyDisplay', label: '频次', width: 96, align: 'center' },
  { key: 'timeText', label: '时间', width: 88, align: 'center' },
  { key: 'statusText', label: '状态', width: 96, align: 'center' },
  { key: 'pageHint', label: '张数', width: 72, align: 'center' },
];

const PATROL_COLUMNS: PrintColumnDef[] = [
  { key: 'bedNo', label: '床号', width: 80, align: 'center' },
  { key: 'patientName', label: '患者', width: 88, align: 'center' },
  { key: 'title', label: '内容', minWidth: 260 },
  { key: 'subtitle', label: '组/规格', width: 140, align: 'center' },
  { key: 'therapyTypeText', label: '长临', width: 72, align: 'center' },
  { key: 'frequencyDisplay', label: '频次', width: 96, align: 'center' },
  { key: 'timeText', label: '时间', width: 88, align: 'center' },
  { key: 'statusText', label: '状态', width: 96, align: 'center' },
  { key: 'pageHint', label: '页/签', width: 72, align: 'center' },
];

const FEE_COLUMNS: PrintColumnDef[] = [
  { key: 'bedNo', label: '床号', width: 80, align: 'center' },
  { key: 'patientName', label: '患者', width: 96, align: 'center' },
  { key: 'title', label: '费用项', minWidth: 200 },
  { key: 'subtitle', label: '分类', width: 120, align: 'center' },
  { key: 'timeText', label: '时间', width: 160, align: 'center' },
  { key: 'pageHint', label: '金额', width: 96, align: 'right' },
];

/** 腕带：对齐纸单字段（姓名/性别年龄/住院号/诊断） */
const WRISTBAND_COLUMNS: PrintColumnDef[] = [
  { key: 'bedNo', label: '床号', width: 80, align: 'center' },
  { key: 'patientName', label: '患者', width: 96, align: 'center' },
  { key: 'pageHint', label: '性别年龄', width: 110, align: 'center' },
  { key: 'subtitle', label: '住院号', width: 140, align: 'center' },
  { key: 'title', label: '诊断', minWidth: 160 },
  { key: 'timeText', label: '入院日', width: 120, align: 'center' },
];

/** 床头卡：对齐贴纸字段（护理级别/诊断/主管医生·责任护士） */
const BED_CARD_COLUMNS: PrintColumnDef[] = [
  { key: 'bedNo', label: '床号', width: 80, align: 'center' },
  { key: 'patientName', label: '患者', width: 96, align: 'center' },
  { key: 'title', label: '护理级别', width: 100, align: 'center' },
  { key: 'subtitle', label: '诊断', minWidth: 140 },
  { key: 'timeText', label: '入院时间', width: 140, align: 'center' },
  { key: 'pageHint', label: '医生/护士', width: 140, align: 'center' },
];

const NURSING_COLUMNS: PrintColumnDef[] = [
  { key: 'bedNo', label: '床号', width: 80, align: 'center' },
  { key: 'patientName', label: '患者', width: 96, align: 'center' },
  { key: 'title', label: '内容', minWidth: 200 },
  { key: 'subtitle', label: '签名/意识', width: 120, align: 'center' },
  { key: 'timeText', label: '时间', width: 140, align: 'center' },
  { key: 'pageHint', label: '体温', width: 80, align: 'center' },
];

/** 单据 → 表列（未配置走默认） */
const COLUMNS_BY_DOC: Partial<Record<PrintDocType, PrintColumnDef[]>> = {
  oral_med_sheet: DRUG_SHEET_COLUMNS,
  order_dispense_sheet: DRUG_SHEET_COLUMNS,
  dispense_sheet: DRUG_SHEET_COLUMNS,
  dispense_summary: DRUG_SHEET_COLUMNS,
  infusion_label: INFUSION_GROUP_COLUMNS,
  patrol_card: PATROL_COLUMNS,
  oral_med_label: LABEL_COLUMNS,
  specimen_barcode: LABEL_COLUMNS,
  fee_list: FEE_COLUMNS,
  fee_detail: FEE_COLUMNS,
  wristband: WRISTBAND_COLUMNS,
  bed_card: BED_CARD_COLUMNS,
  nursing_record: NURSING_COLUMNS,
  long_order: ORDER_SHEET_COLUMNS,
  temp_order: ORDER_SHEET_COLUMNS,
};

/** 需要单日查询的单据（与历史 isDayQueryDocType 对齐，不含体温单） */
const DAY_QUERY_DOC_TYPES = new Set<PrintDocType>([
  'infusion_label',
  'oral_med_label',
  'patrol_card',
  'order_dispense_sheet',
  'oral_med_sheet',
  'long_order',
  'temp_order',
  'nursing_record',
  'fee_list',
  'fee_detail',
  'dispense_summary',
  'dispense_sheet',
  'exam_apply',
  'lab_apply',
  'specimen_barcode',
]);

/** 是否在打印中心展示该单据（院点 hidden 另计） */
export function isPrintDocVisibleInCenter(
  code: PrintDocType,
  hiddenCodes: ReadonlySet<PrintDocType>,
): boolean {
  if (uiExcludedSet.has(code)) {
    return false;
  }
  if (hiddenCodes.has(code)) {
    return false;
  }
  return true;
}

/** 当前导航分类下的可见单据 */
export function listVisibleDocsForNav(
  nav: PrintNavCategory,
  hiddenCodes: ReadonlySet<PrintDocType>,
) {
  const base =
    nav === 'favorite'
      ? PRINT_CENTER_FAVORITE_DOC_TYPES.map((code) => findPrintDocType(code)).filter(
          (doc): doc is NonNullable<typeof doc> => !!doc,
        )
      : PRINT_DOC_TYPES.filter((doc) => doc.category === nav);

  return base.filter((doc) => isPrintDocVisibleInCenter(doc.code, hiddenCodes));
}

/** 打印中心平铺：全部可见单据（按 PRINT_DOC_TYPES 顺序） */
export function listAllVisiblePrintDocs(hiddenCodes: ReadonlySet<PrintDocType>) {
  return PRINT_DOC_TYPES.filter((doc) => isPrintDocVisibleInCenter(doc.code, hiddenCodes));
}

/** 解析默认激活单据：优先巡视卡，否则常用首项，再否则任意可见首项 */
export function resolveDefaultPrintDocType(hiddenCodes: ReadonlySet<PrintDocType>): PrintDocType {
  if (isPrintDocVisibleInCenter('patrol_card', hiddenCodes)) {
    return 'patrol_card';
  }
  for (const code of PRINT_CENTER_FAVORITE_DOC_TYPES) {
    if (isPrintDocVisibleInCenter(code, hiddenCodes)) {
      return code;
    }
  }
  for (const doc of PRINT_DOC_TYPES) {
    if (isPrintDocVisibleInCenter(doc.code, hiddenCodes)) {
      return doc.code;
    }
  }
  return 'patrol_card';
}

/** 单据所属导航分类（常用优先） */
export function resolveNavCategoryForDoc(docType: PrintDocType): PrintNavCategory {
  if (favoriteSet.has(docType)) {
    return 'favorite';
  }
  return findPrintDocType(docType)?.category ?? 'care';
}

/** 导航分类列表（导出供模板） */
export function listPrintNavCategories() {
  return PRINT_NAV_CATEGORIES;
}

/** 当前单据是否需要日期条件 */
export function isDayQueryDocType(docType: PrintDocType): boolean {
  return DAY_QUERY_DOC_TYPES.has(docType);
}

/** 长/临医嘱：全部|未打|续打 */
export function isOrderExecDocType(docType: PrintDocType): boolean {
  return docType === 'long_order' || docType === 'temp_order';
}

/** 患者用药单：输液筛选 */
export function needsOralMedInfusionFilter(docType: PrintDocType): boolean {
  return docType === 'oral_med_sheet';
}

/** 输液贴 / 输液巡视卡：全部 / 长期 / 临时 */
export function needsInfusionTherapyFilter(docType: PrintDocType): boolean {
  return docType === 'infusion_label' || docType === 'patrol_card';
}

/**
 * 输液贴、输液巡视卡待打行按长临收窄。
 * 长期只留 LONG_TERM；临时只留 TEMPORARY；出院带药仅在「全部」中展示。
 *
 * @param rows 已校对输液组
 * @param filter all / long / temp
 */
export function filterInfusionRowsByTherapy(
  rows: PrintPendingRow[],
  filter: InfusionTherapyFilter,
): PrintPendingRow[] {
  if (filter === 'long') {
    return rows.filter((row) => row.therapyTypeCode === 'LONG_TERM');
  }
  if (filter === 'temp') {
    return rows.filter((row) => row.therapyTypeCode === 'TEMPORARY');
  }
  return rows;
}

/** 临时医嘱：全部/手术/麻醉/嘱托（手麻开关关闭时不展示，改用嘱托组） */
export function needsTempOrderSurgeryAnesthFilter(docType: PrintDocType): boolean {
  return docType === 'temp_order' && INPATIENT_SURGERY_ANESTH_ENABLED;
}

/**
 * 长期医嘱嘱托筛选；临嘱仅在手麻关闭时单独展示（手麻开启时嘱托已并入手麻单选组）。
 */
export function needsOrderEntrustFilter(docType: PrintDocType): boolean {
  if (docType === 'long_order') {
    return true;
  }
  return docType === 'temp_order' && !INPATIENT_SURGERY_ANESTH_ENABLED;
}

/**
 * 待打行是否嘱托（categoryEnum=52；BR-IP-ORD-028）。
 *
 * @param row 长/临待打行
 */
export function isEntrustPendingRow(row: Pick<PrintPendingRow, 'categoryEnum'>): boolean {
  return String(row.categoryEnum ?? '').trim() === SERVICE_CATEGORY_ENTRUST;
}

/**
 * 长期/临时医嘱待打行按嘱托收窄。
 *
 * @param rows 已校对长/临待打行
 * @param filter all / entrust
 */
export function filterOrderRowsByEntrust(
  rows: PrintPendingRow[],
  filter: OrderEntrustFilter,
): PrintPendingRow[] {
  if (filter === 'entrust') {
    return rows.filter((row) => isEntrustPendingRow(row));
  }
  return rows;
}

/**
 * 临时医嘱待打行按手术/麻醉/嘱托收窄。
 * 全部/手术排除 AA；麻醉仅认申请单号 AA 前缀；嘱托按 categoryEnum=52。
 *
 * @param rows 已校对临时医嘱待打行
 * @param filter all / surgery / anesthesia / entrust
 */
export function filterTempOrderRowsBySurgeryAnesth(
  rows: PrintPendingRow[],
  filter: TempOrderSurgeryAnesthFilter,
): PrintPendingRow[] {
  if (filter === 'surgery') {
    return rows.filter(
      (row) =>
        row.categoryEnum === SERVICE_CATEGORY_PROCEDURE &&
        !isAnesthesiaApplyPrescriptionNo(row.prescriptionNo),
    );
  }
  if (filter === 'anesthesia') {
    return rows.filter((row) => isAnesthesiaApplyPrescriptionNo(row.prescriptionNo));
  }
  if (filter === 'entrust') {
    return rows.filter(
      (row) => isEntrustPendingRow(row) && !isAnesthesiaApplyPrescriptionNo(row.prescriptionNo),
    );
  }
  // 全部：排除麻醉申请（正常临嘱）
  return rows.filter((row) => !isAnesthesiaApplyPrescriptionNo(row.prescriptionNo));
}

/** 普通长/临待打：排除麻醉申请（AA 前缀，与后端默认口径一致） */
export function excludeAnesthesiaApplyPendingRows(rows: PrintPendingRow[]): PrintPendingRow[] {
  return rows.filter((row) => !isAnesthesiaApplyPrescriptionNo(row.prescriptionNo));
}

/** 输液巡视卡：按频次拆张开关 */
export function needsFrequencySplitSwitch(docType: PrintDocType): boolean {
  return docType === 'patrol_card';
}

/** 取表列配置 */
export function getPrintDocColumns(docType: PrintDocType): PrintColumnDef[] {
  return withPrintStatusColumn(COLUMNS_BY_DOC[docType] ?? DEFAULT_COLUMNS, docType);
}

/** 范围不符时的下一步提示 */
export function resolveScopeGuide(scope: 'single' | 'multiple' | 'ward' | undefined): string {
  if (scope === 'single') {
    return '本单据仅支持 1 位患者：请在左侧只勾选一人，或点「只留第一人」';
  }
  if (scope === 'multiple') {
    return '本单据支持批量：请在左侧至少勾选 1 位患者';
  }
  if (scope === 'ward') {
    return '本单据按病区打印，可不依赖左侧勾选患者';
  }
  return '请先选择打印单据';
}

/** 空态人话（含范围错误与业务无数据） */
export function resolvePrintEmptyDescription(ctx: PrintEmptyHintContext): string {
  if (!ctx.patientScopeOk) {
    return resolveScopeGuide(ctx.scope);
  }
  const when = ctx.dateRangeLabel || '所选日期';
  if (ctx.docType === 'patrol_card') {
    if (ctx.infusionTherapyFilter === 'long') {
      return `${when} 没有可打印的长期输液药品（含待执行/已执行）`;
    }
    if (ctx.infusionTherapyFilter === 'temp') {
      return `${when} 没有可打印的临时输液药品（含待执行/已执行）`;
    }
    return `${when} 没有可打印的输液药品（含待执行/已执行）`;
  }
  if (ctx.docType === 'infusion_label') {
    if (ctx.infusionTherapyFilter === 'long') {
      return `${when} 没有已校对的长期输液医嘱（含已执行/未执行）`;
    }
    if (ctx.infusionTherapyFilter === 'temp') {
      return `${when} 没有已校对的临时输液医嘱（含已执行/未执行）`;
    }
    return `${when} 没有已校对的输液药品医嘱（含已执行/未执行）`;
  }
  if (ctx.docType === 'oral_med_label') {
    return `${when} 没有已校对的口服药医嘱（含已执行/未执行）`;
  }
  if (ctx.docType === 'order_dispense_sheet') {
    return `${when} 没有已执行的药品医嘱`;
  }
  if (ctx.docType === 'oral_med_sheet') {
    if (ctx.oralMedInfusionFilter === 'infusion') {
      return `${when} 没有已执行的输液药品`;
    }
    if (ctx.oralMedInfusionFilter === 'non_infusion') {
      return `${when} 没有已执行的非输液药品`;
    }
    return `${when} 没有已执行的药品医嘱`;
  }
  if (ctx.docType === 'exam_apply') {
    return `${when} 没有开立的检查申请`;
  }
  if (ctx.docType === 'lab_apply') {
    return `${when} 没有开立的检验申请`;
  }
  if (ctx.docType === 'specimen_barcode') {
    return `${when} 没有可打印的采血贴（检验申请）`;
  }
  if (ctx.docType === 'nursing_record') {
    return `${when} 没有护理记录`;
  }
  if (ctx.docType === 'temp_order' && ctx.tempOrderSurgeryAnesthFilter === 'surgery') {
    return `${when} 没有可打印的手术医嘱`;
  }
  if (ctx.docType === 'temp_order' && ctx.tempOrderSurgeryAnesthFilter === 'anesthesia') {
    return `${when} 没有可打印的麻醉申请医嘱`;
  }
  if (ctx.docType === 'temp_order' && ctx.tempOrderSurgeryAnesthFilter === 'entrust') {
    return `${when} 没有可打印的嘱托医嘱`;
  }
  if (ctx.docType === 'long_order' && ctx.orderEntrustFilter === 'entrust') {
    return `${when} 没有可打印的嘱托医嘱`;
  }
  if (ctx.docType === 'temp_order' && ctx.orderEntrustFilter === 'entrust') {
    return `${when} 没有可打印的嘱托医嘱`;
  }
  if (ctx.docType === 'fee_list') {
    return `${when} 没有可打印的费用清单项目`;
  }
  if (ctx.docType === 'fee_detail') {
    return `${when} 没有可打印的费用明细`;
  }
  return '暂无待打数据，可切换日期或点「刷新」重试';
}
