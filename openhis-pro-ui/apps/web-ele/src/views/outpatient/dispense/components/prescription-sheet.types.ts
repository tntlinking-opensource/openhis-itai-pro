/** 处方单正文一行药品（序号由组件按数组顺序生成） */
export interface PrescriptionSheetLine {
  /** 主行：如「红霉素软膏(10g) X1 盒」 */
  title: string;
  /** 缩进副行：如「用法用量：擦皮肤 / 0.010g / 每日三次」 */
  usage?: string;
}

/** 纸质处方单展示模型（与接口解耦，供打印/弹窗/工作站复用） */
export interface PrescriptionSheetData {
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
  prescribingDoctorName: string;
  diagnosis: string;
  lines: PrescriptionSheetLine[];
  /** 页脚：医师（通常同开立医生） */
  footerDoctorName: string;
  footerDispensingName: string;
  footerDeliverName: string;
  /** 合计金额展示，如「4.26 元」 */
  totalAmountLabel: string;
  footerPricingClerkName: string;
}
