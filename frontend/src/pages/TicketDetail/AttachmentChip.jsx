import { Image as ImageIcon, FileText } from "lucide-react";

const ICONS = {
  image: ImageIcon,
  document: FileText,
};

export default function AttachmentChip({ attachment }) {
  const Icon = ICONS[attachment.type] ?? FileText;
  return (
    <a href="#" className="file-link" onClick={(e) => e.preventDefault()}>
      <Icon size={14} />
      {attachment.name}
    </a>
  );
}
