/** 打印中心一级分类 */
export type PrintDocCategory = 'care' | 'order' | 'drug' | 'fee';

/** 打印中心二级单据 */
export type PrintDocType =
  | 'temp_sheet'
  | 'nursing_record'
  | 'patrol_card'
  | 'wristband'
  | 'bed_card'
  | 'long_order'
  | 'temp_order'
  | 'infusion_label'
  | 'oral_med_label'
  | 'oral_med_sheet'
  | 'exam_apply'
  | 'lab_apply'
  | 'specimen_barcode'
  | 'dispense_summary'
  | 'dispense_sheet'
  | 'order_dispense_sheet'
  | 'fee_list'
  | 'fee_detail';

/** 单据对左侧患者选择的要求 */
export type PrintPatientScope = 'single' | 'multiple' | 'ward';

/** 预览区展示形态 */
export type PrintPreviewKind = 'paper' | 'label' | 'table';

export interface PrintDocCategoryOption {
  code: PrintDocCategory;
  label: string;
}

export interface PrintDocTypeOption {
  code: PrintDocType;
  label: string;
  category: PrintDocCategory;
  scope: PrintPatientScope;
  previewKind: PrintPreviewKind;
  /** 角标：待打数量（由列表加载后回写，初始可空） */
  pendingCount?: number;
  description: string;
}

/** 待打列表行业务状态 code（禁止用 statusText 做分支；与打印状态分离） */
export type PrintPendingStatusCode = 'pending' | 'printed' | 'ready' | 'submitted' | 'executable';

/** 待打列表行打印状态 code（审计回显；禁止与业务 statusCode 混用） */
export type PrintPendingPrintStatusCode = 'unprinted' | 'printed';

/** 待打列表行（业务接口映射） */
export interface PrintPendingRow {
  id: string;
  bedNo: string;
  patientName: string;
  title: string;
  subtitle?: string;
  /** 频次展示文案（仅展示，禁止用文案做分支） */
  frequencyDisplay?: string;
  timeText?: string;
  /** 业务状态 code（已执行/待打等），列表 Tag / 业务筛选用 */
  statusCode: PrintPendingStatusCode;
  /** 业务状态展示文案（仅展示） */
  statusText: string;
  /** 打印状态 code：未打印 / 已打印（审计 enrich） */
  printStatusCode?: PrintPendingPrintStatusCode;
  /** 打印状态展示文案（仅展示） */
  printStatusText?: string;
  pageHint?: string;
  encounterId?: string;
  patientId?: string;
  /** 汇总领药单 / 摆药单号 */
  summaryNo?: string;
  /** 医嘱执行行键 */
  listRowKey?: string;
  orderId?: string;
  /** 输液成组号 */
  groupId?: string;
  /** 频次 code（拆张用；禁止用 frequencyDisplay 推断） */
  rateCode?: string;
  /** 查询日去重计划点数（rateCode 缺失时作每日次数回落） */
  daySlotCount?: number;
  /** 是否输液（患者用药单筛选用；标记 infusionFlag 或 用法 methodCode） */
  infusionFlag?: boolean;
  /** 合管检验设备主键（采血贴按 device 分组） */
  specimenTubeMergeDeviceId?: string;
  /**
   * 检验开立数量展示（如「2次」；与申请单数量列同源 dosageDisplay；采血贴张数用）。
   */
  dosageDisplay?: string;
  /** 医嘱优先级 code（加急=EM；采血贴印「急」） */
  priorityEnum?: string;
}

export const PRINT_DOC_CATEGORIES: PrintDocCategoryOption[] = [
  { code: 'care', label: '护理文书' },
  { code: 'order', label: '医嘱/贴签' },
  { code: 'drug', label: '药品' },
  { code: 'fee', label: '费用' },
];

/** 打印中心导航一级（含「常用」） */
export type PrintNavCategory = 'favorite' | PrintDocCategory;

export interface PrintNavCategoryOption {
  code: PrintNavCategory;
  label: string;
}

export const PRINT_NAV_CATEGORIES: PrintNavCategoryOption[] = [
  { code: 'favorite', label: '常用' },
  { code: 'care', label: '护理文书' },
  { code: 'order', label: '医嘱/贴签' },
  { code: 'drug', label: '药品' },
  { code: 'fee', label: '费用' },
];

export const PRINT_DOC_TYPES: PrintDocTypeOption[] = [
  {
    code: 'patrol_card',
    label: '输液巡视卡',
    category: 'care',
    scope: 'multiple',
    previewKind: 'paper',
    description:
      '按日列出输液组供勾选，默认全选；按患者合成一张巡视卡。默认按频次打印：日两次打印两张，关闭后不再按频次拆开',
  },
  {
    code: 'wristband',
    label: '腕带',
    category: 'care',
    scope: 'multiple',
    previewKind: 'label',
    description: '按患者打印住院腕带（TD-2020A/K1a：25×140mm 走纸）',
  },
  {
    code: 'bed_card',
    label: '床头卡',
    category: 'care',
    scope: 'multiple',
    previewKind: 'paper',
    description: '按患者打印床头卡（长21cm×宽13cm）',
  },
  {
    code: 'infusion_label',
    label: '输液贴',
    category: 'order',
    scope: 'multiple',
    previewKind: 'label',
    description: '按日查询已校对输液医嘱（含已执行/未执行），同组合并打贴',
  },
  {
    code: 'oral_med_label',
    label: '口服药贴',
    category: 'drug',
    scope: 'multiple',
    previewKind: 'label',
    description: '按查询区间列出已校对口服医嘱（含已执行/未执行）打印药贴（30×40mm）',
  },
  {
    code: 'oral_med_sheet',
    label: '患者用药单',
    category: 'order',
    scope: 'multiple',
    previewKind: 'table',
    description:
      '按日列出已执行药品，勾选后打印；一张连续多人（患者信息→药品表含备注→患者签字→下一患者）；院点一盒一点时口服长嘱总量按包装单位；同医嘱多次执行合计为一行',
  },
  {
    code: 'exam_apply',
    label: '检查单',
    category: 'order',
    scope: 'multiple',
    previewKind: 'paper',
    description: '按开嘱日查询；按患者合并打印检查申请单（categoryEnum=23）',
  },
  {
    code: 'lab_apply',
    label: '检验单',
    category: 'order',
    scope: 'multiple',
    previewKind: 'paper',
    description: '按开嘱日查询；按患者合并打印检验申请单（categoryEnum=22）',
  },
  {
    code: 'specimen_barcode',
    label: '采血贴',
    category: 'order',
    scope: 'multiple',
    previewKind: 'label',
    description:
      '按开嘱日查询；按检验医嘱开立数量打对应贴数（如 2次→2 张；合管组取组内最大；categoryEnum=22，30×50mm）',
  },
  {
    code: 'temp_sheet',
    label: '体温单',
    category: 'care',
    scope: 'single',
    previewKind: 'paper',
    description: '按周次打印体温曲线纸单',
  },
  {
    code: 'nursing_record',
    label: '护理记录',
    category: 'care',
    scope: 'single',
    previewKind: 'paper',
    description: '按日打印护理记录单',
  },
  {
    code: 'long_order',
    label: '长期医嘱',
    category: 'order',
    scope: 'single',
    previewKind: 'table',
    description: '支持全部打印 / 续打',
  },
  {
    code: 'temp_order',
    label: '临时医嘱',
    category: 'order',
    scope: 'single',
    previewKind: 'table',
    description: '支持全部打印 / 未打 / 续打',
  },
  {
    code: 'fee_list',
    label: '费用清单',
    category: 'fee',
    scope: 'single',
    previewKind: 'table',
    description: '按日打印费用汇总清单（同品合并 + 分类合计）',
  },
  {
    code: 'fee_detail',
    label: '费用明细单',
    category: 'fee',
    scope: 'single',
    previewKind: 'table',
    description: '按日打印费用流水明细（按分类分组，无分类合计）',
  },
  {
    code: 'dispense_summary',
    label: '汇总领药单',
    category: 'drug',
    scope: 'ward',
    previewKind: 'table',
    description: '按病区汇总单打印（可不绑单人）',
  },
  {
    code: 'dispense_sheet',
    label: '批次摆药单',
    category: 'drug',
    scope: 'ward',
    previewKind: 'table',
    description: '按发药批次打印摆药明细',
  },
  {
    code: 'order_dispense_sheet',
    label: '医嘱摆药单',
    category: 'order',
    scope: 'multiple',
    previewKind: 'table',
    description:
      '按日打印已执行全部药品（含口服）；一张连续多人（患者信息在表外→药品表→下一患者），无患者签字；打不下自动分页且每页有医院头；同医嘱多次执行合计为一行',
  },
];

/** 按分类取单据列表 */
export function listPrintDocsByCategory(category: PrintDocCategory): PrintDocTypeOption[] {
  return PRINT_DOC_TYPES.filter((item) => item.category === category);
}

/** 根据单据 code 取配置 */
export function findPrintDocType(code: PrintDocType): PrintDocTypeOption | undefined {
  return PRINT_DOC_TYPES.find((item) => item.code === code);
}

/** 患者范围文案 */
export function resolvePrintScopeLabel(scope: PrintPatientScope): string {
  if (scope === 'single') {
    return '单人';
  }
  if (scope === 'multiple') {
    return '多选';
  }
  return '病区';
}
