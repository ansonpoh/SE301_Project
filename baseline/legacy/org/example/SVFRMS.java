package org.example;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class SVFRMS {
    public static final Path ARCHIVE_DIR = Paths.get("archive");
    public static final Path REPORT_FILE = ARCHIVE_DIR.resolve("daily_report.txt");

    public static ArrayList<String> alerts = new ArrayList<>();
    public static ArrayList<String> archivedAlerts = new ArrayList<>();
    public static ArrayList<String> sensorLogs = new ArrayList<>();
    public static ArrayList<String> reports = new ArrayList<>();
    public static ArrayList<String> controlQueue = new ArrayList<>();
    public static int controlQueueSize = 5;
    public static volatile boolean controlThreadRunning = true;

    public static HashMap<String, ChamberState> chamberStates = new HashMap<>();
    public static HashMap<String, ChamberSummary> chamberSummaries = new HashMap<>();

    public static int totalWaterUsage = 0;
    public static int totalEnergyUsage = 0;
    public static int totalAlerts = 0;
    public static int totalAlertsGenerated = 0;

    public static Object reportLock = new Object();
    public static Object alertLock = new Object();
    public static Object waterPumpLock = new Object();
    public static Object nutrientLock = new Object();

    static Random random = new Random();

    static class ChamberState {
        double temperature = 24;
        double humidity = 70;
        double soilMoisture = 60;
        double lightLevel = 600;
        double nutrientLevel = 70;
    }

    static class ChamberSummary {
        int alerts = 0;
        int mitigationActions = 0;
        String lastAction = "No action";
        String lastAlert = "None";
    }

    public static ChamberState getChamberState(String chamber){
        if(!chamberStates.containsKey(chamber)){
            chamberStates.put(chamber, new ChamberState());
        }
        return chamberStates.get(chamber);
    }

    public static ChamberSummary getChamberSummary(String chamber){
        if(!chamberSummaries.containsKey(chamber)){
            chamberSummaries.put(chamber, new ChamberSummary());
        }
        return chamberSummaries.get(chamber);
    }

    public static void handleEvent(String type, String chamber, String crop, String message){
        if(type.equals("LOG")){
            sensorLogs.add(new Date()+" : "+message);
        }
        else if(type.equals("ALERT_LOG")){
            System.out.println("[" + chamber + "][" + crop + "][ALERT] " + message);
        }
        else if(type.equals("CONTROL_LOG")){
            System.out.println("\t\u001B[32m[" + chamber + "][CONTROL] " + message + "\u001B[0m");
        }
        else if(type.equals("CREATE_ALERT")){
            alerts.add(message);
            totalAlerts++;
            totalAlertsGenerated++;
            System.out.println("[" + chamber + "][" + crop + "][ALERT] " + message);
        }
        else if(type.equals("TRACK_ALERT")){
            ChamberSummary summary = getChamberSummary(chamber);
            summary.alerts++;
            summary.lastAlert = message;
        }
        else if(type.equals("TRACK_ACTION")){
            ChamberSummary summary = getChamberSummary(chamber);
            summary.mitigationActions++;
            summary.lastAction = message;
        }
    }

    public static void activateControl(String chamber, String control){
        ChamberState state = getChamberState(chamber);

            if(control.equals("Cooling Fan")){
                state.temperature = Math.max(18, state.temperature - 5);
                handleEvent("TRACK_ACTION", chamber, "", "Cooling Fan Activated");
                handleEvent("CONTROL_LOG", chamber, "", "Cooling Fan Activated");
                handleEvent("CONTROL_LOG", chamber, "", "Temperature reduced to " + state.temperature + "C");
            }
            else if(control.equals("Water Pump")){
                state.soilMoisture = Math.min(100, state.soilMoisture + 20);
                handleEvent("TRACK_ACTION", chamber, "", "Water Pump Activated");
                handleEvent("CONTROL_LOG", chamber, "", "Water Pump Activated");
                handleEvent("CONTROL_LOG", chamber, "", "Soil moisture increased to " + state.soilMoisture + "%");
            }
            else if(control.equals("Nutrient Injector")){
                state.nutrientLevel = Math.min(100, state.nutrientLevel + 15);
                handleEvent("TRACK_ACTION", chamber, "", "Nutrient Injector Activated");
                handleEvent("CONTROL_LOG", chamber, "", "Nutrient Injector Activated");
                handleEvent("CONTROL_LOG", chamber, "", "Nutrient level increased to " + state.nutrientLevel + "%");
            }
            else if(control.equals("Humidifier")){
                state.humidity = Math.min(100, state.humidity + 10);
                handleEvent("TRACK_ACTION", chamber, "", "Humidifier Activated");
                handleEvent("CONTROL_LOG", chamber, "", "Humidifier Activated");
                handleEvent("CONTROL_LOG", chamber, "", "Humidity increased to " + state.humidity + "%");
            }
            else if(control.equals("Dehumidifier")){
                state.humidity = Math.max(30, state.humidity - 10);
                handleEvent("TRACK_ACTION", chamber, "", "Dehumidifier Activated");
                handleEvent("CONTROL_LOG", chamber, "", "Dehumidifier Activated");
                handleEvent("CONTROL_LOG", chamber, "", "Humidity reduced to " + state.humidity + "%");
            }
            else if(control.equals("Grow Light")){
                state.lightLevel = Math.min(1000, state.lightLevel + 150);
                handleEvent("TRACK_ACTION", chamber, "", "Grow Light Activated");
                handleEvent("CONTROL_LOG", chamber, "", "Grow Light Activated");
                handleEvent("CONTROL_LOG", chamber, "", "Light level increased to " + state.lightLevel + " lux");
            }
    }

    public static void addControlRequest(String chamber, String control){
        synchronized(controlQueue){
            while(controlQueue.size() == controlQueueSize){
                try{controlQueue.wait();}catch(Exception e){}
            }
            controlQueue.add(chamber + "|" + control);
            controlQueue.notifyAll();
        }
    }

    public static void processSensor(String chamber,String crop,String sensor,double value){
        handleEvent("LOG", "", "", "Processing "+crop+" "+sensor+" = "+value);
        ChamberState state = getChamberState(chamber);

        if(sensor.equals("Temperature")){
            state.temperature = value;
            if(crop.equals("Tomato")){
                if(value>28){handleEvent("TRACK_ALERT", chamber, "", chamber+" Tomato High Temp"); handleEvent("CREATE_ALERT", chamber, crop, chamber+" Tomato High Temp"); totalEnergyUsage+=10; addControlRequest(chamber, "Cooling Fan");}
                if(value<22){handleEvent("TRACK_ALERT", chamber, "", chamber+" Tomato Low Temp"); handleEvent("CREATE_ALERT", chamber, crop, chamber+" Tomato Low Temp"); totalEnergyUsage+=15;}
            }
            else if(crop.equals("Strawberry")){
                if(value>24){handleEvent("TRACK_ALERT", chamber, "", chamber+" Strawberry High Temp"); handleEvent("CREATE_ALERT", chamber, crop, chamber+" Strawberry High Temp"); totalEnergyUsage+=10; addControlRequest(chamber, "Cooling Fan");}
                if(value<18){handleEvent("TRACK_ALERT", chamber, "", chamber+" Strawberry Low Temp"); handleEvent("CREATE_ALERT", chamber, crop, chamber+" Strawberry Low Temp"); totalEnergyUsage+=15;}
            }
            else if(crop.equals("Lettuce")){
                if(value>21){handleEvent("TRACK_ALERT", chamber, "", chamber+" Lettuce High Temp"); handleEvent("CREATE_ALERT", chamber, crop, chamber+" Lettuce High Temp"); addControlRequest(chamber, "Cooling Fan");}
                if(value<15){handleEvent("TRACK_ALERT", chamber, "", chamber+" Lettuce Low Temp"); handleEvent("CREATE_ALERT", chamber, crop, chamber+" Lettuce Low Temp");}
            }
            else if(crop.equals("Spinach")){
                if(value>20){handleEvent("TRACK_ALERT", chamber, "", chamber+" Spinach High Temp"); handleEvent("CREATE_ALERT", chamber, crop, chamber+" Spinach High Temp"); addControlRequest(chamber, "Cooling Fan");}
                if(value<10){handleEvent("TRACK_ALERT", chamber, "", chamber+" Spinach Low Temp"); handleEvent("CREATE_ALERT", chamber, crop, chamber+" Spinach Low Temp");}
            }
            else if(crop.equals("Chili")){
                if(value>32){handleEvent("TRACK_ALERT", chamber, "", chamber+" Chili High Temp"); handleEvent("CREATE_ALERT", chamber, crop, chamber+" Chili High Temp"); addControlRequest(chamber, "Cooling Fan");}
                if(value<24){handleEvent("TRACK_ALERT", chamber, "", chamber+" Chili Low Temp"); handleEvent("CREATE_ALERT", chamber, crop, chamber+" Chili Low Temp");}
            }
            else if(crop.equals("Cucumber")){
                if(value>27){handleEvent("TRACK_ALERT", chamber, "", chamber+" Cucumber High Temp"); handleEvent("CREATE_ALERT", chamber, crop, chamber+" Cucumber High Temp"); addControlRequest(chamber, "Cooling Fan");}
                if(value<18){handleEvent("TRACK_ALERT", chamber, "", chamber+" Cucumber Low Temp"); handleEvent("CREATE_ALERT", chamber, crop, chamber+" Cucumber Low Temp");}
            }
        }
        else if(sensor.equals("Humidity")){
            state.humidity = value;
            if(crop.equals("Tomato")){
                if(value>80){handleEvent("TRACK_ALERT", chamber, "", chamber+" Tomato High Humidity"); handleEvent("CREATE_ALERT", chamber, crop, chamber+" Tomato High Humidity"); addControlRequest(chamber, "Dehumidifier");}
                if(value<60){handleEvent("TRACK_ALERT", chamber, "", chamber+" Tomato Low Humidity"); handleEvent("CREATE_ALERT", chamber, crop, chamber+" Tomato Low Humidity"); totalWaterUsage+=20; addControlRequest(chamber, "Humidifier");}
            }
            else if(crop.equals("Strawberry")){
                if(value>85){handleEvent("TRACK_ALERT", chamber, "", chamber+" Strawberry High Humidity"); handleEvent("CREATE_ALERT", chamber, crop, chamber+" Strawberry High Humidity"); addControlRequest(chamber, "Dehumidifier");}
                if(value<70){handleEvent("TRACK_ALERT", chamber, "", chamber+" Strawberry Low Humidity"); handleEvent("CREATE_ALERT", chamber, crop, chamber+" Strawberry Low Humidity"); totalWaterUsage+=25; addControlRequest(chamber, "Humidifier");}
            }
        }
        else if(sensor.equals("Soil")){
            state.soilMoisture = value;
            if(crop.equals("Tomato")){
                if(value<45){handleEvent("TRACK_ALERT", chamber, "", chamber+" Tomato Low Soil Moisture"); handleEvent("CREATE_ALERT", chamber, crop, chamber+" Tomato Low Soil Moisture"); totalWaterUsage+=50; addControlRequest(chamber, "Water Pump");}
            }
        }
        else if(sensor.equals("Light")){
            state.lightLevel = value;
            if(value<500){
                handleEvent("TRACK_ALERT", chamber, "", chamber+" Low Light Level");
                handleEvent("CREATE_ALERT", chamber, crop, chamber+" Low Light Level");
                addControlRequest(chamber, "Grow Light");
            }
        }
        else if(sensor.equals("Nutrient")){
            state.nutrientLevel = value;
            if(value<40){
                handleEvent("TRACK_ALERT", chamber, "", chamber+" Low Nutrient Level");
                handleEvent("CREATE_ALERT", chamber, crop, chamber+" Low Nutrient Level");
                addControlRequest(chamber, "Nutrient Injector");
            }
        }
    }

    public static void generateDailyReport(){
        synchronized(alertLock){
            synchronized(reportLock){
                System.out.println("===== DAILY REPORT =====");
                System.out.println("Generated Alerts: "+totalAlertsGenerated);
                System.out.println("Active Alerts: "+totalAlerts);
                System.out.println("Water Usage: "+totalWaterUsage + " L");
                System.out.println("Energy Usage: "+totalEnergyUsage + " kWh");
                reports.add("Daily Report");
            }
        }
    }

    public static void archiveAlerts(){
        ArrayList<String> toArchive = new ArrayList<>();

        synchronized(alertLock){
            //try{Thread.sleep(100);}catch(Exception e){}
            if(!alerts.isEmpty()){
                toArchive.addAll(alerts);
                alerts.clear();
                totalAlerts = 0;
            }
            synchronized(reportLock){
                if(!toArchive.isEmpty()){
                    archivedAlerts.addAll(toArchive);
                    reports.add("Archived " + toArchive.size() + " alerts");
                    System.out.println("Archiving alerts... " + toArchive.size() + " moved to archive.");
                } else {
                    reports.add("No alerts to archive");
                    System.out.println("Archiving alerts... no active alerts to archive.");
                }
            }
        }
    }

    public static void irrigationCycle(){
        totalWaterUsage += 100;
    }

    public static void feedingCycle(){
        totalEnergyUsage += 20;
    }

    static class ControlThread extends Thread{
        public void run(){
            while(true){
                String request;

                synchronized(controlQueue){
                    while(controlQueue.isEmpty() && controlThreadRunning){
                        try{controlQueue.wait();}catch(Exception e){}
                    }

                    if(controlQueue.isEmpty() && !controlThreadRunning){
                        break;
                    }

                    request = controlQueue.remove(0);
                    controlQueue.notifyAll();
                }

                String[] parts = request.split("\\|");
                activateControl(parts[0], parts[1]);
            }
        }
    }

    static class ChamberThread extends Thread{
        String chamber;
        String crop;

        ChamberThread(String chamber,String crop){
            this.chamber=chamber;
            this.crop=crop;
        }

        public void run(){
            for(int i=0;i<20;i++){
                processSensor(chamber,crop,"Temperature",10+random.nextInt(30));
                processSensor(chamber,crop,"Humidity",40+random.nextInt(60));
                processSensor(chamber,crop,"Soil",20+random.nextInt(60));
                processSensor(chamber,crop,"Light",300+random.nextInt(900));
                processSensor(chamber,crop,"Nutrient",20+random.nextInt(80));
                totalWaterUsage += 2;
                totalEnergyUsage += 1;
            }
        }
    }

    public static void showAlerts(){
        for(String a: alerts){
            System.out.println(a);
        }
    }

    public static void showLogs(){
        for(String s: sensorLogs){
            System.out.println(s);
        }
    }

    public static void saveArchiveReport() {
        try {
            Files.createDirectories(ARCHIVE_DIR);

            ArrayList<String> reportLines = new ArrayList<String>();
            reportLines.add("===== DAILY REPORT =====");
            reportLines.add("Timestamp: " + new Date());
            reportLines.add("Generated Alerts: " + totalAlertsGenerated);
            reportLines.add("Active Alerts: " + totalAlerts);
            reportLines.add("Water Usage: " + totalWaterUsage + " L");
            reportLines.add("Energy Usage: " + totalEnergyUsage + " kWh");
            reportLines.add("Archived alerts: " + archivedAlerts.size());
            reportLines.add("---");
            reportLines.addAll(reports);

            if (chamberSummaries.size() > 0) {
                reportLines.add("--- chamber wise summary ---");
                TreeSet<String> sortedChambers = new TreeSet<String>(chamberSummaries.keySet());
                for (String chamber : sortedChambers) {
                    ChamberSummary summary = chamberSummaries.get(chamber);
                    reportLines.add(chamber + " -> Alerts=" + summary.alerts + ", Actions=" + summary.mitigationActions + ", Last Action=" + summary.lastAction + ", Last Alert=" + summary.lastAlert);
                }
            }

            if (archivedAlerts.size() > 0) {
                reportLines.add("--- archived alerts ---");
                reportLines.addAll(archivedAlerts);
            }

            Files.write(REPORT_FILE, reportLines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);

            System.out.println("Archive report saved to: " + REPORT_FILE.toAbsolutePath());
        } catch (Exception e) {
            System.out.println("Could not save archive report: " + e.getMessage());
        }
    }

    public static void main(String[] args) throws Exception {
        Thread activateControlThread = new ControlThread();
        Thread t1 = new ChamberThread("CH1","Tomato");
        Thread t2 = new ChamberThread("CH2","Strawberry");
        Thread t3 = new ChamberThread("CH3","Lettuce");
        Thread t4 = new ChamberThread("CH4","Chili");

        activateControlThread.start();
        t1.start();
        t2.start();
        t3.start();
        t4.start();

        t1.join();
        t2.join();
        t3.join();
        t4.join();

        synchronized(controlQueue){
            controlThreadRunning = false;
            controlQueue.notifyAll();
        }
        activateControlThread.join();

        Thread genReport = new Thread(new Runnable() {
            public void run() {
                generateDailyReport();
            }
        });
        Thread archiveAlerts = new Thread(new Runnable() {
            public void run() {
                archiveAlerts();
            }
        });

        archiveAlerts.start();
        //Thread.sleep(20);
        genReport.start();

        genReport.join();
        archiveAlerts.join();
        System.out.println("System finished sensor processing.");
        saveArchiveReport();
    }
}
