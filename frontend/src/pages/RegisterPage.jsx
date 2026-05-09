import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Button, Input, Checkbox, message } from 'antd';
import { GoogleLogin } from '@react-oauth/google';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';

const RegisterPage = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [agreed, setAgreed] = useState(false);
  const [loading, setLoading] = useState(false);
  const { register, loginWithGoogle } = useAuth();
  const navigate = useNavigate();
  const { theme } = useTheme();

  const handleRegister = async () => {
    if (!email || !password) { message.warning('Email and password required'); return; }
    if (password.length < 8) { message.warning('Password must be at least 8 characters'); return; }
    if (!agreed) { message.warning('Please accept the terms'); return; }
    setLoading(true);
    try {
      await register(email.trim(), password);
      message.success('Account created!');
      navigate('/');
    } catch (err) {
      message.error(err?.response?.data?.error || 'Registration failed');
    } finally {
      setLoading(false);
    }
  };

  const handleGoogle = async (credentialResponse) => {
    if (!agreed) { message.warning('Please accept the terms'); return; }
    try {
      await loginWithGoogle(credentialResponse.credential);
      message.success('Account created with Google!');
      navigate('/');
    } catch (err) {
      message.error(err?.response?.data?.error || 'Google sign-up failed');
    }
  };

  return (
    <div className="flex items-center justify-center min-h-[calc(100vh-80px)]">
      <div className={`w-full max-w-sm p-8 rounded-2xl border ${theme === 'dark' ? 'bg-[#1a1a1a] border-[#333333]' : 'bg-white border-gray-200'} shadow-lg`}>
        <h1 className="text-2xl font-bold text-[#e6e6e6] mb-6 text-center">Create your account</h1>

        <div className="space-y-3">
          <Input
            size="large"
            placeholder="Email"
            type="email"
            value={email}
            onChange={e => setEmail(e.target.value)}
            className="dark:bg-[#262626] dark:border-[#333333] dark:text-gray-200"
          />
          <Input.Password
            size="large"
            placeholder="Password (min 8 chars)"
            value={password}
            onChange={e => setPassword(e.target.value)}
            onPressEnter={handleRegister}
            className="dark:bg-[#262626] dark:border-[#333333] dark:text-gray-200"
          />
          <Checkbox
            checked={agreed}
            onChange={e => setAgreed(e.target.checked)}
            className="text-gray-400 text-sm"
          >
            I agree to the{' '}
            <Link to="/terms" className="text-blue-400 hover:text-blue-300">Terms of Service</Link>
            {' '}and{' '}
            <Link to="/privacy" className="text-blue-400 hover:text-blue-300">Privacy Policy</Link>
          </Checkbox>
          <Button
            type="primary"
            block
            size="large"
            loading={loading}
            onClick={handleRegister}
            className="bg-blue-500 hover:bg-blue-600 border-blue-500"
          >
            Create account
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
            onError={() => message.error('Google sign-up failed')}
            theme="filled_black"
            shape="rectangular"
            width="320"
          />
        </div>

        <p className="text-center text-sm text-gray-500 mt-6">
          Already have an account?{' '}
          <Link to="/login" className="text-blue-400 hover:text-blue-300">Sign in</Link>
        </p>
      </div>
    </div>
  );
};

export default RegisterPage;
