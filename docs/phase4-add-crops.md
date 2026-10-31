# Phase 4 – Adding Kale and Bok Choy (spec 4.5)

## The whole change

Two data files changed. **No Java file was edited, added or recompiled by hand**: `mvn package` copies the CSVs into `run.jar`.

```diff
--- src/main/resources/crops.csv
+++ src/main/resources/crops.csv
 Cucumber,   TEMPERATURE, LOW,  18,    -,                 0,     0,      {crop} Low Temp
+Kale,       TEMPERATURE, HIGH, 24,    Cooling Fan,       0,     8,      {crop} High Temp
+Kale,       TEMPERATURE, LOW,  12,    -,                 0,     12,     {crop} Low Temp
+Kale,       HUMIDITY,    LOW,  55,    Humidifier,        15,    0,      {crop} Low Humidity
+Kale,       SOIL,        LOW,  40,    Water Pump,        30,    0,      {crop} Low Soil Moisture
+Bok Choy,   TEMPERATURE, HIGH, 22,    Cooling Fan,       0,     8,      {crop} High Temp
+Bok Choy,   TEMPERATURE, LOW,  13,    -,                 0,     12,     {crop} Low Temp
+Bok Choy,   HUMIDITY,    HIGH, 85,    Dehumidifier,      0,     0,      {crop} High Humidity
+Bok Choy,   SOIL,        LOW,  50,    Water Pump,        40,    0,      {crop} Low Soil Moisture
 *,          LIGHT,       LOW,  500,   Grow Light,        0,     0,      Low Light Level

--- demo/chambers.csv
+++ demo/chambers.csv
 CH4, Chili
+CH5, Kale
+CH6, Bok Choy
```

- Both crops also get the shared `*` light and nutrient rules automatically.
- They reuse existing devices only (Cooling Fan, Humidifier, Dehumidifier, Water Pump), as agreed: no new devices.
- The chamber rows go in `demo/chambers.csv`, not the built-in `chambers.csv`, so a default run keeps the legacy behaviour (spec section 4) and `java -jar run.jar demo` runs the new crops with no rebuild. The demo file also adds `CH7, Bell Pepper`.
- **Removing a crop** is the reverse: delete its rows (and its chamber line).
- If a row names a device that doesn't exist (e.g. `Heater`), the program stops at start-up with `crops.csv line N: unknown device 'Heater'` instead of failing later.

## Why no Java change was needed (Open/Closed)

| Concern | Where it lives | Changed? |
|---|---|---|
| Thresholds, costs, messages, device per rule | `crops.csv` rows → `CropProfile` / `ThresholdRule` | rows added |
| Which chambers run | `chambers.csv` → one `ChamberMonitor` thread each | rows added |
| Checking a reading against the rules | `SensorProcessor` (crop-agnostic) | no |
| Device behaviour | `Device` implementations | no |
| Alerts, totals, report, archive | `AlertStore`, `ResourceMeter`, `DailyReport` | no |
| Threading | `FarmSimulation` sizes its pool from the chamber list | no |

## Result (one run of `java -jar target/run.jar demo`)

```
[CH5][Kale][ALERT] CH5 Kale Low Soil Moisture
...
CH5 -> Alerts=30, Actions=29, Last Action=Nutrient Injector Activated, Last Alert=CH5 Low Nutrient Level
CH6 -> Alerts=35, Actions=32, Last Action=Nutrient Injector Activated, Last Alert=CH6 Low Nutrient Level
```

## Verification

`JAVA_HOME=/opt/homebrew/opt/openjdk ./baseline/compare_refactor.sh 42 300`

- **Behaviour still identical to the legacy baseline (seed 42).** The default `chambers.csv` is still CH1–CH4, so the harness runs the same chambers as the legacy baseline.
- **300/300 clean concurrent runs with the default 4 chambers, and 300/300 with the 7 demo chambers.** `check_invariants.py` now reads each alert's water/energy cost from `crops.csv` and the chamber list from `FARM_CHAMBERS`; `FARM_CONFIG=demo` applies the same per-file override as `run.jar demo`, so the expected totals cover Kale and Bok Choy too. Without `FARM_CHAMBERS` it defaults to the 4 legacy chambers, so the legacy stress check is unchanged.
