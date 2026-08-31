/**
 * Atom 填写岛屿（自由文本等）内部的换页垫片。
 * PM decoration 无法插进 atom 中间；多行岛屿跨页时须把 block 垫片画进 contenteditable。
 */

export const EMR_ISLAND_SOFT_BREAK_ATTR = 'data-emr-island-soft-break';
export const EMR_ISLAND_SOFT_BREAK_SELECTOR = `[${EMR_ISLAND_SOFT_BREAK_ATTR}]`;

/** 岛屿可编辑区选择器（与 NodeView 类名对齐） */
export const EMR_ISLAND_INPUT_SELECTOR = [
  '.emr-fillable-field__input',
  '.emr-fillable-field__readonly-text',
  '.emr-inline-text__field',
  '.emr-de__text-field',
  '.emr-free-text__text',
  '.emr-metadata-chip__text',
  '.emr-bracket-field__body',
].join(', ');

export function clearEmrIslandSoftBreaks(root: ParentNode): void {
  root.querySelectorAll(EMR_ISLAND_SOFT_BREAK_SELECTOR).forEach((el) => el.remove());
}

/** 去掉岛屿垫片后再读纯文本，避免 display:block 垫片污染 innerText / 落库 */
export function stripEmrIslandSoftBreaksForRead(el: HTMLElement): HTMLElement {
  if (!el.querySelector(EMR_ISLAND_SOFT_BREAK_SELECTOR)) return el;
  const clone = el.cloneNode(true) as HTMLElement;
  clone.querySelectorAll(EMR_ISLAND_SOFT_BREAK_SELECTOR).forEach((n) => n.remove());
  return clone;
}

function createIslandSoftBreakEl(heightPx: number): HTMLSpanElement {
  const el = document.createElement('span');
  el.setAttribute(EMR_ISLAND_SOFT_BREAK_ATTR, '1');
  el.setAttribute('aria-hidden', 'true');
  el.contentEditable = 'false';
  el.style.display = 'block';
  el.style.width = '100%';
  el.style.height = `${Math.max(1, Math.round(heightPx))}px`;
  el.style.margin = '0';
  el.style.padding = '0';
  el.style.border = 'none';
  el.style.pointerEvents = 'none';
  el.style.userSelect = 'none';
  return el;
}

/**
 * 在岛屿纯文本偏移处插入换页垫片。
 * 调用方须按 textOffset 从大到小插入，避免偏移错位。
 */
export function insertEmrIslandSoftBreakAt(
  island: HTMLElement,
  textOffset: number,
  heightPx: number,
): boolean {
  const clamped = Math.max(0, Math.floor(textOffset));
  const spacer = createIslandSoftBreakEl(heightPx);

  if (clamped <= 0) {
    island.insertBefore(spacer, island.firstChild);
    return true;
  }

  const walker = document.createTreeWalker(island, NodeFilter.SHOW_TEXT, {
    acceptNode(node) {
      if (node.parentElement?.closest(EMR_ISLAND_SOFT_BREAK_SELECTOR)) {
        return NodeFilter.FILTER_REJECT;
      }
      return NodeFilter.FILTER_ACCEPT;
    },
  });

  let remaining = clamped;
  let node = walker.nextNode();
  while (node) {
    const text = node.textContent ?? '';
    const len = text.length;
    if (remaining <= len) {
      if (remaining === 0) {
        node.parentNode?.insertBefore(spacer, node);
        return true;
      }
      if (remaining === len) {
        node.parentNode?.insertBefore(spacer, node.nextSibling);
        return true;
      }
      const textNode = node as Text;
      const after = textNode.splitText(remaining);
      after.parentNode?.insertBefore(spacer, after);
      return true;
    }
    remaining -= len;
    node = walker.nextNode();
  }

  island.appendChild(spacer);
  return true;
}

/** 统计岛屿内某 Range 起点之前的纯文本偏移（跳过垫片） */
export function emrIslandPlainTextOffsetBefore(
  island: HTMLElement,
  container: Node,
  offset: number,
): number {
  if (!island.contains(container) && container !== island) return 0;
  const pre = document.createRange();
  pre.selectNodeContents(island);
  try {
    pre.setEnd(container, offset);
  } catch {
    return 0;
  }
  const probe = document.createElement('div');
  probe.appendChild(pre.cloneContents());
  probe.querySelectorAll(EMR_ISLAND_SOFT_BREAK_SELECTOR).forEach((n) => n.remove());
  return (probe.textContent ?? '').replace(/\r\n/g, '\n').length;
}
