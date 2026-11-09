import { forwardRef, type ButtonHTMLAttributes } from 'react';
import { Slot } from '@radix-ui/react-slot';
import { cva, type VariantProps } from 'class-variance-authority';
import { Loader2 } from 'lucide-react';
import { cn } from '@/lib/utils';

const buttonVariants = cva(
  'inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-full text-sm font-medium tracking-[-0.005em] transition-all duration-300 ease-out disabled:pointer-events-none disabled:opacity-45 [&_svg]:size-4 [&_svg]:shrink-0 cursor-pointer active:scale-[0.98]',
  {
    variants: {
      variant: {
        primary: 'bg-linear-to-b from-accent to-accent-2 text-accent-fg shadow-[0_10px_30px_-12px_rgb(var(--glow)/0.8),inset_0_1px_0_rgb(255_255_255/0.18)] hover:shadow-[0_14px_40px_-12px_rgb(var(--glow)/0.95),inset_0_1px_0_rgb(255_255_255/0.22)] hover:brightness-110',
        secondary: 'bg-surface-2/80 text-fg border border-border hover:border-border-strong hover:bg-surface-3',
        ghost: 'text-fg-2 hover:bg-surface-2 hover:text-fg',
        outline: 'border border-border-strong bg-transparent text-fg hover:border-accent/60 hover:bg-accent-soft/40',
        danger: 'bg-danger text-white hover:brightness-110',
        link: 'text-accent underline-offset-4 hover:underline px-0',
      },
      size: {
        sm: 'h-8 px-3.5 text-xs',
        md: 'h-9 px-4.5',
        lg: 'h-12 px-7 text-[15px]',
        icon: 'h-9 w-9',
        'icon-sm': 'h-7 w-7',
      },
    },
    defaultVariants: { variant: 'primary', size: 'md' },
  },
);

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement>, VariantProps<typeof buttonVariants> {
  asChild?: boolean;
  loading?: boolean;
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, asChild, loading, children, disabled, ...props }, ref) => {
    const Comp = asChild ? Slot : 'button';
    return (
      <Comp ref={ref} className={cn(buttonVariants({ variant, size }), className)} disabled={disabled || loading} {...props}>
        {asChild ? children : (
          <>
            {loading && <Loader2 className="animate-spin" />}
            {children}
          </>
        )}
      </Comp>
    );
  },
);
Button.displayName = 'Button';
