<script setup lang="ts">
/**
 * 平台菜单 path=/basic/emr-template，正文走 Canvas 模板页。
 * 入口必须走 `#/`，院点 override 才能稳定命中。
 */
import EmrCanvasTemplatePage from '#/views/basic/emr-canvas-template/EmrCanvasTemplatePage.vue';

defineOptions({
  name: 'EmrTemplate',
});
</script>

<template>
  <EmrCanvasTemplatePage />
</template>
