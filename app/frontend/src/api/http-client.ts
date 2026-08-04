import axios from 'axios';

let authenticationFailureHandler: () => void = () => undefined;

export function setAuthenticationFailureHandler(handler: () => void) {
  authenticationFailureHandler = handler;
}

export const httpClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
  timeout: 20_000,
  withCredentials: true,
  withXSRFToken: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
});

httpClient.interceptors.request.use((config) => {
  config.headers['X-Request-ID'] ??= crypto.randomUUID();
  return config;
});

httpClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const isAuthRequest = error.config?.url?.includes('/api/v1/auth/');
    if (error.response?.status === 401 && !isAuthRequest) {
      authenticationFailureHandler();
      if (window.location.pathname !== '/login') {
        window.location.assign('/login?reason=session-expired');
      }
    }
    return Promise.reject(error);
  },
);
