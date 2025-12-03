package com.termdash;

import com.termdash.ui.Dashboard;

import java.io.IOException;

public class TermDash {
    public static void main(String[] args) {
        try {
            Dashboard dashboard = new Dashboard();
            dashboard.run();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
