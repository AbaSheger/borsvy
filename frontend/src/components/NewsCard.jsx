import PropTypes from 'prop-types';

function NewsCard({ news }) {
  if (!news || !Array.isArray(news) || news.length === 0) {
    return (
      <div className="bg-[#1a1a1a] rounded-xl shadow-sm p-6 border border-[#333333]">
        <h2 className="flex items-center text-xl font-bold text-[#e6e6e6] mb-4">
          <span className="mr-2 text-blue-400">📰</span>
          Recent News
        </h2>
        <p className="text-gray-400 text-center">No recent news available</p>
      </div>
    );
  }

  return (
    <div className="bg-[#1a1a1a] rounded-xl shadow-sm p-6 border border-[#333333]">
      <h2 className="flex items-center text-xl font-bold text-[#e6e6e6] mb-4">
        <span className="mr-2 text-blue-400">📰</span>
        Recent News
      </h2>
      <div className="space-y-4">
        {news.map((article, index) => (
          <div key={index} className="flex flex-col sm:flex-row gap-4 p-3 sm:p-4 bg-[#262626] rounded-lg border border-[#333333] hover:bg-[#2a2a2a] transition-colors">
            <div className="flex-shrink-0">
              <img
                src={article.thumbnail || 'https://placehold.co/150x150/1a1a1a/666666/png?text=No+Image'}
                alt={article.title}
                className="w-full sm:w-40 h-40 object-cover rounded-lg"
                onError={(e) => {
                  e.target.onerror = null;
                  e.target.src = 'https://placehold.co/150x150/1a1a1a/666666/png?text=No+Image';
                }}
              />
            </div>
            <div className="flex-grow">
              <a 
                href={article.url} 
                target="_blank" 
                rel="noopener noreferrer"
                className="text-base sm:text-lg font-semibold text-blue-400 hover:text-blue-300 mb-1 block"
              >
                {article.title}
              </a>
              <div className="flex flex-wrap items-center text-xs sm:text-sm text-gray-400 mb-2">
                <span>{article.source}</span>
                <span className="mx-2">•</span>
                <span>{article.date}</span>
              </div>
              <p className="text-gray-300 text-sm line-clamp-2">
                {article.summary}
              </p>
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