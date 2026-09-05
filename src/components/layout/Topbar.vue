<template>
  <header class="topbar">
    <div class="crumb">{{ crumb }}</div>
    <div class="right"><div class="model-switch">
      <button class="model-chip" :class="{open}" type="button" title="点击查看生效模型" @click.stop="toggle"><span class="dot"></span><span>{{ config.activeMode || '规则引擎(离线)' }}</span><span class="caret">▾</span></button>
      <div v-if="open" class="model-menu" @click.stop><div class="mm-head">切换生效大模型</div><div class="mm-list">
        <div v-for="item in enabledItems" :key="item.id" class="mm-item" :class="{'mm-on':item.active}" :title="`${item.displayName} · ${item.modelName}`"><span class="mm-name">{{ item.displayName || item.modelName }}</span><span v-if="item.active" class="mm-tag">生效</span><span v-else class="mm-sub">{{ item.modelName }}</span></div>
        <div v-for="item in disabledItems" :key="item.id" class="mm-item mm-disabled" title="已停用,可在模型配置页启用"><span class="mm-name">{{ item.displayName || item.modelName }}</span><span class="mm-tag">停用</span></div>
        <div v-if="!(config.items || []).length" class="mm-empty">暂无已启用的大模型<br>请在「模型配置」页添加并启用</div>
      </div><div class="mm-foot">模型不可用时系统自动回退内置规则引擎,业务不中断</div></div>
    </div></div>
  </header>
</template>
<script setup>
import { computed,onBeforeUnmount,onMounted,ref } from 'vue'
import { useRoute } from 'vue-router'
import { getModelConfiguration } from '../../api/system'
const route=useRoute(),open=ref(false),config=ref({activeMode:'规则引擎(离线)',items:[]})
const crumb=computed(()=>route.path==='/case-recommend'&&route.query.view==='library'?'典型案例库':route.meta.title||'工作台')
const enabledItems=computed(()=>(config.value.items||[]).filter(item=>item.enabled)),disabledItems=computed(()=>(config.value.items||[]).filter(item=>!item.enabled))
function close(){open.value=false}function toggle(){open.value=!open.value}
onMounted(async()=>{document.addEventListener('click',close);try{config.value=await getModelConfiguration()}catch{/* dashboard remains available when model status is unavailable */}})
onBeforeUnmount(()=>document.removeEventListener('click',close))
</script>
