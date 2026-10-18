/**
 * 麻醉申请常用的院点预置。
 *
 * 主线为空。国基在
 * `projects/project-H22010202493/overrides/views/inpatient/doctor-station/surgery-anesth/anesth-favorite-defaults.config.ts`
 * 写入默认编码和顺序。
 *
 * 须以 `#/views/inpatient/doctor-station/surgery-anesth/anesth-favorite-defaults.config` 导入，
 * 相对路径不走院点 override。
 */
export type AnesthFavoriteKind = 'medication' | 'service';

export interface AnesthFavoriteSeed {
  kind: AnesthFavoriteKind;
  /** 目录项目编码，与选品表「项目编码」一致 */
  code: string;
}

export const ANESTH_FAVORITE_SEEDS: AnesthFavoriteSeed[] = [];
