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
  frequency: string;
  usage: string;
  medicationDays?: number;
  totalAmount?: number;
  /** 诊疗开立数量（与 servicePayload.quantity 对应；执行次数仍用 totalAmount） */
  serviceQuantity?: number;
}

export interface AdviceDrugItem extends AdviceEditForm {
  selectedDrugName: string;
  selectedDrugSpec: string;
  selectedDrugPrice: number | string;
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
  /** 门诊拆分属性（OrderCalcRule code "1"~"4"） */
  partAttributeEnum?: string;
  /** 频次模块计算得到的计划执行次数（供前端推算总量） */
  frequencyExecutionCount?: number;
  /** 医生手工改过总量/单位后，自动推算不再覆盖本行 */
  totalAmountManualOverride?: boolean;
  expanded: boolean;
  medicationRequestId?: string;
  productTableName?: string;
  sortNumber?: number;
  groupId?: string;
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
}

export interface AdviceProductOption {
  id: string;
  code: string;
  name: string;
  category: string;
  spec: string;
  price?: number;
  availableQuantity?: number | string;
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
}

/** 开立页主表行（含嵌入编辑卡用的虚拟行） */
export interface AdviceRowItem {
  key: string;
  isEditor?: boolean;
  status: string;
  type: string;
  content: string;
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
  resourceType?: string;
  requestId?: string;
  groupId?: string;
  statusEnum?: string;
  productDefinitionId?: string;
  adviceDisplayName?: string;
  specificationText?: string;
  conditionDefinitionName?: string;
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
}
