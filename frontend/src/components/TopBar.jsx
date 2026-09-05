import { Search, Bell, ChevronDown } from "lucide-react";

/**
 * App shell topbar - wraps every authenticated screen (handoff: "App Shell").
 * height 64px, sticky, 216px-wide brand block to align with the sidebar.
 */
export default function TopBar({ user }) {
  return (
    <div
      style={{
        height: 64,
        borderBottom: "2px solid var(--color-divider)",
        display: "flex",
        alignItems: "center",
        gap: 24,
        padding: "0 24px",
        position: "sticky",
        top: 0,
        background: "var(--color-bg)",
        zIndex: 6,
      }}
    >
      <div style={{ fontFamily: "var(--font-heading)", fontWeight: 800, fontSize: 18, width: 216 }}>
        <span style={{ color: "var(--color-accent)" }}>UNIHELP</span>
      </div>

      <div
        style={{
          flex: 1,
          maxWidth: 420,
          display: "flex",
          border: "1px solid var(--color-divider)",
          background: "var(--color-surface)",
        }}
      >
        <span style={{ display: "grid", placeItems: "center", padding: "0 10px" }}>
          <Search size={16} style={{ opacity: 0.6 }} />
        </span>
        <input className="input" placeholder="Search tickets and articles" style={{ border: 0 }} />
      </div>

      <div style={{ marginLeft: "auto", display: "flex", alignItems: "center", gap: 8 }}>
        <button className="btn btn-icon" style={{ position: "relative" }} aria-label="Notifications">
          <Bell size={18} />
          <span
            style={{
              position: "absolute",
              top: 7,
              right: 7,
              width: 7,
              height: 7,
              background: "var(--color-accent)",
            }}
          />
        </button>
        <button className="btn btn-secondary" style={{ gap: 10, paddingLeft: 8 }}>
          <span
            style={{
              width: 26,
              height: 26,
              background: "var(--color-text)",
              color: "var(--color-bg)",
              display: "grid",
              placeItems: "center",
              fontSize: 11,
            }}
          >
            {user.initials}
          </span>
          {user.name}
          <ChevronDown size={14} style={{ opacity: 0.6 }} />
        </button>
      </div>
    </div>
  );
}
