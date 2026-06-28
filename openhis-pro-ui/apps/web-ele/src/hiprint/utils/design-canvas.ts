/** 给设计态纸张加 grid 类（sv-print 同款，网格仅在纸张内） */
export function applyPaperGridClass(root?: null | ParentNode): void {
  const scope = root ?? document.querySelector('.hiprint-designer-root');
  scope?.querySelectorAll('.hiprint-printPaper.design').forEach((el) => {
    el.classList.add('grid');
  });
}

/** 移除 hiprint 贴在纸张上的内置 SVG 标尺（createRuler） */
export function removeHiprintInnerRulers(root?: null | ParentNode): void {
  const scope = root ?? document.querySelector('.hiprint-designer-root');
  scope?.querySelectorAll('.hiprint_rul_wrapper').forEach((el) => {
    el.remove();
  });
}

function applyHeaderLinePresentation(el: HTMLElement): void {
  el.style.setProperty('top', '0', 'important');
  el.style.setProperty('height', '0', 'important');
  el.style.setProperty('min-height', '0', 'important');
}

function applyFooterLinePresentation(el: HTMLElement, paper: HTMLElement): void {
  const content = el.closest('.hiprint-printPaper-content') as HTMLElement | null;
  const borderPx = 2;

  el.style.setProperty('bottom', 'auto', 'important');
  el.style.setProperty('height', '0', 'important');
  el.style.setProperty('min-height', '0', 'important');

  // 内容区与纸张同高，避免 bottom:0 落在纸外（hiprint 默认 top 用满幅 mm）
  if (content && paper.clientHeight > 0) {
    content.style.height = `${paper.clientHeight}px`;
    content.style.maxHeight = `${paper.clientHeight}px`;
    const lineTop = Math.max(0, content.clientHeight - borderPx);
    el.style.setProperty('top', `${lineTop}px`, 'important');
    return;
  }

  if (paper.clientHeight > 0) {
    const lineTop = Math.max(0, paper.clientHeight - borderPx);
    el.style.setProperty('top', `${lineTop}px`, 'important');
  }
}

/**
 * hiprint 在 paperHeader=0 / paperFooter=height 时加 hide* 类并隐藏边线；
 * 页脚线默认贴底时还可能落在纸外被裁切。挂载/切纸后纠正展示。
 */
export function applyHeaderFooterLinePresentation(root?: null | ParentNode): void {
  const scope = root ?? document.querySelector('.hiprint-designer-root');
  if (!scope) {
    return;
  }

  bindHeaderFooterLineDragCursor(scope);

  scope.querySelectorAll('.hiprint-printPaper.design').forEach((paperNode) => {
    const paper = paperNode as HTMLElement;
    const content = paper.querySelector('.hiprint-printPaper-content') as HTMLElement | null;
    if (content) {
      content.style.overflow = 'visible';
    }

    paper.querySelectorAll('.hiprint-headerLine.hideheaderLinetarget').forEach((node) => {
      applyHeaderLinePresentation(node as HTMLElement);
    });

    paper.querySelectorAll('.hiprint-footerLine').forEach((node) => {
      const el = node as HTMLElement;
      const nearBottom = paper.clientHeight > 0 && el.offsetTop >= paper.clientHeight - 12;
      if (el.classList.contains('hidefooterLinetarget') || nearBottom) {
        applyFooterLinePresentation(el, paper);
      }
    });
  });
}

/** 拖动页眉/页脚时 hiprint 会把 body 设为 row-resize，统一保持 move */
function bindHeaderFooterLineDragCursor(scope: ParentNode): void {
  const root = scope as HTMLElement;
  if (root.dataset.headerFooterDragCursorBound === '1') {
    return;
  }
  root.dataset.headerFooterDragCursorBound = '1';

  const enforceMoveCursor = (): void => {
    document.body.style.setProperty('cursor', 'move', 'important');
  };
  const clearMoveCursor = (): void => {
    document.body.style.removeProperty('cursor');
  };

  scope.addEventListener(
    'mousedown',
    (event) => {
      const target = event.target as HTMLElement | null;
      if (!target?.closest('.hiprint-headerLine, .hiprint-footerLine')) {
        return;
      }
      enforceMoveCursor();
      const onMove = (): void => {
        enforceMoveCursor();
      };
      const onUp = (): void => {
        document.removeEventListener('mousemove', onMove);
        document.removeEventListener('mouseup', onUp);
        clearMoveCursor();
        requestAnimationFrame(() => {
          applyHeaderFooterLinePresentation(scope);
        });
      };
      document.addEventListener('mousemove', onMove);
      document.addEventListener('mouseup', onUp);
    },
    true,
  );
}
