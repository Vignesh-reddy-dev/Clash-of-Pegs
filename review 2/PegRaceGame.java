import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.swing.*;


public class PegRaceGame extends JFrame {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            PegRaceGame frame = new PegRaceGame();
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    public PegRaceGame() {
        super("Peg Race — CPU Dominance Edition");
        GamePanel gamePanel = new GamePanel();
        add(gamePanel);
    }
}

// ==================== CONSTANTS & CONFIG ====================

class GameConstants {
    static final int CELL = 50;
    static final int MARGIN = 20;
    static final int ROWS = 9;
    static final int COLS = 9;

    static final char EMPTY = '.';
    static final char HUMAN = 'W';
    static final char CPU = 'B';

    // Special Pegs
    static final char GOLD = 'g';
    static final char BLUE = 'b';
    static final char RED = 'r';
    static final char NEUTRAL = 'o';

    static final int[] HUMAN_START = { 0, 0 };
    static final int[] CPU_START = { 8, 8 };
    static final int[] TARGET = { 4, 4 };

    static Map<Character, Color> COLOR_MAP = new HashMap<>();
    static Color HUMAN_COLOR = new Color(46, 139, 87); // Sea Green
    static Color CPU_COLOR = new Color(139, 0, 0); // Dark Red
    static Color TARGET_COLOR = new Color(255, 255, 170); // Light Yellow

    static {
        COLOR_MAP.put(EMPTY, Color.WHITE);
        COLOR_MAP.put(HUMAN, Color.WHITE);
        COLOR_MAP.put(CPU, Color.WHITE);
        COLOR_MAP.put(GOLD, new Color(255, 215, 0)); // Gold
        COLOR_MAP.put(BLUE, new Color(30, 144, 255)); // Dodger Blue
        COLOR_MAP.put(RED, new Color(255, 77, 77)); // Red
        COLOR_MAP.put(NEUTRAL, new Color(128, 128, 128)); // Grey
    }

    static final int[][] DIRS = { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } };
}

// ==================== MOVE CLASS ====================

class Move {
    int[] landing;
    int[] jumped;
    char pegType;

    Move(int[] landing, int[] jumped, char pegType) {
        this.landing = landing;
        this.jumped = jumped;
        this.pegType = pegType;
    }
}

// ==================== GAME LOGIC ====================

class GameLogic {
    static boolean inBounds(int[] pos) {
        return pos[0] >= 0 && pos[0] < GameConstants.ROWS &&
                pos[1] >= 0 && pos[1] < GameConstants.COLS;
    }

    static int manhattan(int[] a, int[] b) {
        return Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]);
    }

    static List<Move> getPossibleMoves(int[] pos, char[][] board, int[] opponentPos) {
        List<Move> jumps = new ArrayList<>();
        List<Move> steps = new ArrayList<>();

        int r = pos[0], c = pos[1];

        for (int[] dir : GameConstants.DIRS) {
            int dx = dir[0], dy = dir[1];
            int nr = r + dx, nc = c + dy;

            // 1. Step (Walk 1 tile)
            if (inBounds(new int[] { nr, nc }) && board[nr][nc] == GameConstants.EMPTY &&
                    !(nr == opponentPos[0] && nc == opponentPos[1])) {
                steps.add(new Move(new int[] { nr, nc }, null, '\0'));
            }

            // 2. Jump (Hop over pegs)
            if (inBounds(new int[] { nr, nc }) && isPeg(board[nr][nc])) {
                List<int[]> jumpedPegs = new ArrayList<>();
                int currR = nr, currC = nc;

                while (inBounds(new int[] { currR, currC }) && isPeg(board[currR][currC])) {
                    jumpedPegs.add(new int[] { currR, currC });
                    currR += dx;
                    currC += dy;
                }

                if (inBounds(new int[] { currR, currC }) && board[currR][currC] == GameConstants.EMPTY &&
                        !(currR == opponentPos[0] && currC == opponentPos[1])) {
                    int[] lastJumped = jumpedPegs.get(jumpedPegs.size() - 1);
                    char sym = board[lastJumped[0]][lastJumped[1]];
                    jumps.add(new Move(new int[] { currR, currC }, lastJumped, sym));
                }
            }
        }

        // Priority: Must jump if available, else step
        return jumps.isEmpty() ? steps : jumps;
    }

    static boolean isPeg(char c) {
        return c == GameConstants.GOLD || c == GameConstants.BLUE ||
                c == GameConstants.RED || c == GameConstants.NEUTRAL;
    }

    static class MoveResult {
        char[][] board;
        int[] newPos;
        boolean skipCPU;
        boolean sentBack;
        boolean extraTurn;
        boolean swapPositions;

        MoveResult(char[][] board, int[] newPos, boolean skipCPU, boolean sentBack, boolean extraTurn) {
            this.board = board;
            this.newPos = newPos;
            this.skipCPU = skipCPU;
            this.sentBack = sentBack;
            this.extraTurn = extraTurn;
            this.swapPositions = false;
        }

        MoveResult(char[][] board, int[] newPos, boolean skipCPU, boolean sentBack, boolean extraTurn,
                boolean swapPositions) {
            this.board = board;
            this.newPos = newPos;
            this.skipCPU = skipCPU;
            this.sentBack = sentBack;
            this.extraTurn = extraTurn;
            this.swapPositions = swapPositions;
        }
    }

    static MoveResult applyMove(char[][] board, int[] pos, Move move, int[] startPos) {
        char[][] newBoard = deepCopyBoard(board);
        int[] newPos = move.landing.clone();
        boolean skipCPU = false;
        boolean sentBack = false;
        boolean extraTurn = false;
        boolean swapPositions = false;

        if (move.jumped != null) {
            int jx = move.jumped[0], jy = move.jumped[1];
            newBoard[jx][jy] = GameConstants.EMPTY;

            if (move.pegType == GameConstants.BLUE)
                swapPositions = true;
            else if (move.pegType == GameConstants.RED) {
                sentBack = true;
                newPos = startPos.clone();
            } else if (move.pegType == GameConstants.GOLD)
                extraTurn = true;
        }

        return new MoveResult(newBoard, newPos, skipCPU, sentBack, extraTurn, swapPositions);
    }

    static char[][] deepCopyBoard(char[][] board) {
        char[][] copy = new char[GameConstants.ROWS][GameConstants.COLS];
        for (int i = 0; i < GameConstants.ROWS; i++) {
            System.arraycopy(board[i], 0, copy[i], 0, GameConstants.COLS);
        }
        return copy;
    }
}

// ==================== GREEDY AI ANALYSIS ====================

class DivideAndConquerAnalysis {
    String reasoning;
    double score;
    int[] movePos;

    DivideAndConquerAnalysis(String reasoning, double score, int[] movePos) {
        this.reasoning = reasoning;
        this.score = score;
        this.movePos = movePos;
    }
}

// ==================== AI ENGINE (DIVIDE & CONQUER + DP + SORTING) ====================

class AIEngine {

    static int MAX_DEPTH = 4;

    // Memoization table (DP)
    static Map<String, Double> memo = new HashMap<>();

    static DivideAndConquerAnalysis lastAnalysis;

    static Move getGreedyMove(char[][] board, int[] cpuPos, int[] humanPos, int[] target) {

        long startTime = System.nanoTime();

        List<Move> moves = GameLogic.getPossibleMoves(cpuPos, board, humanPos);
        if (moves.isEmpty()) return null;

        // SORTING — prioritize moves closer to target
        moves.sort((a, b) -> {
            int da = GameLogic.manhattan(a.landing, target);
            int db = GameLogic.manhattan(b.landing, target);
            return da - db;
        });

        Move bestMove = null;
        double bestScore = -Double.MAX_VALUE;

        for (Move move : moves) {

            GameLogic.MoveResult result =
                    GameLogic.applyMove(board, cpuPos, move, GameConstants.CPU_START);

            double score = evaluateDivideConquer(
                    result.board,
                    result.newPos,
                    humanPos,
                    target,
                    MAX_DEPTH - 1,
                    false
            );

            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
        }

        long endTime = System.nanoTime();
        System.out.println("AI Decision Time: " + (endTime - startTime) / 1e6 + " ms");

        if (bestMove != null) {
            lastAnalysis = new DivideAndConquerAnalysis(
                    "D&C chose (" +
                            bestMove.landing[0] + "," +
                            bestMove.landing[1] +
                            ") score=" + bestScore,
                    bestScore,
                    bestMove.landing
            );
        }

        return bestMove;
    }


    /**
     ================= DIVIDE & CONQUER SEARCH =================

     DIVIDE   → Generate all possible moves
     CONQUER  → Recursively evaluate each branch
     COMBINE  → Choose best (max or min)
     */

    static double evaluateDivideConquer(
            char[][] board,
            int[] cpuPos,
            int[] humanPos,
            int[] target,
            int depth,
            boolean cpuTurn
    ) {

        // Memoization key
        String key = Arrays.deepToString(board)
                + cpuPos[0] + cpuPos[1]
                + humanPos[0] + humanPos[1]
                + depth + cpuTurn;

        if (memo.containsKey(key))
            return memo.get(key);

        // BASE CASE
        if (depth == 0) {
            double val = heuristic(cpuPos, humanPos, target);
            memo.put(key, val);
            return val;
        }

        System.out.println("Evaluating depth " + depth + " turn=" + (cpuTurn ? "CPU" : "HUM"));

        double result;

        // ================= CPU TURN (MAXIMIZE) =================
        if (cpuTurn) {

            List<Move> moves = GameLogic.getPossibleMoves(cpuPos, board, humanPos);

            if (moves.isEmpty()) {
                result = heuristic(cpuPos, humanPos, target);
                memo.put(key, result);
                return result;
            }

            // SORT moves for efficiency
            moves.sort((a, b) -> {
                int da = GameLogic.manhattan(a.landing, target);
                int db = GameLogic.manhattan(b.landing, target);
                return da - db;
            });

            double best = -Double.MAX_VALUE;

            for (Move m : moves) {

                GameLogic.MoveResult r =
                        GameLogic.applyMove(board, cpuPos, m, GameConstants.CPU_START);

                best = Math.max(best,
                        evaluateDivideConquer(r.board, r.newPos, humanPos, target, depth - 1, false));
            }

            result = best;
        }

        // ================= HUMAN TURN (MINIMIZE) =================
        else {

            List<Move> moves = GameLogic.getPossibleMoves(humanPos, board, cpuPos);

            if (moves.isEmpty()) {
                result = heuristic(cpuPos, humanPos, target);
                memo.put(key, result);
                return result;
            }

            double worst = Double.MAX_VALUE;

            for (Move m : moves) {

                GameLogic.MoveResult r =
                        GameLogic.applyMove(board, humanPos, m, GameConstants.HUMAN_START);

                worst = Math.min(worst,
                        evaluateDivideConquer(r.board, cpuPos, r.newPos, target, depth - 1, true));
            }

            result = worst;
        }

        memo.put(key, result);
        return result;
    }


    // ================= HEURISTIC FUNCTION =================

    static double heuristic(int[] cpuPos, int[] humanPos, int[] target) {

        int cpuDist = GameLogic.manhattan(cpuPos, target);
        int humanDist = GameLogic.manhattan(humanPos, target);

        return (humanDist - cpuDist) * 100;
    }
}


// ==================== LEVEL DATA ====================

class LevelData {
    int level;
    char[][] board;
    int[] humanStart;
    int[] cpuStart;
    int[] target;

    LevelData(int level, char[][] board, int[] humanStart, int[] cpuStart, int[] target) {
        this.level = level;
        this.board = board;
        this.humanStart = humanStart;
        this.cpuStart = cpuStart;
        this.target = target;
    }
}

class LevelManager {
    static List<LevelData> getLevels() {
        List<LevelData> levels = new ArrayList<>();

        // Level 1: Diamond Path (Easy) - Simple diamond shape with fewer obstacles
        char[][] level1 = {
                { '.', '.', '.', 'o', '.', 'o', '.', '.', '.' },
                { '.', '.', 'o', '.', '.', '.', 'o', '.', '.' },
                { '.', 'o', '.', '.', '.', '.', '.', 'o', '.' },
                { 'o', '.', '.', '.', '.', '.', '.', '.', '.' },
                { '.', '.', '.', '.', '.', '.', '.', '.', '.' },
                { 'o', '.', '.', '.', '.', '.', '.', '.', 'o' },
                { '.', 'o', '.', '.', '.', '.', '.', 'o', '.' },
                { '.', '.', 'o', '.', '.', '.', '.', '.', 'o' },
                { '.', '.', '.', 'o', '.', 'o', 'o', 'o', '.' }
        };
        levels.add(new LevelData(1, level1, GameConstants.HUMAN_START, GameConstants.CPU_START, GameConstants.TARGET));

        // Level 2: Spiral Obstacle Course (Medium) - Spiral shape with multiple paths
        char[][] level2 = {
                { '.', 'o', '.', '.', '.', '.', '.', '.', '.' },
                { 'o', '.', '.', '.', 'o', '.', '.', '.', '.' },
                { 'o', '.', 'o', '.', 'o', '.', '.', '.', '.' },
                { 'o', '.', '.', '.', '.', '.', '.', 'o', '.' },
                { '.', '.', '.', '.', 'o', '.', '.', '.', '.' },
                { 'o', '.', '.', '.', '.', 'o', '.', 'o', '.' },
                { 'o', '.', '.', '.', 'o', 'o', '.', '.', '.' },
                { '.', 'o', '.', 'o', 'o', 'o', '.', '.', 'o' },
                { '.', '.', '.', 'o', 'o', '.', 'o', 'o', '.' }
        };

        levels.add(new LevelData(2, level2, GameConstants.HUMAN_START, GameConstants.CPU_START, GameConstants.TARGET));

        // Level 3: Checkered Gauntlet (Hard) - Dense checkered pattern with blue traps
        char[][] level3 = {
                { 'o', '.', 'o', '.', 'o', '.', 'o', '.', 'o' },
                { '.', 'b', '.', 'b', '.', 'b', '.', 'b', '.' },
                { 'o', '.', 'o', '.', 'o', '.', 'o', '.', 'o' },
                { '.', 'b', '.', '.', '.', '.', '.', 'b', '.' },
                { 'o', '.', 'o', '.', '.', '.', 'o', '.', 'o' },
                { '.', 'b', '.', '.', '.', '.', '.', 'b', '.' },
                { 'o', '.', 'o', '.', 'o', '.', 'o', '.', 'o' },
                { '.', 'b', '.', 'b', '.', 'b', '.', 'b', '.' },
                { 'o', '.', 'o', '.', 'o', '.', 'o', '.', 'o' }
        };
        levels.add(new LevelData(3, level3, GameConstants.HUMAN_START, GameConstants.CPU_START, GameConstants.TARGET));

        // Level 4: Chaos Minefield (Expert) - Dense maze with red mines and gold traps
        char[][] level4 = {
    {'.','r','b','o','r','.','b','r','.'},
    {'o','.','.','.','.','o','.','.','r'},
    {'b','.','r','o','.','.','.','.','b'},
    {'.','.','.','.','.','.','.','.','.'},
    {'r','.','.','.','.','.','r','.','r'},
    {'.','r','.','.','.','.','.','o','.'},
    {'b','.','.','.','.','o','b','.','o'},
    {'r','.','.','.','.','o','.','.','b'},
    {'.','r','b','.','r','.','r','o','.'},
        };
        levels.add(new LevelData(4, level4, GameConstants.HUMAN_START, GameConstants.CPU_START, GameConstants.TARGET));

        return levels;
    }
}

// ==================== NOTIFICATION SYSTEM ====================

class NotificationQueue {
    private static final int NOTIFICATION_GAP = 10;
    private static final int NOTIFICATION_DURATION = 2000;

    private class NotificationItem {
        String message;
        JTextArea area;
        javax.swing.Timer dismissTimer;
        long createdAt;
        long expiryTime;
        long remainingMillis;

        NotificationItem(String message, JTextArea area) {
            this.message = message;
            this.area = area;
            this.createdAt = System.currentTimeMillis();
            this.remainingMillis = NOTIFICATION_DURATION;
        }
    }

    private Queue<NotificationItem> notifications;
    private JPanel container;
    private int maxWidth;
    private int startX;
    private int startY;

    NotificationQueue(JPanel container, int startX, int startY, int maxWidth) {
        this.container = container;
        this.startX = startX;
        this.startY = startY;
        this.maxWidth = maxWidth;
        this.notifications = new ConcurrentLinkedQueue<>();
    }

    void showNotification(String message) {
        JTextArea area = new JTextArea();
        area.setText(message);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setOpaque(true);
        area.setBackground(new Color(50, 50, 50, 220));
        area.setForeground(Color.WHITE);
        area.setFont(new Font("Arial", Font.PLAIN, 13));
        area.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        NotificationItem item = new NotificationItem(message, area);
        notifications.add(item);
        container.add(area);

        // Calculate preferred size with wrap at maxWidth
        int allowed = Math.max(120, Math.min(maxWidth, container.getWidth() - 40));
        area.setSize(allowed, Short.MAX_VALUE);
        Dimension pref = area.getPreferredSize();
        int w = Math.min(pref.width, allowed);
        int h = pref.height;

        // store bounds immediately
        area.setBounds(startX + (maxWidth - w) / 2, startY - h, w, h);

        updatePositions();

        // start dismiss timer and add hover controls to pause/resume
        startDismissTimer(item, NOTIFICATION_DURATION);

        area.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (item.dismissTimer != null) {
                    item.dismissTimer.stop();
                    item.remainingMillis = Math.max(0, item.expiryTime - System.currentTimeMillis());
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                long rem = Math.max(0, item.remainingMillis);
                if (rem <= 0) {
                    dismissNotification(item);
                } else {
                    startDismissTimer(item, (int) rem);
                }
            }
        });
    }

    private void startDismissTimer(NotificationItem item, int delayMillis) {
        if (item.dismissTimer != null) {
            item.dismissTimer.stop();
        }
        item.expiryTime = System.currentTimeMillis() + delayMillis;
        item.dismissTimer = new javax.swing.Timer(delayMillis, e -> dismissNotification(item));
        item.dismissTimer.setRepeats(false);
        item.dismissTimer.start();
    }

    private void dismissNotification(NotificationItem item) {
        notifications.remove(item);
        container.remove(item.area);
        updatePositions();
        container.repaint();
    }

    private void updatePositions() {
        int yOffset = 0;
        List<NotificationItem> items = new ArrayList<>(notifications);
        for (int i = items.size() - 1; i >= 0; i--) {
            NotificationItem item = items.get(i);
            Dimension d = item.area.getPreferredSize();
            int w = Math.min(d.width, maxWidth);
            int h = d.height;
            int x = startX + (maxWidth - w) / 2;
            int y = startY - yOffset - h;
            item.area.setBounds(x, y, w, h);
            item.area.setVisible(true);
            yOffset += h + NOTIFICATION_GAP;
        }
    }

    void clearAll() {
        for (NotificationItem item : notifications) {
            if (item.dismissTimer != null) {
                item.dismissTimer.stop();
            }
            container.remove(item.area);
        }
        notifications.clear();
    }
}

// ==================== GUI PANEL ====================

class GamePanel extends JPanel {
    private List<LevelData> levels;
    private LevelData currentLevel;
    private char[][] board;
    private int[] humanPos;
    private int[] cpuPos;
    private String turn;
    private List<Move> legalMoves;
    private JSpinner levelSpinner;
    private int currentLevelNum = 1;
    private boolean gameOver = false;
    private boolean cpuMoving = false;

    private NotificationQueue notificationQueue;
    private int consecutiveCpuMoves = 0;

    GamePanel() {
        setPreferredSize(new Dimension(
                GameConstants.COLS * GameConstants.CELL + 2 * GameConstants.MARGIN,
                GameConstants.ROWS * GameConstants.CELL + 2 * GameConstants.MARGIN + 80));
        setBackground(Color.WHITE);
        setFocusable(true);
        setLayout(null);

        // Setup notification queue
        int centerX = (GameConstants.COLS * GameConstants.CELL + 2 * GameConstants.MARGIN) / 2 - 150;
        notificationQueue = new NotificationQueue(this, centerX, 100, 300);

        levels = LevelManager.getLevels();
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                onMouseClick(e);
            }
        });

        // Setup level spinner
        int panelY = GameConstants.MARGIN + GameConstants.ROWS * GameConstants.CELL + 10;
        levelSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 4, 1));
        levelSpinner.setBounds(GameConstants.MARGIN + 60, panelY + 5, 50, 25);
        add(levelSpinner);

        // Setup restart button
        JButton restartBtn = new JButton("Load/Restart");
        restartBtn.setBounds(GameConstants.MARGIN + 120, panelY + 5, 120, 25);
        restartBtn.addActionListener(e -> {
            int level = (int) levelSpinner.getValue();
            loadLevel(level);
        });
        add(restartBtn);

        loadLevel(1);
    }

    void loadLevel(int levelNum) {
        if (levelNum < 1 || levelNum > 4)
            levelNum = 1;
        currentLevelNum = levelNum;

        currentLevel = levels.get(levelNum - 1);
        board = GameLogic.deepCopyBoard(currentLevel.board);
        humanPos = currentLevel.humanStart.clone();
        cpuPos = currentLevel.cpuStart.clone();
        turn = "HUM";
        legalMoves = new ArrayList<>();
        gameOver = false;
        cpuMoving = false;
        consecutiveCpuMoves = 0;

        updateStatus("Level " + levelNum + ". Green(W) vs Red(B). CPU will dominate!");
        repaint();
        prepareHumanTurn();
    }

    void prepareHumanTurn() {
        turn = "HUM";
        consecutiveCpuMoves = 0;
        legalMoves = GameLogic.getPossibleMoves(humanPos, board, cpuPos);

        if (legalMoves.isEmpty()) {
            updateStatus("Trapped! CPU Turn.");
            turn = "CPU";
            cpuMoving = true;
            consecutiveCpuMoves = 1;
            new javax.swing.Timer(500, e -> cpuTurn()).start();
            return;
        }

        repaint();
    }

    void onMouseClick(MouseEvent e) {
        if (!turn.equals("HUM") || gameOver)
            return;

        int row = (e.getY() - GameConstants.MARGIN) / GameConstants.CELL;
        int col = (e.getX() - GameConstants.MARGIN) / GameConstants.CELL;

        for (Move move : legalMoves) {
            if (move.landing[0] == row && move.landing[1] == col) {
                executeHumanMove(move);
                return;
            }
        }

        repaint();
    }

    void executeHumanMove(Move move) {
        GameLogic.MoveResult result = GameLogic.applyMove(board, humanPos, move, GameConstants.HUMAN_START);

        board = result.board;
        if (result.sentBack) {
            humanPos = GameConstants.HUMAN_START;
            updateStatus("Reset by Red Peg!");
        } else {
            humanPos = result.newPos;
        }

        repaint();

        if (humanPos[0] == GameConstants.TARGET[0] && humanPos[1] == GameConstants.TARGET[1]) {
            gameOver = true;
            JOptionPane.showMessageDialog(this, "You Won! Great moves!");
            return;
        }

        if (result.extraTurn) {
            updateStatus("Gold Peg! Go again.");
            prepareHumanTurn();
        } else if (result.swapPositions) {
            // Swap positions with CPU
            int[] temp = humanPos.clone();
            humanPos = cpuPos.clone();
            cpuPos = temp;
            updateStatus("Blue Peg! Positions swapped!");
            repaint();
            prepareHumanTurn();
        } else {
            turn = "CPU";
            cpuMoving = true;
            consecutiveCpuMoves = 1;
            legalMoves = new ArrayList<>();
            new javax.swing.Timer(500, e -> cpuTurn()).start();
        }
    }

    void cpuTurn() {
        if (gameOver || !cpuMoving)
            return;

        Move move = AIEngine.getGreedyMove(board, cpuPos, humanPos, GameConstants.TARGET);

        if (move == null) {
            cpuMoving = false;
            updateStatus("CPU Stuck. Your turn.");
            prepareHumanTurn();
            return;
        }

        // Show greedy analysis before making the move
        if (AIEngine.lastAnalysis != null) {
            updateStatus(AIEngine.lastAnalysis.reasoning);
        }

        GameLogic.MoveResult result = GameLogic.applyMove(board, cpuPos, move, GameConstants.CPU_START);

        board = result.board;
        if (result.sentBack) {
            cpuPos = GameConstants.CPU_START;
            updateStatus("CPU Reset!");
        } else {
            cpuPos = result.newPos;
        }

        repaint();

        if (cpuPos[0] == GameConstants.TARGET[0] && cpuPos[1] == GameConstants.TARGET[1]) {
            gameOver = true;
            cpuMoving = false;
            JOptionPane.showMessageDialog(this, "CPU Wins!");
            return;
        }

        if (result.extraTurn) {
            updateStatus("CPU hit Gold! Extra turn.");
            consecutiveCpuMoves++;
            int delay = consecutiveCpuMoves > 2 ? 1500 : 2000;
            new javax.swing.Timer(delay, e -> cpuTurn()).start();
        } else if (result.swapPositions) {
            // CPU hit BLUE peg - swap positions
            int[] temp = humanPos.clone();
            humanPos = cpuPos.clone();
            cpuPos = temp;
            updateStatus("CPU hit Blue! Positions swapped!");
            repaint();
            cpuMoving = false;
            prepareHumanTurn();
        } else {
            cpuMoving = false;
            prepareHumanTurn();
        }
    }

    void updateStatus(String message) {
        notificationQueue.showNotification(message);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw grid
        for (int r = 0; r < GameConstants.ROWS; r++) {
            for (int c = 0; c < GameConstants.COLS; c++) {
                int x1 = GameConstants.MARGIN + c * GameConstants.CELL;
                int y1 = GameConstants.MARGIN + r * GameConstants.CELL;
                int x2 = x1 + GameConstants.CELL;
                int y2 = y1 + GameConstants.CELL;

                g2d.setColor(new Color(240, 240, 240));
                g2d.fillRect(x1, y1, GameConstants.CELL, GameConstants.CELL);
                g2d.setColor(new Color(204, 204, 204));
                g2d.drawRect(x1, y1, GameConstants.CELL, GameConstants.CELL);

                // Draw target
                if (r == GameConstants.TARGET[0] && c == GameConstants.TARGET[1]) {
                    g2d.setColor(GameConstants.TARGET_COLOR);
                    g2d.fillRect(x1 + 4, y1 + 4, GameConstants.CELL - 8, GameConstants.CELL - 8);
                    g2d.setColor(new Color(255, 165, 0));
                    g2d.setFont(new Font("Arial", Font.BOLD, 8));
                    g2d.drawString("GOAL", x1 + 12, y1 + 24);
                }

                // Draw pegs
                char sym = board[r][c];
                if (sym == GameConstants.GOLD || sym == GameConstants.BLUE ||
                        sym == GameConstants.RED || sym == GameConstants.NEUTRAL) {
                    int cx = x1 + GameConstants.CELL / 2;
                    int cy = y1 + GameConstants.CELL / 2;
                    g2d.setColor(GameConstants.COLOR_MAP.get(sym));
                    g2d.fillOval(cx - 14, cy - 14, 28, 28);
                }
            }
        }

        // Draw players
        int hx = GameConstants.MARGIN + humanPos[1] * GameConstants.CELL + GameConstants.CELL / 2;
        int hy = GameConstants.MARGIN + humanPos[0] * GameConstants.CELL + GameConstants.CELL / 2;
        g2d.setColor(GameConstants.HUMAN_COLOR);
        g2d.fillOval(hx - 16, hy - 16, 32, 32);
        g2d.setColor(Color.BLACK);
        g2d.setStroke(new BasicStroke(2));
        g2d.drawOval(hx - 16, hy - 16, 32, 32);
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Helvetica", Font.BOLD, 12));
        FontMetrics fm = g2d.getFontMetrics();
        g2d.drawString("W", hx - fm.stringWidth("W") / 2, hy + fm.getAscent() / 2);

        int cx = GameConstants.MARGIN + cpuPos[1] * GameConstants.CELL + GameConstants.CELL / 2;
        int cy = GameConstants.MARGIN + cpuPos[0] * GameConstants.CELL + GameConstants.CELL / 2;
        g2d.setColor(GameConstants.CPU_COLOR);
        g2d.fillOval(cx - 16, cy - 16, 32, 32);
        g2d.setColor(Color.BLACK);
        g2d.drawOval(cx - 16, cy - 16, 32, 32);
        g2d.setColor(Color.WHITE);
        g2d.drawString("B", cx - fm.stringWidth("B") / 2, cy + fm.getAscent() / 2);

        // Draw highlights for legal moves
        if (turn.equals("HUM")) {
            for (Move move : legalMoves) {
                int mx = GameConstants.MARGIN + move.landing[1] * GameConstants.CELL;
                int my = GameConstants.MARGIN + move.landing[0] * GameConstants.CELL;
                g2d.setColor(move.jumped != null ? Color.GREEN : Color.BLUE);
                g2d.setStroke(new BasicStroke(3));
                g2d.drawRect(mx + 6, my + 6, GameConstants.CELL - 12, GameConstants.CELL - 12);
            }
        }

        // Draw control panel
        int panelY = GameConstants.MARGIN + GameConstants.ROWS * GameConstants.CELL + 10;
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.fillRect(GameConstants.MARGIN, panelY, getWidth() - 2 * GameConstants.MARGIN, 60);

        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.PLAIN, 12));
        g2d.drawString("Level: ", GameConstants.MARGIN + 10, panelY + 20);
    }
}