export default function StatCard({ label, value, subtext, tone }) {
  return (
    <article className={`stat-card ${tone}`}>
      <p className="stat-label">{label}</p>
      <p className="stat-value">{value}</p>
      <p className="stat-subtext">{subtext}</p>
    </article>
  );
}
