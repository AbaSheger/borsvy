import { Button, message } from 'antd';
import { UserOutlined, CrownOutlined, LogoutOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const ProfilePage = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logout();
    message.success('Logged out');
    navigate('/');
  };

  if (!user) return null;

  return (
    <div className="max-w-xl mx-auto space-y-6">
      <h1 className="text-2xl font-bold text-[#e6e6e6] flex items-center gap-2">
        <UserOutlined /> Profile
      </h1>

      <div className="bg-[#1a1a1a] rounded-2xl border border-[#333333] p-6 space-y-4">
        <div>
          <p className="text-xs text-gray-400 mb-1">Email</p>
          <p className="text-[#e6e6e6] font-medium">{user.email}</p>
        </div>

        <div>
          <p className="text-xs text-gray-400 mb-1">Plan</p>
          <div className="flex items-center gap-2">
            {user.isPro ? (
              <>
                <CrownOutlined className="text-yellow-400" />
                <span className="text-yellow-400 font-semibold">Pro</span>
              </>
            ) : (
              <>
                <span className="text-gray-300 font-medium">Free</span>
                <Button
                  size="small"
                  type="primary"
                  onClick={() => navigate('/pricing')}
                  className="bg-blue-500 hover:bg-blue-600 border-blue-500 ml-2"
                >
                  Upgrade to Pro
                </Button>
              </>
            )}
          </div>
        </div>

        {!user.isPro && (
          <div className="bg-[#262626] rounded-xl p-4 border border-[#333333]">
            <p className="text-sm font-semibold text-[#e6e6e6] mb-2">Free plan limits</p>
            <ul className="text-sm text-gray-400 space-y-1">
              <li>• 2 AI analyses per day</li>
              <li>• 3 favorites</li>
              <li>• 3 portfolio holdings</li>
              <li>• 3 active price alerts</li>
              <li>• 1D and 1W charts only</li>
            </ul>
          </div>
        )}
      </div>

      <Button
        danger
        icon={<LogoutOutlined />}
        onClick={handleLogout}
        size="large"
      >
        Sign out
      </Button>
    </div>
  );
};

export default ProfilePage;
