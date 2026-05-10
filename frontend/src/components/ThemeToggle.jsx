import { MoonOutlined, SunOutlined } from '@ant-design/icons';
import { useTheme } from '../context/ThemeContext';

function ThemeToggle() {
  const { theme, toggleTheme } = useTheme();
  const dark = theme === 'dark';

  return (
    <button
      type="button"
      onClick={toggleTheme}
      title={dark ? 'Switch to light mode' : 'Switch to dark mode'}
      className="h-9 w-9 rounded-md grid place-items-center text-slate-500 dark:text-slate-400 hover:text-slate-950 dark:hover:text-white hover:bg-slate-100 dark:hover:bg-[#202838] transition-colors"
      aria-label={dark ? 'Switch to light mode' : 'Switch to dark mode'}
    >
      {dark ? <SunOutlined /> : <MoonOutlined />}
    </button>
  );
}

export default ThemeToggle;
