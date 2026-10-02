import axios from 'axios';

const axiosClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
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