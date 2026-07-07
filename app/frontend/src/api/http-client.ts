import axios from 'axios';

type AccessTokenProvider = () => string | undefined | Promise<string | undefined>;
let accessTokenProvider: AccessTokenProvider = () => undefined;

export function setAccessTokenProvider(provider: AccessTokenProvider) {
  accessTokenProvider = provider;
}

export async function getAccessToken() {
  return accessTokenProvider();
}

export const httpClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
  timeout: 20_000,
});

httpClient.interceptors.request.use(async (config) => {
  const token = await accessTokenProvider();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  config.headers['X-Request-ID'] ??= crypto.randomUUID();
  return config;
});

httpClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401 && window.location.pathname !== '/login') {
      window.location.assign('/login?reason=session-expired');
    }
    return Promise.reject(error);
  },
);
