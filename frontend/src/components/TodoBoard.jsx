const PHASES = [1, 2, 3, 4];

function phaseLabel(phase) {
  if (phase === 1) return "Setup";
  if (phase === 2) return "Detection / Attack Logic";
  if (phase === 3) return "Response / Persistence";
  return "Reporting";
}

function priorityClass(priority) {
  if (priority === "HIGH") return "priority-high";
  if (priority === "MED") return "priority-medium";
  return "priority-low";
}

export default function TodoBoard({ team, todos, onToggle }) {
  const teamTodos = todos.filter((todo) => todo.team === team);

  return (
    <section className="todo-team">
      <h3>{team} Team Tasks</h3>
      {PHASES.map((phase) => {
        const items = teamTodos.filter((todo) => todo.phase === phase);
        const doneCount = items.filter((todo) => todo.done).length;
        const progress = items.length ? Math.round((doneCount / items.length) * 100) : 0;

        return (
          <article className="todo-phase" key={`${team}-${phase}`}>
            <header className="todo-phase-header">
              <p>{phaseLabel(phase)}</p>
              <span>{progress}%</span>
            </header>
            <div className="progress-track">
              <div className="progress-fill" style={{ width: `${progress}%` }} />
            </div>
            <div className="todo-items">
              {items.length === 0 ? <p className="placeholder">No tasks in this phase</p> : null}
              {items.map((todo) => (
                <button className="todo-item" key={todo.id} onClick={() => onToggle(todo.id)}>
                  <span className={`todo-checkbox ${todo.done ? "done" : ""}`} aria-hidden="true" />
                  <span className={`todo-title ${todo.done ? "done" : ""}`}>{todo.title}</span>
                  <span className={`todo-priority ${priorityClass(todo.priority)}`}>{todo.priority}</span>
                </button>
              ))}
            </div>
          </article>
        );
      })}
    </section>
  );
}
