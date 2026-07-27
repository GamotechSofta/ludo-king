const NAV = [
  { id: "dashboard", label: "Dashboard" },
  { id: "platforms", label: "Platforms" },
  { id: "profit-loss", label: "Profit & Loss" },
  { id: "settings", label: "Settings" },
];

export default function Sidebar({
  activePage,
  onNavigate,
  admin,
  onLogout,
  open,
  onClose,
}) {
  return (
    <>
      {open && (
        <button
          type="button"
          className="sidebar-backdrop"
          aria-label="Close menu"
          onClick={onClose}
        />
      )}
      <aside className={`sidebar ${open ? "sidebar-open" : ""}`}>
        <div className="sidebar-brand">
          <span className="sidebar-mark">P</span>
          <div>
            <div className="sidebar-title">PotLudo</div>
            <div className="sidebar-sub">Admin</div>
          </div>
        </div>

        <nav className="sidebar-nav">
          {NAV.map((item) => (
            <button
              key={item.id}
              type="button"
              className={`nav-item ${activePage === item.id ? "active" : ""}`}
              onClick={() => {
                onNavigate(item.id);
                onClose?.();
              }}
            >
              {item.label}
            </button>
          ))}
        </nav>

        <div className="sidebar-footer">
          {admin && (
            <div className="sidebar-admin">
              <div className="sidebar-admin-name">
                {admin.name || admin.email || "Admin"}
              </div>
              {admin.email && admin.name && (
                <div className="sidebar-admin-email">{admin.email}</div>
              )}
            </div>
          )}
          <button type="button" className="btn btn-ghost" onClick={onLogout}>
            Log out
          </button>
        </div>
      </aside>
    </>
  );
}
