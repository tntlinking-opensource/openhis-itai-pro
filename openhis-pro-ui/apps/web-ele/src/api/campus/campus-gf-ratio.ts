/**
 * 公费比例（目录 / 项目）— POST /api/app/campusGfRatioCatalog|Item/<method>
 */
import type { CampusPagedResult, GfChargeMode, IdLike } from './types';

import { publicRequestClient } from '#/api/request';

export interface GfCatalogRatioDto {
  id?: IdLike;
  catalogCode?: string;
  chargeModeEnum?: GfChargeMode;
  chargeModeLabel?: string;
  ratio?: number;
  effectiveFrom?: string;
  effectiveTo?: string;
}

export interface GfItemRatioDto {
  id?: IdLike;
  itemCode?: string;
  chargeModeEnum?: GfChargeMode;
  chargeModeLabel?: string;
  ratio?: number;
  effectiveFrom?: string;
  effectiveTo?: string;
}

export function pageCatalogRatiosApi(params: {
  chargeModeEnum?: GfChargeMode | null;
  keyword?: string;
  pageNo: number;
  pageSize: number;
}) {
  return publicRequestClient.post<CampusPagedResult<GfCatalogRatioDto>>(
    '/campusGfRatioCatalog/pageCatalogRatios',
    params,
  );
}

export function saveCatalogRatioApi(params: {
  id?: IdLike | null;
  catalogCode: string;
  chargeModeEnum: GfChargeMode;
  ratio: number;
  effectiveFrom?: string;
  effectiveTo?: string;
}) {
  return publicRequestClient.post<{ id?: IdLike }>(
    '/campusGfRatioCatalog/saveCatalogRatio',
    params,
  );
}

export function disableCatalogRatioApi(params: { id: IdLike }) {
  return publicRequestClient.post<{ id?: IdLike }>(
    '/campusGfRatioCatalog/disableCatalogRatio',
    params,
  );
}

export function pageItemRatiosApi(params: {
  chargeModeEnum?: GfChargeMode | null;
  keyword?: string;
  pageNo: number;
  pageSize: number;
}) {
  return publicRequestClient.post<CampusPagedResult<GfItemRatioDto>>(
    '/campusGfRatioItem/pageItemRatios',
    params,
  );
}

export function saveItemRatioApi(params: {
  id?: IdLike | null;
  itemCode: string;
  chargeModeEnum: GfChargeMode;
  ratio: number;
  effectiveFrom?: string;
  effectiveTo?: string;
}) {
  return publicRequestClient.post<{ id?: IdLike }>('/campusGfRatioItem/saveItemRatio', params);
}

export function disableItemRatioApi(params: { id: IdLike }) {
  return publicRequestClient.post<{ id?: IdLike }>('/campusGfRatioItem/disableItemRatio', params);
}
