export interface PurchaseList {
  id: string;
  productName: string;
  totalVolume: null | number;
  manufacturer: null | string;
  purposeLocationId: null | string;
  unitCode: string;
  unitCodeDictText: null | string;
  price: null | number;
  remake: null | string;
}

export interface ProductDetail {
  endTime: null | string;
  id: string;
  manufacturer: string;
  minUnitCode: string;
  minUnitCodeDictText: null | string;
  partPercent: number;
  price: number;
  productName: string;
  purposeLocationId: number;
  startTime: null;
  totalVolume: string;
  unitCode: string;
  unitCodeDictText: null | string;
  unitConversionRatio: number;
  totalSalePrice: number;
  itemQuantity: number;
  productDefinitionId: string;
  startTime: string;
  endTime: string;
  manufacturerText: string;
  unitList: ApiOptionItem[];
  isUnit: boolean;
  // 前端使用
  productInfo: ProductDetail;
}
