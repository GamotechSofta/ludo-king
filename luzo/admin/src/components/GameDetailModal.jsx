import { formatDate, formatMoney, formatPnL, pnlTone } from "../utils/format";

function playersOf(game) {
  if (!game) return [];
  return game.players || game.playerResults || game.participants || [];
}

export default function GameDetailModal({ game, onClose, currency = "INR" }) {
  if (!game) return null;

  const cur = game.currency || currency;
  const players = playersOf(game);
  const realCount =
    game.realPlayers ??
    players.filter((p) => !p.isBot && p.type !== "bot").length;
  const botCount =
    game.botPlayers ??
    players.filter((p) => p.isBot || p.type === "bot").length;

  return (
    <div className="modal-backdrop" onClick={onClose} role="presentation">
      <div
        className="modal-panel"
        role="dialog"
        aria-modal="true"
        aria-labelledby="game-detail-title"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="modal-header">
          <div>
            <h2 id="game-detail-title">Game detail</h2>
            <p className="cell-muted">
              {game.roomCode || game.roomId || "—"} ·{" "}
              {formatDate(game.finishedAt || game.endedAt || game.createdAt)}
            </p>
          </div>
          <button type="button" className="btn btn-ghost" onClick={onClose}>
            Close
          </button>
        </div>

        <div className="detail-grid">
          <Detail label="Mode" value={game.mode || `${game.playerCount || game.players || "—"}P`} />
          <Detail
            label="Entry fee"
            value={formatMoney(game.entryFee ?? game.betAmount, cur)}
          />
          <Detail
            label="Display pot"
            value={formatMoney(game.displayPot ?? game.pot ?? game.potAmount, cur)}
          />
          <Detail
            label="Rake"
            value={formatMoney(game.rake ?? game.displayPotRake ?? game.realPotRake, cur)}
          />
          <Detail
            label="Real income"
            value={formatMoney(game.totalRealIncome ?? game.income, cur)}
          />
          <Detail
            label="Winner payout"
            value={formatMoney(game.winnerPayout ?? game.payout, cur)}
          />
          <Detail label="Real players" value={String(realCount)} />
          <Detail label="Bots" value={String(botCount)} />
          <Detail
            label="Platform profit"
            value={
              <span className={`pnl-${pnlTone(game.platformProfit ?? game.profit)}`}>
                {formatPnL(game.platformProfit ?? game.profit, cur)}
              </span>
            }
          />
          <Detail
            label="Loss"
            value={
              <span
                className={`pnl-${
                  Number(game.platformProfit ?? game.profit) < 0 ? "negative" : "neutral"
                }`}
              >
                {formatMoney(
                  Number(game.platformProfit ?? game.profit) < 0
                    ? Math.abs(Number(game.platformProfit ?? game.profit))
                    : 0,
                  cur
                )}
              </span>
            }
          />
          <Detail
            label="Operator"
            value={game.operatorName || game.operatorId || "—"}
          />
        </div>

        <h3 className="section-title">Players</h3>
        {players.length === 0 ? (
          <div className="empty-state">No player breakdown available.</div>
        ) : (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Player</th>
                  <th>Type</th>
                  <th>Bet</th>
                  <th>Payout</th>
                  <th>P&amp;L</th>
                  <th>Platform</th>
                </tr>
              </thead>
              <tbody>
                {players.map((p, i) => {
                  const pnl = p.pnl ?? p.profitLoss ?? (Number(p.payout || 0) - Number(p.bet || p.entryFee || 0));
                  return (
                    <tr key={p.userId || p.id || i}>
                      <td className="cell-strong">
                        {p.username || p.name || p.userId || `Seat ${i + 1}`}
                      </td>
                      <td>{p.isBot || p.type === "bot" ? "Bot" : "Real"}</td>
                      <td>{formatMoney(p.bet ?? p.entryFee ?? p.stake, cur)}</td>
                      <td>{formatMoney(p.payout ?? p.winnings, cur)}</td>
                      <td className={`pnl-${pnlTone(pnl)}`}>
                        {formatPnL(pnl, cur)}
                      </td>
                      <td>
                        {p.operatorName || p.platformName || p.operatorId || "—"}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}

function Detail({ label, value }) {
  return (
    <div className="detail-item">
      <div className="summary-label">{label}</div>
      <div className="detail-value">{value}</div>
    </div>
  );
}
