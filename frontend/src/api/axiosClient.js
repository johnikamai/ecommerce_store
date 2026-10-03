import axios from 'axios';

// The deployed API. Also hardcoded in netlify/functions/keepalive.mjs and the
// GitHub Actions deploy, so this is not a new dependency on one host.
const PROD_API = 'https://ecommerce-backend-2gas.onrender.com/api';
const DEV_API = 'http://localhost:8080/api';

// VITE_API_BASE_URL still wins so the host stays overridable, but a production
// build must never fall back to localhost: Vite inlines env vars at build time,
// so a missing Netlify env var would otherwise ship a bundle that silently
// talks to the developer's own machine and renders an empty store.
const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL ||
  (import.meta.env.PROD ? PROD_API : DEV_API);

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
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

export default axiosClient;