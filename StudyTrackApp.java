import java.io.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.stream.*;

// ==================== FocusSession ====================
class FocusSession {
    private String sessionId;
    private long elapsedMs;
    private boolean isPaused;
    private Instant startTime;
    private Instant pausedAt;

    public FocusSession() {
        this.elapsedMs = 0;
        this.isPaused = false;
        this.startTime = Instant.now();
    }

    public FocusSession(String sessionId) {
        this.sessionId = sessionId;
        this.elapsedMs = 0;
        this.isPaused = false;
        this.startTime = Instant.now();
    }

    public boolean pause() {
        if (!isPaused) {
            isPaused = true;
            pausedAt = Instant.now();
            return true;
        }
        return false;
    }

    public boolean resume() {
        if (isPaused) {
            isPaused = false;
            elapsedMs += Duration.between(pausedAt, Instant.now()).toMillis();
            startTime = Instant.now();
            return true;
        }
        return false;
    }

    public long getElapsed() {
        if (isPaused) return elapsedMs / 1000;
        return (elapsedMs + Duration.between(startTime, Instant.now()).toMillis()) / 1000;
    }

    public String getId() { return sessionId; }

    public void display() {
        System.out.println("Session: " + sessionId + ", Elapsed: " + getElapsed() + "s");
    }

    public String serialize() {
        return sessionId + "|" + elapsedMs + "|" + isPaused;
    }

    public static FocusSession deserialize(String line) {
        String[] parts = line.split("\\|");
        FocusSession fs = new FocusSession(parts[0]);
        fs.elapsedMs = Long.parseLong(parts[1]);
        fs.isPaused = Boolean.parseBoolean(parts[2]);
        fs.startTime = Instant.now();
        return fs;
    }
}

// ==================== FocusSessionHistory ====================
class FocusSessionHistory {
    private Stack<FocusSession> focusSessions;

    public FocusSessionHistory() {
        focusSessions = new Stack<>();
    }

    public void addSession(Scanner scanner) {
        System.out.print("Enter the topic you want to study in your focus session: ");
        String topic = scanner.nextLine();

        if (topic.isEmpty()) {
            System.out.println("Error!! Topic cannot be empty.");
            return;
        }

        String id = generateSessionId(topic);
        focusSessions.push(new FocusSession(id));
        System.out.println("Your focus session on '" + topic + "' is now active! Session ID: " +
                focusSessions.peek().getId() + ". Let's stay productive!");
    }

    public void addSession(FocusSession fs) {
        focusSessions.push(fs);
    }

    public Stack<FocusSession> getSessions() {
        return focusSessions;
    }

    public void pauseCurrentSession() {
        if (focusSessions.isEmpty()) {
            System.out.println("Error!! No active session to pause.");
            return;
        }

        if (focusSessions.peek().pause()) {
            System.out.println("You've paused your session " + focusSessions.peek().getId() +
                    ". Take a breath and resume when ready!");
        } else {
            System.out.println("Session " + focusSessions.peek().getId() + " is already paused.");
        }
    }

    public void resumeCurrentSession() {
        if (focusSessions.isEmpty()) {
            System.out.println("Error!! No session to resume.");
            return;
        }

        if (focusSessions.peek().resume()) {
            System.out.println("Welcome back! Your focus session " + focusSessions.peek().getId() +
                    " is now continuing.");
        } else {
            System.out.println("Session " + focusSessions.peek().getId() + " is already in progress.");
        }
    }

    public void showSummary() {
        if (focusSessions.isEmpty()) {
            System.out.println("Error! No active session.");
            return;
        }
        System.out.println("\n-------------- Session Summary --------------");
        System.out.println("Session ID: " + focusSessions.peek().getId());
        System.out.println("Duration: " + focusSessions.peek().getElapsed() + " seconds");
    }

    public void displayHistory() {
        if (focusSessions.isEmpty()) {
            System.out.println("No session history available.");
            return;
        }
        System.out.println("Stack:");
        for (FocusSession fs : focusSessions) {
            fs.display();
        }
    }

    public void clearHistory() {
        focusSessions.clear();
        System.out.println("Your session history has been successfully cleared. You can start fresh anytime");
    }

    public int sessionCount() {
        int count = focusSessions.size();
        if (count > 0) {
            System.out.println("You've completed " + count + " focus sessions so far. That's awesome progress!");
        } else {
            System.out.println("No focus sessions completed yet. Start your first one whenever you're ready!");
        }
        return count;
    }

    public int getTotalStudyTime() {
        int total = 0;
        for (FocusSession fs : focusSessions) {
            total += fs.getElapsed();
        }

        if (total > 0) {
            System.out.println("Great job! You've accumulated a total of " + total +
                    " seconds of focused study time so far. Keep it up!");
        } else {
            System.out.println("Looks like you haven't completed any focus sessions yet. " +
                    "Start one to begin tracking your progress!");
        }
        return total;
    }

    private String generateSessionId(String topic) {
        StringBuilder initials = new StringBuilder();
        boolean foundFirst = false;

        for (int i = 0; i < topic.length(); i++) {
            if (Character.isLetter(topic.charAt(i))) {
                initials.append(Character.toUpperCase(topic.charAt(i)));
                foundFirst = true;
                break;
            }
        }

        if (foundFirst) {
            for (int i = 0; i < topic.length(); i++) {
                if (topic.charAt(i) == ' ' && i + 1 < topic.length() &&
                        Character.isLetter(topic.charAt(i + 1))) {
                    initials.append(Character.toUpperCase(topic.charAt(i + 1)));
                    break;
                }
            }
        }

        if (initials.length() == 1) {
            initials.append(initials.charAt(0));
        }
        if (initials.length() == 0) {
            initials.append("FS");
        }

        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random = new Random();
        for (int i = 0; i < 3; i++) {
            initials.append(chars.charAt(random.nextInt(chars.length())));
        }

        return initials.toString();
    }
}

// ==================== Flashcard ====================
class Flashcard {
    private String id;
    private String question;
    private String answer;
    private String sourceChunkId;
    private String topic;

    public Flashcard() {}

    public Flashcard(String id, String question, String answer, String sourceChunkId, String topic) {
        this.id = id;
        this.question = question;
        this.answer = answer;
        this.sourceChunkId = sourceChunkId;
        this.topic = topic;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getQuestion() { return question; }
    public void setQuestion(String q) { this.question = q; }
    public String getAnswer() { return answer; }
    public void setAnswer(String a) { this.answer = a; }
    public String getSourceChunkId() { return sourceChunkId; }
    public void setSourceChunkId(String s) { this.sourceChunkId = s; }
    public String getTopic() { return topic; }
    public void setTopic(String t) { this.topic = t; }

    public void display() {
        System.out.println("--------Flashcard: " + id + "--------");
        System.out.println("Topic: " + topic);
        System.out.println("Q: " + question);
        System.out.println("A: " + answer + "\n");
    }

    public String serialize() {
        return id + "|" + topic + "|" + question + "|" + answer + "|" + sourceChunkId;
    }

    public static Flashcard deserialize(String line) {
        String[] parts = line.split("\\|", 5);
        return new Flashcard(parts[0], parts[2], parts[3], parts[4], parts[1]);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Flashcard)) return false;
        Flashcard other = (Flashcard) obj;
        return question.equals(other.question) && answer.equals(other.answer) &&
                sourceChunkId.equals(other.sourceChunkId) && topic.equals(other.topic);
    }
}

// ==================== FlashcardManager ====================
class FlashcardManager {
    private LinkedList<Flashcard> flashcards;

    public FlashcardManager() {
        flashcards = new LinkedList<>();
    }

    public LinkedList<Flashcard> getFlashcards() {
        return flashcards;
    }

    public void setFlashcard(LinkedList<Flashcard> cards) {
        flashcards.clear();
        flashcards.addAll(cards);
    }

    public boolean addFlashcard(Flashcard f) {
        if (isDuplicate(f)) {
            System.out.println("[FlashcardManager] Duplicate flashcard found. Not adding.");
            return false;
        }
        flashcards.add(f);
        System.out.println("[FlashcardManager] Flashcard added successfully: " + f.getId());
        return true;
    }

    public boolean addFlashcardInteractive(Scanner scanner) {
        System.out.print("Enter Topic: ");
        String topic = scanner.nextLine();
        if (topic.isEmpty()) {
            System.out.println("[Input] topic empty. Aborting.");
            return false;
        }

        System.out.print("Enter Question: ");
        String question = scanner.nextLine();
        if (question.isEmpty()) {
            System.out.println("[Input] question empty. Aborting.");
            return false;
        }

        System.out.print("Enter Answer: ");
        String answer = scanner.nextLine();
        if (answer.isEmpty()) {
            System.out.println("[Input] answer empty. Aborting.");
            return false;
        }

        System.out.print("Enter Source Chunk ID (or press Enter for 'unknown'): ");
        String sourceChunkId = scanner.nextLine();
        if (sourceChunkId.isEmpty()) sourceChunkId = "unknown";

        String id = generateId();
        Flashcard newCard = new Flashcard(id, question, answer, sourceChunkId, topic);
        return addFlashcard(newCard);
    }

    public void displayAllFlashcards() {
        if (flashcards.isEmpty()) {
            System.out.println("[FlashcardManager] No flashcards available.");
            return;
        }
        for (Flashcard f : flashcards) {
            f.display();
        }
    }

    public int getFlashcardCount() {
        return flashcards.size();
    }

    public void deleteFlashcard(String id) {
        Flashcard toRemove = null;
        for (Flashcard f : flashcards) {
            if (f.getId().equals(id)) {
                toRemove = f;
                break;
            }
        }
        if (toRemove != null) {
            flashcards.remove(toRemove);
            System.out.println("Flashcard deleted");
        } else {
            System.out.println("Invalid ID!!");
        }
    }

    private boolean isDuplicate(Flashcard f) {
        for (Flashcard card : flashcards) {
            if (card.equals(f)) return true;
        }
        return false;
    }

    private String generateId() {
        return "F" + System.currentTimeMillis() + (new Random().nextInt(1000));
    }
}

// ==================== Quiz ====================
class Quiz {
    private Queue<Flashcard> quizQuestions;
    private int count;
    private int score;

    public Quiz() {
        quizQuestions = new LinkedList<>();
        score = 0;
        count = 0;
    }

    public void createQuiz(List<Flashcard> flashcards) {
        quizQuestions.clear();
        count = 0;
        score = 0;

        quizQuestions.addAll(flashcards);
        count = flashcards.size();

        if (count == 0) {
            System.out.println("Quiz not created!! No flashcards in the list");
        } else {
            System.out.println("[Quiz] Quiz created with " + count + " questions.");
        }
    }

    public void conductQuiz(Scanner scanner) {
        if (quizQuestions.isEmpty()) {
            System.out.println("[Quiz] No questions to conduct.");
            return;
        }

        System.out.println("\n========== STARTING QUIZ ==========");
        int questionNum = 1;

        while (!quizQuestions.isEmpty()) {
            Flashcard question = quizQuestions.poll();
            System.out.println("\nQuestion " + questionNum + "/" + count + ":");
            System.out.println(question.getQuestion());
            System.out.print("Your answer: ");
            String ans = scanner.nextLine();

            if (submitAnswer(question, ans)) {
                score++;
                System.out.println("✓ Correct!");
            } else {
                System.out.println("✗ Incorrect. The correct answer is: " + question.getAnswer());
            }
            questionNum++;
        }

        System.out.println("\n========== QUIZ COMPLETED ==========");
        System.out.println("Your score: " + score + " / " + count + " (" +
                (count > 0 ? (score * 100 / count) : 0) + "%)");
    }

    public boolean submitAnswer(Flashcard f, String ans) {
        return ans.trim().equalsIgnoreCase(f.getAnswer().trim());
    }

    public int getScore() { return score; }
    public int getCount() { return count; }
}

// ==================== QuizManager ====================
class QuizManager {
    private Map<String, List<Flashcard>> topicMap;

    public QuizManager() {
        topicMap = new HashMap<>();
    }

    public void insertFlashcard(Flashcard f) {
        topicMap.computeIfAbsent(f.getTopic(), k -> new ArrayList<>()).add(f);
    }

    public List<Flashcard> getFlashcardsByTopic(String topic) {
        return topicMap.getOrDefault(topic, new ArrayList<>());
    }

    public void displayTopics() {
        System.out.println("\n========== Available Topics ==========");
        if (topicMap.isEmpty()) {
            System.out.println("No topics available. Add flashcards first!");
            return;
        }
        for (String topic : topicMap.keySet()) {
            System.out.println("- " + topic);
        }
    }

    public int countFlashcards(String topic) {
        return topicMap.getOrDefault(topic, new ArrayList<>()).size();
    }
}

// ==================== Task ====================
enum TaskStatus {
    PENDING, IN_PROGRESS, COMPLETED
}

class Task implements Comparable<Task> {
    private int id;
    private String title;
    private String description;
    private LocalDate deadline;
    private TaskStatus status;

    public Task() {
        this.id = -1;
        this.title = "";
        this.description = "";
        this.deadline = null;
        this.status = TaskStatus.PENDING;
    }

    public Task(String title, String description, Scanner scanner) {
        this.id = -1;
        this.title = title;
        this.description = description;
        this.status = TaskStatus.PENDING;
        setDeadlineFromInput(scanner);
    }

    public void setID(int i) { this.id = i; }
    public int getID() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public LocalDate getDeadline() { return deadline; }
    public TaskStatus getStatus() { return status; }

    public void markCompleted() { status = TaskStatus.COMPLETED; }
    public void markInProgress() { status = TaskStatus.IN_PROGRESS; }

    public boolean isLate() {
        if (deadline == null) return false;
        return LocalDate.now().isAfter(deadline);
    }

    public void setDeadlineFromInput(Scanner scanner) {
        while (true) {
            System.out.print("Enter deadline (YYYY-MM-DD) or press Enter to skip: ");
            String input = scanner.nextLine();

            if (input.isEmpty()) {
                deadline = null;
                break;
            }

            try {
                LocalDate date = LocalDate.parse(input);
                if (date.isBefore(LocalDate.now())) {
                    System.out.println("Deadline cannot be in the past! Try again.");
                    continue;
                }
                deadline = date;
                break;
            } catch (DateTimeParseException e) {
                System.out.println("Invalid format! Try again.");
            }
        }
    }

    public void display() {
        System.out.println("\nTask " + id + ":");
        System.out.println("Title: " + title);
        System.out.println("Description: " + description);
        System.out.println("Deadline: " + (deadline != null ? deadline : "N/A"));
        System.out.print("Status: ");
        switch (status) {
            case PENDING: System.out.print("Pending"); break;
            case IN_PROGRESS: System.out.print("In Progress"); break;
            case COMPLETED: System.out.print("Completed"); break;
        }
        if (isLate() && status != TaskStatus.COMPLETED) {
            System.out.print(" (LATE!)");
        }
        System.out.println("\n");
    }

    public String serialize() {
        return id + "|" + title + "|" + description + "|" +
                (deadline != null ? deadline.toString() : "null") + "|" + status.ordinal();
    }

    public static Task deserialize(String line) {
        String[] parts = line.split("\\|");
        Task t = new Task();
        t.id = Integer.parseInt(parts[0]);
        t.title = parts[1];
        t.description = parts[2];
        t.deadline = parts[3].equals("null") ? null : LocalDate.parse(parts[3]);
        t.status = TaskStatus.values()[Integer.parseInt(parts[4])];
        return t;
    }

    @Override
    public int compareTo(Task other) {
        if (this.deadline == null && other.deadline == null) return 0;
        if (this.deadline == null) return 1;
        if (other.deadline == null) return -1;
        return this.deadline.compareTo(other.deadline);
    }
}

// ==================== ToDoList ====================
class ToDoList {
    private PriorityQueue<Task> todolist;
    private static int count = 1;

    public ToDoList() {
        todolist = new PriorityQueue<>();
    }

    public void addTask(Scanner scanner) {
        System.out.println("\nCreating a new task...");
        System.out.print("Enter task title: ");
        String title = scanner.nextLine();
        System.out.print("Enter task description: ");
        String description = scanner.nextLine();

        Task newTask = new Task(title, description, scanner);
        newTask.setID(count++);
        todolist.add(newTask);
        System.out.println();
    }

    public void displayList() {
        System.out.println("------------------Your To Do List------------------");
        if (todolist.isEmpty()) {
            System.out.println("No tasks available.");
            return;
        }
        List<Task> temp = new ArrayList<>(todolist);
        for (Task t : temp) {
            t.display();
        }
    }

    public void taskCompleted(Scanner scanner) {
        System.out.print("Enter the id of the task completed: ");
        int id = scanner.nextInt();
        scanner.nextLine();

        Task found = null;
        for (Task t : todolist) {
            if (t.getID() == id) {
                found = t;
                break;
            }
        }

        if (found != null) {
            found.markCompleted();
            System.out.println("Task " + id + " marked as completed.");
            found.display();
            todolist.remove(found);
            System.out.println("Task " + id + " removed from list.");
        } else {
            System.out.println("Task with ID " + id + " not found!");
        }
    }

    public void deleteTask(Scanner scanner) {
        System.out.print("Enter the id of the task you wish to delete: ");
        int id = scanner.nextInt();
        scanner.nextLine();

        Task found = null;
        for (Task t : todolist) {
            if (t.getID() == id) {
                found = t;
                break;
            }
        }

        if (found != null) {
            found.display();
            todolist.remove(found);
            System.out.println("Task " + id + " deleted");
        } else {
            System.out.println("Task with ID " + id + " not found!");
        }
    }

    public void closestDeadline() {
        if (todolist.isEmpty()) {
            System.out.println("No tasks available.");
            return;
        }
        Task closest = todolist.peek();
        System.out.println("Task " + closest.getID() + " has the closest deadline");
    }

    public static int getCount() { return count; }
    public static void setCount(int c) { count = c; }

    public void addTaskFromFile(Task task) {
        todolist.add(task);
        if (task.getID() >= count) {
            count = task.getID() + 1;
        }
    }

    public List<Task> getAllTasks() {
        return new ArrayList<>(todolist);
    }
}

// ==================== LeaderboardEntry ====================
class LeaderboardEntry implements Comparable<LeaderboardEntry> {
    public String username;
    public int score;

    public LeaderboardEntry(String username, int score) {
        this.username = username;
        this.score = score;
    }

    @Override
    public int compareTo(LeaderboardEntry other) {
        return Integer.compare(other.score, this.score); // Descending order
    }

    public void display() {
        System.out.print(username + ": " + score + " points");
    }
}

// ==================== Leaderboard ====================
class Leaderboard {
    private PriorityQueue<LeaderboardEntry> heap;
    private Map<String, Integer> userScores;

    public Leaderboard() {
        heap = new PriorityQueue<>();
        userScores = new HashMap<>();
    }

    public void insert(String username, int score) {
        userScores.put(username, score);
        rebuildHeap();
    }

    private void rebuildHeap() {
        heap.clear();
        for (Map.Entry<String, Integer> entry : userScores.entrySet()) {
            heap.add(new LeaderboardEntry(entry.getKey(), entry.getValue()));
        }
    }

    public int getUserRank(String username) {
        if (!userScores.containsKey(username)) return -1;

        List<LeaderboardEntry> sorted = new ArrayList<>(heap);
        Collections.sort(sorted);

        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).username.equals(username)) {
                return i + 1;
            }
        }
        return -1;
    }

    public void displayTop(int n) {
        List<LeaderboardEntry> sorted = new ArrayList<>(heap);
        Collections.sort(sorted);

        System.out.println("\n========== TOP " + n + " LEADERBOARD ==========");
        if (sorted.isEmpty()) {
            System.out.println("No entries in leaderboard yet.");
            return;
        }

        int displayCount = Math.min(n, sorted.size());
        for (int i = 0; i < displayCount; i++) {
            System.out.print((i + 1) + ". ");
            sorted.get(i).display();
            System.out.println();
        }
        System.out.println("======================================");
    }
}

// ==================== AI Flashcard Generator ====================
class AIFlashcardGenerator {
    public static List<Flashcard> generateFlashcards(String topic, int count) {
        System.out.println("[AI] Generating " + count + " flashcards for topic: " + topic);
        System.out.println("[AI] Connecting to AI service...");

        List<Flashcard> flashcards = new ArrayList<>();

        try {
            // Simulate AI call with realistic delay
            Thread.sleep(2000);

            // Generate flashcards using AI-like logic
            String[] questions = generateQuestionsForTopic(topic, count);
            String[] answers = generateAnswersForTopic(topic, count);

            for (int i = 0; i < count; i++) {
                String id = "F" + System.currentTimeMillis() + i;
                Flashcard card = new Flashcard(id, questions[i], answers[i], "AI-Generated", topic);
                flashcards.add(card);
            }

            System.out.println("[AI] Successfully generated " + flashcards.size() + " flashcards!");

        } catch (InterruptedException e) {
            System.out.println("[AI] Generation interrupted.");
            Thread.currentThread().interrupt();
        }

        return flashcards;
    }

    private static String[] generateQuestionsForTopic(String topic, int count) {
        // This simulates AI-generated questions based on topic
        Map<String, String[]> topicQuestions = new HashMap<>();

        topicQuestions.put("java", new String[]{
                "What is the difference between JDK, JRE, and JVM?",
                "Explain the concept of inheritance in Java",
                "What are the main principles of Object-Oriented Programming?",
                "What is polymorphism and how is it implemented in Java?",
                "Explain the difference between abstract classes and interfaces"
        });

        topicQuestions.put("data structures", new String[]{
                "What is the time complexity of binary search?",
                "Explain the difference between Stack and Queue",
                "What is a Binary Search Tree and its properties?",
                "Describe how a Hash Map works internally",
                "What is the difference between Array and LinkedList?"
        });

        topicQuestions.put("algorithms", new String[]{
                "Explain the Quick Sort algorithm",
                "What is Dynamic Programming and when to use it?",
                "Describe the Breadth-First Search algorithm",
                "What is the difference between Greedy and Dynamic Programming?",
                "Explain Dijkstra's shortest path algorithm"
        });

        topicQuestions.put("database", new String[]{
                "What is normalization in databases?",
                "Explain ACID properties in database transactions",
                "What is the difference between SQL and NoSQL databases?",
                "Describe what an index is and why it's used",
                "What are foreign keys and how do they work?"
        });

        // Default questions for any topic
        String[] defaultQuestions = {
                "What are the fundamental concepts of " + topic + "?",
                "How does " + topic + " work in practice?",
                "What are the main advantages of " + topic + "?",
                "Explain a key principle of " + topic,
                "What are common use cases for " + topic + "?"
        };

        String topicLower = topic.toLowerCase();
        for (String key : topicQuestions.keySet()) {
            if (topicLower.contains(key)) {
                return Arrays.copyOf(topicQuestions.get(key), count);
            }
        }

        return Arrays.copyOf(defaultQuestions, count);
    }

    private static String[] generateAnswersForTopic(String topic, int count) {
        // This simulates AI-generated answers based on topic
        Map<String, String[]> topicAnswers = new HashMap<>();

        topicAnswers.put("java", new String[]{
                "JDK is Java Development Kit, JRE is Java Runtime Environment, JVM is Java Virtual Machine that executes bytecode",
                "Inheritance allows a class to acquire properties and methods from another class using extends keyword",
                "Encapsulation, Abstraction, Inheritance, and Polymorphism are the four main OOP principles",
                "Polymorphism allows objects to take many forms through method overriding and overloading",
                "Abstract classes can have implementation while interfaces define contracts without implementation until Java 8"
        });

        topicAnswers.put("data structures", new String[]{
                "O(log n) - it divides the search space in half each iteration",
                "Stack follows LIFO (Last In First Out), Queue follows FIFO (First In First Out)",
                "A BST is a tree where left child < parent < right child for all nodes",
                "HashMap uses hashing to convert keys into array indices for O(1) average access time",
                "Arrays have fixed size and O(1) access, LinkedLists have dynamic size but O(n) access"
        });

        topicAnswers.put("algorithms", new String[]{
                "Quick Sort picks a pivot, partitions array around it, and recursively sorts subarrays",
                "DP breaks problems into overlapping subproblems and stores results to avoid recomputation",
                "BFS explores nodes level by level using a queue, finding shortest paths in unweighted graphs",
                "Greedy makes locally optimal choices while DP considers all possibilities",
                "Dijkstra finds shortest paths from source to all vertices using a priority queue"
        });

        topicAnswers.put("database", new String[]{
                "Normalization organizes data to reduce redundancy and improve data integrity through normal forms",
                "Atomicity, Consistency, Isolation, Durability ensure reliable database transactions",
                "SQL is relational with fixed schema, NoSQL is non-relational with flexible schema",
                "An index is a data structure that improves query speed by creating pointers to data",
                "Foreign keys create relationships between tables by referencing primary keys in other tables"
        });

        // Default answers for any topic
        String[] defaultAnswers = {
                "The fundamental concepts include basic definitions, principles, and core components of " + topic,
                topic + " works by applying specific methodologies and techniques to solve problems",
                "Main advantages include efficiency, scalability, and practical applicability in various scenarios",
                "A key principle involves understanding the underlying mechanisms and best practices",
                "Common use cases include practical applications in real-world scenarios and projects"
        };

        String topicLower = topic.toLowerCase();
        for (String key : topicAnswers.keySet()) {
            if (topicLower.contains(key)) {
                return Arrays.copyOf(topicAnswers.get(key), count);
            }
        }

        return Arrays.copyOf(defaultAnswers, count);
    }
}

// ==================== User ====================
class User {
    private String userId;
    private String username;
    private String password;
    private int score;
    private Instant lastActive;
    private FocusSessionHistory focusSessions;
    private FlashcardManager flashcards;
    private QuizManager quizzes;
    private ToDoList todoList;
    private UserManager userManagerPtr;

    public User() {
        this.score = 0;
        this.lastActive = Instant.now();
        this.focusSessions = new FocusSessionHistory();
        this.flashcards = new FlashcardManager();
        this.quizzes = new QuizManager();
        this.todoList = new ToDoList();
    }

    public User(String username, String password) {
        this();
        this.username = username;
        this.password = password;
        this.userId = generateUserId();
    }

    // Getters and Setters
    public void setUserId(String u) { this.userId = u; }
    public String getUserId() { return userId; }
    public void setUsername(String u) { this.username = u; }
    public String getUsername() { return username; }
    public void setPassword(String p) { this.password = p; }
    public String getPassword() { return password; }
    public int getScore() { return score; }
    public void setScore(int s) { this.score = s; }
    public void setUserManager(UserManager mgr) { this.userManagerPtr = mgr; }

    public FlashcardManager getFlashcardManager() { return flashcards; }
    public QuizManager getQuizManager() { return quizzes; }
    public ToDoList getToDoList() { return todoList; }
    public FocusSessionHistory getFocusHistory() { return focusSessions; }

    public void addFlashcard(Flashcard f) {
        if (flashcards.addFlashcard(f)) {
            quizzes.insertFlashcard(f);
            lastActive = Instant.now();
        }
    }

    public void addFlashcardInteractive(Scanner scanner) {
        if (flashcards.addFlashcardInteractive(scanner)) {
            for (Flashcard f : flashcards.getFlashcards()) {
                quizzes.insertFlashcard(f);
            }
            lastActive = Instant.now();
        }
    }

    public void generateFlashcardsWithAI(Scanner scanner) {
        System.out.print("Enter the topic for AI-generated flashcards: ");
        String topic = scanner.nextLine();

        if (topic.isEmpty()) {
            System.out.println("Topic cannot be empty.");
            return;
        }

        System.out.print("How many flashcards do you want to generate? (1-10): ");
        int count = 5;
        try {
            count = Integer.parseInt(scanner.nextLine());
            if (count < 1 || count > 10) {
                System.out.println("Invalid count. Using default: 5");
                count = 5;
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid input. Using default: 5");
        }

        List<Flashcard> generated = AIFlashcardGenerator.generateFlashcards(topic, count);

        for (Flashcard f : generated) {
            addFlashcard(f);
        }

        System.out.println("\n✓ Successfully added " + generated.size() + " AI-generated flashcards to your collection!");
    }

    public void deleteFlashcards(String id) {
        flashcards.deleteFlashcard(id);
    }

    public void reviewFlashcards() {
        flashcards.displayAllFlashcards();
    }

    public void startFocusSession(Scanner scanner) {
        focusSessions.addSession(scanner);
        lastActive = Instant.now();
    }

    public void pauseFocusSession() {
        focusSessions.pauseCurrentSession();
        lastActive = Instant.now();
    }

    public void resumeFocusSession() {
        focusSessions.resumeCurrentSession();
        lastActive = Instant.now();
    }

    public void showFocusSummary() {
        focusSessions.showSummary();
    }

    public void showFocusHistory() {
        focusSessions.displayHistory();
    }

    public void clearFocusHistory() {
        focusSessions.clearHistory();
    }

    public void showTotalStudyTime() {
        focusSessions.getTotalStudyTime();
    }

    public void showNumberOfSessions() {
        focusSessions.sessionCount();
    }

    public void addTask(Scanner scanner) {
        todoList.addTask(scanner);
        lastActive = Instant.now();
    }

    public void viewTasks() {
        todoList.displayList();
    }

    public void completeTask(Scanner scanner) {
        todoList.taskCompleted(scanner);
        lastActive = Instant.now();
    }

    public void removeTask(Scanner scanner) {
        todoList.deleteTask(scanner);
        lastActive = Instant.now();
    }

    public void viewClosestDeadline() {
        todoList.closestDeadline();
    }

    public void createQuizFromTopic(String topic, Scanner scanner) {
        List<Flashcard> topicFlashcards = quizzes.getFlashcardsByTopic(topic);

        if (topicFlashcards.isEmpty()) {
            System.out.println("[User] No flashcards found for topic '" + topic + "'. Add some first!");
            return;
        }

        Quiz q = new Quiz();
        q.createQuiz(topicFlashcards);
        q.conductQuiz(scanner);
        score += q.getScore();
        lastActive = Instant.now();

        if (userManagerPtr != null) {
            userManagerPtr.updateLeaderboard(username, score);
        }
    }

    public String serialize() {
        return username + "|" + password + "|" + userId + "|" + score;
    }

    public static User deserialize(String line) {
        String[] parts = line.split("\\|");
        User u = new User(parts[0], parts[1]);
        u.setUserId(parts[2]);
        u.setScore(Integer.parseInt(parts[3]));
        return u;
    }

    private String generateUserId() {
        return "U" + System.currentTimeMillis() + (new Random().nextInt(10000));
    }
}

// ==================== UserManager ====================
class UserManager {
    private String usersFile;
    private String userDataFile;
    private Map<String, String> userTree; // username -> userId
    private Leaderboard leaderboard;

    public UserManager() {
        this.usersFile = "users.txt";
        this.userDataFile = "userdata_";
        this.userTree = new HashMap<>();
        this.leaderboard = new Leaderboard();
    }

    public boolean signup(String username, String password) {
        if (username.isEmpty() || password.isEmpty()) {
            System.out.println("[Error] Username and password cannot be empty.");
            return false;
        }

        if (userExists(username)) {
            System.out.println("[Error] Username already exists. Please choose a different username.");
            return false;
        }

        User newUser = new User(username, password);
        userTree.put(username, newUser.getUserId());
        leaderboard.insert(username, 0);

        try (PrintWriter writer = new PrintWriter(new FileWriter(usersFile, true))) {
            writer.println(newUser.serialize());
            System.out.println("[Success] Account created successfully! Welcome, " + username + "!");
            return true;
        } catch (IOException e) {
            System.out.println("[Error] Failed to create account: " + e.getMessage());
            return false;
        }
    }

    public User login(String username, String password) {
        if (username.isEmpty() || password.isEmpty()) {
            System.out.println("[Error] Username and password cannot be empty.");
            return null;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(usersFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;

                User u = User.deserialize(line);
                if (u.getUsername().equals(username) && u.getPassword().equals(password)) {
                    User loggedInUser = new User(u.getUsername(), u.getPassword());
                    loggedInUser.setUserId(u.getUserId());
                    loggedInUser.setScore(u.getScore());

                    if (!userTree.containsKey(username)) {
                        userTree.put(username, loggedInUser.getUserId());
                    }

                    loadUserData(loggedInUser);
                    leaderboard.insert(username, loggedInUser.getScore());
                    System.out.println("[Success] Welcome back, " + username + "!");
                    return loggedInUser;
                }
            }

            System.out.println("[Error] Invalid username or password.");
            return null;

        } catch (IOException e) {
            System.out.println("[Error] Login failed: " + e.getMessage());
            return null;
        }
    }

    public boolean saveUserData(User user) {
        String filename = userDataFile + user.getUserId() + ".txt";

        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("[FLASHCARDS]");
            for (Flashcard f : user.getFlashcardManager().getFlashcards()) {
                writer.println(f.serialize());
            }

            writer.println("[TASKS]");
            writer.println(ToDoList.getCount());
            for (Task t : user.getToDoList().getAllTasks()) {
                writer.println(t.serialize());
            }

            writer.println("[SESSIONS]");
            for (FocusSession fs : user.getFocusHistory().getSessions()) {
                writer.println(fs.serialize());
            }

            System.out.println("[Success] All user data saved to " + filename);
            return true;

        } catch (IOException e) {
            System.out.println("[Error] Failed to save user data: " + e.getMessage());
            return false;
        }
    }

    public boolean loadUserData(User user) {
        String filename = userDataFile + user.getUserId() + ".txt";

        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            String currentSection = "";
            boolean nextIdRead = false;

            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;

                if (line.equals("[FLASHCARDS]")) {
                    currentSection = "FLASHCARDS";
                    continue;
                } else if (line.equals("[TASKS]")) {
                    currentSection = "TASKS";
                    nextIdRead = false;
                    continue;
                } else if (line.equals("[SESSIONS]")) {
                    currentSection = "SESSIONS";
                    continue;
                }

                if (currentSection.equals("FLASHCARDS")) {
                    Flashcard f = Flashcard.deserialize(line);
                    user.addFlashcard(f);
                } else if (currentSection.equals("TASKS")) {
                    if (!nextIdRead) {
                        int taskCount = Integer.parseInt(line);
                        ToDoList.setCount(taskCount);
                        nextIdRead = true;
                    } else {
                        Task t = Task.deserialize(line);
                        user.getToDoList().addTaskFromFile(t);
                    }
                } else if (currentSection.equals("SESSIONS")) {
                    FocusSession fs = FocusSession.deserialize(line);
                    user.getFocusHistory().addSession(fs);
                }
            }

            System.out.println("[Success] User data loaded successfully.");
            return true;

        } catch (FileNotFoundException e) {
            System.out.println("[Info] No existing data found. Starting fresh.");
            return true;
        } catch (IOException e) {
            System.out.println("[Error] Failed to load user data: " + e.getMessage());
            return false;
        }
    }

    public boolean updateUserScore(User user) {
        leaderboard.insert(user.getUsername(), user.getScore());

        try {
            List<String> lines = new ArrayList<>();
            boolean updated = false;

            try (BufferedReader reader = new BufferedReader(new FileReader(usersFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) continue;

                    User tempUser = User.deserialize(line);
                    if (tempUser.getUserId().equals(user.getUserId())) {
                        lines.add(user.serialize());
                        updated = true;
                    } else {
                        lines.add(line);
                    }
                }
            }

            if (!updated) return false;

            try (PrintWriter writer = new PrintWriter(new FileWriter(usersFile))) {
                for (String line : lines) {
                    writer.println(line);
                }
            }
            return true;

        } catch (IOException e) {
            System.out.println("[Error] Failed to update user score: " + e.getMessage());
            return false;
        }
    }

    public void displayLeaderboard(int n) {
        leaderboard.displayTop(n);
    }

    public void updateLeaderboard(String username, int score) {
        leaderboard.insert(username, score);
        System.out.println("[Leaderboard] Your score has been updated to " + score + " points!");
    }

    public int getUserRank(String username) {
        return leaderboard.getUserRank(username);
    }

    public void loadAllUsersToTree() {
        try (BufferedReader reader = new BufferedReader(new FileReader(usersFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;
                User u = User.deserialize(line);
                userTree.put(u.getUsername(), u.getUserId());
                leaderboard.insert(u.getUsername(), u.getScore());
            }
        } catch (FileNotFoundException e) {
            // File doesn't exist yet, which is fine
        } catch (IOException e) {
            System.out.println("[Error] Failed to load users: " + e.getMessage());
        }
    }

    private boolean userExists(String username) {
        try (BufferedReader reader = new BufferedReader(new FileReader(usersFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;

                User u = User.deserialize(line);
                if (u.getUsername().equals(username)) {
                    return true;
                }
            }
            return false;

        } catch (FileNotFoundException e) {
            return false;
        } catch (IOException e) {
            System.out.println("[Error] Failed to check user existence: " + e.getMessage());
            return false;
        }
    }
}

// ==================== Main Application ====================
public class StudyTrackApp {
    private static Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        UserManager userManager = new UserManager();
        userManager.loadAllUsersToTree();
        User currentUser = null;

        System.out.println();
        System.out.println("******************************************");
        System.out.println("*                                        *");
        System.out.println("*       WELCOME TO STUDY TRACK APP       *");
        System.out.println("*     Your Personal Study Companion      *");
        System.out.println("*                                        *");
        System.out.println("******************************************");

        while (true) {
            if (currentUser == null) {
                displayMainMenu();
                int choice = getIntInput();

                switch (choice) {
                    case 1: {
                        System.out.println("\n--- LOGIN ---");
                        System.out.print("Username: ");
                        String username = scanner.nextLine();
                        System.out.print("Password: ");
                        String password = scanner.nextLine();

                        currentUser = userManager.login(username, password);
                        if (currentUser != null) {
                            currentUser.setUserManager(userManager);
                        }
                        break;
                    }
                    case 2: {
                        System.out.println("\n--- SIGN UP ---");
                        System.out.print("Choose a username: ");
                        String username = scanner.nextLine();
                        System.out.print("Choose a password: ");
                        String password = scanner.nextLine();

                        userManager.signup(username, password);
                        break;
                    }
                    case 3:
                        System.out.println("\nThank you for using Study Track App. Goodbye!");
                        scanner.close();
                        return;
                    default:
                        System.out.println("[Error] Invalid choice. Please try again.");
                }
            } else {
                displayUserMenu(currentUser.getUsername());
                int choice = getIntInput();

                switch (choice) {
                    case 1:
                        currentUser.addFlashcardInteractive(scanner);
                        break;
                    case 2:
                        System.out.println("\n--- YOUR FLASHCARDS ---");
                        currentUser.reviewFlashcards();
                        break;
                    case 3: {
                        currentUser.reviewFlashcards();
                        System.out.print("Enter flashcard ID you wish to delete: ");
                        String id = scanner.nextLine();
                        currentUser.deleteFlashcards(id);
                        break;
                    }
                    case 4: {
                        System.out.println("\n--- TAKE QUIZ ---");
                        currentUser.getQuizManager().displayTopics();
                        System.out.print("Enter topic name: ");
                        String topic = scanner.nextLine();
                        currentUser.createQuizFromTopic(topic, scanner);
                        break;
                    }
                    case 5:
                        currentUser.getQuizManager().displayTopics();
                        break;
                    case 6:
                        currentUser.generateFlashcardsWithAI(scanner);
                        break;
                    case 7:
                        currentUser.startFocusSession(scanner);
                        break;
                    case 8:
                        currentUser.pauseFocusSession();
                        break;
                    case 9:
                        currentUser.resumeFocusSession();
                        break;
                    case 10:
                        currentUser.showFocusSummary();
                        break;
                    case 11:
                        currentUser.showFocusHistory();
                        break;
                    case 12:
                        currentUser.showTotalStudyTime();
                        break;
                    case 13:
                        currentUser.showNumberOfSessions();
                        break;
                    case 14:
                        currentUser.clearFocusHistory();
                        break;
                    case 15:
                        currentUser.addTask(scanner);
                        break;
                    case 16:
                        currentUser.viewTasks();
                        break;
                    case 17:
                        currentUser.completeTask(scanner);
                        break;
                    case 18:
                        currentUser.viewClosestDeadline();
                        break;
                    case 19:
                        currentUser.removeTask(scanner);
                        break;
                    case 20:
                        userManager.displayLeaderboard(10);
                        break;
                    case 21: {
                        int rank = userManager.getUserRank(currentUser.getUsername());
                        if (rank > 0) {
                            System.out.println("Your current rank: #" + rank + " with " +
                                    currentUser.getScore() + " points");
                        } else {
                            System.out.println("Rank not found.");
                        }
                        break;
                    }
                    case 22:
                        System.out.println("\n--- SAVING DATA ---");
                        userManager.saveUserData(currentUser);
                        userManager.updateUserScore(currentUser);
                        System.out.println("All data saved successfully. See you soon, " +
                                currentUser.getUsername() + "!");
                        currentUser = null;
                        break;
                    default:
                        System.out.println("[Error] Invalid choice. Please try again.");
                }
            }
        }
    }

    private static void displayMainMenu() {
        System.out.println("\n========================================");
        System.out.println("       STUDY TRACK APP - MAIN MENU      ");
        System.out.println("========================================");
        System.out.println("1. Login");
        System.out.println("2. Sign Up");
        System.out.println("3. Exit");
        System.out.println("========================================");
        System.out.print("Enter your choice: ");
    }

    private static void displayUserMenu(String username) {
        System.out.println("\n========================================");
        System.out.println("     Welcome, " + username + "!     ");
        System.out.println("========================================");
        System.out.println("FLASHCARDS:");
        System.out.println("1.  Add Flashcard Manually");
        System.out.println("2.  View All Flashcards");
        System.out.println("3.  Delete Flashcard");
        System.out.println("4.  Take Quiz by Topic");
        System.out.println("5.  View Available Topics");
        System.out.println("6.  Generate Flashcards with AI ✨");
        System.out.println("\nFOCUS SESSIONS:");
        System.out.println("7.  Start Focus Session");
        System.out.println("8.  Pause Focus Session");
        System.out.println("9.  Resume Focus Session");
        System.out.println("10. Show Session Summary");
        System.out.println("11. Show Session History");
        System.out.println("12. Show Total Study Time");
        System.out.println("13. Show Number of Sessions");
        System.out.println("14. Clear Session History");
        System.out.println("\nTO-DO LIST:");
        System.out.println("15. Add Task");
        System.out.println("16. View Tasks");
        System.out.println("17. Mark Task Complete");
        System.out.println("18. View Closest Deadline");
        System.out.println("19. Delete Task");
        System.out.println("\nLEADERBOARD:");
        System.out.println("20. View Leaderboard");
        System.out.println("21. View My Rank");
        System.out.println("\n22. Save & Logout");
        System.out.println("========================================");
        System.out.print("Enter your choice: ");
    }

    private static int getIntInput() {
        while (true) {
            try {
                int choice = Integer.parseInt(scanner.nextLine());
                return choice;
            } catch (NumberFormatException e) {
                System.out.print("[Error] Invalid input. Please enter a number: ");
            }
        }
    }
}
