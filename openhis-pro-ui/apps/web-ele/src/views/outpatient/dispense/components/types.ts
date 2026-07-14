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
  /** 无后端文本字段时可为空，卡片走 genderEnum */
  genderLabel: string;
  /** 与 OutpatientInfo 卡片一致时回填（小整数枚举，非 Long） */
  genderEnum?: null | number;
  ageLabel: string;
  /** 门诊就诊号（卡片左下角等全量展示，可与搜索关键字匹配） */
  visitNo: string;
  /** 门诊卡片「就诊时间」展示 */
  visitTimeLabel: string;
  rxCount: number;
}

/** 处方块（一张处方 + 多条明细，对接后由接口组装） */
export interface DispensePrescriptionBlock {
  id: string;
  rxNo: string;
  rxType: 'herbal' | 'western';
  deptName: string;
  doctorName: string;
  issuedAt: string;
  status: 'blocking' | 'completed' | 'partial' | 'pending' | 'prepared';
  items: DispensePrescriptionLine[];
  /** 中药处方：四列饮片网格（与打印一致） */
  tcmHerbGridRows?: TcmHerbGridDisplayRow[];
  /** 中药处方：付数 + 频次 + 用法 */
  tcmUsageText?: string;
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
