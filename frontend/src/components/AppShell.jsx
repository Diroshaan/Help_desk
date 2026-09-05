import TopBar from "./TopBar.jsx";
import Sidebar from "./Sidebar.jsx";

/**
 * Authenticated app shell (handoff: "App Shell (wraps every authenticated
 * screen, including this one)"). `activeNav` picks the highlighted sidebar
 * item; `children` fills the remaining column to the right of the sidebar.
 */
export default function AppShell({ activeNav, user, onNavigate, children }) {
  return (
    <div>
      <TopBar user={user} />
      <div style={{ display: "grid", gridTemplateColumns: "240px 1fr", minHeight: "calc(100vh - 64px)" }}>
        <Sidebar active={activeNav} studentId={user.studentId} onNavigate={onNavigate} />
        <div style={{ padding: "0 0 80px", position: "relative" }}>{children}</div>
      </div>
    </div>
  );
}
