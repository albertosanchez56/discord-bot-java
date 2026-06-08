package com.main.panel.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Taskbar;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.imageio.ImageIO;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

/**
 * BertiniBot V2 control panel - native Java Swing GUI.
 *
 * <p>Launched via {@code javaw -jar bertinibot-*-panel.jar}, which is GUI
 * subsystem and so never shows a console window. All bot management calls
 * (start, stop, restart, autostart, status, log tailing) are implemented in
 * pure Java with no PowerShell or VBScript intermediary, so antivirus
 * heuristics have nothing suspicious to flag.</p>
 */
public final class PanelApp {

    /* ---- Palette (matches the PowerShell panel) ---------------------- */
    private static final Color BG       = new Color(24, 26, 31);
    private static final Color PANEL    = new Color(34, 37, 44);
    private static final Color LOG_BG   = new Color(18, 20, 24);
    private static final Color TEXT     = new Color(220, 222, 226);
    private static final Color MUTED    = new Color(155, 160, 170);
    private static final Color GREEN    = new Color(46, 204, 113);
    private static final Color RED      = new Color(231, 76, 60);
    private static final Color YELLOW   = new Color(241, 196, 15);
    private static final Color BLUE     = new Color(52, 152, 219);
    private static final Color GRAY     = new Color(99, 105, 117);

    private final BotPaths paths = BotPaths.detect();
    private final BotProcessManager bot = new BotProcessManager(paths);
    private final ScheduledTaskInstaller task = new ScheduledTaskInstaller(paths);
    private final LogTailer tailer = new LogTailer(paths.logFile());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "panel-worker");
        t.setDaemon(true);
        return t;
    });

    private JFrame frame;
    private JLabel statusDot;
    private JLabel statusTitle;
    private JLabel statusDetail;
    private JTextArea logArea;
    private JLabel footer;
    private JButton btnStart;
    private JButton btnStop;
    private JButton btnRestart;
    private JButton btnLogs;
    private JButton btnAutostart;
    private JButton btnClear;

    public static void main(String[] args) {
        // Use Nimbus when available so checkboxes/scroll bars look modern
        // on the dark background; fall back silently otherwise.
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new PanelApp().show());
    }

    private void show() {
        frame = new JFrame("BertiniBot V2 - Control Panel");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setSize(new Dimension(960, 660));
        frame.setMinimumSize(new Dimension(720, 480));
        frame.setLocationRelativeTo(null);
        frame.getContentPane().setBackground(BG);
        applyCustomIcon();

        frame.add(buildHeader(), BorderLayout.NORTH);
        frame.add(buildLogPanel(), BorderLayout.CENTER);
        frame.add(buildFooter(), BorderLayout.SOUTH);

        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                statusTimer.stop();
                logTimer.stop();
                worker.shutdownNow();
            }
        });

        frame.setVisible(true);

        // Initial paint + start polling.
        refreshStatusAsync();
        pollLogs();
        statusTimer.start();
        logTimer.start();
    }

    /* ====================================================================
       Layout
       ==================================================================== */

    private JPanel buildHeader() {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(BG);

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(PANEL);
        header.setPreferredSize(new Dimension(0, 88));
        header.setBorder(BorderFactory.createEmptyBorder(12, 18, 12, 18));

        statusDot = new JLabel("\u25CF");
        statusDot.setFont(new Font("Segoe UI", Font.BOLD, 28));
        statusDot.setForeground(GRAY);
        statusDot.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 14));
        header.add(statusDot, BorderLayout.WEST);

        JPanel text = new JPanel(new GridLayout(2, 1));
        text.setOpaque(false);

        statusTitle = new JLabel("Comprobando estado...");
        statusTitle.setFont(new Font("Segoe UI Semibold", Font.BOLD, 16));
        statusTitle.setForeground(TEXT);
        text.add(statusTitle);

        statusDetail = new JLabel(" ");
        statusDetail.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusDetail.setForeground(MUTED);
        text.add(statusDetail);

        header.add(text, BorderLayout.CENTER);

        wrap.add(header, BorderLayout.NORTH);
        wrap.add(buildToolbar(), BorderLayout.SOUTH);
        return wrap;
    }

    private JPanel buildToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 12));
        toolbar.setBackground(BG);

        btnStart     = flatButton("\u25B6  Start",     GREEN);
        btnStop      = flatButton("\u25A0  Stop",      RED);
        btnRestart   = flatButton("\u21BB  Restart",   YELLOW);
        btnLogs      = flatButton("Carpeta logs",      GRAY);
        btnAutostart = flatButton("Auto-arranque",     BLUE);
        btnClear     = flatButton("Limpiar vista",     GRAY);

        btnStart.addActionListener(e -> runAction("Arrancando...",      bot::start));
        btnStop.addActionListener(e -> runAction("Parando...",           bot::stop));
        btnRestart.addActionListener(e -> runAction("Reiniciando...",    bot::restart));
        btnAutostart.addActionListener(e -> runAction("Aplicando autostart...", () ->
                task.isInstalled() ? task.uninstall() : task.install()));
        btnLogs.addActionListener(e -> openLogsFolder());
        btnClear.addActionListener(e -> {
            logArea.setText("");
            tailer.resetToEnd();
            toast("Vista de logs limpiada (el archivo no se ha tocado).", MUTED);
        });

        toolbar.add(btnStart);
        toolbar.add(btnStop);
        toolbar.add(btnRestart);
        toolbar.add(btnLogs);
        toolbar.add(btnAutostart);
        toolbar.add(btnClear);
        return toolbar;
    }

    private JScrollPane buildLogPanel() {
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setLineWrap(false);
        logArea.setBackground(LOG_BG);
        logArea.setForeground(TEXT);
        logArea.setCaretColor(TEXT);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(LOG_BG);
        return scroll;
    }

    private JPanel buildFooter() {
        JPanel f = new JPanel(new BorderLayout());
        f.setBackground(PANEL);
        f.setPreferredSize(new Dimension(0, 26));
        footer = new JLabel("Logs en vivo desde " + paths.logFile());
        footer.setForeground(MUTED);
        footer.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        footer.setHorizontalAlignment(SwingConstants.RIGHT);
        footer.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 14));
        f.add(footer, BorderLayout.CENTER);
        return f;
    }

    private JButton flatButton(String label, Color color) {
        JButton b = new JButton(label);
        b.setFocusPainted(false);
        b.setBackground(color);
        b.setForeground(Color.WHITE);
        b.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        b.setFont(new Font("Segoe UI Semibold", Font.BOLD, 12));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setContentAreaFilled(true);
        b.setOpaque(true);
        return b;
    }

    /* ====================================================================
       Behavior
       ==================================================================== */

    /** Returns true if any worker action is currently in flight. */
    private volatile boolean busy = false;

    private interface Action { BotProcessManager.Result run(); }

    private void runAction(String pendingMsg, Action action) {
        if (busy) return;
        busy = true;
        setButtonsEnabled(false);
        toast(pendingMsg, YELLOW);
        worker.submit(() -> {
            BotProcessManager.Result r;
            try { r = action.run(); }
            catch (Exception ex) { r = new BotProcessManager.Result(false, ex.getMessage()); }
            BotProcessManager.Result fr = r;
            SwingUtilities.invokeLater(() -> {
                toast(fr.message(), fr.success() ? GREEN : RED);
                refreshStatusAsync();
                busy = false;
                setButtonsEnabled(true);
            });
        });
    }

    private void setButtonsEnabled(boolean enabled) {
        btnStart.setEnabled(enabled);
        btnStop.setEnabled(enabled);
        btnRestart.setEnabled(enabled);
        btnAutostart.setEnabled(enabled);
    }

    private final Timer statusTimer = new Timer(2500, e -> refreshStatusAsync());
    private final Timer logTimer    = new Timer(750,  e -> pollLogs());

    private void refreshStatusAsync() {
        worker.submit(() -> {
            boolean installed = task.isInstalled();
            BotStatus s = bot.status(installed);
            SwingUtilities.invokeLater(() -> applyStatus(s));
        });
    }

    private void applyStatus(BotStatus s) {
        StringBuilder detail = new StringBuilder();
        if (s.running()) {
            statusDot.setForeground(GREEN);
            statusTitle.setText("ONLINE");
            statusTitle.setForeground(GREEN);
            detail.append("PID ").append(s.pid());
            if (s.memoryMb() > 0) {
                detail.append("   |   ").append(String.format("%.1f MB", s.memoryMb()));
            }
            s.uptime().ifPresent(d -> detail.append("   |   ").append(formatUptime(d)));
        } else {
            statusDot.setForeground(RED);
            statusTitle.setText("DETENIDO");
            statusTitle.setForeground(RED);
            detail.append("El bot no esta corriendo.");
        }
        detail.append("   |   Auto-arranque: ").append(s.taskInstalled() ? "ACTIVO" : "inactivo");
        statusDetail.setText(detail.toString());

        if (s.taskInstalled()) {
            btnAutostart.setText("Desactivar autostart");
            btnAutostart.setBackground(GRAY);
        } else {
            btnAutostart.setText("Activar autostart");
            btnAutostart.setBackground(BLUE);
        }
    }

    private static String formatUptime(Duration d) {
        long h = d.toHours();
        long m = d.toMinutesPart();
        long s = d.toSecondsPart();
        return "uptime " + h + "h " + m + "m " + s + "s";
    }

    private void pollLogs() {
        LogTailer.Snapshot snap = tailer.poll();
        if (snap.rotated()) logArea.setText("");
        if (!snap.newText().isEmpty()) {
            logArea.append(snap.newText());
            // Cap the in-memory buffer so a long-running panel doesn't grow
            // forever (the log file on disk is intact).
            int maxChars = 400_000;
            if (logArea.getDocument().getLength() > maxChars) {
                try {
                    logArea.getDocument().remove(0,
                            logArea.getDocument().getLength() - maxChars);
                } catch (Exception ignored) {}
            }
            logArea.setCaretPosition(logArea.getDocument().getLength());
        }
    }

    private void openLogsFolder() {
        try {
            if (!Files.exists(paths.logDir())) {
                Files.createDirectories(paths.logDir());
            }
            Desktop.getDesktop().open(paths.logDir().toFile());
        } catch (Exception e) {
            toast("No se pudo abrir la carpeta: " + e.getMessage(), RED);
        }
    }

    private void toast(String msg, Color color) {
        footer.setText(msg.replace("\n", "  |  "));
        footer.setForeground(color);
    }

    /**
     * Lets the user replace the default Java cup icon by dropping
     * {@code icon.png} into the bot root. PNG is the simplest format that
     * Java's built-in {@link ImageIO} understands; for the Windows taskbar
     * shortcut a separate {@code icon.ico} is picked up by the launcher
     * script (Windows requires {@code .ico} for {@code .lnk} icons).
     */
    private void applyCustomIcon() {
        Path png = paths.root().resolve("icon.png");
        if (!Files.isRegularFile(png)) return;
        try {
            Image img = ImageIO.read(png.toFile());
            if (img == null) return;
            frame.setIconImage(img);
            // Sets the dock / Unity launcher icon on macOS/Linux; no-op on
            // Windows (the taskbar icon there is taken from the .lnk).
            try { Taskbar.getTaskbar().setIconImage(img); }
            catch (UnsupportedOperationException | SecurityException ignored) {}
        } catch (Exception ignored) {
            // Bad PNG / unreadable file: fall back to the default icon.
        }
    }
}
