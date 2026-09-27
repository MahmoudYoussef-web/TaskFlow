import { useEffect, useState } from "react";
import { ApiError, Tasks, type Reminder, type Task } from "./api";
import { DuePill } from "./Board";

interface Entry {
  id: string;
  eventType: string;
  actorId: string | null;
  oldValue: string | null;
  newValue: string | null;
  createdAt: string;
}

function ago(iso: string): string {
  const s = Math.floor((Date.now() - new Date(iso).getTime()) / 1000);
  if (s < 60) return "just now";
  if (s < 3600) return `${Math.floor(s / 60)}m ago`;
  if (s < 86400) return `${Math.floor(s / 3600)}h ago`;
  return new Date(iso).toLocaleDateString();
}

const EVENT_LABEL: Record<string, string> = {
  TASK_CREATED: "Created",
  STATUS_CHANGED: "Moved",
  ASSIGNED: "Assigned",
  REMINDER_FAILED: "Reminder failed",
};

function ReminderBox({ taskId, refreshKey }: { taskId: string; refreshKey: string }) {
  const [rem, setRem] = useState<Reminder | null>(null);
  useEffect(() => {
    setRem(null);
    Tasks.reminder(taskId).then(setRem).catch(() => setRem(null));
  }, [taskId, refreshKey]);
  if (!rem) return null;
  if (!rem.scheduled)
    return (
      <div className="jobbox muted">
        No reminder scheduled — set a due date and the scheduler picks it up automatically.
      </div>
    );
  if (!rem.job) return <div className="jobbox muted">Scheduling…</div>;
  const j = rem.job;
  const used = Math.min(j.retryCount + (j.status === "SUCCESS" ? 1 : 0), j.maxRetries);
  return (
    <div className="jobbox">
      <div className="row" style={{ justifyContent: "space-between" }}>
        <span>Reminder job</span>
        <span className={`st st-${j.status}`}>{j.status.replace(/_/g, " ")}</span>
      </div>
      <div className="attempt-bar" title={`${used}/${j.maxRetries} attempts used`}>
        {Array.from({ length: j.maxRetries }).map((_, i) => (
          <i
            key={i}
            className={
              j.status === "SUCCESS" ? "done"
              : j.status === "PERMANENTLY_FAILED" ? "dead"
              : i < j.retryCount ? "pending" : ""
            }
          />
        ))}
      </div>
      <div className="muted" style={{ marginTop: 6, fontSize: 12 }}>
        {j.status === "SUCCESS" && j.lastAttempt && (
          <>Delivered {ago(j.lastAttempt.startedAt)}.</>
        )}
        {(j.status === "PENDING" || j.status === "RETRYING") && (
          <>Next attempt {new Date(j.nextRunAt).toLocaleString()}.</>
        )}
        {j.lastError && (
          <div style={{ color: "var(--danger)", marginTop: 4 }}>Last error: {j.lastError}</div>
        )}
      </div>
    </div>
  );
}

export function TaskPanel({
  task,
  onClose,
  onChanged,
  onDeleted,
}: {
  task: Task;
  onClose: () => void;
  onChanged: (t: Task) => void;
  onDeleted: (id: string) => void;
}) {
  const [title, setTitle] = useState(task.title);
  const [description, setDescription] = useState(task.description ?? "");
  const [priority, setPriority] = useState(task.priority);
  const [dueDate, setDueDate] = useState(task.dueDate?.slice(0, 16) ?? "");
  const [tags, setTags] = useState<string[]>([...task.tags]);
  const [tagInput, setTagInput] = useState("");
  const [history, setHistory] = useState<Entry[] | null>(null);
  const [saving, setSaving] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    Tasks.history(task.id)
      .then((p) => setHistory(p.content))
      .catch(() => setHistory([]));
  }, [task.id]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
      if ((e.ctrlKey || e.metaKey) && e.key === "s") {
        e.preventDefault();
        save();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  });

  const addTag = () => {
    const t = tagInput.trim().toLowerCase().replace(/\s+/g, "-");
    if (t && !tags.includes(t)) setTags([...tags, t]);
    setTagInput("");
  };

  const save = async () => {
    setSaving(true);
    setErr(null);
    try {
      const updated = await Tasks.update(task.id, {
        version: task.version,
        title,
        description,
        priority,
        tags,
        ...(dueDate
          ? { dueDate: new Date(dueDate).toISOString() }
          : task.dueDate
            ? { clearDueDate: true }
            : {}),
      });
      onChanged(updated);
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) {
        setErr("Someone else edited this task. Close and reopen to get the latest version.");
      } else {
        setErr(e instanceof ApiError ? e.message : "Save failed.");
      }
    } finally {
      setSaving(false);
    }
  };

  const remove = async () => {
    if (!confirm("Delete this task? Its reminder job goes with it.")) return;
    await Tasks.remove(task.id);
    onDeleted(task.id);
  };

  return (
    <>
      <div className="scrim" onClick={onClose} />
      <div className="panel panel-enter">
        <div className="row" style={{ justifyContent: "space-between" }}>
          <DuePill due={task.dueDate} status={task.status} />
          <button className="ghost" onClick={onClose}>Close <kbd>esc</kbd></button>
        </div>
        <div style={{ marginTop: 6 }}>
          <input
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            style={{ fontSize: 18, fontWeight: 650 }}
          />
        </div>
        <div>
          <label>Description</label>
          <textarea rows={4} value={description} onChange={(e) => setDescription(e.target.value)} placeholder="What does done look like?" />
        </div>
        <div className="meta-grid">
          <div>
            <label>Priority</label>
            <select value={priority} onChange={(e) => setPriority(e.target.value as Task["priority"])}>
              <option value="LOW">Low</option>
              <option value="MEDIUM">Medium</option>
              <option value="HIGH">High</option>
            </select>
          </div>
          <div>
            <label>Status</label>
            <select
              value={task.status}
              onChange={async (e) => {
                try {
                  onChanged(await Tasks.move(task.id, e.target.value as Task["status"], task.version));
                } catch {
                  setErr("Couldn't move it — someone else may have edited it. Reopen the panel.");
                }
              }}
            >
              <option value="TODO">Backlog</option>
              <option value="IN_PROGRESS">In Progress</option>
              <option value="DONE">Done</option>
            </select>
          </div>
        </div>
        <div>
          <label>Due date</label>
          <div className="row">
            <input type="datetime-local" value={dueDate} onChange={(e) => setDueDate(e.target.value)} />
            {dueDate && <button className="small" onClick={() => setDueDate("")}>Clear</button>}
          </div>
        </div>
        <div>
          <label>Tags</label>
          <div className="row" style={{ flexWrap: "wrap" }}>
            {tags.map((t) => (
              <span key={t} className="tag">
                #{t}
                <button onClick={() => setTags(tags.filter((x) => x !== t))} title="Remove">×</button>
              </span>
            ))}
          </div>
          <div className="row" style={{ marginTop: 6 }}>
            <input
              placeholder="Add tag…"
              value={tagInput}
              onChange={(e) => setTagInput(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && addTag()}
            />
            <button className="small" onClick={addTag}>Add</button>
          </div>
        </div>
        <h3>Reminder</h3>
        <ReminderBox taskId={task.id} refreshKey={task.updatedAt} />
        {err && <p style={{ color: "var(--danger)" }}>{err}</p>}
        <div className="row" style={{ marginTop: 14 }}>
          <button className="primary" onClick={save} disabled={saving}>
            {saving ? "Saving…" : "Save task"}
          </button>
          <button className="danger" onClick={remove}>Delete</button>
          <span className="faint" style={{ marginLeft: "auto", fontSize: 12 }}><kbd>Ctrl</kbd>+<kbd>S</kbd> to save</span>
        </div>
        <h3>Activity</h3>
        {!history && <div className="skeleton" style={{ height: 60, marginTop: 8 }} />}
        {history && history.length === 0 && <p className="muted">No changes recorded yet.</p>}
        {history && history.length > 0 && (
          <ul className="timeline">
            {history.map((h) => (
              <li key={h.id}>
                <b>{EVENT_LABEL[h.eventType] ?? h.eventType}</b>
                {h.oldValue && h.newValue && <span> — {h.oldValue} → {h.newValue}</span>}
                {h.eventType === "TASK_CREATED" && h.newValue && <span> — “{h.newValue}”</span>}
                <div className="faint" title={new Date(h.createdAt).toLocaleString()}>{ago(h.createdAt)}</div>
              </li>
            ))}
          </ul>
        )}
        <p className="faint mono" style={{ marginTop: 18 }}>
          v{task.version} · updated {ago(task.updatedAt)}
        </p>
      </div>
    </>
  );
}
