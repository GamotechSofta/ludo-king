import { formatMoney, formatNumber } from "../utils/format";

function operatorsFrom(summary) {
  if (!summary) return [];
  if (Array.isArray(summary.operators)) return summary.operators;
  if (Array.isArray(summary.platforms)) return summary.platforms;
  if (summary.byOperator && typeof summary.byOperator === "object") {
    return Object.entries(summary.byOperator).map(([id, row]) => ({
      operatorId: id,
      ...(typeof row === "object" ? row : { profit: row }),
    }));
  }
  return [];
}

export default function PlatformsPage({ summary, loading, error, onSelectPlatform }) {
  const currency = summary?.currency || "INR";
  const operators = operatorsFrom(summary);

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <h1>Platforms</h1>
          <p className="page-lead">
            Per-operator breakdown. Players from different platforms can share a
            match; attribution follows each operator session.
          </p>
        </div>
      </header>

      {error && <div className="banner-error">{error}</div>}
      {loading && !summary ? (
        <div className="empty-state">Loading platforms…</div>
      ) : operators.length === 0 ? (
        <div className="empty-state">No operators found in summary.</div>
      ) : (
        <div className="platform-grid">
          {operators.map((op) => {
            const id = op.operatorId || op.id;
            return (
              <button
                key={id || op.name}
                type="button"
                className="platform-card"
                onClick={() => onSelectPlatform(id, op)}
              >
                <div className="platform-name">
                  {op.name || op.domain || op.operatorName || id || "Operator"}
                </div>
                <div className="cell-muted">{op.domain || id}</div>
                <div className="platform-stats">
                  <Stat label="Users" value={formatNumber(op.users ?? op.usersCount ?? 0)} />
                  <Stat label="Games" value={formatNumber(op.games ?? op.gamesCount ?? 0)} />
                  <Stat
                    label="Income"
                    value={formatMoney(op.income ?? op.totalIncome, currency)}
                  />
                  <Stat
                    label="Profit"
                    value={formatMoney(
                      op.platformProfit ?? op.profit ?? op.attributedProfit,
                      currency
                    )}
                  />
                </div>
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}

function Stat({ label, value }) {
  return (
    <div>
      <div className="summary-label">{label}</div>
      <div className="platform-stat-value">{value}</div>
    </div>
  );
}
