<template>
  <div class="stat-card">
    <div class="ring" :style="{ '--accent': color, '--value': value + '%' }">
      <span>{{ display }}</span>
    </div>
    <div class="stat-label">{{ label }}</div>
  </div>
</template>

<script setup>
defineProps({
  label: {
    type: String,
    required: true
  },
  display: {
    type: String,
    required: true
  },
  value: {
    type: Number,
    default: 60
  },
  color: {
    type: String,
    default: '#2f72f6'
  }
})
</script>

<style scoped>
.stat-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 118px;
  gap: 12px;
}

.ring {
  display: grid;
  width: 72px;
  height: 72px;
  background: conic-gradient(var(--accent) var(--value), #e9edf3 0);
  border-radius: 50%;
  place-items: center;
}

.ring::before {
  position: absolute;
  width: 56px;
  height: 56px;
  background: #fff;
  border-radius: 50%;
  content: "";
}

.ring span {
  position: relative;
  z-index: 1;
  font-size: 18px;
  font-weight: 700;
}

.stat-label {
  color: #54657a;
  font-size: 13px;
}
</style>
