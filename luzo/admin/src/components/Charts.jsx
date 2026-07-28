import { formatMoney, formatNumber } from "../utils/format";

/** Pure SVG pie — no chart library. */
export function PieChart({ slices = [], size = 180, title }) {
  const data = (slices || [])
    .map((s) => ({
      label: s.label || s.name || "—",
      value: Math.max(0, Number(s.value) || 0),
      color: s.color || "#94a3b8",
    }))
    .filter((s) => s.value > 0);

  const total = data.reduce((sum, s) => sum + s.value, 0);
  const r = size / 2;
  const ir = r * 0.58;
  const cx = r;
  const cy = r;

  if (total <= 0) {
    return (
      <div className="chart-empty">
        <div className="chart-title">{title}</div>
        <div className="cell-muted">No data yet</div>
      </div>
    );
  }

  let angle = -Math.PI / 2;
  const arcs = data.map((s) => {
    const sweep = (s.value / total) * Math.PI * 2;
    const start = angle;
    const end = angle + sweep;
    angle = end;
    const large = sweep > Math.PI ? 1 : 0;
    const x1 = cx + r * Math.cos(start);
    const y1 = cy + r * Math.sin(start);
    const x2 = cx + r * Math.cos(end);
    const y2 = cy + r * Math.sin(end);
    const ix1 = cx + ir * Math.cos(end);
    const iy1 = cy + ir * Math.sin(end);
    const ix2 = cx + ir * Math.cos(start);
    const iy2 = cy + ir * Math.sin(start);
    const d = [
      `M ${x1} ${y1}`,
      `A ${r} ${r} 0 ${large} 1 ${x2} ${y2}`,
      `L ${ix1} ${iy1}`,
      `A ${ir} ${ir} 0 ${large} 0 ${ix2} ${iy2}`,
      "Z",
    ].join(" ");
    return { ...s, d, pct: (s.value / total) * 100 };
  });

  return (
    <div className="chart-card">
      <div className="chart-title">{title}</div>
      <div className="chart-body">
        <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} aria-hidden>
          {arcs.map((a) => (
            <path key={a.label} d={a.d} fill={a.color}>
              <title>
                {a.label}: {a.value} ({a.pct.toFixed(1)}%)
              </title>
            </path>
          ))}
          <circle cx={cx} cy={cy} r={ir * 0.92} fill="var(--surface)" />
          <text
            x={cx}
            y={cy - 4}
            textAnchor="middle"
            className="chart-center-value"
            fill="var(--ink)"
            fontSize="13"
            fontWeight="700"
          >
            {formatNumber(Math.round(total))}
          </text>
          <text
            x={cx}
            y={cy + 12}
            textAnchor="middle"
            fill="var(--muted)"
            fontSize="10"
          >
            total
          </text>
        </svg>
        <ul className="chart-legend">
          {arcs.map((a) => (
            <li key={a.label}>
              <span className="legend-swatch" style={{ background: a.color }} />
              <span className="legend-label">{a.label}</span>
              <span className="legend-value">
                {a.pct.toFixed(0)}% · {formatNumber(a.value)}
              </span>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}

/** Horizontal bar chart for signed money values. */
export function BarChart({ bars = [], title, currency = "INR" }) {
  const data = (bars || []).map((b) => ({
    label: b.label || b.name || "—",
    value: Number(b.value) || 0,
    color: b.color || (Number(b.value) >= 0 ? "#047857" : "#b91c1c"),
  }));
  const maxAbs = Math.max(1, ...data.map((b) => Math.abs(b.value)));

  if (!data.length) {
    return (
      <div className="chart-empty">
        <div className="chart-title">{title}</div>
        <div className="cell-muted">No data yet</div>
      </div>
    );
  }

  return (
    <div className="chart-card">
      <div className="chart-title">{title}</div>
      <div className="bar-list">
        {data.map((b) => {
          const width = `${Math.max(4, (Math.abs(b.value) / maxAbs) * 100)}%`;
          return (
            <div key={b.label} className="bar-row">
              <div className="bar-label">{b.label}</div>
              <div className="bar-track">
                <div
                  className="bar-fill"
                  style={{ width, background: b.color }}
                  title={formatMoney(b.value, currency)}
                />
              </div>
              <div className="bar-value">{formatMoney(b.value, currency)}</div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
