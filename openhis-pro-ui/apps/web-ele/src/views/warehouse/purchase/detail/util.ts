import type { ProductDetail } from '#/types/common';

import { calcMoneyLineTotal, sumMoneyThenSettle } from '#/utils/format/money-scale';

export const calcPurchasePrice = (partPercent: number, unitPrice: number) =>
  calcMoneyLineTotal(unitPrice, partPercent);

export const calcTotalPrice = (total: number, price: number) => {
  return calcMoneyLineTotal(price, total);
};

export const calcAllTotalPrice = (supplyList: ProductDetail[]) => {
  return sumMoneyThenSettle(supplyList.map((item) => item.totalSalePrice ?? 0));
};

export const validRules = {
  productInfo: [{ required: true, message: '必须填写' }],
  volume: [{ required: true, message: '必须填写' }],
  manufacturerText: [{ required: true, message: '必须填写' }],
  unitCode: [{ required: true, message: '必须填写' }],
  itemQuantity: [{ required: true, message: '必须填写' }],
  price: [{ required: true, message: '必须填写' }],
  totalPrice: [{ required: true, message: '必须填写' }],
  lotNumber: [{ required: true, message: '必须填写' }],
  startTime: [{ required: true, message: '必须填写' }],
  endTime: [{ required: true, message: '必须填写' }],
  invoiceNo: [{ required: true, message: '必须填写' }],
};

export const defaultValues = {
  productInfo: '',
  volume: '',
  manufacturerText: '',
  unitCode: '',
  itemQuantity: '',
  price: '',
  totalSalePrice: '',
  lotNumber: '',
  startTime: '',
  endTime: '',
};

export type PurchaseDetailState = 'edit' | 'view';

export interface PurchaseStatusMeta {
  /** 抽屉打开时是编辑还是查看 */
  detailState: PurchaseDetailState;
  /** 是否允许删除 */
  canDelete: boolean;
  /** 是否允许退库 */
  canReturn: boolean;
  /** 是否允许提交审批 */
  canSubmit: boolean;
  /** 是否允许撤销审批 */
  canCancelReview: boolean;
}

/**
 * 集中根据 statusEnum 计算所有与操作相关的状态
 */
export const getPurchaseStatusMeta = (statusEnum?: number | string): PurchaseStatusMeta => {
  const status = String(statusEnum ?? '');

  // 默认：可编辑，其他操作禁用
  const meta: PurchaseStatusMeta = {
    detailState: 'edit',
    canDelete: false,
    canReturn: false,
    canSubmit: false,
    canCancelReview: false,
  };

  switch (status) {
    case '1': // 待审核
    case '9': {
      // 已撤回
      meta.detailState = 'edit';
      meta.canDelete = true;
      meta.canSubmit = true;
      break;
    }
    case '2': {
      // 审核中
      meta.detailState = 'view';
      meta.canCancelReview = true;
      break;
    }
    case '3': {
      // 同意
      meta.detailState = 'view';
      meta.canReturn = true;
      break;
    }
    default: {
      // 其他状态一律按只读处理
      meta.detailState = 'view';
      break;
    }
  }

  return meta;
};
