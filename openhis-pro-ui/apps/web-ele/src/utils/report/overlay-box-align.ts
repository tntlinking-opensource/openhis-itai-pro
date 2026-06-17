import type { LayoutBox } from '#/types/report-layout';

export type BoxAlignMode =
  | 'bottom'
  | 'center-h'
  | 'center-v'
  | 'left'
  | 'match-height'
  | 'match-size'
  | 'match-width'
  | 'right'
  | 'top';

export interface BoxRect {
  h: number;
  w: number;
  x: number;
  y: number;
}

function round1(n: number): number {
  return Math.round(n * 10) / 10;
}

export function layoutBoxToRect(box: LayoutBox): BoxRect {
  return {
    x: box.xMm ?? 0,
    y: box.yMm ?? 0,
    w: Math.max(1, box.widthMm ?? 1),
    h: Math.max(1, box.heightMm ?? 1),
  };
}

export function rectToLayoutBox(rect: BoxRect, seed: LayoutBox): LayoutBox {
  return {
    ...seed,
    xMm: round1(rect.x),
    yMm: round1(rect.y),
    widthMm: round1(rect.w),
    heightMm: round1(rect.h),
  };
}

/** 将 source 相对 reference 对齐，返回新的 effective 落格。 */
export function alignLayoutBox(
  source: LayoutBox,
  reference: LayoutBox,
  mode: BoxAlignMode,
): LayoutBox {
  const s = layoutBoxToRect(source);
  const r = layoutBoxToRect(reference);
  const next = { ...s };

  switch (mode) {
    case 'bottom':
      next.y = r.y + r.h - s.h;
      break;
    case 'center-h':
      next.x = r.x + (r.w - s.w) / 2;
      break;
    case 'center-v':
      next.y = r.y + (r.h - s.h) / 2;
      break;
    case 'left':
      next.x = r.x;
      break;
    case 'match-height':
      next.h = r.h;
      break;
    case 'match-size':
      next.w = r.w;
      next.h = r.h;
      break;
    case 'match-width':
      next.w = r.w;
      break;
    case 'right':
      next.x = r.x + r.w - s.w;
      break;
    case 'top':
      next.y = r.y;
      break;
    default:
      break;
  }

  return rectToLayoutBox(next, source);
}

export const BOX_ALIGN_ACTIONS: Array<{ label: string; mode: BoxAlignMode; title?: string }> = [
  { label: '左', mode: 'left', title: '左缘与参考项对齐' },
  { label: '右', mode: 'right', title: '右缘与参考项对齐' },
  { label: '水平中', mode: 'center-h', title: '水平居中于参考项' },
  { label: '顶', mode: 'top', title: '顶边与参考项对齐' },
  { label: '底', mode: 'bottom', title: '底边与参考项对齐' },
  { label: '垂直中', mode: 'center-v', title: '垂直居中于参考项' },
  { label: '同宽', mode: 'match-width', title: '宽度与参考项一致' },
  { label: '同高', mode: 'match-height', title: '高度与参考项一致' },
];
