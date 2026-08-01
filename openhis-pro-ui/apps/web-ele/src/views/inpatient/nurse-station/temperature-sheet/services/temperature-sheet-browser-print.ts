/**
 * 体温单浏览器打印（同文档克隆方案）。
 *
 * 关键点：
 * 1) 组件内 `@media print { body * { visibility:hidden } }` 会把 body 上的打印宿主一并藏掉 → 空白页；
 * 2) iframe 克隆常丢 Vite/Vue scoped 样式 → 也空白；
 * 3) 仅 visibility:hidden 仍占版心 → 浏览器「适应纸张」缩到左上角。
 *
 * 做法：同文档克隆纸面挂到 body，打印时只显示宿主；样式用本文件自带的非 scoped CSS
 * （并设置 --axis-* 变量，因克隆已离开 .temperature-sheet 根节点）。
 */

const BODY_PRINTING_CLASS = 'is-temperature-sheet-printing';
const HOST_CLASS = 'temperature-sheet-print-host';
const STYLE_ID = 'temperature-sheet-browser-print-style';

/** 打印宿主 + 纸面布局（不依赖 Vue scoped） */
const PRINT_HOST_CSS = `
@page {
  size: A4 portrait;
  margin: 0;
}

.${HOST_CLASS} {
  --axis-column-width: 38px;
  --axis-border-width: 1px;
  --axis-total-width: calc(var(--axis-column-width) * 2);
  box-sizing: border-box;
  color: #020817;
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
}

.${HOST_CLASS} *,
.${HOST_CLASS} *::before,
.${HOST_CLASS} *::after {
  box-sizing: border-box;
}

@media screen {
  .${HOST_CLASS} {
    position: fixed !important;
    left: -10000px !important;
    top: 0 !important;
    width: 794px !important;
    height: 1123px !important;
    opacity: 0 !important;
    pointer-events: none !important;
    z-index: -1 !important;
    overflow: hidden !important;
    background: #fff !important;
  }
}

@media print {
  body.${BODY_PRINTING_CLASS} {
    margin: 0 !important;
    padding: 0 !important;
    width: 210mm !important;
    height: 297mm !important;
    overflow: hidden !important;
    background: #fff !important;
    -webkit-print-color-adjust: exact;
    print-color-adjust: exact;
  }

  body.${BODY_PRINTING_CLASS} > *:not(.${HOST_CLASS}) {
    display: none !important;
  }

  body.${BODY_PRINTING_CLASS} .${HOST_CLASS} {
    display: block !important;
    visibility: visible !important;
    position: fixed !important;
    top: 4mm !important;
    left: 4mm !important;
    width: 794px !important;
    height: 1123px !important;
    margin: 0 !important;
    padding: 0 !important;
    opacity: 1 !important;
    background: #fff !important;
    overflow: hidden !important;
    z-index: 999999 !important;
    transform: scale(0.96) !important;
    transform-origin: top left !important;
  }
}

.${HOST_CLASS} .sheet-paper {
  position: relative !important;
  top: auto !important;
  left: auto !important;
  box-sizing: border-box;
  width: 794px !important;
  height: 1123px !important;
  margin: 0 !important;
  border: 1px solid #222;
  background: #fff;
  padding: 8px;
  display: flex;
  flex-direction: column;
  transform: none !important;
  transform-origin: top left;
}

.${HOST_CLASS} .sheet-title {
  text-align: center;
  font-size: 28px;
  letter-spacing: 12px;
  font-weight: 700;
  line-height: 1.2;
  margin-bottom: 6px;
  color: #020817;
}

.${HOST_CLASS} .patient-info-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 14px;
  min-height: 24px;
  padding: 2px 4px;
  font-size: 12px;
  color: #020817;
}

.${HOST_CLASS} .sheet-head-grid {
  display: grid;
  grid-template-columns: var(--axis-total-width) minmax(0, 1fr);
  border: 1px solid #222;
  font-size: 13px;
  color: #020817;
}

.${HOST_CLASS} .head-label {
  min-height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-right: 1px solid #222;
  border-bottom: 1px solid #222;
}

.${HOST_CLASS} .head-content {
  min-width: 0;
  width: 100%;
  border-bottom: 1px solid #222;
}

.${HOST_CLASS} .day-content,
.${HOST_CLASS} .time-content {
  display: grid;
}

.${HOST_CLASS} .head-day-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  border-right: 1px solid #222;
}

.${HOST_CLASS} .head-time-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  border-right: 1px solid #9ca3af;
  color: #111827;
}

.${HOST_CLASS} .head-time-cell.is-day-divider {
  border-right: 1px solid #b91c1c;
}

.${HOST_CLASS} .chart-wrapper {
  display: grid;
  grid-template-columns: var(--axis-total-width) minmax(0, 1fr);
  flex: 1;
  min-height: 0;
  border: 1px solid #222;
  border-top: 0;
}

.${HOST_CLASS} .chart-axis-rail {
  display: flex;
  min-width: 0;
  height: 100%;
  border-right: 1px solid #222;
}

.${HOST_CLASS} .axis {
  flex: 1 1 0;
  width: auto;
  min-width: 0;
  position: relative;
  display: flex;
  align-items: stretch;
  font-size: 10px;
  padding: 0;
}

.${HOST_CLASS} .axis-left {
  border-right: var(--axis-border-width) solid #222;
  color: #dc2626;
}

.${HOST_CLASS} .axis-mid {
  color: #1d4ed8;
}

.${HOST_CLASS} .axis-title {
  position: absolute;
  top: 4px;
  left: 50%;
  transform: translateX(-50%);
  writing-mode: vertical-rl;
  font-size: 10px;
  letter-spacing: 1px;
  line-height: 1;
  z-index: 1;
  pointer-events: none;
}

.${HOST_CLASS} .axis-values {
  flex: 1;
  width: 100%;
  position: relative;
}

.${HOST_CLASS} .axis-value {
  display: flex;
  align-items: center;
  justify-content: center;
}

.${HOST_CLASS} .axis-value-temp,
.${HOST_CLASS} .axis-value-pulse {
  position: absolute;
  left: 0;
  width: 100%;
  transform: translateY(-50%);
}

.${HOST_CLASS} .chart-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
  position: relative;
}

.${HOST_CLASS} .event-text-marker {
  position: absolute;
  transform: translateX(-50%);
  z-index: 4;
  writing-mode: vertical-rl;
  text-orientation: upright;
  color: #b91c1c;
  font-size: 12px;
  line-height: 1.1;
  letter-spacing: 1px;
  pointer-events: none;
}

.${HOST_CLASS} .slot-vline-overlay {
  position: absolute;
  inset: 0;
  display: grid;
  pointer-events: none;
  z-index: 3;
}

.${HOST_CLASS} .slot-vline {
  border-right: 1px solid #9ca3af;
}

.${HOST_CLASS} .slot-vline.is-day-divider {
  border-right: 1px solid #b91c1c;
}

.${HOST_CLASS} .plot-area {
  position: relative;
  flex: 1;
  min-height: 0;
}

.${HOST_CLASS} .plot-chart,
.${HOST_CLASS} .plot-area img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: fill;
}

.${HOST_CLASS} .sheet-footer-grid {
  display: grid;
  grid-template-columns: var(--axis-total-width) minmax(0, 1fr);
  border: 1px solid #222;
  border-top: 0;
  font-size: 10px;
  color: #111827;
}

.${HOST_CLASS} .footer-label {
  min-height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-right: 1px solid #222;
  border-bottom: 1px solid #222;
  font-size: 12px;
}

.${HOST_CLASS} .footer-content {
  min-width: 0;
  width: 100%;
  border-bottom: 1px solid #222;
}

.${HOST_CLASS} .footer-content.day-content,
.${HOST_CLASS} .footer-content.time-content {
  display: grid;
}

.${HOST_CLASS} .footer-content.time-content .head-time-cell {
  min-height: 22px;
  font-size: 10px;
}

.${HOST_CLASS} .footer-resp-label {
  min-height: 40px;
}

.${HOST_CLASS} .resp-content .footer-resp-cell {
  min-height: 40px;
  font-size: 10px;
}

.${HOST_CLASS} .footer-resp-cell.is-resp-high {
  align-items: flex-start;
  padding-top: 4px;
}

.${HOST_CLASS} .footer-resp-cell.is-resp-low {
  align-items: flex-end;
  padding-bottom: 4px;
}

.${HOST_CLASS} .footer-bp-cell {
  border-right: 1px solid #9ca3af;
}

.${HOST_CLASS} .footer-bp-cell.is-day-end {
  border-right: 1px solid #b91c1c;
}

.${HOST_CLASS} .footer-day-cell {
  min-height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-right: 1px solid #222;
  padding: 0 4px;
  line-height: 1.2;
  word-break: break-all;
}
`;

function ensurePrintStyle(): void {
  let style = document.getElementById(STYLE_ID) as HTMLStyleElement | null;
  if (!style) {
    style = document.createElement('style');
    style.id = STYLE_ID;
    document.head.appendChild(style);
  }
  style.textContent = PRINT_HOST_CSS;
}

function replaceChartWithImage(sourceRoot: HTMLElement, cloneRoot: HTMLElement): number {
  const sourcePlot = sourceRoot.querySelector('.plot-area');
  const clonePlot = cloneRoot.querySelector('.plot-area');
  const canvas = sourcePlot?.querySelector('canvas');
  if (!(sourcePlot instanceof HTMLElement) || !(clonePlot instanceof HTMLElement)) {
    return 0;
  }
  if (!(canvas instanceof HTMLCanvasElement)) {
    return 0;
  }
  const width = canvas.clientWidth || canvas.width;
  const height = canvas.clientHeight || canvas.height;
  if (width <= 0 || height <= 0) {
    return 0;
  }
  try {
    const image = document.createElement('img');
    image.src = canvas.toDataURL('image/png');
    image.alt = '';
    image.style.display = 'block';
    image.style.width = '100%';
    image.style.height = '100%';
    image.style.objectFit = 'fill';
    clonePlot.innerHTML = '';
    clonePlot.appendChild(image);
    return 1;
  } catch {
    return 0;
  }
}

function waitForImages(root: HTMLElement): Promise<void> {
  const images = Array.from(root.querySelectorAll('img'));
  if (images.length === 0) {
    return Promise.resolve();
  }
  return Promise.all(
    images.map(
      (image) =>
        new Promise<void>((resolve) => {
          if (image.complete && image.naturalWidth > 0) {
            resolve();
            return;
          }
          image.addEventListener('load', () => resolve(), { once: true });
          image.addEventListener('error', () => resolve(), { once: true });
        }),
    ),
  ).then(() => undefined);
}

function cleanupPrintHost(host: HTMLElement): void {
  document.body.classList.remove(BODY_PRINTING_CLASS);
  host.remove();
}

/** 克隆体温单纸面到 body 宿主并唤起系统打印 */
export async function printTemperatureSheetPaper(paper: HTMLElement): Promise<void> {
  ensurePrintStyle();

  document.querySelectorAll(`.${HOST_CLASS}`).forEach((node) => node.remove());
  document.body.classList.remove(BODY_PRINTING_CLASS);

  const clone = paper.cloneNode(true) as HTMLElement;
  clone.style.transform = 'none';
  clone.style.width = '794px';
  clone.style.height = '1123px';

  if (!clone.innerHTML.trim()) {
    throw new Error('体温单纸面内容为空，无法打印');
  }

  const canvasReplaced = replaceChartWithImage(paper, clone);

  const host = document.createElement('div');
  host.className = HOST_CLASS;
  host.setAttribute('aria-hidden', 'true');
  host.appendChild(clone);
  document.body.appendChild(host);

  await waitForImages(clone);
  await new Promise<void>((resolve) => {
    requestAnimationFrame(() => requestAnimationFrame(() => resolve()));
  });

  // 离屏宿主仍应有布局宽度；用 scrollWidth 避免 getBoundingClientRect 受 transform 影响
  if (clone.scrollWidth < 100 || clone.scrollHeight < 100) {
    cleanupPrintHost(host);
    throw new Error('打印纸面尺寸异常，请刷新后重试');
  }

  // 有图表区却一张 canvas 都导不出时给出明确提示（仍允许打印表头表尾）
  const hadCanvas = paper.querySelectorAll('canvas').length > 0;
  if (hadCanvas && canvasReplaced === 0) {
    console.warn('[temperature-sheet-print] chart canvas export failed, header/footer still print');
  }

  const handleAfterPrint = () => {
    cleanupPrintHost(host);
    window.removeEventListener('afterprint', handleAfterPrint);
  };
  window.addEventListener('afterprint', handleAfterPrint);

  document.body.classList.add(BODY_PRINTING_CLASS);
  await new Promise<void>((resolve) => {
    requestAnimationFrame(() => resolve());
  });

  try {
    window.print();
  } catch (error) {
    handleAfterPrint();
    throw error;
  }

  window.setTimeout(() => {
    if (document.body.contains(host)) {
      handleAfterPrint();
    }
  }, 60_000);
}
