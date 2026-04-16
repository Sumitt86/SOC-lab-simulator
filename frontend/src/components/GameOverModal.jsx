export default function GameOverModal({ summary, onRestart }) {
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
          </div>
          <div className="score-block blue-score">
            <p className="score-label">Blue Team</p>
            <p className="score-value">{summary.blueScore}</p>
          </div>
        </div>

        <div className="game-over-stats">
          <p>Duration: {summary.elapsedSeconds}s</p>
          <p>Final Phase: {summary.attackPhaseDisplay}</p>
          <p>Threat Score: {summary.threatScore}</p>
          <p>Difficulty: {summary.difficulty}</p>
        </div>

        <div className="game-over-actions">
          <button className="btn btn-restart" onClick={() => onRestart("EASY")}>Easy</button>
          <button className="btn btn-restart" onClick={() => onRestart("MEDIUM")}>Medium</button>
          <button className="btn btn-restart" onClick={() => onRestart("HARD")}>Hard</button>
        </div>
      </div>
    </div>
  );
}
