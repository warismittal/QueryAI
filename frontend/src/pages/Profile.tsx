import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation, useQuery } from '@tanstack/react-query';
import { toast } from 'sonner';
import { api, errorMessage } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import type { AuditEvent, User } from '@/lib/types';
import { formatDateTime, timeAgo } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Card, CardBody, CardHeader } from '@/components/ui/card';
import { Field, Input } from '@/components/ui/input';
import { PageHeader, Skeleton } from '@/components/ui/feedback';
import { passwordRule } from './Auth';

const profileSchema = z.object({ fullName: z.string().trim().min(2).max(120), jobTitle: z.string().max(120) });
const passwordSchema = z.object({ currentPassword: z.string().min(1, 'Required'), newPassword: passwordRule, confirm: z.string() })
  .refine((v) => v.newPassword === v.confirm, { path: ['confirm'], message: 'Passwords do not match' });

export default function Profile() {
  const { user, refreshUser } = useAuth();
  const profile = useForm<z.infer<typeof profileSchema>>({ resolver: zodResolver(profileSchema) });
  const pw = useForm<z.infer<typeof passwordSchema>>({ resolver: zodResolver(passwordSchema), defaultValues: { currentPassword: '', newPassword: '', confirm: '' } });
  useEffect(() => { if (user) profile.reset({ fullName: user.fullName, jobTitle: user.jobTitle ?? '' }); }, [user, profile]);
  const activity = useQuery({ queryKey: ['my-activity'], queryFn: () => api.get<AuditEvent[]>('/audit/me') });

  const saveProfile = useMutation({
    mutationFn: (v: z.infer<typeof profileSchema>) => api.put<User>('/users/me', v),
    onSuccess: async () => { await refreshUser(); toast.success('Profile updated'); },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const changePassword = useMutation({
    mutationFn: (v: z.infer<typeof passwordSchema>) => api.put('/users/me/password', { currentPassword: v.currentPassword, newPassword: v.newPassword }),
    onSuccess: () => { toast.success('Password changed'); pw.reset(); },
    onError: (e) => toast.error(errorMessage(e)),
  });

  return (
    <div>
      <PageHeader title="Profile" description={user ? `${user.email} · member since ${formatDateTime(user.createdAt)}` : undefined} />
      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader title="Personal details" actions={user?.systemRole === 'ADMIN' && <Badge tone="accent">platform admin</Badge>} />
          <CardBody>
            <form className="space-y-3" onSubmit={profile.handleSubmit((v) => saveProfile.mutate(v))} noValidate>
              <Field label="Full name" error={profile.formState.errors.fullName?.message}><Input {...profile.register('fullName')} /></Field>
              <Field label="Job title" error={profile.formState.errors.jobTitle?.message}><Input {...profile.register('jobTitle')} placeholder="e.g. Data Analyst" /></Field>
              <Field label="Email"><Input value={user?.email ?? ''} disabled /></Field>
              <Button type="submit" loading={saveProfile.isPending}>Save profile</Button>
            </form>
          </CardBody>
        </Card>
        <Card>
          <CardHeader title="Change password" description="8+ characters with at least one letter and one digit." />
          <CardBody>
            <form className="space-y-3" onSubmit={pw.handleSubmit((v) => changePassword.mutate(v))} noValidate>
              <Field label="Current password" error={pw.formState.errors.currentPassword?.message}><Input type="password" autoComplete="current-password" {...pw.register('currentPassword')} /></Field>
              <Field label="New password" error={pw.formState.errors.newPassword?.message}><Input type="password" autoComplete="new-password" {...pw.register('newPassword')} /></Field>
              <Field label="Confirm new password" error={pw.formState.errors.confirm?.message}><Input type="password" autoComplete="new-password" {...pw.register('confirm')} /></Field>
              <Button type="submit" loading={changePassword.isPending}>Change password</Button>
            </form>
          </CardBody>
        </Card>
        <Card className="lg:col-span-2">
          <CardHeader title="Your recent activity" description="From the audit log" />
          <CardBody className="space-y-2">
            {activity.isLoading ? <Skeleton className="h-24" /> : activity.data?.length === 0 ? <p className="text-sm text-muted">No activity yet.</p> : activity.data?.map((e) => (
              <div key={e.id} className="flex items-center gap-3 text-sm">
                <Badge tone={e.success ? 'neutral' : 'danger'}>{e.action.toLowerCase().replace(/_/g, ' ')}</Badge>
                <span className="text-xs text-muted">{e.resourceType ? `${e.resourceType.toLowerCase()} #${e.resourceId}` : ''}</span>
                <span className="ml-auto text-xs text-muted">{timeAgo(e.createdAt)} · {e.ipAddress}</span>
              </div>
            ))}
          </CardBody>
        </Card>
      </div>
    </div>
  );
}
