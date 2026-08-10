/**
 * Minimal sandook (safe / cash-box) icon.
 * Used in the top bar, login page, and favicon.
 * Accepts `className` so callers can resize freely.
 */
export function SandookIcon({ className = "h-8 w-8" }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 32 32"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={className}
      aria-hidden="true"
    >
      {/* body */}
      <rect x="3" y="11" width="26" height="18" rx="3" fill="#059669" />
      {/* lid */}
      <rect x="3" y="8" width="26" height="7" rx="3" fill="#047857" />
      {/* lid highlight */}
      <rect x="3" y="8" width="26" height="3.5" rx="3" fill="#10b981" />
      {/* coin slot */}
      <rect x="13" y="5" width="6" height="3" rx="1.5" fill="#047857" />
      {/* lock circle */}
      <circle cx="16" cy="20.5" r="3.5" fill="#f5f5f4" />
      {/* keyhole */}
      <rect x="14.8" y="19" width="2.4" height="3" rx="1.2" fill="#047857" />
    </svg>
  );
}
