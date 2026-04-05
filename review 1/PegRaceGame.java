import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Peg Race — "The CPU Dominance Edition"
 * Human (W) vs CPU (B)
 * 4 Levels with increasing difficulty
 * AI: Ruthlessly efficient. Prioritizes combos and blocking.
 */

public class PegRaceGame extends JFrame {
    /**
     * Main entry point for the game
     * Time Complexity: O(1) - Constant time for setup
     * Space Complexity: O(1) - Fixed memory for frame creation
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            PegRaceGame frame = new PegRaceGame();
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    /**
     * Constructor for main game window
     * Time Complexity: O(1) - Constant time initialization
     * Space Complexity: O(n²) for board storage where n=9
     */
    public PegRaceGame() {
        super("Peg Race — CPU Dominance Edition");
        GamePanel gamePanel = new GamePanel();
        add(gamePanel);
    }
}

// ==================== CONSTANTS & CONFIG ====================

class GameConstants {
    // Board dimensions and spacing
    static final int CELL = 50;               // Size of each grid cell in pixels
    static final int MARGIN = 20;             // Border margin around board
    static final int ROWS = 9;                // 9x9 game board
    static final int COLS = 9;

    // Player symbols
    static final char EMPTY = '.';           // Empty cell
    static final char HUMAN = 'W';           // Human player (White)
    static final char CPU = 'B';             // CPU player (Black)

    // Special Peg types with unique abilities
    static final char GOLD = 'g';            // Grants extra turn
    static final char BLUE = 'b';            // Swaps player positions
    static final char RED = 'r';             // Sends player back to start
    static final char NEUTRAL = 'o';         // Basic peg with no special effect

    // Game positions
    static final int[] HUMAN_START = { 0, 0 }; // Top-left corner
    static final int[] CPU_START = { 8, 8 };   // Bottom-right corner
    static final int[] TARGET = { 4, 4 };      // Center of board (win condition)

    // Color mapping for visual representation
    static Map<Character, Color> COLOR_MAP = new HashMap<>();
    static Color HUMAN_COLOR = new Color(46, 139, 87);   // Sea Green for human
    static Color CPU_COLOR = new Color(139, 0, 0);       // Dark Red for CPU
    static Color TARGET_COLOR = new Color(255, 255, 170);// Light Yellow for goal

    // Initialize color mappings
    static {
        COLOR_MAP.put(EMPTY, Color.WHITE);
        COLOR_MAP.put(HUMAN, Color.WHITE);
        COLOR_MAP.put(CPU, Color.WHITE);
        COLOR_MAP.put(GOLD, new Color(255, 215, 0));     // Gold
        COLOR_MAP.put(BLUE, new Color(30, 144, 255));    // Dodger Blue
        COLOR_MAP.put(RED, new Color(255, 77, 77));      // Bright Red
        COLOR_MAP.put(NEUTRAL, new Color(128, 128, 128));// Grey
    }

    // Movement directions: up, down, left, right
    static final int[][] DIRS = { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } };
}

// ==================== MOVE CLASS ====================

/**
 * Represents a game move with landing position and jumped peg info
 * Time Complexity: O(1) for creation
 * Space Complexity: O(1) - stores 3 integers and 1 character
 */
class Move {
    int[] landing;      // Final position after move
    int[] jumped;       // Position of jumped peg (if any)
    char jumpedSymbol;  // Type of jumped peg

    Move(int[] landing, int[] jumped, char jumpedSymbol) {
        this.landing = landing;
        this.jumped = jumped;
        this.jumpedSymbol = jumpedSymbol;
    }
}

// ==================== GAME LOGIC ====================

class GameLogic {
    /**
     * Check if position is within board boundaries
     * Time Complexity: O(1)
     * Space Complexity: O(1)
     */
    static boolean inBounds(int[] pos) {
        return pos[0] >= 0 && pos[0] < GameConstants.ROWS &&
                pos[1] >= 0 && pos[1] < GameConstants.COLS;
    }

    /**
     * Calculate Manhattan distance between two positions
     * Used for AI heuristic evaluation
     * Time Complexity: O(1)
     * Space Complexity: O(1)
     */
    static int manhattan(int[] a, int[] b) {
        return Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]);
    }

    /**
     * Get all possible moves for a player at given position
     * Time Complexity: O(d * n) where d=4 directions, n=board size in worst case
     * Space Complexity: O(m) where m = number of possible moves (max 4)
     */
    static List<Move> getPossibleMoves(int[] pos, char[][] board, int[] opponentPos) {
        List<Move> jumps = new ArrayList<>();
        List<Move> steps = new ArrayList<>();

        int r = pos[0], c = pos[1];

        for (int[] dir : GameConstants.DIRS) {
            int dx = dir[0], dy = dir[1];
            int nr = r + dx, nc = c + dy;

            // 1. Step (Walk 1 tile) - basic move to adjacent empty cell
            if (inBounds(new int[] { nr, nc }) && board[nr][nc] == GameConstants.EMPTY &&
                    !(nr == opponentPos[0] && nc == opponentPos[1])) {
                steps.add(new Move(new int[] { nr, nc }, null, '\0'));
            }

            // 2. Jump (Hop over pegs) - chain jump over multiple pegs
            if (inBounds(new int[] { nr, nc }) && isPeg(board[nr][nc])) {
                List<int[]> jumpedPegs = new ArrayList<>();
                int currR = nr, currC = nc;

                // Continue jumping while there are consecutive pegs
                while (inBounds(new int[] { currR, currC }) && isPeg(board[currR][currC])) {
                    jumpedPegs.add(new int[] { currR, currC });
                    currR += dx;
                    currC += dy;
                }

                // Valid jump if landing on empty cell not occupied by opponent
                if (inBounds(new int[] { currR, currC }) && board[currR][currC] == GameConstants.EMPTY &&
                        !(currR == opponentPos[0] && currC == opponentPos[1])) {
                    int[] lastJumped = jumpedPegs.get(jumpedPegs.size() - 1);
                    char sym = board[lastJumped[0]][lastJumped[1]];
                    jumps.add(new Move(new int[] { currR, currC }, lastJumped, sym));
                }
            }
        }

        // Priority: Must jump if available (game rule), else step
        return jumps.isEmpty() ? steps : jumps;
    }

    /**
     * Check if character represents a jumpable peg
     * Time Complexity: O(1)
     * Space Complexity: O(1)
     */
    static boolean isPeg(char c) {
        return c == GameConstants.GOLD || c == GameConstants.BLUE ||
                c == GameConstants.RED || c == GameConstants.NEUTRAL;
    }

    /**
     * Result container for move application
     * Stores board state and special move effects
     */
    static class MoveResult {
        char[][] board;          // Updated board state
        int[] newPos;            // New player position
        boolean skipCPU;         // Special effect flags
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

    /**
     * Apply a move to the game board and handle special effects
     * Time Complexity: O(n²) for board copy where n=9
     * Space Complexity: O(n²) for new board copy
     */
    static MoveResult applyMove(char[][] board, int[] pos, Move move, int[] startPos) {
        char[][] newBoard = deepCopyBoard(board);
        int[] newPos = move.landing.clone();
        boolean skipCPU = false;
        boolean sentBack = false;
        boolean extraTurn = false;
        boolean swapPositions = false;

        // Handle jumped peg special effects
        if (move.jumped != null) {
            int jx = move.jumped[0], jy = move.jumped[1];
            newBoard[jx][jy] = GameConstants.EMPTY;  // Remove jumped peg

            // Apply special effects based on peg type
            if (move.jumpedSymbol == GameConstants.BLUE)
                swapPositions = true;               // Swap player positions
            else if (move.jumpedSymbol == GameConstants.RED) {
                sentBack = true;                    // Send player back to start
                newPos = startPos.clone();
            } else if (move.jumpedSymbol == GameConstants.GOLD)
                extraTurn = true;                   // Grant extra turn
        }

        return new MoveResult(newBoard, newPos, skipCPU, sentBack, extraTurn, swapPositions);
    }

    /**
     * Create deep copy of board state
     * Time Complexity: O(n²) where n=9
     * Space Complexity: O(n²) for new board
     */
    static char[][] deepCopyBoard(char[][] board) {
        char[][] copy = new char[GameConstants.ROWS][GameConstants.COLS];
        for (int i = 0; i < GameConstants.ROWS; i++) {
            System.arraycopy(board[i], 0, copy[i], 0, GameConstants.COLS);
        }
        return copy;
    }
}

// ==================== GREEDY AI ANALYSIS ====================

/**
 * Stores AI decision reasoning for display to player
 * Time Complexity: O(1) for creation
 * Space Complexity: O(1) - stores string and score
 */
class GreedyAnalysis {
    String reasoning;   // Human-readable explanation of AI decision
    double score;       // Numerical score assigned to move
    int[] movePos;      // Proposed move position

    GreedyAnalysis(String reasoning, double score, int[] movePos) {
        this.reasoning = reasoning;
        this.score = score;
        this.movePos = movePos;
    }
}

// ==================== AI ENGINE ====================

class AIEngine {
    static Random random = new Random();
    static GreedyAnalysis lastAnalysis;  // Stores last AI decision for display

    /**
     * Greedy AI that evaluates moves based on multiple heuristic factors
     * Time Complexity: O(m * k) where m = moves, k = evaluation factors (7 tiers)
     * Space Complexity: O(1) - constant space for evaluation
     */
    static Move getGreedyMove(char[][] board, int[] cpuPos, int[] humanPos, int[] target) {
        List<Move> moves = GameLogic.getPossibleMoves(cpuPos, board, humanPos);
        if (moves.isEmpty())
            return null;

        Move bestMove = null;
        double bestScore = -Double.MAX_VALUE;
        String bestReasoning = "";

        int currDist = GameLogic.manhattan(cpuPos, target);
        int humanDist = GameLogic.manhattan(humanPos, target);

        // Evaluate each possible move
        for (Move move : moves) {
            String reasoning = "";
            double score = 0;

            // TIER 1: INSTANT WIN - Highest priority
            if (move.landing[0] == target[0] && move.landing[1] == target[1]) {
                lastAnalysis = new GreedyAnalysis(
                        "🎯 GREEDY DECISION: INSTANT WIN! Moving to goal position!",
                        Double.MAX_VALUE,
                        move.landing);
                return move;  // Immediate return for winning move
            }

            // TIER 2: BLOCK HUMAN WINNING - Defensive play
            if (humanDist <= 3 && move.jumpedSymbol == GameConstants.BLUE) {
                lastAnalysis = new GreedyAnalysis(
                        "🛡️ GREEDY DECISION: DEFENSIVE MOVE! Human is close (dist=" + humanDist
                                + "), jumping BLUE peg to block!",
                        Double.MAX_VALUE - 1,
                        move.landing);
                return move;  // High-priority defensive move
            }

            // TIER 3: MOVE TOWARDS TARGET - Basic pathfinding
            int newDist = GameLogic.manhattan(move.landing, target);
            int distGain = currDist - newDist;  // Positive = closer to goal
            score = distGain * 100;             // Weight distance gain heavily
            reasoning += "Distance gain: " + distGain + " (score: " + (int) score + ") | ";

            // TIER 4: SPECIAL PEG STRATEGY - Evaluate peg type effects
            if (move.jumped != null) {
                if (move.jumpedSymbol == GameConstants.GOLD) {
                    score += 200;               // Extra turn is valuable
                    reasoning += "GOLD peg: +200 (extra turn!) | ";
                } else if (move.jumpedSymbol == GameConstants.BLUE) {
                    if (humanDist < 6) {
                        score += 150;           // Strategic swap when human is close
                        reasoning += "BLUE peg: +150 (human close, strategic swap) | ";
                    } else {
                        score += 100;           // Regular swap value
                        reasoning += "BLUE peg: +100 (swap move) | ";
                    }
                } else if (move.jumpedSymbol == GameConstants.RED) {
                    if (distGain > 0) {
                        score += 50;            // Acceptable if gaining distance
                        reasoning += "RED peg: +50 (safe reset, gaining distance) | ";
                    } else {
                        score -= 1000;          // Avoid losing progress
                        reasoning += "RED peg: -1000 (AVOID - losing distance!) | ";
                    }
                } else if (move.jumpedSymbol == GameConstants.NEUTRAL) {
                    score += 50;                // Basic peg value
                    reasoning += "Neutral peg: +50 | ";
                }
            } else {
                score -= 5;                     // Penalize non-jump moves
                reasoning += "No peg jumped: -5 (prefer jumps) | ";
            }

            // TIER 5: AGGRESSIVE CENTER APPROACH - Control board center
            int dr = Math.abs(move.landing[0] - 4);
            int dc = Math.abs(move.landing[1] - 4);
            double centerScore = (9 - dr - dc) * 10;  // Closer to center = higher score
            score += centerScore;
            reasoning += "Center bonus: +" + (int) centerScore + " | ";

            // TIER 6: HUMAN CONTAINMENT - Keep pressure on human
            if (humanDist < currDist) {
                score += 50;                    // Bonus for being closer to goal than human
                reasoning += "Human containment: +50 | ";
            }

            // TIER 7: TIE-BREAKER - Add randomness to avoid predictable patterns
            score += random.nextDouble() * 5;
            reasoning += "Tie-breaker: +" + String.format("%.1f", score % 5);
            
            // Format reasoning for display
            reasoning = "CPU considers move to (" + move.landing[0] + "," + move.landing[1] + "): " + reasoning;

            // Track best move
            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
                bestReasoning = reasoning;
            }
        }

        // Store analysis for UI display
        if (bestMove != null) {
            lastAnalysis = new GreedyAnalysis(bestReasoning, bestScore, bestMove.landing);
        }
        return bestMove;
    }
}

// ==================== LEVEL DATA ====================

/**
 * Container for level configuration data
 * Time Complexity: O(1) for creation
 * Space Complexity: O(n²) for board storage
 */
class LevelData {
    int level;           // Level number (1-4)
    char[][] board;      // Board layout
    int[] humanStart;    // Human starting position
    int[] cpuStart;      // CPU starting position
    int[] target;        // Goal position

    LevelData(int level, char[][] board, int[] humanStart, int[] cpuStart, int[] target) {
        this.level = level;
        this.board = board;
        this.humanStart = humanStart;
        this.cpuStart = cpuStart;
        this.target = target;
    }
}

class LevelManager {
    /**
     * Creates and returns all 4 game levels with increasing difficulty
     * Time Complexity: O(1) - Fixed number of levels
     * Space Complexity: O(4 * n²) where n=9, 4 levels
     */
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

/**
 * Manages on-screen notifications with hover-to-pause functionality
 * Time Complexity: O(k) for updatePositions where k = notification count
 * Space Complexity: O(k) for storing notifications
 */
class NotificationQueue {
    private static final int NOTIFICATION_GAP = 10;        // Space between notifications
    private static final int NOTIFICATION_DURATION = 2000; // Default display time (ms)

    /**
     * Inner class representing a single notification
     */
    private class NotificationItem {
        String message;              // Notification text
        JTextArea area;              // UI component for display
        javax.swing.Timer dismissTimer; // Timer for auto-dismissal
        long createdAt;              // Creation timestamp
        long expiryTime;             // When notification expires
        long remainingMillis;        // Remaining display time (for pause/resume)

        NotificationItem(String message, JTextArea area) {
            this.message = message;
            this.area = area;
            this.createdAt = System.currentTimeMillis();
            this.remainingMillis = NOTIFICATION_DURATION;
        }
    }

    private Queue<NotificationItem> notifications; // Queue of active notifications
    private JPanel container;                      // Parent panel for display
    private int maxWidth;                          // Maximum notification width
    private int startX, startY;                    // Starting position for notifications

    /**
     * Initialize notification system
     * Time Complexity: O(1)
     * Space Complexity: O(1)
     */
    NotificationQueue(JPanel container, int startX, int startY, int maxWidth) {
        this.container = container;
        this.startX = startX;
        this.startY = startY;
        this.maxWidth = maxWidth;
        this.notifications = new ConcurrentLinkedQueue<>(); // Thread-safe queue
    }

    /**
     * Display a new notification
     * Time Complexity: O(k) where k = current notification count
     * Space Complexity: O(1) per notification
     */
    void showNotification(String message) {
        // Create UI component for notification
        JTextArea area = new JTextArea();
        area.setText(message);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setOpaque(true);
        area.setBackground(new Color(50, 50, 50, 220)); // Semi-transparent dark background
        area.setForeground(Color.WHITE);
        area.setFont(new Font("Arial", Font.PLAIN, 13));
        area.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        // Create notification item and add to queue
        NotificationItem item = new NotificationItem(message, area);
        notifications.add(item);
        container.add(area);

        // Calculate optimal size with word wrapping
        int allowed = Math.max(120, Math.min(maxWidth, container.getWidth() - 40));
        area.setSize(allowed, Short.MAX_VALUE);
        Dimension pref = area.getPreferredSize();
        int w = Math.min(pref.width, allowed);
        int h = pref.height;

        // Set initial position
        area.setBounds(startX + (maxWidth - w) / 2, startY - h, w, h);

        updatePositions(); // Recalculate all notification positions

        // Start auto-dismiss timer with hover controls
        startDismissTimer(item, NOTIFICATION_DURATION);

        // Add hover functionality to pause/resume dismissal
        area.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                // Pause timer when mouse hovers
                if (item.dismissTimer != null) {
                    item.dismissTimer.stop();
                    item.remainingMillis = Math.max(0, item.expiryTime - System.currentTimeMillis());
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                // Resume timer when mouse leaves
                long rem = Math.max(0, item.remainingMillis);
                if (rem <= 0) {
                    dismissNotification(item);
                } else {
                    startDismissTimer(item, (int) rem);
                }
            }
        });
    }

    /**
     * Start or restart dismissal timer
     * Time Complexity: O(1)
     * Space Complexity: O(1)
     */
    private void startDismissTimer(NotificationItem item, int delayMillis) {
        if (item.dismissTimer != null) {
            item.dismissTimer.stop();
        }
        item.expiryTime = System.currentTimeMillis() + delayMillis;
        item.dismissTimer = new javax.swing.Timer(delayMillis, e -> dismissNotification(item));
        item.dismissTimer.setRepeats(false);
        item.dismissTimer.start();
    }

    /**
     * Remove notification from display
     * Time Complexity: O(k) where k = notification count
     * Space Complexity: O(1)
     */
    private void dismissNotification(NotificationItem item) {
        notifications.remove(item);
        container.remove(item.area);
        updatePositions(); // Recalculate positions after removal
        container.repaint();
    }

    /**
     * Update positions of all notifications (stack from top)
     * Time Complexity: O(k) where k = notification count
     * Space Complexity: O(1)
     */
    private void updatePositions() {
        int yOffset = 0;
        List<NotificationItem> items = new ArrayList<>(notifications);
        
        // Stack notifications from bottom to top
        for (int i = items.size() - 1; i >= 0; i--) {
            NotificationItem item = items.get(i);
            Dimension d = item.area.getPreferredSize();
            int w = Math.min(d.width, maxWidth);
            int h = d.height;
            int x = startX + (maxWidth - w) / 2;
            int y = startY - yOffset - h;
            item.area.setBounds(x, y, w, h);
            item.area.setVisible(true);
            yOffset += h + NOTIFICATION_GAP; // Add gap between notifications
        }
    }

    /**
     * Clear all notifications
     * Time Complexity: O(k) where k = notification count
     * Space Complexity: O(1)
     */
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

/**
 * Main game panel handling rendering and user interaction
 * Time Complexity for painting: O(n²) where n=9 (board size)
 * Space Complexity: O(n²) for board storage
 */
class GamePanel extends JPanel {
    private List<LevelData> levels;     // All available levels (4 levels)
    private LevelData currentLevel;     // Currently active level
    private char[][] board;             // Game board state
    private int[] humanPos;             // Human player position
    private int[] cpuPos;               // CPU player position
    private String turn;                // Current turn: "HUM" or "CPU"
    private List<Move> legalMoves;      // Legal moves for current player
    private JSpinner levelSpinner;      // Level selection spinner
    private int currentLevelNum = 1;    // Current level number (1-4)
    private boolean gameOver = false;   // Game state flag
    private boolean cpuMoving = false;  // CPU turn in progress flag

    private NotificationQueue notificationQueue; // Notification system
    private int consecutiveCpuMoves = 0; // Track CPU extra turns

    /**
     * Constructor - sets up game panel and UI components
     * Time Complexity: O(1) for setup
     * Space Complexity: O(n²) for board initialization
     */
    GamePanel() {
        // Set panel dimensions
        setPreferredSize(new Dimension(
                GameConstants.COLS * GameConstants.CELL + 2 * GameConstants.MARGIN,
                GameConstants.ROWS * GameConstants.CELL + 2 * GameConstants.MARGIN + 80));
        setBackground(Color.WHITE);
        setFocusable(true);
        setLayout(null); // Absolute positioning for custom layout

        // Setup notification system (centered at top)
        int centerX = (GameConstants.COLS * GameConstants.CELL + 2 * GameConstants.MARGIN) / 2 - 150;
        notificationQueue = new NotificationQueue(this, centerX, 100, 300);

        // Load level data (4 levels only)
        levels = LevelManager.getLevels();
        
        // Add mouse listener for human moves
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                onMouseClick(e);
            }
        });

        // Setup level selection spinner (1-4)
        int panelY = GameConstants.MARGIN + GameConstants.ROWS * GameConstants.CELL + 10;
        levelSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 4, 1)); // Changed to max 4
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

        // Start with level 1
        loadLevel(1);
    }

    /**
     * Load a specific level and reset game state
     * Time Complexity: O(n²) for board copy
     * Space Complexity: O(n²) for new board
     */
    void loadLevel(int levelNum) {
        // Validate level number (1-4)
        if (levelNum < 1 || levelNum > 4) // Changed to 4
            levelNum = 1;
        currentLevelNum = levelNum;

        // Load level configuration
        currentLevel = levels.get(levelNum - 1);
        board = GameLogic.deepCopyBoard(currentLevel.board);
        humanPos = currentLevel.humanStart.clone();
        cpuPos = currentLevel.cpuStart.clone();
        turn = "HUM";
        legalMoves = new ArrayList<>();
        gameOver = false;
        cpuMoving = false;
        consecutiveCpuMoves = 0;

        // Show level start notification
        updateStatus("Level " + levelNum + ". Green(W) vs Red(B). CPU will dominate!");
        repaint();
        prepareHumanTurn();
    }

    /**
     * Prepare for human player's turn
     * Time Complexity: O(m) where m = possible moves
     * Space Complexity: O(m) for storing legal moves
     */
    void prepareHumanTurn() {
        turn = "HUM";
        consecutiveCpuMoves = 0; // Reset CPU consecutive moves counter
        
        // Calculate legal moves for human
        legalMoves = GameLogic.getPossibleMoves(humanPos, board, cpuPos);

        // Check if human is trapped (no legal moves)
        if (legalMoves.isEmpty()) {
            updateStatus("Trapped! CPU Turn.");
            turn = "CPU";
            cpuMoving = true;
            consecutiveCpuMoves = 1;
            // Delay CPU turn for visual feedback
            new javax.swing.Timer(500, e -> cpuTurn()).start();
            return;
        }

        repaint(); // Refresh UI to show legal move highlights
    }

    /**
     * Handle mouse click for human move selection
     * Time Complexity: O(m) where m = legal moves
     * Space Complexity: O(1)
     */
    void onMouseClick(MouseEvent e) {
        if (!turn.equals("HUM") || gameOver)
            return;

        // Convert mouse coordinates to board coordinates
        int row = (e.getY() - GameConstants.MARGIN) / GameConstants.CELL;
        int col = (e.getX() - GameConstants.MARGIN) / GameConstants.CELL;

        // Check if clicked position is a legal move
        for (Move move : legalMoves) {
            if (move.landing[0] == row && move.landing[1] == col) {
                executeHumanMove(move);
                return;
            }
        }

        repaint(); // Refresh if invalid click
    }

    /**
     * Execute human move and handle consequences
     * Time Complexity: O(n²) for board update
     * Space Complexity: O(n²) for new board state
     */
    void executeHumanMove(Move move) {
        // Apply move to game state
        GameLogic.MoveResult result = GameLogic.applyMove(board, humanPos, move, GameConstants.HUMAN_START);

        board = result.board;
        
        // Handle special effects
        if (result.sentBack) {
            humanPos = GameConstants.HUMAN_START; // RED peg: reset to start
            updateStatus("Reset by Red Peg!");
        } else {
            humanPos = result.newPos; // Normal move
        }

        repaint();

        // Check for win condition
        if (humanPos[0] == GameConstants.TARGET[0] && humanPos[1] == GameConstants.TARGET[1]) {
            gameOver = true;
            JOptionPane.showMessageDialog(this, "You Won! Great moves!");
            return;
        }

        // Handle special peg effects
        if (result.extraTurn) {
            updateStatus("Gold Peg! Go again.");
            prepareHumanTurn(); // Extra turn for GOLD peg
        } else if (result.swapPositions) {
            // BLUE peg: swap positions with CPU
            int[] temp = humanPos.clone();
            humanPos = cpuPos.clone();
            cpuPos = temp;
            updateStatus("Blue Peg! Positions swapped!");
            repaint();
            prepareHumanTurn();
        } else {
            // Normal turn progression
            turn = "CPU";
            cpuMoving = true;
            consecutiveCpuMoves = 1;
            legalMoves = new ArrayList<>();
            // Delay CPU turn for better game flow
            new javax.swing.Timer(500, e -> cpuTurn()).start();
        }
    }

    /**
     * Execute CPU turn with AI decision making
     * Time Complexity: O(m * k) for AI move evaluation
     * Space Complexity: O(n²) for board updates
     */
    void cpuTurn() {
        if (gameOver || !cpuMoving)
            return;

        // Get AI's chosen move
        Move move = AIEngine.getGreedyMove(board, cpuPos, humanPos, GameConstants.TARGET);

        // Handle case where CPU has no legal moves
        if (move == null) {
            cpuMoving = false;
            updateStatus("CPU Stuck. Your turn.");
            prepareHumanTurn();
            return;
        }

        // Display AI reasoning to player
        if (AIEngine.lastAnalysis != null) {
            updateStatus(AIEngine.lastAnalysis.reasoning);
        }

        // Apply CPU move
        GameLogic.MoveResult result = GameLogic.applyMove(board, cpuPos, move, GameConstants.CPU_START);

        board = result.board;
        if (result.sentBack) {
            cpuPos = GameConstants.CPU_START; // RED peg effect
            updateStatus("CPU Reset!");
        } else {
            cpuPos = result.newPos; // Normal move
        }

        repaint();

        // Check CPU win condition
        if (cpuPos[0] == GameConstants.TARGET[0] && cpuPos[1] == GameConstants.TARGET[1]) {
            gameOver = true;
            cpuMoving = false;
            JOptionPane.showMessageDialog(this, "CPU Wins!");
            return;
        }

        // Handle special peg effects for CPU
        if (result.extraTurn) {
            updateStatus("CPU hit Gold! Extra turn.");
            consecutiveCpuMoves++;
            // Increase delay for consecutive CPU moves for better UX
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
            // Normal turn progression
            cpuMoving = false;
            prepareHumanTurn();
        }
    }

    /**
     * Display status notification
     * Time Complexity: O(k) where k = notification count
     * Space Complexity: O(1) per notification
     */
    void updateStatus(String message) {
        notificationQueue.showNotification(message);
    }

    /**
     * Paint game board and components
     * Time Complexity: O(n²) where n=9 (board cells)
     * Space Complexity: O(1) for rendering
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw grid cells
        for (int r = 0; r < GameConstants.ROWS; r++) {
            for (int c = 0; c < GameConstants.COLS; c++) {
                int x1 = GameConstants.MARGIN + c * GameConstants.CELL;
                int y1 = GameConstants.MARGIN + r * GameConstants.CELL;
                int x2 = x1 + GameConstants.CELL;
                int y2 = y1 + GameConstants.CELL;

                // Draw cell background
                g2d.setColor(new Color(240, 240, 240));
                g2d.fillRect(x1, y1, GameConstants.CELL, GameConstants.CELL);
                g2d.setColor(new Color(204, 204, 204));
                g2d.drawRect(x1, y1, GameConstants.CELL, GameConstants.CELL);

                // Draw target cell (goal)
                if (r == GameConstants.TARGET[0] && c == GameConstants.TARGET[1]) {
                    g2d.setColor(GameConstants.TARGET_COLOR);
                    g2d.fillRect(x1 + 4, y1 + 4, GameConstants.CELL - 8, GameConstants.CELL - 8);
                    g2d.setColor(new Color(255, 165, 0));
                    g2d.setFont(new Font("Arial", Font.BOLD, 8));
                    g2d.drawString("GOAL", x1 + 12, y1 + 24);
                }

                // Draw pegs on board
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

        // Draw human player (Green 'W')
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

        // Draw CPU player (Red 'B')
        int cx = GameConstants.MARGIN + cpuPos[1] * GameConstants.CELL + GameConstants.CELL / 2;
        int cy = GameConstants.MARGIN + cpuPos[0] * GameConstants.CELL + GameConstants.CELL / 2;
        g2d.setColor(GameConstants.CPU_COLOR);
        g2d.fillOval(cx - 16, cy - 16, 32, 32);
        g2d.setColor(Color.BLACK);
        g2d.drawOval(cx - 16, cy - 16, 32, 32);
        g2d.setColor(Color.WHITE);
        g2d.drawString("B", cx - fm.stringWidth("B") / 2, cy + fm.getAscent() / 2);

        // Highlight legal moves for human (green for jumps, blue for steps)
        if (turn.equals("HUM")) {
            for (Move move : legalMoves) {
                int mx = GameConstants.MARGIN + move.landing[1] * GameConstants.CELL;
                int my = GameConstants.MARGIN + move.landing[0] * GameConstants.CELL;
                g2d.setColor(move.jumped != null ? Color.GREEN : Color.BLUE);
                g2d.setStroke(new BasicStroke(3));
                g2d.drawRect(mx + 6, my + 6, GameConstants.CELL - 12, GameConstants.CELL - 12);
            }
        }

        // Draw control panel at bottom
        int panelY = GameConstants.MARGIN + GameConstants.ROWS * GameConstants.CELL + 10;
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.fillRect(GameConstants.MARGIN, panelY, getWidth() - 2 * GameConstants.MARGIN, 60);

        // Draw level label
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.PLAIN, 12));
        g2d.drawString("Level: ", GameConstants.MARGIN + 10, panelY + 20);
    }
}