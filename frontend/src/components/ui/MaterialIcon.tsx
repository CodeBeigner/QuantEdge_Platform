interface MaterialIconProps {
  name: string;
  size?: number;
  className?: string;
}

export function MaterialIcon({ name, size = 20, className = '' }: MaterialIconProps) {
  return (
    <span
      className={`material-icons-outlined ${className}`}
      style={{ fontSize: size, lineHeight: 1 }}
    >
      {name}
    </span>
  );
}
