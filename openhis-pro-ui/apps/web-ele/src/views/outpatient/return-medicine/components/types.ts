/** 左侧退药患者队列行（对齐 ReturnPatientRowDto） */
export interface ReturnMedicineQueuePatient {
  id: string;
  patientName: string;
  genderEnumText: string;
  ageLabel: string;
  visitNo: string;
  visitTimeLabel: string;
  departmentName: string;
  refundEnum: string;
  refundEnumText: string;
  /** 费用性质展示名（队列 contractDisplay；卡片底栏胶囊） */
  feeNatureLabel?: string;
}

/** 退药明细表格行（对齐 ReturnMedicineDetailRowDto，仅药品） */
export interface ReturnMedicineDetailRow {
  id: string;
  dispenseId: string;
  requestId: string;
  itemName: string;
  lotNumber: string;
  quantity: string;
  unitText: string;
  /** 行总价数值（合计用；无价时为 null） */
  totalPrice: null | number;
  totalPriceLabel: string;
  doctorName: string;
  refundEnum: string;
  refundEnumText: string;
  reqStatusText: string;
  traceNo: string;
  /** 退药发放业务单号（码上放心 116） */
  busNo: string;
  /** 待退药时可勾选提交 */
  selectable: boolean;
}
