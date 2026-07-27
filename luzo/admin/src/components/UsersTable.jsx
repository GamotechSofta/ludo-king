import { formatMoney, formatNumber, formatPnL, pnlTone } from "../utils/format";

function usersList(data) {
  if (!data) return [];
  if (Array.isArray(data)) return data;
  return data.users || data.items || data.results || [];
}

export default function UsersTable({ data, currency = "INR" }) {
  const rows = usersList(data);

  if (!rows.length) {
    return <div className="empty-state">No user P&amp;L rows yet.</div>;
  }

  return (
    <div className="table-wrap">
      <table className="data-table">
        <thead>
          <tr>
            <th>User</th>
            <th>Platform</th>
            <th>Games</th>
            <th>Wagered</th>
            <th>Payout</th>
            <th>P&amp;L</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((user) => {
            const id = user.userId || user.id || user.username;
            const pnl = user.pnl ?? user.profitLoss ?? user.net ?? 0;
            return (
              <tr key={id || JSON.stringify(user)}>
                <td>
                  <div className="cell-strong">
                    {user.username || user.name || user.displayName || id || "—"}
                  </div>
                  <div className="cell-muted">{user.userId || user.id || ""}</div>
                </td>
                <td>
                  {user.operatorName ||
                    user.platformName ||
                    user.operatorId ||
                    "—"}
                </td>
                <td>{formatNumber(user.gamesPlayed ?? user.games ?? 0)}</td>
                <td>
                  {formatMoney(
                    user.wagered ?? user.totalBet ?? user.stakes,
                    user.currency || currency
                  )}
                </td>
                <td>
                  {formatMoney(
                    user.payout ?? user.totalPayout ?? user.winnings,
                    user.currency || currency
                  )}
                </td>
                <td className={`pnl-${pnlTone(pnl)}`}>
                  {formatPnL(pnl, user.currency || currency)}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
