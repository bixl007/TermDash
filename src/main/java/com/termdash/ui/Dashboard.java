package com.termdash.ui;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.Symbols;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import com.termdash.service.CryptoService;
import com.termdash.service.EnvironmentService;
import com.termdash.service.SystemMonitor;
import oshi.software.os.OSProcess;

import java.io.IOException;
import java.text.DecimalFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Dashboard {

    private final Screen screen;
    private final TextGraphics tg;
    private final SystemMonitor sysMon;
    private final EnvironmentService envService;
    private final CryptoService cryptoService;
    private final DecimalFormat df = new DecimalFormat("0.0");
    
    private List<OSProcess> cachedProcesses = Collections.emptyList();
    private long lastProcessUpdate = 0;

    // Matrix Theme Colors
    private static final TextColor BACK_COLOR = TextColor.ANSI.BLACK;
    private static final TextColor TEXT_COLOR = TextColor.ANSI.GREEN;
    private static final TextColor HIGHLIGHT_COLOR = TextColor.ANSI.GREEN_BRIGHT;
    private static final TextColor ALERT_COLOR = TextColor.ANSI.RED_BRIGHT;

    public Dashboard() throws IOException {
        Terminal terminal = new DefaultTerminalFactory().createTerminal();
        this.screen = new TerminalScreen(terminal);
        this.screen.startScreen();
        this.screen.setCursorPosition(null); // Hide cursor
        this.tg = screen.newTextGraphics();
        
        this.sysMon = new SystemMonitor();
        this.envService = new EnvironmentService();
        this.cryptoService = new CryptoService();
    }

    public void run() throws IOException, InterruptedException {
        while (true) {
            long startTime = System.currentTimeMillis();

            draw();
            screen.refresh();

            // Handle input (exit on 'q' or 'Esc')
            var key = screen.pollInput();
            if (key != null && (key.getCharacter() != null && key.getCharacter() == 'q' || key.getKeyType() == com.googlecode.lanterna.input.KeyType.Escape)) {
                break;
            }

            // Cap at ~30 FPS or just sleep a bit
            long sleepTime = 100 - (System.currentTimeMillis() - startTime);
            if (sleepTime > 0) Thread.sleep(sleepTime);
        }
        screen.stopScreen();
    }

    private void draw() {
        TerminalSize size = screen.getTerminalSize();
        int width = size.getColumns();
        int height = size.getRows();

        // Update Network Stats
        sysMon.updateNetworkSpeeds();

        // Clear background
        tg.setBackgroundColor(BACK_COLOR);
        tg.setForegroundColor(TEXT_COLOR);
        tg.fill(' ');

        // --- System Stats Section (Left Top) ---
        int leftWidth = width / 2 - 2;
        int statsHeight = 16; 
        
        double cpu = sysMon.getCpuLoad();
        double mem = sysMon.getMemoryUsage();
        double storage = sysMon.getStorageUsage();
        double temp = sysMon.getCpuTemperature();
        String battery = sysMon.getBatteryInfo();
        int procs = sysMon.getProcessCount();
        int threads = sysMon.getThreadCount();
        
        drawProgressBar(4, 4, leftWidth - 4, "CPU USAGE", cpu);
        drawProgressBar(4, 6, leftWidth - 4, "RAM USAGE", mem);
        drawProgressBar(4, 8, leftWidth - 4, "STORAGE  ", storage);
        
        tg.setForegroundColor(TEXT_COLOR);
        tg.putString(4, 10, "CPU TEMP : ");
        tg.setForegroundColor(temp > 75 ? ALERT_COLOR : HIGHLIGHT_COLOR);
        tg.putString(15, 10, String.format("%.1f C", temp));
        
        tg.setForegroundColor(TEXT_COLOR);
        tg.putString(4, 11, "BATTERY  : ");
        tg.setForegroundColor(HIGHLIGHT_COLOR);
        tg.putString(15, 11, battery);

        tg.setForegroundColor(TEXT_COLOR);
        tg.putString(4, 12, "PROCESSES: ");
        tg.setForegroundColor(HIGHLIGHT_COLOR);
        tg.putString(15, 12, String.valueOf(procs));

        tg.setForegroundColor(TEXT_COLOR);
        tg.putString(4, 13, "THREADS  : ");
        tg.setForegroundColor(HIGHLIGHT_COLOR);
        tg.putString(15, 13, String.valueOf(threads));

        // --- Network & Environment Section (Right Top) ---
        int rightX = width / 2 + 1;
        int rightWidth = width / 2 - 3;
        
        tg.setForegroundColor(TEXT_COLOR);
        tg.putString(rightX + 2, 4, "OWNER  : ");
        tg.setForegroundColor(HIGHLIGHT_COLOR);
        tg.putString(rightX + 11, 4, "Xyrix");

        tg.setForegroundColor(TEXT_COLOR);
        tg.putString(rightX + 2, 5, "OS     : ");
        tg.setForegroundColor(HIGHLIGHT_COLOR);
        tg.putString(rightX + 11, 5, sysMon.getOsName());

        tg.setForegroundColor(TEXT_COLOR);
        tg.putString(rightX + 2, 6, "UPTIME : ");
        tg.setForegroundColor(HIGHLIGHT_COLOR);
        tg.putString(rightX + 11, 6, sysMon.getUptime());

        tg.setForegroundColor(TEXT_COLOR);
        tg.putString(rightX + 2, 8, "BRANCH : ");
        tg.setForegroundColor(HIGHLIGHT_COLOR);
        tg.putString(rightX + 11, 8, envService.getGitBranch());
        
        tg.setForegroundColor(TEXT_COLOR);
        tg.putString(rightX + 2, 9, "WEATHER: ");
        tg.setForegroundColor(HIGHLIGHT_COLOR);
        tg.putString(rightX + 11, 9, envService.getWeather());

        tg.setForegroundColor(TEXT_COLOR);
        tg.putString(rightX + 2, 10, "FANS   : ");
        tg.setForegroundColor(HIGHLIGHT_COLOR);
        tg.putString(rightX + 11, 10, sysMon.getFanSpeed());

        long rx = sysMon.getNetworkDownloadSpeed();
        long tx = sysMon.getNetworkUploadSpeed();

        tg.setForegroundColor(TEXT_COLOR);
        tg.putString(rightX + 2, 12, "NET DWN: ");
        tg.setForegroundColor(HIGHLIGHT_COLOR);
        tg.putString(rightX + 11, 12, formatBytes(rx) + "/s");

        tg.setForegroundColor(TEXT_COLOR);
        tg.putString(rightX + 2, 13, "NET UP : ");
        tg.setForegroundColor(HIGHLIGHT_COLOR);
        tg.putString(rightX + 11, 13, formatBytes(tx) + "/s");

        // --- Bottom Sections ---
        int bottomY = 2 + statsHeight;
        int bottomHeight = height - bottomY - 3;
        
        if (bottomHeight > 6) {
            // --- Parasite Radar (Left Bottom) ---
            long now = System.currentTimeMillis();
            if (now - lastProcessUpdate > 2000) {
                cachedProcesses = sysMon.getTopProcesses(3);
                lastProcessUpdate = now;
            }

            tg.setForegroundColor(ALERT_COLOR);
            tg.putString(4, bottomY + 1, "[!] TOP CONSUMERS");
            
            int pY = bottomY + 3;
            for (int i = 0; i < cachedProcesses.size(); i++) {
                OSProcess p = cachedProcesses.get(i);
                String name = p.getName();
                if (name.length() > 15) name = name.substring(0, 15);
                
                // Note: getProcessCpuLoadCumulative() is easier for snapshot
                double pCpu = 100d * (p.getKernelTime() + p.getUserTime()) / p.getUpTime();
                
                String line = String.format("%d. %-15s (%.1f%%)", i + 1, name, pCpu);
                
                tg.setForegroundColor(i == 0 ? ALERT_COLOR : TEXT_COLOR);
                tg.putString(4, pY + i, line);
            }

            // --- Crypto Ticker (Right Bottom) ---
            Map<String, Double> prices = cryptoService.getPrices();
            int cY = bottomY + 2;
            String[] coins = {"bitcoin", "ethereum", "solana", "dogecoin", "monero"};
            String[] symbols = {"BTC", "ETH", "SOL", "DOGE", "XMR"};
            
            int maxPriceLen = rightWidth - 12; // Leave room for border and padding
            for (int i = 0; i < coins.length; i++) {
                if (cY + i >= bottomY + bottomHeight - 1) break;
                
                Double price = prices.getOrDefault(coins[i], 0.0);
                tg.setForegroundColor(TEXT_COLOR);
                tg.putString(rightX + 2, cY + i, String.format("%-4s : ", symbols[i]));
                
                String priceStr = String.format("$%,.2f", price);
                if (priceStr.length() > maxPriceLen) {
                    priceStr = priceStr.substring(0, maxPriceLen);
                }
                tg.setForegroundColor(HIGHLIGHT_COLOR);
                tg.putString(rightX + 9, cY + i, priceStr);
            }
        }

        // --- DRAW BORDERS LAST ---
        // Draw Main Border
        drawBox(0, 0, width, height, " TERMDASH SYSTEM V1.0");
        
        // Draw Section Borders
        drawBox(2, 2, leftWidth, statsHeight, " SYSTEM VITALS ");
        drawBox(rightX, 2, rightWidth, statsHeight, " NETWORK & ENV ");
        
        if (bottomHeight > 6) {
            drawBox(2, bottomY, leftWidth, bottomHeight, " PARASITE RADAR ");
            drawBox(rightX, bottomY, rightWidth, bottomHeight, " CRYPTO TICKER ");
        }

        // --- Footer / Status ---
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String footer = " STATUS: ONLINE | TIME: " + time + " | PRESS 'q' TO DISCONNECT ";
        tg.setForegroundColor(TEXT_COLOR);
        tg.putString((width - footer.length()) / 2, height - 2, footer);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    private void drawBox(int x, int y, int width, int height, String title) {
        tg.setForegroundColor(TEXT_COLOR);
        
        // Corners
        tg.setCharacter(x, y, Symbols.SINGLE_LINE_TOP_LEFT_CORNER);
        tg.setCharacter(x + width - 1, y, Symbols.SINGLE_LINE_TOP_RIGHT_CORNER);
        tg.setCharacter(x, y + height - 1, Symbols.SINGLE_LINE_BOTTOM_LEFT_CORNER);
        tg.setCharacter(x + width - 1, y + height - 1, Symbols.SINGLE_LINE_BOTTOM_RIGHT_CORNER);

        // Horizontal lines
        for (int i = x + 1; i < x + width - 1; i++) {
            tg.setCharacter(i, y, Symbols.SINGLE_LINE_HORIZONTAL);
            tg.setCharacter(i, y + height - 1, Symbols.SINGLE_LINE_HORIZONTAL);
        }

        // Vertical lines
        for (int i = y + 1; i < y + height - 1; i++) {
            tg.setCharacter(x, i, Symbols.SINGLE_LINE_VERTICAL);
            tg.setCharacter(x + width - 1, i, Symbols.SINGLE_LINE_VERTICAL);
        }

        // Title
        if (title != null && !title.isEmpty()) {
            tg.setForegroundColor(HIGHLIGHT_COLOR);
            tg.putString(x + 2, y, title);
            tg.setForegroundColor(TEXT_COLOR);
        }
    }

    private void drawProgressBar(int x, int y, int width, String label, double percentage) {
        // Label
        tg.setForegroundColor(TEXT_COLOR);
        tg.putString(x, y, label);
        String percentStr = df.format(percentage * 100) + "%";
        tg.putString(x + width - percentStr.length(), y, percentStr);

        // Bar
        int barWidth = width;
        int filledWidth = (int) (barWidth * percentage);
        
        // Draw empty bar
        tg.setForegroundColor(TextColor.ANSI.BLACK_BRIGHT); // Dark Grey
        for (int i = 0; i < barWidth; i++) {
            tg.setCharacter(x + i, y + 1, Symbols.BLOCK_MIDDLE);
        }

        // Draw filled bar
        for (int i = 0; i < filledWidth; i++) {
            if (percentage > 0.9) tg.setForegroundColor(ALERT_COLOR);
            else if (percentage > 0.7) tg.setForegroundColor(TextColor.ANSI.YELLOW);
            else tg.setForegroundColor(HIGHLIGHT_COLOR);
            
            tg.setCharacter(x + i, y + 1, Symbols.BLOCK_SOLID);
        }
    }
}
