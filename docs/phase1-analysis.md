# Phase 1 – Legacy Analysis of `SVFRMS.java`

All line numbers refer to the original [src/main/java/org/example/SVFRMS.java](../src/main/java/org/example/SVFRMS.java) (393 lines, commit `2aefe14`).

---

## a) What the system does

A single class simulates a vertical farm for one "day":

1. **Four chamber threads** (`CH1 Tomato`, `CH2 Strawberry`, `CH3 Lettuce`, `CH4 Chili`) each run 20 iterations. Each iteration draws five random sensor readings (temperature, humidity, soil, light, nutrient) and adds a fixed per-iteration resource cost (+2 L water, +1 kWh energy).
2. **`processSensor`** stores each reading in the chamber's `ChamberState` and compares it with **hard-coded, per-crop thresholds**. When a reading is out of range it:
   - records the alert in the chamber summary,
   - adds it to the global alert list, increments counters and prints `[CHx][Crop][ALERT] ...`,
   - for some rules, adds a water/energy cost,
   - for some rules, puts a device request (`"CH1|Cooling Fan"`) into a **bounded control queue** (capacity 5).
3. **One control thread** takes requests off the queue and "activates" the device. The device changes the chamber's state (for example, the fan lowers the temperature by 5 °C, never below 18 °C), updates the chamber summary and prints a green `[CHx][CONTROL] ...` line.
4. After the chambers finish, `main` stops the control thread. It then starts **two threads that race**: one archives the alerts and one prints the daily report. Finally it writes `archive/daily_report.txt`, which holds the totals, the report log, a summary per chamber and the archived alerts.

### Main components (all inside the one class)

| Component | Lines | Responsibility |
|---|---|---|
| Global state | 7–31 | 5 lists, 2 maps, 4 counters, 4 lock objects, `Random`, the queue size, the run flag – all `public static` and mutable |
| Domain model | 33–46 | `ChamberState` (current readings) and `ChamberSummary` (alert/action counts) |
| Lazy lookup | 48–60 | `getChamberState` / `getChamberSummary` |
| Event hub | 62–88 | `handleEvent(type, …)`: six unrelated operations chosen by a string |
| Rule engine | 141–205 | `processSensor`: a sensor × crop `if/else` matrix |
| Device control | 90–129 | `activateControl`: an `if/else` chain over device names |
| Control queue | 131–139, 251–273 | hand-written bounded buffer (`wait/notifyAll`) and the `ControlThread` consumer |
| Simulation | 275–295 | `ChamberThread` sensor generator |
| Reporting | 207–241, 309–347 | console report, alert archiving, file report |
| Dead code | 243–249, 297–307 | `irrigationCycle`, `feedingCycle`, `showAlerts`, `showLogs` – never called |
| Bootstrap | 349–392 | creates and starts all threads |

---

## b) Code smells and SOLID violations

| # | Smell | Where | Why it matters | SOLID (spec 4.2) |
|---|---|---|---|---|
| S1 | **God class**: one class holds the model, rules, devices, queue, threads, reporting and file I/O | whole file | A change to any concern means editing and retesting everything | **SRP (i)** |
| S2 | **Global `public static` mutable state** | 10–31 | Any code can change any field, so coupling is hidden and nothing can be tested in isolation. It is also the root cause of most concurrency bugs | SRP, **DIP (v)** |
| S3 | **Stringly-typed `handleEvent` switch**: `"LOG"`, `"CREATE_ALERT"`, `"TRACK_ACTION"`… | 62–88 | Typos fail silently. Callers pass dummy `""` for arguments the branch ignores (95, 142, 148). One entry point does logging, alert storage, statistics and console output. `ALERT_LOG` (66–68) is never used and duplicates the printing at 76 | **SRP (i)**, **OCP (ii)**, **ISP (iv)**: one wide "interface" forces every caller to depend on all six behaviours |
| S4 | **Crop `if/else` chains** | 147–170, 174–181, 185–187 | A new crop means editing `processSensor` in up to three places. The same alert block (`TRACK_ALERT` + `CREATE_ALERT`, sometimes plus a cost and a control request) is copy-pasted **19 times**: 17 in the crop chains and 2 for light and nutrient (191–203). The crop name is re-typed inside each message string | **OCP (ii)**, DRY |
| S5 | **Hard-coded thresholds / magic numbers** | 148–202 (thresholds), 34–38 (initial state), 94–124 (device deltas and clamps), 285–290 (iterations, random ranges), 15 (queue size), 148–186 & 291–292 (costs) | Crop data is mixed into control flow. You cannot list "the Tomato profile" without reading 60 lines | OCP |
| S6 | **`activateControl` device chain** | 90–129 | A new device (Heating Unit, Ventilation, Shade) means editing this method. Each branch repeats the same three `handleEvent` calls | **OCP (ii)**, SRP |
| S7 | **Stringly-typed queue messages** `chamber + "\|" + control`, parsed again with `split("\\|")` | 136, 269 | Producer and consumer are coupled through a string format, and it breaks if a name ever contains `\|` | – |
| S8 | **Hand-written bounded buffer** re-implementing `BlockingQueue` | 131–139, 256–267 | More code to get wrong. It swallows `InterruptedException` (`catch(Exception e){}` at 134, 258) | – |
| S9 | **`extends Thread`** for `ControlThread` / `ChamberThread` | 251, 275 | Ties *what* runs to *how* it runs, so an `ExecutorService` cannot be used. `ControlThread` swallows `interrupt()` and loops `while(true)`, which breaks the cancellation contract callers expect from a `Thread` | **LSP (iii)** – see note below |
| S10 | **Duplicated / misleading counters**: `totalAlerts` is really "active alerts", `totalAlertsGenerated` is the real total | 23–24, 74–75, 228 | Hard to understand and easy to break | – |
| S11 | **Duplicated report formatting** in the console and file versions | 210–214 vs 314–319 | Two places to keep in sync. Computation is mixed with output | SRP |
| S12 | **Direct use of concrete platform classes**: `System.out`, `new Date()`, `Files`, a static `Random` | throughout | Output is not reproducible or testable, and readings cannot be replaced by real sensors | **DIP (v)** |
| S13 | **Dead code and dead locks**: `irrigationCycle`, `feedingCycle`, `showAlerts`, `showLogs`, `waterPumpLock`, `nutrientLock`; `sensorLogs` is written (64) but never read; commented-out sleeps. (`controlThreadRunning` is *not* dead: it is the control thread's shutdown signal, at 257, 261 and 368) | 243–249, 297–307, 28–29, 224, 385 | Noise that hides intent | – |
| S14 | **Error handling**: catch-all `Exception` with only a message print; crashed threads ignored | 344, 134, 258 | A chamber thread can die and the report is still written as if nothing happened (see C1) | – |
| S15 | **`main` does everything**: wiring, lifecycle, two anonymous `Runnable`s | 349–392 | No composition root; the crop/chamber list is hard-coded here too | SRP, OCP |

### Gaps between the code and the specification

These are documented as limitations. They are **not** implemented, because spec section 4 says to preserve existing behaviour.

| Spec | Specification says | Code does |
|---|---|---|
| 3.3 Devices | Water Pump, Cooling Fan, **Heating Unit**, Grow Light, Nutrient Injector, **Ventilation System**, **Shade Controller** | Has the pump, fan, light and injector, plus **Humidifier** and **Dehumidifier**, which the spec does not list. The three devices in bold are missing |
| 3.1 Crop profiles | Every crop has temperature, humidity, soil and light ranges (min and max) | Only Tomato has every rule; humidity only for Tomato/Strawberry; soil only a minimum, and only for Tomato; light/nutrient use one global threshold |
| 3.4 Alerts | Severity INFO / WARNING / CRITICAL; Sensor Failure Alert | No severity; no sensor-failure detection |
| 3.5 Reports | Daily, Weekly, Water, Energy, Nutrient reports | One combined daily report |

**Note on LSP (4.2 iii).** The legacy code has no domain inheritance hierarchy at all, so there is no classic "Square extends Rectangle" violation. The only inheritance is `extends Thread`, and the defensible LSP point is S9: a `Thread` subtype that ignores `interrupt()` does not behave as callers of `Thread` expect. LSP matters more for the *new* design. For example, a single `Device` base class with `increase()`/`decrease()` would force a Shade Controller or Cooling Fan to implement a method that makes no sense for it. We avoid that with small interfaces (Phase 2).

---

## c) Concurrency analysis

### Threads and shared data

| Shared data | Written by | Read by | Protection in legacy code |
|---|---|---|---|
| `chamberStates`, `chamberSummaries` (`HashMap`) | 4 chamber threads (put), control thread | all | **none** |
| `alerts`, `sensorLogs` (`ArrayList`) | 4 chamber threads | archive thread, file report | **none** on write; `alertLock` on archive only |
| `totalWaterUsage`, `totalEnergyUsage`, `totalAlerts`, `totalAlertsGenerated` (`int`) | 4 chamber threads (`+=`, `++`); archive thread (`= 0`) | report threads | **none** on write |
| `ChamberState` fields (`double`) | owning chamber thread (sensor value) and control thread (read-modify-write) | both | **none** |
| `ChamberSummary` fields | owning chamber thread (`alerts`, `lastAlert`); control thread (`mitigationActions`, `lastAction`) | file report | **none** |
| `controlQueue` | chamber threads, control thread, main | – | `synchronized(controlQueue)` + `wait/notifyAll` ✔ |
| `reports`, `archivedAlerts` | report and archive threads | file report | `reportLock` ✔ (file report reads after `join`, so this is safe) |
| `random` | 4 chamber threads | – | `java.util.Random` is internally thread-safe (CAS) |

### Issues

| # | Issue | Where | Effect | Observed? |
|---|---|---|---|---|
| **C1** | **Check-then-act on a non-thread-safe `HashMap`**. `containsKey` → `put` → `get` is not atomic, and concurrent `put`s from different chamber threads can corrupt the table during a resize | 48–60 | `get` returns `null` and the chamber thread dies with an NPE at **146** (`state.temperature`) or **80** (`summary.alerts++`). That chamber silently loses its remaining readings, and an entire chamber can be missing from the report | **Yes**: 123/300 runs; for example, CH2 was missing from the very first run |
| **C2** | **Unsynchronized `ArrayList.add`** from 4 threads | 64, 73 | Adds are lost (size overwritten); `ArrayIndexOutOfBoundsException` is possible | **Yes**: `Generated Alerts` ≠ number of archived alerts in 67/300 runs |
| **C3** | **Non-atomic counters**: `x += n` is read-modify-write | 74–75, 148–186, 291–292 | Lost updates to water, energy and alert totals | **Yes**: water wrong in 27/300 runs, energy in 24/300 |
| **C4** | **`ChamberState` shared between a chamber thread and the control thread** with no lock or `volatile` | 146–198 vs 94–124 | The control thread's `temperature - 5` can use a stale value or overwrite a newer reading (lost update). Nothing guarantees visibility. Printed "reduced to X" values depend on timing. (`double` writes may tear on 32-bit JVMs, JLS §17.7 – theoretical on 64-bit HotSpot) | Yes: control-log values differ between identical seeded runs |
| **C5** | **`ChamberSummary` accessed by two threads without synchronization**. Each field has only one writer, so increments are not lost here; the real damage comes through C1 (the summary disappears or is replaced) | 79–86 | Visibility only; counts are wrong as a side effect of C1/C2 | Yes (indirectly) |
| **C6** | **Ordering race: `archiveAlerts` vs `generateDailyReport`**. Both threads are started back-to-back (384–386) and whichever grabs `alertLock` first wins. Locks give mutual exclusion, not ordering | 373–389 | Report first → console prints `Active Alerts: N` (non-zero) and the file's report log order flips (`Daily Report` before `Archived N alerts`) | **Yes**: 7/300 runs (19/500 across two batches) |
| **C7** | **Inconsistent locking discipline**. `alertLock` guards `alerts` in `archiveAlerts`, but the writer (`handleEvent`, 73) never takes it. `generateDailyReport` reads counters under `alertLock`, but their writers never lock | 73, 208, 223 | The locks give a false sense of safety; correctness depends only on the `join()` calls in `main` | by inspection |
| **C8** | **Unused locks** `waterPumpLock`, `nutrientLock` | 28–29 | Dead code; suggests synchronization was planned and never done | by inspection |
| **C9** | **Swallowed `InterruptedException`** in both `wait()` loops | 134, 258 | Threads cannot be cancelled; `ControlThread` can only stop through the flag | by inspection |
| **C10** | **Liveness hazard (hang, not deadlock)**. If the control thread dies (for example, an NPE from C1 inside `activateControl` at 91), producers block forever on a full queue (`wait()` at 134) and `main` blocks on `join()` | 131–139, 251–273 | Program never terminates | Not seen in 450 runs, but reachable |
| **C11** | **Silent thread death**. No `UncaughtExceptionHandler`; `main` writes a "successful" report regardless | 362–391 | Corrupt report with no error status | Yes (every C1 run) |
| **C12** | **One shared `Random`**. Thread-safe, but the order in which chambers draw numbers is decided by the scheduler, so a fixed seed still does not reproduce a run | 31, 286–290 | Non-reproducible output | Yes |

### Deadlock assessment (honest)

**No deadlock is reachable in the code as written.** Lock inventory:

| Lock | Acquired at | Nested inside / around |
|---|---|---|
| `controlQueue` monitor | 132, 256, 367 | never nested with another lock; `activateControl` runs *after* the lock is released (270) |
| `alertLock` → `reportLock` | `generateDailyReport` 208→209, `archiveAlerts` 223→230 | **same order in both methods** |
| `waterPumpLock`, `nutrientLock` | never | – |

A deadlock needs a cycle in the lock-wait graph. The only nesting is `alertLock` then `reportLock`, in the same order everywhere, so no cycle can form. Uncommenting the `Thread.sleep(100)` at 224 only widens the window and still cannot deadlock. The code is **one edit away** from the classic deadlock, though: if either method took `reportLock` first, the two report threads could each hold one lock and wait for the other. The refactor will remove the nesting entirely, or document a single global order if nesting is still needed.

The real liveness risk is **C10**, a hang caused by a dead consumer, rather than a lock-ordering deadlock.

### Evidence (300 runs of the unmodified concurrent `main`)

```
Runs with no detected defect: 92 / 300
 123 thread crashed: java.lang.NullPointerException     (C1 – at lines 146 and 80)
  67 Generated Alerts N != archived N                   (C2)
  58 chamber Alerts sum N != archived N                 (C1/C2)
  27 water N != expected N (lost update)                (C3)
  24 energy N != expected N (lost update)               (C3)
   7 report printed BEFORE archive (ordering race)      (C6)
```
The lost-update checks are skipped when a thread crashed, so these counts **undercount**. See [baseline/check_invariants.py](../baseline/check_invariants.py) for how each invariant is derived from the archived alert list.

---

## d) Baseline

**Problem.** A fixed seed alone does not make the legacy run reproducible (C12), and the legacy run is often corrupted anyway (C1–C3).

**Approach.** [baseline/src/org/example/BaselineHarness.java](../baseline/src/org/example/BaselineHarness.java) runs the *unmodified* legacy classes (compiled from `src/`, which is not edited). It:
- sets `SVFRMS.random = new Random(42)`;
- runs the same `ChamberThread`s **one at a time** (start + join) in the legacy order CH1→CH4, so draws are always consumed in the same order, with the legacy `ControlThread` running concurrently;
- then calls `archiveAlerts()`, `generateDailyReport()` and `saveArchiveReport()`, using archive-first, the order seen in ~96% of legacy runs (481/500).

[baseline/normalize.py](../baseline/normalize.py) strips the timestamp and the absolute path. It turns the control lines (their position and numbers depend on how far the control thread lags, C4) into **per-chamber, per-device counts**. Everything else is compared exactly: every alert line in order, the totals, the chamber summary and the archived alert list.

**Run it:** `JAVA_HOME=<jdk25> ./baseline/run_baseline.sh 42 300`

**Captured** in [baseline/expected/](../baseline/expected/):
- `seed42-normalized.txt` – the file the refactor must reproduce (verified identical across two runs)
- `seed42-stdout.txt`, `seed42-daily_report.txt` – raw output
- `legacy-stress.txt` – the defect census above

Seed-42 headline: `Generated Alerts: 130, Water 940 L, Energy 480 kWh`; CH1 43 alerts / 34 actions, CH2 38/31, CH3 26/23, CH4 23/16.

In Phase 3 the refactored app gets the same seed hook, with sensor readings drawn in the same order, so its normalized output can be `diff`ed against `seed42-normalized.txt`.

---

## Legacy behaviour quirks – agreed decisions

Rule: **preserve behaviour (spec section 4), fix only concurrency defects and dead code.** Every quirk below stays as it is and is listed in the README as a known limitation. Because crop rules become data, each quirk can later be changed by editing one profile, with no code change.

| # | Quirk | Where | Decision |
|---|---|---|---|
| Q1 | Humidity rules exist only for Tomato and Strawberry; the soil rule only for Tomato | 174–187 | Keep |
| Q2 | Soil moisture has no upper-bound check | 185–187 | Keep |
| Q3 | Light (<500 lux) and nutrient (<40%) thresholds are global and do not depend on the crop | 191, 199 | Keep the values; each crop profile carries them, so one crop could override them later |
| Q4 | Inconsistent costs: energy only on Tomato/Strawberry temperature alerts (+10 high / +15 low), water only on Tomato/Strawberry low humidity (20 / 25) and Tomato low soil (50); per iteration +2 L / +1 kWh | 148–186, 291–292 | Keep the exact numbers, stored with each rule |
| Q5 | Low temperature triggers no device but still charges 15 kWh (Tomato/Strawberry) | 149, 153, … | Keep |
| Q6 | Spinach and Cucumber are defined but never scheduled | 159–170, 351–354 | Keep as profiles; not scheduled |
| Q7 | Heating Unit, Ventilation System and Shade Controller are missing; Humidifier and Dehumidifier are extra | – | Keep the legacy device set. Adding a device later means adding one class |
| Q8 | Archive-vs-report order is random | 384–386 | **Fix**: always archive first (the ~96% legacy behaviour) – this is concurrency issue C6 |
| Q9 | The console report's `Active Alerts` is almost always 0, because it runs after archiving | 212 | Keep (follows from Q8) |
| Q10 | Device effects are overwritten by the next sensor reading, so devices never influence later alerts | 94–124 vs 146–198 | Keep the asynchronous model; only make each state update thread-safe (C4) |
| Q11 | `sensorLogs` is collected but never output; the `reports` log contains the literal `"Daily Report"` | 64, 215 | Keep both (the second is part of the file format) |
| Q12 | Dead code: `irrigationCycle`, `feedingCycle`, `showAlerts`, `showLogs`, two unused locks | 243–249, 297–307, 28–29 | **Remove** (no observable behaviour) |
| Q13 | Severity levels, sensor-failure alerts and extra reports (spec 3.4/3.5) | – | Not implemented; listed as limitations |

---

## Scope for Phases 2–5: each spec requirement, and nothing more

| Spec requirement | What we will do (minimal) |
|---|---|
| 4.1 Split the monolith | About 12–15 small classes in a few packages (`model`, `crop`, `device`, `alert`, `report`, `app`). `org.example.SVFRMS` stays as the entry point |
| 4.2 (i) SRP | One job per class: rules, devices, the alert store, metrics, reporting, simulation |
| 4.2 (ii) New crop without editing code | A `CropProfile` is data (thresholds, costs, which device to trigger), kept in a `CropRegistry`. Adding Kale = adding one profile entry |
| 4.2 (iii) LSP | Small device interface with no "unsupported operation" methods; tasks are `Runnable`s, not `Thread` subclasses |
| 4.2 (iv) ISP | `handleEvent` is replaced by small, specific methods/interfaces (alert listener, device) |
| 4.2 (v) DIP | Rules depend on `Device` and alert-listener interfaces; the random sensor source is injected, which also gives us the seed for the baseline |
| 4.3 Design patterns (3–4, each fixing a smell above) | **Strategy** – one class per device (S6); **Registry** – crop profiles (S4, S5); **Observer** – alert listeners for console, store and summary (S3); **Producer–Consumer** – `BlockingQueue` control queue (S7, S8) |
| 4.4 Concurrency | `ConcurrentHashMap.computeIfAbsent` (C1), thread-safe lists (C2), `AtomicInteger` counters (C3), a lock per chamber's state (C4, C5), `ArrayBlockingQueue` with proper interrupt handling (C8–C10), `ExecutorService` plus failure reporting (C11), archive-then-report in a fixed order (C6), no nested locks |
| 4.5 Add/remove crops | Phase 4: add Kale and Bok Choy as one profile each, and show the diff |
| 5 Deliverables | `run.jar` via `mvn package`; README sections 3–8 |

**Not doing** (overengineering for this brief): configuration files or JSON loaders, a Strategy class per crop, new devices, severity levels, new report types, dependency-injection frameworks, or extra abstraction layers with only one implementation and no smell to fix.
