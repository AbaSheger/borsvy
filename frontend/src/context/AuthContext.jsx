import { createContext, useContext, useState, useEffect, useCallback } from 'react';
import axios from 'axios';
import { axiosConfig } from '../config';

const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  const fetchMe = useCallback(async () => {
    try {
      const res = await axios.get('/api/auth/me', axiosConfig);
      setUser(res.data);
    } catch {
      setUser(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchMe(); }, [fetchMe]);

  const login = async (email, password) => {
    const res = await axios.post('/api/auth/login', { email, password }, axiosConfig);
    setUser(res.data);
    return res.data;
  };

  const register = async (email, password) => {
    const res = await axios.post('/api/auth/register', { email, password }, axiosConfig);
    setUser(res.data);
    return res.data;
  };

  const loginWithGoogle = async (credential) => {
    const res = await axios.post('/api/auth/google', { credential }, axiosConfig);
    setUser(res.data);
    return res.data;
  };

  const logout = async () => {
    await axios.post('/api/auth/logout', {}, axiosConfig);
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, loading, login, register, loginWithGoogle, logout, refreshUser: fetchMe }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
};
