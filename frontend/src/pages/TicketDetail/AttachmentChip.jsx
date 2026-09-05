import { Image as ImageIcon, FileText } from "lucide-react";

const ICONS = {
  image: ImageIcon,
  document: FileText,
};

export default function AttachmentChip({ attachment }) {
  const Icon = ICONS[attachment.type] ?? FileText;
  return (
    <a
      href="#"
      onClick={(e) => e.preventDefault()}
      style={{
        display: "flex",
        alignItems: "center",
        gap: 8,
        border: "1px solid var(--color-divider)",
        padding: "6px 10px",
        fontSize: 12,
        color: "inherit",
        textDecoration: "none",
      }}
    >
      <Icon size={14} />
      {attachment.name}
    </a>
  );
}
