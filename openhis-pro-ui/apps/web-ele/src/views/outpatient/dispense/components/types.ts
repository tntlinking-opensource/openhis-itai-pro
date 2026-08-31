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
  /**
   * 就诊类型展示（后端 encounterMedicationTypeText ← adm_encounter_diagnosis.med_type）。
   * 一张方一种类型；发药单笺面回显用，打印不消费。
   */
  visitTypeLabel?: string;
  /** 发放单 busNo（码上放心上传键；与处方号 rxNo 不同） */
  dispenseBusNo?: string;
  /** 患者手机号（后端 slip.patientPhone） */
  patientPhone?: string;
  /**
   * 方头纸质分类第一行（普通处方 / 慢病处方 / 急诊处方等）。
   * 与打印 prescriptionTypeLine1 同源。
   */
  prescriptionTypeLine1?: string;
  /** 方头第二行（默认「当日有效」） */
  prescriptionTypeLine2?: string;
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

/**
 * 批号候选（一行一条；库房已锁定为发放行库房，选项仅切换批号）。
 * value 仍编码库房+批号，与提交 codec 兼容。
 */
export interface DispenseInventoryPickOption {
  value: string;
  label: string;
  /** 该批号剩余库存展示（如 4盒6片） */
  remainingStockText?: string;
  /** 批号字面量（展示/回显） */
  lotNumber?: string;
}

/** 处方内单行药品（发药列表粒度） */
export interface DispensePrescriptionLine {
  id: string;
  /**
   * 展示合并行背后的发放主键列表（住院按日+同医嘱累加数量时）。
   * 缺省视为仅 {@link id} 一条；勾选/发药提交须展开为本列表。
   */
  sourceDispenseIds?: string[];
  /** 药品定义主键，批量扫码行级匹配用 */
  medicationId?: string;
  itemName: string;
  /** 发药数量展示：接口 quantity + unitText（不拼单位 code） */
  quantity: string;
  /** 发放/计价单位码（与后端 slip.unitCode 对齐） */
  unitCode?: string;
  /** 发放/计价单位展示名（库存剩余换算用） */
  unitText?: string;
  /** 本发放行数量数值（与后端 slip.quantity 对齐） */
  quantityValue?: number;
  /** 包装单位码；追溯默认按包装计码 */
  packageUnitCode?: string;
  /** 包装相对拆零倍数 */
  packageUnitRatio?: number;
  /** 拆零单位码 */
  splitUnitCode?: string;
  /**
   * 当前药品剩余库存展示（锚定发放库房×当前批号；如 4盒6片）。
   * 代煎费等非药品行可为「—」。选批后由界面按选项覆盖。
   */
  remainingStockText?: string;
  /**
   * 追溯码主档贴码单位（cus_trace_number；发药待扫换算用）。
   * 非药典目录值。
   */
  traceUnitCode?: string;
  /** 追溯码主档贴码单位展示文案（UNIT_CODE 术语） */
  traceUnitCodeText?: string;
  /**
   * 追溯码主档贴码比例快照（1 码对应拆零量；发药待扫换算用）。
   * 语义：1 个追溯码单位 = 本值个拆零单位。
   */
  traceUnitRatio?: number;
  /**
   * 主表该品批是否已有码。false/缺省按无码批：扫码软提示、不挡发药。
   */
  lotHasTraceMaster?: boolean;
  spec: string;
  traceCode: string;
  batchNo: string;
  /** 行金额（totalPrice，列表「金额」列与笺面合计用） */
  amount: string;
  /** 单价（unitPrice；笺面「单价」；住院汇总等可不填） */
  unitPrice?: string;
  /** 无下拉候选时的展示文案（如已发药仅展示） */
  pharmacy: string;
  /** 发放库房主键（库取追溯码、提交发药用） */
  dispenseLocationId?: string;
  /** 待发药且本库有批号候选时，按行选择批号（不可换库房） */
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
