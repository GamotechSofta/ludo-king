import { formatMoney, formatNumber, formatPnL, pnlTone } from "../utils/format";
import { PieChart, BarChart } from "./Charts";

function pick(summary, keys, fallback = 0) {
  if (!summary) return fallback;
  for (const key of keys) {
    if (summary[key] != null) return summary[key];
  }
  return fallback;
}

function sliceOrBuild(summary, key, fallback) {
  const charts = summary?.charts;
  if (charts && Array.isArray(charts[key]) && charts[key].length) {
    return charts[key];
  }
  return fallback;
}

export default function PlAnalytics({ summary }) {
  if (!summary) return null;

  const currency = pick(summary, ["currency"], "INR");
  const income = Number(pick(summary, ["totalRealIncome", "totalIncome", "income"]));
  const payouts = Number(pick(summary, ["winnerPayouts", "totalPayouts", "payouts"]));
  const net = Number(pick(summary, ["platformProfit"]));
  const profitOnly = Number(pick(summary, ["totalProfit", "profit"], Math.max(0, net)));
  const lossFromApi = pick(summary, ["totalLoss", "loss"], null);
  const lossOnly =
    lossFromApi != null
      ? Math.abs(Number(lossFromApi))
      : net < 0
        ? Math.abs(net)
        : 0;
  const rake = Number(pick(summary, ["totalRake", "rake"]));
  const margin = Number(pick(summary, ["marginPct", "insights.marginPct"], 0));
  const fee = Number(pick(summary, ["platformFeePerPlayer"], 10));
  const insights = summary.insights || {};

  const profitLossPie = sliceOrBuild(summary, "profitLoss", [
    { label: "Profit", value: profitOnly, color: "#047857" },
    { label: "Loss", value: lossOnly, color: "#b91c1c" },
  ]);
  const incomePayoutPie = sliceOrBuild(summary, "incomePayout", [
    { label: "Income", value: income, color: "#0f766e" },
    { label: "Winner payouts", value: payouts, color: "#0369a1" },
  ]);
  const modeGamesPie = sliceOrBuild(summary, "modeGames", []);
  const outcomePie = sliceOrBuild(summary, "outcome", []);
  const fillPie = sliceOrBuild(summary, "fill", []);
  const modeProfitBars = sliceOrBuild(summary, "modeProfit", []);

  return (
    <div className="analytics-block">
      <section className="panel insights-panel">
        <div className="panel-header">
          <h2>How P&amp;L works</h2>
        </div>
        <div className="insights-grid">
          <div className="insight-card">
            <div className="summary-label">Formula</div>
            <code className="insight-code">
              {insights.formula || "platformProfit = totalRealIncome − winnerPayout"}
            </code>
            <p className="insight-note">
              Real income = human bets only. Winner payout = 0 if bot/house wins.
            </p>
          </div>
          <div className="insight-card">
            <div className="summary-label">Rake</div>
            <p className="insight-note">
              {insights.rakeNote ||
                `Fee ₹${fee} × seats (bots count). Displayed rake sum: ${formatMoney(rake, currency)}.`}
            </p>
            <div className="insight-metrics">
              <span>
                Fee / seat: <strong>{formatMoney(fee, currency)}</strong>
              </span>
              <span>
                Total rake (info): <strong>{formatMoney(rake, currency)}</strong>
              </span>
            </div>
          </div>
          <div className="insight-card">
            <div className="summary-label">Bot-fill impact</div>
            <p className="insight-note">
              {insights.botFillNote ||
                "1 real + bots: pot includes synthetic bot seats → human win can create platform loss."}
            </p>
            <div className="insight-metrics">
              <span>
                With bots: <strong>{formatNumber(insights.withBots ?? 0)}</strong>
              </span>
              <span>
                All human: <strong>{formatNumber(insights.allHuman ?? 0)}</strong>
              </span>
            </div>
          </div>
          <div className="insight-card">
            <div className="summary-label">Snapshot</div>
            <div className="insight-metrics stacked">
              <span>
                Margin:{" "}
                <strong className={`pnl-${pnlTone(net)}`}>
                  {Number.isFinite(margin) ? `${margin.toFixed(1)}%` : "—"}
                </strong>
              </span>
              <span>
                Avg income / game:{" "}
                <strong>
                  {formatMoney(
                    insights.avgIncomePerGame ?? summary.avgIncomePerGame,
                    currency
                  )}
                </strong>
              </span>
              <span>
                Avg net / game:{" "}
                <strong className={`pnl-${pnlTone(net)}`}>
                  {formatPnL(
                    insights.avgNetPerGame ?? summary.avgNetPerGame,
                    currency
                  )}
                </strong>
              </span>
              <span>
                Winning / losing games:{" "}
                <strong>
                  {formatNumber(insights.winningGames ?? 0)} /{" "}
                  {formatNumber(insights.losingGames ?? 0)}
                </strong>
              </span>
            </div>
          </div>
        </div>
      </section>

      <section className="charts-grid">
        <PieChart title="Profit vs Loss" slices={profitLossPie} />
        <PieChart title="Income vs Payouts" slices={incomePayoutPie} />
        <PieChart title="Games by mode" slices={modeGamesPie} />
        <PieChart title="Match outcomes" slices={outcomePie} />
        <PieChart title="Human vs bot-filled" slices={fillPie} />
        <BarChart
          title="Net P&L by mode"
          bars={modeProfitBars}
          currency={currency}
        />
      </section>
    </div>
  );
}
