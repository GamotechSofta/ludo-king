import SummaryCards from "../components/SummaryCards";
import GamesTable from "../components/GamesTable";
import UsersTable from "../components/UsersTable";

function operatorsFrom(summary) {
  if (!summary) return [];
  if (Array.isArray(summary.operators)) return summary.operators;
  if (summary.byOperator && typeof summary.byOperator === "object") {
    return Object.entries(summary.byOperator).map(([id, row]) => ({
      operatorId: id,
      ...(typeof row === "object" ? row : {}),
    }));
  }
  return [];
}

export default function ProfitLossPage({
  summary,
  games,
  users,
  filters,
  section,
  loading,
  error,
  operatorOptions,
  onFiltersChange,
  onSectionChange,
  onPageChange,
  onSelectGame,
}) {
  const currency = summary?.currency || "INR";
  const ops = operatorOptions?.length
    ? operatorOptions
    : operatorsFrom(summary);

  const gamesPage = games?.page ?? games?.currentPage ?? 1;
  const usersPage = users?.page ?? users?.currentPage ?? 1;
  const gamesTotalPages = games?.totalPages ?? games?.pages;
  const usersTotalPages = users?.totalPages ?? users?.pages;

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <h1>Profit &amp; Loss</h1>
          <p className="page-lead">
            Slice finished matches by player count and operator.
          </p>
        </div>
      </header>

      <div className="filters-bar">
        <div className="segmented">
          {[
            { value: "", label: "All" },
            { value: "2", label: "2 Player" },
            { value: "4", label: "4 Player" },
          ].map((opt) => (
            <button
              key={opt.label}
              type="button"
              className={
                String(filters.players || "") === opt.value ? "active" : ""
              }
              onClick={() =>
                onFiltersChange({
                  ...filters,
                  players: opt.value || undefined,
                })
              }
            >
              {opt.label}
            </button>
          ))}
        </div>

        <label className="field field-inline">
          <span>Platform</span>
          <select
            value={filters.operatorId || ""}
            onChange={(e) =>
              onFiltersChange({
                ...filters,
                operatorId: e.target.value || undefined,
              })
            }
          >
            <option value="">All Platforms</option>
            {ops.map((op) => {
              const id = op.operatorId || op.id;
              return (
                <option key={id} value={id}>
                  {op.name || op.domain || op.operatorName || id}
                </option>
              );
            })}
          </select>
        </label>
      </div>

      <div className="segmented section-tabs">
        {[
          { id: "overview", label: "Overview" },
          { id: "games", label: "Games" },
          { id: "users", label: "Users" },
        ].map((tab) => (
          <button
            key={tab.id}
            type="button"
            className={section === tab.id ? "active" : ""}
            onClick={() => onSectionChange(tab.id)}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {error && <div className="banner-error">{error}</div>}
      {loading && <div className="cell-muted loading-line">Refreshing…</div>}

      {section === "overview" && (
        <>
          <SummaryCards summary={summary} />
          <section className="panel">
            <div className="panel-header">
              <h2>Recent games</h2>
            </div>
            <GamesTable
              data={games}
              currency={currency}
              onSelect={onSelectGame}
            />
          </section>
        </>
      )}

      {section === "games" && (
        <section className="panel">
          <div className="panel-header">
            <h2>Games</h2>
            <Pagination
              page={gamesPage}
              totalPages={gamesTotalPages}
              onChange={(page) => onPageChange("games", page)}
            />
          </div>
          <GamesTable
            data={games}
            currency={currency}
            onSelect={onSelectGame}
          />
        </section>
      )}

      {section === "users" && (
        <section className="panel">
          <div className="panel-header">
            <h2>Users</h2>
            <Pagination
              page={usersPage}
              totalPages={usersTotalPages}
              onChange={(page) => onPageChange("users", page)}
            />
          </div>
          <UsersTable data={users} currency={currency} />
        </section>
      )}
    </div>
  );
}

function Pagination({ page, totalPages, onChange }) {
  if (!totalPages || totalPages <= 1) return null;
  return (
    <div className="pagination">
      <button
        type="button"
        className="btn btn-sm"
        disabled={page <= 1}
        onClick={() => onChange(page - 1)}
      >
        Prev
      </button>
      <span className="cell-muted">
        {page} / {totalPages}
      </span>
      <button
        type="button"
        className="btn btn-sm"
        disabled={page >= totalPages}
        onClick={() => onChange(page + 1)}
      >
        Next
      </button>
    </div>
  );
}
