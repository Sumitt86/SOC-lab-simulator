import { useState, useEffect } from "react";
import { getScoringSummary, getGameEvents, getScoreBreakdown } from "../api/client";

export default function GameOverModal({ summary, onRestart }) {
  const [scoringDetails, setScoringDetails] = useState(null);
  const [gameEvents, setGameEvents] = useState([]);
  const [scoreBreakdown, setScoreBreakdown] = useState(null);
  const [showDetails, setShowDetails] = useState(false);

  useEffect(() => {
    if (summary && summary.gameStatus !== "ACTIVE") {
      fetchScoringDetails();
    }
  }, [summary]);

  const fetchScoringDetails = async () => {
    try {
      const [scoringSummary, events, breakdown] = await Promise.all([
        getScoringSummary(),
        getGameEvents(),
        getScoreBreakdown()
      ]);
      setScoringDetails(scoringSummary);
      setGameEvents(events.slice(-10));
      setScoreBreakdown(breakdown);
    } catch (error) {
      console.error("Failed to fetch scoring details:", error);
    }
  };

  if (!summary || summary.gameStatus === "ACTIVE") return null;

  const isBlueWin = summary.gameStatus === "BLUE_WIN";
  const isDraw = summary.gameStatus === "DRAW";

  return (
    <div className="game-over-overlay">
      <div className={`game-over-modal ${isBlueWin ? "blue-win" : isDraw ? "draw" : "red-win"}`}>
        <h2 className="game-over-title">
          {isBlueWin ? "🔵 Blue Team Wins!" : isDraw ? "⏱ Draw" : "🔴 Red Team Wins!"}
        </h2>
        <p className="game-over-reason">{summary.winReason}</p>

        <div className="game-over-scores">
          <div className="score-block red-score">
            <p className="score-label">Red Team</p>
            <p className="score-value">{summary.redScore}</p>
            {scoringDetails && (
              <p className="score-events text-sm text-gray-400">{scoringDetails.redEvents} events</p>
            )}
          </div>
          <div className="score-block blue-score">
            <p className="score-label">Blue Team</p>
            <p className="score-value">{summary.blueScore}</p>
            {scoringDetails && (
              <p className="score-events text-sm text-gray-400">{scoringDetails.blueEvents} events</p>
            )}
          </div>
        </div>

        <div className="game-over-stats">
          <p>Duration: {Math.floor(summary.elapsedSeconds / 60)}m {summary.elapsedSeconds % 60}s</p>
          <p>Final Phase: {summary.attackPhaseDisplay || summary.phase}</p>
          <p>Threat Score: {summary.threatScore}</p>
          <p>Difficulty: {summary.difficulty}</p>
        </div>

        <button 
          className="text-sm text-blue-400 hover:text-blue-300 mb-2"
          onClick={() => setShowDetails(!showDetails)}
        >
          {showDetails ? "▼ Hide Details" : "▶ Show Detailed Breakdown"}
        </button>

        {showDetails && scoreBreakdown && (
          <div className="score-breakdown bg-gray-800 rounded p-3 mb-3 max-h-60 overflow-y-auto">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <h4 className="text-red-400 font-semibold mb-2">Red Team Points</h4>
                <div className="text-sm space-y-1">
                  {Object.entries(scoreBreakdown.RED || {}).map(([eventType, points]) => (
                    <div key={eventType} className="flex justify-between">
                      <span className="text-gray-300">{eventType.replace(/_/g, " ")}</span>
                      <span className="text-red-400">{points > 0 ? "+" : ""}{points}</span>
                    </div>
                  ))}
                </div>
              </div>
              <div>
                <h4 className="text-blue-400 font-semibold mb-2">Blue Team Points</h4>
                <div className="text-sm space-y-1">
                  {Object.entries(scoreBreakdown.BLUE || {}).map(([eventType, points]) => (
                    <div key={eventType} className="flex justify-between">
                      <span className="text-gray-300">{eventType.replace(/_/g, " ")}</span>
                      <span className="text-blue-400">{points > 0 ? "+" : ""}{points}</span>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>
        )}

        <div className="game-over-actions">
          <button className="btn btn-restart" onClick={() => onRestart("EASY")}>Easy</button>
          <button className="btn btn-restart" onClick={() => onRestart("MEDIUM")}>Medium</button>
          <button className="btn btn-restart" onClick={() => onRestart("HARD")}>Hard</button>
        </div>
      </div>
    </div>
  );
}
