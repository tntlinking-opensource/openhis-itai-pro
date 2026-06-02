export type TransferDetailState = 'edit' | 'view';

export interface TransferStatusMeta {
  detailState: TransferDetailState;
  canDelete: boolean;
  canSubmit: boolean;
  canCancelReview: boolean;
}

/**
 * 根据 statusEnum 返回调拨单操作状态（参考采购）
 */
export const getTransferStatusMeta = (statusEnum?: number | string): TransferStatusMeta => {
  const status = String(statusEnum ?? '');
  const meta: TransferStatusMeta = {
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
  productInfo: [{ required: true, message: '必须填写' }],
  unitCode: [{ required: true, message: '必须填写' }],
  itemQuantity: [{ required: true, message: '必须填写' }],
};
