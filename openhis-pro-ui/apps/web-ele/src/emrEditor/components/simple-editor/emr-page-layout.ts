/**
 * Word 式分页：按纸张高度把分页符撑到页底，并在下方另起一页。
 * 只做坐标计算，不碰 DOM。
 */

export interface EmrPageMetrics {
  pageGapPx: number;
  pageHeightPx: number;
  paddingBottomPx: number;
  paddingTopPx: number;
}

export type EmrPageItemKind = 'block' | 'break';

export interface EmrPageItem {
  /**
   * 顶层块 pos。
   * 行级拆分时多条 item 共享同一 anchorPos；soft spacer 插在 item.pos 前（可为行内位置）。
   */
  anchorPos: number;
  compactHeightPx: number;
  kind: EmrPageItemKind;
  /** soft spacer 插入位置（行级切开时为该行起点） */
  pos: number;
  /**
   * 行起点落在 atom 填写岛屿内时的纯文本偏移。
   * 有值时换页垫片画进岛屿 DOM，而非文档 decoration（atom 无法从中间切开）。
   */
  islandTextOffset?: number;
  /**
   * 无正文空段（仅占位）。本页放得下就排；放不下不换页，避免末页空纸。
   * 对齐 PDF blankPagePolicy=hide。
   */
  filler?: boolean;
}

export interface EmrPageBreakBox {
  heightPx: number;
  marginBottomPx: number;
  marginTopPx: number;
}

export interface EmrSoftSpacer {
  heightPx: number;
  pos: number;
  /** 与 EmrPageItem.islandTextOffset 同义；有值则由岛屿 DOM 垫片消费 */
  islandTextOffset?: number;
}

export interface EmrPageLayoutPlan {
  breakBoxes: EmrPageBreakBox[];
  pageCount: number;
  softSpacers: EmrSoftSpacer[];
}

export function cyclePx(metrics: EmrPageMetrics): number {
  return metrics.pageHeightPx + metrics.pageGapPx;
}

/** 单次换页垫片高度上限（≈ 本页余白 + 页缝 + 下页页眉） */
export function maxSoftSpacerHeightPx(metrics: EmrPageMetrics): number {
  return Math.round(cyclePx(metrics) + metrics.paddingTopPx + 8);
}

/** 防止 DOM refine / 累计误差产出跨多页的巨型垫片 */
export function clampSoftSpacersInPlan(
  plan: EmrPageLayoutPlan,
  metrics: EmrPageMetrics,
): EmrPageLayoutPlan {
  const maxH = maxSoftSpacerHeightPx(metrics);
  const softSpacers = plan.softSpacers
    .map((s) => ({
      pos: s.pos,
      heightPx: Math.min(Math.max(0, Math.round(s.heightPx)), maxH),
      ...(s.islandTextOffset != null ? { islandTextOffset: s.islandTextOffset } : {}),
    }))
    .filter((s) => s.heightPx > 0.5);
  return { ...plan, softSpacers };
}

function remainingAreFiller(items: readonly EmrPageItem[], fromIndex: number): boolean {
  if (fromIndex >= items.length) return true;
  for (let i = fromIndex; i < items.length; i++) {
    const it = items[i]!;
    if (it.kind === 'break') return false;
    if (!it.filler) return false;
  }
  return true;
}

/** 文末连续短块（签名行等）合计高度；含更高块则不是「末页孤儿」 */
function trailingShortRunHeightPx(
  items: readonly EmrPageItem[],
  fromIndex: number,
  maxItemPx: number,
): number | null {
  let height = 0;
  for (let i = fromIndex; i < items.length; i++) {
    const it = items[i]!;
    if (it.kind === 'break') return null;
    if (it.filler) continue;
    if (it.compactHeightPx > maxItemPx) return null;
    height += it.compactHeightPx;
  }
  return height;
}

/**
 * 把文档顶层块按 A4 纸高排成多页。
 * 分页符：吃掉本页剩余白边 + 页缝 + 下页上边距。
 * 普通块/行片段：落到本页底边距/页缝时推到下一页。
 * 超高段落应先拆成行片段再喂进来，才能从中间切开。
 */
export function planEmrPageLayout(
  items: readonly EmrPageItem[],
  metrics: EmrPageMetrics,
): EmrPageLayoutPlan {
  const breakBoxes: EmrPageBreakBox[] = [];
  const softSpacers: EmrSoftSpacer[] = [];
  const cycle = cyclePx(metrics);
  if (cycle <= 0 || metrics.pageHeightPx <= 0) {
    return { breakBoxes, pageCount: 1, softSpacers };
  }

  let y = metrics.paddingTopPx;
  let pageIndex = 0;

  const pageTop = () => pageIndex * cycle;
  const pageBottom = () => pageTop() + metrics.pageHeightPx;
  const pageContentTop = () => pageTop() + metrics.paddingTopPx;
  const pageContentBottom = () => pageBottom() - metrics.paddingBottomPx;
  const pageContentHeight = () => pageContentBottom() - pageContentTop();
  /** 本页已排正文高度；过小视为空段残留，硬分页仍应吞掉以免首页空白 */
  const bodyOnPagePx = () => y - pageContentTop();
  /**
   * 硬分页生效所需的本页最低正文占比。
   * 入院记录模版曾用章节硬分页模拟 Word 多页；填满后符落在半空页上会撑出大片留白。
   * 低于此值忽略该符，后文继续排；接近页底的 Ctrl+Enter 仍换页。
   */
  const MIN_BODY_RATIO_FOR_HARD_BREAK = 0.6;

  const pushToNextPage = (item: EmrPageItem) => {
    const nextContent = (pageIndex + 1) * cycle + metrics.paddingTopPx;
    let skip = nextContent - y;
    const maxSkip = cycle + metrics.paddingTopPx + 8;
    if (skip > maxSkip) skip = maxSkip;
    const spacerPos = item.pos;
    const islandOff = item.islandTextOffset;
    if (skip > 0.5) {
      const last = softSpacers[softSpacers.length - 1];
      // 岛屿垫片按 textOffset 区分，禁止合并到 atom 前的同一 decoration（否则跨页内容连成一块）
      const canMerge = last && last.pos === spacerPos && last.islandTextOffset === islandOff;
      if (canMerge) {
        last.heightPx = Math.round(last.heightPx + skip);
      } else {
        softSpacers.push({
          pos: spacerPos,
          heightPx: Math.round(skip),
          ...(islandOff != null ? { islandTextOffset: islandOff } : {}),
        });
      }
    }
    y = nextContent;
    pageIndex += 1;
  };

  const syncPageIndexWithY = (item: EmrPageItem) => {
    while (y > pageContentBottom() + 0.5) {
      pushToNextPage(item);
    }
  };

  for (let i = 0; i < items.length; i++) {
    const item = items[i]!;
    const trailingFiller = item.filler && remainingAreFiller(items, i);
    if (trailingFiller) {
      const blockH = Math.max(0, item.compactHeightPx);
      if (blockH > 0 && y + blockH <= pageContentBottom() + 0.5) {
        y += blockH;
      }
      continue;
    }
    if (item.kind === 'break') {
      syncPageIndexWithY(item);
      // 本页还很空：忽略硬分页（含模版章节符），避免半页空白；仅页底附近的符才换页
      const minBodyPx = Math.max(40, pageContentHeight() * MIN_BODY_RATIO_FOR_HARD_BREAK);
      if (bodyOnPagePx() <= minBodyPx) {
        breakBoxes.push({ marginTopPx: 0, heightPx: 0, marginBottomPx: 0 });
        continue;
      }
      const remainingWhite = Math.max(0, pageBottom() - y);
      breakBoxes.push({
        marginTopPx: Math.round(remainingWhite),
        heightPx: Math.round(metrics.pageGapPx),
        marginBottomPx: Math.round(metrics.paddingTopPx),
      });
      y = (pageIndex + 1) * cycle + metrics.paddingTopPx;
      pageIndex += 1;
      continue;
    }

    const maxBlockH = pageContentHeight();
    const blockH = Math.max(0, item.compactHeightPx);

    if (blockH > 0) {
      // 行高低估导致 y 已越过页底：先补换页垫片（典型缺 1 页眉高度 → 第 2 页顶进页眉）
      syncPageIndexWithY(item);
      // 可拆分块（行级）：放不下就换页；页顶也须校验（canPush 旧逻辑会漏掉换页后的首块）
      if (blockH <= maxBlockH + 0.5) {
        while (y + blockH > pageContentBottom() + 0.5) {
          // 文末签名/空段合计不超过约 4 行时不另起一页，避免半页空白
          const trailH = trailingShortRunHeightPx(items, i, 80);
          const keepTrailing = trailH != null && trailH <= 140 && bodyOnPagePx() > 80;
          if (keepTrailing) {
            break;
          }
          pushToNextPage(item);
        }
      } else {
        // 原子超高块：本页剩余不够则整段推到下一页
        const spaceLeft = pageContentBottom() - y;
        if (y > pageContentTop() + 0.5 && blockH > spaceLeft + 0.5) {
          pushToNextPage(item);
        }
      }
      y += blockH;
    }
  }

  const fromHeight = Math.ceil((y - 1e-4) / cycle);
  const pageCount = Math.max(1, pageIndex + 1, fromHeight);
  return { breakBoxes, pageCount, softSpacers };
}

/** 比较两次排版结果，避免 decoration 事务空转 */
export function emrPageLayoutPlanKey(plan: EmrPageLayoutPlan): string {
  return JSON.stringify({
    b: plan.breakBoxes,
    p: plan.pageCount,
    s: plan.softSpacers,
  });
}
