/** 将 requestClient（responseReturn: data）或完整 ApiResponse 归一为数组 */
export function unwrapApiList<T>(payload: unknown): T[] {
  if (Array.isArray(payload)) {
    return payload as T[];
  }
  if (
    payload !== null &&
    typeof payload === 'object' &&
    Array.isArray((payload as { data?: unknown }).data)
  ) {
    return (payload as { data: T[] }).data;
  }
  return [];
}
