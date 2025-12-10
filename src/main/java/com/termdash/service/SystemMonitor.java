package com.termdash.service;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.hardware.PowerSource;
import oshi.hardware.Sensors;
import oshi.software.os.OSFileStore;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SystemMonitor {
    private final SystemInfo systemInfo;
    private final HardwareAbstractionLayer hardware;
    private final OperatingSystem os;
    private final CentralProcessor processor;
    private final GlobalMemory memory;
    private final Sensors sensors;
    
    private long[] prevTicks;
    private double cachedCpuLoad = 0.0;
    private long lastCpuUpdate = 0;

    private long[] prevNetBytesRecv;
    private long[] prevNetBytesSent;
    private long lastNetUpdate = 0;
    private long cachedRxSpeed = 0;
    private long cachedTxSpeed = 0;

    private Map<Integer, OSProcess> prevProcesses = new HashMap<>();

    public SystemMonitor() {
        this.systemInfo = new SystemInfo();
        this.hardware = systemInfo.getHardware();
        this.os = systemInfo.getOperatingSystem();
        this.processor = hardware.getProcessor();
        this.memory = hardware.getMemory();
        this.sensors = hardware.getSensors();
        
        this.prevTicks = processor.getSystemCpuLoadTicks();
        
        List<NetworkIF> networkIFs = hardware.getNetworkIFs();
        this.prevNetBytesRecv = new long[networkIFs.size()];
        this.prevNetBytesSent = new long[networkIFs.size()];
        for (int i = 0; i < networkIFs.size(); i++) {
            this.prevNetBytesRecv[i] = networkIFs.get(i).getBytesRecv();
            this.prevNetBytesSent[i] = networkIFs.get(i).getBytesSent();
        }
        this.lastNetUpdate = System.currentTimeMillis();
    }

    public double getCpuLoad() {
        long now = System.currentTimeMillis();
        if (now - lastCpuUpdate > 1000) {
            long[] ticks = processor.getSystemCpuLoadTicks();
            cachedCpuLoad = processor.getSystemCpuLoadBetweenTicks(prevTicks);
            prevTicks = ticks;
            lastCpuUpdate = now;
        }
        return cachedCpuLoad;
    }

    public double getCpuTemperature() {
        return sensors.getCpuTemperature();
    }

    public void updateNetworkSpeeds() {
        long now = System.currentTimeMillis();
        long timeDelta = now - lastNetUpdate;
        
        if (timeDelta > 1000) { 
            List<NetworkIF> networkIFs = hardware.getNetworkIFs();
            long totalRx = 0;
            long totalTx = 0;
            
            if (networkIFs.size() != prevNetBytesRecv.length) {
                this.prevNetBytesRecv = new long[networkIFs.size()];
                this.prevNetBytesSent = new long[networkIFs.size()];
                for (int i = 0; i < networkIFs.size(); i++) {
                    this.prevNetBytesRecv[i] = networkIFs.get(i).getBytesRecv();
                    this.prevNetBytesSent[i] = networkIFs.get(i).getBytesSent();
                }
                this.lastNetUpdate = now;
                return;
            }

            for (int i = 0; i < networkIFs.size(); i++) {
                NetworkIF net = networkIFs.get(i);
                net.updateAttributes();
                
                long rx = net.getBytesRecv();
                long tx = net.getBytesSent();
                
                long rxDelta = rx - prevNetBytesRecv[i];
                long txDelta = tx - prevNetBytesSent[i];
                
                if (rxDelta > 0) totalRx += rxDelta;
                if (txDelta > 0) totalTx += txDelta;
                
                prevNetBytesRecv[i] = rx;
                prevNetBytesSent[i] = tx;
            }
            
            cachedRxSpeed = (long) (totalRx * (1000.0 / timeDelta));
            cachedTxSpeed = (long) (totalTx * (1000.0 / timeDelta));
            lastNetUpdate = now;
        }
    }

    public long getNetworkDownloadSpeed() {
        return cachedRxSpeed;
    }

    public long getNetworkUploadSpeed() {
        return cachedTxSpeed;
    }

    public double getStorageUsage() {
        List<OSFileStore> fileStores = os.getFileSystem().getFileStores();
        long total = 0;
        long used = 0;
        for (OSFileStore fs : fileStores) {
            total += fs.getTotalSpace();
            used += (fs.getTotalSpace() - fs.getUsableSpace());
        }
        return total == 0 ? 0 : (double) used / total;
    }

    public String getBatteryInfo() {
        List<PowerSource> powerSources = hardware.getPowerSources();
        if (powerSources.isEmpty()) {
            return "AC POWER";
        }
        PowerSource ps = powerSources.get(0);
        return String.format("%.0f%% %s", ps.getRemainingCapacityPercent() * 100, ps.isPowerOnLine() ? "(CHR)" : "(BAT)");
    }

    public String getUptime() {
        long uptimeSeconds = os.getSystemUptime();
        long days = uptimeSeconds / (24 * 3600);
        long hours = (uptimeSeconds % (24 * 3600)) / 3600;
        long minutes = (uptimeSeconds % 3600) / 60;
        return String.format("%dd %02dh %02dm", days, hours, minutes);
    }

    public int getProcessCount() {
        return os.getProcessCount();
    }

    public int getThreadCount() {
        return os.getThreadCount();
    }

    public String getOsName() {
        return os.getFamily() + " " + os.getVersionInfo().getVersion();
    }

    public String getFanSpeed() {
        int[] speeds = sensors.getFanSpeeds();
        if (speeds.length > 0) {
            return speeds[0] + " RPM";
        }
        return "N/A";
    }

    public List<ProcessMetric> getTopProcesses(int limit) {
        List<OSProcess> currentProcesses = os.getProcesses();
        List<ProcessMetric> metrics = new ArrayList<>();
        int cpuCount = processor.getLogicalProcessorCount();

        for (OSProcess p : currentProcesses) {
            OSProcess prev = prevProcesses.get(p.getProcessID());
            double cpuUsage = 0.0;
            if (prev != null) {
                cpuUsage = 100d * p.getProcessCpuLoadBetweenTicks(prev) / cpuCount;
            }
            metrics.add(new ProcessMetric(p.getName(), cpuUsage));
        }

        prevProcesses.clear();
        for (OSProcess p : currentProcesses) {
            prevProcesses.put(p.getProcessID(), p);
        }

        metrics.sort(Comparator.comparingDouble(ProcessMetric::getCpuUsage).reversed());

        if (metrics.size() > limit) {
            return metrics.subList(0, limit);
        }
        return metrics;
    }

    public static class ProcessMetric {
        private final String name;
        private final double cpuUsage;

        public ProcessMetric(String name, double cpuUsage) {
            this.name = name;
            this.cpuUsage = cpuUsage;
        }

        public String getName() { return name; }
        public double getCpuUsage() { return cpuUsage; }
    }

    public long getTotalMemory() {
        return memory.getTotal();
    }

    public long getUsedMemory() {
        return memory.getTotal() - memory.getAvailable();
    }

    public double getMemoryUsage() {
        return (double) getUsedMemory() / getTotalMemory();
    }
}
