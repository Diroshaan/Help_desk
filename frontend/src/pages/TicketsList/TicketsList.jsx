import { useMemo, useState } from "react";
import { Plus, Search } from "lucide-react";
import StatusBadge from "../../components/StatusBadge.jsx";
import DeleteTicketModal from "../TicketDetail/DeleteTicketModal.jsx";
import KebabMenu from "./KebabMenu.jsx";
import { mockTickets } from "../../data/mockTickets.js";

const STATUS_TABS = [
  { key: "All", label: "All" },
  { key: "OPEN", label: "OPEN" },
  { key: "IN_PROGRESS", label: "IN PROGRESS" },
  { key: "RESOLVED", label: "RESOLVED" },
];

const DEPARTMENTS = ["All departments", "IT Services", "Registration", "Financial Aid", "Library", "Hostel", "Examinations"];

export default function TicketsList({ tickets = mockTickets, onOpenTicket }) {
  const [statusFilter, setStatusFilter] = useState("All");
  const [search, setSearch] = useState("");
  const [department, setDepartment] = useState("All departments");
  const [dateRange, setDateRange] = useState("Last 30 days");
  const [pageSize, setPageSize] = useState(10);
  const [page, setPage] = useState(1);
  const [selectedRows, setSelectedRows] = useState(new Set());
  const [deleteTarget, setDeleteTarget] = useState(null);

  const filtered = useMemo(() => {
    return tickets.filter((t) => {
      if (statusFilter !== "All" && t.status !== statusFilter) return false;
      if (department !== "All departments" && t.department !== department) return false;
      const q = search.trim().toLowerCase();
      if (q && !t.id.toLowerCase().includes(q) && !t.subject.toLowerCase().includes(q)) return false;
      return true;
    });
  }, [tickets, statusFilter, department, search]);

  const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize));
  const currentPage = Math.min(page, totalPages);
  const pageRows = filtered.slice((currentPage - 1) * pageSize, currentPage * pageSize);

  const allOnPageSelected = pageRows.length > 0 && pageRows.every((t) => selectedRows.has(t.id));

  function toggleRow(id) {
    setSelectedRows((prev) => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  }

  function toggleAllOnPage() {
    setSelectedRows((prev) => {
      const next = new Set(prev);
      if (allOnPageSelected) {
        pageRows.forEach((t) => next.delete(t.id));
      } else {
        pageRows.forEach((t) => next.add(t.id));
      }
      return next;
    });
  }

  return (
    <div>
      <div style={{ padding: "32px 32px 20px", display: "flex", alignItems: "flex-end", justifyContent: "space-between" }}>
        <div>
          <div
            style={{
              fontSize: 11,
              letterSpacing: "0.14em",
              textTransform: "uppercase",
              color: "var(--color-accent)",
              marginBottom: 8,
            }}
          >
            My tickets
          </div>
          <h2 style={{ margin: 0 }}>You have {tickets.length} tickets</h2>
        </div>
        <button className="btn btn-primary">
          <Plus size={16} />
          New ticket
        </button>
      </div>

      <div style={{ padding: "0 32px 20px", display: "flex", gap: 12, flexWrap: "wrap", alignItems: "center" }}>
        <div style={{ display: "flex", border: "1px solid var(--color-divider)", background: "var(--color-surface)", minWidth: 260 }}>
          <span style={{ display: "grid", placeItems: "center", padding: "0 10px" }}>
            <Search size={15} style={{ opacity: 0.6 }} />
          </span>
          <input
            className="input"
            style={{ border: 0 }}
            placeholder="Search by ticket ID or subject"
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(1);
            }}
          />
        </div>

        <select
          className="input"
          style={{ width: "auto" }}
          value={department}
          onChange={(e) => {
            setDepartment(e.target.value);
            setPage(1);
          }}
        >
          {DEPARTMENTS.map((d) => (
            <option key={d}>{d}</option>
          ))}
        </select>

        <select className="input" style={{ width: "auto" }} value={dateRange} onChange={(e) => setDateRange(e.target.value)}>
          <option>Last 30 days</option>
          <option>This semester</option>
          <option>Custom range</option>
        </select>

        <div className="seg" style={{ marginLeft: "auto" }}>
          {STATUS_TABS.map((tab) => {
            const active = statusFilter === tab.key;
            return (
              <label key={tab.key} className={`seg-opt${active ? " is-active" : ""}`}>
                <input
                  type="radio"
                  name="status-filter"
                  checked={active}
                  onChange={() => {
                    setStatusFilter(tab.key);
                    setPage(1);
                  }}
                />
                {tab.label}
              </label>
            );
          })}
        </div>
      </div>

      <div style={{ padding: "0 32px" }}>
        <table className="table">
          <thead>
            <tr>
              <th style={{ width: 34 }}>
                <input type="checkbox" checked={allOnPageSelected} onChange={toggleAllOnPage} />
              </th>
              <th>Ticket ID</th>
              <th>Subject</th>
              <th>Department</th>
              <th>Status</th>
              <th>Created</th>
              <th>Updated</th>
              <th style={{ width: 40 }} />
            </tr>
          </thead>
          <tbody>
            {pageRows.map((t) => (
              <tr key={t.id} style={{ cursor: "pointer" }}>
                <td onClick={(e) => e.stopPropagation()}>
                  <input type="checkbox" checked={selectedRows.has(t.id)} onChange={() => toggleRow(t.id)} />
                </td>
                <td
                  onClick={() => onOpenTicket?.(t.id)}
                  style={{ fontFamily: "var(--font-mono)", fontSize: 13, whiteSpace: "nowrap" }}
                >
                  <span
                    style={{
                      display: "inline-block",
                      width: 7,
                      height: 7,
                      background: t.unread ? "var(--color-accent)" : "transparent",
                      marginRight: 7,
                    }}
                  />
                  {t.id}
                </td>
                <td onClick={() => onOpenTicket?.(t.id)} style={{ fontWeight: t.unread ? 600 : 400 }}>
                  {t.subject}
                </td>
                <td style={{ fontSize: 13, opacity: 0.7 }}>{t.department}</td>
                <td>
                  <StatusBadge status={t.status} />
                </td>
                <td style={{ fontSize: 12, opacity: 0.55, whiteSpace: "nowrap" }}>{t.createdAt}</td>
                <td style={{ fontSize: 12, opacity: 0.55, whiteSpace: "nowrap" }}>{t.updatedAt}</td>
                <td>
                  <KebabMenu
                    ticket={t}
                    onEdit={() => onOpenTicket?.(t.id)}
                    onDelete={() => setDeleteTarget(t)}
                  />
                </td>
              </tr>
            ))}
            {pageRows.length === 0 && (
              <tr>
                <td colSpan={8} style={{ textAlign: "center", opacity: 0.6, padding: "32px 0" }}>
                  No tickets match these filters.
                </td>
              </tr>
            )}
          </tbody>
        </table>

        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "20px 0", fontSize: 13 }}>
          <div style={{ opacity: 0.6 }}>Kebab menu offers Edit and Delete only while a ticket is OPEN.</div>
          <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
            <span style={{ opacity: 0.6 }}>Rows per page</span>
            <select
              className="input"
              style={{ width: "auto" }}
              value={pageSize}
              onChange={(e) => {
                setPageSize(Number(e.target.value));
                setPage(1);
              }}
            >
              <option value={10}>10</option>
              <option value={25}>25</option>
              <option value={50}>50</option>
            </select>
            <div style={{ display: "flex", border: "1px solid var(--color-divider)" }}>
              <button className="btn" disabled={currentPage <= 1} onClick={() => setPage((p) => p - 1)}>
                Prev
              </button>
              <button
                className="btn"
                style={{ borderLeft: "1px solid var(--color-divider)" }}
                disabled={currentPage >= totalPages}
                onClick={() => setPage((p) => p + 1)}
              >
                Next
              </button>
            </div>
          </div>
        </div>
      </div>

      {deleteTarget && (
        <DeleteTicketModal
          ticket={deleteTarget}
          onClose={() => setDeleteTarget(null)}
          onConfirm={() => setDeleteTarget(null)}
        />
      )}
    </div>
  );
}
