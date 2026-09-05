import { ArrowLeft } from "lucide-react";

/**
 * Shared "← label" back-link style used on every screen in the app
 * (handoff: "All back-links across the app share one style"). Arrow icon +
 * semibold label at 65% opacity (100% on hover), no underline. Styling
 * lives in the .back-link CSS class (not inline) so :hover can apply.
 */
export default function BackLink({ children, onClick, href = "#" }) {
  return (
    <a href={href} className="back-link" onClick={onClick ?? ((e) => e.preventDefault())}>
      <ArrowLeft size={14} />
      {children}
    </a>
  );
}
