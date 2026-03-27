import { CSSProperties } from 'react';

export interface MaterialIconProps {
  name: string;
  size?: number;
  className?: string;
  style?: CSSProperties;
}

export function MaterialIcon({ name, size = 20, className = '', style }: MaterialIconProps) {
  return (
    <span
      className={`material-icons-outlined ${className}`}
      style={{ fontSize: size, lineHeight: 1, ...style }}
    >
      {name}
    </span>
  );
}
