import type {
  DoctorStationAdviceInventoryRowDto,
  DoctorStationMedicationUnitBandDto,
  ServiceRequestDetailSummaryDto,
} from '#/api/doctor-station/advice.types';
import type { DrugGroupDisplayItem } from '#/utils/doctor-station/drug-group-table';

/** 编辑卡、嘱保存、目录选品共用类型 */

export interface ProductColumn {
  prop: string;
  label: string;
  width?: number;
}

export interface AdviceEditForm {
  orderType: string;
  selectedId: string;
  /** 诊断展示名（多选名称顿号拼接，折叠摘要用） */
  diagnosis: string;
  /** 绑定就诊诊断主键列表（有序；主诊第一；提交拼 ids 串） */
  encounterDiagnosisIds?: string[];
  feeNature: string;
  /** 费用性质/合同主键（落库 `contract_id`） */
  contractId?: string;
  skinTest?: boolean;
  pharmacy: string;
  /** 未录入时为 `undefined`，避免界面默认显示 0 */
  singleDose?: number;
  frequency: string;
  usage: string;
  medicationDays?: number;
  /**
   * 药品/耗材：开立总量；诊疗：执行次数（提交前与 {@link serviceQuantity} 相乘得账单 quantity）。
   */
  totalAmount?: number;
  /**
   * 诊疗：单次执行数量（UI 录入）；提交时 `quantity = serviceQuantity × totalAmount(执行次数)`。
   */
  serviceQuantity?: number;
}

export interface AdviceDrugItem extends AdviceEditForm {
  selectedDrugName: string;
  selectedDrugSpec: string;
  selectedDrugPrice: number | string;
  /** 医保等级码（CHRGITM_LV）；展示用 chrgitmLvText */
  chrgitmLv?: number | string;
  /** 医保等级展示（甲类/乙类/丙类） */
  chrgitmLvText?: string;
  doseMainUnit: string;
  doseSubUnit: string;
  doseConvertRatio?: number;
  doseBandConvertK?: number;
  totalAmountUnit: string;
  totalUnitPackageCode?: string;
  totalUnitPackageName?: string;
  totalUnitSplitCode?: string;
  totalUnitSplitName?: string;
  totalUnitSalesCode?: string;
  totalUnitSalesName?: string;
  totalAmountSplitToSalesK?: number;
  /** 目录原始单价（SALES 带，未换算） */
  catalogUnitPrice?: number | string;
  unitBands?: DoctorStationMedicationUnitBandDto[];
  /** 选品时目录可开库存（包装口径；展示优先 availableQuantityText） */
  availableQuantity?: number | string;
  /** 满包+拆零库存展示（如 4盒6片） */
  availableQuantityText?: string;
  /** 库存明细（拆零 SPLIT）；缺 Text 时本地拼展示） */
  inventoryList?: DoctorStationAdviceInventoryRowDto[];
  /** 门诊拆分属性（OrderCalcRule code "1"~"4"） */
  partAttributeEnum?: string;
  /** 目录默认单次剂量（剂量单位） */
  catalogDose?: number;
  /** 目录单次最小剂量（剂量单位） */
  catalogMinUnit?: number;
  /** 目录单次最大剂量（剂量单位） */
  catalogMaxUnit?: number;
  /** 当前剂量快捷预设：默认 / 最大 / 最小 */
  dosePresetKind?: 'default' | 'max' | 'min';
  /** 频次模块计算得到的计划执行次数（供前端推算总量） */
  frequencyExecutionCount?: number;
  /** 医生手工改过总量/单位后，自动推算不再覆盖本行 */
  totalAmountManualOverride?: boolean;
  expanded: boolean;
  medicationRequestId?: string;
  productTableName?: string;
  sortNumber?: number;
  groupId?: string;
  /** 注射/输液药品（目录 inject_flag / 列表 injectionFlag） */
  injectFlag?: boolean;
  tcmSpecialDecoction?: string;
  performOrgId?: string;
  performOrgName?: string;
}

export type AdviceDrugPatchPayload = {
  key: keyof AdviceDrugItem;
  value: AdviceDrugItem[keyof AdviceDrugItem];
};

export interface ProductQueryParams {
  pageNo: number;
  pageSize: number;
  keyword?: string;
  productTableNames?: string[];
  /** 排除药品 medication_category_code（如开立西药医嘱传 ["4"] 排除中草药） */
  excludeMedicationCategoryCodes?: string[];
  /** 仅看库存>0；后端分页过滤，禁止前端筛当前页 */
  positiveQuantityOnly?: boolean;
}

export interface AdviceProductOption {
  id: string;
  code: string;
  name: string;
  /** 商品名称（药品）；空时列表展示「—」 */
  merchandiseName?: string;
  category: string;
  spec: string;
  price?: number;
  availableQuantity?: number | string;
  /** 满包整数 + 拆零余量展示（如 4盒6片） */
  availableQuantityText?: string;
  /** 厂家/产地 */
  manufacturer?: string;
  inventoryList?: DoctorStationAdviceInventoryRowDto[];
  unitName?: string;
  rateCode?: string;
  methodCode?: string;
  productDefinitionId?: string;
  productTableName?: string;
  unitBands?: DoctorStationMedicationUnitBandDto[];
  defaultPerformOrgId?: string;
  defaultPerformOrgName?: string;
  defaultUnitCode?: string;
  defaultUnitName?: string;
  /** 目录原始单价（SALES 带） */
  catalogUnitPrice?: number;
  /** 门诊拆分属性（OrderCalcRule code "1"~"4"） */
  partAttributeEnum?: string;
  /** 默认单次剂量（剂量单位） */
  dose?: number;
  /** 单次最小剂量（剂量单位） */
  minUnit?: number;
  /** 单次最大剂量（剂量单位） */
  maxUnit?: number;
  /** 医保等级码（CHRGITM_LV） */
  chrgitmLv?: number | string;
  /** 医保等级展示（甲类/乙类/丙类） */
  chrgitmLvText?: string;
  /** 注射/输液药品标志（目录 inject_flag） */
  injectFlag?: boolean;
  /** 中药特殊煎法术语码（目录 dosage_instruction） */
  dosageInstruction?: string;
  /** 中药特殊煎法展示名 */
  dosageInstructionDictText?: string;
}

/** 开立页主表行（含嵌入编辑卡用的虚拟行） */
export interface AdviceRowItem {
  key: string;
  isEditor?: boolean;
  /** 医嘱签发/请求态展示文案 */
  status: string;
  /** 费用状态展示文案 */
  chargeStatus: string;
  type: string;
  content: string;
  /** 签发时间展示文案（reqAuthoredTime；未签发为空） */
  startTime: string;
  extension: string;
  lineTotalAmount: string;
  diagnosisSummary: string;
  orderingDoctor: string;
  skinTestText: string;
  skinTestOutcomeCode?: string;
  dosageText?: string;
  groupItems?: Array<AdviceDrugItem | DrugGroupDisplayItem>;
  mergedAdviceRows?: AdviceRowItem[];
  productTableName?: string;
  sortNumber?: number;
  skinTestFlag?: boolean;
  /** 注射/输液标志（列表 injectionFlag ← 申请 infusion_flag） */
  injectionFlag?: boolean;
  resourceType?: string;
  requestId?: string;
  groupId?: string;
  /** 组套标识；成组与 groupId 同源 */
  packageId?: string;
  /** 医嘱签发/请求态码（RequestStatus） */
  statusEnum?: string;
  /** 费用状态码（ChargeItemStatus） */
  chargeStatusEnum?: string;
  productDefinitionId?: string;
  adviceDisplayName?: string;
  specificationText?: string;
  conditionDefinitionName?: string;
  /** 行绑定就诊诊断有序 ids 串（列表回填多选） */
  encounterDiagnosisIds?: string;
  methodCode?: string;
  rateCode?: string;
  listDose?: number;
  listDoseUnitCode?: string;
  listUnitCode?: string;
  listUnitName?: string;
  dispensePerDuration?: number;
  /** 列表计价数量（诊疗 quantity / 药品总量等） */
  listQuantity?: number;
  /** 诊疗执行次数（结构-only 行 executionCount） */
  listExecutionCount?: number;
  performOrgId?: string;
  performOrgName?: string;
  /** 派生行锚定药品申请（用法绑定带出项等） */
  basedOnMedicationRequestId?: string;
  /** 派生行锚定诊疗申请 */
  basedOnServiceRequestId?: string;
  /** 申请单整单号（诊疗 AR 前缀 prescriptionNo，同单多行共享） */
  applyFormNo?: string;
  /** 申请单行备注（打印注意事项） */
  remark?: string;
  /** 申请单部位/样本明细 */
  serviceRequestDetails?: ServiceRequestDetailSummaryDto[];
  /** 目录类别 code：检验 PROOF、检查 TEST */
  catalogCategoryCode?: string;
  /** 医保等级展示（甲类/乙类/丙类）；申请单编辑回显用 */
  chrgitmLvText?: string;
  /** 费用性质主键（药品/诊疗合同） */
  contractId?: string;
  /** 费用性质分类展示（自费/公费/省医保/市医保/工伤保险） */
  feeNatureText?: string;
}
