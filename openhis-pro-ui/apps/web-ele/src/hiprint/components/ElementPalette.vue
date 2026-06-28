<script lang="ts" setup>
defineOptions({ name: 'HiprintElementPalette' });

/** 左侧可拖拽元素（tid 对应 defaultElementTypeProvider） */
interface PaletteItem {
  tid: string;
  label: string;
  icon: string;
  extraClass?: string;
}

const paletteGroups: { items: PaletteItem[]; title: string }[] = [
  {
    title: '常规',
    items: [
      { tid: 'defaultModule.text', label: '文本', icon: 'T' },
      { tid: 'defaultModule.longText', label: '长文', icon: '¶' },
      { tid: 'defaultModule.table', label: '表格', icon: '▦' },
      { tid: 'defaultModule.image', label: '图片', icon: '🖼' },
    ],
  },
  {
    title: '条码',
    items: [
      { tid: 'defaultModule.text', label: '条形码', icon: '|||', extraClass: 'ep-barcode' },
      { tid: 'defaultModule.text', label: '二维码', icon: 'QR', extraClass: 'ep-qrcode' },
    ],
  },
  {
    title: '辅助',
    items: [
      { tid: 'defaultModule.hline', label: '横线', icon: '—' },
      { tid: 'defaultModule.vline', label: '竖线', icon: '|' },
      { tid: 'defaultModule.rect', label: '矩形', icon: '□' },
    ],
  },
];
</script>

<template>
  <aside class="hiprint-palette">
    <div class="palette-header">
      <span class="i-mdi:view-grid-outline palette-header-icon"></span>
      元素列表
    </div>
    <div class="palette-body">
      <div v-for="group in paletteGroups" :key="group.title" class="palette-group">
        <div class="palette-group-title">{{ group.title }}</div>
        <div class="palette-grid">
          <div
            v-for="(item, idx) in group.items"
            :key="`${item.tid}-${idx}`"
            class="ep-draggable-item palette-item"
            :class="[item.extraClass]"
            :tid="item.tid"
          >
            <a href="javascript:void(0)">
              <span class="palette-icon">{{ item.icon }}</span>
              <p>{{ item.label }}</p>
            </a>
          </div>
        </div>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.hiprint-palette {
  display: flex;
  flex-direction: column;
  width: 220px;
  flex-shrink: 0;
  overflow: hidden;
  background: var(--sv-panel-bg);
  border-right: 1px solid var(--sv-border);
}

.palette-header {
  display: flex;
  flex-shrink: 0;
  gap: 6px;
  align-items: center;
  height: var(--sv-panel-header-h, 36px);
  padding: 0 12px;
  font-size: 13px;
  font-weight: 600;
  color: var(--sv-text-on-primary);
  background: var(--sv-primary);
}

.palette-header-icon {
  font-size: 16px;
}

.palette-body {
  flex: 1;
  overflow: auto;
}

.palette-group-title {
  padding: 10px 12px 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--sv-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.palette-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  padding: 0 10px 12px;
}

.palette-item {
  height: 68px;
}

.palette-item > a {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  text-decoration: none;
  cursor: grab;
  background: var(--el-bg-color);
  border: 1px solid var(--sv-border);
  border-radius: 6px;
  color: var(--el-text-color-regular);
  transition:
    border-color 0.15s,
    box-shadow 0.15s,
    color 0.15s;
}

.palette-item > a:hover {
  color: var(--sv-primary);
  border-color: var(--sv-primary-light);
  box-shadow: 0 2px 6px var(--sv-shadow-primary);
}

.palette-item > a:active {
  cursor: grabbing;
}

.palette-icon {
  font-size: 20px;
  line-height: 1.2;
}

.palette-item p {
  margin: 4px 0 0;
  font-size: 12px;
}
</style>
