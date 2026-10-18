/* eslint-disable @typescript-eslint/no-explicit-any -- mock 与后端宽松对齐 */
import type { GetInfoParams, PageApiRequest, PageApiResponse } from '#/types/api-response';
import type { ApiOptionItem } from '#/types/api-response';
import type { ProductDetail, PurchaseList } from '#/types/common';

const MOCK_DELAY_MS = 120;

function delay<T>(value: T): Promise<T> {
  return new Promise((resolve) => {
    setTimeout(() => resolve(value), MOCK_DELAY_MS);
  });
}

/** 与订货等模块 mock 共用开关（进货单选品已走 ZXPlat IF-WHS-IN-11，不依赖本开关） */
export function usePublicProductListMock(): boolean {
  return import.meta.env.VITE_USE_MOCK_PURCHASE_REQUEST === 'true';
}

/** 下拉表格列表行（含 productBusNo 供列展示） */
type ProductListRow = PurchaseList & { productBusNo?: string };

/** 选品详情：与 ProductSelectTable / 订货选品字段对齐 */
const PRODUCT_DETAIL_BY_ID = new Map<string, ProductDetail>();

function registerProduct(p: ProductDetail) {
  PRODUCT_DETAIL_BY_ID.set(String(p.id), p);
}

function buildUnitList(
  main: string,
  mainText: string,
  sub?: string,
  subText?: string,
): ApiOptionItem[] {
  const list: ApiOptionItem[] = [{ label: mainText, value: main }];
  if (sub && subText) list.push({ label: subText, value: sub });
  return list;
}

registerProduct({
  id: 'pub-p-1',
  productDefinitionId: 'pub-p-1',
  productName: '阿莫西林胶囊',
  manufacturer: '华北制药',
  manufacturerText: '华北制药',
  totalVolume: '0.25g×24粒',
  unitCode: 'BOX',
  unitCodeDictText: '盒',
  minUnitCode: 'PCS',
  minUnitCodeDictText: '粒',
  price: 12.5,
  partPercent: 24,
  purposeLocationId: 1,
  unitConversionRatio: 1,
  totalSalePrice: 0,
  itemQuantity: 0,
  startTime: '',
  endTime: '',
  unitList: buildUnitList('BOX', '盒', 'PCS', '粒'),
  isUnit: true,
} as ProductDetail);

registerProduct({
  id: 'pub-p-2',
  productDefinitionId: 'pub-p-2',
  productName: '氯化钠注射液',
  manufacturer: '科伦药业',
  manufacturerText: '科伦药业',
  totalVolume: '0.9% 250ml',
  unitCode: 'BAG',
  unitCodeDictText: '袋',
  minUnitCode: 'BAG',
  minUnitCodeDictText: '袋',
  price: 3.2,
  partPercent: 1,
  purposeLocationId: 1,
  unitConversionRatio: 1,
  totalSalePrice: 0,
  itemQuantity: 0,
  startTime: '',
  endTime: '',
  unitList: buildUnitList('BAG', '袋'),
  isUnit: true,
} as ProductDetail);

registerProduct({
  id: 'pub-p-3',
  productDefinitionId: 'pub-p-3',
  productName: '布洛芬缓释胶囊',
  manufacturer: '芬必得制药',
  manufacturerText: '芬必得制药',
  totalVolume: '0.3g×20粒',
  unitCode: 'BOX',
  unitCodeDictText: '盒',
  minUnitCode: 'PCS',
  minUnitCodeDictText: '粒',
  price: 8.6,
  partPercent: 20,
  purposeLocationId: 1,
  unitConversionRatio: 1,
  totalSalePrice: 0,
  itemQuantity: 0,
  startTime: '',
  endTime: '',
  unitList: buildUnitList('BOX', '盒', 'PCS', '粒'),
  isUnit: true,
} as ProductDetail);

registerProduct({
  id: 'pub-p-4',
  productDefinitionId: 'pub-p-4',
  productName: '维生素C片',
  manufacturer: '东北制药',
  manufacturerText: '东北制药',
  totalVolume: '100mg×100片',
  unitCode: 'BOT',
  unitCodeDictText: '瓶',
  minUnitCode: 'BOT',
  minUnitCodeDictText: '瓶',
  price: 6.8,
  partPercent: 1,
  purposeLocationId: 1,
  unitConversionRatio: 1,
  totalSalePrice: 0,
  itemQuantity: 0,
  startTime: '',
  endTime: '',
  unitList: buildUnitList('BOT', '瓶'),
  isUnit: true,
} as ProductDetail);

registerProduct({
  id: 'pub-p-5',
  productDefinitionId: 'pub-p-5',
  productName: '一次性使用无菌注射器',
  manufacturer: '威高集团',
  manufacturerText: '威高集团',
  totalVolume: '5ml',
  unitCode: 'BOX',
  unitCodeDictText: '盒',
  minUnitCode: 'PCS',
  minUnitCodeDictText: '支',
  price: 0.45,
  partPercent: 100,
  purposeLocationId: 1,
  unitConversionRatio: 1,
  totalSalePrice: 0,
  itemQuantity: 0,
  startTime: '',
  endTime: '',
  unitList: buildUnitList('BOX', '盒', 'PCS', '支'),
  isUnit: true,
} as ProductDetail);

function toListRow(p: ProductDetail): ProductListRow {
  return {
    id: p.id,
    productName: p.productName,
    totalVolume: p.totalVolume as null | number,
    manufacturer: p.manufacturer,
    purposeLocationId: null,
    unitCode: p.unitCode,
    unitCodeDictText: p.unitCodeDictText,
    price: p.price,
    remake: null,
    productBusNo: `YP-${String(p.id).replace('pub-p-', '')}`,
  };
}

const ALL_LIST_ROWS: ProductListRow[] = [...PRODUCT_DETAIL_BY_ID.values()].map(toListRow);

export async function mockGetProductList(
  params: PageApiRequest & Record<string, any>,
): Promise<PageApiResponse<ProductListRow>> {
  const pageNo = Math.max(1, Number(params.pageNo) || 1);
  const pageSize = Math.max(1, Number(params.pageSize) || 20);
  const keyword = String(params.keyword ?? '')
    .trim()
    .toLowerCase();
  let rows = ALL_LIST_ROWS;
  if (keyword) {
    rows = rows.filter(
      (r) =>
        String(r.productName).toLowerCase().includes(keyword) ||
        String(r.productBusNo ?? '')
          .toLowerCase()
          .includes(keyword),
    );
  }
  const total = rows.length;
  const start = (pageNo - 1) * pageSize;
  const content = rows.slice(start, start + pageSize);
  return delay({
    content,
    pageNo,
    pageSize,
    totalCount: total,
    totalElements: total,
  });
}

export async function mockGetProductDetail(params?: GetInfoParams): Promise<ProductDetail> {
  const id = String(params?.id ?? '');
  const detail = PRODUCT_DETAIL_BY_ID.get(id);
  return delay(detail ? structuredClone(detail) : ({} as ProductDetail));
}
