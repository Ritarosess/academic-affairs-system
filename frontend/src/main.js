import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'

import App from './App.vue'
import router from './router'
import { purgeLegacyLocalStorage } from './utils/authStorage'
import './styles/index.css'

// 认证令牌现存放于 sessionStorage（按标签页隔离，支持同一浏览器多用户同时登录）。
// 清理早期版本遗留在 localStorage 中的令牌与用户信息。
purgeLegacyLocalStorage()

const app = createApp(App)

// 全局注册 Element Plus 全部图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')
