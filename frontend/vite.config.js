import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  build: {
    sourcemap: false,
    rollupOptions: {
      onwarn(warning, warn) {
        // Suppress docx-preview source map warnings
        if (warning.code === 'THIS_IS_UNDEFINED') return
        if (warning.message?.includes('docx-preview')) return
        warn(warning)
      },
    },
  },
  optimizeDeps: {
    include: ['docx-preview'],
  },
})
