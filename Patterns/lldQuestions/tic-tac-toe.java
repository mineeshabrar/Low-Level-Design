# Tic Tac Toe — SDE-2 LLD (Java)

```java id="y5g7qn"
import java.util.*;


/*
    ==========================================================
                        TIC TAC TOE
    ==========================================================

    FEATURES
    --------
    1. NxN board
    2. Multiple players support
    3. Winner detection
    4. O(1) move validation
    5. Clean OO design
    6. Extensible
*/


/* ==========================================================
                        SYMBOL
   ========================================================== */

enum Symbol {
    X,
    O
}


/* ==========================================================
                        PLAYER
   ========================================================== */

class Player {

    private final String name;

    private final Symbol symbol;

    public Player(
            String name,
            Symbol symbol
    ) {

        this.name = name;

        this.symbol = symbol;
    }

    public String getName() {
        return name;
    }

    public Symbol getSymbol() {
        return symbol;
    }
}


/* ==========================================================
                        MOVE
   ========================================================== */

class Move {

    private final int row;

    private final int col;

    private final Player player;

    public Move(
            int row,
            int col,
            Player player
    ) {

        this.row = row;

        this.col = col;

        this.player = player;
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }

    public Player getPlayer() {
        return player;
    }
}


/* ==========================================================
                        BOARD
   ========================================================== */

class Board {

    private final int size;

    private final Symbol[][] grid;

    /*
        OPTIMIZED WIN CHECK

        rowCount[player][row]
        colCount[player][col]
    */

    private final Map<Symbol, int[]>
            rowCount = new HashMap<>();

    private final Map<Symbol, int[]>
            colCount = new HashMap<>();

    private final Map<Symbol, Integer>
            diagonalCount = new HashMap<>();

    private final Map<Symbol, Integer>
            antiDiagonalCount = new HashMap<>();

    public Board(
            int size,
            List<Player> players
    ) {

        this.size = size;

        this.grid = new Symbol[size][size];

        for(Player player : players) {

            rowCount.put(
                    player.getSymbol(),
                    new int[size]
            );

            colCount.put(
                    player.getSymbol(),
                    new int[size]
            );

            diagonalCount.put(
                    player.getSymbol(),
                    0
            );

            antiDiagonalCount.put(
                    player.getSymbol(),
                    0
            );
        }
    }

    public boolean isValidMove(
            int row,
            int col
    ) {

        return row >= 0
                &&
                row < size
                &&
                col >= 0
                &&
                col < size
                &&
                grid[row][col] == null;
    }

    public boolean makeMove(
            Move move
    ) {

        int row = move.getRow();

        int col = move.getCol();

        Symbol symbol =
                move.getPlayer()
                        .getSymbol();

        if(!isValidMove(row, col)) {

            return false;
        }

        grid[row][col] = symbol;

        /*
            UPDATE COUNTS
        */

        rowCount.get(symbol)[row]++;

        colCount.get(symbol)[col]++;

        if(row == col) {

            diagonalCount.put(
                    symbol,

                    diagonalCount.get(symbol)
                            + 1
            );
        }

        if(row + col == size - 1) {

            antiDiagonalCount.put(
                    symbol,

                    antiDiagonalCount.get(symbol)
                            + 1
            );
        }

        return true;
    }

    /*
        O(1) WIN CHECK
    */

    public boolean checkWinner(
            Move move
    ) {

        int row = move.getRow();

        int col = move.getCol();

        Symbol symbol =
                move.getPlayer()
                        .getSymbol();

        return rowCount.get(symbol)[row]
                    == size

                ||

                colCount.get(symbol)[col]
                    == size

                ||

                diagonalCount.get(symbol)
                    == size

                ||

                antiDiagonalCount.get(symbol)
                    == size;
    }

    public boolean isBoardFull() {

        for(int i = 0;
                i < size;
                i++) {

            for(int j = 0;
                    j < size;
                    j++) {

                if(grid[i][j] == null) {

                    return false;
                }
            }
        }

        return true;
    }

    public void printBoard() {

        for(int i = 0;
                i < size;
                i++) {

            for(int j = 0;
                    j < size;
                    j++) {

                if(grid[i][j] == null) {

                    System.out.print("- ");

                } else {

                    System.out.print(
                            grid[i][j] + " "
                    );
                }
            }

            System.out.println();
        }
    }
}


/* ==========================================================
                        GAME
   ========================================================== */

class Game {

    private final Board board;

    private final Queue<Player> players;

    private boolean gameOver;

    public Game(
            int size,
            List<Player> playerList
    ) {

        this.board =
                new Board(
                        size,
                        playerList
                );

        this.players =
                new LinkedList<>(
                        playerList
                );

        this.gameOver = false;
    }

    public void playMove(
            int row,
            int col
    ) {

        if(gameOver) {

            System.out.println(
                    "Game already over"
            );

            return;
        }

        Player currentPlayer =
                players.poll();

        Move move =
                new Move(
                        row,
                        col,
                        currentPlayer
                );

        boolean success =
                board.makeMove(move);

        if(!success) {

            System.out.println(
                    "Invalid move"
            );

            players.offer(currentPlayer);

            return;
        }

        System.out.println(
                currentPlayer.getName()
                        + " placed "
                        + currentPlayer.getSymbol()
                        + " at "
                        + row
                        + ","
                        + col
        );

        board.printBoard();

        /*
            CHECK WINNER
        */

        if(board.checkWinner(move)) {

            System.out.println(
                    currentPlayer.getName()
                            + " wins!"
            );

            gameOver = true;

            return;
        }

        /*
            DRAW
        */

        if(board.isBoardFull()) {

            System.out.println(
                    "Game Draw"
            );

            gameOver = true;

            return;
        }

        /*
            NEXT TURN
        */

        players.offer(currentPlayer);
    }
}


/* ==========================================================
                            MAIN
   ========================================================== */

public class Main {

    public static void main(String[] args) {

        Player p1 =
                new Player(
                        "Mineesha",
                        Symbol.X
                );

        Player p2 =
                new Player(
                        "Rahul",
                        Symbol.O
                );

        Game game =
                new Game(
                        3,
                        Arrays.asList(
                                p1,
                                p2
                        )
                );

        game.playMove(0, 0);

        game.playMove(1, 0);

        game.playMove(0, 1);

        game.playMove(1, 1);

        game.playMove(0, 2);
    }
}
```
