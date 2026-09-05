import { LayoutDashboard, PlusCircle, Ticket, Bookmark, BookOpen, User } from "lucide-react";

const NAV_ITEMS = [
  { key: "dashboard", label: "Dashboard", Icon: LayoutDashboard },
  { key: "submit", label: "Submit ticket", Icon: PlusCircle },
  { key: "tickets", label: "My tickets", Icon: Ticket },
  { key: "bookmarks", label: "Bookmarks", Icon: Bookmark },
  { key: "kbStudent", label: "Knowledge base", Icon: BookOpen },
  { key: "profile", label: "Profile", Icon: User },
];

/**
 * App shell left sidebar (handoff: "App Shell"). `active` is the current
 * nav key - the Ticket Detail screen keeps "tickets" (My tickets) active
 * per the handoff note, since a single ticket is reached from that list.
 */
export default function Sidebar({ active, studentId, onNavigate }) {
  return (
    <div style={{ borderRight: "2px solid var(--color-divider)", padding: "20px 0" }}>
      <div
        style={{
          fontSize: 11,
          letterSpacing: "0.12em",
          textTransform: "uppercase",
          opacity: 0.45,
          padding: "0 20px 12px",
        }}
      >
        Student
      </div>

      {NAV_ITEMS.map(({ key, label, Icon }) => {
        const isActive = key === active;
        return (
          <a
            key={key}
            href="#"
            className={`nav-link${isActive ? " is-active" : ""}`}
            onClick={(e) => {
              e.preventDefault();
              onNavigate?.(key);
            }}
          >
            <Icon size={17} />
            {label}
          </a>
        );
      })}

      <hr className="hr" style={{ margin: "20px 20px" }} />
      <div style={{ padding: "0 20px", fontSize: 12, opacity: 0.5, lineHeight: 1.6 }}>
        Student ID
        <br />
        <span style={{ fontFamily: "var(--font-mono)", opacity: 0.9 }}>{studentId}</span>
      </div>
    </div>
  );
}
