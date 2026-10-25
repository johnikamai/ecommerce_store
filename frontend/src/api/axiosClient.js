import axios from 'axios';
import { clearSession, tokenIsCurrent } from '../utils/session';

// API Gateway for microservices architecture
const PROD_API = 'http://localhost:8080/api';
const DEV_API = 'http://localhost:8080/api';

// VITE_API_BASE_URL still wins so the host stays overridable
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || DEV_API;

const axiosClient = axios.create({
  baseURL: API_BASE_URL,
  // The backend runs on a free-tier host that sleeps when idle, so the first
  // request after a pause has to cold-start the service. 15s aborted calls that
  // were about to succeed, leaving pages stuck on an empty result.
  timeout: 60000,
});

// Automatically attach the JWT token to every request, if we have one
axiosClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token && tokenIsCurrent(token)) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

axiosClient.interceptors.response.use(response => response, error => {
  if (error.response?.data && typeof error.response.data === 'object') {
    error.response.data = error.response.data.message || error.response.data.error || 'Request failed';
  }
  const url = error.config?.url || '';
  if (error.response?.status === 401 && localStorage.getItem('token') && !['/auth/login', '/auth/register', '/auth/reset-password'].includes(url)) {
    clearSession();
    if (!['/', '/login'].includes(window.location.pathname)) window.location.assign('/login');
  }
  return Promise.reject(error);
});
export default axiosClient;