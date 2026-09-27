import { useEffect, useMemo, useRef, useState } from "react";
import {
  DndContext,
  PointerSensor,
  useDraggable,
  useDroppable,
  useSensor,
  useSensors,
  type DragEndEvent,
} from "@dnd-kit/core";
import { ApiError, Tasks, type Priority, type Task, type TaskStatus } from "./api";
import { TaskPanel } from "./TaskPanel";

const COLUMNS: { id: TaskStatus; title: string }[] = [
  { id: "TODO", title: "Backlog" },
  { id: "IN_PROGRESS", title: "In Progress" },
  { id: "DONE", title: "Done" },
];

const NEXT: Record<TaskStatus, TaskStatus | null> = {
  TODO: "IN_PROGRESS",
  IN_PROGRESS: "DONE",
  DONE: null,
};
const PREV: Record<TaskStatus, TaskStatus | null> = {
  TODO: null,
  IN_PROGRESS: "TODO",
  DONE: "IN_PROGRESS",
};

function dueState(due: string | null, status: TaskStatus): "over" | "soon" | "ok" | null {
  if (!due || status === "DONE") return null;
  const ms = new Date(due).getTime() - Date.now();
  if (ms < 0) return "over";
  if (ms < 48 * 3600 * 1000) return "soon";
  return "ok";
}

export function DuePill({ due, status }: { due: string | null; status: TaskStatus }) {
  const s = dueState(due, status);
  if (!due || !s) return null;
  const label =
    s === "over"
      ? `Overdue · ${new Date(due).toLocaleDateString()}`
      : `Due ${new Date(due).toLocaleDateString()}`;
  return <span className={`due${s === "over" ? " due-over" : s === "soon" ? " due-soon" : ""}`}>{label}</span>;
}

function Card({
  task,
  onOpen,
  onMove,
}: {
  task: Task;
  onOpen: (t: Task) => void;
  onMove: (t: Task, s: TaskStatus) => void;
}) {
  const { attributes, listeners, setNodeRef, transform, isDragging } = useDraggable({ id: task.id });
  const over = dueState(task.dueDate, task.status) === "over";
  return (
    <div
      ref={setNodeRef}
      {...attributes}
      {...listeners}
      onClick={() => onOpen(task)}
      className={`task-card${isDragging ? " dragging" : ""}${over ? " overdue" : ""}`}
      style={transform ? { transform: `translate(${transform.x}px, ${transform.y}px)` } : undefined}
    >
      <div className="quick-actions" onClick={(e) => e.stopPropagation()}>
        {PREV[task.status] && (
          <button title="Move back" onClick={() => onMove(task, PREV[task.status]!)}>←</button>
        )}
        {NEXT[task.status] && (
          <button title="Move forward" onClick={() => onMove(task, NEXT[task.status]!)}>→</button>
        )}
      </div>
      <span className={`pri pri-${task.priority}`}>{task.priority}</span>
      <div className="title">{task.title}</div>
      <div className="meta">
        <DuePill due={task.dueDate} status={task.status} />
        {task.tags.slice(0, 3).map((t) => (
          <span key={t} className="tag">{t}</span>
        ))}
        {task.tags.length > 3 && <span className="faint">+{task.tags.length - 3}</span>}
        <span style={{ marginLeft: "auto" }} title={task.assigneeId ? "Assigned" : "Unassigned"}>
          <span className="avatar" style={!task.assigneeId ? { background: "var(--raised-2)", color: "var(--ink-faint)" } : undefined}>
            {(task.assigneeId ?? "?").slice(0, 1).toUpperCase()}
          </span>
        </span>
      </div>
    </div>
  );
}

function Column({
  id,
  title,
  tasks,
  onOpen,
  onMove,
  children,
}: {
  id: TaskStatus;
  title: string;
  tasks: Task[];
  onOpen: (t: Task) => void;
  onMove: (t: Task, s: TaskStatus) => void;
  children?: React.ReactNode;
}) {
  const { setNodeRef, isOver } = useDroppable({ id });
  return (
    <div ref={setNodeRef} className={`column${isOver ? " drop-target" : ""}`}>
      <div className="column-head">
        <h3>{title}</h3>
        <span className="count-badge">{tasks.length}</span>
      </div>
      {children}
      {tasks.map((t) => (
        <Card key={t.id} task={t} onOpen={onOpen} onMove={onMove} />
      ))}
      {tasks.length === 0 && !children && (
        <div className="empty" style={{ padding: "20px 10px" }}>
          <div className="glyph">○</div>
          Nothing here. Drag a card over, or add one below.
        </div>
      )}
    </div>
  );
}

export function Board() {
  const [tasks, setTasks] = useState<Task[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [conflict, setConflict] = useState<string | null>(null);
  const [open, setOpen] = useState<Task | null>(null);
  const [quickAdd, setQuickAdd] = useState("");
  const [quickPri, setQuickPri] = useState<Priority>("MEDIUM");
  const [filter, setFilter] = useState("");
  const [priFilter, setPriFilter] = useState<Priority | null>(null);
  const [tagFilter, setTagFilter] = useState<string | null>(null);
  const [overdueOnly, setOverdueOnly] = useState(false);
  const searchRef = useRef<HTMLInputElement>(null);
  const addRef = useRef<HTMLInputElement>(null);
  // Clicks must open the card: only start a drag after real movement,
  // otherwise dnd-kit swallows the click as a zero-distance drag.
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } })
  );

  const load = () => {
    setError(null);
    Tasks.list()
      .then((p) => setTasks(p.content))
      .catch(() => setError("The API isn't answering. Check it's running, then retry."));
  };
  useEffect(load, []);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const typing = /INPUT|TEXTAREA|SELECT/.test((e.target as HTMLElement)?.tagName ?? "");
      if (e.key === "/" && !typing) {
        e.preventDefault();
        searchRef.current?.focus();
      }
      if ((e.key === "n" || e.key === "N") && !typing) {
        e.preventDefault();
        addRef.current?.focus();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  const move = async (task: Task, dest: TaskStatus) => {
    if (dest === task.status) return;
    setTasks((prev) => prev!.map((t) => (t.id === task.id ? { ...t, status: dest } : t)));
    try {
      const updated = await Tasks.move(task.id, dest, task.version);
      setTasks((prev) => prev!.map((t) => (t.id === task.id ? updated : t)));
      setConflict(null);
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setConflict("Someone else edited that task — reloaded the latest board. Try the move again.");
        load();
      } else {
        setTasks((prev) => prev!.map((t) => (t.id === task.id ? task : t)));
      }
    }
  };

  const onDragEnd = (e: DragEndEvent) => {
    const task = tasks?.find((t) => t.id === e.active.id);
    const dest = e.over?.id as TaskStatus | undefined;
    if (task && dest) move(task, dest);
  };

  const add = async () => {
    const title = quickAdd.trim();
    if (!title) return;
    setQuickAdd("");
    try {
      const created = await Tasks.create({ title, priority: quickPri });
      setTasks((prev) => [created, ...(prev ?? [])]);
    } catch {
      setQuickAdd(title);
    }
  };

  const allTags = useMemo(
    () => [...new Set((tasks ?? []).flatMap((t) => t.tags))].sort(),
    [tasks]
  );

  if (error)
    return (
      <div className="empty">
        <div className="glyph">!</div>
        <h2>Couldn't reach your board</h2>
        <p>{error}</p>
        <button className="primary" onClick={load}>Retry</button>
      </div>
    );
  if (!tasks)
    return (
      <div className="board">
        {COLUMNS.map((c) => (
          <div key={c.id} className="column">
            <div className="column-head"><h3>{c.title}</h3></div>
            {[0, 1, 2].map((i) => (
              <div key={i} className="skeleton" style={{ height: 92, marginBottom: 8 }} />
            ))}
          </div>
        ))}
      </div>
    );

  const visible = (s: TaskStatus) =>
    tasks.filter(
      (t) =>
        t.status === s &&
        (!filter || `${t.title} ${t.description ?? ""}`.toLowerCase().includes(filter.toLowerCase())) &&
        (!priFilter || t.priority === priFilter) &&
        (!tagFilter || t.tags.includes(tagFilter)) &&
        (!overdueOnly || dueState(t.dueDate, t.status) === "over")
    );

  return (
    <>
      <div className="toolbar">
        <input
          ref={searchRef}
          type="search"
          placeholder="Search tasks…  ( / )"
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
        />
        <div className="chip-row">
          {(["HIGH", "MEDIUM", "LOW"] as Priority[]).map((p) => (
            <button
              key={p}
              className="fchip"
              aria-pressed={priFilter === p}
              onClick={() => setPriFilter(priFilter === p ? null : p)}
            >
              {p}
            </button>
          ))}
          <button
            className="fchip"
            aria-pressed={overdueOnly}
            onClick={() => setOverdueOnly(!overdueOnly)}
          >
            Overdue only
          </button>
        </div>
        {allTags.length > 0 && (
          <select
            value={tagFilter ?? ""}
            onChange={(e) => setTagFilter(e.target.value || null)}
            style={{ maxWidth: 160 }}
          >
            <option value="">All tags</option>
            {allTags.map((t) => (
              <option key={t} value={t}>#{t}</option>
            ))}
          </select>
        )}
      </div>
      {conflict && <div className="banner">{conflict}</div>}
      <DndContext sensors={sensors} onDragEnd={onDragEnd}>
        <div className="board">
          {COLUMNS.map((c) => (
            <Column key={c.id} id={c.id} title={c.title} tasks={visible(c.id)} onOpen={setOpen} onMove={move}>
              {c.id === "TODO" && (
                <div className="quick-add">
                  <input
                    ref={addRef}
                    placeholder="New task — Enter to add  ( N )"
                    value={quickAdd}
                    onChange={(e) => setQuickAdd(e.target.value)}
                    onKeyDown={(e) => e.key === "Enter" && add()}
                  />
                  <select value={quickPri} onChange={(e) => setQuickPri(e.target.value as Priority)} title="Priority">
                    <option value="LOW">Low</option>
                    <option value="MEDIUM">Med</option>
                    <option value="HIGH">High</option>
                  </select>
                  <button className="primary" onClick={add}>Add</button>
                </div>
              )}
            </Column>
          ))}
        </div>
      </DndContext>
      {open && (
        <TaskPanel
          task={open}
          onClose={() => {
            setOpen(null);
            load();
          }}
          onChanged={(t) => {
            setTasks((prev) => prev!.map((x) => (x.id === t.id ? t : x)));
            setOpen(t);
          }}
          onDeleted={(id) => {
            setTasks((prev) => prev!.filter((x) => x.id !== id));
            setOpen(null);
          }}
        />
      )}
    </>
  );
}
