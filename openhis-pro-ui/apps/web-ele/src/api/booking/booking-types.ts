/** 统一下拉选项（对齐后端 OptionDto） */
export interface BookingOptionDto {
  sort?: number;
  value?: string;
  label?: string;
  busNo?: string;
  children?: BookingOptionDto[];
}

export interface BookingPagedResult<T> {
  content?: T[];
  totalCount?: number;
  pageNo?: number;
  pageSize?: number;
}
