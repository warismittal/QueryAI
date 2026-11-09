import { cn } from '@/lib/utils';

/** The QueryAI mark: an aperture (the question) whose tail is a single crimson line of data (the answer). */
export function LogoMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 32 32" className={cn('size-7', className)} aria-hidden="true">
      <defs>
        <linearGradient id="qa-ring" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#f4efe9" />
          <stop offset="1" stopColor="#8f8a94" />
        </linearGradient>
        <linearGradient id="qa-tail" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#e0405a" />
          <stop offset="1" stopColor="#7a1c2d" />
        </linearGradient>
      </defs>
      <rect width="32" height="32" rx="9" fill="#0b0a0c" />
      <rect x="0.5" y="0.5" width="31" height="31" rx="8.5" fill="none" stroke="#ffffff" strokeOpacity="0.08" />
      <circle cx="14.5" cy="14.5" r="7.25" fill="none" stroke="url(#qa-ring)" strokeWidth="2.1" />
      <path d="M19.6 19.6 L25 25" stroke="url(#qa-tail)" strokeWidth="2.6" strokeLinecap="round" />
      <circle cx="14.5" cy="14.5" r="1.6" fill="#e0405a" />
    </svg>
  );
}

export function Logo({ className, size = 'md' }: { className?: string; size?: 'md' | 'lg' }) {
  return (
    <span className={cn('inline-flex items-center gap-2.5', className)}>
      <LogoMark className={size === 'lg' ? 'size-9' : 'size-7'} />
      <span className={cn('font-medium tracking-[-0.02em] text-fg', size === 'lg' ? 'text-xl' : 'text-[15px]')}>
        Query<span className="font-display text-[1.18em] italic text-accent">AI</span>
      </span>
    </span>
  );
}
