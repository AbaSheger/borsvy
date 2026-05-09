import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Button, Input, message } from 'antd';
import { GoogleLogin } from '@react-oauth/google';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';

const LoginPage = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const { login, loginWithGoogle } = useAuth();
  const navigate = useNavigate();
  const { theme } = useTheme();

  const handleLogin = async () => {
    if (!email || !password) { message.warning('Email and password required'); return; }
    setLoading(true);
    try {
      await login(email.trim(), password);
      message.success('Logged in');
      navigate('/');
    } catch (err) {
      message.error(err?.response?.data?.error || 'Login failed');
    } finally {
      setLoading(false);
    }
  };

  const handleGoogle = async (credentialResponse) => {
    try {
      await loginWithGoogle(credentialResponse.credential);
      message.success('Logged in with Google');
      navigate('/');
    } catch (err) {
      message.error(err?.response?.data?.error || 'Google login failed');
    }
  };

  return (
    <div className="flex items-center justify-center min-h-[calc(100vh-80px)]">
      <div className={`w-full max-w-sm p-8 rounded-2xl border ${theme === 'dark' ? 'bg-[#1a1a1a] border-[#333333]' : 'bg-white border-gray-200'} shadow-lg`}>
        <h1 className="text-2xl font-bold text-[#e6e6e6] mb-6 text-center">Sign in to BörsVy</h1>

        <div className="space-y-3">
          <Input
            size="large"
            placeholder="Email"
            type="email"
            value={email}
            onChange={e => setEmail(e.target.value)}
            onPressEnter={handleLogin}
            className="dark:bg-[#262626] dark:border-[#333333] dark:text-gray-200"
          />
          <Input.Password
            size="large"
            placeholder="Password"
            value={password}
            onChange={e => setPassword(e.target.value)}
            onPressEnter={handleLogin}
            className="dark:bg-[#262626] dark:border-[#333333] dark:text-gray-200"
          />
          <Button
            type="primary"
            block
            size="large"
            loading={loading}
            onClick={handleLogin}
            className="bg-blue-500 hover:bg-blue-600 border-blue-500"
          >
            Sign in
          </Button>
        </div>

        <div className="flex items-center gap-3 my-4">
          <div className="flex-1 h-px bg-[#333333]" />
          <span className="text-gray-500 text-sm">or</span>
          <div className="flex-1 h-px bg-[#333333]" />
        </div>

        <div className="flex justify-center">
          <GoogleLogin
            onSuccess={handleGoogle}
            onError={() => message.error('Google login failed')}
            theme="filled_black"
            shape="rectangular"
            width="320"
          />
        </div>

        <p className="text-center text-sm text-gray-500 mt-6">
          No account?{' '}
          <Link to="/register" className="text-blue-400 hover:text-blue-300">Create one</Link>
        </p>
      </div>
    </div>
  );
};

export default LoginPage;
