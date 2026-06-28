import type { RulerStyle } from './ruler-types';

/** sv-print 标尺 Canvas 渲染（摘自 sv-print/lib/ruler） */
export default class Ruler {
  canvasX!: HTMLCanvasElement;
  canvasY!: HTMLCanvasElement;
  ctxX!: CanvasRenderingContext2D;
  ctxY!: CanvasRenderingContext2D;
  devicePixelRatio: number;
  ratioX = 1;
  ratioY = 1;
  rulerId: string;
  scrollLeft = 0;
  scrollTop = 0;
  sizeX!: { height: number; width: number };
  sizeY!: { height: number; width: number };
  style: RulerStyle;

  constructor(style: RulerStyle) {
    this.rulerId = style.rulerId;
    this.style = style;
    this.devicePixelRatio = window.devicePixelRatio || 1;
    if (this.getCanvas()) {
      this.drawCanvas();
    }
  }

  clearCanvas(): void {
    if (this.ctxX && this.canvasX) {
      this.ctxX.clearRect(0, 0, this.canvasX.width, this.canvasX.height);
    }
    if (this.ctxY && this.canvasY) {
      this.ctxY.clearRect(0, 0, this.canvasY.width, this.canvasY.height);
    }
  }

  destroy(): void {
    this.clearCanvas();
  }

  draw(ctx: CanvasRenderingContext2D | null, type: 'X' | 'Y', scrollNum = 0): void {
    if (!ctx) {
      return;
    }
    const isX = type === 'X';
    const drawW = isX ? this.sizeX.width + scrollNum : this.sizeY.width;
    const drawH = isX ? this.sizeX.height : this.sizeY.height + scrollNum;
    const { ratioX, ratioY } = this;
    const {
      startX,
      startY,
      backgroundColor,
      fontColor,
      fontSize,
      fontWeight,
      fontMode,
      gap,
      tickColor,
      tickWidth,
      border,
      borderColor,
      borderWidth,
      scale,
      paperRect,
      paperRectColor,
      paperWidth,
      paperHeight,
    } = this.style;
    const tickCount = isX ? drawW / gap : drawH / gap;
    let pos = 0;
    const tickBase = 0;

    ctx.rect(0, 0, drawW, drawH);
    ctx.fillStyle = backgroundColor;
    ctx.fill();

    if (border) {
      ctx.beginPath();
      if (isX) {
        ctx.moveTo(0, drawH);
        ctx.lineTo(drawW, drawH);
      } else {
        ctx.moveTo(drawW, 0);
        ctx.lineTo(drawW, drawH);
      }
      ctx.strokeStyle = borderColor;
      ctx.lineWidth = borderWidth / (isX ? ratioX : ratioY);
      ctx.stroke();
    }

    if (paperRect) {
      if (isX) {
        ctx.rect(-startX * 2, 0, paperWidth * 2, drawH);
      } else {
        ctx.rect(0, -startY * 2, drawW, paperHeight * 2);
      }
      ctx.fillStyle = paperRectColor;
      ctx.fill();
    }

    ctx.save();
    if (isX) {
      ctx.translate(0.5, 0.5);
    } else {
      ctx.translate(0, 0.5);
    }
    ctx.scale(ratioX * 2, ratioY * 2);
    ctx.textBaseline = 'alphabetic';
    ctx.fillStyle = fontColor;
    const labelSize = fontSize || Math.round(isX ? drawH * 0.53 : drawW * 0.53);
    ctx.font = `${fontWeight} ${labelSize / ratioX}px auto`;
    ctx.strokeStyle = tickColor;
    ctx.lineWidth = tickWidth / (isX ? ratioX : ratioY);
    const centerMode = fontMode === 'center';

    ctx.beginPath();
    for (let i = Math.round((isX ? startX : startY) / gap); i < tickCount; i++) {
      if (i % 10 === 0) {
        const labelVal = Math.round(i / scale);
        if (isX) {
          const text = labelVal === 0 && centerMode ? '' : `${labelVal}`;
          const textW = ctx.measureText(String(labelVal)).width / ratioX;
          ctx.fillText(
            text,
            (centerMode ? pos - textW * 0.5 : pos + fontSize * 0.3) / ratioX,
            (fontSize * 0.8) / ratioX,
          );
        } else {
          const text = labelVal === 0 && centerMode ? '' : `${labelVal}`;
          const textW = ctx.measureText(String(labelVal)).width / ratioY;
          ctx.save();
          ctx.translate(
            (fontSize * 0.8) / ratioY,
            (centerMode ? pos + textW * 0.5 : pos - fontSize * 0.3) / ratioY,
          );
          ctx.rotate(-90 * (Math.PI / 180));
          ctx.fillText(text, 0, 0);
          ctx.restore();
        }
      }
      if (isX) {
        ctx.moveTo(pos / ratioX, drawH / ratioX);
        if (i % 10 === 0) {
          ctx.lineTo(
            pos / ratioX,
            Math.round(centerMode ? tickBase + drawH / 1.5 : tickBase) / ratioX,
          );
        } else if (i % 5 === 0) {
          ctx.lineTo(
            pos / ratioX,
            Math.round(centerMode ? tickBase + drawH / 1.5 / 2 : tickBase + drawH / 2 / 2) / ratioX,
          );
        } else {
          ctx.lineTo(
            pos / ratioX,
            Math.round(centerMode ? tickBase + drawH / 1.2 / 2 : tickBase + drawH / 1.5 / 2) /
              ratioX,
          );
        }
      } else {
        ctx.moveTo(drawW / ratioY, pos / ratioY);
        if (i % 10 === 0) {
          ctx.lineTo(Math.round(centerMode ? drawW / 1.5 / 2 : -drawW / 2) / ratioY, pos / ratioY);
        } else if (i % 5 === 0) {
          ctx.lineTo(
            Math.round(centerMode ? drawW / 1.5 / 2 : drawW / 2 / 2) / ratioY,
            pos / ratioY,
          );
        } else {
          ctx.lineTo(
            Math.round(centerMode ? drawW / 1.2 / 2 : drawW / 1.5 / 2) / ratioY,
            pos / ratioY,
          );
        }
      }
      pos += gap;
    }
    ctx.stroke();
    ctx.restore();
  }

  drawCanvas(): void {
    if (!this.isReady()) {
      return;
    }
    this.draw(this.ctxX, 'X', this.scrollLeft);
    this.draw(this.ctxY, 'Y', this.scrollTop);
  }

  /** 绑定 canvas，未找到时返回 false */
  getCanvas(): boolean {
    const { size, width, height } = this.style;
    this.canvasX = document.querySelector(
      `#${CSS.escape(`ruler-x-${this.rulerId}`)}`,
    ) as HTMLCanvasElement;
    if (!this.canvasX) {
      return false;
    }
    this.canvasX.style.height = `${size}px`;
    this.canvasX.width = width * 2;
    this.canvasX.height = size * 2;
    const ctxX = this.canvasX.getContext('2d');
    if (!ctxX) {
      return false;
    }
    this.ctxX = ctxX;

    let backing =
      (this.ctxX as CanvasRenderingContext2D & { webkitBackingStorePixelRatio?: number })
        .webkitBackingStorePixelRatio ||
      (this.ctxX as CanvasRenderingContext2D & { mozBackingStorePixelRatio?: number })
        .mozBackingStorePixelRatio ||
      (this.ctxX as CanvasRenderingContext2D & { msBackingStorePixelRatio?: number })
        .msBackingStorePixelRatio ||
      (this.ctxX as CanvasRenderingContext2D & { oBackingStorePixelRatio?: number })
        .oBackingStorePixelRatio ||
      (this.ctxX as CanvasRenderingContext2D & { backingStorePixelRatio?: number })
        .backingStorePixelRatio ||
      1;
    this.ratioX = this.devicePixelRatio / backing;

    this.canvasY = document.querySelector(
      `#${CSS.escape(`ruler-y-${this.rulerId}`)}`,
    ) as HTMLCanvasElement;
    if (!this.canvasY) {
      return false;
    }
    this.canvasY.style.width = `${size}px`;
    this.canvasY.width = size * 2;
    this.canvasY.height = height * 2;
    const ctxY = this.canvasY.getContext('2d');
    if (!ctxY) {
      return false;
    }
    this.ctxY = ctxY;

    backing =
      (this.ctxY as CanvasRenderingContext2D & { webkitBackingStorePixelRatio?: number })
        .webkitBackingStorePixelRatio ||
      (this.ctxY as CanvasRenderingContext2D & { mozBackingStorePixelRatio?: number })
        .mozBackingStorePixelRatio ||
      (this.ctxY as CanvasRenderingContext2D & { msBackingStorePixelRatio?: number })
        .msBackingStorePixelRatio ||
      (this.ctxY as CanvasRenderingContext2D & { oBackingStorePixelRatio?: number })
        .oBackingStorePixelRatio ||
      (this.ctxY as CanvasRenderingContext2D & { backingStorePixelRatio?: number })
        .backingStorePixelRatio ||
      1;
    this.ratioY = this.devicePixelRatio / backing;

    this.sizeX = { width: this.canvasX.width, height: this.canvasX.height };
    this.sizeY = { width: this.canvasY.width, height: this.canvasY.height };
    return true;
  }

  /** canvas 是否已就绪 */
  isReady(): boolean {
    return Boolean(this.canvasX && this.canvasY && this.ctxX && this.ctxY);
  }

  resize(style: Partial<RulerStyle>): void {
    if (!this.isReady() && !this.getCanvas()) {
      return;
    }
    this.style = { ...this.style, ...style };
    const { size, width, height } = this.style;
    // 与 getCanvas 一致：物理像素 2x，CSS 尺寸 1x，保证 scale(ratio*2) 下 1mm=3.78px
    this.canvasX.width = width * 2;
    this.canvasX.height = size * 2;
    this.canvasX.style.width = `${width}px`;
    this.canvasX.style.height = `${size}px`;
    this.canvasY.width = size * 2;
    this.canvasY.height = height * 2;
    this.canvasY.style.width = `${size}px`;
    this.canvasY.style.height = `${height}px`;
    const ctxX = this.canvasX.getContext('2d');
    const ctxY = this.canvasY.getContext('2d');
    if (!ctxX || !ctxY) {
      return;
    }
    this.ctxX = ctxX;
    this.ctxY = ctxY;
    this.sizeX = { width: this.canvasX.width, height: this.canvasX.height };
    this.sizeY = { width: this.canvasY.width, height: this.canvasY.height };
    this.drawCanvas();
  }

  update(style: Partial<RulerStyle>): void {
    if (!this.isReady() && !this.getCanvas()) {
      return;
    }
    this.style = { ...this.style, ...style };
    this.draw(this.ctxX, 'X', this.scrollLeft);
    this.draw(this.ctxY, 'Y', this.scrollTop);
  }

  zoom(style: Partial<RulerStyle>): void {
    this.update(style);
  }
}
