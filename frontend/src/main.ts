import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import { createApp } from 'vue'

import App from './App.vue'
import router from './router'
import 'element-plus/dist/index.css'
import './assets/styles.css'

const application = createApp(App)

application.use(createPinia())
application.use(router)
application.use(ElementPlus)
application.mount('#app')
