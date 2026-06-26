import type { BookingEntityId } from './booking-id';

export type { BookingEntityId } from './booking-id';
export { bookingOptionValue, isSameBookingId, toBookingId } from './booking-id';

/** 统一下拉选项（对齐后端 OptionDto；value 为 Long 字符串） */
export interface BookingOptionDto {
  sort?: BookingEntityId | number;
  value?: BookingEntityId;
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
