/** 调价单审批状态（与后端 PriceAdjustmentStatus code 一致） */
export type PriceAdjustmentStatusEnum =
  | 'approved'
  | 'deprecated'
  | 'draft'
  | 'pending'
  | 'rejected';

/** 批号明细行（对齐 PriceAdjustmentDetailDto） */
export interface PriceAdjustmentDetailRow {
  id?: string;
  lotNumber?: string;
  quantity?: number;
  oldPurchaseAmount?: number;
  oldSellingAmount?: number;
  purchaseImpact?: number;
  sellingImpact?: number;
}

/** 调价项目行（对齐 PriceAdjustmentLineDto + 编辑态） */
export interface PriceAdjustmentLineRow {
  id?: string;
  itemTable?: string;
  itemTableText?: string;
  itemId?: string;
  /** 行内选品单元格（同构药库进货单 medicinePick） */
  itemPick?: { id?: string; name?: string };
  itemName?: string;
  itemCode?: string;
  specification?: string;
  manufacturer?: string;
  categoryCodeDictText?: string;
  unitName?: string;
  /** 目录当前进价（选品 enrich；已保存单取自批号快照调前价） */
  currentPurchasePrice?: number;
  /** 目录当前售价（选品 enrich；诊疗取自调前售价） */
  currentSellingPrice?: number;
  /** 库存数量（物流：批号快照 quantity 合计） */
  quantity?: number;
  newPurchaseAmount?: number | string;
  newSellingAmount?: number | string;
  oldSellingAmount?: number;
  adjustmentReason?: string;
  adjustmentReasonText?: string;
  adjustmentTime?: string;
  adjustmentStyle?: string;
  adjustmentStyleText?: string;
  orgId?: string;
  details?: PriceAdjustmentDetailRow[];
}

/** 单头表单（对齐 SavePriceAdjustmentDraftRequest 头字段） */
export interface PriceAdjustmentHeadForm {
  busNo?: string;
  orgId?: string;
  adjustmentReason?: string;
  adjustmentTime?: string;
}

/** 盈亏汇总（对齐详情 totalPurchaseImpact / totalSellingImpact） */
export interface PriceAdjustmentImpactSummary {
  totalPurchaseImpact?: number;
  totalSellingImpact?: number;
}
