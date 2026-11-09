import { AlertTriangle, CheckCircle2, CircleDashed, HelpCircle, ShieldAlert, XCircle } from 'lucide-react';
import type { ExecutionStatus, ValidationStatus } from '@/lib/types';
import { Badge } from './badge';

export function ExecutionBadge({ status }: { status: ExecutionStatus }) {
  switch (status) {
    case 'SUCCESS':
      return <Badge tone="success"><CheckCircle2 />Success</Badge>;
    case 'FAILED':
      return <Badge tone="danger"><XCircle />Failed</Badge>;
    case 'BLOCKED':
      return <Badge tone="danger"><ShieldAlert />Blocked</Badge>;
    case 'CLARIFICATION_NEEDED':
      return <Badge tone="warning"><HelpCircle />Clarification</Badge>;
    default:
      return <Badge><CircleDashed />Not run</Badge>;
  }
}

export function ValidationBadge({ status }: { status: ValidationStatus }) {
  switch (status) {
    case 'VALID':
      return <Badge tone="success"><CheckCircle2 />Valid</Badge>;
    case 'WARNING':
      return <Badge tone="warning"><AlertTriangle />Warnings</Badge>;
    case 'INVALID':
      return <Badge tone="danger"><XCircle />Invalid</Badge>;
    default:
      return <Badge><CircleDashed />Unchecked</Badge>;
  }
}
