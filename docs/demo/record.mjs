import { chromium } from "playwright";

const UI = "http://localhost:5173";
const EMAIL = "mahmoud@taskflow.local";
const PASS = "TaskFlow123";
const OUT = process.env.TF_VIDEO_DIR ?? ".";
const pause = (ms) => new Promise((r) => setTimeout(r, ms));

async function login(page) {
  await page.goto(`${UI}/login`, { waitUntil: "networkidle" });
  await page.getByPlaceholder("you@team.com").fill(EMAIL);
  await page.locator('input[type="password"]').fill(PASS);
  await pause(400);
  await page.getByRole("button", { name: "Log in" }).click();
  await page.getByPlaceholder(/Search tasks/).waitFor({ timeout: 15000 });
  await pause(800);
}

async function desktop() {
  const browser = await chromium.launch({ slowMo: 220 });
  const ctx = await browser.newContext({
    viewport: { width: 1280, height: 800 },
    recordVideo: { dir: OUT, size: { width: 1280, height: 800 } },
  });
  const page = await ctx.newPage();
  await login(page);

  // 1. quick-add a task with priority (unique title per take)
  const TITLE = `Demo ship ${new Date().toTimeString().slice(0, 5)}`;
  await page.locator(".quick-add input").fill(TITLE);
  await page.locator(".quick-add select").selectOption("HIGH");
  await pause(300);
  await page.locator(".quick-add button.primary").click();
  const card = page.locator(".task-card", { hasText: TITLE }).first();
  await card.waitFor({ timeout: 10000 });
  await pause(900);

  // 2. drag Backlog -> In Progress
  const src = await card.boundingBox();
  const destCol = page.locator(".column").nth(1);
  const dst = await destCol.boundingBox();
  await page.mouse.move(src.x + src.width / 2, src.y + src.height / 2);
  await page.mouse.down();
  await page.mouse.move(dst.x + dst.width / 2, dst.y + 200, { steps: 25 });
  await pause(400);
  await page.mouse.up();
  await pause(1200);

  // 3. open detail panel, set due in +2 min, tag, save
  await page.screenshot({ path: `${OUT}/dbg-board.png` });
  await card.locator(".title").click({ timeout: 8000 });
  await pause(1200);
  await page.screenshot({ path: `${OUT}/dbg-panel.png` });
  if (!(await page.locator(".panel").isVisible().catch(() => false))) {
    await card.locator(".title").click({ timeout: 8000, force: true });
    await pause(1200);
  }
  await page.getByText("Reminder", { exact: false }).first().waitFor({ timeout: 12000 });
  const due = new Date(Date.now() + 2 * 60 * 1000);
  const pad = (n) => String(n).padStart(2, "0");
  const stamp = `${due.getFullYear()}-${pad(due.getMonth() + 1)}-${pad(due.getDate())}T${pad(due.getHours())}:${pad(due.getMinutes())}`;
  await page.locator('input[type="datetime-local"]').fill(stamp);
  await page.getByPlaceholder("Add tag…").fill("demo");
  await page.getByRole("button", { name: "Add", exact: true }).last().click();
  await pause(400);
  await page.getByRole("button", { name: "Save task" }).click();
  await pause(1500);
  await page.getByText("Reminder job").waitFor({ timeout: 8000 });
  await pause(1500);

  // 4. close, overview
  await page.keyboard.press("Escape");
  await pause(600);
  await page.getByRole("link", { name: "Overview" }).click();
  await page.getByText("Needs attention").waitFor({ timeout: 8000 });
  await pause(1200);
  await page.mouse.wheel(0, 500);
  await pause(1200);

  // 5. jobs page, wait for live SUCCESS if it happens
  await page.getByRole("link", { name: "Jobs" }).click();
  await page.locator("table.jobs, .empty").first().waitFor({ timeout: 10000 });
  await pause(800);
  let fired = false;
  for (let i = 0; i < 10 && !fired; i++) {
    if (await page.getByText("SUCCESS", { exact: true }).first().isVisible().catch(() => false)) {
      fired = true;
      break;
    }
    await pause(15000);
    await page.reload({ waitUntil: "networkidle" }).catch(() => {});
  }
  const runsBtn = page.getByRole("button", { name: "Runs" }).first();
  if (await runsBtn.isVisible().catch(() => false)) {
    await runsBtn.click();
    await page.getByText("Job runs").waitFor({ timeout: 8000 });
    await pause(1500);
    await page.keyboard.press("Escape");
    await pause(500);
  }

  // 6. dark mode on the board
  await page.getByRole("link", { name: "Board" }).click();
  await page.getByPlaceholder(/Search tasks/).waitFor({ timeout: 10000 });
  await pause(600);
  await page.getByRole("button", { name: "Dark", exact: true }).click();
  await pause(1500);
  await page.mouse.wheel(0, 300);
  await pause(1000);

  await ctx.close();
  await browser.close();
  console.log("desktop done, job fired live:", fired);
}

async function mobile() {
  const browser = await chromium.launch({ slowMo: 220 });
  const ctx = await browser.newContext({
    viewport: { width: 390, height: 844 },
    recordVideo: { dir: OUT, size: { width: 390, height: 844 } },
  });
  const page = await ctx.newPage();
  await login(page);
  await pause(800);
  // swipeable columns: horizontal scroll
  await page.mouse.wheel(400, 0);
  await pause(1200);
  await page.mouse.wheel(400, 0);
  await pause(1200);
  await ctx.close();
  await browser.close();
  console.log("mobile done");
}

await desktop();
await mobile();
