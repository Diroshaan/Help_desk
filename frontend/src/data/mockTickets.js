// TODO(API integration): stand-in for GET /api/tickets (and, filtered,
// the ticket-derived cards on the Bookmarks page). Replace with a real
// fetch once the ticketportal backend exposes it, and delete this file -
// it should not ship in the final build.
/**
 * Stand-in for a GET /api/tickets response (the student's ticket list).
 * Matches the sample data in the "My Tickets" design handoff. TKT-1042
 * lines up with data/mockTicket.js so clicking that row opens the ticket
 * detail page we already built.
 */
export const mockTickets = [
  {
    id: "TKT-1042",
    subject: "Campus wi-fi rejects my credentials after a password reset",
    department: "IT Services",
    status: "OPEN",
    createdAt: "12 Mar",
    updatedAt: "2 hours ago",
    unread: true,
  },
  {
    id: "TKT-1037",
    subject: "Module registration closed before my slot opened",
    department: "Registration",
    status: "IN_PROGRESS",
    createdAt: "09 Mar",
    updatedAt: "Yesterday",
    unread: false,
  },
  {
    id: "TKT-1030",
    subject: "Scholarship instalment not reflected on my statement",
    department: "Financial Aid",
    status: "IN_PROGRESS",
    createdAt: "06 Mar",
    updatedAt: "2 days ago",
    unread: true,
  },
  {
    id: "TKT-1021",
    subject: "Library fine charged for a book returned on time",
    department: "Library",
    status: "RESOLVED",
    createdAt: "28 Feb",
    updatedAt: "4 days ago",
    unread: false,
  },
  {
    id: "TKT-1014",
    subject: "Hostel room key card stopped working",
    department: "Hostel",
    status: "RESOLVED",
    createdAt: "21 Feb",
    updatedAt: "1 week ago",
    unread: false,
  },
  {
    id: "TKT-1009",
    subject: "Re-sit timetable clashes with a core lab",
    department: "Examinations",
    status: "RESOLVED",
    createdAt: "17 Feb",
    updatedAt: "2 weeks ago",
    unread: false,
  },
];
