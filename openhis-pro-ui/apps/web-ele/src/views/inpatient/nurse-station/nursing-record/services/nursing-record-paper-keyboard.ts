import type { FormKeyboardNavigationContext } from '#/hooks/use-form-keyboard-navigation';

interface PaperNavMeta {
  col: string;
  el: HTMLElement;
  row: HTMLTableRowElement;
  slot: string;
}

const SLOT_ORDER = ['date', 'time', ''];

function readPaperNavMeta(el: HTMLElement): PaperNavMeta | null {
  const col = el.dataset.navCol;
  if (col == null) {
    return null;
  }
  const row = el.closest('tr');
  if (!row || !el.closest('.paper-record__table tbody')) {
    return null;
  }
  return {
    col,
    el,
    row: row as HTMLTableRowElement,
    slot: el.dataset.navSlot ?? '',
  };
}

function comparePaperFieldOrder(a: HTMLElement, b: HTMLElement) {
  const colA = Number(a.dataset.navCol);
  const colB = Number(b.dataset.navCol);
  if (colA !== colB) {
    return colA - colB;
  }
  const slotA = a.dataset.navSlot ?? '';
  const slotB = b.dataset.navSlot ?? '';
  return SLOT_ORDER.indexOf(slotA) - SLOT_ORDER.indexOf(slotB);
}

function rowFields(ctx: FormKeyboardNavigationContext, row: HTMLTableRowElement) {
  return ctx.focusable
    .filter((el) => row.contains(el) && el.dataset.navCol != null)
    .sort(comparePaperFieldOrder);
}

function findHorizontalPaperTarget(
  ctx: FormKeyboardNavigationContext,
  meta: PaperNavMeta,
  direction: -1 | 1,
) {
  const fields = rowFields(ctx, meta.row);
  const idx = fields.indexOf(meta.el);
  if (idx === -1) {
    return ctx.currentIndex;
  }
  const next = fields[idx + direction];
  if (!next) {
    return ctx.currentIndex;
  }
  return ctx.focusable.indexOf(next);
}

function findVerticalPaperTarget(
  ctx: FormKeyboardNavigationContext,
  rows: HTMLTableRowElement[],
  meta: PaperNavMeta,
  step: 1 | -1,
) {
  const rowIdx = rows.indexOf(meta.row);
  for (let i = rowIdx + step; i >= 0 && i < rows.length; i += step) {
    const candidates = ctx.focusable.filter((el) => {
      if (!rows[i]?.contains(el) || el.dataset.navCol !== meta.col) {
        return false;
      }
      if (meta.slot) {
        return (el.dataset.navSlot ?? '') === meta.slot;
      }
      return true;
    });
    if (candidates.length > 0) {
      const exact = candidates.find((el) => (el.dataset.navSlot ?? '') === meta.slot);
      return ctx.focusable.indexOf(exact ?? candidates[0]!);
    }
  }
  return ctx.currentIndex;
}

/** 纸质单表格：同行左右、同列上下切换焦点。 */
export function resolvePaperTableFocusIndex(
  ctx: FormKeyboardNavigationContext,
): number | undefined {
  const current = ctx.focusable[ctx.currentIndex];
  if (!current) {
    return undefined;
  }

  const meta = readPaperNavMeta(current);
  if (!meta) {
    return undefined;
  }

  const key = ctx.event.key;
  const tbody = meta.row.parentElement;
  if (!tbody) {
    return undefined;
  }
  const rows = [...tbody.querySelectorAll<HTMLTableRowElement>(':scope > tr')];

  if (key === 'ArrowDown') {
    return findVerticalPaperTarget(ctx, rows, meta, 1);
  }
  if (key === 'ArrowUp') {
    return findVerticalPaperTarget(ctx, rows, meta, -1);
  }
  if (key === 'ArrowRight' || key === 'Enter') {
    return findHorizontalPaperTarget(ctx, meta, 1);
  }
  if (key === 'ArrowLeft') {
    return findHorizontalPaperTarget(ctx, meta, -1);
  }

  return undefined;
}
