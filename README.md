# FunFriday Games Backend 🎮

A real-time multiplayer gaming server built to host quick, interactive challenges with friends.

---

## 🎮 Available Games

The server currently supports two game types, automatically managing live scoring and sync across players:

1. **SUDOKU:** A competitive multiplayer race. Track live board progress, notes mode entry updates, and scores as players work to finish the layout or give up.
2. **WORDLE:** A classic word-guessing game adapted for synchronized rooms where players guess the mystery word in parallel.

---

## 🚀 How to Run the Server

Follow these simple steps to launch the backend application on your machine.

### 1. Prerequisites
Make sure you have the following installed on your Mac:
* **Java 17 or higher** (To check, run: `java -version`)
* **Maven** (To check, run: `mvn -v`)

### 2. Startup Commands
Open your terminal, navigate directly into the project's root folder, and execute the following commands:

```bash
# Clean previous build artifacts and compile dependencies
mvn clean install

# Launch the game server
mvn spring-boot:run