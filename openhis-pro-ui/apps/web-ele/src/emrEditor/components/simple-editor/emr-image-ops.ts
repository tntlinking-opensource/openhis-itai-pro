/**
 * 正文图片常用操作：选区解析、等比缩放、画布旋转/裁切。
 * 旋转与裁切会重写 data URL，保证打印与跨端一致（不靠纯 CSS transform）。
 */

import type { Editor } from '@tiptap/core';
import type { Node as PMNode } from '@tiptap/pm/model';
import type { Selection } from '@tiptap/pm/state';

export interface EmrImageSelection {
  attrs: {
    alt?: null | string;
    height?: null | number;
    rotate?: null | number;
    src: string;
    title?: null | string;
    width?: null | number;
  };
  node: PMNode;
  pos: number;
}

export interface EmrImageCropRect {
  /** 相对原图 0～1 */
  height: number;
  width: number;
  x: number;
  y: number;
}

/** 不靠 instanceof（多副本 @tiptap/pm 时会失效），用 node 字段识别 NodeSelection */
function nodeFromSelection(selection: Selection): null | PMNode {
  const maybe = selection as Selection & { node?: PMNode };
  if (maybe.node && typeof maybe.node.type?.name === 'string') {
    return maybe.node;
  }
  return null;
}

function asPositiveInt(v: unknown): null | number {
  const n = typeof v === 'number' ? v : Number(v);
  if (!Number.isFinite(n) || n <= 0) return null;
  return Math.round(n);
}

function asRotate(v: unknown): number {
  const n = typeof v === 'number' ? v : Number(v);
  if (!Number.isFinite(n)) return 0;
  const r = ((Math.round(n) % 360) + 360) % 360;
  return r;
}

/** 把图片节点打成工具条用的选区快照 */
export function toEmrImageSelection(pos: number, node: PMNode): EmrImageSelection | null {
  if (node.type.name !== 'image') return null;
  const src = String(node.attrs.src ?? '').trim();
  if (!src) return null;
  return {
    pos,
    node,
    attrs: {
      src,
      alt: node.attrs.alt ?? null,
      title: node.attrs.title ?? null,
      width: asPositiveInt(node.attrs.width),
      height: asPositiveInt(node.attrs.height),
      rotate: asRotate(node.attrs.rotate),
    },
  };
}

/** 当前是否选中了正文图片节点 */
export function resolveSelectedEmrImage(
  editor: Editor | null | undefined,
): EmrImageSelection | null {
  if (!editor || editor.isDestroyed) return null;
  const { selection } = editor.state;
  const node = nodeFromSelection(selection);
  if (node?.type.name === 'image') {
    return toEmrImageSelection(selection.from, node);
  }
  // 光标落在图旁时也认（缩放外壳常把选区打成 TextSelection）
  const $from = selection.$from;
  if ($from.nodeAfter?.type.name === 'image') {
    return toEmrImageSelection($from.pos, $from.nodeAfter);
  }
  if ($from.nodeBefore?.type.name === 'image') {
    const before = $from.nodeBefore;
    return toEmrImageSelection($from.pos - before.nodeSize, before);
  }
  return null;
}

/** 按文档位置取图片（工具条钉住选区时用，不依赖 NodeSelection） */
export function resolveEmrImageAtPos(
  editor: Editor | null | undefined,
  pos: number,
): EmrImageSelection | null {
  if (!editor || editor.isDestroyed) return null;
  if (pos < 0 || pos > editor.state.doc.content.size) return null;
  const node = editor.state.doc.nodeAt(pos);
  if (!node) return null;
  return toEmrImageSelection(pos, node);
}

/** 从文档位解析相邻 image 节点起点 */
function imagePosNear(editor: Editor, pos: number): number {
  if (pos < 0 || pos > editor.state.doc.content.size) return -1;
  const nodeAt = editor.state.doc.nodeAt(pos);
  if (nodeAt?.type.name === 'image') return pos;
  try {
    const $pos = editor.state.doc.resolve(Math.min(pos, editor.state.doc.content.size));
    if ($pos.nodeAfter?.type.name === 'image') return $pos.pos;
    if ($pos.nodeBefore?.type.name === 'image') {
      return $pos.pos - $pos.nodeBefore.nodeSize;
    }
  } catch {
    /* ignore */
  }
  return -1;
}

/** 从点击 DOM / 坐标反查图片节点位置 */
export function resolveEmrImagePosFromDom(
  editor: Editor | null | undefined,
  target: EventTarget | null,
  clientX?: number,
  clientY?: number,
): number {
  if (!editor || editor.isDestroyed) return -1;

  if (target instanceof Element && editor.view.dom.contains(target)) {
    const shell = target.closest(
      '.emr-img-resize, [data-resize-container], img.emr-editor-img, img[data-node="image"], img[src]',
    );
    if (shell instanceof Element && editor.view.dom.contains(shell)) {
      let imagePos = -1;
      try {
        const directPos = editor.view.posAtDOM(shell, 0);
        if (typeof directPos === 'number') {
          imagePos = imagePosNear(editor, directPos);
        }
      } catch {
        /* posAtDOM 对 NodeView 外壳偶发失败，走下面扫描 */
      }

      if (imagePos < 0) {
        editor.state.doc.descendants((node, p) => {
          if (node.type.name !== 'image') return true;
          const dom = editor.view.nodeDOM(p);
          if (!(dom instanceof Element)) return true;
          if (dom === shell || dom.contains(shell) || shell.contains(dom)) {
            imagePos = p;
            return false;
          }
          return true;
        });
      }
      if (imagePos >= 0) return imagePos;
    }
  }

  // 坐标兜底：pointer-events:none / 点穿外壳时用
  if (typeof clientX === 'number' && typeof clientY === 'number') {
    try {
      const found = editor.view.posAtCoords({ left: clientX, top: clientY });
      if (found) {
        const byInside = found.inside >= 0 ? imagePosNear(editor, found.inside) : -1;
        if (byInside >= 0) return byInside;
        const byPos = imagePosNear(editor, found.pos);
        if (byPos >= 0) return byPos;
      }
    } catch {
      /* ignore */
    }
  }
  return -1;
}

/** 加载图片自然尺寸（用于默认宽度） */
export function loadImageNaturalSize(src: string): Promise<{ height: number; width: number }> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () =>
      resolve({
        width: img.naturalWidth || img.width,
        height: img.naturalHeight || img.height,
      });
    img.onerror = () => reject(new Error('image load failed'));
    img.src = src;
  });
}

/** 按目标宽等比算出高；无自然尺寸时只改宽 */
export async function computeEmrImageSize(
  src: string,
  width: number,
  prev?: { height?: null | number; width?: null | number },
): Promise<{ height: number; width: number }> {
  const w = Math.max(16, Math.round(width));
  if (prev?.width && prev.height && prev.width > 0) {
    const h = Math.max(16, Math.round((w * prev.height) / prev.width));
    return { width: w, height: h };
  }
  try {
    const nat = await loadImageNaturalSize(src);
    if (nat.width > 0 && nat.height > 0) {
      return {
        width: w,
        height: Math.max(16, Math.round((w * nat.height) / nat.width)),
      };
    }
  } catch {
    /* ignore */
  }
  return { width: w, height: w };
}

function canvasToJpegOrPng(canvas: HTMLCanvasElement, srcHint: string): string {
  const preferJpeg = /image\/jpe?g/i.test(srcHint) || srcHint.includes('/jpeg');
  if (preferJpeg) {
    return canvas.toDataURL('image/jpeg', 0.92);
  }
  return canvas.toDataURL('image/png');
}

async function loadHtmlImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = () => reject(new Error('image load failed'));
    img.src = src;
  });
}

/** 顺时针旋转 90°（重写 src；宽高对调） */
export async function rotateEmrImageSrcClockwise(
  src: string,
): Promise<{ height: number; src: string; width: number }> {
  const img = await loadHtmlImage(src);
  const w = img.naturalWidth || img.width;
  const h = img.naturalHeight || img.height;
  const canvas = document.createElement('canvas');
  canvas.width = h;
  canvas.height = w;
  const ctx = canvas.getContext('2d');
  if (!ctx) {
    throw new Error('canvas unavailable');
  }
  ctx.translate(h, 0);
  ctx.rotate(Math.PI / 2);
  ctx.drawImage(img, 0, 0);
  return {
    src: canvasToJpegOrPng(canvas, src),
    width: h,
    height: w,
  };
}

/** 按相对矩形裁切，输出新 data URL 与像素尺寸 */
export async function cropEmrImageSrc(
  src: string,
  rect: EmrImageCropRect,
): Promise<{ height: number; src: string; width: number }> {
  const img = await loadHtmlImage(src);
  const nw = img.naturalWidth || img.width;
  const nh = img.naturalHeight || img.height;
  const x = Math.max(0, Math.min(nw - 1, Math.round(rect.x * nw)));
  const y = Math.max(0, Math.min(nh - 1, Math.round(rect.y * nh)));
  const w = Math.max(1, Math.min(nw - x, Math.round(rect.width * nw)));
  const h = Math.max(1, Math.min(nh - y, Math.round(rect.height * nh)));
  const canvas = document.createElement('canvas');
  canvas.width = w;
  canvas.height = h;
  const ctx = canvas.getContext('2d');
  if (!ctx) {
    throw new Error('canvas unavailable');
  }
  ctx.drawImage(img, x, y, w, h, 0, 0, w, h);
  return {
    src: canvasToJpegOrPng(canvas, src),
    width: w,
    height: h,
  };
}
