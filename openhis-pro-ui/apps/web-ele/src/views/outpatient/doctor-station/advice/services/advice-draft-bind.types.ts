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
   * 同用法跨组合并时记录各组触发项（来源「N 组」展示）。
   * 诊疗（输液费）落库只用首项一份；耗材按组 fanOut 各落一份。
   * 耗材合并时 {@link quantity} 为合计（模板量×组数）；落库再均分到各组。未合并时可不设。
   */
  fanOutBindItems?: DoctorStationAdviceDraftBindItemDto[];
}

/** 弹窗确认结果 */
export interface AdviceDraftBindDialogConfirmResult {
  rows: AdviceDraftBindDialogRow[];
}
