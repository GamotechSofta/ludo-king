import { formatMoney, formatNumber } from "../utils/format";

function pick(summary, keys, fallback = 0) {
  if (!summary) return fallback;
  for (const key of keys) {
    if (summary[key] != null) return summary[key];
  }
  return fallback;
}

export default function SummaryCards({ summary }) {
  const currency = pick(summary, ["currency"], "INR");

  const cards = [
    {
      label: "Games",
      value: formatNumber(
        pick(summary, ["totalGames", "gamesCount", "games"])
      ),
    },
    {
      label: "Income",
      value: formatMoney(
        pick(summary, ["totalIncome", "income", "grossIncome"]),
        currency
      ),
    },
    {
      label: "Platform profit",
      value: formatMoney(
        pick(summary, [
          "platformProfit",
          "totalPlatformProfit",
          "profit",
        ]),
        currency
      ),
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
        pick(summary, ["totalOperators", "operatorsCount", "operators"], null) ??
          (Array.isArray(summary?.operators)
            ? summary.operators.length
            : summary?.byOperator
              ? Object.keys(summary.byOperator).length
              : 0)
      ),
    },
    {
      label: "Payouts",
      value: formatMoney(
        pick(summary, ["totalPayouts", "payouts", "winnerPayouts"]),
        currency
      ),
    },
  ];

  return (
    <div className="summary-grid">
      {cards.map((card) => (
        <div key={card.label} className="summary-card">
          <div className="summary-label">{card.label}</div>
          <div className="summary-value">{card.value}</div>
        </div>
      ))}
    </div>
  );
}
