package com.uddernetworks.bettermouse;

import com.sun.jna.Pointer;
import com.sun.jna.platform.DesktopWindow;
import com.sun.jna.platform.WindowUtils;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import com.uddernetworks.bettermouse.mouse.LowLevelMouseProc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BetterMouse {

    private static final Logger LOGGER = LoggerFactory.getLogger(BetterMouse.class);

    public static final int WM_MOUSEMOVE = 512;
    public static final int MOUSEEVENTF_MOVE = 0x0001;
    public static final int MOUSEEVENTF_ABSOLUTE = 0x8000;

    private WinDef.POINT last = null;

    public static void main(String[] args) throws InterruptedException {
        Thread.sleep(5000);
        new BetterMouse().start(args[0]);
        Thread.sleep(Long.MAX_VALUE);
    }

    private void start(String windowName) {
        DesktopWindow window = WindowUtils.getAllWindows(true)
                .stream()
                .filter(titleWindow -> titleWindow.getTitle().contains(windowName))
                .findFirst().orElseThrow();
        LOGGER.info("Using window '{}'", window.getTitle());
        var size = window.getLocAndSize();

        var user32 = User32.INSTANCE;

        user32.EnumDisplayMonitors(null, null, (hMonitor, hdcMonitor, lprcMonitor, dwData) -> {
            try {
                var monitorInfo = new WinUser.MONITORINFOEX();
                User32.INSTANCE.GetMonitorInfo(hMonitor, monitorInfo);
                if ((monitorInfo.dwFlags & WinUser.MONITORINFOF_PRIMARY) == 0) return 1;

                var monitorSize = monitorInfo.rcMonitor;
                centerMouse();

                user32.SetWindowPos(window.getHWND(), new WinDef.HWND(new Pointer(0)),
                        size.x = (monitorSize.right / 2) - (size.width / 2),
                        size.y = (monitorSize.bottom / 2) - (size.height / 2), 0, 0, WinUser.SWP_NOSIZE);

                var mouseHook = new NativeHook(WinUser.WH_MOUSE_LL);
                mouseHook.setHook((LowLevelMouseProc) (nCode, wParam, info) -> {
                    var point = info.pt;
                    if (last == null) last = point;

                    if (wParam.intValue() == WM_MOUSEMOVE) {
                        var yDiff = last.y - point.y;
                        var xDiff = last.x - point.x;
                        size.y += yDiff;
                        size.x += xDiff;

                        user32.SetWindowPos(window.getHWND(), new WinDef.HWND(new Pointer(0)), size.x, size.y, 0, 0, WinUser.SWP_NOSIZE);
                        return new WinDef.LRESULT(1);
                    }

                    Pointer ptr = info.getPointer();
                    long peer = Pointer.nativeValue(ptr);
                    return User32.INSTANCE.CallNextHookEx(mouseHook.getHHK(), nCode, wParam, new WinDef.LPARAM(peer));
                });

                var keyHook = new NativeHook(WinUser.WH_KEYBOARD_LL);
                keyHook.setHook((WinUser.LowLevelKeyboardProc) (nCode, wParam, info) -> {
                    if (info.vkCode == 27) { // Esc
                        System.exit(0);
                    }

                    Pointer ptr = info.getPointer();
                    long peer = Pointer.nativeValue(ptr);
                    System.out.println(keyHook);
                    return User32.INSTANCE.CallNextHookEx(keyHook.getHHK(), nCode, wParam, new WinDef.LPARAM(peer));
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
            return 1;
        }, new WinDef.LPARAM(0));
    }

    public void centerMouse() {
        var input = new WinUser.INPUT();
        input.type = new WinDef.DWORD(WinUser.INPUT.INPUT_MOUSE);
        input.input.setType("mi");
        input.input.mi.dx = new WinDef.LONG(65535 / 2);
        input.input.mi.dy = new WinDef.LONG(65535 / 2);
        input.input.mi.time = new WinDef.DWORD(0);
        input.input.mi.dwExtraInfo = new BaseTSD.ULONG_PTR(0);
        input.input.mi.dwFlags = new WinDef.DWORD(MOUSEEVENTF_ABSOLUTE | MOUSEEVENTF_MOVE);
        User32.INSTANCE.SendInput(new WinDef.DWORD(1), new WinUser.INPUT[]{input}, input.size());
    }

}
