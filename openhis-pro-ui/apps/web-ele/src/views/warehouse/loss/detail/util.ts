export type LossDetailState = 'edit' | 'view';

export interface LossStatusMeta {
  detailState: LossDetailState;
  canDelete: boolean;
  canSubmit: boolean;
  canCancelReview: boolean;
}

/**
 * 根据 statusEnum 返回报损单操作状态（参考调拨/采购）
 */
export const getLossStatusMeta = (statusEnum?: number | string): LossStatusMeta => {
  const status = String(statusEnum ?? '');
  const meta: LossStatusMeta = {
    detailState: 'view',
    canDelete: false,
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
      break;
    }
    default: {
      break;
    }
  }
  return meta;
};

export const validRules = {
  unitCode: [{ required: true, message: '必须填写' }],
  itemQuantity: [{ required: true, message: '必须填写' }],
  productInfo: [{ required: true, message: '必须填写' }],
};
