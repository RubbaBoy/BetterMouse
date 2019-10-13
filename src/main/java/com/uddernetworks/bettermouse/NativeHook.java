package com.uddernetworks.bettermouse;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class NativeHook {

    private static final Logger LOGGER = LoggerFactory.getLogger(NativeHook.class);
    private final int idHook;

    private WinUser.HHOOK hhk;

    private WinUser.HOOKPROC hookProc;

    public NativeHook(int idHook) {
        this.idHook = idHook;
    }

    public void setHook(WinUser.HOOKPROC hookProc) {
        this.hookProc = hookProc;
        CompletableFuture.runAsync(() -> {
            var user32 = User32.INSTANCE;
            var kernel = Kernel32.INSTANCE;

            var handle = kernel.GetModuleHandle(null);
            hhk = user32.SetWindowsHookEx(idHook, hookProc, handle, 0);

            int result;
            WinUser.MSG msg = new WinUser.MSG();
            while ((result = User32.INSTANCE.GetMessage(msg, null, 0, 0)) != 0) {
                if (result == -1) {
                    LOGGER.error("Could not be updated!");
                    break;
                } else {
                    user32.TranslateMessage(msg);
                    user32.DispatchMessage(msg);
                }
            }
        });
    }

    public WinUser.HHOOK getHHK() {
        return hhk;
    }
}
