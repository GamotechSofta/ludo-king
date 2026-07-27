import { useCallback, useEffect, useMemo, useState } from "react";
import {
  fetchGames,
  fetchMe,
  fetchSummary,
  fetchUsers,
  login as apiLogin,
  logout as apiLogout,
  setUnauthorizedHandler,
} from "./api/client";
import { clearToken, getToken, setToken } from "./api/authStorage";
import Sidebar from "./components/Sidebar";
import GameDetailModal from "./components/GameDetailModal";
import LoginPage from "./pages/LoginPage";
import DashboardPage from "./pages/DashboardPage";
import PlatformsPage from "./pages/PlatformsPage";
import ProfitLossPage from "./pages/ProfitLossPage";
import SettingsPage from "./pages/SettingsPage";

const DEFAULT_LIMIT = 20;

export default function App() {
  const [bootstrapping, setBootstrapping] = useState(true);
  const [admin, setAdmin] = useState(null);
  const [loginError, setLoginError] = useState("");
  const [loginLoading, setLoginLoading] = useState(false);

  const [activePage, setActivePage] = useState("dashboard");
  const [sidebarOpen, setSidebarOpen] = useState(false);

  const [dashboardSummary, setDashboardSummary] = useState(null);
  const [dashboardGames, setDashboardGames] = useState(null);

  const [plSummary, setPlSummary] = useState(null);
  const [plGames, setPlGames] = useState(null);
  const [plUsers, setPlUsers] = useState(null);
  const [plFilters, setPlFilters] = useState({});
  const [plSection, setPlSection] = useState("overview");
  const [plGamesPage, setPlGamesPage] = useState(1);
  const [plUsersPage, setPlUsersPage] = useState(1);

  const [dataLoading, setDataLoading] = useState(false);
  const [dataError, setDataError] = useState("");
  const [selectedGame, setSelectedGame] = useState(null);

  const clearSession = useCallback(() => {
    clearToken();
    setAdmin(null);
    setDashboardSummary(null);
    setDashboardGames(null);
    setPlSummary(null);
    setPlGames(null);
    setPlUsers(null);
    setSelectedGame(null);
    setActivePage("dashboard");
    setDataError("");
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(() => {
      clearSession();
    });
    return () => setUnauthorizedHandler(null);
  }, [clearSession]);

  const loadDashboard = useCallback(async () => {
    const [summary, games] = await Promise.all([
      fetchSummary(),
      fetchGames({ page: 1, limit: DEFAULT_LIMIT }),
    ]);
    setDashboardSummary(summary);
    setDashboardGames(games);
  }, []);

  const loadProfitLoss = useCallback(async (filters, gamesPage = 1, usersPage = 1) => {
    const query = {
      ...(filters.players ? { players: filters.players } : {}),
      ...(filters.operatorId ? { operatorId: filters.operatorId } : {}),
    };
    const [summary, games, users] = await Promise.all([
      fetchSummary(query),
      fetchGames({ page: gamesPage, limit: DEFAULT_LIMIT, ...query }),
      fetchUsers({ page: usersPage, limit: DEFAULT_LIMIT, ...query }),
    ]);
    setPlSummary(summary);
    setPlGames(games);
    setPlUsers(users);
  }, []);

  const refreshAll = useCallback(
    async (filters = plFilters, gamesPage = plGamesPage, usersPage = plUsersPage) => {
      setDataLoading(true);
      setDataError("");
      try {
        await Promise.all([
          loadDashboard(),
          loadProfitLoss(filters, gamesPage, usersPage),
        ]);
      } catch (err) {
        if (err.status !== 401) {
          setDataError(err.message || "Failed to load admin data");
        }
      } finally {
        setDataLoading(false);
      }
    },
    [loadDashboard, loadProfitLoss, plFilters, plGamesPage, plUsersPage]
  );

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const token = getToken();
      if (!token) {
        if (!cancelled) setBootstrapping(false);
        return;
      }
      try {
        const me = await fetchMe();
        if (cancelled) return;
        setAdmin(me?.admin || me);
        await refreshAll({}, 1, 1);
      } catch {
        if (!cancelled) clearSession();
      } finally {
        if (!cancelled) setBootstrapping(false);
      }
    })();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- boot once
  }, []);

  async function handleLogin(email, password) {
    setLoginLoading(true);
    setLoginError("");
    try {
      const res = await apiLogin(email, password);
      const token = res?.token || res?.accessToken;
      if (!token) throw new Error("Login response missing token");
      setToken(token);
      setAdmin(res.admin || res.user || { email });
      setBootstrapping(false);
      await refreshAll({}, 1, 1);
    } catch (err) {
      clearToken();
      setLoginError(err.message || "Login failed");
    } finally {
      setLoginLoading(false);
    }
  }

  async function handleLogout() {
    try {
      await apiLogout();
    } catch {
      /* ignore network errors on logout */
    }
    clearSession();
  }

  async function handlePlFiltersChange(next) {
    setPlFilters(next);
    setPlGamesPage(1);
    setPlUsersPage(1);
    setDataLoading(true);
    setDataError("");
    try {
      await loadProfitLoss(next, 1, 1);
    } catch (err) {
      if (err.status !== 401) setDataError(err.message || "Failed to reload");
    } finally {
      setDataLoading(false);
    }
  }

  async function handlePageChange(kind, page) {
    setDataLoading(true);
    setDataError("");
    try {
      if (kind === "games") {
        setPlGamesPage(page);
        const query = {
          ...(plFilters.players ? { players: plFilters.players } : {}),
          ...(plFilters.operatorId ? { operatorId: plFilters.operatorId } : {}),
        };
        const games = await fetchGames({
          page,
          limit: DEFAULT_LIMIT,
          ...query,
        });
        setPlGames(games);
      } else {
        setPlUsersPage(page);
        const query = {
          ...(plFilters.players ? { players: plFilters.players } : {}),
          ...(plFilters.operatorId ? { operatorId: plFilters.operatorId } : {}),
        };
        const users = await fetchUsers({
          page,
          limit: DEFAULT_LIMIT,
          ...query,
        });
        setPlUsers(users);
      }
    } catch (err) {
      if (err.status !== 401) setDataError(err.message || "Failed to page data");
    } finally {
      setDataLoading(false);
    }
  }

  const operatorOptions = useMemo(() => {
    const s = dashboardSummary || plSummary;
    if (!s) return [];
    if (Array.isArray(s.operators)) return s.operators;
    if (s.byOperator && typeof s.byOperator === "object") {
      return Object.entries(s.byOperator).map(([id, row]) => ({
        operatorId: id,
        ...(typeof row === "object" ? row : {}),
      }));
    }
    return [];
  }, [dashboardSummary, plSummary]);

  if (bootstrapping) {
    return (
      <div className="boot-screen">
        <div className="boot-card">Checking admin session…</div>
      </div>
    );
  }

  if (!admin) {
    return (
      <LoginPage
        onLogin={handleLogin}
        error={loginError}
        loading={loginLoading}
      />
    );
  }

  return (
    <div className="app-shell">
      <Sidebar
        activePage={activePage}
        onNavigate={setActivePage}
        admin={admin}
        onLogout={handleLogout}
        open={sidebarOpen}
        onClose={() => setSidebarOpen(false)}
      />

      <div className="main-column">
        <header className="topbar">
          <button
            type="button"
            className="btn btn-ghost menu-btn"
            onClick={() => setSidebarOpen(true)}
            aria-label="Open menu"
          >
            Menu
          </button>
          <div className="topbar-title">PotLudo Admin</div>
        </header>

        <main className="main-content">
          {activePage === "dashboard" && (
            <DashboardPage
              summary={dashboardSummary}
              games={dashboardGames}
              loading={dataLoading}
              error={dataError}
              onSelectGame={setSelectedGame}
              onOpenPlatforms={() => setActivePage("platforms")}
              onOpenProfitLoss={(opts = {}) => {
                const next = {
                  ...plFilters,
                  ...(opts.operatorId
                    ? { operatorId: opts.operatorId }
                    : {}),
                };
                setPlFilters(next);
                setPlSection("overview");
                setActivePage("profit-loss");
                void handlePlFiltersChange(next);
              }}
            />
          )}

          {activePage === "platforms" && (
            <PlatformsPage
              summary={dashboardSummary}
              loading={dataLoading}
              error={dataError}
              onSelectPlatform={(operatorId) => {
                const next = { ...plFilters, operatorId };
                setPlFilters(next);
                setPlSection("overview");
                setActivePage("profit-loss");
                void handlePlFiltersChange(next);
              }}
            />
          )}

          {activePage === "profit-loss" && (
            <ProfitLossPage
              summary={plSummary}
              games={plGames}
              users={plUsers}
              filters={plFilters}
              section={plSection}
              loading={dataLoading}
              error={dataError}
              operatorOptions={operatorOptions}
              onFiltersChange={handlePlFiltersChange}
              onSectionChange={setPlSection}
              onPageChange={handlePageChange}
              onSelectGame={setSelectedGame}
            />
          )}

          {activePage === "settings" && <SettingsPage />}
        </main>
      </div>

      <GameDetailModal
        game={selectedGame}
        onClose={() => setSelectedGame(null)}
        currency={
          plSummary?.currency || dashboardSummary?.currency || "INR"
        }
      />
    </div>
  );
}
