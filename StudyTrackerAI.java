
import java.net.URL;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class StudyTrackerAI {
    // File paths
    private static final Path BASE_DIR = Paths.get(System.getProperty("user.dir"), "StudyTrackerAI");
    private static final Path DATA_DIR = BASE_DIR.resolve("data");
    private static final Path USERS_JSON = DATA_DIR.resolve("users.json");
    private static final Path SUBJECTS_JSON = DATA_DIR.resolve("subjects.json");
    private static final Path NOTES_JSON = DATA_DIR.resolve("notes.json");
    private static final Path FLASHCARDS_JSON = DATA_DIR.resolve("flashcards.json");
    private static final Path TASKS_JSON = DATA_DIR.resolve("tasks.json");
    private static final Path SETTINGS_JSON = DATA_DIR.resolve("settings.json");

    private static String nowIso() {
        return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private static void showInfoLater(final String title, final String msg) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, msg, title, JOptionPane.INFORMATION_MESSAGE));
    }

    private final Storage storage = new Storage();
    private User currentUser;
    private JFrame mainFrame;
    private CardLayout cardLayout;
    private JPanel contentPanel;

    private final DefaultComboBoxModel<Subject> subjectComboModel = new DefaultComboBoxModel<>();
    private DefaultListModel<FlashcardDeck> deckModel;
    private JList<FlashcardDeck> deckList;

    private static final FocusManager focusManager = new FocusManager();

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                new StudyTrackerAI().start();
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "Startup error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void start() throws IOException {
        Files.createDirectories(DATA_DIR);
        if (Files.notExists(USERS_JSON)) Files.writeString(USERS_JSON, "[]", StandardCharsets.UTF_8);
        if (Files.notExists(SUBJECTS_JSON)) Files.writeString(SUBJECTS_JSON, "[]", StandardCharsets.UTF_8);
        if (Files.notExists(NOTES_JSON)) Files.writeString(NOTES_JSON, "[]", StandardCharsets.UTF_8);
        if (Files.notExists(FLASHCARDS_JSON)) Files.writeString(FLASHCARDS_JSON, "{\"decks\":[],\"cards\":[]}", StandardCharsets.UTF_8);
        if (Files.notExists(TASKS_JSON)) Files.writeString(TASKS_JSON, "[]", StandardCharsets.UTF_8);
        if (Files.notExists(SETTINGS_JSON)) Files.writeString(SETTINGS_JSON, "{\"local_ai_url\":\"\"}", StandardCharsets.UTF_8);
        storage.loadAll();
        showAuthDialog();
    }

    private void showAuthDialog() {
        JTabbedPane tabs = new JTabbedPane();

        JPanel loginPanel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.gridx = 0; c.gridy = 0; loginPanel.add(new JLabel("Username:"), c);
        c.gridx = 1; JTextField loginUser = new JTextField(16); loginPanel.add(loginUser, c);
        c.gridx = 0; c.gridy = 1; loginPanel.add(new JLabel("Password:"), c);
        c.gridx = 1; JPasswordField loginPass = new JPasswordField(16); loginPanel.add(loginPass, c);
        c.gridy = 2; c.gridx = 1;
        JLabel loginMsg = new JLabel(" ");
        JButton loginBtn = new JButton("Login");
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row.add(loginBtn); row.add(loginMsg);
        loginPanel.add(row, c);

        JPanel regPanel = new JPanel(new GridBagLayout());
        GridBagConstraints r = new GridBagConstraints();
        r.insets = new Insets(6, 6, 6, 6);
        r.gridx = 0; r.gridy = 0; regPanel.add(new JLabel("Username:"), r);
        r.gridx = 1; JTextField regUser = new JTextField(16); regPanel.add(regUser, r);
        r.gridx = 0; r.gridy = 1; regPanel.add(new JLabel("Password:"), r);
        r.gridx = 1; JPasswordField regPass = new JPasswordField(16); regPanel.add(regPass, r);
        r.gridy = 2; r.gridx = 1;
        JLabel regMsg = new JLabel(" ");
        JButton regBtn = new JButton("Register");
        JPanel regRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        regRow.add(regBtn); regRow.add(regMsg);
        regPanel.add(regRow, r);

        tabs.addTab("Login", loginPanel);
        tabs.addTab("Register", regPanel);

        JFrame auth = new JFrame("StudyTrackerAI - Login/Register");
        auth.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        auth.getContentPane().add(tabs);
        auth.pack();
        auth.setLocationRelativeTo(null);
        auth.setVisible(true);

        loginBtn.addActionListener(evt -> {
            String u = loginUser.getText().trim();
            String p = new String(loginPass.getPassword());
            Optional<User> opt = storage.users.stream().filter(x -> x.username.equalsIgnoreCase(u)).findFirst();
            if (opt.isEmpty()) {
                loginMsg.setText("User not found");
                return;
            }
            if (!Objects.equals(opt.get().passwordHash, HashUtil.sha256(p))) {
                loginMsg.setText("Invalid credentials");
                return;
            }
            currentUser = opt.get();
            refreshSubjectsModel();
            auth.dispose();
            SwingUtilities.invokeLater(this::buildMainFrame);
        });

        regBtn.addActionListener(evt -> {
            String u = regUser.getText().trim();
            String p = new String(regPass.getPassword());
            if (u.isBlank() || p.isBlank()) { regMsg.setText("Required"); return; }
            boolean exists = storage.users.stream().anyMatch(x -> x.username.equalsIgnoreCase(u));
            if (exists) { regMsg.setText("Exists"); return; }
            User nu = new User(UUID.randomUUID().toString(), u, HashUtil.sha256(p));
            storage.users.add(nu);
            storage.saveUsers();
            regMsg.setText("Registered");
        });
    }

    private void buildMainFrame() {
        mainFrame = new JFrame("StudyTrackerAI");
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setSize(1100, 720);
        mainFrame.setLocationRelativeTo(null);

        JPanel topNav = new JPanel(new BorderLayout());
        topNav.setBorder(new EmptyBorder(6, 12, 6, 12));
        topNav.setBackground(new Color(0x2b2f36));
        JLabel title = new JLabel("StudyTrackerAI", SwingConstants.LEFT);
        title.setForeground(Color.WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        title.setBorder(new EmptyBorder(6,6,6,6));
        topNav.add(title, BorderLayout.WEST);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        btnRow.setBackground(new Color(0x2b2f36));

        String[] names = {"Dashboard","Subjects","Notes","Flashcards","Tasks","AI Assistant","Focus","Settings","Logout"};
        cardLayout = new CardLayout();
        contentPanel = new JPanel(cardLayout);

        contentPanel.add(wrapCard(buildDashboardView()), "Dashboard");
        contentPanel.add(wrapCard(buildSubjectsView()), "Subjects");
        contentPanel.add(wrapCard(buildNotesView()), "Notes");
        contentPanel.add(wrapCard(buildFlashcardsView()), "Flashcards");
        contentPanel.add(wrapCard(buildTasksView()), "Tasks");
        contentPanel.add(wrapCard(buildAIView()), "AI Assistant");
        contentPanel.add(wrapCard(buildFocusView()), "Focus");
        contentPanel.add(wrapCard(buildSettingsView()), "Settings");

        for (String n : names) {
            JButton b = new JButton(n);
            b.setPreferredSize(new Dimension(130, 34));
            b.setBackground(new Color(0x3a3f46));
            b.setForeground(Color.WHITE);
            b.setFocusPainted(false);
            b.setBorder(BorderFactory.createEmptyBorder(6,12,6,12));
            b.addActionListener(evt -> {
                if (n.equals("Logout")) {
                    int ok = JOptionPane.showConfirmDialog(mainFrame, "Logout and return to login?", "Logout", JOptionPane.YES_NO_OPTION);
                    if (ok == JOptionPane.YES_OPTION) {
                        try { storage.saveAll(); } catch (Exception ignore) {}
                        mainFrame.dispose();
                        currentUser = null;
                        showAuthDialog();
                    }
                } else {
                    cardLayout.show(contentPanel, n);
                }
            });
            btnRow.add(b);
        }

        topNav.add(btnRow, BorderLayout.CENTER);

        JLabel status = new JLabel("Logged in as: " + (currentUser != null ? currentUser.username : "n/a"));
        status.setBorder(new EmptyBorder(6,10,6,10));

        mainFrame.getContentPane().setLayout(new BorderLayout());
        mainFrame.getContentPane().add(topNav, BorderLayout.NORTH);
        mainFrame.getContentPane().add(contentPanel, BorderLayout.CENTER);
        mainFrame.getContentPane().add(status, BorderLayout.SOUTH);

        mainFrame.setVisible(true);

        SwingUtilities.invokeLater(this::notifyDecksUpdated);
    }

    // Helper to wrap each view into a large centered rectangular card
    private JPanel wrapCard(JComponent inner) {
        JPanel outer = new JPanel(new GridBagLayout());
        outer.setBackground(new Color(0xf0f2f5));
        JPanel card = new JPanel(new BorderLayout());
        card.setPreferredSize(new Dimension(820, 520));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xc8d0d8), 2),
                BorderFactory.createEmptyBorder(12,12,12,12)
        ));
        card.setBackground(Color.WHITE);
        card.add(inner, BorderLayout.CENTER);
        outer.add(card);
        return outer;
    }

    // Dashboard: show rectangular blocks for each main area
    private JPanel buildDashboardView() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(new Color(0xffffff));
        JPanel grid = new JPanel(new GridLayout(2, 3, 16, 16));
        grid.setBorder(new EmptyBorder(24,24,24,24));
        String[] blocks = {"Subjects", "Notes", "Flashcards", "Tasks", "AI Assistant", "Focus"};
        for (String b : blocks) {
            JButton block = new JButton("<html><div style='text-align:center; font-size:14pt;'>" + b + "</div></html>");
            block.setBackground(new Color(0x3a8bd6));
            block.setForeground(Color.WHITE);
            block.setFocusPainted(false);
            block.setBorder(BorderFactory.createLineBorder(new Color(0x2b6fa0), 2));
            block.addActionListener(e -> cardLayout.show(contentPanel, b));
            grid.add(block);
        }
        root.add(grid, BorderLayout.CENTER);
        return root;
    }

    // Use qualified javax.swing.Timer to avoid ambiguity
    static class ClockPanel extends JPanel {
        private final javax.swing.Timer timer;
        ClockPanel() {
            setPreferredSize(new Dimension(360, 360));
            setBackground(new Color(0xf5f7f9));
            timer = new javax.swing.Timer(1000, e -> repaint());
            timer.start();
            addHierarchyListener(e -> {
                if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0) {
                    if (!isDisplayable()) timer.stop();
                    else timer.start();
                }
            });
        }
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            int w = getWidth(), h = getHeight();
            int size = Math.min(w, h) - 20;
            int cx = w/2, cy = h/2;
            int r = size/2;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(0xffffff));
            g2.fillOval(cx - r, cy - r, 2*r, 2*r);
            g2.setColor(new Color(0x2b2f36));
            g2.setStroke(new BasicStroke(3f));
            g2.drawOval(cx - r, cy - r, 2*r, 2*r);
            for (int i=0;i<60;i++) {
                double angle = Math.toRadians(i * 6 - 90);
                int inner = r - (i%5==0 ? 14 : 8);
                int x1 = cx + (int)(inner * Math.cos(angle));
                int y1 = cy + (int)(inner * Math.sin(angle));
                int x2 = cx + (int)(r * Math.cos(angle));
                int y2 = cy + (int)(r * Math.sin(angle));
                g2.setStroke(new BasicStroke(i%5==0?3f:1.5f));
                g2.drawLine(x1,y1,x2,y2);
            }
            LocalDateTime now = LocalDateTime.now();
            int hour = now.getHour();
            int minute = now.getMinute();
            int second = now.getSecond();
            double sAngle = Math.toRadians(second * 6 - 90);
            double mAngle = Math.toRadians((minute + second/60.0) * 6 - 90);
            double hAngle = Math.toRadians(((hour % 12) + minute/60.0) * 30 - 90);
            g2.setColor(new Color(0x2b2f36));
            g2.setStroke(new BasicStroke(6f));
            int hx = cx + (int)((r*0.5) * Math.cos(hAngle));
            int hy = cy + (int)((r*0.5) * Math.sin(hAngle));
            g2.drawLine(cx, cy, hx, hy);
            g2.setStroke(new BasicStroke(4f));
            int mx = cx + (int)((r*0.75) * Math.cos(mAngle));
            int my = cy + (int)((r*0.75) * Math.sin(mAngle));
            g2.drawLine(cx, cy, mx, my);
            g2.setColor(Color.RED);
            g2.setStroke(new BasicStroke(2f));
            int sx = cx + (int)((r*0.85) * Math.cos(sAngle));
            int sy = cy + (int)((r*0.85) * Math.sin(sAngle));
            g2.drawLine(cx, cy, sx, sy);
            g2.setColor(new Color(0x2b2f36));
            g2.fillOval(cx-6, cy-6, 12, 12);
            g2.dispose();
        }
    }

    // Subjects view
    private JPanel buildSubjectsView() {
        JPanel root = new JPanel(new BorderLayout(8,8));
        root.setBorder(new EmptyBorder(10,10,10,10));

        DefaultListModel<Subject> listModel = new DefaultListModel<>();
        for (Subject s : storage.subjects) if (s.userId.equals(currentUser.id)) listModel.addElement(s);
        JList<Subject> list = new JList<>(listModel);
        list.setPreferredSize(new Dimension(260,0));

        JTextField nameField = new JTextField(20);
        JButton add = new JButton("Add Subject");
        add.addActionListener(e -> {
            String nm = nameField.getText().trim();
            if (nm.isEmpty()) return;
            Subject s = new Subject(UUID.randomUUID().toString(), currentUser.id, nm, 0.0);
            storage.subjects.add(s);
            storage.saveSubjects();
            listModel.addElement(s);
            refreshSubjectsModel();
            nameField.setText("");
        });

        JButton del = new JButton("Delete");
        del.addActionListener(e -> {
            Subject s = list.getSelectedValue();
            if (s == null) return;
            storage.subjects.removeIf(x -> x.id.equals(s.id));
            storage.saveSubjects();
            listModel.removeElement(s);
            refreshSubjectsModel();
        });

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Subject:")); top.add(nameField); top.add(add); top.add(del);

        root.add(top, BorderLayout.NORTH);
        root.add(new JScrollPane(list), BorderLayout.CENTER);
        return root;
    }

    private void refreshSubjectsModel() {
        SwingUtilities.invokeLater(() -> {
            subjectComboModel.removeAllElements();
            if (currentUser == null) return;
            for (Subject s : storage.subjects) if (s.userId.equals(currentUser.id)) subjectComboModel.addElement(s);
        });
    }

    // Notes view (shares subjectComboModel)
    private JPanel buildNotesView() {
        JPanel root = new JPanel(new BorderLayout(8,8));
        root.setBorder(new EmptyBorder(10,10,10,10));

        JComboBox<Subject> subjectCombo = new JComboBox<>(subjectComboModel);
        JButton newBtn = new JButton("New Note");
        JButton delBtn = new JButton("Delete Note");
        JButton saveBtn = new JButton("Save Note (Generate Flashcards)");
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT,6,6));
        top.add(new JLabel("Subject:")); top.add(subjectCombo); top.add(newBtn); top.add(delBtn); top.add(saveBtn);

        DefaultListModel<Note> noteListModel = new DefaultListModel<>();
        JList<Note> noteList = new JList<>(noteListModel);
        JScrollPane leftPane = new JScrollPane(noteList);
        leftPane.setPreferredSize(new Dimension(300, 0));

        JEditorPane editor = new JEditorPane();
        editor.setContentType("text/html");
        JScrollPane editorPane = new JScrollPane(editor);

        subjectCombo.addActionListener(e -> {
            Subject s = (Subject) subjectCombo.getSelectedItem();
            noteListModel.clear();
            if (s == null) return;
            for (Note n : storage.notes) if (n.userId.equals(currentUser.id) && n.subjectId.equals(s.id)) noteListModel.addElement(n);
            editor.setText("");
        });

        noteList.addListSelectionListener((ListSelectionEvent e) -> {
            Note n = noteList.getSelectedValue();
            if (n != null) editor.setText(n.htmlContent);
            else editor.setText("");
        });

        newBtn.addActionListener(e -> {
            Subject s = (Subject) subjectCombo.getSelectedItem();
            if (s == null) { JOptionPane.showMessageDialog(mainFrame, "Select a subject first."); return; }
            String title = nextNoteTitle(s);
            Note n = new Note(UUID.randomUUID().toString(), currentUser.id, s.id, title, "<p></p>", nowIso());
            storage.notes.add(n);
            noteListModel.addElement(n);
            noteList.setSelectedValue(n, true);
            storage.saveNotes();
        });

        delBtn.addActionListener(e -> {
            int idx = noteList.getSelectedIndex();
            if (idx < 0) return;
            Note n = noteListModel.getElementAt(idx);
            storage.notes.removeIf(x -> x.id.equals(n.id));
            noteListModel.remove(idx);
            editor.setText("");
            storage.saveNotes();
        });

        saveBtn.addActionListener(e -> {
            Note n = noteList.getSelectedValue(); if (n == null) return;
            n.htmlContent = editor.getText();
            n.updatedAt = nowIso();
            storage.saveNotes();

            // Validate note content for weird characters before generating
            String plain = HtmlUtil.stripTags(n.htmlContent);
            // remove bullets/list-lines so generator ignores them
            String cleaned = HtmlUtil.removeBullets(plain);

            if (!isValidNoteText(cleaned)) {
                // find first illegal character and present details
                int pos = findFirstIllegalCharIndex(cleaned);
                char ch = pos >= 0 && pos < cleaned.length() ? cleaned.charAt(pos) : '?';
                String msg = "Cannot generate flashcards: note contains unsupported or control characters.\n" +
                        "First illegal char: '" + ch + "' U+" + String.format("%04X", (int)ch) + " at pos " + pos;
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(mainFrame, msg, "Generation Error", JOptionPane.ERROR_MESSAGE));
                return;
            }

            // Generate flashcards using local AI if configured, else fall back to heuristics.
            CompletableFuture.supplyAsync(() -> {
                List<FlashcardGenerator.Flashcard> aiGenerated = LocalAIClient.tryGenerate(cleaned, 8);
                List<FlashcardGenerator.Flashcard> generated;
                if (aiGenerated != null && !aiGenerated.isEmpty()) {
                    generated = aiGenerated;
                } else {
                    generated = FlashcardGenerator.generateFlashcards(cleaned, 8, false, true, 4);
                }

                List<FlashcardGenerator.Flashcard> validated = new ArrayList<>();
                Set<String> seen = new HashSet<>();
                for (FlashcardGenerator.Flashcard f : generated) {
                    if (f == null || f.answer == null) continue;
                    String ans = f.answer.trim();
                    if (ans.length() < 8) continue;
                    if (ans.replaceAll("[^A-Za-z]", "").length() < 3) continue;
                    if (ans.split("\\s+").length < 2) continue;
                    String key = ans.toLowerCase();
                    if (seen.contains(key)) continue;
                    seen.add(key);
                    validated.add(f);
                    if (validated.size() >= 8) break;
                }
                if (validated.isEmpty()) return Map.of("created", 0, "deckName", "", "error", "No valid flashcards could be generated from this note. Try adding more explanatory text or check for unsupported characters.");
                String deckName = nextDeckName(n);
                FlashcardDeck deck = new FlashcardDeck(UUID.randomUUID().toString(), currentUser.id, deckName);
                storage.decks.add(deck);
                for (FlashcardGenerator.Flashcard f : validated) {
                    FlashcardCard c = new FlashcardCard(UUID.randomUUID().toString(), deck.id, f.question, f.answer, nowIso(), f.choices);
                    storage.cards.add(c);
                }
                storage.saveFlashcards();
                return Map.of("created", validated.size(), "deckName", deck.name, "error", "");
            }).thenAccept(res -> {
                SwingUtilities.invokeLater(() -> {
                    notifyDecksUpdated();
                    int created = ((Long) res.get("created")).intValue();
                    String deckName = (String) res.get("deckName");
                    String err = (String) res.get("error");
                    if (created <= 0) {
                        JOptionPane.showMessageDialog(mainFrame, err, "Generation Error", JOptionPane.ERROR_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(mainFrame, "Generated " + created + " flashcards in deck: " + deckName, "Generation Complete", JOptionPane.INFORMATION_MESSAGE);
                    }
                });
            }).exceptionally(ex -> {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(mainFrame, "Generation failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE));
                ex.printStackTrace();
                return null;
            });
        });

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPane, editorPane);
        split.setResizeWeight(0.28);
        root.add(top, BorderLayout.NORTH);
        root.add(split, BorderLayout.CENTER);
        return root;
    }

    private boolean isValidNoteText(String text) {
        if (text == null) return true;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\t' || ch == '\n' || ch == '\r') continue;
            if ((ch >= 32 && ch <= 126) || (ch >= 160 && ch <= 591)) continue;
            return false;
        }
        return true;
    }

    private int findFirstIllegalCharIndex(String text) {
        if (text == null) return -1;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\t' || ch == '\n' || ch == '\r') continue;
            if ((ch >= 32 && ch <= 126) || (ch >= 160 && ch <= 591)) continue;
            return i;
        }
        return -1;
    }

    // Generate a next deck name based on whether the note has a title.
    // If note has a non-blank title -> "deckN"
    // If note has blank title -> "untitledN"
    private String nextDeckName(Note n) {
        String base = (n.title != null && !n.title.isBlank()) ? "deck" : "untitled";
        int max = 0;
        for (FlashcardDeck d : storage.decks) {
            if (!d.userId.equals(currentUser.id)) continue;
            String name = d.name.toLowerCase();
            if (name.startsWith(base)) {
                String num = name.substring(base.length()).replaceAll("[^0-9]", "");
                if (!num.isEmpty()) {
                    try { max = Math.max(max, Integer.parseInt(num)); } catch (Exception ignored) {}
                }
            }
        }
        return base + (max + 1);
    }

    // Generate next note title for a subject: note1, note2, ...
    private String nextNoteTitle(Subject s) {
        if (s == null) return "note1";
        int max = 0;
        for (Note n : storage.notes) {
            if (!n.userId.equals(currentUser.id)) continue;
            if (!Objects.equals(n.subjectId, s.id)) continue;
            String title = n.title == null ? "" : n.title.toLowerCase();
            if (title.startsWith("note")) {
                String num = title.substring(4).replaceAll("[^0-9]", "");
                if (!num.isEmpty()) {
                    try { max = Math.max(max, Integer.parseInt(num)); } catch (Exception ignored) {}
                }
            }
        }
        return "note" + (max + 1);
    }

    // New static helper used for validating note characters on load.
    // Only allow basic printable ASCII (space through '~') plus standard whitespace (tab/newline/carriage).
    // If you want to permit extended Unicode letters, expand the allowed ranges here.
    private static boolean isNoteTextAllowedOnLoad(String text) {
        if (text == null) return true;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            // Allow usual whitespace
            if (ch == '\t' || ch == '\n' || ch == '\r' || ch == ' ') continue;
            // Allow basic printable ASCII (numbers, letters, punctuation, symbols)
            if (ch >= 32 && ch <= 126) continue;
            // Otherwise, disallow
            return false;
        }
        return true;
    }

    // Flashcards view
    private JPanel buildFlashcardsView() {
        JPanel root = new JPanel(new BorderLayout(8,8));
        root.setBorder(new EmptyBorder(10,10,10,10));

        deckModel = new DefaultListModel<>();
        for (FlashcardDeck d : storage.decks) if (d.userId.equals(currentUser.id)) deckModel.addElement(d);
        deckList = new JList<>(deckModel);
        deckList.setPreferredSize(new Dimension(260,0));
        DefaultListModel<FlashcardCard> cardModel = new DefaultListModel<>();
        JList<FlashcardCard> cardList = new JList<>(cardModel);

        deckList.addListSelectionListener(e -> {
            FlashcardDeck d = deckList.getSelectedValue();
            cardModel.clear();
            if (d == null) return;
            for (FlashcardCard c : storage.cards) if (c.deckId.equals(d.id)) cardModel.addElement(c);
        });

        JButton studyBtn = new JButton("Study Selected Deck");
        studyBtn.addActionListener(e -> {
            FlashcardDeck d = deckList.getSelectedValue();
            if (d == null) { JOptionPane.showMessageDialog(mainFrame, "Select deck"); return; }
            List<FlashcardCard> cards = new ArrayList<>();
            for (FlashcardCard c : storage.cards) if (c.deckId.equals(d.id)) cards.add(c);
            if (cards.isEmpty()) { JOptionPane.showMessageDialog(mainFrame, "No cards in deck"); return; }
            flashcardStudyDialog(cards);
        });

        JButton addDeck = new JButton("Add Deck");
        addDeck.addActionListener(e -> {
            String nm = JOptionPane.showInputDialog(mainFrame, "Deck name:");
            if (nm == null || nm.isBlank()) return;
            FlashcardDeck d = new FlashcardDeck(UUID.randomUUID().toString(), currentUser.id, nm);
            storage.decks.add(d);
            deckModel.addElement(d);
            storage.saveFlashcards();
        });

        JButton delDeck = new JButton("Delete Deck");
        delDeck.addActionListener(e -> {
            FlashcardDeck d = deckList.getSelectedValue(); if (d == null) return;
            storage.cards.removeIf(c -> c.deckId.equals(d.id));
            storage.decks.removeIf(x -> x.id.equals(d.id));
            deckModel.removeElement(d);
            cardModel.clear();
            storage.saveFlashcards();
        });

        JPanel left = new JPanel(new BorderLayout(6,6));
        left.add(new JLabel("Decks"), BorderLayout.NORTH);
        left.add(new JScrollPane(deckList), BorderLayout.CENTER);
        JPanel leftBtns = new JPanel(new FlowLayout(FlowLayout.LEFT,6,6)); leftBtns.add(addDeck); leftBtns.add(delDeck); leftBtns.add(studyBtn); left.add(leftBtns, BorderLayout.SOUTH);

        JPanel center = new JPanel(new BorderLayout(6,6));
        center.add(new JLabel("Cards"), BorderLayout.NORTH);
        center.add(new JScrollPane(cardList), BorderLayout.CENTER);

        root.add(left, BorderLayout.WEST);
        root.add(center, BorderLayout.CENTER);
        return root;
    }

    private void notifyDecksUpdated() {
        if (deckModel == null) return;
        deckModel.clear();
        for (FlashcardDeck d : storage.decks) if (d.userId.equals(currentUser.id)) deckModel.addElement(d);
    }

    private void flashcardStudyDialog(List<FlashcardCard> cards) {
        if (cards == null || cards.isEmpty()) { JOptionPane.showMessageDialog(mainFrame, "No cards"); return; }
        List<FlashcardCard> queue = new ArrayList<>(cards);
        Collections.shuffle(queue);
        JDialog dialog = new JDialog(mainFrame, "Study", true);
        dialog.setSize(700, 420);
        dialog.setLocationRelativeTo(mainFrame);

        JPanel root = new JPanel(new BorderLayout(8,8));
        JTextArea frontArea = new JTextArea(); frontArea.setEditable(false); frontArea.setLineWrap(true); frontArea.setWrapStyleWord(true);
        JTextArea backArea = new JTextArea(); backArea.setEditable(false); backArea.setLineWrap(true); backArea.setWrapStyleWord(true);
        backArea.setVisible(false);

        JPanel center = new JPanel(new GridLayout(2,1,6,6));
        center.add(new JScrollPane(frontArea));
        center.add(new JScrollPane(backArea));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 12));
        JButton flip = new JButton("Show Answer");
        JButton again = new JButton("Again");
        JButton good = new JButton("Good");
        JButton easy = new JButton("Easy");
        again.setEnabled(false); good.setEnabled(false); easy.setEnabled(false);

        actions.add(flip); actions.add(again); actions.add(good); actions.add(easy);

        root.add(center, BorderLayout.CENTER);
        root.add(actions, BorderLayout.SOUTH);

        final int[] idx = {0};

        Runnable showCard = () -> {
            if (idx[0] >= queue.size()) {
                dialog.dispose();
                JOptionPane.showMessageDialog(mainFrame, "Study session complete.");
                storage.saveFlashcards();
                return;
            }
            FlashcardCard c = queue.get(idx[0]);
            frontArea.setText(c.front);
            backArea.setText(c.back);
            backArea.setVisible(false);
            flip.setEnabled(true);
            again.setEnabled(false); good.setEnabled(false); easy.setEnabled(false);
        };

        flip.addActionListener(e -> {
            backArea.setVisible(true);
            flip.setEnabled(false);
            again.setEnabled(true); good.setEnabled(true); easy.setEnabled(true);
        });

        again.addActionListener(e -> { applySpacedRepetition(queue.get(idx[0]), 2); idx[0]++; showCard.run(); });
        good.addActionListener(e -> { applySpacedRepetition(queue.get(idx[0]), 4); idx[0]++; showCard.run(); });
        easy.addActionListener(e -> { applySpacedRepetition(queue.get(idx[0]), 5); idx[0]++; showCard.run(); });

        showCard.run();
        dialog.getContentPane().add(root);
        dialog.setVisible(true);
    }

    private void applySpacedRepetition(FlashcardCard c, int quality) {
        int days = 1;
        if (quality <= 2) days = 1;
        else if (quality == 4) days = 3;
        else if (quality >= 5) days = 7;
        c.createdAt = nowIso();
        c.nextReviewDate = LocalDate.now().plusDays(days).toString();
    }

    // Tasks, AI, Focus, Settings simplified
    private JPanel buildTasksView() {
        JPanel root = new JPanel(new BorderLayout(8,8));
        root.setBorder(new EmptyBorder(10,10,10,10));
        DefaultListModel<TaskModel> model = new DefaultListModel<>();
        for (TaskModel t : storage.tasks) if (t.userId.equals(currentUser.id)) model.addElement(t);
        JList<TaskModel> list = new JList<>(model);
        JScrollPane sp = new JScrollPane(list);
        sp.setPreferredSize(new Dimension(420,0));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JTextField titleField = new JTextField(18);
        JTextField dateField = new JTextField(10);
        JCheckBox rem = new JCheckBox("Reminder");
        JButton add = new JButton("Add Task");
        top.add(new JLabel("Title:")); top.add(titleField); top.add(new JLabel("Deadline (YYYY-MM-DD):")); top.add(dateField); top.add(rem); top.add(add);
        add.addActionListener(e -> {
            String title = titleField.getText().trim();
            String dl = dateField.getText().trim();
            if (title.isEmpty() || dl.isEmpty()) { JOptionPane.showMessageDialog(mainFrame, "Fill title and deadline."); return; }
            try { LocalDate.parse(dl); } catch (Exception ex) { JOptionPane.showMessageDialog(mainFrame, "Invalid date format."); return; }
            TaskModel t = new TaskModel(UUID.randomUUID().toString(), currentUser.id, title, dl, rem.isSelected());
            storage.tasks.add(t);
            storage.saveTasks();
            model.addElement(t);
            titleField.setText(""); dateField.setText(""); rem.setSelected(false);
        });
        JButton del = new JButton("Delete");
        del.addActionListener(e -> {
            TaskModel t = list.getSelectedValue();
            if (t == null) return;
            storage.tasks.removeIf(x -> x.id.equals(t.id));
            model.removeElement(t);
            storage.saveTasks();
        });
        root.add(top, BorderLayout.NORTH);
        root.add(sp, BorderLayout.CENTER);
        root.add(del, BorderLayout.SOUTH);
        return root;
    }

    private JPanel buildAIView() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(new EmptyBorder(20,20,20,20));
        JPanel center = new JPanel(new GridBagLayout());
        JButton talkBtn = new JButton("Talk to AI (Web)");
        talkBtn.setPreferredSize(new Dimension(240, 60));
        talkBtn.setFont(new Font("SansSerif", Font.BOLD, 16));
        talkBtn.addActionListener(e -> openWebAI(""));
        center.add(talkBtn);
        root.add(center, BorderLayout.CENTER);
        return root;
    }

    private void openWebAI(String prompt) {
        try {
            String base = System.getenv("AI_WEB_URL");
            if (base == null || base.isBlank()) base = "https://chat.openai.com/";
            String enc = URLEncoder.encode(prompt == null ? "" : prompt, StandardCharsets.UTF_8.toString());
            String url = base.contains("{prompt}") ? base.replace("{prompt}", enc) : (base + (base.contains("?") ? "&prompt=" : "?prompt=") + enc);
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            } else {
                showInfoLater("Error","Cannot open browser on this system.");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            showInfoLater("Error","Failed to open web AI: " + ex.getMessage());
        }
    }

    // Focus view: choose subject, choose time (10/15/30s), start session. History shows totals and messages.
    private JPanel buildFocusView() {
        JPanel root = new JPanel(new BorderLayout(8,8));
        root.setBorder(new EmptyBorder(10,10,10,10));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JComboBox<Subject> subjCombo = new JComboBox<>(subjectComboModel);
        JComboBox<String> timeCombo = new JComboBox<>(new String[] {"10", "15", "30"});
        JButton startBtn = new JButton("Start Session");
        JButton showHistory = new JButton("Show History");

        controls.add(new JLabel("Subject:")); controls.add(subjCombo);
        controls.add(new JLabel("Duration (s):")); controls.add(timeCombo);
        controls.add(startBtn); controls.add(showHistory);

        JTextArea out = new JTextArea(); out.setEditable(false);
        JScrollPane outSp = new JScrollPane(out);

        // Right: clock
        JPanel right = new JPanel(new BorderLayout());
        right.setBorder(new EmptyBorder(8,8,8,8));
        right.add(new ClockPanel(), BorderLayout.CENTER);

        startBtn.addActionListener(e -> {
            Subject s = (Subject) subjCombo.getSelectedItem();
            if (s == null) { JOptionPane.showMessageDialog(mainFrame, "Select a subject to study."); return; }
            int seconds;
            try { seconds = Integer.parseInt((String)timeCombo.getSelectedItem()); } catch (Exception ex) { seconds = 30; }
            // Start session via focusManager; UI callback appends messages to out area.
            focusManager.startSessionForUser(currentUser != null ? currentUser.id : "", s.name, seconds, (msg) -> SwingUtilities.invokeLater(() -> out.append(msg + "\n")));
            out.append("Started: " + s.name + " " + seconds + "s\n");
        });

        showHistory.addActionListener(e -> {
            String dump = focusManager.dumpForUser(currentUser != null ? currentUser.id : "");
            out.append(dump + "\n");
        });

        root.add(controls, BorderLayout.NORTH);
        root.add(outSp, BorderLayout.CENTER);
        root.add(right, BorderLayout.EAST);
        return root;
    }

    private JPanel buildSettingsView() {
        JPanel root = new JPanel(new BorderLayout(8,8));
        root.setBorder(new EmptyBorder(10,10,10,10));
        JPanel inner = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6,6,6,6);
        c.gridx = 0; c.gridy = 0; inner.add(new JLabel("Local AI URL:"), c);
        JTextField localAiField = new JTextField(36);
        Settings s = Settings.load();
        localAiField.setText(s.localAiUrl == null ? "" : s.localAiUrl);
        c.gridx = 1; inner.add(localAiField, c);

        JButton saveAll = new JButton("Save All");
        JButton reload = new JButton("Reload Data");
        JButton saveLocalAI = new JButton("Save Local AI URL");
        JButton testLocalAI = new JButton("Test Local AI");

        saveAll.addActionListener(e -> { storage.saveAll(); JOptionPane.showMessageDialog(mainFrame, "Saved"); });
        reload.addActionListener(e -> { storage.loadAll(); refreshSubjectsModel(); notifyDecksUpdated(); JOptionPane.showMessageDialog(mainFrame, "Reloaded"); });
        saveLocalAI.addActionListener(e -> {
            String url = localAiField.getText().trim();
            Settings.save(new Settings(url));
            JOptionPane.showMessageDialog(mainFrame, "Saved Local AI URL");
        });
        testLocalAI.addActionListener(e -> {
            String url = localAiField.getText().trim();
            Settings.save(new Settings(url));
            List<FlashcardGenerator.Flashcard> res = LocalAIClient.tryGenerate("Water is a chemical compound with formula H2O. It is essential for life.", 1);
            if (res == null) JOptionPane.showMessageDialog(mainFrame, "Test failed or no response.");
            else if (res.isEmpty()) JOptionPane.showMessageDialog(mainFrame, "No flashcards returned.");
            else JOptionPane.showMessageDialog(mainFrame, "Local AI responded with 1+ flashcards.");
        });

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btns.add(saveAll); btns.add(reload); btns.add(saveLocalAI); btns.add(testLocalAI);

        root.add(inner, BorderLayout.NORTH);
        root.add(btns, BorderLayout.SOUTH);
        return root;
    }

    // Models & Storage
    static class User { public String id; public String username; public String passwordHash; public User(){} public User(String id,String u,String h){this.id=id;this.username=u;this.passwordHash=h;} }
    static class Subject { public String id; public String userId; public String name; public double studyHours; public Subject(){} public Subject(String id,String uid,String name,double hs){this.id=id;this.userId=uid;this.name=name;this.studyHours=hs;} public String toString(){return name;} }
    static class Note { public String id; public String userId; public String subjectId; public String title; public String htmlContent; public String updatedAt; public Note(){} public Note(String id,String uid,String sid,String title,String html,String updated){this.id=id;this.userId=uid;this.subjectId=sid;this.title=title;this.htmlContent=html;this.updatedAt=updated;} public String toString(){return title + " ("+updatedAt+")";} }
    static class FlashcardDeck { public String id; public String userId; public String name; public FlashcardDeck(){} public FlashcardDeck(String id,String uid,String name){this.id=id;this.userId=uid;this.name=name;} public String toString(){return name;} }
    static class FlashcardCard { public String id; public String deckId; public String front; public String back; public String createdAt; public String nextReviewDate; public List<String> choices; public FlashcardCard(){} public FlashcardCard(String id,String deckId,String front,String back,String createdAt){this.id=id;this.deckId=deckId;this.front=front;this.back=back;this.createdAt=createdAt; this.choices=new ArrayList<>();} public FlashcardCard(String id,String deckId,String front,String back,String createdAt,List<String> choices){this.id=id;this.deckId=deckId;this.front=front;this.back=back;this.createdAt=createdAt;this.choices=choices==null?new ArrayList<>():choices;} public String toString(){return front + " -> " + back;} }
    static class TaskModel { public String id; public String userId; public String title; public String deadline; public boolean reminder; public boolean completed; public TaskModel(){} public TaskModel(String id,String uid,String title,String deadline,boolean reminder){this.id=id;this.userId=uid;this.title=title;this.deadline=deadline;this.reminder=reminder;this.completed=false;} public String toString(){ return title == null ? "" : title; } }

    static class Storage {
        List<User> users = new ArrayList<>();
        List<Subject> subjects = new ArrayList<>();
        List<Note> notes = new ArrayList<>();
        List<FlashcardDeck> decks = new ArrayList<>();
        List<FlashcardCard> cards = new ArrayList<>();
        List<TaskModel> tasks = new ArrayList<>();

        void loadAll() {
            users = JsonUtil.readList(USERS_JSON, User.class);
            subjects = JsonUtil.readList(SUBJECTS_JSON, Subject.class);
            notes = JsonUtil.readList(NOTES_JSON, Note.class);

            // Validate notes for disallowed characters immediately after reading.
            List<String> problems = new ArrayList<>();
            for (Note n : notes) {
                String plain = HtmlUtil.stripTags(n.htmlContent);
                for (int i = 0; i < plain.length(); i++) {
                    char ch = plain.charAt(i);
                    if (!(ch == '\t' || ch == '\n' || ch == '\r' || (ch >= 32 && ch <= 126))) {
                        problems.add("Note '" + (n.title == null ? n.id : n.title) + "' (" + n.id + "): illegal char U+" + String.format("%04X", (int)ch) + " at pos " + i);
                        break;
                    }
                }
                // also check title
                if (n.title != null && !isTitleOk(n.title)) {
                    problems.add("Note title '" + n.title + "' (" + n.id + "): contains illegal characters.");
                }
            }
            if (!problems.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                sb.append("Found notes with unsupported characters when loading notes:\n");
                for (String p : problems) {
                    sb.append(" - ").append(p).append("\n");
                }
                StudyTrackerAI.showInfoLater("Notes Load Error", sb.toString());
            }

            decks = JsonUtil.readList(FLASHCARDS_JSON, FlashcardDeck.class, "decks");
            cards = JsonUtil.readList(FLASHCARDS_JSON, FlashcardCard.class, "cards");
            tasks = JsonUtil.readList(TASKS_JSON, TaskModel.class);
        }

        private boolean isTitleOk(String title) {
            if (title == null) return true;
            for (int i = 0; i < title.length(); i++) {
                char ch = title.charAt(i);
                if (!(ch == '\t' || ch == '\n' || ch == '\r' || ch == ' ' || (ch >= 32 && ch <= 126))) return false;
            }
            return true;
        }

        void saveAll() { saveUsers(); saveSubjects(); saveNotes(); saveFlashcards(); saveTasks(); }
        void saveUsers() { JsonUtil.writeList(USERS_JSON, users); }
        void saveSubjects() { JsonUtil.writeList(SUBJECTS_JSON, subjects); }
        void saveNotes() { JsonUtil.writeList(NOTES_JSON, notes); }
        void saveFlashcards() {
            Map<String,Object> obj = new LinkedHashMap<>();
            obj.put("decks", decks);
            obj.put("cards", cards);
            JsonUtil.writeObject(FLASHCARDS_JSON, obj);
        }
        void saveTasks() { JsonUtil.writeList(TASKS_JSON, tasks); }
    }

    // Minimal Json util (manual parsing)
    static class JsonUtil {
        public static <T> List<T> readList(Path path, Class<T> clazz) {
            if (Files.notExists(path)) return new ArrayList<>();
            try {
                String s = Files.readString(path, StandardCharsets.UTF_8).trim();
                if (s.isEmpty()) return new ArrayList<>();
                if (s.startsWith("[")) {
                    int a = 0;
                    int b = findMatchingBracket(s, a);
                    if (b > a) {
                        String inner = s.substring(a + 1, b).trim();
                        if (inner.isEmpty()) return new ArrayList<>();
                        return simpleParseArray(inner, clazz);
                    }
                }
            } catch (Exception e) { e.printStackTrace(); }
            return new ArrayList<>();
        }

        public static <T> List<T> readList(Path path, Class<T> clazz, String key) {
            List<T> out = new ArrayList<>();
            if (Files.notExists(path)) return out;
            try {
                String s = Files.readString(path, StandardCharsets.UTF_8);
                if (s == null || s.isEmpty()) return out;
                String needle = "\"" + key + "\"";
                int idx = s.indexOf(needle);
                while (idx >= 0) {
                    int a = s.indexOf('[', idx);
                    if (a >= 0) {
                        int b = findMatchingBracket(s, a);
                        if (b > a) {
                            String inner = s.substring(a + 1, b).trim();
                            if (!inner.isEmpty()) return simpleParseArray(inner, clazz);
                            else return out;
                        }
                    }
                    idx = s.indexOf(needle, idx + needle.length());
                }
                int a = s.indexOf('[');
                if (a >= 0) {
                    int b = findMatchingBracket(s, a);
                    if (b > a) {
                        String inner = s.substring(a + 1, b).trim();
                        if (!inner.isEmpty()) return simpleParseArray(inner, clazz);
                    }
                }
            } catch (Exception e) { e.printStackTrace(); }
            return out;
        }

        private static <T> List<T> simpleParseArray(String content, Class<T> clazz) {
            List<T> out = new ArrayList<>();
            int idx = 0;
            while (idx < content.length()) {
                int start = content.indexOf('{', idx);
                if (start < 0) break;
                int end = findMatchingBrace(content, start);
                if (end < 0) break;
                String obj = content.substring(start, end + 1);
                T inst = mapTo(clazz, obj);
                if (inst != null) out.add(inst);
                idx = end + 1;
            }
            return out;
        }

        private static int findMatchingBracket(String s, int pos) {
            int depth = 0;
            for (int i = pos; i < s.length(); i++) {
                char ch = s.charAt(i);
                if (ch == '[') depth++;
                else if (ch == ']') {
                    depth--;
                    if (depth == 0) return i;
                } else if (ch == '"') {
                    i = skipString(s, i);
                } else if (ch == '{') {
                    i = findMatchingBrace(s, i);
                    if (i < 0) return -1;
                }
            }
            return -1;
        }

        // made public for LocalAIClient parser usage
        public static int findMatchingBrace(String s, int pos) {
            int depth = 0;
            for (int i = pos; i < s.length(); i++) {
                char ch = s.charAt(i);
                if (ch == '{') depth++;
                else if (ch == '}') {
                    depth--;
                    if (depth == 0) return i;
                } else if (ch == '"') {
                    i = skipString(s, i);
                }
            }
            return -1;
        }

        public static int skipString(String s, int startQuote) {
            int i = startQuote + 1;
            while (i < s.length()) {
                char ch = s.charAt(i);
                if (ch == '\\') i += 2;
                else if (ch == '"') return i;
                else i++;
            }
            return s.length() - 1;
        }

        private static <T> T mapTo(Class<T> clazz, String json) {
            try {
                T inst = clazz.getDeclaredConstructor().newInstance();
                String body = json.trim();
                if (body.startsWith("{")) body = body.substring(1);
                if (body.endsWith("}")) body = body.substring(0, body.length()-1);
                int idx = 0;
                while (idx < body.length()) {
                    while (idx < body.length() && (Character.isWhitespace(body.charAt(idx)) || body.charAt(idx) == ',')) idx++;
                    if (idx >= body.length()) break;
                    if (body.charAt(idx) != '"') break;
                    int keyEnd = skipString(body, idx);
                    String key = unescapeJsonString(body.substring(idx + 1, keyEnd));
                    idx = keyEnd + 1;
                    while (idx < body.length() && Character.isWhitespace(body.charAt(idx))) idx++;
                    if (idx < body.length() && body.charAt(idx) == ':') idx++;
                    while (idx < body.length() && Character.isWhitespace(body.charAt(idx))) idx++;
                    if (idx >= body.length()) break;
                    String value = null;
                    char ch = body.charAt(idx);
                    if (ch == '"') {
                        int vEnd = skipString(body, idx);
                        value = unescapeJsonString(body.substring(idx + 1, vEnd));
                        idx = vEnd + 1;
                    } else if (ch == '[') {
                        int vEnd = findMatchingBracket(body, idx);
                        String arr = (vEnd >= 0) ? body.substring(idx, vEnd + 1) : body.substring(idx);
                        value = arr;
                        idx = (vEnd >= 0) ? vEnd + 1 : body.length();
                    } else if (ch == '{') {
                        int vEnd = findMatchingBrace(body, idx);
                        String obj = (vEnd >= 0) ? body.substring(idx, vEnd + 1) : body.substring(idx);
                        value = obj;
                        idx = (vEnd >= 0) ? vEnd + 1 : body.length();
                    } else {
                        int startVal = idx;
                        while (idx < body.length() && body.charAt(idx) != ',') idx++;
                        value = body.substring(startVal, idx).trim();
                    }
                    try {
                        java.lang.reflect.Field f = clazz.getField(key);
                        if (f.getType() == String.class) {
                            f.set(inst, value == null ? null : value);
                        }
                    } catch (NoSuchFieldException ignored) {}
                }
                return inst;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        private static String unescapeJsonString(String s) {
            StringBuilder sb = new StringBuilder();
            for (int i=0;i<s.length();i++) {
                char ch = s.charAt(i);
                if (ch == '\\' && i+1 < s.length()) {
                    char n = s.charAt(i+1);
                    if (n == '"' || n == '\\' || n == '/') { sb.append(n); i++; }
                    else if (n == 'n') { sb.append('\n'); i++; }
                    else if (n == 'r') { sb.append('\r'); i++; }
                    else if (n == 't') { sb.append('\t'); i++; }
                    else { sb.append(n); i++; }
                } else sb.append(ch);
            }
            return sb.toString();
        }

        public static void writeList(Path path, List<?> list) {
            try {
                StringBuilder sb = new StringBuilder();
                sb.append("[");
                boolean first=true;
                for (Object o : list) {
                    if (!first) sb.append(",");
                    first=false;
                    sb.append(simpleToJson(o));
                }
                sb.append("]");
                Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
            } catch (Exception e) { e.printStackTrace(); }
        }

        public static void writeObject(Path path, Map<String,Object> obj) {
            try {
                StringBuilder sb = new StringBuilder();
                sb.append("{");
                boolean first=true;
                for (Map.Entry<String,Object> e : obj.entrySet()) {
                    if (!first) sb.append(",");
                    first=false;
                    sb.append("\"").append(e.getKey()).append("\":");
                    Object v = e.getValue();
                    if (v instanceof List) {
                        sb.append("[");
                        boolean f2=true;
                        for (Object it : (List<?>)v) {
                            if (!f2) sb.append(",");
                            f2=false;
                            sb.append(simpleToJson(it));
                        }
                        sb.append("]");
                    } else sb.append(simpleToJson(v));
                }
                sb.append("}");
                Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
            } catch (Exception e) { e.printStackTrace(); }
        }

        private static String simpleToJson(Object o) {
            if (o == null) return "null";
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            boolean first=true;
            for (java.lang.reflect.Field f : o.getClass().getFields()) {
                try {
                    Object val = f.get(o);
                    if (!first) sb.append(",");
                    first=false;
                    sb.append("\"").append(f.getName()).append("\":");
                    if (val == null) sb.append("null");
                    else if (val instanceof List) {
                        List<?> list = (List<?>) val;
                        sb.append("[");
                        boolean f2 = true;
                        for (Object it : list) {
                            if (!f2) sb.append(",");
                            f2 = false;
                            sb.append("\"").append(escapeJson(it == null ? "" : it.toString())).append("\"");
                        }
                        sb.append("]");
                    } else {
                        sb.append("\"").append(escapeJson(val.toString())).append("\"");
                    }
                } catch (Exception ex) {}
            }
            sb.append("}");
            return sb.toString();
        }

        private static String escapeJson(String s) {
            return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t");
        }
    }

    static class HashUtil {
        public static String sha256(String input) {
            try { MessageDigest md = MessageDigest.getInstance("SHA-256"); byte[] h = md.digest(input.getBytes(StandardCharsets.UTF_8)); StringBuilder sb = new StringBuilder(); for (byte b : h) sb.append(String.format("%02x", b)); return sb.toString(); }
            catch (Exception e) { return ""; }
        }
    }

    static class HtmlUtil {
        public static String stripTags(String html) {
            if (html == null) return "";
            return html.replaceAll("<[^>]*>", "").replaceAll("&nbsp;", " ").trim();
        }

        // Remove/list bullet lines so generation ignores bullet items.
        // We treat lines that start with -, *, +, bullet chars, or numbered lists as list items and skip them.
        public static String removeBullets(String text) {
            if (text == null) return "";
            StringBuilder sb = new StringBuilder();
            String[] lines = text.split("\\r?\\n");
            for (String line : lines) {
                String t = line.trim();
                if (t.isEmpty()) {
                    sb.append("\n");
                    continue;
                }
                // matches: -, *, +, bullet chars, or numbered lists like "1. " or "2) "
                if (t.matches("^(?:[\\-\\*\\+•◦‣]|\\d+[\\.)])\\s+.*")) {
                    // skip this bullet/list line entirely
                    continue;
                }
                // also remove leading bullet characters if embedded without space (e.g. "•Item")
                t = t.replaceFirst("^[\\-\\*\\+•◦‣]\\s*", "");
                sb.append(t).append("\n");
            }
            return sb.toString().trim();
        }
    }

    // Local AI client and Settings
    static class Settings {
        public String localAiUrl;
        public Settings() {}
        public Settings(String url) { this.localAiUrl = url; }
        public static Settings load() {
            try {
                if (Files.notExists(SETTINGS_JSON)) return new Settings("");
                String s = Files.readString(SETTINGS_JSON, StandardCharsets.UTF_8);
                if (s == null || s.isBlank()) return new Settings("");
                String needle = "\"local_ai_url\"";
                int idx = s.indexOf(needle);
                if (idx >= 0) {
                    int colon = s.indexOf(':', idx);
                    if (colon >= 0) {
                        int q1 = s.indexOf('"', colon);
                        if (q1 >= 0) {
                            int q2 = JsonUtil.skipString(s, q1);
                            String val = JsonUtil.unescapeJsonString(s.substring(q1+1, q2));
                            return new Settings(val);
                        }
                    }
                }
            } catch (Exception e) { e.printStackTrace(); }
            return new Settings("");
        }
        public static void save(Settings s) {
            try {
                String out = "{\"local_ai_url\":\"" + JsonUtil.escapeJson(s.localAiUrl == null ? "" : s.localAiUrl) + "\"}";
                Files.writeString(SETTINGS_JSON, out, StandardCharsets.UTF_8);
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    static class LocalAIClient {
        // Attempts to call the local AI and parse a simple JSON array of {question,answer,choices}
        // Expects POST JSON: {"text":"...","count":N}
        // Returns empty list on failure/no-response so callers can fall back.
        public static List<FlashcardGenerator.Flashcard> tryGenerate(String text, int max) {
            try {
                Settings s = Settings.load();
                if (s.localAiUrl == null || s.localAiUrl.isBlank()) return Collections.emptyList();
                URL url = new URL(s.localAiUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(15000);
                conn.setDoOutput(true);
                String payload = "{\"text\":\"" + JsonUtil.escapeJson(text) + "\",\"count\":" + max + "}";
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.getBytes(StandardCharsets.UTF_8));
                }
                int code = conn.getResponseCode();
                if (code < 200 || code >= 300) {
                    // non-OK -> treat as failure (caller should fallback)
                    return Collections.emptyList();
                }
                String resp;
                try (InputStream is = conn.getInputStream()) {
                    resp = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
                if (resp == null || resp.isBlank()) return Collections.emptyList();
                return parseResponseArray(resp);
            } catch (Exception e) {
                // network or parse error -> fallback
                e.printStackTrace();
                return Collections.emptyList();
            }
        }

        // parse JSON array of objects with keys question, answer, choices
        private static List<FlashcardGenerator.Flashcard> parseResponseArray(String json) {
            List<FlashcardGenerator.Flashcard> out = new ArrayList<>();
            int idx = 0;
            while (idx < json.length()) {
                int start = json.indexOf('{', idx);
                if (start < 0) break;
                int end = JsonUtil.findMatchingBrace(json, start);
                if (end < 0) break;
                String obj = json.substring(start, end + 1);
                LocalAIResponse r = JsonUtil.mapTo(LocalAIResponse.class, obj);
                if (r != null && r.answer != null && r.question != null) {
                    List<String> choices = parseChoicesField(r.choices);
                    out.add(new FlashcardGenerator.Flashcard(r.question, r.answer, choices));
                }
                idx = end + 1;
            }
            return out;
        }

        private static List<String> parseChoicesField(String raw) {
            List<String> out = new ArrayList<>();
            if (raw == null) return out;
            String s = raw.trim();
            if (!s.startsWith("[") || !s.endsWith("]")) return out;
            int i = 1;
            while (i < s.length()-1) {
                while (i < s.length()-1 && Character.isWhitespace(s.charAt(i))) i++;
                if (s.charAt(i) == '"') {
                    int q = JsonUtil.skipString(s, i);
                    String val = JsonUtil.unescapeJsonString(s.substring(i+1, q));
                    out.add(val);
                    i = q + 1;
                } else {
                    int j = i;
                    while (j < s.length()-1 && s.charAt(j) != ',') j++;
                    String val = s.substring(i, j).trim();
                    if (!val.isEmpty()) out.add(val.replaceAll("^\"|\"$", ""));
                    i = j + 1;
                }
                while (i < s.length()-1 && s.charAt(i) != '"') {
                    if (s.charAt(i) == ',') { i++; break; }
                    i++;
                }
            }
            return out;
        }

        // small DTO used by JsonUtil.mapTo
        public static class LocalAIResponse { public String question; public String answer; public String choices; public LocalAIResponse(){} }
    }

    // Flashcard generator (stricter)
    static class FlashcardGenerator {
        public static class Flashcard {
            public final String question;
            public final String answer;
            public final List<String> choices;
            public Flashcard(String q, String a) { this(q,a,new ArrayList<>()); }
            public Flashcard(String q, String a, List<String> choices) { this.question=q;this.answer=a;this.choices = choices==null?new ArrayList<>():choices; }
        }

        private static final Set<String> STOP = new HashSet<>(Arrays.asList(
                "a","an","the","and","or","but","if","while","of","at","by","for","with","about","against","between","into","through"
        ));

        public static List<Flashcard> generateFlashcards(String text, int count, boolean cloze, boolean withChoices, int choicesCount) {
            if (text == null || text.isBlank()) return Collections.emptyList();
            List<String> sentences = splitSentences(text);
            if (sentences.isEmpty()) return Collections.emptyList();
            List<String> phrases = extractCandidatePhrases(text);
            if (phrases.isEmpty()) return Collections.emptyList();
            Map<String, Double> scores = scorePhrases(phrases);
            List<String> top = new ArrayList<>(scores.keySet());
            top.sort((a,b)->Double.compare(scores.get(b), scores.get(a)));

            List<Flashcard> out = new ArrayList<>();
            Random rnd = new Random();
            List<String> selected = top.size() <= count*2 ? top : top.subList(0, Math.min(top.size(), count*2));
            Set<String> seenAnswers = new HashSet<>();
            for (String phrase : selected) {
                if (out.size() >= count) break;
                String sentence = findBestSentenceForPhrase(sentences, phrase);
                if (sentence == null) continue;
                QA qa = extractQAFromSentence(sentence, phrase);
                if (qa == null) qa = new QA("What is " + phrase + "?", summarize(sentence, phrase));
                if (qa == null || qa.answer == null) continue;
                String answer = qa.answer.trim();
                if (answer.length() < 8) continue;
                if (answer.split("\\s+").length < 2) continue;
                if (answer.replaceAll("[^A-Za-z]", "").length() < 3) continue;
                if (answer.equalsIgnoreCase(phrase)) continue;
                String key = answer.toLowerCase();
                if (seenAnswers.contains(key)) continue;
                seenAnswers.add(key);

                List<String> choices = new ArrayList<>();
                if (withChoices) {
                    choices.add(answer);
                    for (String other : selected) {
                        if (choices.size() >= choicesCount) break;
                        if (other.equalsIgnoreCase(phrase)) continue;
                        String shortDef = extractShortDef(sentences, other);
                        if (shortDef == null || shortDef.isBlank()) shortDef = other;
                        if (!choices.contains(shortDef)) choices.add(shortDef);
                    }
                    while (choices.size() < choicesCount) choices.add("Option " + (choices.size()+1));
                    Collections.shuffle(choices, rnd);
                }
                out.add(new Flashcard(qa.question, answer, choices));
            }
            return out;
        }

        private static class QA { String question, answer; QA(String q,String a){this.question=q;this.answer=a;} }

        private static QA extractQAFromSentence(String s, String phrase) {
            String lower = s.toLowerCase();
            int pos = lower.indexOf(phrase.toLowerCase());
            if (pos >= 0) {
                int idx = lower.indexOf(" is ", pos);
                if (idx >= 0 && idx - pos < 120) {
                    String tail = s.substring(idx + 4).trim();
                    tail = tail.replaceAll("[\\.;]$", "").trim();
                    if (!tail.isEmpty()) return new QA("What is " + phrase + "?", tail);
                }
                String[] keys = {"includes","consists of","such as","contains","used for","used to","refers to","means","called","known as"};
                for (String k : keys) {
                    int kidx = lower.indexOf(k, pos);
                    if (kidx >= 0) {
                        String tail = s.substring(kidx + k.length()).trim();
                        if (tail.isEmpty()) continue;
                        tail = tail.replaceAll("[\\.;]$", "").trim();
                        if (k.contains("used")) return new QA("What is " + phrase + " used for?", tail);
                        if (k.contains("called") || k.contains("known")) return new QA("What is " + phrase + "?", tail);
                        return new QA("What does " + phrase + " include?", tail);
                    }
                }
            }
            return null;
        }

        private static String extractShortDef(List<String> sentences, String phrase) {
            for (String s : sentences) {
                if (s.toLowerCase().contains(phrase.toLowerCase())) {
                    QA q = extractQAFromSentence(s, phrase);
                    if (q != null) return q.answer.length()>80?q.answer.substring(0,80)+"...":q.answer;
                }
            }
            return null;
        }

        private static String summarize(String sentence, String phrase) {
            String s = sentence.replaceAll("(?i)\\b" + java.util.regex.Pattern.quote(phrase) + "\\b", "").trim();
            if (s.length() > 200) return s.substring(0,200) + "...";
            return s.isEmpty() ? sentence : s;
        }

        private static List<String> splitSentences(String text) {
            List<String> out = new ArrayList<>();
            java.text.BreakIterator it = java.text.BreakIterator.getSentenceInstance(Locale.ENGLISH);
            it.setText(text);
            int start = it.first();
            for (int end = it.next(); end != java.text.BreakIterator.DONE; start = end, end = it.next()) {
                String s = text.substring(start, end).trim();
                if (!s.isEmpty()) out.add(s);
            }
            return out;
        }

        private static List<String> extractCandidatePhrases(String text) {
            List<String> phrases = new ArrayList<>();
            String[] tokens = text.replaceAll("[\\n\\r]", " ").split("\\s+");
            StringBuilder sb = new StringBuilder();
            for (String t : tokens) {
                String w = t.replaceAll("^[^A-Za-z0-9]+|[^A-Za-z0-9]+$", "");
                if (w.isEmpty()) {
                    if (sb.length() > 0) { phrases.add(sb.toString().trim()); sb.setLength(0); }
                    continue;
                }
                String lw = w.toLowerCase();
                if (STOP.contains(lw)) {
                    if (sb.length() > 0) { phrases.add(sb.toString().trim()); sb.setLength(0); }
                } else {
                    if (sb.length() > 0) sb.append(' ');
                    sb.append(w);
                }
            }
            if (sb.length() > 0) phrases.add(sb.toString().trim());
            return phrases;
        }

        private static Map<String,Double> scorePhrases(List<String> phrases) {
            Map<String,Integer> freq = new HashMap<>();
            for (String p : phrases) for (String w : p.split("\\s+")) {
                String k = w.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
                if (k.isEmpty()) continue;
                freq.put(k, freq.getOrDefault(k, 0) + 1);
            }
            Map<String,Double> score = new LinkedHashMap<>();
            for (String p : phrases) {
                double s = 0;
                for (String w : p.split("\\s+")) {
                    String k = w.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
                    if (k.isEmpty()) continue;
                    s += freq.getOrDefault(k, 1);
                }
                score.put(p, s);
            }
            return score;
        }

        private static String findBestSentenceForPhrase(List<String> sentences, String phrase) {
            String lp = phrase.toLowerCase();
            for (String s : sentences) {
                String ls = s.toLowerCase();
                if (ls.contains(lp) && (ls.contains(" is ") || ls.contains(" includes ") || ls.contains(" used for ") || ls.contains(" such as "))) return s;
            }
            for (String s : sentences) if (s.toLowerCase().contains(lp)) return s;
            return null;
        }
    }

    // Task table model
    static class TaskTableModel extends AbstractTableModel {
        private final List<TaskModel> data;
        private final String[] cols = {"Title","Deadline","Reminder","Done"};
        TaskTableModel(List<TaskModel> list){ this.data = new ArrayList<>(list); }
        public int getRowCount(){ return data.size(); }
        public int getColumnCount(){ return cols.length; }
        public String getColumnName(int c){ return cols[c]; }
        public Object getValueAt(int r, int c){ TaskModel t = data.get(r); switch(c){ case 0: return t.title; case 1: return t.deadline; case 2: return t.reminder; case 3: return t.completed; default: return null; } }
    }

    // Focus manager - schedules sessions, keeps per-user totals and history.
    static class FocusManager {
        // key: userId -> subjectName -> totalSeconds
        private final Map<String, Map<String, Integer>> totals = new ConcurrentHashMap<>();
        // key: userId -> list of history messages
        private final Map<String, List<String>> logs = new ConcurrentHashMap<>();
        private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

        // Start a session for user/subject for 'seconds'. uiCallback receives messages as they happen.
        public void startSessionForUser(String userId, String subjectName, int seconds, Consumer<String> uiCallback) {
            final String uid = (userId == null ? "" : userId);
            String started = String.format("Started session: %s %ds", subjectName, seconds);
            appendLog(uid, started);
            uiCallback.accept(started);

            scheduler.schedule(() -> {
                incrementTotal(uid, subjectName, seconds);
                int total = getTotal(uid, subjectName);
                String completed = String.format("Completed session: %s %ds (total %s)", subjectName, seconds, formatSecondsHuman(total));
                appendLog(uid, completed);
                uiCallback.accept(completed);
            }, seconds, TimeUnit.SECONDS);
        }
        private void incrementTotal(String userId, String subjectName, int seconds) {
            totals.computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                    .merge(subjectName, seconds, Integer::sum);
        }

        private int getTotal(String userId, String subjectName) {
            return totals.getOrDefault(userId, Collections.emptyMap()).getOrDefault(subjectName, 0);
        }

        private void appendLog(String userId, String msg) {
            logs.computeIfAbsent(userId, k -> Collections.synchronizedList(new ArrayList<>())).add(nowIso() + " - " + msg);
        }

        // Dump formatted history and totals for the user
        public String dumpForUser(String userId) {
            StringBuilder sb = new StringBuilder();
            sb.append("Focus history:\n");
            List<String> lg = logs.getOrDefault(userId, Collections.emptyList());
            for (String l : lg) sb.append(" - ").append(l).append("\n");
            sb.append("Totals:\n");
            Map<String, Integer> t = totals.getOrDefault(userId, Collections.emptyMap());
            for (Map.Entry<String,Integer> e : t.entrySet()) {
                sb.append(" - ").append(e.getKey()).append(": ").append(formatSecondsHuman(e.getValue())).append("\n");
            }
            if (lg.isEmpty() && t.isEmpty()) sb.append(" (no sessions yet)\n");
            return sb.toString();
        }

        private String formatSecondsHuman(int secs) {
            if (secs < 60) return secs + "s";
            int minutes = secs / 60;
            int rem = secs % 60;
            if (rem == 0) {
                return minutes + " minute" + (minutes == 1 ? "" : "s");
            } else {
                return minutes + "m " + rem + "s";
            }
        }
    }

}
