# Clash of Pegs — Adversarial AI Strategy Game

A two-player adversarial strategy game (Human vs CPU) built in Java, inspired by Simon Tatham puzzles. The CPU AI was developed across 3 phases, each implementing a more sophisticated algorithm paradigm.

## Game Rules
- 9x9 board with special pegs: Gold (extra turn), Blue (swap positions), Red (sent back to start)
- Human and CPU race from opposite corners to the center goal
- Jumping is mandatory when available
- First to land exactly on the goal wins

## Algorithm Paradigms Implemented

### Review 1 — Greedy
CPU selects the best immediate move toward the goal without considering future consequences

### Review 2 — Divide & Conquer
Recursive game-tree search to depth 4, exploring and evaluating future board states

### Review 3 — Minimax + DP + Backtracking
- Minimax with memoised Dynamic Programming for optimal decision making
- Backtracking with in-place board undo for efficient state management
- Move-sorting (closest-to-goal first) to reduce explored branches
- Worst-case O(N^4) with significant practical speedup via memoisation

## Tech Stack
Java, Java Swing (GUI)

## How to Run
1. Clone the repo
2. Open in Eclipse or any Java IDE
3. Navigate to Review-3-Final-DP-Backtracking
4. Run `PegRaceGame.java`

## Project Structure
- `Review-1-Greedy/` — Greedy AI implementation
- `Review-2-DivideAndConquer/` — D&C game tree search
- `Review-3-Final-DP-Backtracking/` — Final version with Minimax + DP + Backtracking
