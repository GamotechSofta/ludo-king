import { formatMoney, formatNumber, formatPnL, pnlTone } from "../utils/format";

function pick(summary, keys, fallback = 0) {
  if (!summary) return fallback;
  for (const key of keys) {
    if (summary[key] != null) return summary[key];
  }
  return fallback;
}

export default function SummaryCards({ summary }) {
  const currency = pick(summary, ["currency"], "INR");
  const income = Number(
    pick(summary, ["totalRealIncome", "totalIncome", "income", "grossIncome"])
  );
  const net = Number(
    pick(summary, ["platformProfit", "totalPlatformProfit"])
  );
  const profitOnly = Number(
    pick(summary, ["totalProfit", "profit"], Math.max(0, net))
  );
  // Prefer explicit totalLoss from API; else derive from negative net
  const lossFromApi = pick(summary, ["totalLoss", "loss"], null);
  const lossOnly =
    lossFromApi != null
      ? Math.abs(Number(lossFromApi))
      : net < 0
        ? Math.abs(net)
        : 0;
  const payouts = Number(
    pick(summary, ["winnerPayouts", "totalPayouts", "payouts"])
  );

  const cards = [
    {
      label: "Games",
      value: formatNumber(
        pick(summary, ["totalGames", "gamesCount", "games"])
      ),
    },
    {
      label: "Income",
      value: formatMoney(income, currency),
      hint: "Real player bets",
    },
    {
      label: "Profit",
      value: formatMoney(profitOnly, currency),
      tone: profitOnly > 0 ? "positive" : "neutral",
      hint: "Sum of winning games",
    },
    {
      label: "Loss",
      value: formatMoney(lossOnly, currency),
      tone: lossOnly > 0 ? "negative" : "neutral",
      hint: "Sum of losing games",
    },
    {
      label: "Net P&L",
      value: formatPnL(net, currency),
      tone: pnlTone(net),
      hint: "Income − winner payouts",
    },
    {
      label: "Margin",
      value: `${Number(pick(summary, ["marginPct"], 0)).toFixed(1)}%`,
      tone: pnlTone(net),
      hint: "Net ÷ income",
    },
    {
      label: "Rake (info)",
      value: formatMoney(pick(summary, ["totalRake", "rake"]), currency),
      hint: "Fee × seats across games",
    },
    {
      label: "Winner payouts",
      value: formatMoney(payouts, currency),
    },
    {
      label: "Users",
      value: formatNumber(
        pick(summary, ["totalUsers", "usersCount", "users"])
      ),
    },
    {
      label: "Operators",
      value: formatNumber(
        (() => {
          const n = pick(summary, ["totalOperators", "operatorsCount"], null);
          if (n != null && typeof n !== "object") return n;
          if (Array.isArray(summary?.operators)) return summary.operators.length;
          if (summary?.byOperator && typeof summary.byOperator === "object") {
            return Object.keys(summary.byOperator).length;
          }
          return 0;
        })()
      ),
    },
  ];

  return (
    <div className="summary-grid">
      {cards.map((card) => (
        <div key={card.label} className="summary-card">
          <div className="summary-label">{card.label}</div>
          <div className={`summary-value pnl-${card.tone || "neutral"}`}>
            {card.value}
          </div>
          {card.hint ? <div className="summary-hint">{card.hint}</div> : null}
        </div>
      ))}
    </div>
  );
}
