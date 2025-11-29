#define UNICODE
#define _UNICODE

#include <windows.h>
#include <gdiplus.h>
#include <string>
#include <thread>
#include <chrono>

#pragma comment(lib, "gdiplus.lib")
#pragma comment(lib, "user32.lib")
#pragma comment(lib, "gdi32.lib")

using namespace Gdiplus;

// Глобальные переменные для состояния
ULONG_PTR gdiplusToken;
float progress = 0.0f;     // От 0.0 до 1.0
bool isFinished = false;
HWND hMainWnd = NULL;

// --- ЛОГИКА "СКАНИРОВАНИЯ" ---
void FakeScanningTask(HWND hWnd) {
    // Имитируем работу в течение 5 секунд
    // 100 шагов по 50 мс = 5000 мс = 5 сек
    for (int i = 0; i <= 100; ++i) {
        progress = (float)i / 100.0f;
        
        // Заставляем окно перерисоваться
        InvalidateRect(hWnd, NULL, FALSE);
        
        std::this_thread::sleep_for(std::chrono::milliseconds(50));
    }

    isFinished = true;
    InvalidateRect(hWnd, NULL, FALSE);
}

// --- ОТРИСОВКА ---
void DrawInterface(Graphics& g, int width, int height) {
    // Настройка шрифтов и кистей
    FontFamily fontFamily(L"Segoe UI");
    Font fontHeader(&fontFamily, 24, FontStyleBold, UnitPixel);
    Font fontStatus(&fontFamily, 16, FontStyleRegular, UnitPixel);
    Font fontSuccess(&fontFamily, 28, FontStyleBold, UnitPixel);

    SolidBrush bgBrush(Color(255, 30, 30, 30));        // Темный фон
    SolidBrush whiteBrush(Color(255, 255, 255, 255));  // Белый текст
    SolidBrush greenBrush(Color(255, 50, 205, 50));    // Зеленый для успеха/бара
    SolidBrush grayBrush(Color(255, 70, 70, 70));      // Фон бара
    Pen borderPen(Color(255, 100, 100, 100), 2);

    StringFormat centerFormat;
    centerFormat.SetAlignment(StringAlignmentCenter);
    centerFormat.SetLineAlignment(StringAlignmentCenter);

    // 1. Заливаем фон
    g.FillRectangle(&bgBrush, 0, 0, width, height);

    if (!isFinished) {
        // --- ЭКРАН ПРОЦЕССА ---
        g.DrawString(L"Системное сканирование...", -1, &fontHeader, PointF(width / 2.0f, height * 0.3f), &centerFormat, &whiteBrush);

        // Рисуем рамку прогресс-бара
        float barWidth = width * 0.8f;
        float barHeight = 30.0f;
        float barX = (width - barWidth) / 2.0f;
        float barY = height * 0.5f;

        // Фон бара
        g.FillRectangle(&grayBrush, barX, barY, barWidth, barHeight);
        
        // Заполнение бара (зеленым)
        g.FillRectangle(&greenBrush, barX, barY, barWidth * progress, barHeight);
        
        // Рамка бара
        g.DrawRectangle(&borderPen, barX, barY, barWidth, barHeight);

        // Текст процентов
        std::wstring percentText = std::to_wstring((int)(progress * 100)) + L"%";
        g.DrawString(percentText.c_str(), -1, &fontStatus, PointF(width / 2.0f, barY + barHeight + 20), &centerFormat, &whiteBrush);

    } else {
        // --- ЭКРАН УСПЕХА ---
        g.DrawString(L"МАЙНЕР УДАЛЕН УСПЕШНО", -1, &fontSuccess, PointF(width / 2.0f, height * 0.45f), &centerFormat, &greenBrush);
        g.DrawString(L"Система в безопасности. Можете закрыть окно.", -1, &fontStatus, PointF(width / 2.0f, height * 0.6f), &centerFormat, &whiteBrush);
    }
}

LRESULT CALLBACK WndProc(HWND hWnd, UINT message, WPARAM wParam, LPARAM lParam) {
    switch (message) {
    case WM_PAINT: {
        PAINTSTRUCT ps;
        HDC hdc = BeginPaint(hWnd, &ps);
        RECT rc;
        GetClientRect(hWnd, &rc);

        // Двойная буферизация (чтобы не мерцало)
        HDC memDC = CreateCompatibleDC(hdc);
        HBITMAP memBM = CreateCompatibleBitmap(hdc, rc.right, rc.bottom);
        SelectObject(memDC, memBM);

        Graphics g(memDC);
        g.SetTextRenderingHint(TextRenderingHintAntiAlias);
        
        DrawInterface(g, rc.right, rc.bottom);

        BitBlt(hdc, 0, 0, rc.right, rc.bottom, memDC, 0, 0, SRCCOPY);
        
        DeleteObject(memBM);
        DeleteDC(memDC);
        EndPaint(hWnd, &ps);
        return 0;
    }
    case WM_DESTROY:
        PostQuitMessage(0);
        return 0;
    default:
        return DefWindowProc(hWnd, message, wParam, lParam);
    }
}

int WINAPI wWinMain(HINSTANCE hInstance, HINSTANCE hPrevInstance, LPWSTR lpCmdLine, int nCmdShow) {
    // Инициализация GDI+
    GdiplusStartupInput gdiplusStartupInput;
    GdiplusStartup(&gdiplusToken, &gdiplusStartupInput, NULL);

    WNDCLASSW wc = {0};
    wc.lpfnWndProc = WndProc;
    wc.hInstance = hInstance;
    wc.lpszClassName = L"CleanerApp";
    wc.hCursor = LoadCursor(NULL, IDC_ARROW);
    RegisterClassW(&wc);

    // Создаем окно по центру экрана (размер 600x400)
    int winW = 600;
    int winH = 400;
    int screenW = GetSystemMetrics(SM_CXSCREEN);
    int screenH = GetSystemMetrics(SM_CYSCREEN);
    int posX = (screenW - winW) / 2;
    int posY = (screenH - winH) / 2;

    // Используем обычный стиль окна (WS_OVERLAPPEDWINDOW), чтобы его можно было закрыть крестиком
    hMainWnd = CreateWindowExW(0, L"CleanerApp", L"Anti-Miner Tool",
        WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU | WS_MINIMIZEBOX | WS_VISIBLE,
        posX, posY, winW, winH, NULL, NULL, hInstance, NULL);

    // Запускаем поток "сканирования"
    std::thread worker(FakeScanningTask, hMainWnd);
    worker.detach();

    MSG msg;
    while (GetMessage(&msg, NULL, 0, 0)) {
        TranslateMessage(&msg);
        DispatchMessage(&msg);
    }

    GdiplusShutdown(gdiplusToken);
    return 0;
}
