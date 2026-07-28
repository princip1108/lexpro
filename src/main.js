import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './styles/global.css'
import App from './App.vue'
import router from './router'
import { UNAUTHORIZED_EVENT } from './api/http'

window.addEventListener(UNAUTHORIZED_EVENT, () => {
  const currentPath = router.currentRoute.value.fullPath
  if (router.currentRoute.value.path !== '/login') {
    router.replace({ path: '/login', query: { redirect: currentPath } })
  }
})

createApp(App).use(router).use(ElementPlus).mount('#app')
