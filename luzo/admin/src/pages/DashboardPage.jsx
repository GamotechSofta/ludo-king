import SummaryCards from "../components/SummaryCards";
import GamesTable from "../components/GamesTable";
import { formatMoney, formatNumber } from "../utils/format";

function operatorsFrom(summary) {
  if (!summary) return [];
  if (Array.isArray(summary.operators)) return summary.operators;
  if (Array.isArray(summary.topOperators)) return summary.topOperators;
  if (summary.byOperator && typeof summary.byOperator === "object") {
    return Object.entries(summary.byOperator).map(([id, row]) => ({
      operatorId: id,
      ...(typeof row === "object" ? row : { profit: row }),
    }));
  }
  return [];
}

export default function DashboardPage({
  summary,
  games,
  loading,
  error,
  onSelectGame,
  onOpenPlatforms,
  onOpenProfitLoss,
}) {
  const currency = summary?.currency || "INR";
  const operators = operatorsFrom(summary).slice(0, 5);

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <h1>Dashboard</h1>
          <p className="page-lead">Live overview of finished games and platform profit.</p>
        </div>
      </header>

      {error && <div className="banner-error">{error}</div>}
      {loading && !summary ? (
        <div className="empty-state">Loading dashboard…</div>
      ) : (
        <>
          <SummaryCards summary={summary} />

          <div className="dashboard-columns">
            <section className="panel">
              <div className="panel-header">
                <h2>Recent games</h2>
                <button
                  type="button"
                  className="btn btn-sm"
                  onClick={() => onOpenProfitLoss()}
                >
                  View all
                </button>
              </div>
              <GamesTable
                data={games}
                currency={currency}
                onSelect={onSelectGame}
              />
            </section>

            <section className="panel">
              <div className="panel-header">
                <h2>Top operators</h2>
                <button
                  type="button"
                  className="btn btn-sm"
                  onClick={onOpenPlatforms}
                >
                  Platforms
                </button>
              </div>
              {operators.length === 0 ? (
                <div className="empty-state">No operator stats yet.</div>
              ) : (
                <ul className="operator-list">
                  {operators.map((op) => {
                    const id = op.operatorId || op.id;
                    return (
                      <li key={id || op.name}>
                        <button
                          type="button"
                          className="operator-row"
                          onClick={() => onOpenProfitLoss({ operatorId: id })}
                        >
                          <div>
                            <div className="cell-strong">
                              {op.name || op.domain || op.operatorName || id}
                            </div>
                            <div className="cell-muted">
                              {formatNumber(op.games ?? op.gamesCount ?? 0)} games ·{" "}
                              {formatNumber(op.users ?? op.usersCount ?? 0)} users
                            </div>
                          </div>
                          <div className="operator-profit">
                            {formatMoney(
                              op.platformProfit ?? op.profit ?? op.attributedProfit,
                              currency
                            )}
                          </div>
                        </button>
                      </li>
                    );
                  })}
                </ul>
              )}
            </section>
          </div>
        </>
      )}
    </div>
  );
}
