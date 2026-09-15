/** Opensource stub — APIs disabled. */
import type { ApiResponse } from '#/types/api-response';

function disabled<T>(): Promise<ApiResponse<T>> {
  return Promise.reject(new Error('Optional module disabled in opensource build'));
}

export interface MsfxSupplierMapItem {
  id?: number | string;
  supplierId?: number | string;
  msfxEntId?: string;
  msfxEntName?: string;
  msfxRefEntId?: string;
  [key: string]: unknown;
}

export interface MsfxSupplierMapPageParams {
  pageNo?: number;
  pageSize?: number;
  [key: string]: unknown;
}

export interface MsfxSupplierMapPageResult {
  records?: MsfxSupplierMapItem[];
  total?: number;
}

export interface MsfxSupplierMapInitResult {
  [key: string]: unknown;
}

export interface MsfxSupplierMapSaveParams {
  [key: string]: unknown;
}

export type MsfxMapStatus = 'ALL' | 'MAPPED' | 'UNMAPPED' | '';

export interface MsfxHisSupplierWorkbenchRow {
  [key: string]: unknown;
}

export interface MsfxPlatformPartnerWorkbenchRow {
  [key: string]: unknown;
}

export interface MsfxPlatformPartnerPageResult {
  records?: MsfxPlatformPartnerWorkbenchRow[];
  total?: number;
}

export function initMsfxSupplierMapApi() {
  return disabled<MsfxSupplierMapInitResult>();
}

export function pageMsfxSupplierMapApi(_data: MsfxSupplierMapPageParams) {
  return disabled<MsfxSupplierMapPageResult>();
}

export function getMsfxSupplierMapApi(_data: { id: number | string }) {
  return disabled<MsfxSupplierMapItem | null>();
}

export function getMsfxSupplierMapBySupplierIdApi(_data: { supplierId: number | string }) {
  return disabled<MsfxSupplierMapItem | null>();
}

export function saveMsfxSupplierMapApi(_data: MsfxSupplierMapSaveParams) {
  return disabled<MsfxSupplierMapItem>();
}

export function deleteMsfxSupplierMapApi(_data: { id: number | string }) {
  return disabled<void>();
}

export function pageMsfxHisSuppliersApi(_data: Record<string, unknown>) {
  return disabled<{ records?: MsfxHisSupplierWorkbenchRow[]; total?: number }>();
}

export function pageMsfxPlatformPartnersApi(_data: Record<string, unknown>) {
  return disabled<MsfxPlatformPartnerPageResult>();
}

export function pullAllMsfxPlatformPartnersApi(_data: Record<string, unknown>) {
  return disabled<MsfxPlatformPartnerPageResult>();
}
