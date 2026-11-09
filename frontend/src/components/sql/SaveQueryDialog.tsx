import { useEffect, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { api, errorMessage } from '@/lib/api';
import { useWorkspace } from '@/lib/workspace';
import type { SavedQuery } from '@/lib/types';
import { Button } from '@/components/ui/button';
import { Dialog } from '@/components/ui/dialog';
import { Checkbox, Field, Input, Textarea } from '@/components/ui/input';

export function SaveQueryDialog({ open, onOpenChange, sql, question, chartType, dataSourceId }: {
  open: boolean; onOpenChange: (o: boolean) => void; sql: string; question?: string; chartType?: string; dataSourceId: number | null | undefined;
}) {
  const { workspaceId } = useWorkspace();
  const qc = useQueryClient();
  const [name, setName] = useState('');
  const [folder, setFolder] = useState('');
  const [description, setDescription] = useState('');
  const [shared, setShared] = useState(false);
  const [template, setTemplate] = useState(false);
  useEffect(() => {
    if (open) {
      setName(question ? question.slice(0, 80) : '');
      setFolder('');
      setDescription('');
      setShared(false);
      setTemplate(false);
    }
  }, [open, question]);
  const save = useMutation({
    mutationFn: () => api.post<SavedQuery>('/saved-queries', {
      workspaceId, dataSourceId, name: name.trim(), folder: folder.trim() || undefined, description: description.trim() || undefined,
      question, sql, chartType, visibility: shared ? 'WORKSPACE' : 'PRIVATE', template,
    }),
    onSuccess: (s) => {
      toast.success(`Saved “${s.name}”`);
      qc.invalidateQueries({ queryKey: ['saved-queries'] });
      onOpenChange(false);
    },
    onError: (e) => toast.error(errorMessage(e)),
  });
  return (
    <Dialog open={open} onOpenChange={onOpenChange} title="Save query" description="Saved queries keep a version history and can power dashboards."
      footer={<><Button variant="secondary" onClick={() => onOpenChange(false)}>Cancel</Button>
        <Button onClick={() => save.mutate()} loading={save.isPending} disabled={!name.trim() || !dataSourceId}>Save</Button></>}>
      <div className="space-y-3">
        <Field label="Name" htmlFor="sq-name"><Input id="sq-name" value={name} onChange={(e) => setName(e.target.value)} maxLength={160} /></Field>
        <Field label="Folder" htmlFor="sq-folder" hint="Optional, e.g. Finance"><Input id="sq-folder" value={folder} onChange={(e) => setFolder(e.target.value)} maxLength={120} /></Field>
        <Field label="Description" htmlFor="sq-desc"><Textarea id="sq-desc" value={description} onChange={(e) => setDescription(e.target.value)} rows={2} /></Field>
        <div className="flex flex-wrap gap-4">
          <Checkbox label="Share with workspace" checked={shared} onChange={(e) => setShared(e.target.checked)} />
          <Checkbox label="Reusable template" checked={template} onChange={(e) => setTemplate(e.target.checked)} />
        </div>
      </div>
    </Dialog>
  );
}
