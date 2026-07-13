import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { ConfigProvider } from 'antd';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import zhCN from 'antd/locale/zh_CN';
import { AuthProvider } from './features/auth/AuthProvider';
import { App } from './app/App';
import './styles/global.css';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, staleTime: 15_000 },
    mutations: { retry: false },
  },
});

async function enableMocks() {
  if (import.meta.env.VITE_MOCK_API !== 'msw') return;
  const { startMockWorker } = await import('./mocks/browser');
  await startMockWorker();
}

void enableMocks().then(() => {
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <ConfigProvider
        locale={zhCN}
        theme={{
          token: {
            colorPrimary: '#0f766e',
            borderRadius: 6,
            fontFamily: '"Inter", "PingFang SC", "Microsoft YaHei", sans-serif',
          },
        }}
      >
        <QueryClientProvider client={queryClient}>
          <BrowserRouter>
            <AuthProvider>
              <App />
            </AuthProvider>
          </BrowserRouter>
        </QueryClientProvider>
      </ConfigProvider>
    </StrictMode>,
  );
});
