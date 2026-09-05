// TODO(API integration): stand-in for whatever endpoint ends up backing
// saved knowledge-base articles (not yet defined in the backend). Replace
// once that exists, and delete this file - it should not ship in the
// final build.
// Saved knowledge-base articles for the Bookmarks screen's "Articles" tab
// (handoff: design_handoff_bookmarks). Hardcoded per the reference mock -
// no live KB data source wired up yet.
export const mockArticles = [
  { id: "KB-204", title: "Resetting your campus wi-fi password", status: "ARTICLE", folder: "Saved articles" },
  { id: "KB-118", title: "Requesting an official transcript", status: "ARTICLE", folder: "Saved articles" },
];
