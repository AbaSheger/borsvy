// Backend API URL - Support for environment variables using Vite
const API_URL = import.meta.env.DEV 
  ? 'http://localhost:8080' // Local dev
  : (import.meta.env.VITE_API_URL || '');

// Axios default configuration
const axiosConfig = {
    baseURL: API_URL,
    headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json'
    },
    withCredentials: true
};

// Export the configuration
export { API_URL, axiosConfig };
