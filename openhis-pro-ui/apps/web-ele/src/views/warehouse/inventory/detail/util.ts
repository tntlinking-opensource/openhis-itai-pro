export type InventoryDetailState = 'edit' | 'view';

export interface InventoryStatusMeta {
  detailState: InventoryDetailState;
  canDelete: boolean;
  canSubmit: boolean;
  canCancelReview: boolean;
}

/** 根据 statusEnum 返回盘点单操作状态（参考调拨） */
export const getInventoryStatusMeta = (statusEnum?: number | string): InventoryStatusMeta => {
  const status = String(statusEnum ?? '');
  const meta: InventoryStatusMeta = {
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
