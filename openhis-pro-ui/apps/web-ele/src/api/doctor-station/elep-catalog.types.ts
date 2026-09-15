export interface ElepCatalogItemDto {
  hilistCode?: string;
  hilistName?: string;
  spec?: string;
  dosform?: string;
  aprvno?: string;
  prodentpName?: string;
  pinyin?: string;
  valiFlag?: string;
  sourceInfno?: string;
}

export interface ElepCatalogPageResultDto {
  total?: number;
  page?: number;
  pageSize?: number;
  items?: ElepCatalogItemDto[];
}

export interface ElepCatalogSearchRequest {
  contractBusNo?: string;
  contractId?: string;
  keyword?: string;
  aprvno?: string;
  spec?: string;
  page?: number;
  pageSize?: number;
}

export type ElepCatalogPickerKind = 'diagnosis' | 'drug';
