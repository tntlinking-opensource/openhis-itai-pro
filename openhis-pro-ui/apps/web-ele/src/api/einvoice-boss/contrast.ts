/**
 * 博思电子票据 — 收费对照维护 API（POST /api/app/einvoiceBossContrast/*）
 */
import { publicRequestClient } from '#/api/request';

const CONTRAST_BASE = '/einvoiceBossContrast';

export interface ChargeContrastDto {
  id?: string;
  orgId?: string;
  internalCode?: string;
  bossChargeCode?: string;
  bossChargeName?: string;
  updatedAt?: string;
}

export interface PageContrastsRequest {
  keyword?: string;
  pageNo?: number;
  pageSize?: number;
}

export interface PageContrastsResponse {
  rows?: ChargeContrastDto[];
  total?: number;
}

export interface SaveChargeContrastRequest {
  id?: string;
  orgId?: string;
  internalCode?: string;
  bossChargeCode?: string;
  bossChargeName?: string;
}

export function pageContrastsApi(request: PageContrastsRequest) {
  return publicRequestClient.post<PageContrastsResponse>(`${CONTRAST_BASE}/pageContrasts`, request);
}

export function saveContrastApi(request: SaveChargeContrastRequest) {
  return publicRequestClient.post<ChargeContrastDto>(`${CONTRAST_BASE}/saveContrast`, request);
}

export function deleteContrastApi(id: string) {
  return publicRequestClient.post<void>(`${CONTRAST_BASE}/deleteContrast`, { id });
}
