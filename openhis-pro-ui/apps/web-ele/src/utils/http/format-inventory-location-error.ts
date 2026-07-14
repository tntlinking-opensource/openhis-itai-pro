/** 将库存库位越权类后端报错转为用户可理解的提示。 */
export function formatInventoryLocationError(rawMessage: string): string | null {
  const msg = String(rawMessage ?? '').trim();
  if (!msg) {
    return null;
  }

  if (msg.includes('药库位置不在当前用户可选药库范围')) {
    return '请先在单头选择具体药库（不能选「全部库房」）';
  }
  if (msg.includes('药房位置不在当前用户可选药房范围')) {
    return '请先在单头选择具体药房（不能选「全部药房」）';
  }
  if (msg.includes('科室药房不在当前用户可选范围')) {
    return '请先在单头选择具体科室药房（不能选「全部科室药房」）';
  }
  if (msg.includes('目的药房') && msg.includes('不在当前用户可选')) {
    return '请先在单头选择具体目的药房';
  }
  if (msg.includes('目的药库') && msg.includes('不在当前用户可选')) {
    return '请先在单头选择具体目的药库';
  }
  if (msg.includes('不在当前用户可选')) {
    return '请先在单头选择具体库位后再操作';
  }

  return null;
}
