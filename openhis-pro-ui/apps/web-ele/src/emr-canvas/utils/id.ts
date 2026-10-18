/** 雪花主键保持字符串，禁止 Number() 丢精度 */
export function preserveApiId(value: number | string | null | undefined): string | undefined {
  if (value === null || value === undefined) return undefined;
  const text = String(value).trim();
  return text ? text : undefined;
}
