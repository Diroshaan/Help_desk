import { useState } from "react";
import AppShell from "./components/AppShell.jsx";
import TicketsList from "./pages/TicketsList/TicketsList.jsx";
import TicketDetail from "./pages/TicketDetail/TicketDetail.jsx";
import Bookmarks from "./pages/Bookmarks/Bookmarks.jsx";
import { mockTicket } from "./data/mockTicket.js";
import { mockTickets } from "./data/mockTickets.js";
import "./styles/buttons.css";

function App() {
  // Simple client-side view state standing in for real routing, matching
  // the handoffs' goTickets/goTicket/goBookmarks-style navigation between
  // screens.
  const [view, setView] = useState({ screen: "tickets", ticketId: null });

  function openTicket(ticketId) {
    setView({ screen: "ticket", ticketId });
  }

  function backToTickets(e) {
    e?.preventDefault?.();
    setView({ screen: "tickets", ticketId: null });
  }

  // Sidebar nav: only "My tickets" and "Bookmarks" lead to built screens so
  // far - the other nav items (Dashboard, Submit ticket, Knowledge base,
  // Profile) stay inert until those handoffs arrive.
  function navigate(key) {
    if (key === "tickets") setView({ screen: "tickets", ticketId: null });
    else if (key === "bookmarks") setView({ screen: "bookmarks", ticketId: null });
  }

  // Only TKT-1042 has full detail mock data wired up (data/mockTicket.js).
  const ticket = view.ticketId === mockTicket.id ? mockTicket : { ...mockTicket, id: view.ticketId };

  // "My tickets" stays the active sidebar item while viewing a single
  // ticket, per the Ticket Detail handoff's note.
  const activeNav = view.screen === "bookmarks" ? "bookmarks" : "tickets";

  return (
    <AppShell
      activeNav={activeNav}
      onNavigate={navigate}
      user={{
        name: mockTicket.student.name,
        initials: mockTicket.student.initials,
        studentId: mockTicket.student.studentId,
      }}
    >
      {view.screen === "tickets" && <TicketsList tickets={mockTickets} onOpenTicket={openTicket} />}
      {view.screen === "ticket" && <TicketDetail ticket={ticket} onBack={backToTickets} />}
      {view.screen === "bookmarks" && <Bookmarks />}
    </AppShell>
  );
}

export default App;
