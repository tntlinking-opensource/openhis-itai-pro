export interface WarehouseTypeOptionItem {
  label: string;
  value: number | string;
  locationOption: { id: number | string; name: string }[];
  orderOption: { label: string; value: number | string }[];
}

export interface DocumentManagementInitData {
  warehouseTypeOption: WarehouseTypeOptionItem[];
  supplyStatusOptions: { label: string; value: number | string }[];
}

export interface DocumentManagementPageQuery {
  pageNo?: number;
  pageSize?: number;
  locationTypeEnum?: number | string | null;
  typeEnum?: number | string | null;
  locationId?: number | string | null;
  statusEnum?: number | string | null;
  applyTimeSTime?: null | string;
  applyTimeETime?: null | string;
  approvalTimeSTime?: null | string;
  approvalTimeETime?: null | string;
}
