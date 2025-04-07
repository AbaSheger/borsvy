import { Link } from 'react-router-dom';
import { useState } from 'react';
import { useTheme } from '../context/ThemeContext';

function Navbar() {
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const { theme } = useTheme();

  return (
    <nav className={`${theme === 'dark' ? 'bg-[#1a1a1a] text-[#e6e6e6]' : 'bg-blue-600 text-white'}`}>
      <div className="container mx-auto px-4 py-3">
        <div className="flex justify-between items-center">
          <div>
            <Link to="/" className="text-xl sm:text-2xl font-bold flex items-center">
              <div className="transform hover:scale-105 transition-all duration-300">
                <div className="bg-gradient-to-r from-blue-500 via-purple-500 to-pink-500 p-1 rounded-2xl shadow-2xl">
                  <div className={`${theme === 'dark' ? 'bg-[#1a1a1a]' : 'bg-white'} rounded-xl p-6`}>
                    <span className="text-5xl font-bold text-transparent bg-clip-text bg-gradient-to-r from-blue-400 to-purple-400 tracking-tight">
                      Bö<span className="text-yellow-400">rs</span>vy
                    </span>
                  </div>
                </div>
              </div>
            </Link>
          </div>
          
          <div className="hidden md:flex space-x-6">
            <Link to="/" className={`${theme === 'dark' ? 'hover:text-blue-400' : 'hover:text-blue-200'} transition-colors`}>
              Home
            </Link>
            <Link to="/favorites" className={`${theme === 'dark' ? 'hover:text-blue-400' : 'hover:text-blue-200'} transition-colors`}>
              Favorites
            </Link>
          </div>
          
          <div className="flex md:hidden">
            <button 
              className={`${theme === 'dark' ? 'text-[#e6e6e6]' : 'text-white'} focus:outline-none`}
              onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
            >
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-6 h-6">
                {isMobileMenuOpen ? (
                  <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                ) : (
                  <path strokeLinecap="round" strokeLinejoin="round" d="M3.75 6.75h16.5M3.75 12h16.5m-16.5 5.25h16.5" />
                )}
              </svg>
            </button>
          </div>
        </div>
      </div>
      
      {/* Mobile menu */}
      {isMobileMenuOpen && (
        <div className={`md:hidden ${theme === 'dark' ? 'bg-[#262626]' : 'bg-blue-700'}`}>
          <div className="container mx-auto px-4 py-2 space-y-2">
            <Link 
              to="/" 
              className={`block py-2 ${theme === 'dark' ? 'hover:text-blue-400' : 'hover:text-blue-200'} transition-colors`}
              onClick={() => setIsMobileMenuOpen(false)}
            >
              Home
            </Link>
            <Link 
              to="/favorites" 
              className={`block py-2 ${theme === 'dark' ? 'hover:text-blue-400' : 'hover:text-blue-200'} transition-colors`}
              onClick={() => setIsMobileMenuOpen(false)}
            >
              Favorites
            </Link>
          </div>
        </div>
      )}
    </nav>
  );
}

export default Navbar;