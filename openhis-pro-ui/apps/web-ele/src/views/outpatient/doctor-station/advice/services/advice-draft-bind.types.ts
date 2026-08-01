import type { AdviceBindSourceCacheKey } from './advice-bind-skip-cache.service';

import type { DoctorStationAdviceDraftBindItemDto } from '#/api/doctor-station/advice.types';

/** 绑定确认弹窗行：展示带出项并允许改数量 / 删行 */
export interface AdviceDraftBindDialogRow {
  key: string;
  bindItem: DoctorStationAdviceDraftBindItemDto;
  sourceItemName: string;
  sourceCacheKey: AdviceBindSourceCacheKey;
  /** 来源类型：用法 / 诊疗 */
  sourceKindLabel: string;
  targetItemName: string;
  targetKindLabel: string;
  quantity: number;
  unitCode: string;
  unitName: string;
  /**
   * 同用法跨组合并展示时：确认后按组展开的绑定项（含展示用首项）。
   * 未合并时可不设，落库仅用 bindItem。
   */
  fanOutBindItems?: DoctorStationAdviceDraftBindItemDto[];
}

/** 弹窗内按来源分组的「不再提示」选项 */
export interface AdviceDraftBindDialogSourceGroup {
  sourceCacheKey: AdviceBindSourceCacheKey;
  sourceLabel: string;
  /** 来源类型：用法 / 诊疗 */
  sourceKindLabel: string;
}

/** 弹窗确认结果 */
export interface AdviceDraftBindDialogConfirmResult {
  rows: AdviceDraftBindDialogRow[];
  skipSourceKeys: AdviceBindSourceCacheKey[];
}
