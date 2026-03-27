interface PageHeaderProps {
  title: string;
  subtitle: string;
  children?: React.ReactNode;
}

export function PageHeader({ title, subtitle, children }: PageHeaderProps) {
  return (
    <div className="page-header" style={{ marginBottom: '1.75rem' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div>
          <h1>{title}</h1>
          <div className="subtitle">{subtitle}</div>
        </div>
        {children}
      </div>
    </div>
  );
}
