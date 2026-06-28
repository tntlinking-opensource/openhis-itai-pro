/**
 * 门诊队列/医生站侧栏：后端「已挂号」编码 REGISTERED 在前端统一展示为「待诊」。
 */
export function encounterQueueStatusDisplayText(
  statusEnum: null | string | undefined,
  statusEnumText: null | string | undefined,
): string {
  if (statusEnum === 'REGISTERED') return '待诊';
  const text = (statusEnumText ?? '').trim();
  if (text) return text;
  return (statusEnum ?? '').trim();
}
