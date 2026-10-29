# SVFRMS – Singapore Vertical Farming Research Management System

SE301 project: a refactor of the legacy single-file `SVFRMS.java` into a modular, SOLID, thread-safe Java application.

The system simulates growing chambers. For each chamber it:
1. reads the sensors (temperature, humidity, soil moisture, light, nutrients),
2. checks the readings against the crop's ranges,
3. raises alerts (INFO / WARNING / CRITICAL),
4. switches on devices (fans, pumps, lights...) to fix the problem,
5. prints and saves reports.

The full technical report (issues found, design, concurrency fixes, patterns, UML diagrams) is in **[README.pdf](README.pdf)**.

## Requirements

- Java 25 or later
- Maven 3.9+

## Build, test and run

```bash
mvn package          # compiles, runs the 30 tests, builds target/run.jar
java -jar target/run.jar
```

The console shows alerts and device actions, followed by five reports. The full report is also saved to `archive/daily_report.txt`.

To run the tests only:

```bash
mvn test
```

## Adding or removing a crop

Crops are data, not code. Edit [`src/main/resources/crops.csv`](src/main/resources/crops.csv):

```csv
name,tempMin,tempMax,humidityMin,humidityMax,soilMin,soilMax,lightMin,lightMax,nutrientMin,nutrientMax
Kale,15,24,60,80,55,75,400,850,40,85
```

- **Add a crop:** add one line.
- **Remove a crop:** delete its line (and any chamber using it).
- **Choose which crop each chamber grows:** edit [`src/main/resources/chambers.csv`](src/main/resources/chambers.csv).

No Java code changes are needed. A `crops.csv` or `chambers.csv` placed in the folder you run the jar from overrides the built-in one, so you can change crops without rebuilding.

## Project structure

```
src/main/java/org/example/
├── app/         Main (wires everything), FarmSystem (facade), console output, settings
├── model/       Range, SensorType, SensorReading, CropProfile, Chamber, ChamberState
├── config/      CSV reading, chamber layout
├── crop/        CropRepository + CSV implementation
├── rule/        Sensor rules (threshold, sensor failure), severity policy, rule engine
├── device/      Device interface, configurable devices, the standard device list
├── control/     Control queue (producer-consumer) and control worker
├── event/       EventBus (observer)
├── alert/       Alert, AlertType, Severity, AlertStore
├── metrics/     Resource usage, per-chamber activity, reading statistics
├── simulation/  Sensor source (random simulator), chamber monitor
└── report/      The five reports, report generator, archive file writer
```

## Extending the system

| To add...           | Do this                                                              |
|---------------------|----------------------------------------------------------------------|
| A crop              | Add a row to `crops.csv`                                             |
| A device            | Add an `EnvironmentalDevice` in `StandardDevices` and use it in a rule |
| A rule / alert type | Implement `SensorRule` and register it in `StandardRules`            |
| A report            | Implement `Report` and register it in `ReportGenerator.standard()`   |
| Real sensors        | Implement `SensorSource` and pass it in `Main`                       |
| A new alert output  | Subscribe a listener with `farm.onAlert(...)` in `Main`              |

## Design patterns used

Strategy (rules, devices, reports), Observer (`EventBus`), Command (`ControlRequest`), Producer–Consumer (`ControlQueue`), Facade (`FarmSystem`), Repository (`CropRepository`) and Dependency Injection (`Main`). See [README.pdf](README.pdf) for the reasons behind each.
