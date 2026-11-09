import { Link } from 'react-router-dom';
import { Button } from '@/components/ui/button';

export default function NotFound() {
  return (
    <div className="flex min-h-full flex-col items-center justify-center gap-4 p-8 text-center">
      <p className="font-mono text-sm text-accent">404</p>
      <h1 className="text-2xl font-semibold">This page doesn't exist</h1>
      <p className="max-w-md text-sm text-muted">The link may be broken or the resource was removed.</p>
      <Button asChild><Link to="/app">Back to QueryAI</Link></Button>
    </div>
  );
}
