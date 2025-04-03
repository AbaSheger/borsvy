// Backend API URL
const API_URL = 'https://borsvy-backend-borsvy-295875c6.koyeb.app';

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