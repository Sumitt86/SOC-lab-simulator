import { useState, useEffect } from 'react';
import { getCurrentScores, getScoringSummary } from '../api/client';

/**
 * Real-time score display component showing Red vs Blue team scores.
 * Updates every second and shows score difference, event counts, and game duration.
 */
export default function ScoreDisplay() {
  const [scores, setScores] = useState({
    redScore: 0,
    blueScore: 0,
    scoreDifference: 0,
    elapsedSeconds: 0,
    gameStatus: 'ACTIVE',
  });
  const [summary, setSummary] = useState(null);

  useEffect(() => {
    // Fetch initial scores
    fetchScores();
    
    // Poll for updates every 2 seconds
    const interval = setInterval(fetchScores, 2000);
    
    return () => clearInterval(interval);
  }, []);

  const fetchScores = async () => {
    try {
      const currentScores = await getCurrentScores();
      setScores(currentScores);
      
      // Fetch summary for event counts every 10 updates
      if (Math.floor(Date.now() / 1000) % 10 === 0) {
        const summaryData = await getScoringSummary();
        setSummary(summaryData);
      }
    } catch (error) {
      console.error('Failed to fetch scores:', error);
    }
  };

  const formatTime = (seconds) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  };

  const getStatusColor = () => {
    switch (scores.gameStatus) {
      case 'RED_WIN': return 'text-red-600';
      case 'BLUE_WIN': return 'text-blue-600';
      case 'DRAW': return 'text-gray-600';
      default: return 'text-yellow-600';
    }
  };

  const getLeaderStyle = () => {
    if (scores.scoreDifference > 0) return 'border-red-500';
    if (scores.scoreDifference < 0) return 'border-blue-500';
    return 'border-gray-400';
  };

  return (
    <div className={`bg-gray-900 border-2 ${getLeaderStyle()} rounded-lg p-4 shadow-xl`}>
      {/* Header */}
      <div className="flex justify-between items-center mb-4">
        <h3 className="text-lg font-bold text-white">Live Score</h3>
        <div className="flex items-center space-x-2">
          <span className={`text-sm font-medium ${getStatusColor()}`}>
            {scores.gameStatus === 'ACTIVE' ? '🔴 LIVE' : scores.gameStatus}
          </span>
          <span className="text-sm text-gray-400">{formatTime(scores.elapsedSeconds)}</span>
        </div>
      </div>

      {/* Score Display */}
      <div className="grid grid-cols-3 gap-4 mb-4">
        {/* Red Team */}
        <div className="text-center">
          <div className="text-xs text-red-400 font-semibold mb-1">RED TEAM</div>
          <div className="text-3xl font-bold text-red-500">{scores.redScore}</div>
          {summary && (
            <div className="text-xs text-gray-400 mt-1">{summary.redEvents} events</div>
          )}
        </div>

        {/* Score Difference */}
        <div className="flex flex-col items-center justify-center">
          <div className="text-xs text-gray-500 mb-1">DIFF</div>
          <div className={`text-2xl font-bold ${
            scores.scoreDifference > 0 ? 'text-red-400' : 
            scores.scoreDifference < 0 ? 'text-blue-400' : 
            'text-gray-400'
          }`}>
            {scores.scoreDifference > 0 ? '+' : ''}{scores.scoreDifference}
          </div>
        </div>

        {/* Blue Team */}
        <div className="text-center">
          <div className="text-xs text-blue-400 font-semibold mb-1">BLUE TEAM</div>
          <div className="text-3xl font-bold text-blue-500">{scores.blueScore}</div>
          {summary && (
            <div className="text-xs text-gray-400 mt-1">{summary.blueEvents} events</div>
          )}
        </div>
      </div>

      {/* Progress Bar */}
      <div className="relative h-2 bg-gray-700 rounded-full overflow-hidden">
        <div 
          className="absolute left-0 top-0 h-full bg-red-500 transition-all duration-500"
          style={{ 
            width: `${Math.min(50, (scores.redScore / (scores.redScore + scores.blueScore + 1)) * 100)}%` 
          }}
        />
        <div 
          className="absolute right-0 top-0 h-full bg-blue-500 transition-all duration-500"
          style={{ 
            width: `${Math.min(50, (scores.blueScore / (scores.redScore + scores.blueScore + 1)) * 100)}%` 
          }}
        />
      </div>

      {/* Winner Indicator */}
      {scores.scoreDifference !== 0 && (
        <div className="text-center mt-3">
          <span className={`text-xs font-semibold ${
            scores.scoreDifference > 0 ? 'text-red-400' : 'text-blue-400'
          }`}>
            {scores.scoreDifference > 0 ? '🔴 Red Team Leading' : '🔵 Blue Team Leading'}
          </span>
        </div>
      )}
    </div>
  );
}
