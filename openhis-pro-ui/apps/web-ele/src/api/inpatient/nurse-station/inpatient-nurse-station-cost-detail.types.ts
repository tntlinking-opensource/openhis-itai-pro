/** 费用明细展示模式 */
export type NurseStationCostDetailPresentationMode = 'DETAIL' | 'SUMMARY';

/** 费用明细状态筛选（与 ChargeItemStatus code 一致） */
export type NurseStationCostDetailStatusFilter = 'PENDING_SETTLEMENT' | 'SETTLED';

/** 费用明细页初始化响应 */
export interface LoadNurseStationCostDetailPageInitResponse {
  dateFrom?: string;
  dateTo?: string;
  defaultStatusEnum?: NurseStationCostDetailStatusFilter | string;
  presentationOptions?: NurseStationCostDetailPresentationOptionDto[];
  statusFilterOptions?: NurseStationCostDetailPresentationOptionDto[];
  expenseBlockOrder?: string[];
}

/** 展示模式选项 */
export interface NurseStationCostDetailPresentationOptionDto {
  code?: NurseStationCostDetailPresentationMode | string;
  display?: string;
}

/** 查询住院费用明细请求 */
export interface QueryNurseStationInpatientCostDetailRequest {
  encounterId: string;
  patientId: string;
  dateFrom: string;
  dateTo: string;
  presentationMode: NurseStationCostDetailPresentationMode;
  statusEnum: NurseStationCostDetailStatusFilter;
}

/** 查询住院费用明细响应 */
export interface QueryNurseStationInpatientCostDetailResponse {
  presentationMode?: NurseStationCostDetailPresentationMode | string;
  detailLines?: NurseStationCostDetailLineDto[];
  summaryLines?: NurseStationCostSummaryLineDto[];
  totals?: NurseStationCostDetailTotalsDto;
}

/** 费用明细行 */
export interface NurseStationCostDetailLineDto {
  chargeItemId?: number | string;
  productId?: number | string;
  productTableName?: string;
  itemName?: string;
  itemSpec?: string;
  quantityValue?: number | string;
  quantityUnit?: string;
  quantityUnitDisplay?: string;
  unitPrice?: number | string;
  totalPrice?: number | string;
  businessDateTime?: string;
  statusEnum?: string;
  statusDisplay?: string;
  generateSourceEnum?: string;
  generateSourceDisplay?: string;
  refundHighlight?: boolean;
  serviceTable?: string;
  expenseBlockCode?: string;
}

/** 费用汇总行 */
export interface NurseStationCostSummaryLineDto {
  productId?: number | string;
  productTableName?: string;
  itemName?: string;
  itemSpec?: string;
  categoryCode?: string;
  categoryDisplay?: string;
  unitPrice?: number | string;
  lineTotalAmount?: number | string;
  packageQuantity?: number;
  splitUnitQuantity?: number | string;
  packageUnitCode?: string;
  packageUnitDisplay?: string;
  splitUnitCode?: string;
  splitUnitDisplay?: string;
  expenseBlockCode?: string;
  expenseBlockDisplay?: string;
}

/** 费用合计 */
export interface NurseStationCostDetailTotalsDto {
  medicationTotal?: number | string;
  serviceTotal?: number | string;
  deviceTotal?: number | string;
  categoryTotals?: NurseStationCostCategoryTotalDto[];
  grandTotal?: number | string;
}

/** 分类小计 */
export interface NurseStationCostCategoryTotalDto {
  categoryCode?: string;
  categoryDisplay?: string;
  amount?: number | string;
}
