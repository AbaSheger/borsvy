import { useTheme } from '../context/ThemeContext';

const SentimentBadge = ({ sentiment, confidence }) => {
  const map = {
    POSITIVE: { label: 'Bullish', color: 'text-emerald-400 bg-emerald-400/10 border-emerald-400/20' },
    NEGATIVE: { label: 'Bearish', color: 'text-rose-400 bg-rose-400/10 border-rose-400/20' },
    NEUTRAL:  { label: 'Neutral', color: 'text-amber-400 bg-amber-400/10 border-amber-400/20' },
  };
  const { label, color } = map[sentiment?.toUpperCase()] || map.NEUTRAL;
  const pct = confidence != null ? Math.round(confidence * 100) : null;

  return (
    <div className={`inline-flex items-center gap-2 px-3 py-1.5 rounded-xl border text-sm font-semibold ${color}`}>
      <span>{label}</span>
      {pct != null && <span className="opacity-70 font-normal text-xs">{pct}% confidence</span>}
    </div>
  );
};

const TechStat = ({ label, value, note, dark }) => (
  <div className={`px-4 py-3 rounded-xl ${dark ? 'bg-[#161616] border border-[#222]' : 'bg-slate-50 border border-slate-100'}`}>
    <p className={`text-xs mb-1 ${dark ? 'text-zinc-500' : 'text-slate-400'}`}>{label}</p>
    <p className={`text-sm font-bold tabular-nums ${dark ? 'text-zinc-100' : 'text-slate-800'}`}>
      {value != null ? Number(value).toFixed(2) : '—'}
    </p>
    {note && <p className={`text-xs mt-0.5 ${dark ? 'text-zinc-600' : 'text-slate-400'}`}>{note}</p>}
  </div>
);

const AnalysisVisualization = ({ analysis }) => {
  const { theme } = useTheme();
  const dark = theme === 'dark';

  if (!analysis) return null;

  const llm = analysis.llm || {};
  const technical = analysis.technical || {};
  const sentiment = llm.sentiment || analysis.sentiment;
  const confidence = llm.confidence ?? analysis.confidence;
  const summary = llm.summary || analysis.summary || analysis.aiAnalysis;
  const bullish = llm.bullishPoints || analysis.bullishPoints || [];
  const bearish = llm.bearishRisks || analysis.bearishRisks || [];
  const outlook = llm.outlook || analysis.outlook;

  const rsi = technical.rsi ?? analysis.rsi;
  const macd = technical.macd ?? analysis.macd;
  const sma20 = technical.sma20 ?? analysis.sma20;
  const sma50 = technical.sma50 ?? analysis.sma50;

  const card = dark
    ? 'bg-[#111111] border border-[#222222] rounded-2xl'
    : 'bg-white border border-slate-200 rounded-2xl';

  const hasLlm = summary || bullish.length > 0 || bearish.length > 0 || outlook;
  const hasTech = rsi != null || macd != null || sma20 != null || sma50 != null;

  if (!hasLlm && !hasTech) {
    return (
      <div className={`${card} p-8 text-center ${dark ? 'text-zinc-500' : 'text-slate-400'}`}>
        No analysis data available
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {/* LLM Analysis */}
      {hasLlm && (
        <div className={`${card} p-5 space-y-5`}>
          {/* Header */}
          <div className="flex items-center justify-between flex-wrap gap-3">
            <h3 className={`text-sm font-semibold uppercase tracking-wider ${dark ? 'text-zinc-400' : 'text-slate-500'}`}>
              AI Analysis
            </h3>
            {sentiment && <SentimentBadge sentiment={sentiment} confidence={confidence} />}
          </div>

          {/* Summary */}
          {summary && (
            <div>
              {summary.split('\n\n').filter(Boolean).map((para, i) => (
                <p key={i} className={`text-sm leading-relaxed mb-3 last:mb-0 ${dark ? 'text-zinc-300' : 'text-slate-600'}`}>
                  {para}
                </p>
              ))}
            </div>
          )}

          {/* Bullish / Bearish columns */}
          {(bullish.length > 0 || bearish.length > 0) && (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              {bullish.length > 0 && (
                <div className={`rounded-xl p-4 ${dark ? 'bg-emerald-400/5 border border-emerald-400/15' : 'bg-emerald-50 border border-emerald-100'}`}>
                  <p className="text-xs font-semibold text-emerald-400 uppercase tracking-wider mb-3">Bullish Signals</p>
                  <ul className="space-y-2">
                    {bullish.map((pt, i) => (
                      <li key={i} className={`text-sm flex gap-2 leading-snug ${dark ? 'text-zinc-300' : 'text-slate-600'}`}>
                        <span className="text-emerald-400 flex-shrink-0 mt-0.5">▲</span>
                        {pt}
                      </li>
                    ))}
                  </ul>
                </div>
              )}
              {bearish.length > 0 && (
                <div className={`rounded-xl p-4 ${dark ? 'bg-rose-400/5 border border-rose-400/15' : 'bg-rose-50 border border-rose-100'}`}>
                  <p className="text-xs font-semibold text-rose-400 uppercase tracking-wider mb-3">Key Risks</p>
                  <ul className="space-y-2">
                    {bearish.map((pt, i) => (
                      <li key={i} className={`text-sm flex gap-2 leading-snug ${dark ? 'text-zinc-300' : 'text-slate-600'}`}>
                        <span className="text-rose-400 flex-shrink-0 mt-0.5">▼</span>
                        {pt}
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          )}

          {/* Outlook */}
          {outlook && (
            <div className={`rounded-xl p-4 ${dark ? 'bg-blue-500/5 border border-blue-500/15' : 'bg-blue-50 border border-blue-100'}`}>
              <p className="text-xs font-semibold text-blue-400 uppercase tracking-wider mb-2">Outlook</p>
              <p className={`text-sm leading-relaxed ${dark ? 'text-zinc-300' : 'text-slate-600'}`}>{outlook}</p>
            </div>
          )}
        </div>
      )}

      {/* Technical indicators */}
      {hasTech && (
        <div className={`${card} p-5`}>
          <h3 className={`text-sm font-semibold uppercase tracking-wider mb-4 ${dark ? 'text-zinc-400' : 'text-slate-500'}`}>
            Technical Indicators
          </h3>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
            <TechStat label="RSI (14)"
              value={rsi}
              note={rsi != null ? (rsi > 70 ? 'Overbought' : rsi < 30 ? 'Oversold' : 'Neutral') : null}
              dark={dark} />
            <TechStat label="MACD" value={macd} dark={dark} />
            <TechStat label="SMA 20" value={sma20} dark={dark} />
            <TechStat label="SMA 50" value={sma50} dark={dark} />
          </div>
        </div>
      )}
    </div>
  );
};

export default AnalysisVisualization;
