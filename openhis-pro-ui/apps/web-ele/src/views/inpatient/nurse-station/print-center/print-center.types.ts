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

/** 待打列表行状态 code（禁止用 statusText 做分支） */
export type PrintPendingStatusCode = 'pending' | 'printed' | 'ready' | 'submitted' | 'executable';

/** 待打列表行（业务接口映射） */
export interface PrintPendingRow {
  id: string;
  bedNo: string;
  patientName: string;
  title: string;
  subtitle?: string;
  timeText?: string;
  /** 状态 code，列表 Tag / 筛选用 */
  statusCode: PrintPendingStatusCode;
  /** 状态展示文案（仅展示） */
  statusText: string;
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
  /** 是否输液（患者用药单筛选用；标记 infusionFlag 或 用法 methodCode） */
  infusionFlag?: boolean;
}

export const PRINT_DOC_CATEGORIES: PrintDocCategoryOption[] = [
  { code: 'care', label: '护理文书' },
  { code: 'order', label: '医嘱执行' },
  { code: 'drug', label: '药品相关' },
  { code: 'fee', label: '费用' },
];

export const PRINT_DOC_TYPES: PrintDocTypeOption[] = [
  {
    code: 'patrol_card',
    label: '输液巡视卡',
    category: 'care',
    scope: 'multiple',
    previewKind: 'paper',
    description: '按患者、按日打印；默认当天，含待执行/已执行输液药品，无药不可打',
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
    description: '按口服药医嘱打印药贴（50×30mm）',
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
    description: '按开嘱日查询；按检验医嘱逐张打印采血贴（categoryEnum=22，50×30mm）',
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
    label: '摆药单',
    category: 'drug',
    scope: 'ward',
    previewKind: 'table',
    description: '按发药批次打印摆药明细',
  },
  {
    code: 'order_dispense_sheet',
    label: '摆药单',
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
