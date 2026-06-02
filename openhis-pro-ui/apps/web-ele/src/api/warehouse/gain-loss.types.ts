import type { ApiOptionItem } from '#/types/api-response';

/** 药库损益单初始化下拉 */
export interface GainLossInitResponse {
  cabinetListOptions: ApiOptionItem[];
  practitionerOption: ApiOptionItem[];
  supplierOption: ApiOptionItem[];
  supplyCategoryOptions: ApiOptionItem[];
  supplyStatusOptions: ApiOptionItem[];
  supplyTypeOptions: ApiOptionItem[];
  packagingConditionOptions?: ApiOptionItem[];
  acceptanceResultoryOptions?: ApiOptionItem[];
  pharmacyListOptions?: ApiOptionItem[];
}

export interface GainLossPageQuery {
  locationId?: number | string;
  keyword?: string;
}

export interface GainLossPageRecords {
  records: Record<string, unknown>[];
  total?: number;
}

export interface GainLossDetailQuery {
  busNo?: string;
  pageNo?: number;
  pageSize?: number;
}

export interface MedicationInfoQuery {
  locationId?: number | string;
  pageNo?: number;
  pageSize?: number;
  keyword?: string;
  supplierId?: number | string;
  typeEnum?: number;
}
