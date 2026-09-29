/**
 * 收费折扣配置 — POST /api/app/chargeDiscountConfig/<method>
 */
import type { ApiOptionItem, PageParams } from '#/types/api-response';

import { publicRequestClient } from '#/api/request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

/** 配置状态 code（仅 ACTIVE 参与门控与命中） */
export const CHARGE_DISCOUNT_STATUS = {
  ACTIVE: 'ACTIVE',
  INACTIVE: 'INACTIVE',
} as const;

export type ChargeDiscountStatusCode =
  (typeof CHARGE_DISCOUNT_STATUS)[keyof typeof CHARGE_DISCOUNT_STATUS];

/** 选品检索模式（仅 UI，不落库） */
export const DISCOUNT_CATALOG_SEARCH_MODE = {
  CATEGORY: 'CATEGORY',
  ITEM: 'ITEM',
} as const;

export type DiscountCatalogSearchMode =
  (typeof DISCOUNT_CATALOG_SEARCH_MODE)[keyof typeof DISCOUNT_CATALOG_SEARCH_MODE];

/** 行形态（仅 UI：大类行 / 单项行） */
export type DiscountRowShape = 'CATEGORY' | 'PRODUCT';

export interface ChargeDiscountConfigItem {
  id?: string;
  productTable?: string;
  productTableName?: string;
  categoryCode?: null | string;
  categoryName?: null | string;
  productId?: null | string;
  productName?: null | string;
  discountRate?: null | number | string;
  statusEnum?: string;
  statusEnumText?: string;
  remark?: null | string;
  orgId?: null | string;
}

export interface ChargeDiscountConfigPageInit {
  productTableOptions?: ApiOptionItem[];
  statusOptions?: ApiOptionItem[];
}

export interface PageChargeDiscountConfigsParams extends PageParams {
  productTable?: string;
  statusEnum?: string;
  keyword?: string;
}

export interface CreateChargeDiscountConfigParams {
  productTable: string;
  categoryCode?: null | string;
  /** 雪花主键以 string 提交 */
  productId?: null | string;
  /** null=款员定率；有值须 (0,1] */
  discountRate?: null | number;
  remark?: null | string;
}

export interface ModifyChargeDiscountConfigParams extends CreateChargeDiscountConfigParams {
  id: string;
}

export interface DiscountCatalogOption {
  id?: string;
  value?: string;
  label?: string;
  busNo?: string;
  /** 大类模式下：本院目录挂项数 */
  itemCount?: number;
}

export interface SearchDiscountCatalogOptionsParams extends PageParams {
  productTable: string;
  searchMode: DiscountCatalogSearchMode;
  keyword?: string;
}

/** 判断配置是否为有效态 */
export function isChargeDiscountConfigActive(statusEnum: null | string | undefined): boolean {
  return String(statusEnum ?? '').trim() === CHARGE_DISCOUNT_STATUS.ACTIVE;
}

/** 由列表行推断 UI 行形态 */
export function resolveDiscountRowShape(
  row: Pick<ChargeDiscountConfigItem, 'categoryCode' | 'productId'>,
): DiscountRowShape {
  const productId = preserveApiIdForRequest(row.productId);
  if (productId) {
    return 'PRODUCT';
  }
  return 'CATEGORY';
}

/** 折扣率 → 折（0.85 → 8.5）；非法返回 null */
export function discountRateToZhe(rate: null | number | string | undefined): null | number {
  if (rate === null || rate === undefined || rate === '') {
    return null;
  }
  const n = Number(rate);
  if (!Number.isFinite(n) || n <= 0 || n > 1) {
    return null;
  }
  // 保留一位小数，与「8.5折」录入对齐
  return Math.round(n * 1000) / 100;
}

/** 折 → 折扣率（8.5 → 0.85）；非法返回 null */
export function zheToDiscountRate(zhe: null | number | string | undefined): null | number {
  if (zhe === null || zhe === undefined || zhe === '') {
    return null;
  }
  const n = Number(zhe);
  if (!Number.isFinite(n) || n <= 0 || n > 10) {
    return null;
  }
  return Math.round(n * 1000) / 10000;
}

/** 折扣率展示：空=款员定；有值展示「x折」 */
export function formatDiscountRateDisplay(rate: null | number | string | undefined): string {
  if (rate === null || rate === undefined || rate === '') {
    return '款员定';
  }
  const zhe = discountRateToZhe(rate);
  if (zhe === null) {
    return String(rate);
  }
  const text = Number.isInteger(zhe) ? String(zhe) : zhe.toFixed(1).replace(/\.0$/, '');
  return `${text}折`;
}

export async function loadChargeDiscountConfigPageInitApi() {
  return publicRequestClient.post<ChargeDiscountConfigPageInit>(
    '/chargeDiscountConfig/loadChargeDiscountConfigPageInit',
    {},
  );
}

export async function pageChargeDiscountConfigsApi(params: PageChargeDiscountConfigsParams) {
  return publicRequestClient.post<unknown>(
    '/chargeDiscountConfig/pageChargeDiscountConfigs',
    params,
  );
}

export async function createChargeDiscountConfigApi(params: CreateChargeDiscountConfigParams) {
  return publicRequestClient.post<{ id?: string }>(
    '/chargeDiscountConfig/createChargeDiscountConfig',
    {
      productTable: params.productTable,
      categoryCode: params.categoryCode ?? null,
      productId: preserveApiIdForRequest(params.productId) ?? null,
      discountRate: params.discountRate ?? null,
      remark: params.remark ?? null,
    },
  );
}

export async function modifyChargeDiscountConfigApi(params: ModifyChargeDiscountConfigParams) {
  return publicRequestClient.post<void>('/chargeDiscountConfig/modifyChargeDiscountConfig', {
    id: preserveApiIdForRequest(params.id),
    productTable: params.productTable,
    categoryCode: params.categoryCode ?? null,
    productId: preserveApiIdForRequest(params.productId) ?? null,
    discountRate: params.discountRate ?? null,
    remark: params.remark ?? null,
  });
}

export async function changeChargeDiscountConfigStatusApi(params: {
  id: string;
  statusEnum: ChargeDiscountStatusCode;
}) {
  return publicRequestClient.post<void>('/chargeDiscountConfig/changeChargeDiscountConfigStatus', {
    id: preserveApiIdForRequest(params.id),
    statusEnum: params.statusEnum,
  });
}

export async function deleteChargeDiscountConfigApi(params: { id: string }) {
  return publicRequestClient.post<void>('/chargeDiscountConfig/deleteChargeDiscountConfig', {
    id: preserveApiIdForRequest(params.id),
  });
}

export async function searchDiscountCatalogOptionsApi(params: SearchDiscountCatalogOptionsParams) {
  return publicRequestClient.post<unknown>(
    '/chargeDiscountConfig/searchDiscountCatalogOptions',
    params,
  );
}
