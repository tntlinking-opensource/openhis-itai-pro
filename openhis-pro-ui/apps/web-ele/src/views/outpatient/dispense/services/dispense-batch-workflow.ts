/** 待发药队列/明细查询：待配药 + 已配待发 */
export const PENDING_DISPENSE_STATUS_FILTER = ['PREPARATION', 'IN_PROGRESS', 'PREPARED'] as const;

/** 批量发药可操作行（待配或已配待发） */
export function isBatchDispenseActionableStatus(status: null | string | undefined): boolean {
  return status === 'PREPARATION' || status === 'IN_PROGRESS' || status === 'PREPARED';
}

/** 已保存配药，可直接核发发药，无需重复 savePrepare */
export function isAlreadyPreparedDispenseStatus(status: null | string | undefined): boolean {
  return status === 'IN_PROGRESS' || status === 'PREPARED';
}
