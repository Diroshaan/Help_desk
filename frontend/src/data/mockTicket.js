/**
 * Stand-in for a GET /api/tickets/{id} response, matching the UNIHELP
 * high-fidelity design handoff.
 *
 * Per the project specification (System Limitations & Constraints):
 * "Single-response resolution model - each ticket carries one official
 * solution response ... rather than a live threaded conversation."
 * So `messages` only ever grows from the officer side (status updates before
 * the final call) - there is no reply box for the student to keep the
 * thread going, and `resolution` is the one official response that closes
 * the ticket out.
 */
export const mockTicket = {
  id: "TKT-1042",
  subject: "Campus wi-fi rejects my credentials after a password reset",
  status: "OPEN", // OPEN | IN_PROGRESS | RESOLVED
  priority: "Normal",
  department: "IT Services",
  createdAt: "2026-03-12T09:41:00",
  updatedAt: "2026-03-12T10:22:00",
  isBookmarked: false,
  student: {
    name: "Alex Rivera",
    initials: "AR",
    studentId: "ST20481734",
  },
  officer: {
    name: "D. Kumar",
    initials: "DK",
  },
  request: {
    name: "Alex Rivera",
    initials: "AR",
    timestamp: "12 Mar, 09:41",
    body:
      "I changed my portal password yesterday and now eduroam refuses to connect on both my laptop and phone. The portal itself logs in fine with the new password. I have forgotten the network and re-added it twice.",
    attachments: [
      { id: "a1", name: "error-screen.png", type: "image" },
      { id: "a2", name: "device-log.pdf", type: "document" },
    ],
  },
  // Officer status updates that precede the final resolution. Never
  // student-authored - there is no reply box in this design.
  officerUpdates: [
    {
      id: "u1",
      name: "D. Kumar",
      initials: "DK",
      timestamp: "12 Mar, 10:22",
      body:
        "Thanks — the reset has not propagated to the RADIUS directory yet, which is why the portal works and eduroam does not. I have forced a sync. Please remove the saved network once more and reconnect in about ten minutes, then let me know.",
    },
  ],
  // null until the assigned officer publishes the single official response
  resolution: null,
  timeline: [
    { key: "submitted", label: "Submitted", meta: "12 Mar, 09:41", state: "done" },
    { key: "routed", label: "Routed to IT Services", meta: "Automatic · 09:41", state: "done" },
    {
      key: "in_progress",
      label: "In progress",
      meta: "Picked up by D. Kumar · 10:14",
      state: "current",
    },
    { key: "resolved", label: "Resolved", meta: "Not yet", state: "upcoming" },
  ],
  feedback: null, // set once the student submits a rating/comment
};

/** Example of what `resolution` looks like once RESOLVED, for reference/testing. */
export const mockResolvedResolution = {
  officerName: "D. Kumar",
  timestamp: "12 Mar, 11:05",
  body:
    "The RADIUS sync has completed and your credentials are now propagated. Please reconnect to eduroam using your current portal password - let us know if it still fails to authenticate.",
};
