import React from 'react';
import PropTypes from 'prop-types';
import { useTheme } from '../context/ThemeContext';

function NewsCard({ news }) {
  const { theme } = useTheme();

  if (!news || !Array.isArray(news) || news.length === 0) {
    return (
      <div className={`${theme === 'dark' ? 'bg-[#1a1a1a] border-[#333333] text-[#e6e6e6]' : 'bg-white border-gray-200 text-gray-800'} rounded-2xl shadow-lg p-4 sm:p-6 border`}>
        <h2 className={`flex items-center text-xl sm:text-2xl font-bold mb-4 sm:mb-6`}>
          <span className="mr-2 text-blue-400">📰</span>
          Recent News
        </h2>
        <p className={`${theme === 'dark' ? 'text-gray-400' : 'text-gray-500'} text-center py-4`}>No recent news available</p>
      </div>
    );
  }

  return (
    <div className={`${theme === 'dark' ? 'bg-[#1a1a1a] border-[#333333] text-[#e6e6e6]' : 'bg-white border-gray-200 text-gray-800'} rounded-2xl shadow-lg p-4 sm:p-6 border`}>
      <h2 className={`flex items-center text-xl sm:text-2xl font-bold mb-4 sm:mb-6`}>
        <span className="mr-2 text-blue-400">📰</span>
        Recent News
      </h2>
      
      {/* News grid layout - single column on mobile, multi-column on larger screens */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 sm:gap-5">
        {news.map((article, index) => (
          <div 
            key={index} 
            className={`flex flex-col ${theme === 'dark' ? 'bg-[#262626] border-[#333333] hover:bg-[#2a2a2a]' : 'bg-gray-50 border-gray-200 hover:bg-gray-100'} rounded-xl border transition-colors overflow-hidden h-full`}
          >
            <div className="h-48 overflow-hidden">
              <img
                src={article.thumbnail || `https://placehold.co/400x300/${theme === 'dark' ? '1a1a1a/666666' : 'eeeeee/999999'}/png?text=No+Image`}
                alt={article.title}
                className="w-full h-full object-cover"
                onError={(e) => {
                  e.target.onerror = null;
                  e.target.src = `https://placehold.co/400x300/${theme === 'dark' ? '1a1a1a/666666' : 'eeeeee/999999'}/png?text=No+Image`;
                }}
              />
            </div>
            <div className="flex flex-col flex-grow p-4">
              <a 
                href={article.url} 
                target="_blank" 
                rel="noopener noreferrer"
                className="text-base font-semibold text-blue-400 hover:text-blue-300 mb-2 line-clamp-2"
              >
                {article.title}
              </a>
              <div className={`flex flex-wrap items-center text-xs ${theme === 'dark' ? 'text-gray-400' : 'text-gray-500'} mb-3`}>
                <span>{article.source}</span>
                <span className="mx-2">•</span>
                <span>{article.date}</span>
              </div>
              <p className={`${theme === 'dark' ? 'text-gray-300' : 'text-gray-600'} text-sm line-clamp-3 flex-grow`}>
                {article.summary}
              </p>
              <a 
                href={article.url} 
                target="_blank" 
                rel="noopener noreferrer"
                className="mt-3 text-sm font-medium text-blue-400 hover:text-blue-300 inline-flex items-center"
              >
                Read more 
                <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4 ml-1" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14 5l7 7m0 0l-7 7m7-7H3" />
                </svg>
              </a>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

NewsCard.propTypes = {
  news: PropTypes.arrayOf(
    PropTypes.shape({
      title: PropTypes.string.isRequired,
      url: PropTypes.string.isRequired,
      source: PropTypes.string.isRequired,
      date: PropTypes.string.isRequired,
      summary: PropTypes.string.isRequired,
      thumbnail: PropTypes.string
    })
  )
};

export default NewsCard;