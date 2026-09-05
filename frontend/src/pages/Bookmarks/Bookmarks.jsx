import { useMemo, useState } from "react";
import { mockTickets } from "../../data/mockTickets.js";
import { mockArticles } from "../../data/mockArticles.js";
import BookmarkCard from "./BookmarkCard.jsx";
import "./Bookmarks.css";

// Folder definitions (handoff: name / count / dot color). Counts are the
// mock's static numbers - not derived from the (much smaller) sample data
// sets below, since the real product would track this server-side.
const FOLDER_DEFS = [
  { name: "All bookmarks", count: 12, dot: "var(--color-text)" },
  { name: "IT issues", count: 7, dot: "var(--color-accent)" },
  { name: "Financial aid", count: 3, dot: "var(--color-neutral-600)" },
];

const TABS = ["Tickets", "Articles"];

function folderForTicket(ticket) {
  return ticket.department === "IT Services" ? "IT issues" : "All bookmarks";
}

export default function Bookmarks() {
  const [activeFolder, setActiveFolder] = useState("All bookmarks");
  const [activeTab, setActiveTab] = useState("Tickets");
  const [sortOrder, setSortOrder] = useState("Recently added");

  const bookmarkedTickets = useMemo(
    () =>
      mockTickets.slice(0, 6).map((t) => ({
        id: t.id,
        title: t.subject,
        status: t.status,
        folder: folderForTicket(t),
        department: t.department,
      })),
    []
  );

  const sourceCards = activeTab === "Tickets" ? bookmarkedTickets : mockArticles;

  const cards = useMemo(() => {
    const filtered =
      activeFolder === "All bookmarks" ? sourceCards : sourceCards.filter((c) => c.folder === activeFolder);
    const sorted = [...filtered];
    if (sortOrder === "Status") {
      sorted.sort((a, b) => a.status.localeCompare(b.status));
    } else if (sortOrder === "Department") {
      sorted.sort((a, b) => (a.department ?? "").localeCompare(b.department ?? ""));
    }
    return sorted;
  }, [sourceCards, activeFolder, sortOrder]);

  return (
    <div style={{ display: "grid", gridTemplateColumns: "240px 1fr", minHeight: "calc(100vh - 64px)" }}>
      <div style={{ borderRight: "2px solid var(--color-divider)", padding: "26px 20px" }}>
        <h6 style={{ marginBottom: 16 }}>Folders</h6>
        <div style={{ display: "flex", flexDirection: "column", gap: 0 }}>
          {FOLDER_DEFS.map((f) => {
            const isActive = f.name === activeFolder;
            return (
              <div
                key={f.name}
                className={`folder-row${isActive ? " is-active" : ""}`}
                style={{ borderLeftColor: isActive ? f.dot : "transparent" }}
                onClick={() => setActiveFolder(f.name)}
              >
                <span style={{ width: 9, height: 9, background: f.dot, display: "inline-block", flexShrink: 0 }} />
                <span style={{ flex: 1 }}>{f.name}</span>
                <span style={{ fontSize: 12, opacity: 0.5 }}>{f.count}</span>
              </div>
            );
          })}
        </div>
        <button className="btn btn-secondary btn-block" style={{ borderStyle: "dashed", marginTop: 18, paddingLeft: 12 }}>
          + New folder
        </button>
        <p style={{ fontSize: 11, opacity: 0.5, marginTop: 16, lineHeight: 1.5 }}>
          Drag a card onto a folder to move it. A &quot;Move to folder&quot; menu is the keyboard fallback.
        </p>
      </div>

      <div style={{ padding: "0 32px" }}>
        <div
          style={{
            padding: "32px 0 20px",
            display: "flex",
            alignItems: "flex-end",
            justifyContent: "space-between",
          }}
        >
          <div style={{ borderLeft: "3px solid var(--color-accent)", paddingLeft: 18 }}>
            <div
              style={{
                fontSize: 11,
                letterSpacing: "0.14em",
                textTransform: "uppercase",
                color: "var(--color-accent)",
                marginBottom: 8,
              }}
            >
              Bookmarks
            </div>
            <h2 style={{ margin: 0 }}>{activeFolder}</h2>
          </div>
          <select className="input" style={{ width: "auto" }} value={sortOrder} onChange={(e) => setSortOrder(e.target.value)}>
            <option>Recently added</option>
            <option>Status</option>
            <option>Department</option>
          </select>
        </div>

        <div style={{ display: "flex", gap: 0, borderBottom: "2px solid var(--color-divider)", marginBottom: 22, paddingTop: 20 }}>
          {TABS.map((tab) => (
            <button
              key={tab}
              type="button"
              className={`bm-tab${activeTab === tab ? " is-active" : ""}`}
              onClick={() => setActiveTab(tab)}
            >
              {tab}
            </button>
          ))}
        </div>

        <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 16, paddingBottom: 32 }}>
          {cards.map((item) => (
            <BookmarkCard key={item.id} item={item} />
          ))}
          {cards.length === 0 && (
            <div style={{ gridColumn: "1 / -1", textAlign: "center", opacity: 0.6, padding: "32px 0" }}>
              No bookmarks in this folder yet.
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
