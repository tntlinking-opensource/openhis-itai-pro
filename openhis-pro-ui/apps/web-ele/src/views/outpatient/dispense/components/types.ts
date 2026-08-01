import type { TcmHerbGridDisplayRow } from '#/utils/tcm-prescription-display';

/** 中西药 tab 角标：按处方块计数（非明细行数） */
export interface DispenseRxKindBadgeCounts {
  herbal: number;
  western: number;
}

/** 左侧待发药患者队列 */
export interface DispenseQueuePatient {
  id: string;
  patientId?: string;
  patientName: string;
  /** 性别展示文案：直接用后端 genderEnumText，前端不翻译 code */
  genderLabel: string;
  ageLabel: string;
  /** 接诊科室名称（卡片右上角第三段） */
  organizationName?: string;
  /** 门诊就诊号（卡片左下角等全量展示，可与搜索关键字匹配） */
  visitNo: string;
  /** 门诊卡片「就诊时间」展示 */
  visitTimeLabel: string;
  /** 患者手机号（队列 patientPhone） */
  patientPhone?: string;
  /** 费用性质展示名（队列 contractDisplay；卡片底栏胶囊） */
  feeNatureLabel?: string;
  rxCount: number;
}

/** 处方块（一张处方 + 多条明细，对接后由接口组装） */
export interface DispensePrescriptionBlock {
  id: string;
  rxNo: string;
  rxType: 'herbal' | 'western';
  deptName: string;
  /** 开单医生（orderingPractitionerName） */
  doctorName: string;
  /** 调配（preparerPractitionerName） */
  preparerPractitionerName?: string;
  /** 发药（dispensingPractitionerName） */
  dispensingPractitionerName?: string;
  /** 划价（pricingPractitionerName；取收费人，禁止回落开单医生） */
  pricingPractitionerName?: string;
  /** 费用性质展示文案（后端 feeNatureText；空时笺面补偿用队列患者 feeNatureLabel） */
  feeNatureLabel?: string;
  /** 患者手机号（后端 slip.patientPhone） */
  patientPhone?: string;
  issuedAt: string;
  status: 'blocking' | 'completed' | 'partial' | 'pending' | 'prepared';
  items: DispensePrescriptionLine[];
  /** 中药处方：四列饮片网格（与打印一致） */
  tcmHerbGridRows?: TcmHerbGridDisplayRow[];
  /** 中药处方：付数 + 频次 + 用法 */
  tcmUsageText?: string;
  /** 方头是否代煎 */
  sufferingFlag?: boolean;
  /** 代煎项目名称（默认「代煎中药」） */
  decoctionActivityName?: string;
  /** 代煎费金额展示（已格式化） */
  decoctionChargeAmountLabel?: string;
  /** 代煎费数量展示（如「7付」） */
  decoctionChargeQuantityLabel?: string;
}

/** 发放库房候选（一行一条选项，对应库存明细） */
export interface DispenseInventoryPickOption {
  value: string;
  label: string;
}

/** 处方内单行药品（发药列表粒度） */
export interface DispensePrescriptionLine {
  id: string;
  /** 药品定义主键，批量扫码行级匹配用 */
  medicationId?: string;
  itemName: string;
  /** 发药数量展示：接口 quantity + unitText（不拼单位 code） */
  quantity: string;
  /** 发放/计价单位码（与后端 slip.unitCode 对齐） */
  unitCode?: string;
  /** 本发放行数量数值（与后端 slip.quantity 对齐） */
  quantityValue?: number;
  /** 包装单位码；追溯默认按包装计码 */
  packageUnitCode?: string;
  /** 包装相对拆零倍数 */
  packageUnitRatio?: number;
  /** 拆零单位码 */
  splitUnitCode?: string;
  /**
   * 追溯码主档贴码单位（cus_trace_number；发药待扫换算用）。
   * 非药典目录值。
   */
  traceUnitCode?: string;
  /**
   * 追溯码主档贴码比例快照（1 码对应拆零量；发药待扫换算用）。
   */
  traceUnitRatio?: number;
  /**
   * 主表该品批是否已有码。false/缺省按无码批：扫码软提示、不挡发药。
   */
  lotHasTraceMaster?: boolean;
  spec: string;
  traceCode: string;
  batchNo: string;
  amount: string;
  /** 无下拉候选时的展示文案（如已发药仅展示） */
  pharmacy: string;
  /** 发放库房主键（库取追溯码、提交发药用） */
  dispenseLocationId?: string;
  /** 待发药且有库存候选时，按行选择库房/批号 */
  inventoryPickOptions?: DispenseInventoryPickOption[];
  factory: string;
  diagnosis: string;
  freq: string;
  /** 单味特殊煎法展示名（中药饮片） */
  dosageInstructionText?: string;
  /**
   * 明细行类型：代煎费为合成展示行，不参与发药提交。
   * 缺省视为药材/药品发放行。
   */
  lineKind?: 'decoctionFee' | 'medication';
}

/** 单表展示用扁平行：同 rxId 多行 + rowspan 元数据 */
export interface DispensePrescriptionItemRow extends DispensePrescriptionLine {
  rxId: string;
  rxNo: string;
  rxType: 'herbal' | 'western';
  deptName: string;
  doctorName: string;
  issuedAt: string;
  status: 'blocking' | 'completed' | 'partial' | 'pending' | 'prepared';
  rxGroupIndex: number;
  /** 本处方在表内占几行（用于合并左侧元数据列） */
  rxRowSpan: number;
  isFirstInRx: boolean;
  isLastInRx: boolean;
}

/** 组与组之间的空白间隔行（仅占位，不承载业务） */
export interface DispenseRxTableSpacerRow {
  id: string;
  rowKind: 'spacer';
}

export type DispenseRxTableRow = DispensePrescriptionItemRow | DispenseRxTableSpacerRow;
