import { useEffect, useState } from "react";
import { fetchSettings, updateSettings } from "../api/client";
import { formatMoney } from "../utils/format";

export default function SettingsPage() {
  const [fee, setFee] = useState("");
  const [currency, setCurrency] = useState("INR");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError("");
      try {
        const data = await fetchSettings();
        if (cancelled) return;
        const value =
          data?.platformFeePerPlayer ??
          data?.settings?.platformFeePerPlayer ??
          "";
        setFee(value === "" || value == null ? "" : String(value));
        setCurrency(data?.currency || "INR");
      } catch (err) {
        if (!cancelled) setError(err.message || "Failed to load settings");
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  async function handleSave(e) {
    e.preventDefault();
    setSaving(true);
    setError("");
    setMessage("");
    const n = Number(fee);
    if (!Number.isFinite(n) || n < 0) {
      setError("Enter a valid non-negative fee.");
      setSaving(false);
      return;
    }
    try {
      await updateSettings({ platformFeePerPlayer: n });
      setMessage(
        "Saved. New matches will use this platform fee per player."
      );
    } catch (err) {
      setError(err.message || "Failed to save settings");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <h1>Settings</h1>
          <p className="page-lead">
            Platform fee charged per player when a match starts.
          </p>
        </div>
      </header>

      {loading ? (
        <div className="empty-state">Loading settings…</div>
      ) : (
        <form className="panel settings-form" onSubmit={handleSave}>
          <label className="field">
            <span>Platform fee per player</span>
            <input
              type="number"
              min="0"
              step="0.01"
              value={fee}
              onChange={(e) => setFee(e.target.value)}
              required
            />
            <span className="field-hint">
              Preview: {formatMoney(Number(fee) || 0, currency)} per seat
            </span>
          </label>

          {error && <div className="form-error">{error}</div>}
          {message && <div className="form-success">{message}</div>}

          <button
            type="submit"
            className="btn btn-primary"
            disabled={saving}
          >
            {saving ? "Saving…" : "Save settings"}
          </button>
        </form>
      )}
    </div>
  );
}
