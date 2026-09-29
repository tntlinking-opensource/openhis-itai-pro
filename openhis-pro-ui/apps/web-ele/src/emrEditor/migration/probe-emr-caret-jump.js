/**
 * 光标窜尾排查：在病历编辑器页控制台粘贴执行。
 * 输入/删除几下后看日志：若 selection 突然变大且接近 docSize，即窜尾。
 *
 * 用法：复制整段到 Console → Enter；再在编辑器里打字/删字观察。
 * 停用：__emrCaretProbe.stop()
 */
(() => {
  const prev = window.__emrCaretProbe;
  if (prev?.stop) prev.stop();

  const logs = [];
  let last = null;

  const read = () => {
    const ed = window.__emrEditor ?? document.querySelector('.ProseMirror')?.pmViewDesc?.view;
    const view = ed?.view ?? ed;
    if (!view?.state) return null;
    const { selection, doc } = view.state;
    const active = document.activeElement;
    const island =
      active instanceof HTMLElement &&
      (active.matches?.(
        '.emr-fillable-field__input,.emr-free-text__text,.emr-metadata-chip__text,.emr-bracket-field__body',
      ) ||
        active.closest?.(
          '.emr-fillable-field__input,.emr-free-text__text,.emr-metadata-chip__text,.emr-bracket-field__body',
        ));
    return {
      t: Date.now(),
      from: selection.from,
      to: selection.to,
      empty: selection.empty,
      docSize: doc.content.size,
      nearEnd: selection.head > doc.content.size - 8,
      active: active?.className || active?.tagName || null,
      island: !!island,
      softDom: document.querySelectorAll('.emr-page-soft-break').length,
      islandSoft: document.querySelectorAll('[data-emr-island-soft-break]').length,
    };
  };

  const onSel = () => {
    const cur = read();
    if (!cur) return;
    const jumped =
      last &&
      ((cur.nearEnd && !last.nearEnd) ||
        (Math.abs(cur.from - last.from) > 40 && cur.from > last.from));
    if (jumped) {
      const row = { kind: 'JUMP', prev: last, cur };
      logs.push(row);
      console.warn('[emr-caret-jump]', row);
    }
    last = cur;
  };

  document.addEventListener('selectionchange', onSel);
  const dump = () => {
    const cur = read();
    console.log('[emr-caret-jump] now', cur, 'jumps', logs.slice(-10));
    return { cur, jumps: logs.slice() };
  };

  window.__emrCaretProbe = {
    dump,
    stop() {
      document.removeEventListener('selectionchange', onSel);
      delete window.__emrCaretProbe;
      console.log('[emr-caret-jump] stopped');
    },
  };
  last = read();
  console.log('[emr-caret-jump] ready — type/delete then __emrCaretProbe.dump()');
})();
