import { formatDate, formatMoney, formatPnL, pnlTone } from "../utils/format";

function gamesList(data) {
  if (!data) return [];
  if (Array.isArray(data)) return data;
  return data.games || data.items || data.results || [];
}

export default function GamesTable({ data, onSelect, currency = "INR" }) {
  const rows = gamesList(data);

  if (!rows.length) {
    return <div className="empty-state">No finished games yet.</div>;
  }

  return (
    <div className="table-wrap">
      <table className="data-table">
        <thead>
          <tr>
            <th>Room / Round</th>
            <th>Mode</th>
            <th>Finished</th>
            <th>Entry</th>
            <th>Real income</th>
            <th>Winner payout</th>
            <th>Profit / Loss</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {rows.map((game) => {
            const id = game.id || game.roomId || game.roundId;
            const profit = game.platformProfit ?? game.profit ?? 0;
            return (
              <tr key={id || JSON.stringify(game)}>
                <td>
                  <div className="cell-strong">
                    {game.roomCode || game.roomId || "—"}
                  </div>
                  <div className="cell-muted">{game.roundId || id || ""}</div>
                </td>
                <td>
                  {game.mode ||
                    (game.playerCount
                      ? `${game.playerCount}P`
                      : game.players
                        ? `${game.players}P`
                        : "—")}
                </td>
                <td>{formatDate(game.finishedAt || game.endedAt || game.createdAt)}</td>
                <td>
                  {formatMoney(
                    game.entryFee ?? game.betAmount ?? game.stake,
                    game.currency || currency
                  )}
                </td>
                <td>
                  {formatMoney(
                    game.totalRealIncome ?? game.income ?? game.pot ?? game.potAmount,
                    game.currency || currency
                  )}
                </td>
                <td>
                  {formatMoney(
                    game.winnerPayout ?? game.payout ?? 0,
                    game.currency || currency
                  )}
                </td>
                <td className={`pnl-${pnlTone(profit)}`}>
                  {formatPnL(profit, game.currency || currency)}
                </td>
                <td>
                  <button
                    type="button"
                    className="btn btn-sm"
                    onClick={() => onSelect?.(game)}
                  >
                    View
                  </button>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
