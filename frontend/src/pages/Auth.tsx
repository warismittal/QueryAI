import { forwardRef, useEffect, useRef, useState, type InputHTMLAttributes, type KeyboardEvent, type ReactNode } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useQuery } from '@tanstack/react-query';
import { ArrowRight, Check, Eye, EyeOff, Fingerprint, LockKeyhole, ShieldCheck, Sparkles } from 'lucide-react';
import { api, ApiError, errorMessage } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { QUESTION_CATEGORIES } from '@/lib/questions';
import type { SystemInfo } from '@/lib/types';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { Field, Input } from '@/components/ui/input';
import { Notice } from '@/components/ui/feedback';
import { Logo } from '@/components/layout/Logo';
import { SceneBackdrop } from '@/components/landing/SceneBackdrop';

const loginSchema = z.object({
  email: z.string().trim().email('Enter a valid email address'),
  password: z.string().min(1, 'Password is required'),
  remember: z.boolean(),
});

export const passwordRule = z
  .string()
  .min(8, 'At least 8 characters')
  .max(72, 'At most 72 characters')
  .regex(/[A-Za-z]/, 'Include at least one letter')
  .regex(/\d/, 'Include at least one digit');

const registerSchema = z
  .object({
    fullName: z.string().trim().min(2, 'Enter your name').max(120),
    email: z.string().trim().email('Enter a valid email address'),
    password: passwordRule,
    confirm: z.string(),
  })
  .refine((v) => v.password === v.confirm, { path: ['confirm'], message: 'Passwords do not match' });

const EXAMPLES = QUESTION_CATEGORIES.flatMap((c) => c.questions.slice(0, 2));

/** Cycles through real example questions on the left side of the sign-in screens. */
function RotatingQuestion() {
  const [i, setI] = useState(0);
  useEffect(() => {
    const id = window.setInterval(() => setI((n) => (n + 1) % EXAMPLES.length), 3600);
    return () => window.clearInterval(id);
  }, []);
  return (
    <div className="glass-soft relative max-w-lg overflow-hidden rounded-3xl p-6">
      <p className="eyebrow mb-3 flex items-center gap-2"><Sparkles className="size-3.5 text-accent" /> People ask things like</p>
      <p key={i} className="min-h-[4.5rem] font-display text-[1.7rem] leading-snug text-fg animate-fade-up">“{EXAMPLES[i]}”</p>
      <div className="mt-4 flex gap-1.5" aria-hidden="true">
        {EXAMPLES.map((_, n) => (
          <span key={n} className={cn('h-1 rounded-full transition-all duration-500', n === i ? 'w-6 bg-accent' : 'w-1.5 bg-border-strong')} />
        ))}
      </div>
    </div>
  );
}

function AuthLayout({ title, subtitle, children, footer }: { title: string; subtitle: string; children: ReactNode; footer: ReactNode }) {
  return (
    <div className="relative isolate grid min-h-screen lg:grid-cols-[1.1fr_0.9fr]">
      {/* the library door, glowing, behind glass */}
      <SceneBackdrop progress={0.7} />
      <div className="pointer-events-none absolute inset-y-0 left-0 -z-[5] w-full bg-linear-to-r from-bg/85 via-bg/40 to-transparent lg:w-2/3" />
      <aside className="relative hidden flex-col justify-between p-12 lg:flex">
        <Link to="/" className="w-fit"><Logo /></Link>
        <div className="space-y-10 animate-fade-up">
          <p className="max-w-lg font-display text-6xl leading-[0.98] text-fg">
            Every answer is <span className="italic ombre-text">already</span> written somewhere in your data.
          </p>
          <RotatingQuestion />
        </div>
        <ul className="flex flex-wrap gap-x-6 gap-y-2 text-xs text-muted">
          <li className="flex items-center gap-1.5"><ShieldCheck className="size-3.5 text-accent" /> Read-only by design</li>
          <li className="flex items-center gap-1.5"><Fingerprint className="size-3.5 text-accent" /> Personal data masked</li>
          <li className="flex items-center gap-1.5"><LockKeyhole className="size-3.5 text-accent" /> Every query audited</li>
        </ul>
      </aside>
      <main className="relative flex items-center justify-center px-5 py-16">
        <div className="glass relative w-full max-w-md rounded-[32px] p-8 animate-fade-up sm:p-10">
          <div className="pointer-events-none absolute -inset-px rounded-[32px] bg-[radial-gradient(120%_60%_at_50%_0%,rgb(var(--glow)/0.18),transparent_60%)]" />
          <div className="relative">
            <Link to="/" className="mb-8 flex justify-center lg:hidden"><Logo /></Link>
            <h1 className="font-display text-[2.6rem] leading-none text-fg">{title}</h1>
            <p className="mb-8 mt-3 text-sm text-fg-2">{subtitle}</p>
            {children}
            <p className="mt-8 text-center text-sm text-muted">{footer}</p>
          </div>
        </div>
      </main>
    </div>
  );
}

/** Password field with show/hide and a Caps Lock warning. */
const PasswordInput = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(function PasswordInput(props, ref) {
  const [visible, setVisible] = useState(false);
  const [caps, setCaps] = useState(false);
  const check = (e: KeyboardEvent<HTMLInputElement>) => setCaps(e.getModifierState?.('CapsLock') ?? false);
  return (
    <div>
      <div className="relative">
        <Input {...props} ref={ref} type={visible ? 'text' : 'password'} className="h-11 rounded-xl pr-10"
          onKeyDown={(e) => { check(e); props.onKeyDown?.(e); }} onKeyUp={check} />
        <button type="button" onClick={() => setVisible((v) => !v)} aria-label={visible ? 'Hide password' : 'Show password'}
          className="absolute right-2 top-1/2 -translate-y-1/2 rounded-lg p-1.5 text-muted transition-colors hover:text-fg">
          {visible ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
        </button>
      </div>
      {caps && <p className="mt-1 text-xs text-warning">Caps Lock is on</p>}
    </div>
  );
});

function strength(pw: string): { score: number; label: string } {
  let score = 0;
  if (pw.length >= 8) score++;
  if (pw.length >= 12) score++;
  if (/[a-z]/.test(pw) && /[A-Z]/.test(pw)) score++;
  if (/\d/.test(pw) && /[^A-Za-z0-9]/.test(pw)) score++;
  return { score, label: ['Too short', 'Weak', 'Fair', 'Good', 'Strong'][score] };
}

function StrengthMeter({ value }: { value: string }) {
  if (!value) return null;
  const { score, label } = strength(value);
  const tone = score <= 1 ? 'bg-danger' : score === 2 ? 'bg-warning' : 'bg-success';
  return (
    <div className="mt-2 flex items-center gap-2" aria-live="polite">
      <div className="flex flex-1 gap-1">
        {[0, 1, 2, 3].map((n) => (
          <span key={n} className={cn('h-1 flex-1 rounded-full transition-colors duration-300', n < score ? tone : 'bg-surface-3')} />
        ))}
      </div>
      <span className="w-14 text-right text-[11px] text-muted">{label}</span>
    </div>
  );
}

/** A short horizontal jolt (Web Animations API) when a form is rejected; skipped for reduced motion. */
function shakeElement(el: HTMLElement | null) {
  if (!el || typeof el.animate !== 'function' || window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return;
  el.animate([{ transform: 'translateX(0)' }, { transform: 'translateX(-6px)' }, { transform: 'translateX(5px)' },
    { transform: 'translateX(-3px)' }, { transform: 'translateX(2px)' }, { transform: 'translateX(0)' }], { duration: 420, easing: 'ease-out' });
}

function Divider({ children }: { children: ReactNode }) {
  return (
    <div className="my-5 flex items-center gap-3 text-[11px] uppercase tracking-[0.18em] text-muted">
      <span className="h-px flex-1 bg-border" />{children}<span className="h-px flex-1 bg-border" />
    </div>
  );
}

export function LoginPage() {
  const { user, login, guest, sessionMessage } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [error, setError] = useState<string | null>(null);
  const formRef = useRef<HTMLFormElement>(null);
  const [guestPending, setGuestPending] = useState(false);
  const info = useQuery({ queryKey: ['system-info'], queryFn: () => api.get<SystemInfo>('/system/info'), retry: false });
  const form = useForm<z.infer<typeof loginSchema>>({ resolver: zodResolver(loginSchema), defaultValues: { email: '', password: '', remember: true } });
  const from = (location.state as { from?: string } | null)?.from ?? '/app';
  const signingIn = useRef(false);

  if (user && !signingIn.current) return <Navigate to={from} replace />;

  const fail = (message: string) => {
    signingIn.current = false;
    setError(message);
    shakeElement(formRef.current);
  };

  const onSubmit = form.handleSubmit(async (values) => {
    setError(null);
    signingIn.current = true;
    try {
      await login(values.email, values.password, values.remember);
      navigate(from, { replace: true });
    } catch (e) {
      fail(e instanceof ApiError && e.status === 401 ? 'Incorrect email or password.' : errorMessage(e));
    }
  }, () => shakeElement(formRef.current));

  const continueAsGuest = async () => {
    setError(null);
    setGuestPending(true);
    signingIn.current = true;
    try {
      await guest();
      navigate('/app', { replace: true });
    } catch (e) {
      fail(errorMessage(e));
    } finally {
      setGuestPending(false);
    }
  };

  return (
    <AuthLayout title="Welcome back" subtitle="Sign in to continue to your workspace."
      footer={<>New to QueryAI? <Link className="font-medium text-accent hover:underline" to="/register">Create an account</Link></>}>
      <form ref={formRef} onSubmit={onSubmit} className="space-y-4" noValidate>
        {sessionMessage && <Notice tone="warning">{sessionMessage}</Notice>}
        {error && <Notice tone="danger">{error}</Notice>}
        <Field label="Email" htmlFor="email" error={form.formState.errors.email?.message}>
          <Input id="email" type="email" autoComplete="email" autoFocus placeholder="you@company.com" className="h-11 rounded-xl"
            aria-invalid={!!form.formState.errors.email} {...form.register('email')} />
        </Field>
        <Field label="Password" htmlFor="password" error={form.formState.errors.password?.message}>
          <PasswordInput id="password" autoComplete="current-password" placeholder="••••••••"
            aria-invalid={!!form.formState.errors.password} {...form.register('password')} />
        </Field>
        <label className="flex cursor-pointer select-none items-center gap-2 text-sm text-fg-2">
          <input type="checkbox" className="size-4 rounded accent-[var(--accent)]" {...form.register('remember')} />
          Keep me signed in
        </label>
        <Button type="submit" size="lg" className="h-11 w-full" loading={form.formState.isSubmitting}>
          {!form.formState.isSubmitting && <>Sign in <ArrowRight /></>}
        </Button>
      </form>
      {info.data?.guestAccess && (
        <>
          <Divider>or</Divider>
          <Button type="button" variant="outline" size="lg" className="h-11 w-full" onClick={continueAsGuest} loading={guestPending}>
            Continue as guest
          </Button>
          <p className="mt-2 text-center text-[11px] text-muted">Opens a ready-made sales workspace. Personal data stays masked.</p>
        </>
      )}
    </AuthLayout>
  );
}

export function RegisterPage() {
  const { user, register: registerUser } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);
  const formRef = useRef<HTMLFormElement>(null);
  // set once this page creates the account, so the new user lands on "connect a database" rather than the home page
  const registering = useRef(false);
  const form = useForm<z.infer<typeof registerSchema>>({
    resolver: zodResolver(registerSchema),
    defaultValues: { fullName: '', email: '', password: '', confirm: '' },
  });
  const password = form.watch('password');
  const confirm = form.watch('confirm');

  if (user && !registering.current) return <Navigate to="/app" replace />;

  const onSubmit = form.handleSubmit(async (values) => {
    setError(null);
    registering.current = true;
    try {
      await registerUser(values.fullName, values.email, values.password);
      navigate('/app/connections', { replace: true });
    } catch (e) {
      if (e instanceof ApiError && e.fieldErrors) {
        Object.entries(e.fieldErrors).forEach(([field, message]) =>
          form.setError(field as keyof z.infer<typeof registerSchema>, { message }));
      }
      registering.current = false;
      setError(errorMessage(e));
      shakeElement(formRef.current);
    }
  }, () => shakeElement(formRef.current));
  const errors = form.formState.errors;

  return (
    <AuthLayout title="Create your account" subtitle="Get a personal workspace and connect your first database in minutes."
      footer={<>Already have an account? <Link className="font-medium text-accent hover:underline" to="/login">Sign in</Link></>}>
      <form ref={formRef} onSubmit={onSubmit} className="space-y-4" noValidate>
        {error && <Notice tone="danger">{error}</Notice>}
        <Field label="Full name" htmlFor="fullName" error={errors.fullName?.message}>
          <Input id="fullName" autoComplete="name" autoFocus placeholder="Your name" className="h-11 rounded-xl"
            aria-invalid={!!errors.fullName} {...form.register('fullName')} />
        </Field>
        <Field label="Work email" htmlFor="email" error={errors.email?.message}>
          <Input id="email" type="email" autoComplete="email" placeholder="you@company.com" className="h-11 rounded-xl"
            aria-invalid={!!errors.email} {...form.register('email')} />
        </Field>
        <Field label="Password" htmlFor="password" error={errors.password?.message} hint="8+ characters with a letter and a digit">
          <PasswordInput id="password" autoComplete="new-password" aria-invalid={!!errors.password} {...form.register('password')} />
          <StrengthMeter value={password} />
        </Field>
        <Field label="Confirm password" htmlFor="confirm" error={errors.confirm?.message}>
          <div className="relative">
            <PasswordInput id="confirm" autoComplete="new-password" aria-invalid={!!errors.confirm} {...form.register('confirm')} />
            {confirm && confirm === password && (
              <Check className="absolute right-10 top-[22px] size-4 -translate-y-1/2 text-success" aria-label="Passwords match" />
            )}
          </div>
        </Field>
        <Button type="submit" size="lg" className="h-11 w-full" loading={form.formState.isSubmitting}>
          {!form.formState.isSubmitting && <>Create account <ArrowRight /></>}
        </Button>
        <p className="text-center text-[11px] leading-relaxed text-muted">
          Your password is stored as a one-way hash. Database credentials you add later are encrypted.
        </p>
      </form>
    </AuthLayout>
  );
}
