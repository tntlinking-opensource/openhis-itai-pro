export type DrawDetailState = 'edit' | 'view';

export interface DrawStatusMeta {
  detailState: DrawDetailState;
  canDelete: boolean;
  canSubmit: boolean;
  canCancelReview: boolean;
}

/** 根据 statusEnum 返回领用单操作状态（参考调拨） */
export const getDrawStatusMeta = (statusEnum?: number | string): DrawStatusMeta => {
  const status = String(statusEnum ?? '');
  const meta: DrawStatusMeta = {
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
      meta.detailState = 'view';
      meta.canCancelReview = true;
      break;
    }
    case '3': {
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
