# SVFRMS – Refactoring the Singapore Vertical Farming Research Management System

**SE301 Project 2026-27 · G1 Team 3**
Team members: Etienne, Naren, Anson

<div style="page-break-after: always"></div>

**Build and run** (Java 25 or later, no external libraries):

```bash
mvn package                      # runs the tests (48 methods, 60 runs) and builds target/run.jar
java -jar target/run.jar         # runs one simulated day with the 4 legacy chambers
java -jar target/run.jar demo    # same, with demo/chambers.csv: adds Kale, Bok Choy and Bell Pepper (CH5-CH7), no rebuild
```

A run writes `archive/daily_report.txt` (legacy format) and `archive/alerts_by_severity.txt` (every alert tagged INFO / WARNING / CRITICAL, see §5). Exit status: 0 = success, 1 = a chamber failed, 2 = invalid configuration. The full technical report is `README.pdf`.

Legacy line numbers below refer to the original file, kept unchanged at `baseline/legacy/org/example/SVFRMS.java` (393 lines).

---

## 1. Legacy System Analysis

**Purpose.** SVFRMS simulates one day of an indoor vertical farm. Each growing chamber holds one crop. Sensors read temperature, humidity, soil moisture, light and nutrient levels. When a reading is outside the crop's limits, the system raises an alert, charges water or energy, and asks a device (fan, pump, light…) to correct the chamber. At the end of the day it archives the alerts and writes a daily report to the console and to `archive/daily_report.txt`.

**Main components.** Everything lived in one class, `SVFRMS`:

| Component | Legacy lines | What it does |
|---|---|---|
| Global state | 7–31 | 5 lists, 2 maps, 4 counters, 4 lock objects, `Random`, all `public static` and mutable |
| Domain model | 33–46 | `ChamberState` (current readings) and `ChamberSummary` (alert/action counts) |
| Event hub | 62–88 | `handleEvent(type, …)`: six unrelated operations selected by a string |
| Rule engine | 141–205 | `processSensor`: an `if/else` matrix of sensor × crop |
| Device control | 90–129 | `activateControl`: an `if/else` chain over device names |
| Control queue | 131–139, 251–273 | hand-written bounded buffer (`wait/notifyAll`) and a `ControlThread` consumer |
| Simulation | 275–295 | `ChamberThread`: 20 cycles of 5 random readings per chamber |
| Reporting | 207–241, 309–347 | console report, alert archiving, file report |
| Bootstrap | 349–392 | `main` starts 4 chamber threads (Tomato, Strawberry, Lettuce, Chili), the control thread, and two report threads |

The intern added threads for performance: four chamber threads produce readings in parallel, and one control thread consumes device requests.

---

## 2. Code Analysis and Limitations

### 2.1 Design issues

| # | Issue | Legacy example | SOLID (spec 4.2) |
|---|---|---|---|
| S1 | **God class.** The model, rules, devices, queue, threads, reports and file I/O all live in one class | whole file | SRP (i) |
| S2 | **Global mutable state.** Every field is `public static`, so any code can change anything | 10–31 | SRP, DIP (v) |
| S3 | **String-typed event hub.** `handleEvent("CREATE_ALERT", chamber, "", msg)` does logging, storing, counting and printing. Callers pass dummy `""` arguments the branch ignores | 62–88, 142 | SRP (i), ISP (iv) |
| S4 | **Crop rules hard-coded in control flow.** The same alert block is copy-pasted **19 times**; adding a crop means editing `processSensor` in up to 3 places | 147–203 | **OCP (ii)** |
| S5 | **Magic numbers.** Thresholds, costs, device deltas and random ranges are scattered through the code | 94–124, 148–202, 285–292 | OCP |
| S6 | **Device `if/else` chain.** Adding a Heating Unit means editing `activateControl` | 90–129 | OCP, SRP |
| S7 | **Stringly-typed queue messages** `"CH1\|Cooling Fan"`, parsed again with `split` | 136, 269 | – |
| S8 | **Hand-written bounded buffer** that swallows `InterruptedException` (`catch(Exception e){}`) | 131–139, 256–267 | – |
| S9 | **`extends Thread`.** `ControlThread` ignores `interrupt()` and loops `while(true)`, so it does not behave the way callers of `Thread` expect | 251, 275 | **LSP (iii)** |
| S10 | **Concrete dependencies.** `System.out`, `new Date()` and a static `Random` are used directly, so a run can't be reproduced or tested | throughout | DIP (v) |
| S11 | **Duplicate report formatting** in the console and file versions | 210–214 vs 314–319 | SRP |
| S12 | **Dead code.** `irrigationCycle`, `feedingCycle`, `showAlerts`, `showLogs` and two unused locks | 243–249, 297–307 | – |

Example of S3 + S4. One Tomato rule; the same block is repeated for every crop and sensor:

```java
if(value>28){handleEvent("TRACK_ALERT", chamber, "", chamber+" Tomato High Temp");
             handleEvent("CREATE_ALERT", chamber, crop, chamber+" Tomato High Temp");
             totalEnergyUsage+=10; addControlRequest(chamber, "Cooling Fan");}
```

**LSP note.** The legacy code has no domain inheritance hierarchy. Its only inheritance is `extends Thread`, and S9 is the LSP problem there. We also avoided creating a new one: a `Device` base class with `increase()`/`decrease()` would force a Cooling Fan to implement a method that makes no sense for it.

### 2.2 Concurrency analysis (spec 4.4)

| # | Issue | Where | Effect |
|---|---|---|---|
| C1 | **Check-then-act on a `HashMap`** (`containsKey` → `put` → `get`) from 4 threads | 48–60 | `get` returns `null`, the chamber thread dies with an NPE, and a whole chamber can vanish from the report |
| C2 | **Unsynchronized `ArrayList.add`** from 4 threads | 64, 73 | lost alerts: `Generated Alerts` ≠ number archived |
| C3 | **Non-atomic counters** (`totalWaterUsage += n`) | 74–75, 148–186, 291 | lost updates to the water, energy and alert totals |
| C4 | **`ChamberState` written by a chamber thread and the control thread** with no lock | 146–198 vs 94–124 | a device's `temperature − 5` overwrites a newer reading, and there is no visibility guarantee |
| C5 | **`ChamberSummary`** read and written by two threads without synchronization | 79–86 | visibility; wrong counts through C1 |
| C6 | **Ordering race:** the archive and report threads start back-to-back (384–386). A lock gives mutual exclusion, not order | 373–389 | the report sometimes prints *before* archiving |
| C7 | **Locks that don't guard the writers:** `alertLock` is taken by the archiver but never by `handleEvent`, which adds to the list | 73, 208, 223 | a false sense of safety |
| C9 | **Swallowed interrupts** in both `wait()` loops | 134, 258 | threads cannot be cancelled |
| C10 | **Hang risk:** if the control thread dies, producers block forever on the full queue and `main` blocks on `join()` | 131–139, 251–273 | the program never ends |
| C11 | **Silent thread death:** no error handling, and `main` writes a "successful" report anyway | 362–391 | a corrupt report with no error |

Two minor issues are left out of this table: C8, two lock objects that are never used (removed with the dead code, S12), and C12, one shared `Random`, which is thread-safe but makes runs non-reproducible. The full list is in `docs/phase1-analysis.md`.

**Deadlocks.** None are reachable as written. The only nested locks are `alertLock` → `reportLock`, taken in the same order in both methods, so no cycle can form. It is one edit away, though: if either method took `reportLock` first, the two report threads could deadlock. The real liveness risk is the hang in C10.

**Evidence.** We ran the unmodified program 300 times and checked invariants that must always hold. For example, water = 160 + the sum of the archived alerts' water costs, and `Generated Alerts` must equal the number of archived alerts (`baseline/check_invariants.py`):

```
Legacy: 92 / 300 runs with no defect
 123 thread crashed: NullPointerException   (C1)
  67 Generated Alerts != archived           (C2)
  27 / 24 water / energy lost update        (C3)
   7 report printed BEFORE archive          (C6)
```

### 2.3 Limitations kept on purpose

Spec section 4 says to preserve behaviour, so these legacy behaviours are kept unchanged. Each is now visible in one data row and can be changed without touching code:
- humidity rules exist only for Tomato and Strawberry, and the soil rule only for Tomato;
- light (< 500 lux) and nutrient (< 40%) limits are shared by all crops;
- low temperature triggers no device for the legacy crops;
- Spinach and Cucumber have profiles but no chamber;
- the legacy crops use only the legacy devices. We added the Heating Unit (§5), used only by the new Bell Pepper and Eggplant rows; Ventilation and Shade Controller are still absent, and the Humidifier and Dehumidifier the spec doesn't list are kept;
- the extra report types from spec 3.5 were never implemented. (Severity levels and the sensor-failure alert from spec 3.4 were missing too; we added them without changing the legacy output, see §5.)

---

## 3. Proposed Design

**Design rule:** preserve behaviour; remove each smell and concurrency defect above; add nothing else. Every class exists because it fixes a specific S# or C#.

### 3.1 Key changes and decisions

1. **Split the monolith into 7 small packages** (`model`, `crop`, `sensor`, `device`, `alert`, `report`, `app`): 33 classes, the largest 103 lines. `org.example.SVFRMS` remains the entry point but only wires objects together (S1, S2, S15).
2. **Crop rules become data (S4, S5).** The 19 copy-pasted blocks became 19 rows in `src/main/resources/crops.csv`:
   ```
   # crop,  sensor,      dir,  limit, device,       water, energy, severity, message
   Tomato,  TEMPERATURE, HIGH, 28,    Cooling Fan,  0,     10,     CRITICAL, {crop} High Temp
   *,       LIGHT,       LOW,  500,   Grow Light,   0,     0,      INFO,     Low Light Level
   ```
   `SensorProcessor` loops over the rules for a crop and sensor and never names a crop:
   ```java
   for (ThresholdRule rule : crops.rulesFor(crop, sensor)) {
       if (!rule.isBreachedBy(value)) continue;
       alerts.publish(new Alert(chamberId, crop, rule.alertText(chamberId, crop), rule.severity()));
       resources.addWater(rule.waterCost());
       resources.addEnergy(rule.energyCost());
       if (rule.triggersDevice())
           control.request(new ControlRequest(chamberId, state, devices.get(rule.deviceName())));
   }
   ```
   *Decision:* a CSV resource instead of a Java "catalog" class. A catalog class would still have to be *edited* to add a crop, which is exactly what spec 4.2 (ii) asks to avoid. One small plain-Java class (`FarmConfigLoader`) reads the CSV; no library is needed. A bad row (wrong column count, unknown device or sensor, a duplicate chamber) stops the program at start-up with the file and line number; a chamber whose crop has no rules gets a warning.
3. **One class per device** behind a `Device` interface (S6), with a `DeviceRegistry` that looks devices up by name.
4. **Typed objects instead of strings (S3, S7).** An `Alert` record goes to listeners; a `ControlRequest` record goes onto the queue.
5. **Dependencies are injected (S10).** `SensorSource` (random readings), the output and error `PrintStream`s and the `Clock` used for timestamps are passed in through constructors. The same code can then run with a seeded source, which is how we proved behaviour is unchanged.
6. **Dead code removed (S12).**

### 3.2 SOLID mapping (spec 4.2 i–v)

| Principle | Before | After |
|---|---|---|
| **(i) SRP** | `SVFRMS` did everything; `handleEvent` had 6 jobs | One job per class: `AlertStore` stores, `ConsoleAlertPrinter` prints, `ResourceMeter` counts, `DailyReport` formats |
| **(ii) OCP** | A new crop meant editing 3 `if` chains | A new crop means new rows in `crops.csv`, with **zero** Java edits (§5). A new device is a new class |
| **(iii) LSP** | `ControlThread extends Thread` ignores `interrupt()` | No `Thread` subclasses: tasks are `Runnable`s run by an `ExecutorService`, and they honour interrupts. Every `Device` can honour the whole interface |
| **(iv) ISP** | One wide `handleEvent(type, chamber, crop, msg)`; callers pass dummy `""` | Three tiny interfaces, each with 1–2 methods: `SensorSource`, `Device`, `AlertListener` |
| **(v) DIP** | Static fields, `System.out`, `new Date()`, static `Random` | `SensorProcessor` depends on `SensorSource`, `Device` and `AlertListener` abstractions; concrete objects are created only in `SVFRMS` (composition root) |

### 3.3 Concurrency fixes

| Issue | Fix | Class |
|---|---|---|
| C1 | `ConcurrentHashMap.computeIfAbsent` creates a summary atomically. Each chamber thread owns its `ChamberState`, so state is no longer looked up at all | `ChamberSummaryTracker`, `ChamberMonitor` |
| C2 | `synchronized` methods on the alert store; `Collections.synchronizedList` for the sensor log | `AlertStore`, `SensorLog` |
| C3 | `AtomicInteger.addAndGet` | `ResourceMeter` |
| C4, C5 | `synchronized set/adjust` on each chamber's state. A device's read-modify-write is one atomic `adjust(sensor, v -> max(18, v - 5))` | `ChamberState`, `ChamberSummary` |
| C6 | Archive, *then* report, in a fixed order on the main thread | `FarmSimulation` |
| C7 | Shared data is reachable only through its owner's methods, which hold that owner's lock | all of the above |
| C9 | `ArrayBlockingQueue(5)` replaces the hand-written buffer; a `STOP` request queued behind pending work drains the queue before the consumer exits. Interrupts are never swallowed: a producer interrupted on a full queue keeps the flag and fails loudly (no request is lost silently), and if `STOP` can't be queued the consumer is interrupted instead. If the main thread is interrupted, the chamber threads are interrupted before the consumer, so none is left blocked on a full queue, and the report is still written | `ControlService`, `FarmSimulation` |
| C10 | The consumer catches a failure per request and keeps running; executors shut down with a 30 s timeout | `ControlService`, `FarmSimulation` |
| C11 | `Future.get()` surfaces a chamber's exception: it prints `ERROR: chamber CH2 failed: …` and the program exits with code 1 | `FarmSimulation` |

**No deadlock by construction.** No method holds two locks at once. Each lock guards a few field updates and is never held while calling another object. In particular, `SensorProcessor` holds no lock while it publishes an alert or waits for room in the control queue (a timed `offer` that also checks the control thread is still alive). With at most one lock held at any time, there can be no cycle of threads waiting on each other.

---

## 4. Final Architecture

### 4.1 Component diagram

```mermaid
flowchart LR
    CSV[("crops.csv<br/>chambers.csv")] --> CFG[FarmConfigLoader]
    MAIN["SVFRMS<br/>(wiring)"] --> SIM[FarmSimulation]
    MAIN --> CFG
    CFG --> REG[CropRegistry]
    SIM -->|"1 thread per chamber"| MON[ChamberMonitor]
    SIM -->|"1 control thread"| CTRL["ControlService<br/>BlockingQueue(5)"]
    SIM --> RPT[DailyReport]
    MON --> SRC[["SensorSource"]]
    MON --> PROC[SensorProcessor]
    PROC --> REG
    PROC --> STATE[ChamberState]
    PROC --> METER[ResourceMeter]
    PROC --> PUB[AlertPublisher]
    PROC -->|ControlRequest| CTRL
    PUB --> L[["AlertListener"]]
    L -.-> TRK[ChamberSummaryTracker]
    L -.-> STORE[AlertStore]
    L -.-> PRN[ConsoleAlertPrinter]
    L -.-> SEV[SeverityLog]
    CTRL --> DEV[["Device"]]
    DEV --> STATE
    CTRL --> TRK
    RPT --> STORE
    RPT --> METER
    RPT --> TRK
```
`[[…]]` = interface. Device implementations: `CoolingFan`, `WaterPump`, `NutrientInjector`, `Humidifier`, `Dehumidifier`, `GrowLight`.

| Package | Classes |
|---|---|
| `app` | `FarmSimulation` (runs one day), `FarmConfigLoader`, `ChamberAssignment` |
| `model` | `SensorType` (enum), `ChamberState`, `ChamberSummary` |
| `crop` | `ThresholdRule`, `CropProfile`, `CropRegistry` |
| `sensor` | `SensorSource`, `RandomSensorSource`, `SensorProcessor`, `SensorRanges`, `ChamberMonitor` |
| `device` | `Device` + 7 devices, `DeviceRegistry`, `ControlRequest`, `ControlService` |
| `alert` | `Alert`, `Severity`, `AlertListener`, `AlertPublisher`, `AlertStore`, `ConsoleAlertPrinter` |
| `report` | `ChamberSummaryTracker`, `ResourceMeter`, `SensorLog`, `DailyReport`, `SeverityLog`, `Timestamps` |

### 4.2 Sequence: one high-temperature reading in CH1

```mermaid
sequenceDiagram
    participant M as ChamberMonitor (CH1 thread)
    participant P as SensorProcessor
    participant A as AlertPublisher
    participant Q as ControlService queue
    participant C as Control thread
    participant F as CoolingFan
    participant S as ChamberState
    M->>P: process(CH1, Tomato, TEMPERATURE, 31)
    P->>S: set(TEMPERATURE, 31)
    P->>P: rule "Tomato TEMPERATURE HIGH 28" breached
    P->>A: publish(Alert "CH1 Tomato High Temp")
    A-->>A: summary tracker → store → console
    P->>P: ResourceMeter.addEnergy(10)
    P->>Q: put(ControlRequest(CH1, state, CoolingFan)) — blocks if 5 queued
    C->>Q: take()
    C->>F: apply(state)
    F->>S: adjust(TEMPERATURE, max(18, v − 5))
    C-->>C: record action, print "Cooling Fan Activated"
```

**End of the run** (`FarmSimulation.run`): wait for every chamber's `Future` → queue `STOP` (with a 30 s limit, so a hung device cannot block shutdown) so the control thread drains its queue and exits → check the control thread's own `Future`, so a crashed control thread fails the run → archive alerts → print the report → save `archive/daily_report.txt`.

### 4.3 Proof that behaviour is preserved

- **Same output as the original.** The original program can't be reproduced with a fixed seed, because the scheduler decides which chamber draws the next random number. We ran both versions with the same seed and the chambers one at a time, then compared every alert line, total, chamber summary and archived alert. The output is **identical** for 5 different seeds (`baseline/compare_refactor.sh`).
- **Concurrency.** The real concurrent `run.jar` passed the same invariant check in **300 / 300** runs with the legacy chambers and **300 / 300** with the 7-chamber `demo` layout, compared with 92 / 300 for the legacy program.
- **Tests.** 48 JUnit test methods (60 runs; the stress tests repeat 3 times): stress tests on every shared object (8 threads × 5,000 operations), the producer–consumer queue with capacity 1, a failing device (C10), a crashed control thread whose unrun requests are reported and fail the run, a `stop()` that gives up on a full queue, an interrupted producer (C9), a failing chamber (C11), an interrupted run that still writes its report, the config loader's error cases, and end-to-end runs with exact totals for both chamber layouts.
- The baseline scripts run on Linux, macOS and Windows (Git Bash).

---

## 5. Extensibility and Maintainability

**Demonstration: adding Kale and Bok Choy (spec 4.5).** The complete change is 8 rows in `crops.csv` and 2 rows in a `chambers.csv`. No Java file changed. The chamber rows live in `demo/chambers.csv`, so the default run keeps the legacy behaviour and `java -jar run.jar demo` runs the new crops:

```diff
+Kale,     TEMPERATURE, HIGH, 24, Cooling Fan, 0,  8, CRITICAL, {crop} High Temp
+Kale,     TEMPERATURE, LOW,  12, -,           0, 12, CRITICAL, {crop} Low Temp
+Kale,     HUMIDITY,    LOW,  55, Humidifier, 15,  0, WARNING,  {crop} Low Humidity
+Kale,     SOIL,        LOW,  40, Water Pump, 30,  0, WARNING,  {crop} Low Soil Moisture
+Bok Choy, ...                                           (4 similar rows)
 # demo/chambers.csv
+CH5, Kale
+CH6, Bok Choy
```

Both new crops automatically get the shared light and nutrient rules. With the 7 demo chambers, the program still passed 300 / 300 concurrent runs. **Removing a crop** means deleting its rows; if a chamber still uses it, that chamber keeps only the shared rules (as in the legacy code) and a start-up warning names the line. Bell Pepper (demo chamber CH7) and Eggplant (rules only, no chamber) were added the same way, and their low-temperature rows use the new Heating Unit.

**Changing crops without rebuilding.** `java -jar run.jar <folder>` uses `<folder>/crops.csv`, `chambers.csv` and `sensors.csv` in place of the built-in files when they exist, and prints which file it used. A chamber whose crop has no rows in `crops.csv` still runs with only the shared rules, as in the legacy code, but now prints a start-up warning, so a typo is visible. A row with an empty column, a limit that is not a finite number, a negative cost, or a second rule with the same crop, sensor, direction and limit stops the program with the file and line. `demo/chambers.csv` adds `CH5, Kale`, `CH6, Bok Choy` and `CH7, Bell Pepper` for the presentation. Only a folder named on the command line is read, so a stray CSV can never change a normal run. Files saved by Notepad or PowerShell (byte-order mark, UTF-16, or appended with `>>`) are read correctly; any other non-UTF-8 text stops the program with the file name instead of being silently altered.

**Alerting extension (spec 3.4).** Each rule has a severity column (temperature CRITICAL; humidity, soil and nutrient WARNING; light INFO). A new `SeverityLog` listener writes every alert with its level, plus a count per level, to `archive/alerts_by_severity.txt`. A sensor that reports a reading outside its valid range in `sensors.csv` (e.g. NaN, humidity outside 0–100 %) or does not respond at all raises a CRITICAL *Sensor Failure* alert; the value is not stored, no device is triggered, and the chamber carries on with its other sensors. At the end of the run one extra console line gives the count per level, e.g. `Alerts by severity: INFO=21, WARNING=64, CRITICAL=93`. This took one new listener, one data file and one method in `SensorProcessor`. Every legacy console line and the daily report are unchanged, so the legacy comparison still passes (it ignores the one extension line).

| Future change | Legacy | Refactored |
|---|---|---|
| Add or remove a crop | edit `processSensor` in up to 3 places | add or delete rows in `crops.csv` |
| Change a threshold or cost | find the right `if` among 19 copies | edit one number in one row |
| Add a device (e.g. Heating Unit) | edit `activateControl` | add `HeatingUnit implements Device`, register it in `SVFRMS`, name it in a CSV row (done: Bell Pepper, Eggplant) |
| Add an alert output (file log, SMS) | edit `handleEvent` | add an `AlertListener` and subscribe it (done for severity: `SeverityLog`) |
| Read real sensors | rewrite `ChamberThread` | add a `SensorSource` implementation |
| Change the report format | edit two places | edit `DailyReport` only |
| Add a chamber | edit `main` | add a row to `chambers.csv`, or run with a config folder |

**Maintainability.**
- Each class has one job. Most are under 100 lines; the largest (`FarmConfigLoader`, `FarmSimulation`, `ControlService`) are long because of input checks and shutdown handling, not extra responsibilities.
- Names describe the domain (`AlertStore`, `ResourceMeter`) instead of string codes.
- Thread-safety lives inside the class that owns the data, so callers can't forget a lock.
- The single-lock rule (§3.3) is documented in `SensorProcessor`, so future changes keep the program deadlock-free.
- The baseline scripts let any future change be checked against the original behaviour.

---

## 6. Design Patterns

| Pattern | Where | Problem it solves | Why this pattern |
|---|---|---|---|
| **Strategy** | `Device` interface + 6 device classes, used by `ControlService` | The `activateControl` `if/else` chain over device names (S6): every new device meant editing it | Each device is a different algorithm for changing a chamber's state behind one interface. `ControlService` runs any `Device` without knowing which one, so a new device is one new class |
| **Observer** | `AlertPublisher` notifies `ChamberSummaryTracker`, `AlertStore`, `ConsoleAlertPrinter` (all `AlertListener`) | `handleEvent` did tracking, storing, counting and printing behind one string switch (S3: SRP, ISP) | The rule engine raises one `Alert` and does not know who listens. Each listener has one job, and a new reaction is a new listener: severity was added this way (`SeverityLog`). Listeners are called in registration order, which preserves the legacy output order. `CopyOnWriteArrayList` makes the listener list safe to read from many threads |
| **Command** | `ControlRequest` (chamber + state + device, with `execute()`) | Queue messages were strings like `"CH1\|Cooling Fan"`, parsed again with `split` (S7) | A request is an object that carries everything needed to run later on another thread, so there's no string parsing and no lookup by chamber name |
| **Producer–Consumer** | `ControlService`: chamber threads `put`, the control thread `take`s from an `ArrayBlockingQueue(5)` | A hand-written `wait/notifyAll` buffer that swallowed interrupts and could hang (S8, C9, C10) | Same bounded-buffer behaviour as the legacy code (capacity 5, producers block when full), using the JDK's tested implementation |
| **Registry** | `CropRegistry`, `DeviceRegistry` | Crop and device names hard-coded in control flow (S4, S6) | Looking an object up by name is all that is needed; it lets CSV rows refer to devices by name and catches typos at start-up |
| **Composition root / constructor injection** | `SVFRMS.createSimulation` | Global `static` state (S2) and concrete dependencies (S10) | Every object is created once and passed in, so there are no globals, and a seeded `SensorSource` can be injected to check behaviour |

**Patterns not used, on purpose.**
- **Strategy per crop.** Crops differ only in numbers, not in behaviour, so data (CSV rows) is simpler than one class per crop.
- **Singleton.** It would bring back the global state that caused S2 and most of the concurrency bugs.
- **Factory / Abstract Factory.** No family of related objects needs to be created.
