import tkinter as tk
from tkinter import messagebox
import random

class WordleGame:
    def __init__(self, master):
        self.master = master
        self.master.title("Wordle Game")
        self.master.geometry("400x600")

        # The target word (Password)
        self.target_word = "PENIS"
        self.max_attempts = 6
        self.current_attempt = 0

        self.guesses = [["" for _ in range(5)] for _ in range(6)]
        self.cells = []

        self.create_widgets()

    def create_widgets(self):
        # Title
        title_label = tk.Label(self.master, text="Guess the Word!", font=("Helvetica", 24))
        title_label.pack(pady=20)

        # Grid
        grid_frame = tk.Frame(self.master)
        grid_frame.pack(pady=20)

        for row in range(6):
            row_cells = []
            for col in range(5):
                cell = tk.Label(
                    grid_frame,
                    text="",
                    width=4,
                    height=2,
                    font=("Helvetica", 18, "bold"),
                    relief="solid",
                    borderwidth=1
                )
                cell.grid(row=row, column=col, padx=2, pady=2)
                row_cells.append(cell)
            self.cells.append(row_cells)

        # Input
        input_frame = tk.Frame(self.master)
        input_frame.pack(pady=20)

        self.entry = tk.Entry(input_frame, font=("Helvetica", 14), width=10)
        self.entry.pack(side=tk.LEFT, padx=10)
        self.entry.bind("<Return>", self.check_guess)

        submit_btn = tk.Button(input_frame, text="Guess", command=self.check_guess)
        submit_btn.pack(side=tk.LEFT)

    def check_guess(self, event=None):
        guess = self.entry.get().upper()
        if len(guess) != 5:
            messagebox.showwarning("Invalid Input", "Please enter a 5-letter word.")
            return

        if not guess.isalpha():
            messagebox.showwarning("Invalid Input", "Only letters are allowed.")
            return

        self.update_grid(guess)
        self.current_attempt += 1
        self.entry.delete(0, tk.END)

        if guess == self.target_word:
            messagebox.showinfo("Success", f"Correct! The password is: {self.target_word}")
            self.master.quit()
        elif self.current_attempt >= self.max_attempts:
            messagebox.showinfo("Game Over", f"Out of attempts! The word was: {self.target_word}")
            self.master.quit()

    def update_grid(self, guess):
        row = self.current_attempt
        for i, letter in enumerate(guess):
            label = self.cells[row][i]
            label.config(text=letter)

            # Color logic
            if letter == self.target_word[i]:
                label.config(bg="green", fg="white")
            elif letter in self.target_word:
                label.config(bg="yellow", fg="black")
            else:
                label.config(bg="gray", fg="white")

if __name__ == "__main__":
    root = tk.Tk()
    game = WordleGame(root)
    root.mainloop()
