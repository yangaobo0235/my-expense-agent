import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 25105,
    proxy: {
      '/api': 'http://localhost:25101',
      '/v3': 'http://localhost:25101',
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return undefined;
          if (id.includes('antd') || id.includes('@ant-design') || id.includes('@rc-component')) return 'vendor-antd';
          if (id.includes('react') || id.includes('scheduler')) return 'vendor-react';
          if (id.includes('@tanstack') || id.includes('axios') || id.includes('zustand')) return 'vendor-data';
          if (id.includes('keycloak-js')) return 'vendor-auth';
          return 'vendor';
        },
      },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/tests/setup.ts',
    exclude: ['node_modules/**', 'dist/**', 'e2e/**', 'playwright-report/**'],
  },
});
