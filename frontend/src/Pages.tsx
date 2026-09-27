import { useEffect, useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import { ApiError, Dashboard, Jobs, Notifications, Tasks, type Task } from "./api";
import { useAuth } from "./auth";
import { TaskPanel } from "./TaskPanel";
import { DuePill } from "./Board";

function AuthShell({ title, sub, children }: { title: string; sub: string; children: React.ReactNode }) {
  return (
    <div className="auth-split">
      <div className="auth-side">
        <div style={{ fontWeight: 700, fontSize: 20, marginBottom: 4 }}>TaskFlow</div>
        <h1>Every due date is a promise the system keeps.</h1>
        <p style={{ color: "#b9bfca" }}>
          A board on the surface, a distributed scheduler underneath — reminders that
          retry, locks that prevent double-work, and an audit trail for everything.
        </p>
        <ul>
          <li>Drag-and-drop board with optimistic concurrency</li>
          <li>Due dates become jobs with retry and backoff</li>
          <li>Every change lands in the audit timeline</li>
        </ul>
      </div>
      <div className="auth-form">
        <div className="auth-box">
          <h1>{title}</h1>
          <p className="muted">{sub}</p>
          {children}
        </div>
      </div>
    </div>
  );
}

export function Login() {
  const { login } = useAuth();
  const nav = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const go = () => {
    setBusy(true);
    setErr(null);
    login(email, password)
      .then(() => nav("/"))
      .catch((e) => setErr(e instanceof ApiError ? e.message : "Couldn't reach the API. Is it running?"))
      .finally(() => setBusy(false));
  };
  return (
    <AuthShell title="Welcome back" sub="Log in to get to your board.">
      <label>Email</label>
      <input value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@team.com" onKeyDown={(e) => e.key === "Enter" && go()} />
      <label>Password</label>
      <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} onKeyDown={(e) => e.key === "Enter" && go()} />
      {err && <p style={{ color: "var(--danger)" }}>{err}</p>}
      <button className="primary" style={{ width: "100%", marginTop: 16 }} onClick={go} disabled={busy}>
        {busy ? "Logging in…" : "Log in"}
      </button>
      <p className="muted">New here? <Link to="/register">Create an account</Link></p>
    </AuthShell>
  );
}

export function Register() {
  const { register } = useAuth();
  const nav = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [name, setName] = useState("");
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const go = () => {
    setBusy(true);
    setErr(null);
    register(email, password, name)
      .then(() => nav("/"))
      .catch((e) => setErr(e instanceof ApiError ? e.message : "Couldn't reach the API. Is it running?"))
      .finally(() => setBusy(false));
  };
  return (
    <AuthShell title="Create your account" sub="One board, zero setup. The first account becomes workspace admin.">
      <label>Name</label>
      <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Your name" onKeyDown={(e) => e.key === "Enter" && go()} />
      <label>Email</label>
      <input value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@team.com" onKeyDown={(e) => e.key === "Enter" && go()} />
      <label>Password (8+ characters)</label>
      <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} onKeyDown={(e) => e.key === "Enter" && go()} />
      {err && <p style={{ color: "var(--danger)" }}>{err}</p>}
      <button className="primary" style={{ width: "100%", marginTop: 16 }} onClick={go} disabled={busy}>
        {busy ? "Creating…" : "Create account"}
      </button>
      <p className="muted">Have an account? <Link to="/login">Log in</Link></p>
    </AuthShell>
  );
}

function ago(iso: string): string {
  const s = Math.floor((Date.now() - new Date(iso).getTime()) / 1000);
  if (s < 60) return "just now";
  if (s < 3600) return `${Math.floor(s / 60)}m ago`;
  if (s < 86400) return `${Math.floor(s / 3600)}h ago`;
  return new Date(iso).toLocaleDateString();
}

export function Overview() {
  const [data, setData] = useState<any>(null);
  const [overdue, setOverdue] = useState<Task[] | null>(null);
  const [open, setOpen] = useState<Task | null>(null);
  const [err, setErr] = useState(false);

  const load = () => {
    Dashboard.summary().then(setData).catch(() => setErr(true));
    Tasks.list({ overdue: "true" }).then((p) => setOverdue(p.content)).catch(() => setOverdue([]));
  };
  useEffect(load, []);

  if (err)
    return <div className="empty"><div className="glyph">!</div><h2>Couldn't load the overview</h2><p>The API may be down. Try again shortly.</p></div>;
  if (!data || !overdue)
    return (
      <div className="grid2">
        {[0, 1, 2, 3].map((i) => <div key={i} className="skeleton" style={{ height: 110 }} />)}
      </div>
    );
  const total = data.todo + data.inProgress + data.done;
  const donePct = total === 0 ? 0 : Math.round((data.done / total) * 100);
  return (
    <>
      <div className="grid2">
        <div className="card stat"><span className="muted">Backlog</span><b>{data.todo}</b><span className="sub muted">waiting to start</span></div>
        <div className="card stat"><span className="muted">In Progress</span><b>{data.inProgress}</b><span className="sub muted">being worked on</span></div>
        <div className="card stat"><span className="muted">Done</span><b>{data.done}</b><span className="sub muted">{donePct}% of all tasks</span></div>
        <div className="card stat" style={data.overdue > 0 ? { borderColor: "var(--danger)" } : undefined}>
          <span className="muted">Overdue</span>
          <b style={data.overdue > 0 ? { color: "var(--danger)" } : undefined}>{data.overdue}</b>
          <span className="sub muted">{data.overdue > 0 ? "Needs attention today." : "All clear."}</span>
        </div>
      </div>
      <div className="rows">
        <h2>Needs attention</h2>
        {overdue.length === 0 && <p className="muted">Nothing overdue. The board is healthy.</p>}
        {overdue.map((t) => (
          <div key={t.id} className="rowline" onClick={() => setOpen(t)}>
            <span className={`pri pri-${t.priority}`}>{t.priority}</span>
            <span style={{ fontWeight: 600 }}>{t.title}</span>
            <span style={{ marginLeft: "auto" }}><DuePill due={t.dueDate} status={t.status} /></span>
          </div>
        ))}
        <h2 style={{ marginTop: 26 }}>Recent activity</h2>
        {data.recentActivity.length === 0 && (
          <p className="muted">Nothing happened yet. Move a card on the board and it will show up here.</p>
        )}
        <ul className="timeline">
          {data.recentActivity.map((a: any, i: number) => (
            <li key={i}>
              <b>{a.eventType.replace(/_/g, " ")}</b>
              {a.oldValue && a.newValue && <span> — {a.oldValue} → {a.newValue}</span>}
              {a.eventType === "TASK_CREATED" && a.newValue && <span> — “{a.newValue}”</span>}
              <div className="faint">{ago(a.at)}</div>
            </li>
          ))}
        </ul>
      </div>
      {open && (
        <TaskPanel
          task={open}
          onClose={() => { setOpen(null); load(); }}
          onChanged={setOpen}
          onDeleted={() => { setOpen(null); load(); }}
        />
      )}
    </>
  );
}

const JOB_FILTERS = ["", "PENDING", "RETRYING", "SUCCESS", "PERMANENTLY_FAILED"];

export function JobsPage() {
  const [jobs, setJobs] = useState<any>(null);
  const [status, setStatus] = useState("");
  const [detail, setDetail] = useState<any>(null);
  const [denied, setDenied] = useState(false);
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setDetail(null);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);
  const load = () => {
    setJobs(null);
    Jobs.list(status)
      .then((p: any) => setJobs(p.content))
      .catch((e) => {
        if (e instanceof ApiError && e.status === 403) setDenied(true);
        else setJobs([]);
      });
  };
  useEffect(load, [status]);
  if (denied)
    return <div className="empty"><div className="glyph">!</div><h2>Admins only</h2><p>Scheduled jobs are visible to workspace admins.</p></div>;
  return (
    <>
      <div className="toolbar">
        <div className="chip-row">
          {JOB_FILTERS.map((s) => (
            <button key={s} className="fchip" aria-pressed={status === s} onClick={() => setStatus(s)}>
              {s === "" ? "All" : s.replace(/_/g, " ")}
            </button>
          ))}
        </div>
      </div>
      {!jobs && <div style={{ padding: 20 }}><div className="skeleton" style={{ height: 220 }} /></div>}
      {jobs && jobs.length === 0 && (
        <div className="empty">
          <div className="glyph">○</div>
          <h2>No scheduled jobs{status ? " with this status" : ""}</h2>
          <p>Jobs appear here when a task has a due date. Add one from the board.</p>
        </div>
      )}
      {jobs && jobs.length > 0 && (
        <table className="jobs">
          <thead><tr><th>Type</th><th>Status</th><th>Attempts</th><th>Next run</th><th>Last error</th><th></th></tr></thead>
          <tbody>
            {jobs.map((j: any) => (
              <tr key={j.id}>
                <td className="mono">{j.jobType}</td>
                <td className={`status-${j.status}`}>{j.status.replace(/_/g, " ")}</td>
                <td className="mono">{j.retryCount}/{j.maxRetries}</td>
                <td>{new Date(j.nextRunAt).toLocaleString()}</td>
                <td className="muted" style={{ maxWidth: 220, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                  {j.lastError ?? "—"}
                </td>
                <td>
                  <button className="small" onClick={() => Jobs.executions(j.id).then((p: any) => setDetail({ job: j, runs: p.content }))}>
                    Runs
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {detail && (
        <>
          <div className="scrim" onClick={() => setDetail(null)} />
          <div className="panel panel-enter">
            <div className="row" style={{ justifyContent: "space-between" }}>
              <h2>Job runs</h2>
              <button className="ghost" onClick={() => setDetail(null)}>Close <kbd>esc</kbd></button>
            </div>
            <p className="muted mono">{detail.job.id}</p>
            {detail.runs.length === 0 && <p className="muted">No attempts recorded yet — the poller hasn't picked it up.</p>}
            <ul className="timeline">
              {detail.runs.map((r: any) => (
                <li key={r.id}>
                  <b className={r.status === "SUCCESS" ? "status-SUCCESS" : "status-PERMANENTLY_FAILED"}>{r.status}</b>
                  <span className="muted"> — {ago(r.startedAt)}</span>
                  {r.errorMessage && <div style={{ color: "var(--danger)" }}>{r.errorMessage}</div>}
                </li>
              ))}
            </ul>
            {(detail.job.status === "PERMANENTLY_FAILED" || detail.job.status === "FAILED") && (
              <button className="primary" style={{ marginTop: 12 }} onClick={() => Jobs.retry(detail.job.id).then(() => { setDetail(null); load(); })}>
                Retry job
              </button>
            )}
          </div>
        </>
      )}
    </>
  );
}

export function Bell() {
  const [unread, setUnread] = useState(0);
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState<any[]>([]);
  const refresh = () => Notifications.unread().then((r) => setUnread(r.unread)).catch(() => {});
  useEffect(() => {
    refresh();
    const t = setInterval(refresh, 30000);
    return () => clearInterval(t);
  }, []);
  const show = () => {
    if (!open) Notifications.list().then((p: any) => setItems(p.content)).catch(() => {});
    setOpen(!open);
  };
  return (
    <div style={{ position: "relative" }}>
      <button onClick={show}>Notifications{unread > 0 ? ` (${unread})` : ""}</button>
      {open && (
        <div className="card" style={{ position: "absolute", right: 0, top: 44, width: 330, zIndex: 30, padding: 12 }}>
          <div className="row" style={{ justifyContent: "space-between" }}>
            <b>Notifications</b>
            <button className="small" onClick={() => Notifications.markAll().then(() => { setUnread(0); setItems([]); })}>
              Mark all read
            </button>
          </div>
          {items.length === 0 && <p className="muted">You're all caught up.</p>}
          {items.map((n: any) => (
            <div key={n.id} style={{ padding: "8px 0", borderBottom: "1px solid var(--line)", opacity: n.read ? 0.6 : 1 }}>
              <div><b>{n.title}</b></div>
              <div className="muted" style={{ fontSize: 12 }}>{n.body} · {ago(n.createdAt)}</div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
