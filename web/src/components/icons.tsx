type IconProps = { className?: string };

const base = {
  width: 24,
  height: 24,
  viewBox: "0 0 24 24",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 1.9,
  strokeLinecap: "round" as const,
  strokeLinejoin: "round" as const,
  "aria-hidden": true,
  focusable: false,
};

export function UsersIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <path d="M16 20v-1.2a3.8 3.8 0 0 0-3.8-3.8H7.8A3.8 3.8 0 0 0 4 18.8V20" />
      <circle cx="10" cy="8" r="3.2" />
      <path d="M20 20v-1a3.2 3.2 0 0 0-2.4-3.1M15.6 4.9a3.2 3.2 0 0 1 0 6.2" />
    </svg>
  );
}

export function QrIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <rect x="3.5" y="3.5" width="6.5" height="6.5" rx="1.2" />
      <rect x="14" y="3.5" width="6.5" height="6.5" rx="1.2" />
      <rect x="3.5" y="14" width="6.5" height="6.5" rx="1.2" />
      <path d="M14 14h2.5v2.5H14zM19 14h1.5M14 19h2.5M19 17.5V20.5" />
    </svg>
  );
}

export function UserIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className}>
      <circle cx="12" cy="8.2" r="3.7" />
      <path d="M4.8 20.2c.6-3.6 3.5-5.6 7.2-5.6s6.6 2 7.2 5.6" />
    </svg>
  );
}

export function CopyIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className} width={20} height={20}>
      <rect x="8.5" y="8.5" width="11" height="11" rx="2.2" />
      <path d="M15.5 8.5V6.2a2 2 0 0 0-2-2H6.2a2 2 0 0 0-2 2v7.3a2 2 0 0 0 2 2h2.3" />
    </svg>
  );
}

export function CheckIcon({ className }: IconProps) {
  return (
    <svg {...base} className={className} width={20} height={20}>
      <path d="m5 12.5 4.5 4.5L19 7.5" />
    </svg>
  );
}

export function LogoMark({ className }: IconProps) {
  return (
    <svg viewBox="0 0 108 108" className={className} aria-hidden="true" focusable="false">
      <circle cx="54" cy="54" r="22" fill="none" stroke="#1E6E5C" strokeWidth="7" />
      <circle cx="54" cy="54" r="6.5" fill="#1E6E5C" />
    </svg>
  );
}
