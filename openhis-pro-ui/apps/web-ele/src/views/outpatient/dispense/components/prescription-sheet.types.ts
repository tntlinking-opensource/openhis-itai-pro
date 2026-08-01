import type { TcmHerbGridDisplayRow } from '#/utils/tcm-prescription-display';

/**
 * 发药单正文一行药品（与列表明细字段对齐；序号由组件按数组顺序生成）。
 */
export interface PrescriptionSheetLine {
  /** 项目名称 */
  itemName: string;
  /** 数量（含单位展示） */
  quantity: string;
  /** 规格 */
  spec: string;
  /** 特殊煎法（中药饮片） */
  dosageInstructionText?: string;
  /** 批号 */
  batchNo: string;
  /** 单价（行金额展示） */
  amount: string;
  /** 追溯码 */
  traceCode: string;
  /** 厂家 */
  factory: string;
  /** 用法用量文案（接在规格后、数量前），不含「用法用量」标签 */
  usage?: string;
}

/** 发药单展示模型（与接口解耦，供工作站笺面复用） */
export interface PrescriptionSheetData {
  rxType?: 'herbal' | 'western';
  /** 发药工作站处方块状态（与列表明细同源） */
  status?: 'blocking' | 'completed' | 'partial' | 'pending' | 'prepared';
  /** 状态展示文案，如「待发」「已配药」 */
  statusLabel?: string;
  /** 页眉副标题：发药站「发药单/中药发药单」；医生站「处方单/中药处方单」 */
  documentTitle?: string;
  /** 机构名称（页眉居中） */
  hospitalName: string;
  /** 处方号 */
  prescriptionNo: string;
  patientName: string;
  patientAgeLabel: string;
  patientGenderLabel: string;
  deptName: string;
  /** 费用性质，如自费、医保 */
  feeNatureLabel: string;
  /** 处方日期 YYYY-MM-DD */
  prescriptionDate: string;
  /** 门诊号/就诊号展示文案 */
  outpatientNoLabel: string;
  /** 开单医生（orderingPractitionerName / authoredByName） */
  prescribingDoctorName: string;
  diagnosis: string;
  /** 患者手机号（后端 patientPhone） */
  patientPhone: string;
  /** 发放药房（明细行库房去重聚合，展示在基础信息区） */
  dispensePharmacyLabel: string;
  lines: PrescriptionSheetLine[];
  /** 中药：四列饮片网格（药名+克数，note* 为右上角特殊煎法） */
  tcmHerbGridRows?: TcmHerbGridDisplayRow[];
  /** 中药：付数 + 频次 + 用法 */
  tcmUsageText?: string;
  /** 页脚：医师（同源 orderingPractitionerName） */
  footerDoctorName: string;
  /** 页脚：调配（preparerPractitionerName） */
  footerDispensingName: string;
  /** 页脚：发药（dispensingPractitionerName） */
  footerDeliverName: string;
  /** 合计金额展示，如「4.26 元」 */
  totalAmountLabel: string;
  /** 页脚：划价（pricingPractitionerName=收费人，空则 —） */
  footerPricingClerkName: string;
  /** 中药：代煎费金额展示（有代煎时展示） */
  decoctionChargeAmountLabel?: string;
  /** 中药：代煎项目名称 */
  decoctionActivityName?: string;
  /** 中药：代煎数量展示 */
  decoctionChargeQuantityLabel?: string;
}
