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
  diagnosis: string;
  feeNature: string;
  /** 费用性质/合同主键（落库 `contract_id`） */
  contractId?: string;
  skinTest?: boolean;
  pharmacy: string;
  /** 未录入时为 `undefined`，避免界面默认显示 0 */
  singleDose?: number;
  /** 首日/首次用量（仅长期；空=首次槽用常规单次量） */
  firstDose?: number;
  /** 首日用量单位编码 */
  firstDoseUnitCode?: string;
  frequency: string;
  usage: string;
  /** 输液滴速编码（术语 speed_code） */
  speedCode?: string;
  medicationDays?: number;
  totalAmount?: number;
  /** 诊疗开立数量（与 servicePayload.quantity 对应；执行次数仍用 totalAmount） */
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
  /** 住院临时医嘱拆分属性（OrderCalcRule code "1"~"4"） */
  partAttributeEnum?: string;
  /** 频次模块计算得到的计划执行次数（供前端推算总量） */
  frequencyExecutionCount?: number;
  /** 医生手工改过总量/单位后，自动推算不再覆盖本行 */
  totalAmountManualOverride?: boolean;
  /** 目录原始单价（SALES 带，未换算） */
  catalogUnitPrice?: number | string;
  unitBands?: DoctorStationMedicationUnitBandDto[];
  /** 选品时目录可开库存（包装口径；展示优先 availableQuantityText） */
  availableQuantity?: number | string;
  /** 满包+拆零库存展示（如 4盒6片） */
  availableQuantityText?: string;
  /** 库存明细（拆零 SPLIT；缺 Text 时本地拼展示） */
  inventoryList?: DoctorStationAdviceInventoryRowDto[];
  expanded: boolean;
  medicationRequestId?: string;
  productTableName?: string;
  sortNumber?: number;
  groupId?: string;
  tcmSpecialDecoction?: string;
  performOrgId?: string;
  performOrgName?: string;
  /** 住院治疗类型：LONG_TERM / TEMPORARY / DISCHARGE_MEDICATION */
  therapyEnum?: string;
  /** 目录分类码（如 52=嘱托、26=护理级别） */
  catalogCategoryCode?: string;
  /** 嘱托正文（servicePayload.entrustContent；BR-IP-ORD-028） */
  entrustContent?: string;
  /** 医嘱备注（药嘱/诊嘱 remark；与嘱托正文分立） */
  remark?: string;
  /** 目录/列表注射输液标志；为 true 时编辑卡展示滴速 */
  injectFlag?: boolean;
}

/** 住院医嘱编辑卡 · 长期/临时/出院带药（与 AS-IP-DOR-003 出院带药处方类别 code 对齐） */
export type InpatientAdviceTherapyType = 'DISCHARGE_MEDICATION' | 'LONG_TERM' | 'TEMPORARY';

export type AdviceDrugPatchPayload = {
  key: keyof AdviceDrugItem;
  value: AdviceDrugItem[keyof AdviceDrugItem];
};

export interface ProductQueryParams {
  pageNo: number;
  pageSize: number;
  keyword?: string;
  productTableNames?: string[];
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
  /** 目录注射/输液标志（inject_flag）；开立卡用于展示滴速 */
  injectFlag?: boolean;
  productDefinitionId?: string;
  productTableName?: string;
  unitBands?: DoctorStationMedicationUnitBandDto[];
  defaultPerformOrgId?: string;
  defaultPerformOrgName?: string;
  defaultUnitCode?: string;
  defaultUnitName?: string;
  /** 目录分类码（活动定义 category_code，如 52 嘱托） */
  categoryCode?: string;
  /** 目录原始单价（SALES 带） */
  catalogUnitPrice?: number;
  /** 门诊/住院临时医嘱拆分属性（OrderCalcRule code "1"~"4"） */
  partAttributeEnum?: string;
  /** 医保等级码（CHRGITM_LV） */
  chrgitmLv?: number | string;
  /** 医保等级展示（甲类/乙类/丙类） */
  chrgitmLvText?: string;
  /** 中药特殊煎法术语码（目录 dosage_instruction） */
  dosageInstruction?: string;
  /** 中药特殊煎法展示名 */
  dosageInstructionDictText?: string;
}

/** 开立页主表行（含嵌入编辑卡用的虚拟行） */
export interface AdviceRowItem {
  key: string;
  isEditor?: boolean;
  status: string;
  type: string;
  content: string;
  /** 起止时间列·开立时间（仅时间文案，人名见 orderingDoctor） */
  startTime: string;
  /** 起止时间列·停嘱时间（仅时间文案；未停嘱为空） */
  endTime?: string;
  /** 接口原始开立时间（改时 DatePicker 回填） */
  openedTimeRaw?: string;
  /** 接口原始停嘱时间（改时 DatePicker 回填） */
  stopTimeRaw?: string;
  /** 停嘱人姓名（改停嘱时间门禁/展示） */
  stopperName?: string;
  /** 停嘱校对人主键；非空时禁止取消停嘱 */
  stopPerformerCheckId?: string;
  extension: string;
  lineTotalAmount: string;
  diagnosisSummary: string;
  orderingDoctor: string;
  skinTestText: string;
  /** 皮试结果 code（AllergyIntolerance clinicalStatus），驱动 Tag 颜色 */
  skinTestOutcomeCode?: string;
  /** 长嘱皮试药换批提示：建议追加皮试检查 */
  needAppendSkinTestFlag?: boolean;
  dosageText?: string;
  groupItems?: Array<AdviceDrugItem | DrugGroupDisplayItem>;
  mergedAdviceRows?: AdviceRowItem[];
  productTableName?: string;
  sortNumber?: number;
  skinTestFlag?: boolean;
  resourceType?: string;
  requestId?: string;
  groupId?: string;
  statusEnum?: string;
  /** 住院列表：LONG_TERM / TEMPORARY / DISCHARGE_MEDICATION */
  therapyTypeCode?: string;
  /** 住院列表：治疗类型展示文案 */
  therapyTypeText?: string;
  /** 嘱托正文回显（列表内容列优先） */
  entrustContent?: string;
  /** 医嘱备注回显（编辑卡 / 暂存） */
  remark?: string;
  /** 列表行频次 code（诊疗/药品编辑回填） */
  rateCode?: string;
  productDefinitionId?: string;
  /** 列表行数量（诊疗执行次数 / 药品总量等） */
  listQuantity?: number;
  adviceDisplayName?: string;
  specificationText?: string;
  conditionDefinitionName?: string;
  methodCode?: string;
  /** 列表行输液滴速编码 */
  speedCode?: string;
  /** 列表行输液滴速展示名 */
  speedName?: string;
  /** 列表注射/输液标志（injectionFlag） */
  injectionFlag?: boolean;
  listDose?: number;
  listDoseUnitCode?: string;
  /** 首日/首次用量（列表回显 → 编辑卡） */
  listFirstDose?: number;
  listFirstDoseUnitCode?: string;
  listUnitCode?: string;
  /** 列表行单位展示名（与 listUnitCode 成对，编辑卡回显） */
  listUnitName?: string;
  /** 列表行执行次数（诊疗临时医嘱编辑回填） */
  listExecutionCount?: number;
  /** 列表行执行科室（诊疗编辑回填） */
  performOrgId?: string;
  performOrgName?: string;
  dispensePerDuration?: number;
  /** 申请单整单号（诊疗 AR 前缀 prescriptionNo，同单多行共享） */
  applyFormNo?: string;
  /** 申请单部位/样本明细（编辑弹窗回显） */
  serviceRequestDetails?: ServiceRequestDetailSummaryDto[];
  /** 诊疗服务类目 code（26=护理级别） */
  serviceCategoryEnum?: string;
  /** 目录分类码（与 serviceCategoryEnum 对齐时可用于识别护理级别） */
  catalogCategoryCode?: string;
  /** 医保项目等级码（CHRGITM_LV） */
  chrgitmLv?: number | string;
  /** 医保项目等级展示（甲类/乙类/丙类） */
  chrgitmLvText?: string;
  /** 口服一盒一点剩余文案（已吃/剩余/下次领盒） */
  oralPackageRemainText?: string;
  oralPackageBatchInventoryApplied?: boolean;
}
