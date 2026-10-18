/**
 * 行级 + 行内字符级 diff（审批左右对比高亮，无第三方依赖）。
 */

export type DiffSideToken = {
  text: string;
  /** 相对另一侧：same 不变 / add 新增 / remove 删除 */
  kind: 'add' | 'remove' | 'same';
};

export type DiffRow = {
  /** 左侧行号（删除/不变）；新增行无左侧号 */
  leftNo?: number;
  rightNo?: number;
  leftTokens: DiffSideToken[];
  rightTokens: DiffSideToken[];
  /** 整行语义 */
  rowKind: 'add' | 'change' | 'equal' | 'remove';
};

/** 按行对比，变动行内再做字符级高亮 */
export function buildTextDiffRows(leftText: string, rightText: string): DiffRow[] {
  const leftLines = splitLines(leftText);
  const rightLines = splitLines(rightText);
  const lcs = buildLcsTable(leftLines, rightLines);
  const ops = backtrackOps(leftLines, rightLines, lcs);

  const rows: DiffRow[] = [];
  let leftNo = 0;
  let rightNo = 0;
  let i = 0;
  while (i < ops.length) {
    const op = ops[i]!;
    if (op.type === 'equal') {
      leftNo += 1;
      rightNo += 1;
      rows.push({
        rowKind: 'equal',
        leftNo,
        rightNo,
        leftTokens: [{ text: op.left, kind: 'same' }],
        rightTokens: [{ text: op.right, kind: 'same' }],
      });
      i += 1;
      continue;
    }
    // 相邻 remove+add 合并为 change，便于行内高亮
    if (op.type === 'remove' && ops[i + 1]?.type === 'add') {
      const next = ops[i + 1]!;
      leftNo += 1;
      rightNo += 1;
      const { leftTokens, rightTokens } = diffInline(op.left, next.right);
      rows.push({
        rowKind: 'change',
        leftNo,
        rightNo,
        leftTokens,
        rightTokens,
      });
      i += 2;
      continue;
    }
    if (op.type === 'remove') {
      leftNo += 1;
      rows.push({
        rowKind: 'remove',
        leftNo,
        leftTokens: [{ text: op.left, kind: 'remove' }],
        rightTokens: [],
      });
      i += 1;
      continue;
    }
    rightNo += 1;
    rows.push({
      rowKind: 'add',
      rightNo,
      leftTokens: [],
      rightTokens: [{ text: op.right, kind: 'add' }],
    });
    i += 1;
  }
  return rows;
}

type LineOp =
  | { type: 'add'; right: string }
  | { type: 'equal'; left: string; right: string }
  | { type: 'remove'; left: string };

function splitLines(text: string): string[] {
  if (!text) return [];
  return text.replace(/\r\n/g, '\n').replace(/\r/g, '\n').split('\n');
}

function buildLcsTable(a: string[], b: string[]): number[][] {
  const n = a.length;
  const m = b.length;
  const dp: number[][] = Array.from({ length: n + 1 }, () => Array.from({ length: m + 1 }, () => 0));
  for (let i = n - 1; i >= 0; i -= 1) {
    for (let j = m - 1; j >= 0; j -= 1) {
      if (a[i] === b[j]) {
        dp[i]![j] = (dp[i + 1]![j + 1] ?? 0) + 1;
      } else {
        dp[i]![j] = Math.max(dp[i + 1]![j] ?? 0, dp[i]![j + 1] ?? 0);
      }
    }
  }
  return dp;
}

function backtrackOps(a: string[], b: string[], dp: number[][]): LineOp[] {
  const ops: LineOp[] = [];
  let i = 0;
  let j = 0;
  while (i < a.length && j < b.length) {
    if (a[i] === b[j]) {
      ops.push({ type: 'equal', left: a[i]!, right: b[j]! });
      i += 1;
      j += 1;
      continue;
    }
    if ((dp[i + 1]![j] ?? 0) >= (dp[i]![j + 1] ?? 0)) {
      ops.push({ type: 'remove', left: a[i]! });
      i += 1;
    } else {
      ops.push({ type: 'add', right: b[j]! });
      j += 1;
    }
  }
  while (i < a.length) {
    ops.push({ type: 'remove', left: a[i]! });
    i += 1;
  }
  while (j < b.length) {
    ops.push({ type: 'add', right: b[j]! });
    j += 1;
  }
  return ops;
}

/** 字符级 LCS，标记左右两侧增删 */
function diffInline(left: string, right: string): {
  leftTokens: DiffSideToken[];
  rightTokens: DiffSideToken[];
} {
  if (left === right) {
    return {
      leftTokens: [{ text: left, kind: 'same' }],
      rightTokens: [{ text: right, kind: 'same' }],
    };
  }
  const a = [...left];
  const b = [...right];
  const dp = buildLcsTable(a, b);
  const leftTokens: DiffSideToken[] = [];
  const rightTokens: DiffSideToken[] = [];
  let i = 0;
  let j = 0;
  while (i < a.length && j < b.length) {
    if (a[i] === b[j]) {
      pushToken(leftTokens, a[i]!, 'same');
      pushToken(rightTokens, b[j]!, 'same');
      i += 1;
      j += 1;
      continue;
    }
    if ((dp[i + 1]![j] ?? 0) >= (dp[i]![j + 1] ?? 0)) {
      pushToken(leftTokens, a[i]!, 'remove');
      i += 1;
    } else {
      pushToken(rightTokens, b[j]!, 'add');
      j += 1;
    }
  }
  while (i < a.length) {
    pushToken(leftTokens, a[i]!, 'remove');
    i += 1;
  }
  while (j < b.length) {
    pushToken(rightTokens, b[j]!, 'add');
    j += 1;
  }
  return { leftTokens: mergeTokens(leftTokens), rightTokens: mergeTokens(rightTokens) };
}

function pushToken(tokens: DiffSideToken[], ch: string, kind: DiffSideToken['kind']) {
  const last = tokens[tokens.length - 1];
  if (last && last.kind === kind) {
    last.text += ch;
    return;
  }
  tokens.push({ text: ch, kind });
}

function mergeTokens(tokens: DiffSideToken[]): DiffSideToken[] {
  return tokens.filter((t) => t.text.length > 0);
}
