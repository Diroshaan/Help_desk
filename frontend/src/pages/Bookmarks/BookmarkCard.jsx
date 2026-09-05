import StatusBadge from "../../components/StatusBadge.jsx";
import CardKebabMenu from "./CardKebabMenu.jsx";

/**
 * One bookmark card, shared by the Tickets and Articles tabs (handoff:
 * "Bookmark card grid"). `item` is either a bookmarked ticket
 * ({id, title, status, folder}) or a saved article (status "ARTICLE").
 */
export default function BookmarkCard({ item }) {
  return (
    <div className="bookmark-card">
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
        <span style={{ fontFamily: "var(--font-mono)", fontSize: 12, opacity: 0.6 }}>{item.id}</span>
        <CardKebabMenu />
      </div>
      <div style={{ fontFamily: "var(--font-heading)", fontWeight: 800, fontSize: 15, lineHeight: 1.25 }}>
        {item.title}
      </div>
      <div style={{ display: "flex", gap: 8, marginTop: "auto", flexWrap: "wrap" }}>
        <StatusBadge status={item.status} />
        <span className="tag tag-neutral">{item.folder}</span>
      </div>
    </div>
  );
}
