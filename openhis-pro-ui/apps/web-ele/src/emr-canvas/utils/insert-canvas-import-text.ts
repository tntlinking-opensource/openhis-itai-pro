import type { Command, IElement, IRange } from '@hufe921/canvas-editor';

/** 纯文本 → canvas 元素列表（换行拆成独立 `\n` 元素） */
export function plainTextToCanvasElements(text: string): IElement[] {
  const parts = text.split('\n');
  const elements: IElement[] = [];
  for (let i = 0; i < parts.length; i += 1) {
    const line = parts[i] ?? '';
    if (line) {
      elements.push({ font: 'SimSun', size: 16, value: line });
    }
    if (i < parts.length - 1) {
      elements.push({ value: '\n' });
    }
  }
  return elements;
}

/** 快照当前选区，供弹窗抢焦后还原插入落点 */
export function captureCanvasInsertRange(command: Command | null | undefined): IRange | null {
  if (!command) return null;
  const range = command.getRange();
  if (range == null) return null;
  return {
    startIndex: range.startIndex,
    endIndex: range.endIndex,
    isCrossRowCol: range.isCrossRowCol,
    tableId: range.tableId,
    startTdIndex: range.startTdIndex,
    endTdIndex: range.endTdIndex,
    startTrIndex: range.startTrIndex,
    endTrIndex: range.endTrIndex,
    zone: range.zone,
  };
}

/**
 * 在光标/选区处插入诊断、医嘱纯文本。
 * 落在 TEXT 控件内时由 canvas-editor 写入控件值。
 */
export function insertCanvasImportText(options: {
  command: Command | null | undefined;
  text: string;
  range?: IRange | null;
}): boolean {
  const { command, text } = options;
  if (!command || !text) return false;
  const elements = plainTextToCanvasElements(text);
  if (elements.length === 0) return false;

  const range = options.range;
  if (range) {
    if (range.zone != null) {
      command.executeSetZone(range.zone);
    }
    command.executeSetRange(
      range.startIndex,
      range.endIndex,
      range.tableId,
      range.startTdIndex,
      range.endTdIndex,
      range.startTrIndex,
      range.endTrIndex,
    );
  }

  command.executeFocus();
  command.executeInsertElementList(elements, { isReplace: true });
  return true;
}
