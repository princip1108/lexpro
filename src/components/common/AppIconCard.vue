<template>
  <button class="app-icon-card" type="button" @click="$emit('select')">
    <span class="icon-box" :style="{ background: color }">
      <el-icon><component :is="icon" /></el-icon>
    </span>
    <span>{{ title }}</span>
  </button>
</template>

<script setup>
defineProps({
  title: {
    type: String,
    required: true
  },
  icon: {
    type: Object,
    required: true
  },
  color: {
    type: String,
    default: '#2f72f6'
  }
})

defineEmits(['select'])
</script>

<style scoped>
.app-icon-card {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  min-width: 92px;
  padding: 10px 8px;
  color: #34475f;
  background: #fff;
  border: 0;
  border-radius: 6px;
  cursor: pointer;
}

.app-icon-card:hover {
  background: #f4f8ff;
}

.icon-box {
  display: grid;
  width: 42px;
  height: 42px;
  margin-bottom: 9px;
  color: #fff;
  font-size: 22px;
  border-radius: 8px;
  place-items: center;
}

.app-icon-card span:last-child {
  font-size: 13px;
  white-space: nowrap;
}
</style>
