import type {
  GetInfoParams,
  PageApiRequest,
  PageApiResponse,
  PageParams,
} from '#/types/api-response';
import type { ProductDetail, PurchaseList } from '#/types/common';

import { publicRequestClient } from '#/api/request';

import {
  mockGetProductDetail,
  mockGetProductList,
  usePublicProductListMock,
} from './public-list.mock';

export interface ResponsibilityDept {
  id: string;
  name: string;
  displayOrder: number;
  typeEnum: number;
  typeEnumText: string;
  classEnum: number;
  classEnumText: string;
  ybNo: string;
  ybName: string;
  activeFlag: number;
  activeFlag_dictText: string;
  children: ResponsibilityDept[];
  busNo: string;
}

export interface ChargeType {
  sort: string;
  value: string;
  label: string;
  busNo: string;
  /** 费用性质分类（ContractCategory code，如 SELF_CHARGE） */
  categoryEnum?: string;
  children: ChargeType[];
}

export interface InventoryItemDetail {
  itemId: string;
  manufacturer: string;
  price: number;
  expirationDate: string;
  id: string;
  lotNumber: string;
  name: string;
  partPercent: number;
  productionDate: string;
  totalQuantity: number; // 库存数量
  supplierId: string;
  supplierName: string;
  unitCode: string;
  unitCode_dictText: string;
  minUnitCode: string;
  minUnitCode_dictText: string;
  volume: string;
  itemQuantity: number; // 调拨数量
  itemTable?: string;
}

export interface PublicProductListSearchParams {
  sortBy: string;
  productTableName: string;
  keyword: string;
  categoryEnum: string;
}
export async function getProductListApi(
  params: PageApiRequest & PublicProductListSearchParams,
): Promise<PageApiResponse<PurchaseList>> {
  if (usePublicProductListMock()) {
    return mockGetProductList(params);
  }
  return publicRequestClient.post('/publicList/getProductList', params);
}

export async function getProductDetailApi(
  params: GetInfoParams,
): Promise<ProductDetail | undefined> {
  if (usePublicProductListMock()) {
    return mockGetProductDetail(params);
  }
  return publicRequestClient.post('/publicList/getProductDetail', params);
}

export async function getTreeForResponsibilityDeptApi() {
  return publicRequestClient.post<ResponsibilityDept[]>('/department/getTreeForResponsibilityDept');
}

export async function getChargeTypeListApi() {
  type ContractItem = {
    busNo?: string;
    categoryEnum?: string;
    displayOrder?: number;
    id: string;
    name: string;
  };
  const list = await publicRequestClient.post<ContractItem[]>('/chargeType/getChargeTypeList');
  return (list || []).map<ChargeType>((item, index) => ({
    // sort：显示顺序，没有则用索引
    sort: String(item.displayOrder ?? index),
    // value：费用性质主键 ID
    value: String(item.id ?? ''),
    // label：费用性质名称
    label: item.name ?? '',
    // busNo：费用性质业务编码
    busNo: item.busNo ?? '',
    categoryEnum: item.categoryEnum,
    // children：当前为平铺结构，无子节点
    children: [],
  }));
}

export async function getWarehouseProductListApi(
  params: PageParams & { locationId: string; keyword: string },
) {
  return publicRequestClient.post<PageApiResponse<PurchaseList>>(
    '/publicList/getInventoryItemList',
    params,
  );
}

export async function getInventoryItemDetailApi(params: GetInfoParams) {
  return publicRequestClient.post<InventoryItemDetail>(
    '/publicList/getInventoryItemDetail',
    params,
  );
}
