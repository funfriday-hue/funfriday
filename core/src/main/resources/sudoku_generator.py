import random
import mysql.connector
from copy import deepcopy

# ============================================================

# CONFIG

# ============================================================

MYSQL_HOST = "localhost"
MYSQL_USER = "root"
MYSQL_PASSWORD = ""
MYSQL_DATABASE = "sudoku"

NUM_6X6 = 100
NUM_9X9 = 100

# ============================================================

# MYSQL

# ============================================================

conn = mysql.connector.connect(
host=MYSQL_HOST,
user=MYSQL_USER,
password=MYSQL_PASSWORD,
database=MYSQL_DATABASE
)

cursor = conn.cursor()

# ============================================================

# GENERIC SUDOKU HELPERS

# ============================================================

def get_box_size(size):
if size == 9:
return (3, 3)
elif size == 6:
return (2, 3)
else:
raise Exception("Unsupported size")

def is_valid(board, row, col, num, size):
if num in board[row]:
return False

```
for r in range(size):
    if board[r][col] == num:
        return False

box_rows, box_cols = get_box_size(size)

start_row = row - row % box_rows
start_col = col - col % box_cols

for r in range(start_row, start_row + box_rows):
    for c in range(start_col, start_col + box_cols):
        if board[r][c] == num:
            return False

return True
```

def find_empty(board, size):
for r in range(size):
for c in range(size):
if board[r][c] == 0:
return (r, c)
return None

# ============================================================

# SOLVER

# ============================================================

def solve(board, size):
empty = find_empty(board, size)

```
if not empty:
    return True

row, col = empty

nums = list(range(1, size + 1))
random.shuffle(nums)

for num in nums:
    if is_valid(board, row, col, num, size):
        board[row][col] = num

        if solve(board, size):
            return True

        board[row][col] = 0

return False
```

# ============================================================

# COUNT SOLUTIONS

# ============================================================

def count_solutions(board, size, limit=2):
empty = find_empty(board, size)

```
if not empty:
    return 1

row, col = empty

count = 0

for num in range(1, size + 1):
    if is_valid(board, row, col, num, size):
        board[row][col] = num

        count += count_solutions(board, size, limit)

        board[row][col] = 0

        if count >= limit:
            return count

return count
```

# ============================================================

# GENERATE FULL SOLUTION

# ============================================================

def generate_complete_board(size):
board = [[0] * size for _ in range(size)]
solve(board, size)
return board

# ============================================================

# MAKE UNIQUE PUZZLE

# ============================================================

def create_puzzle(solution_board, size):
puzzle = deepcopy(solution_board)

```
cells = [(r, c) for r in range(size) for c in range(size)]
random.shuffle(cells)

for row, col in cells:
    backup = puzzle[row][col]
    puzzle[row][col] = 0

    temp = deepcopy(puzzle)

    solutions = count_solutions(temp, size)

    if solutions != 1:
        puzzle[row][col] = backup

return puzzle
```

# ============================================================

# SERIALIZE

# ============================================================

def board_to_string(board):
return ''.join(
str(cell)
for row in board
for cell in row
)

# ============================================================

# INSERT

# ============================================================

def insert_puzzle(size, puzzle, solution):
cursor.execute(
"""
INSERT INTO sudoku_puzzles
(
size,
grid,
solution
)
VALUES
(
%s,
%s,
%s
)
""",
(
size,
puzzle,
solution
)
)

# ============================================================

# GENERATION LOOP

# ============================================================

def generate_and_insert(size, count):
print(f"Generating {count} puzzles for size {size}")

```
for i in range(count):
    solution_board = generate_complete_board(size)

    puzzle_board = create_puzzle(
        solution_board,
        size
    )

    puzzle_str = board_to_string(
        puzzle_board
    )

    solution_str = board_to_string(
        solution_board
    )

    insert_puzzle(
        size,
        puzzle_str,
        solution_str
    )

    conn.commit()

    print(
        f"Inserted {i + 1}/{count} size {size}"
    )
```

# ============================================================

# MAIN

# ============================================================

generate_and_insert(6, NUM_6X6)
generate_and_insert(9, NUM_9X9)

print("Done")

cursor.close()
conn.close()

