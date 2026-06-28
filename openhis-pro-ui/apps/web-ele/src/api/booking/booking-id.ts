/**
 * 后端 Long 主键在 JS 中必须以 string 处理。
 * 例如 322663218269917184 经 Number() 会变成 322663218269917200，导致查不到数据。
 */
export type BookingEntityId = string;

export function toBookingId(raw: unknown): BookingEntityId | undefined {
  if (raw === null || raw === undefined || raw === '') {
    return undefined;
  }
  const value = String(raw).trim();
  return value.length > 0 ? value : undefined;
}

export function isSameBookingId(a: unknown, b: unknown): boolean {
  const left = toBookingId(a);
  const right = toBookingId(b);
  return left !== undefined && left === right;
}

/** ElOption :value — 禁止 Number()，直接保留 string */
export function bookingOptionValue(raw: unknown): BookingEntityId {
  return toBookingId(raw) ?? '';
}
